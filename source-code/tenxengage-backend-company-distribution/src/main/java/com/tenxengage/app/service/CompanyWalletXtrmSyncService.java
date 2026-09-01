package com.tenxengage.app.service;

import com.tenxengage.app.entity.PartnerCompanyXtrmAccount;
import com.tenxengage.app.entity.RewardWallet;
import com.tenxengage.app.entity.enums.WalletType;
import com.tenxengage.app.repository.PartnerCompanyXtrmAccountRepository;
import com.tenxengage.app.repository.RewardWalletRepository;
import com.tenxengage.app.security.TenantValidator;
import com.tenxengage.app.service.xtrm.XtrmApiClient;
import com.tenxengage.app.service.xtrm.XtrmApiClient.GetWalletsCommand;
import com.tenxengage.app.service.xtrm.XtrmApiClient.GetWalletsResult;
import com.tenxengage.app.service.xtrm.XtrmApiClient.WalletInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

/**
 * Mirrors a partner company's XTRM balance onto its internal wallet.
 *
 * <p><b>XTRM holds the money; this wallet holds the hold.</b> A company is funded once, at XTRM, and that
 * balance is copied here — nobody credits this wallet by hand. What this row adds is the one thing XTRM
 * cannot do: reserve.</p>
 *
 * <p>XTRM reports only money that has already moved. It offers no way to say "set aside 80 for me while I
 * pay these sellers", so two admins submitting at the same moment would both read the same balance, both
 * pass, and the second would run out part-way through its fan-out — some sellers paid, the rest stranded.
 * A row we own can be locked ({@code SELECT … FOR UPDATE}), so the second submit waits, sees what the first
 * already committed, and is refused before anyone is paid.</p>
 *
 * <p>Hence the split: {@code available} is XTRM's, restated on every read; {@code reserved} is ours, and
 * survives the restatement because the balance is always recomputed as
 * {@code xtrmBalance - reserved}. That formula is self-correcting — an in-flight distribution still shows
 * against a stale XTRM balance until its payouts actually land.</p>
 *
 * <p>Deliberately writes <b>no ledger entry</b>. A sync is a restatement of someone else's number, not a
 * movement of money, and this runs on every page load. {@code RESERVE}, {@code DEBIT} and {@code RELEASE}
 * still record theirs, so the audit trail continues to answer "what did this company commit", which is the
 * question it exists for.</p>
 */
@Service
public class CompanyWalletXtrmSyncService {

    private static final Logger log = LoggerFactory.getLogger(CompanyWalletXtrmSyncService.class);

    /** The only currency distributions support, matching {@code FundCompanyWalletRequest}. */
    static final String CURRENCY = "cash";

    private final PartnerCompanyXtrmAccountRepository accountRepository;
    private final RewardWalletRepository walletRepository;
    private final XtrmApiClient xtrmApiClient;
    private final TenantValidator tenantValidator;

    /** Self-proxy: the transactional write is called from a method that must not be transactional. */
    private CompanyWalletXtrmSyncService self;

    public CompanyWalletXtrmSyncService(PartnerCompanyXtrmAccountRepository accountRepository,
                                        RewardWalletRepository walletRepository,
                                        XtrmApiClient xtrmApiClient,
                                        TenantValidator tenantValidator) {
        this.accountRepository = accountRepository;
        this.walletRepository = walletRepository;
        this.xtrmApiClient = xtrmApiClient;
        this.tenantValidator = tenantValidator;
    }

    @Autowired
    public void setSelf(@Lazy CompanyWalletXtrmSyncService self) {
        this.self = self;
    }

    /**
     * Refresh this company's spendable balance from XTRM.
     *
     * <p><b>Never throws.</b> It runs before reading the wallet and before submitting a distribution, and
     * neither should fail because the vendor is briefly unreachable — the stored balance is then simply
     * the last one we knew. Failing open is safe in this direction: a stale balance can only be lower than
     * the truth for money added at XTRM since, so the worst case is refusing a distribution that would in
     * fact have been affordable.</p>
     *
     * <p>Not {@code @Transactional} on purpose: it makes an HTTP call to XTRM, and holding a database
     * connection open for a vendor's latency is what the provisioning path already avoids.</p>
     */
    public void syncIfConnected(UUID partnerCompanyId) {
        try {
            // Same guard the wallet read uses, applied before any vendor call — otherwise this endpoint
            // would let a caller trigger XTRM traffic for a company they cannot see.
            tenantValidator.validatePartnerCompanyAccess(partnerCompanyId);
            doSync(tenantValidator.getCurrentClientId(), partnerCompanyId);
        } catch (RuntimeException e) {
            log.warn("[step=company_wallet_sync_failed] partnerCompanyId={} reason={}",
                    partnerCompanyId, e.getClass().getSimpleName());
        }
    }

    private void doSync(UUID clientId, UUID partnerCompanyId) {
        PartnerCompanyXtrmAccount account = accountRepository
                .findByClientIdAndPartnerCompanyId(clientId, partnerCompanyId)
                .orElse(null);
        // No account, or one still being provisioned, has no balance to mirror. Not an error: a company
        // that has never connected simply cannot distribute yet, and the store says so.
        if (account == null || !account.isPayoutReady()) {
            return;
        }

        GetWalletsResult result = xtrmApiClient.getBeneficiaryWallets(
                new GetWalletsCommand(account.getXtrmAccountNumber()));
        if (!result.success()) {
            log.warn("[step=company_wallet_sync_failed] XTRM rejected the wallet lookup; partnerCompanyId={} errors={}",
                    partnerCompanyId, result.errors());
            return;
        }

        BigDecimal balance = result.wallets().stream()
                .filter(w -> Objects.equals(account.getXtrmWalletId(), w.id()))
                .map(WalletInfo::balance)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
        if (balance == null) {
            // The wallet we provisioned against is gone, renamed, or reported without a balance. Leaving
            // the stored figure alone is the safe reading — zeroing it would strand a funded company.
            log.warn("[step=company_wallet_sync_failed] XTRM returned no balance for wallet {}; partnerCompanyId={}",
                    account.getXtrmWalletId(), partnerCompanyId);
            return;
        }

        self.applyBalance(clientId, partnerCompanyId, balance);
    }

    /**
     * The transactional half: restate {@code available} from XTRM, leaving {@code reserved} untouched.
     *
     * <p>Creates the wallet on first sync. Before this existed the row appeared only when someone funded
     * it by hand, so a company with money at XTRM showed as "not funded yet" — true of the row, and quite
     * false of the company.</p>
     */
    @Transactional
    public void applyBalance(UUID clientId, UUID partnerCompanyId, BigDecimal xtrmBalance) {
        RewardWallet wallet = walletRepository
                .findForUpdateByCompany(clientId, partnerCompanyId, CURRENCY, WalletType.COMPANY)
                .orElseGet(() -> walletRepository.save(RewardWallet.builder()
                        .clientId(clientId)
                        .partnerCompanyId(partnerCompanyId)
                        .currencyId(CURRENCY)
                        .walletType(WalletType.COMPANY)
                        .build()));

        BigDecimal reserved = wallet.getReservedBalance() == null ? BigDecimal.ZERO : wallet.getReservedBalance();
        // Never negative: money already committed can exceed a balance XTRM has since paid out, and a
        // negative available would read as debt the company does not owe.
        BigDecimal available = xtrmBalance.subtract(reserved).max(BigDecimal.ZERO);

        if (available.compareTo(wallet.getAvailableBalance()) == 0) {
            return; // Nothing moved; skip the write and the version bump.
        }

        log.info("[step=company_wallet_synced] partnerCompanyId={} xtrmBalance={} reserved={} available={}",
                partnerCompanyId, xtrmBalance, reserved, available);
        wallet.setAvailableBalance(available);
        walletRepository.save(wallet);
    }
}

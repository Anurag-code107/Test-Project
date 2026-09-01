package com.tenxengage.app.service;

import com.tenxengage.app.entity.PartnerCompanyXtrmAccount;
import com.tenxengage.app.entity.RewardWallet;
import com.tenxengage.app.entity.enums.WalletType;
import com.tenxengage.app.entity.enums.XtrmAccountStatus;
import com.tenxengage.app.repository.PartnerCompanyXtrmAccountRepository;
import com.tenxengage.app.repository.RewardWalletRepository;
import com.tenxengage.app.security.TenantValidator;
import com.tenxengage.app.service.xtrm.XtrmApiClient;
import com.tenxengage.app.service.xtrm.XtrmApiClient.GetWalletsResult;
import com.tenxengage.app.service.xtrm.XtrmApiClient.WalletInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * XTRM holds the money; this wallet holds the hold.
 *
 * <p>The company is funded once, at XTRM, and that balance is copied here — so {@code available} is a
 * restatement of someone else's number, while {@code reserved} is ours and must survive it.</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CompanyWalletXtrmSyncServiceTest {

    @Mock private PartnerCompanyXtrmAccountRepository accountRepository;
    @Mock private RewardWalletRepository walletRepository;
    @Mock private XtrmApiClient xtrmApiClient;
    @Mock private TenantValidator tenantValidator;

    private CompanyWalletXtrmSyncService service;

    private static final UUID CLIENT_ID = UUID.randomUUID();
    private static final UUID COMPANY_ID = UUID.randomUUID();
    private static final String SPN = "SPN26241048";
    private static final String WALLET_ID = "207678";

    private RewardWallet wallet;

    @BeforeEach
    void setUp() {
        service = new CompanyWalletXtrmSyncService(
                accountRepository, walletRepository, xtrmApiClient, tenantValidator);
        service.setSelf(service);

        when(tenantValidator.getCurrentClientId()).thenReturn(CLIENT_ID);
        when(accountRepository.findByClientIdAndPartnerCompanyId(CLIENT_ID, COMPANY_ID))
                .thenReturn(Optional.of(connectedAccount()));

        wallet = RewardWallet.builder()
                .clientId(CLIENT_ID).partnerCompanyId(COMPANY_ID)
                .currencyId(CompanyWalletXtrmSyncService.CURRENCY).walletType(WalletType.COMPANY)
                .build();
        when(walletRepository.findForUpdateByCompany(
                CLIENT_ID, COMPANY_ID, CompanyWalletXtrmSyncService.CURRENCY, WalletType.COMPANY))
                .thenReturn(Optional.of(wallet));
        when(walletRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private PartnerCompanyXtrmAccount connectedAccount() {
        PartnerCompanyXtrmAccount a = new PartnerCompanyXtrmAccount();
        a.setClientId(CLIENT_ID);
        a.setPartnerCompanyId(COMPANY_ID);
        a.setXtrmAccountNumber(SPN);
        a.setXtrmWalletId(WALLET_ID);
        a.setEncryptedCredentials("blob");
        a.setStatus(XtrmAccountStatus.CONNECTED);
        return a;
    }

    private void xtrmReports(String balance) {
        when(xtrmApiClient.getBeneficiaryWallets(any())).thenReturn(GetWalletsResult.ok(
                List.of(new WalletInfo(WALLET_ID, "Cash", "USD", new BigDecimal(balance)))));
    }

    @Test
    void copiesTheXtrmBalanceOntoTheWallet() {
        xtrmReports("100.00");

        service.syncIfConnected(COMPANY_ID);

        assertThat(wallet.getAvailableBalance()).isEqualByComparingTo("100.00");
    }

    @Test
    void keepsMoneyAlreadyCommittedOutOfTheSpendableBalance() {
        // The whole reason this row exists. XTRM still reports 100 while a distribution's payouts are in
        // flight, so a straight copy would offer the reserved 80 for spending a second time.
        wallet.setReservedBalance(new BigDecimal("80.00"));
        xtrmReports("100.00");

        service.syncIfConnected(COMPANY_ID);

        assertThat(wallet.getAvailableBalance()).isEqualByComparingTo("20.00");
        assertThat(wallet.getReservedBalance()).isEqualByComparingTo("80.00");
    }

    @Test
    void neverReportsANegativeBalance() {
        // XTRM has already paid out what we still hold reserved. Available is nothing, not debt.
        wallet.setReservedBalance(new BigDecimal("80.00"));
        xtrmReports("20.00");

        service.syncIfConnected(COMPANY_ID);

        assertThat(wallet.getAvailableBalance()).isEqualByComparingTo("0.00");
    }

    @Test
    void createsTheWalletOnFirstSync() {
        // Before this, the row appeared only when someone funded it by hand — so a company with money at
        // XTRM read as "not funded yet", which was true of the row and false of the company.
        when(walletRepository.findForUpdateByCompany(any(), any(), any(), any())).thenReturn(Optional.empty());
        xtrmReports("100.00");

        service.syncIfConnected(COMPANY_ID);

        // Two saves, deliberately: one to create the row, one to write the balance onto it. Assert the row
        // that ends up persisted rather than a call count, which would only pin the implementation.
        ArgumentCaptor<RewardWallet> saved = ArgumentCaptor.forClass(RewardWallet.class);
        verify(walletRepository, atLeastOnce()).save(saved.capture());
        RewardWallet created = saved.getValue();
        assertThat(created.getPartnerCompanyId()).isEqualTo(COMPANY_ID);
        assertThat(created.getWalletType()).isEqualTo(WalletType.COMPANY);
        assertThat(created.getAvailableBalance()).isEqualByComparingTo("100.00");
    }

    @Test
    void leavesTheStoredBalanceAloneWhenXtrmIsUnreachable() {
        wallet.setAvailableBalance(new BigDecimal("50.00"));
        when(xtrmApiClient.getBeneficiaryWallets(any()))
                .thenReturn(GetWalletsResult.failed(List.of("Could not reach XTRM"), true));

        service.syncIfConnected(COMPANY_ID);

        // Failing open is safe in this direction: a stale figure can only understate money added since.
        assertThat(wallet.getAvailableBalance()).isEqualByComparingTo("50.00");
    }

    @Test
    void leavesTheStoredBalanceAloneWhenOurWalletIsNotInTheResponse() {
        wallet.setAvailableBalance(new BigDecimal("50.00"));
        when(xtrmApiClient.getBeneficiaryWallets(any())).thenReturn(GetWalletsResult.ok(
                List.of(new WalletInfo("999999", "Someone else", "USD", new BigDecimal("7.00")))));

        service.syncIfConnected(COMPANY_ID);

        assertThat(wallet.getAvailableBalance()).isEqualByComparingTo("50.00");
    }

    @Test
    void doesNotCallXtrmForACompanyThatHasNeverConnected() {
        when(accountRepository.findByClientIdAndPartnerCompanyId(CLIENT_ID, COMPANY_ID))
                .thenReturn(Optional.empty());

        service.syncIfConnected(COMPANY_ID);

        verify(xtrmApiClient, never()).getBeneficiaryWallets(any());
    }

    @Test
    void doesNotCallXtrmForAnAccountStillBeingProvisioned() {
        PartnerCompanyXtrmAccount pending = connectedAccount();
        pending.setStatus(XtrmAccountStatus.PENDING);
        when(accountRepository.findByClientIdAndPartnerCompanyId(CLIENT_ID, COMPANY_ID))
                .thenReturn(Optional.of(pending));

        service.syncIfConnected(COMPANY_ID);

        verify(xtrmApiClient, never()).getBeneficiaryWallets(any());
    }

    @Test
    void refusesToTouchACompanyTheCallerCannotSee() {
        doThrow(new AccessDeniedException("nope")).when(tenantValidator).validatePartnerCompanyAccess(COMPANY_ID);

        service.syncIfConnected(COMPANY_ID);

        // Swallowed rather than thrown — the read that follows raises the real 403 — but the vendor call
        // must not happen, or this endpoint becomes a way to probe other companies.
        verify(xtrmApiClient, never()).getBeneficiaryWallets(any());
    }

    @Test
    void skipsTheWriteWhenNothingChanged() {
        wallet.setAvailableBalance(new BigDecimal("100.00"));
        xtrmReports("100.00");

        service.syncIfConnected(COMPANY_ID);

        // Runs on every page load; a no-op sync must not bump the row's version and fight optimistic locking.
        verify(walletRepository, never()).save(any());
    }
}

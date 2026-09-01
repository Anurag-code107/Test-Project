package com.tenxengage.app.service.xtrm;

import com.tenxengage.app.entity.Client;
import com.tenxengage.app.entity.PartnerCompany;
import com.tenxengage.app.entity.PartnerCompanyXtrmAccount;
import com.tenxengage.app.entity.enums.XtrmAccountStatus;
import com.tenxengage.app.repository.ClientRepository;
import com.tenxengage.app.repository.PartnerCompanyRepository;
import com.tenxengage.app.repository.PartnerCompanyXtrmAccountRepository;
import com.tenxengage.app.service.xtrm.XtrmApiClient.CreateBeneficiaryCommand;
import com.tenxengage.app.service.xtrm.XtrmApiClient.CreateBeneficiaryResult;
import com.tenxengage.app.service.xtrm.XtrmApiClient.GetWalletsCommand;
import com.tenxengage.app.service.xtrm.XtrmApiClient.GetWalletsResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Gives a partner company an identity at XTRM.
 *
 * <p>Three calls, and the order <em>is</em> the design. {@code Beneficiary/CreateBeneficiary} returns the
 * company's SPN <em>and</em> its pseudo credentials, and it returns the secret <b>exactly once</b>. The call
 * cannot be replayed for the same company — the name is taken on a second attempt — so the credentials are
 * written to the database before the token check or the wallet lookup is attempted. Losing them costs a
 * support ticket to XTRM; persisting them early costs one write.</p>
 *
 * <p>This is the only class that writes {@code encrypted_credentials}.</p>
 *
 * <p><b>Transaction boundaries.</b> {@link #claim} runs inside the caller's transaction and nothing else
 * does: {@link #provision} makes HTTP calls, so it runs outside any transaction and each repository save is
 * its own. That matches {@code XtrmEnrollmentService}, which enrolls individual payees the same way.</p>
 */
@Service
public class XtrmCompanyProvisioningService {

    private static final Logger log = LoggerFactory.getLogger(XtrmCompanyProvisioningService.class);
    private static final int ERROR_MAX = 500;
    private static final int NAME_MAX = 255;

    private final PartnerCompanyXtrmAccountRepository accountRepository;
    private final PartnerCompanyRepository companyRepository;
    private final XtrmApiClient xtrmApiClient;
    private final XtrmCredentialsResolver credentialsResolver;
    private final ClientRepository clientRepository;

    /**
     * Whether XTRM emails the company admin when the account is created.
     *
     * <p><b>On by default.</b> That email is how the admin reaches XTRM's own portal — without it they
     * have an account they cannot sign in to, and nothing else in this system hands them those
     * credentials. Suppressing it makes provisioning look successful while leaving the admin locked out.</p>
     *
     * <p>It was originally off outside prod, to stop a mistyped address in a developer's form from
     * emailing a stranger from the sandbox. That risk is much smaller since D-16: the address belongs to
     * the admin who signs in and completes their own profile, rather than being typed on their behalf.
     * Set {@code XTRM_BENEFICIARY_EMAIL_NOTIFICATION=false} to suppress it for a specific environment.</p>
     */
    @Value("${redemption.xtrm.beneficiary-email-notification:true}")
    private boolean emailNotification;

    public XtrmCompanyProvisioningService(PartnerCompanyXtrmAccountRepository accountRepository,
                                          PartnerCompanyRepository companyRepository,
                                          XtrmApiClient xtrmApiClient,
                                          XtrmCredentialsResolver credentialsResolver,
                                          ClientRepository clientRepository) {
        this.accountRepository = accountRepository;
        this.companyRepository = companyRepository;
        this.xtrmApiClient = xtrmApiClient;
        this.credentialsResolver = credentialsResolver;
        this.clientRepository = clientRepository;
    }

    /**
     * Reserve this company's provisioning slot.
     *
     * <p>Called inside the company-create transaction, and {@code MANDATORY} so it cannot accidentally be
     * called outside one. {@code uq_xtrm_account_per_company} is what actually serializes concurrent
     * attempts, and it can only do that if the row exists <em>before</em> anyone calls XTRM. Claim first and
     * the loser of the race stops here; claim last and both attempts create a real beneficiary company at
     * XTRM, one of which we then discard and can never delete.</p>
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public PartnerCompanyXtrmAccount claim(UUID clientId, UUID partnerCompanyId) {
        return accountRepository.save(PartnerCompanyXtrmAccount.builder()
                .clientId(clientId)
                .partnerCompanyId(partnerCompanyId)
                .status(XtrmAccountStatus.PENDING)
                .build());
    }

    /**
     * Run the three-call sequence against an existing claim row.
     *
     * <p>Idempotent, resumable, and <b>never throws</b>: it runs after the company is already committed, so
     * an exception here cannot undo anything — it can only surface as an unhandled error on a background
     * thread.</p>
     */
    public void provision(UUID clientId, UUID partnerCompanyId) {
        try {
            doProvision(clientId, partnerCompanyId);
        } catch (RuntimeException e) {
            log.error("[step=xtrm_provision_failed] partnerCompanyId={} reason={}",
                    partnerCompanyId, e.getClass().getSimpleName(), e);
            recordError(clientId, partnerCompanyId, "Provisioning failed: " + e.getClass().getSimpleName());
        }
    }

    private void doProvision(UUID clientId, UUID partnerCompanyId) {
        PartnerCompanyXtrmAccount account = accountRepository
                .findByClientIdAndPartnerCompanyId(clientId, partnerCompanyId)
                .orElse(null);
        if (account == null) {
            log.warn("[step=xtrm_provision_skipped] no claim row; partnerCompanyId={}", partnerCompanyId);
            return;
        }
        if (account.isPayoutReady()) {
            return; // already CONNECTED, and CreateBeneficiary is not replayable
        }

        PartnerCompany company = companyRepository.findByIdAndClientId(partnerCompanyId, clientId).orElse(null);
        if (company == null || !company.hasCompleteAdminDetails()) {
            log.info("[step=xtrm_provision_skipped] no admin details; partnerCompanyId={}", partnerCompanyId);
            recordError(account, "Company admin details are required before connecting to XTRM.");
            return;
        }

        // --- 1. CreateBeneficiary, then persist immediately -------------------------------------------
        // Skipped when the row already carries an SPN: resuming must not replay a call that cannot be
        // replayed.
        if (account.getXtrmAccountNumber() == null) {
            String beneficiaryName = beneficiaryNameFor(company, clientNameOf(clientId));
            CreateBeneficiaryResult created = xtrmApiClient.createBeneficiary(new CreateBeneficiaryCommand(
                    beneficiaryName, company.getWebsite(),
                    company.getAdminFirstName(), company.getAdminLastName(), company.getAdminEmail(),
                    company.getAdminMobileNumber(), company.getAdminCountryIso2(),
                    company.getAdminCity(), company.getAdminRegion(), company.getAdminPostalCode(),
                    emailNotification));

            if (!created.success()) {
                recordError(account, String.join("; ", created.errors()));
                return;
            }

            account.setXtrmAccountNumber(created.beneficiaryAccountNumber());
            account.setXtrmBeneficiaryName(beneficiaryName);
            account.setAccountIdentityLevel(created.accountIdentityLevel());
            account.setEncryptedCredentials(
                    credentialsResolver.encryptCredentials(created.clientId(), created.clientSecret()));
            account.setLastError(null);
            // Still PENDING — no wallet yet, and the constraint refuses CONNECTED without one. This save is
            // the point of the whole ordering: from here the secret is durable.
            account = accountRepository.save(account);
        }

        // --- 2. Prove the credentials actually work ----------------------------------------------------
        // The only way to learn this before money depends on it. It also warms the per-client-id token cache
        // for the first payout.
        try {
            credentialsResolver.forCompanyUnchecked(account);
        } catch (RuntimeException e) {
            recordError(account, "Credentials could not be used: " + e.getClass().getSimpleName());
            return;
        }

        // --- 3. Discover the wallet --------------------------------------------------------------------
        GetWalletsResult wallets;
        try {
            wallets = xtrmApiClient.getBeneficiaryWallets(new GetWalletsCommand(account.getXtrmAccountNumber()));
        } catch (RuntimeException e) {
            recordError(account, "Wallet lookup failed: " + e.getClass().getSimpleName());
            return;
        }
        if (!wallets.success() || wallets.wallets().isEmpty()) {
            recordError(account, "XTRM returned no wallet for this company.");
            return;
        }

        account.setXtrmWalletId(wallets.wallets().get(0).id());
        account.setStatus(XtrmAccountStatus.CONNECTED);
        account.setConnectedAt(Instant.now());
        account.setLastError(null);
        accountRepository.save(account);

        log.info("[step=xtrm_provision_connected] partnerCompanyId={} account={}",
                partnerCompanyId, account.getXtrmAccountNumber());
    }

    /**
     * The name sent as {@code BeneficiaryCompanyName}.
     *
     * <p>Our company names are unique per tenant; XTRM's namespace appears to be global under the issuer
     * account. Two tenants each with an "Acme Corp" would then collide on the second create, and the failure
     * would read as a vendor outage rather than a name clash. Disambiguating costs nothing if the namespace
     * turns out to be per-issuer after all. <b>Unverified</b> — this is a cheap defence, not a known fact.</p>
     */
    public String beneficiaryNameFor(PartnerCompany company, String clientName) {
        String composed = clientName == null || clientName.isBlank()
                ? company.getName()
                : company.getName() + " (" + clientName + ")";
        return composed.length() <= NAME_MAX ? composed : composed.substring(0, NAME_MAX);
    }

    private String clientNameOf(UUID clientId) {
        return clientRepository.findById(clientId).map(Client::getName).orElse("");
    }

    private void recordError(UUID clientId, UUID partnerCompanyId, String message) {
        accountRepository.findByClientIdAndPartnerCompanyId(clientId, partnerCompanyId)
                .ifPresent(a -> recordError(a, message));
    }

    /**
     * Records why the last attempt failed, without discarding anything already learned — in particular the
     * credentials, which cannot be obtained again.
     */
    private void recordError(PartnerCompanyXtrmAccount account, String message) {
        account.setStatus(XtrmAccountStatus.PENDING);
        account.setLastError(message == null ? null : message.substring(0, Math.min(message.length(), ERROR_MAX)));
        accountRepository.save(account);
    }
}

package com.tenxengage.app.service.xtrm;

import com.tenxengage.app.entity.PartnerCompanyXtrmAccount;
import com.tenxengage.app.exception.BusinessRuleException;
import com.tenxengage.app.repository.PartnerCompanyXtrmAccountRepository;
import com.tenxengage.app.service.ConnectorEncryptionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Chooses which XTRM account a call authenticates as.
 *
 * <p>Two answers, and the difference is whose money is being moved:</p>
 *
 * <ul>
 *   <li>{@link #platform()} — the client's own account. Personal redemptions, enrolment, catalogue reads, and
 *       funding a partner company. The money is the client's.</li>
 *   <li>{@link #forCompany} — a partner company's pseudo credentials, for a company paying its own sellers.
 *       The money is the company's, and XTRM refuses to move it on the platform's authority.</li>
 * </ul>
 *
 * <p>Credentials are decrypted per call rather than held in a map. They are needed once per payout and the
 * blob is small, so keeping decrypted secrets in a long-lived cache would add real exposure for no meaningful
 * saving. The expensive part — the access token — is what gets cached, in {@code XtrmApiClientImpl}.</p>
 */
@Service
public class XtrmCredentialsResolver {

    private static final Logger log = LoggerFactory.getLogger(XtrmCredentialsResolver.class);

    static final String KEY_CLIENT_ID = "clientId";
    static final String KEY_CLIENT_SECRET = "clientSecret";

    /** One message for every not-connected case, so it reads the same to an admin however it failed. */
    private static final String NOT_CONNECTED_CODE = "XTRM_COMPANY_NOT_CONNECTED";

    private final PartnerCompanyXtrmAccountRepository accountRepository;
    private final ConnectorEncryptionService encryptionService;

    @Value("${redemption.xtrm.client-id}")
    private String platformClientId;
    @Value("${redemption.xtrm.client-secret}")
    private String platformClientSecret;
    @Value("${redemption.xtrm.issuer-account-number}")
    private String platformIssuerAccountNumber;
    @Value("${redemption.xtrm.wallet-id}")
    private String platformWalletId;
    @Value("${redemption.xtrm.program-id}")
    private String programId;

    /**
     * Identity levels XTRM will actually pay out from, e.g. {@code Verified,Advanced}.
     *
     * <p><b>Empty means no opinion, and empty is the default.</b> This is a configured lever, not a
     * protection — anything depending on identity level being enforced is depending on someone setting this
     * property. We have observed exactly one value ({@code Basic}), and a rule inferred from one
     * observation would block real companies on a guess. Tighten it when XTRM says which level clears
     * payouts; no code change needed.</p>
     */
    @Value("${redemption.xtrm.acceptable-identity-levels:}")
    private String acceptableIdentityLevels;

    public XtrmCredentialsResolver(PartnerCompanyXtrmAccountRepository accountRepository,
                                   ConnectorEncryptionService encryptionService) {
        this.accountRepository = accountRepository;
        this.encryptionService = encryptionService;
    }

    /** The client's own XTRM account — everything that spends the client's money. */
    public XtrmCredentials platform() {
        return new XtrmCredentials(platformClientId, platformClientSecret,
                platformIssuerAccountNumber, platformWalletId, programId);
    }

    /** True when this company has usable credentials, i.e. the XTRM rails are open for it. */
    @Transactional(readOnly = true)
    public boolean canPayFromOwnWallet(UUID clientId, UUID partnerCompanyId) {
        return accountRepository.findByClientIdAndPartnerCompanyId(clientId, partnerCompanyId)
                .filter(PartnerCompanyXtrmAccount::isPayoutReady)
                .filter(this::hasAcceptableIdentityLevel)
                .isPresent();
    }

    /**
     * The company's XTRM account number, without decrypting anything.
     *
     * <p>{@link #forCompany} would also answer this, but it decrypts the credential blob to do so. The
     * account number is an identifier, not a secret, and callers on listing paths ask for it once per row —
     * decrypting a company's credentials two hundred times to read a public value would be absurd.</p>
     */
    @Transactional(readOnly = true)
    public Optional<String> companyIssuerAccountNumber(UUID clientId, UUID partnerCompanyId) {
        return accountRepository.findByClientIdAndPartnerCompanyId(clientId, partnerCompanyId)
                .map(PartnerCompanyXtrmAccount::getXtrmAccountNumber);
    }

    /**
     * True when this account's KYC tier is one we are willing to pay from.
     *
     * <p>Folded into {@link #canPayFromOwnWallet} so there is one question, not two that could be asked in
     * different places and drift.</p>
     */
    public boolean hasAcceptableIdentityLevel(PartnerCompanyXtrmAccount account) {
        if (acceptableIdentityLevels == null || acceptableIdentityLevels.isBlank()) {
            return true;
        }
        String level = account.getAccountIdentityLevel();
        return level != null && Arrays.stream(acceptableIdentityLevels.split(","))
                .map(String::trim)
                .anyMatch(allowed -> allowed.equalsIgnoreCase(level));
    }

    /**
     * The company's own credentials.
     *
     * <p>Throws rather than quietly falling back to {@link #platform()}. A fallback would appear to succeed
     * while paying the seller out of the <em>client's</em> money instead of the company's — the money would
     * really move, from the wrong pocket, and nothing downstream would notice.</p>
     */
    @Transactional(readOnly = true)
    public XtrmCredentials forCompany(UUID clientId, UUID partnerCompanyId) {
        PartnerCompanyXtrmAccount account = accountRepository
                .findByClientIdAndPartnerCompanyId(clientId, partnerCompanyId)
                .filter(PartnerCompanyXtrmAccount::isPayoutReady)
                // Same identity gate the eligibility check applies. Enforced here too, because eligibility
                // is advice and this is the layer that actually hands over the authority to move money.
                .filter(this::hasAcceptableIdentityLevel)
                .orElseThrow(() -> new BusinessRuleException(NOT_CONNECTED_CODE,
                        "This company is not set up to pay from its own wallet yet."));

        Map<String, String> secrets = decrypt(account);
        String companyClientId = secrets.get(KEY_CLIENT_ID);
        String companyClientSecret = secrets.get(KEY_CLIENT_SECRET);
        if (isBlank(companyClientId) || isBlank(companyClientSecret)) {
            throw new BusinessRuleException(NOT_CONNECTED_CODE,
                    "This company's payout credentials are incomplete.");
        }

        return new XtrmCredentials(companyClientId, companyClientSecret,
                account.getXtrmAccountNumber(), account.getXtrmWalletId(), programId);
    }

    /**
     * Credentials for an account that is not {@code CONNECTED} yet.
     *
     * <p>Only for provisioning, where the row is deliberately still {@code PENDING} while we prove the
     * credentials work — the wallet id is not known at that point, so {@link #forCompany}'s readiness gate
     * would reject it.</p>
     *
     * <p><b>Never use this on a payout path.</b> {@code forCompany} refuses a not-ready company on purpose;
     * bypassing that is how a company payout silently becomes a platform payout, moving real money out of
     * the wrong pocket.</p>
     */
    public XtrmCredentials forCompanyUnchecked(PartnerCompanyXtrmAccount account) {
        Map<String, String> secrets = decrypt(account);
        String companyClientId = secrets.get(KEY_CLIENT_ID);
        String companyClientSecret = secrets.get(KEY_CLIENT_SECRET);
        if (isBlank(companyClientId) || isBlank(companyClientSecret)) {
            throw new BusinessRuleException(NOT_CONNECTED_CODE,
                    "This company's payout credentials are incomplete.");
        }
        return new XtrmCredentials(companyClientId, companyClientSecret,
                account.getXtrmAccountNumber(), account.getXtrmWalletId(), programId);
    }

    private Map<String, String> decrypt(PartnerCompanyXtrmAccount account) {
        try {
            return encryptionService.decrypt(account.getEncryptedCredentials());
        } catch (RuntimeException e) {
            // Log the company and the exception type only — never the blob, the key, or the message, any of
            // which could carry fragments of the secret.
            log.error("[step=xtrm_credentials_undecryptable] partnerCompanyId={} reason={}",
                    account.getPartnerCompanyId(), e.getClass().getSimpleName());
            throw new BusinessRuleException(NOT_CONNECTED_CODE,
                    "This company's payout credentials could not be read.");
        }
    }

    /** Encrypts a credential pair for storage — the only place these become a stored blob. */
    public String encryptCredentials(String clientId, String clientSecret) {
        return encryptionService.encrypt(Map.of(
                KEY_CLIENT_ID, clientId,
                KEY_CLIENT_SECRET, clientSecret));
    }

    public Optional<PartnerCompanyXtrmAccount> findAccount(UUID clientId, UUID partnerCompanyId) {
        return accountRepository.findByClientIdAndPartnerCompanyId(clientId, partnerCompanyId);
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}

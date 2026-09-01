package com.tenxengage.app.entity;

import com.tenxengage.app.entity.enums.XtrmAccountStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Filter;

import java.time.Instant;
import java.util.UUID;

/**
 * A partner company's identity and credentials at XTRM.
 *
 * <p>Exists because XTRM will not let one account spend another's balance: paying a seller from a partner
 * company's wallet must be authenticated <em>as that company</em>, with the pseudo credentials XTRM issues
 * for it. The platform credentials used everywhere else cannot do it — presenting them with a company's
 * wallet id is what returns {@code 400 Invalid wallet id}.</p>
 *
 * <p>A {@link XtrmAccountStatus#CONNECTED} row is what allows a company to use the XTRM-backed distribution
 * rails, so this row <em>is</em> the per-company enablement switch. That is deliberate: XTRM onboards each
 * company separately, so a single global flag could never be accurate.</p>
 */
@Entity
@Table(name = "partner_company_xtrm_accounts")
@Filter(name = "tenantFilter", condition = "client_id = :clientId")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class PartnerCompanyXtrmAccount extends BaseEntity implements TenantAware {

    @Column(name = "client_id", nullable = false)
    private UUID clientId;

    @Column(name = "partner_company_id", nullable = false)
    private UUID partnerCompanyId;

    /**
     * XTRM's SPN account number for this company. An identifier, not a secret.
     *
     * <p>Nullable because a row exists before it is known: the claim row is written inside the
     * company-create transaction, and a provisioning attempt that fails at {@code CreateBeneficiary} has no
     * SPN and still needs somewhere to record why.</p>
     */
    @Column(name = "xtrm_account_number", length = 50)
    private String xtrmAccountNumber;

    /**
     * The company's XTRM wallet that payouts draw from. An identifier, not a secret.
     *
     * <p>Nullable because {@code CreateBeneficiary} does not return it — it is discovered by a second call,
     * after the credentials have already been persisted.</p>
     */
    @Column(name = "xtrm_wallet_id", length = 50)
    private String xtrmWalletId;

    /** XTRM's KYC tier for this account, e.g. {@code Basic}. Stored for the identity-level gate and support. */
    @Column(name = "account_identity_level", length = 30)
    private String accountIdentityLevel;

    /**
     * The name actually sent as {@code BeneficiaryCompanyName}.
     *
     * <p>Not necessarily the company's name. Ours are unique per tenant and XTRM's namespace appears to be
     * global under the issuer account, so the name is disambiguated before sending. Without this column
     * nobody can match our row against XTRM's portal.</p>
     */
    @Column(name = "xtrm_beneficiary_name", length = 255)
    private String xtrmBeneficiaryName;

    /**
     * AES-GCM blob of {@code {clientId, clientSecret}}.
     *
     * <p><b>Never read this directly and never log it.</b> Go through the credential resolver, which decrypts
     * it and hands back a value object that does not expose the secret in {@code toString()}.</p>
     */
    @Column(name = "encrypted_credentials")
    private String encryptedCredentials;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    @Builder.Default
    private XtrmAccountStatus status = XtrmAccountStatus.PENDING;

    @Column(name = "connected_at")
    private Instant connectedAt;

    /** Why the last attempt to use these credentials failed, for support. Never contains the secret. */
    @Column(name = "last_error", length = 500)
    private String lastError;

    /**
     * True when this company may actually pay from its own wallet.
     *
     * <p>Checks all three, not just the credentials. Once the identifiers became nullable so that a
     * {@code PENDING} row could hold partial progress, "connected with credentials" stopped being enough:
     * a row missing an account number or a wallet is exactly as unpayable as one missing credentials, and
     * would fail just as late — at dispatch, after money is reserved.</p>
     */
    public boolean isPayoutReady() {
        return status == XtrmAccountStatus.CONNECTED
                && notBlank(encryptedCredentials)
                && notBlank(xtrmAccountNumber)
                && notBlank(xtrmWalletId);
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}

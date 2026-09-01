package com.tenxengage.app.service.xtrm;

import com.tenxengage.app.dto.request.xtrm.SaveRedemptionAddressRequest;
import com.tenxengage.app.entity.User;
import com.tenxengage.app.entity.enums.AuditAction;
import com.tenxengage.app.entity.enums.AuditResourceType;
import com.tenxengage.app.entity.enums.xtrm.RedemptionPayoutMethod;
import com.tenxengage.app.entity.enums.xtrm.XtrmEnrollmentStatus;
import com.tenxengage.app.entity.xtrm.PartnerAddress;
import com.tenxengage.app.entity.xtrm.PartnerRedemption;
import com.tenxengage.app.exception.BusinessRuleException;
import com.tenxengage.app.exception.ResourceNotFoundException;
import com.tenxengage.app.repository.UserRepository;
import com.tenxengage.app.repository.xtrm.PartnerRedemptionRepository;
import com.tenxengage.app.service.AuditLogService;
import com.tenxengage.app.service.xtrm.SellerEnrollmentIssuerResolver.EnrollmentIssuer;
import com.tenxengage.app.service.xtrm.XtrmApiClient.CreateUserCommand;
import com.tenxengage.app.service.xtrm.XtrmApiClient.CreateUserResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Owns XTRM enrollment ({@code CreateUser}) for payee users (F-03 enhancement).
 *
 * <p>Enrollment is idempotent and non-blocking: {@link #enrollIfNeeded(UUID)} is triggered eagerly at
 * profile completion and lazily before a payout ({@link #ensureEnrolledForPayout(UUID)}). A payee must
 * have {@code addressLine1} + {@code countryIso2} on their {@code partner_redemption} row before
 * {@code CreateUser} can succeed — until then enrollment is skipped cleanly.</p>
 *
 * <p><b>Transaction boundaries.</b> The XTRM HTTP call runs <b>outside</b> any {@code @Transactional}
 * scope; the load and the result-persist are separate Spring-Data repository operations (each its own
 * transaction). The tenant is resolved from the {@link User} entity (context-independent) so the same
 * code path works from a request thread <i>and</i> a backfill sweep with no security context.</p>
 */
@Service
public class XtrmEnrollmentService {

    private static final Logger log = LoggerFactory.getLogger(XtrmEnrollmentService.class);
    private static final int ENROLLMENT_ERROR_MAX = 500;

    private final PartnerRedemptionRepository userRedemptionRepository;
    private final UserRepository userRepository;
    private final XtrmApiClient xtrmApiClient;
    private final AuditLogService auditLogService;
    private final SellerEnrollmentIssuerResolver issuerResolver;

    public XtrmEnrollmentService(PartnerRedemptionRepository userRedemptionRepository,
                                 UserRepository userRepository,
                                 XtrmApiClient xtrmApiClient,
                                 AuditLogService auditLogService,
                                 SellerEnrollmentIssuerResolver issuerResolver) {
        this.userRedemptionRepository = userRedemptionRepository;
        this.userRepository = userRepository;
        this.xtrmApiClient = xtrmApiClient;
        this.auditLogService = auditLogService;
        this.issuerResolver = issuerResolver;
    }

    /**
     * Load the user's payout profile, creating a {@code NOT_ENROLLED} shell if absent. Safe under a
     * create-create race via the {@code uq_partner_redemption_user_id} unique index.
     */
    public PartnerRedemption getOrCreateProfile(UUID userId) {
        return getOrCreateProfile(loadUser(userId));
    }

    /**
     * Read-only profile view for {@code GET /profile}. Returns the persisted row if present, else a
     * transient default ({@code NOT_ENROLLED} / {@code ANYPAY}, not saved) so a first-time user gets a
     * sensible response without a write on a GET.
     */
    public PartnerRedemption getProfileView(UUID userId) {
        User user = loadUser(userId);
        return userRedemptionRepository.findByUserIdAndClientId(userId, user.getClientId())
                .orElseGet(() -> PartnerRedemption.builder()
                        .clientId(user.getClientId())
                        .userId(userId)
                        .enrollmentStatus(XtrmEnrollmentStatus.NOT_ENROLLED)
                        .payoutMethod(RedemptionPayoutMethod.ANYPAY)
                        .build());
    }

    PartnerRedemption getOrCreateProfile(User user) {
        UUID clientId = user.getClientId();
        UUID userId = user.getId();
        return userRedemptionRepository.findByUserIdAndClientId(userId, clientId)
                .orElseGet(() -> {
                    try {
                        return userRedemptionRepository.save(PartnerRedemption.builder()
                                .clientId(clientId)
                                .userId(userId)
                                .enrollmentStatus(XtrmEnrollmentStatus.NOT_ENROLLED)
                                .payoutMethod(RedemptionPayoutMethod.ANYPAY)
                                .build());
                    } catch (DataIntegrityViolationException race) {
                        // Concurrent creator won — re-read their row.
                        return userRedemptionRepository.findByUserIdAndClientId(userId, clientId)
                                .orElseThrow(() -> race);
                    }
                });
    }

    /**
     * Persist the payee's payout address to {@code partner_redemption}, then eager-enroll them in XTRM
     * (FR-01/FR-02). The address write is required before enrollment can succeed; enrollment is
     * non-blocking (a failure leaves the address saved and the status {@code FAILED} for later retry).
     * Callers must gate this to payee roles (the profile endpoint's redeem permission does).
     */
    public PartnerRedemption saveAddressAndEnroll(UUID userId, SaveRedemptionAddressRequest address) {
        User user = loadUser(userId);
        PartnerRedemption profile = getOrCreateProfile(user);
        profile.setAddress(PartnerAddress.builder()
                .line1(address.addressLine1())
                .line2(address.addressLine2())
                .city(address.city())
                .region(address.region())
                .postalCode(address.postalCode())
                .countryIso2(address.countryIso2())
                .build());
        userRedemptionRepository.save(profile);

        // Address is now present → attempt eager enrollment (non-blocking).
        enrollIfNeeded(user);

        return userRedemptionRepository.findByUserIdAndClientId(userId, user.getClientId()).orElse(profile);
    }

    /**
     * Enroll the user in XTRM if not already enrolled. Idempotent (no-op when {@code ENROLLED}) and
     * strictly non-blocking — any XTRM/transport failure is captured on the profile as {@code FAILED}
     * and never propagated to the caller (profile completion must still succeed, FR-10).
     */
    public void enrollIfNeeded(UUID userId) {
        enrollIfNeeded(loadUser(userId));
    }

    void enrollIfNeeded(User user) {
        PartnerRedemption profile = getOrCreateProfile(user);

        if (profile.getEnrollmentStatus() == XtrmEnrollmentStatus.ENROLLED
                && !isBlank(profile.getRecipientUserId())) {
            return; // idempotent no-op
        }
        PartnerAddress addr = profile.getAddress();
        if (addr == null || !addr.isEnrollable()) {
            log.info("[step=xtrm_enroll] userId={} skipped — missing address (line1/country required)", user.getId());
            return; // cannot enroll yet; stays NOT_ENROLLED / FAILED
        }

        // Which account creates this seller decides, permanently, who can pay them. XTRM refuses a second
        // user with the same email, so there is no correcting this later.
        EnrollmentIssuer issuer = issuerResolver.resolve(user.getClientId(), user.getPartnerCompanyId());
        if (issuer instanceof EnrollmentIssuer.Defer defer) {
            // Deliberately NOT markFailed: nothing failed, and a retry once the company connects will
            // succeed. FAILED would read as a problem with the seller and invite a manual "fix" — which
            // here would mean enrolling them under the wrong account, irreversibly.
            profile.setEnrollmentError(defer.reason());
            userRedemptionRepository.save(profile);
            log.info("[step=xtrm_enroll_deferred] userId={} reason=company_not_connected", user.getId());
            return;
        }
        XtrmCredentials credentials = ((EnrollmentIssuer.UseAccount) issuer).credentials();

        CreateUserResult result;
        try {
            result = xtrmApiClient.createUser(new CreateUserCommand(
                    user.getFirstName(), user.getLastName(), user.getEmail(),
                    user.getPhone(), user.getPhoneCountryIso2(),
                    addr.getLine1(), addr.getLine2(), addr.getCity(),
                    addr.getRegion(), addr.getPostalCode(), addr.getCountryIso2()), credentials);
        } catch (RuntimeException e) {
            // Defensive — the client contract is no-throw, but guarantee non-blocking regardless.
            markFailed(profile, "Enrollment error: " + e.getClass().getSimpleName());
            log.warn("[step=xtrm_enroll_failed] userId={} unexpected error: {}", user.getId(), e.getClass().getSimpleName());
            return;
        }

        if (result.success()) {
            profile.setRecipientUserId(result.recipientUserId());
            profile.setIdentityLevel(result.identityLevel());
            profile.setEnrolledIssuerAccountNumber(credentials.issuerAccountNumber());
            profile.setEnrollmentStatus(XtrmEnrollmentStatus.ENROLLED);
            profile.setEnrollmentError(null);
            profile.setEnrolledAt(Instant.now());
            userRedemptionRepository.save(profile);
            auditEnrolled(profile);
            log.info("[step=xtrm_enroll] userId={} enrollmentStatus=ENROLLED", user.getId());
        } else {
            markFailed(profile, String.join("; ", result.errors()));
            log.warn("[step=xtrm_enroll_failed] userId={} reason={}", user.getId(), truncate(String.join("; ", result.errors()), 120));
        }
    }

    /**
     * Return the user's XTRM recipient id (PAT) for a payout, lazily enrolling first if needed
     * (FR-09/FR-11). Throws {@code XTRM_NOT_ENROLLED} (422) when enrollment is not possible / still failing,
     * so the caller can hold the redemption and release the reserved balance.
     */
    public String ensureEnrolledForPayout(UUID userId) {
        User user = loadUser(userId);
        PartnerRedemption profile = getOrCreateProfile(user);
        if (profile.getEnrollmentStatus() == XtrmEnrollmentStatus.ENROLLED
                && !isBlank(profile.getRecipientUserId())) {
            return profile.getRecipientUserId();
        }

        enrollIfNeeded(user); // lazy attempt

        PartnerRedemption refreshed = userRedemptionRepository
                .findByUserIdAndClientId(user.getId(), user.getClientId())
                .orElseThrow(() -> new BusinessRuleException("XTRM_NOT_ENROLLED",
                        "This account isn't set up for payouts yet."));
        if (refreshed.getEnrollmentStatus() == XtrmEnrollmentStatus.ENROLLED
                && !isBlank(refreshed.getRecipientUserId())) {
            return refreshed.getRecipientUserId();
        }
        throw new BusinessRuleException("XTRM_NOT_ENROLLED",
                "This account isn't set up for payouts yet. Complete your payout profile to continue.");
    }

    /**
     * Backfill sweep for pre-existing users (FR-11): retry a page of {@code NOT_ENROLLED} / {@code FAILED}
     * profiles for one tenant. Per-tenant by design — never sweeps across clients. Returns the number of
     * profiles attempted this page.
     */
    public int backfillEnrollments(UUID clientId, XtrmEnrollmentStatus status, int limit) {
        Page<PartnerRedemption> page = userRedemptionRepository.findByClientIdAndEnrollmentStatus(
                clientId, status, PageRequest.of(0, limit));
        int attempted = 0;
        for (PartnerRedemption profile : page.getContent()) {
            userRepository.findById(profile.getUserId()).ifPresent(user -> enrollIfNeeded(user));
            attempted++;
        }
        log.info("[step=xtrm_enroll_backfill] clientId={} status={} attempted={}", clientId, status, attempted);
        return attempted;
    }

    // ---------------------------------------------------------------------

    private void markFailed(PartnerRedemption profile, String reason) {
        profile.setEnrollmentStatus(XtrmEnrollmentStatus.FAILED);
        profile.setEnrollmentError(truncate(reason, ENROLLMENT_ERROR_MAX));
        userRedemptionRepository.save(profile);
    }

    private void auditEnrolled(PartnerRedemption profile) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("userId", profile.getUserId().toString());
        metadata.put("enrollmentStatus", XtrmEnrollmentStatus.ENROLLED.name());
        if (!isBlank(profile.getIdentityLevel())) {
            metadata.put("identityLevel", profile.getIdentityLevel());
        }
        // No PII / PAT / bank refs in the audit payload.
        auditLogService.logAsync(AuditAction.ENROLLED, AuditResourceType.PARTNER_REDEMPTION,
                profile.getId(), null, "User enrolled in XTRM", metadata);
    }

    private User loadUser(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return null;
        }
        return s.length() <= max ? s : s.substring(0, max);
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}

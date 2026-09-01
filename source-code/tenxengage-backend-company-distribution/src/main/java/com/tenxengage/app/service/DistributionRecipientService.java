package com.tenxengage.app.service;

import com.tenxengage.app.dto.response.DistributionRecipientResponse;
import com.tenxengage.app.entity.User;
import com.tenxengage.app.entity.enums.DistributionRail;
import com.tenxengage.app.entity.enums.xtrm.XtrmEnrollmentStatus;
import com.tenxengage.app.entity.xtrm.PartnerLinkedBank;
import com.tenxengage.app.entity.xtrm.PartnerRedemption;
import com.tenxengage.app.exception.BusinessRuleException;
import com.tenxengage.app.repository.UserRepository;
import com.tenxengage.app.repository.xtrm.PartnerLinkedBankRepository;
import com.tenxengage.app.repository.xtrm.PartnerRedemptionRepository;
import com.tenxengage.app.service.xtrm.XtrmCredentialsResolver;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Who can receive a distribution, and whether each rail can actually reach them.
 *
 * <p>Recipients are active {@code PARTNER_SELLER}s of the caller's company (OQ-14). Ineligible sellers are
 * returned <b>with a reason</b> rather than hidden, so the admin knows who cannot be paid and why instead
 * of wondering who is missing.</p>
 *
 * <p>Since the wallet rail was retired (2026-08-26) those reasons name no remedy, because there is none: a
 * seller who is platform-bound at XTRM, or not yet enrolled, cannot be reached by either remaining rail.
 * Naming a rail that no longer exists would be worse than naming none.</p>
 *
 * <p>Unlike personal dispatch, distribution never lazily enrols anyone. The admin picks from a pre-filtered
 * list, so enrolment is a precondition here, not a side effect.</p>
 */
@Service
public class DistributionRecipientService {

    /**
     * Copy shown when the XTRM-backed rails are switched off.
     *
     * <p>Says "temporarily" and stops there. It used to name the wallet rail as the thing that still worked;
     * with that rail retired there is nothing to point at, and both remaining rails need XTRM — so this
     * state now means distribution is off entirely.</p>
     */
    static final String RAIL_UNAVAILABLE =
            "Temporarily unavailable";

    private final UserRepository userRepository;
    private final PartnerRedemptionRepository profileRepository;
    private final PartnerLinkedBankRepository linkedBankRepository;

    /**
     * Kill switch for the two rails that need XTRM.
     *
     * <p><b>The reason this existed is gone.</b> It was switched off because company→seller payouts were
     * believed impossible — {@code TransferFund} sources from the issuer's own wallet, so a company could
     * not fund a payout. That turned out to be an authentication problem, not a missing capability: a seller
     * created under their own company's credentials is bound to that company, and the company pays them with
     * its own token. Verified against the sandbox on 2026-08-26.</p>
     *
     * <p>It is kept as a switch rather than deleted, because switching payouts off without a deploy is worth
     * having. But it is now <b>all or nothing</b>: the wallet rail that used to carry distribution while
     * this was off has been retired, so turning it off disables distribution entirely rather than falling
     * back to an internal rail.</p>
     *
     * <p>Enforced on <b>submit only</b> ({@link #assertAllEligible}), not on the listing: a partner admin can
     * still open those rails and see which of their sellers would be reachable, which is real information
     * while they wait. Flip {@code XTRM_PAYOUT_RAILS_ENABLED=true} when XTRM is ready; no code change is
     * needed. The frontend has a matching flag that greys out its send button, but the server does not trust
     * it — this is the gate that actually holds.</p>
     */
    private final boolean xtrmPayoutRailsEnabled;

    private final XtrmCredentialsResolver credentialsResolver;

    public DistributionRecipientService(UserRepository userRepository,
                                         PartnerRedemptionRepository profileRepository,
                                         PartnerLinkedBankRepository linkedBankRepository,
                                         XtrmCredentialsResolver credentialsResolver,
                                         @Value("${redemption.distribution.xtrm-payout-rails-enabled:false}")
                                         boolean xtrmPayoutRailsEnabled) {
        this.userRepository = userRepository;
        this.profileRepository = profileRepository;
        this.linkedBankRepository = linkedBankRepository;
        this.credentialsResolver = credentialsResolver;
        this.xtrmPayoutRailsEnabled = xtrmPayoutRailsEnabled;
    }

    /** Every active seller of the company, each annotated with whether this rail can reach them. */
    @Transactional(readOnly = true)
    public List<DistributionRecipientResponse> listRecipients(UUID clientId, UUID companyId, DistributionRail rail) {
        List<User> sellers = userRepository.findActiveSellersOfCompany(clientId, companyId);
        Map<UUID, PartnerRedemption> profiles = profilesByUser(clientId, sellers);

        List<DistributionRecipientResponse> out = new ArrayList<>(sellers.size());
        for (User u : sellers) {
            Eligibility e = evaluate(clientId, companyId, u, profiles.get(u.getId()), rail);
            out.add(new DistributionRecipientResponse(
                    u.getId(),
                    displayName(u),
                    u.getEmail(),
                    e.eligible(),
                    e.reason(),
                    e.destination()));
        }
        return out;
    }

    /**
     * Gate for submit. Re-checks server-side rather than trusting the ids the client posted, and names the
     * first offender so the admin can act instead of retrying blindly.
     */
    @Transactional(readOnly = true)
    public void assertAllEligible(UUID clientId, UUID companyId, List<UUID> recipientIds, DistributionRail rail) {
        // Rail availability is checked here and not in evaluate(), so the recipient LISTING keeps reporting
        // each seller's real payout readiness. An admin can then browse the gift-card and bank rails and see
        // who would be reachable once XTRM ships, instead of every row reading "temporarily unavailable" and
        // telling them nothing. Sending is what must be refused, and this is the only path that sends.
        if (!railAvailable(rail)) {
            throw new BusinessRuleException("RAIL_UNAVAILABLE", RAIL_UNAVAILABLE);
        }

        Map<UUID, User> sellers = userRepository.findActiveSellersOfCompany(clientId, companyId).stream()
                .collect(Collectors.toMap(User::getId, Function.identity(), (a, b) -> a));

        for (UUID id : recipientIds) {
            User u = sellers.get(id);
            if (u == null) {
                // Covers all of: not in this company, not a seller, and not active — deliberately one
                // message, so the endpoint cannot be used to probe who exists in another company.
                throw new BusinessRuleException("RECIPIENT_NOT_ELIGIBLE",
                        "One or more selected recipients are not active sellers of your company.");
            }
        }

        Map<UUID, PartnerRedemption> profiles = profilesByUser(clientId,
                recipientIds.stream().map(sellers::get).toList());

        for (UUID id : recipientIds) {
            Eligibility e = evaluate(clientId, companyId, sellers.get(id), profiles.get(id), rail);
            if (!e.eligible()) {
                throw new BusinessRuleException("RECIPIENT_NOT_ELIGIBLE",
                        displayName(sellers.get(id)) + ": " + e.reason());
            }
        }
    }

    /** False while the rail's vendor cannot actually be called. {@code WALLET_CREDIT} needs no vendor. */
    private boolean railAvailable(DistributionRail rail) {
        return xtrmPayoutRailsEnabled
                || (rail != DistributionRail.GIFT_CARD && rail != DistributionRail.BANK_TRANSFER);
    }

    /** Per-rail readiness. Each rail needs a different thing, and says so in the recipient's own terms. */
    private Eligibility evaluate(UUID clientId, UUID companyId, User user, PartnerRedemption profile,
                                 DistributionRail rail) {
        // A company that has not finished XTRM onboarding cannot pay anyone from its own wallet. This is a
        // property of the company rather than the seller, but it is reported per-seller so it shows up on
        // the LISTING, where an admin can act on it — not only at submit, after they have built the whole
        // distribution. WALLET_CREDIT is exempt: it calls no vendor.
        //
        // Deliberately takes precedence over the per-seller reasons below. When the company is not
        // connected, fixing a seller's payout profile changes nothing — so naming the company is the more
        // actionable answer. Once connected, the seller-specific reason surfaces as before.
        if (rail.isVendorPayout() && !credentialsResolver.canPayFromOwnWallet(clientId, companyId)) {
            return Eligibility.no("Your company isn't connected to XTRM yet");
        }
        // Even with the company connected, XTRM will only let it pay sellers it created itself. Sellers
        // enrolled before company-scoped enrollment existed are bound to the platform and cannot be
        // re-enrolled — XTRM refuses a second user with the same email. Refusing here is the only honest
        // option; the alternative reserves the seller's share and fails at the vendor.
        //
        // Only for a seller who IS enrolled. An un-enrolled seller falls through to the per-rail checks
        // below, which say "no payout profile yet" — the right message, because that resolves itself the
        // moment they enrol under their company. Saying "cannot receive" there would imply a permanence
        // that does not apply to them.
        if (rail.isVendorPayout()
                && profile != null
                && profile.getEnrollmentStatus() == XtrmEnrollmentStatus.ENROLLED) {
            String companyIssuer = credentialsResolver
                    .companyIssuerAccountNumber(clientId, companyId).orElse(null);
            if (!profile.isEnrolledUnder(companyIssuer)) {
                return Eligibility.no(
                        "This seller cannot receive company payouts");
            }
        }
        switch (rail) {
            case GIFT_CARD -> {
                if (profile == null || profile.getEnrollmentStatus() != XtrmEnrollmentStatus.ENROLLED
                        || isBlank(profile.getRecipientUserId())) {
                    return Eligibility.no("No payout profile yet");
                }
                if (isBlank(user.getEmail())) {
                    return Eligibility.no("No email on file to deliver the gift card");
                }
                return Eligibility.yes(user.getEmail());
            }
            case BANK_TRANSFER -> {
                if (profile == null || profile.getEnrollmentStatus() != XtrmEnrollmentStatus.ENROLLED) {
                    return Eligibility.no("No payout profile yet");
                }
                Optional<PartnerLinkedBank> bank = defaultBank(clientId, user.getId(), profile);
                return bank.map(b -> Eligibility.yes(b.getMaskedLabel()))
                        .orElseGet(() -> Eligibility.no("No bank account linked"));
            }
            case WALLET_CREDIT -> {
                // Retired 2026-08-26. Existing distributions on this rail still settle and still display;
                // it is simply no longer offered as a destination for new ones.
                return Eligibility.no("Wallet transfer is no longer available");
            }
            default -> {
                return Eligibility.no("Unsupported rail");
            }
        }
    }

    /**
     * The bank a distribution would pay: the profile's default when it resolves, else the oldest linked bank
     * (the same deterministic auto-promote order the profile itself uses).
     */
    private Optional<PartnerLinkedBank> defaultBank(UUID clientId, UUID userId, PartnerRedemption profile) {
        List<PartnerLinkedBank> banks = linkedBankRepository
                .findByUserIdAndClientIdOrderByCreatedAtAsc(userId, clientId);
        if (banks.isEmpty()) {
            return Optional.empty();
        }
        String defaultBeneficiary = profile == null ? null : profile.getPartnerLinkedBankId();
        return banks.stream()
                .filter(b -> defaultBeneficiary != null && defaultBeneficiary.equals(b.getXtrmBeneficiaryId()))
                .findFirst()
                .or(() -> Optional.of(banks.get(0)));
    }

    private Map<UUID, PartnerRedemption> profilesByUser(UUID clientId, List<User> users) {
        Map<UUID, PartnerRedemption> out = new java.util.HashMap<>();
        for (User u : users) {
            if (u == null) {
                continue;
            }
            profileRepository.findByUserIdAndClientId(u.getId(), clientId)
                    .ifPresent(p -> out.put(u.getId(), p));
        }
        return out;
    }

    private static String displayName(User u) {
        String first = u.getFirstName() == null ? "" : u.getFirstName();
        String last = u.getLastName() == null ? "" : u.getLastName();
        String name = (first + " " + last).trim();
        return name.isEmpty() ? u.getEmail() : name;
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private record Eligibility(boolean eligible, String reason, String destination) {
        static Eligibility yes(String destination) {
            return new Eligibility(true, null, destination);
        }
        static Eligibility no(String reason) {
            return new Eligibility(false, reason, null);
        }
    }
}

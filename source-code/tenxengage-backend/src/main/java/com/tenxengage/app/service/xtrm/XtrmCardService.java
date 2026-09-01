package com.tenxengage.app.service.xtrm;

import com.tenxengage.app.dto.request.xtrm.AddCardRequest;
import com.tenxengage.app.dto.response.xtrm.LinkedCardResponse;
import com.tenxengage.app.entity.enums.xtrm.RedemptionPayoutMethod;
import com.tenxengage.app.entity.xtrm.PartnerLinkedCard;
import com.tenxengage.app.entity.xtrm.PartnerRedemption;
import com.tenxengage.app.exception.BusinessRuleException;
import com.tenxengage.app.exception.ExternalServiceException;
import com.tenxengage.app.exception.ResourceNotFoundException;
import com.tenxengage.app.repository.xtrm.PartnerLinkedCardRepository;
import com.tenxengage.app.repository.xtrm.PartnerRedemptionRepository;
import com.tenxengage.app.service.xtrm.XtrmApiClient.DeleteCardCommand;
import com.tenxengage.app.service.xtrm.XtrmApiClient.DeleteCardResult;
import com.tenxengage.app.service.xtrm.XtrmApiClient.LinkCardCommand;
import com.tenxengage.app.service.xtrm.XtrmApiClient.LinkCardResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * Manages the current user's XTRM linked cards (F-03 multi-card enhancement). A card is DUAL-PURPOSE: a
 * payout rail ({@code TransferFund} + {@code CardToken}) AND a withdrawal destination. Mirrors
 * {@link XtrmBankService}: a user may link many cards (each a {@link PartnerLinkedCard} row); the Payout tab
 * lists them from a fast DB read; the <b>default</b> card lives on
 * {@code partner_redemption.partner_linked_card_id} (the XTRM {@code CardToken}, one value → one default free).
 *
 * <p>⚠️ <b>PCI:</b> the raw card in {@link AddCardRequest} is pass-through to XTRM {@code LinkCard} and is
 * NEVER persisted or logged. Only the returned {@code CardToken} + masked last-4 are stored. XTRM calls run
 * outside a transaction; the result is persisted afterward. All operations are self-only + tenant-scoped.</p>
 */
@Service
public class XtrmCardService {

    private static final Logger log = LoggerFactory.getLogger(XtrmCardService.class);
    private static final int LABEL_MAX = 100;

    private final PartnerRedemptionRepository userRedemptionRepository;
    private final PartnerLinkedCardRepository linkedCardRepository;
    private final XtrmApiClient xtrmApiClient;
    private final XtrmEnrollmentService enrollmentService;

    public XtrmCardService(PartnerRedemptionRepository userRedemptionRepository,
                           PartnerLinkedCardRepository linkedCardRepository,
                           XtrmApiClient xtrmApiClient,
                           XtrmEnrollmentService enrollmentService) {
        this.userRedemptionRepository = userRedemptionRepository;
        this.linkedCardRepository = linkedCardRepository;
        this.xtrmApiClient = xtrmApiClient;
        this.enrollmentService = enrollmentService;
    }

    /**
     * Link a card for the user (append — never replaces). Requires XTRM enrollment (a PAT) — lazily enrolls,
     * else throws {@code XTRM_NOT_ENROLLED} (422). Tokenizes at XTRM ({@code LinkCard}), inserts a local
     * {@link PartnerLinkedCard} row (token + masked last-4 only), and makes it the default only if the user
     * has none yet. Transient XTRM outages surface as 503; domain rejections as 422. Raw card never stored.
     */
    public PartnerRedemption addCard(UUID userId, AddCardRequest request) {
        String recipientUserId = enrollmentService.ensureEnrolledForPayout(userId);
        PartnerRedemption profile = enrollmentService.getOrCreateProfile(userId);

        // ⚠️ PCI: the raw card is forwarded once here and never persisted/logged.
        LinkCardResult result = xtrmApiClient.linkCard(new LinkCardCommand(
                recipientUserId,
                request.cardNumber(), request.expMonth(), request.expYear(), request.cvv(),
                request.cardType(), request.nameOnCard(),
                request.firstName(), request.lastName(),
                request.addressLine1(), request.addressLine2(), request.city(),
                request.region(), request.postalCode(), request.countryIso2()));

        if (!result.success()) {
            if (result.retryable()) {
                throw new ExternalServiceException("XTRM_UNAVAILABLE",
                        "Payouts are temporarily unavailable. Please try again shortly.");
            }
            throw new BusinessRuleException("XTRM_CARD_LINK_FAILED",
                    "We couldn't link that card. Please check the details and try again.");
        }

        String cardToken = result.cardToken();
        String last4 = request.last4();
        String label = maskLabel(request.cardType(), last4);

        try {
            linkedCardRepository.save(PartnerLinkedCard.builder()
                    .clientId(profile.getClientId())
                    .userId(userId)
                    .cardToken(cardToken)
                    .maskedLast4(last4)
                    .cardType(request.cardType())
                    .status(result.cardStatus())
                    .build());
        } catch (DataIntegrityViolationException dup) {
            throw new BusinessRuleException("XTRM_CARD_DUPLICATE", "This card is already linked.");
        }

        // Append-not-replace: the first card becomes the default; an existing default is never overwritten.
        if (isBlank(profile.getPartnerLinkedCardId())) {
            profile.setPartnerLinkedCardId(cardToken);
            profile.setLinkedCardLabel(label);
            profile = userRedemptionRepository.save(profile);
        }
        log.info("[step=xtrm_card_linked] userId={}", userId); // no card number
        return profile;
    }

    /**
     * List the user's linked cards (fast local read — no XTRM call). Marks the default by matching the token
     * pointer on {@code partner_redemption}. Read-only: never writes a profile shell on this GET.
     */
    public List<LinkedCardResponse> listCards(UUID userId) {
        PartnerRedemption profile = enrollmentService.getProfileView(userId);
        UUID clientId = profile.getClientId();
        String defaultCardToken = profile.getPartnerLinkedCardId();
        return linkedCardRepository.findByUserIdAndClientIdOrderByCreatedAtAsc(userId, clientId).stream()
                .map(card -> LinkedCardResponse.of(card, card.getCardToken().equals(defaultCardToken)))
                .toList();
    }

    /**
     * Remove a linked card (by our row PK). Hard-deletes it at XTRM ({@code DeleteCard}) then soft-deletes the
     * local row. A transient XTRM failure keeps the row + surfaces a 503; a non-retryable rejection (already
     * gone at XTRM) is idempotent — we still soft-delete locally. If the removed card was the default:
     * auto-promote the oldest remaining card, or (none left) clear the default and reset a CARD rail to ANYPAY.
     */
    public PartnerRedemption removeCard(UUID userId, UUID cardId) {
        PartnerRedemption profile = enrollmentService.getOrCreateProfile(userId);
        UUID clientId = profile.getClientId();

        PartnerLinkedCard card = linkedCardRepository.findByIdAndUserIdAndClientId(cardId, userId, clientId)
                .orElseThrow(() -> new ResourceNotFoundException("LinkedCard", "id", cardId));

        DeleteCardResult result = xtrmApiClient.deleteCard(
                new DeleteCardCommand(profile.getRecipientUserId(), card.getCardToken()));
        if (!result.success() && result.retryable()) {
            throw new ExternalServiceException("XTRM_UNAVAILABLE",
                    "Payouts are temporarily unavailable. Please try again shortly.");
        }
        if (!result.success()) {
            log.warn("[step=xtrm_card_unlink] userId={} XTRM delete non-fatal, soft-deleting locally", userId);
        }

        card.setDeleted(true);
        linkedCardRepository.save(card);

        // Re-point the default if we just removed it (has cards <=> has default).
        if (card.getCardToken().equals(profile.getPartnerLinkedCardId())) {
            List<PartnerLinkedCard> remaining =
                    linkedCardRepository.findByUserIdAndClientIdOrderByCreatedAtAsc(userId, clientId);
            if (!remaining.isEmpty()) {
                PartnerLinkedCard promoted = remaining.get(0); // oldest — deterministic
                profile.setPartnerLinkedCardId(promoted.getCardToken());
                profile.setLinkedCardLabel(maskLabel(promoted.getCardType(), promoted.getMaskedLast4()));
            } else {
                profile.setPartnerLinkedCardId(null);
                profile.setLinkedCardLabel(null);
                if (profile.getPayoutMethod() == RedemptionPayoutMethod.CARD) {
                    profile.setPayoutMethod(RedemptionPayoutMethod.ANYPAY);
                }
            }
            profile = userRedemptionRepository.save(profile);
        }
        log.info("[step=xtrm_card_unlinked] userId={}", userId);
        return profile;
    }

    /**
     * Set the default card (by our row PK) — the destination for the CARD rail. No XTRM call: the default is
     * the {@code CardToken} pointer on {@code partner_redemption}.
     */
    public PartnerRedemption setDefaultCard(UUID userId, UUID cardId) {
        PartnerRedemption profile = enrollmentService.getOrCreateProfile(userId);
        PartnerLinkedCard card = linkedCardRepository
                .findByIdAndUserIdAndClientId(cardId, userId, profile.getClientId())
                .orElseThrow(() -> new ResourceNotFoundException("LinkedCard", "id", cardId));
        profile.setPartnerLinkedCardId(card.getCardToken());
        profile.setLinkedCardLabel(maskLabel(card.getCardType(), card.getMaskedLast4()));
        PartnerRedemption saved = userRedemptionRepository.save(profile);
        log.info("[step=xtrm_default_card_set] userId={}", userId);
        return saved;
    }

    // ---------------------------------------------------------------------

    /** Build a masked, display-only label like {@code "Visa ••1111"}; never contains the full number. */
    private static String maskLabel(String cardType, String last4) {
        String type = isBlank(cardType) ? "Card" : cardType.trim();
        String label = isBlank(last4) ? type : type + " ••" + last4;
        return label.length() > LABEL_MAX ? label.substring(0, LABEL_MAX) : label;
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}

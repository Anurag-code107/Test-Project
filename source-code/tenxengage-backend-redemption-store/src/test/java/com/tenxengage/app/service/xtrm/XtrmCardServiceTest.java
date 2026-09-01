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
import com.tenxengage.app.testdata.xtrm.PartnerRedemptionFixtures;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class XtrmCardServiceTest {

    @Mock
    private PartnerRedemptionRepository userRedemptionRepository;
    @Mock
    private PartnerLinkedCardRepository linkedCardRepository;
    @Mock
    private XtrmApiClient xtrmApiClient;
    @Mock
    private XtrmEnrollmentService enrollmentService;

    private static final UUID CLIENT_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();

    private XtrmCardService service() {
        return new XtrmCardService(userRedemptionRepository, linkedCardRepository, xtrmApiClient, enrollmentService);
    }

    private AddCardRequest request() {
        return new AddCardRequest(
                "4111111111111111", "12", "2029", "123", "Visa", "Ada Lovelace",
                "Ada", "Lovelace", "123 Main St", null, "Los Angeles", "CA", "90001", "US");
    }

    private PartnerLinkedCard card(UUID id, String token, String last4, String type) {
        PartnerLinkedCard c = PartnerLinkedCard.builder()
                .clientId(CLIENT_ID).userId(USER_ID)
                .cardToken(token).maskedLast4(last4).cardType(type).status("Active").build();
        c.setId(id);
        return c;
    }

    // ---- addCard ----

    @Test
    void addCard_firstCard_insertsRowAndBecomesDefault() {
        PartnerRedemption profile = PartnerRedemptionFixtures.enrolled(CLIENT_ID, USER_ID, "PAT-1").build();
        when(enrollmentService.ensureEnrolledForPayout(USER_ID)).thenReturn("PAT-1");
        when(enrollmentService.getOrCreateProfile(USER_ID)).thenReturn(profile);
        when(xtrmApiClient.linkCard(any(LinkCardCommand.class)))
                .thenReturn(LinkCardResult.ok("CARD-TOK-1", "Active"));
        when(userRedemptionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        PartnerRedemption result = service().addCard(USER_ID, request());

        // First card → becomes the default pointer on partner_redemption (holds the CardToken, not the PAN).
        assertThat(result.getPartnerLinkedCardId()).isEqualTo("CARD-TOK-1");
        assertThat(result.getLinkedCardLabel()).isEqualTo("Visa ••1111");

        // A local row is persisted with ONLY the token + masked last-4 — never the full number/CVV.
        ArgumentCaptor<PartnerLinkedCard> row = ArgumentCaptor.forClass(PartnerLinkedCard.class);
        verify(linkedCardRepository).save(row.capture());
        assertThat(row.getValue().getCardToken()).isEqualTo("CARD-TOK-1");
        assertThat(row.getValue().getMaskedLast4()).isEqualTo("1111");
        assertThat(row.getValue().getCardType()).isEqualTo("Visa");
        assertThat(row.getValue().getStatus()).isEqualTo("Active");
        // Defensive PCI assertion: the persisted row never contains the raw PAN.
        assertThat(row.getValue().getCardToken()).doesNotContain("4111111111111111");
    }

    @Test
    void addCard_secondCard_appendsWithoutOverwritingDefault() {
        PartnerRedemption profile = PartnerRedemptionFixtures.enrolled(CLIENT_ID, USER_ID, "PAT-1")
                .partnerLinkedCardId("EXISTING-TOK").linkedCardLabel("MC ••2222").build();
        when(enrollmentService.ensureEnrolledForPayout(USER_ID)).thenReturn("PAT-1");
        when(enrollmentService.getOrCreateProfile(USER_ID)).thenReturn(profile);
        when(xtrmApiClient.linkCard(any(LinkCardCommand.class)))
                .thenReturn(LinkCardResult.ok("CARD-TOK-2", "Active"));

        PartnerRedemption result = service().addCard(USER_ID, request());

        // The existing default is untouched (append-not-replace)...
        assertThat(result.getPartnerLinkedCardId()).isEqualTo("EXISTING-TOK");
        // ...the new card is still persisted, but the profile default is not re-saved.
        verify(linkedCardRepository).save(any(PartnerLinkedCard.class));
        verify(userRedemptionRepository, never()).save(any());
    }

    @Test
    void addCard_xtrmTransientOutage_throws503AndInsertsNoRow() {
        PartnerRedemption profile = PartnerRedemptionFixtures.enrolled(CLIENT_ID, USER_ID, "PAT-1").build();
        when(enrollmentService.ensureEnrolledForPayout(USER_ID)).thenReturn("PAT-1");
        when(enrollmentService.getOrCreateProfile(USER_ID)).thenReturn(profile);
        when(xtrmApiClient.linkCard(any(LinkCardCommand.class)))
                .thenReturn(LinkCardResult.failed(List.of("Could not reach XTRM"), true));

        assertThatThrownBy(() -> service().addCard(USER_ID, request()))
                .isInstanceOf(ExternalServiceException.class)
                .hasFieldOrPropertyWithValue("errorCode", "XTRM_UNAVAILABLE");

        verify(linkedCardRepository, never()).save(any());
        verify(userRedemptionRepository, never()).save(any());
    }

    @Test
    void addCard_nonRetryableFailure_throws422CardLinkFailed() {
        PartnerRedemption profile = PartnerRedemptionFixtures.enrolled(CLIENT_ID, USER_ID, "PAT-1").build();
        when(enrollmentService.ensureEnrolledForPayout(USER_ID)).thenReturn("PAT-1");
        when(enrollmentService.getOrCreateProfile(USER_ID)).thenReturn(profile);
        when(xtrmApiClient.linkCard(any(LinkCardCommand.class)))
                .thenReturn(LinkCardResult.failed(List.of("Card declined"), false));

        assertThatThrownBy(() -> service().addCard(USER_ID, request()))
                .isInstanceOf(BusinessRuleException.class)
                .hasFieldOrPropertyWithValue("errorCode", "XTRM_CARD_LINK_FAILED");

        verify(linkedCardRepository, never()).save(any());
    }

    @Test
    void addCard_localUniqueViolation_throws422Duplicate() {
        PartnerRedemption profile = PartnerRedemptionFixtures.enrolled(CLIENT_ID, USER_ID, "PAT-1").build();
        when(enrollmentService.ensureEnrolledForPayout(USER_ID)).thenReturn("PAT-1");
        when(enrollmentService.getOrCreateProfile(USER_ID)).thenReturn(profile);
        when(xtrmApiClient.linkCard(any(LinkCardCommand.class)))
                .thenReturn(LinkCardResult.ok("CARD-TOK-1", "Active"));
        when(linkedCardRepository.save(any())).thenThrow(new DataIntegrityViolationException("dup"));

        assertThatThrownBy(() -> service().addCard(USER_ID, request()))
                .isInstanceOf(BusinessRuleException.class)
                .hasFieldOrPropertyWithValue("errorCode", "XTRM_CARD_DUPLICATE");

        verify(userRedemptionRepository, never()).save(any());
    }

    @Test
    void addCard_notEnrolled_propagatesAndSkipsXtrmCall() {
        when(enrollmentService.ensureEnrolledForPayout(USER_ID))
                .thenThrow(new BusinessRuleException("XTRM_NOT_ENROLLED", "not set up"));

        assertThatThrownBy(() -> service().addCard(USER_ID, request()))
                .isInstanceOf(BusinessRuleException.class)
                .hasFieldOrPropertyWithValue("errorCode", "XTRM_NOT_ENROLLED");

        verifyNoInteractions(xtrmApiClient, linkedCardRepository);
        verify(userRedemptionRepository, never()).save(any());
    }

    // ---- listCards ----

    @Test
    void listCards_marksTheDefaultCard() {
        PartnerRedemption profile = PartnerRedemptionFixtures.enrolled(CLIENT_ID, USER_ID, "PAT-1")
                .partnerLinkedCardId("TOK-2").build();
        when(enrollmentService.getProfileView(USER_ID)).thenReturn(profile);
        when(linkedCardRepository.findByUserIdAndClientIdOrderByCreatedAtAsc(USER_ID, CLIENT_ID))
                .thenReturn(List.of(
                        card(UUID.randomUUID(), "TOK-1", "1111", "Visa"),
                        card(UUID.randomUUID(), "TOK-2", "2222", "Mastercard")));

        List<LinkedCardResponse> cards = service().listCards(USER_ID);

        assertThat(cards).hasSize(2);
        assertThat(cards.get(0).label()).isEqualTo("Visa ••1111");
        assertThat(cards.get(0).isDefault()).isFalse();
        assertThat(cards.get(1).label()).isEqualTo("Mastercard ••2222");
        assertThat(cards.get(1).isDefault()).isTrue();
    }

    // ---- removeCard ----

    @Test
    void removeCard_default_softDeletesAndAutoPromotesOldestRemaining() {
        UUID id1 = UUID.randomUUID();
        PartnerRedemption profile = PartnerRedemptionFixtures.enrolled(CLIENT_ID, USER_ID, "PAT-1")
                .partnerLinkedCardId("TOK-1").linkedCardLabel("Visa ••1111")
                .payoutMethod(RedemptionPayoutMethod.CARD).build();
        PartnerLinkedCard removed = card(id1, "TOK-1", "1111", "Visa");
        when(enrollmentService.getOrCreateProfile(USER_ID)).thenReturn(profile);
        when(linkedCardRepository.findByIdAndUserIdAndClientId(id1, USER_ID, CLIENT_ID))
                .thenReturn(Optional.of(removed));
        when(xtrmApiClient.deleteCard(any(DeleteCardCommand.class))).thenReturn(DeleteCardResult.ok());
        when(linkedCardRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(linkedCardRepository.findByUserIdAndClientIdOrderByCreatedAtAsc(USER_ID, CLIENT_ID))
                .thenReturn(List.of(card(UUID.randomUUID(), "TOK-2", "2222", "Mastercard")));
        when(userRedemptionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        PartnerRedemption result = service().removeCard(USER_ID, id1);

        assertThat(removed.isDeleted()).isTrue();
        assertThat(result.getPartnerLinkedCardId()).isEqualTo("TOK-2");
        assertThat(result.getLinkedCardLabel()).isEqualTo("Mastercard ••2222");
        assertThat(result.getPayoutMethod()).isEqualTo(RedemptionPayoutMethod.CARD);
    }

    @Test
    void removeCard_lastCard_clearsDefaultAndResetsRailToAnypay() {
        UUID id1 = UUID.randomUUID();
        PartnerRedemption profile = PartnerRedemptionFixtures.enrolled(CLIENT_ID, USER_ID, "PAT-1")
                .partnerLinkedCardId("TOK-1").linkedCardLabel("Visa ••1111")
                .payoutMethod(RedemptionPayoutMethod.CARD).build();
        PartnerLinkedCard removed = card(id1, "TOK-1", "1111", "Visa");
        when(enrollmentService.getOrCreateProfile(USER_ID)).thenReturn(profile);
        when(linkedCardRepository.findByIdAndUserIdAndClientId(id1, USER_ID, CLIENT_ID))
                .thenReturn(Optional.of(removed));
        when(xtrmApiClient.deleteCard(any(DeleteCardCommand.class))).thenReturn(DeleteCardResult.ok());
        when(linkedCardRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(linkedCardRepository.findByUserIdAndClientIdOrderByCreatedAtAsc(USER_ID, CLIENT_ID))
                .thenReturn(List.of());
        when(userRedemptionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        PartnerRedemption result = service().removeCard(USER_ID, id1);

        assertThat(result.getPartnerLinkedCardId()).isNull();
        assertThat(result.getLinkedCardLabel()).isNull();
        assertThat(result.getPayoutMethod()).isEqualTo(RedemptionPayoutMethod.ANYPAY);
    }

    @Test
    void removeCard_transientXtrm_throws503AndKeepsRow() {
        UUID id1 = UUID.randomUUID();
        PartnerRedemption profile = PartnerRedemptionFixtures.enrolled(CLIENT_ID, USER_ID, "PAT-1")
                .partnerLinkedCardId("TOK-1").build();
        PartnerLinkedCard target = card(id1, "TOK-1", "1111", "Visa");
        when(enrollmentService.getOrCreateProfile(USER_ID)).thenReturn(profile);
        when(linkedCardRepository.findByIdAndUserIdAndClientId(id1, USER_ID, CLIENT_ID))
                .thenReturn(Optional.of(target));
        when(xtrmApiClient.deleteCard(any(DeleteCardCommand.class)))
                .thenReturn(DeleteCardResult.failed(List.of("Could not reach XTRM"), true));

        assertThatThrownBy(() -> service().removeCard(USER_ID, id1))
                .isInstanceOf(ExternalServiceException.class)
                .hasFieldOrPropertyWithValue("errorCode", "XTRM_UNAVAILABLE");

        assertThat(target.isDeleted()).isFalse();
        verify(linkedCardRepository, never()).save(any());
        verify(userRedemptionRepository, never()).save(any());
    }

    @Test
    void removeCard_nonRetryableXtrm_idempotentlySoftDeletesLocally() {
        UUID id1 = UUID.randomUUID();
        // The removed card is NOT the default → no promote, just a soft-delete.
        PartnerRedemption profile = PartnerRedemptionFixtures.enrolled(CLIENT_ID, USER_ID, "PAT-1")
                .partnerLinkedCardId("OTHER-TOK").build();
        PartnerLinkedCard target = card(id1, "TOK-1", "1111", "Visa");
        when(enrollmentService.getOrCreateProfile(USER_ID)).thenReturn(profile);
        when(linkedCardRepository.findByIdAndUserIdAndClientId(id1, USER_ID, CLIENT_ID))
                .thenReturn(Optional.of(target));
        when(xtrmApiClient.deleteCard(any(DeleteCardCommand.class)))
                .thenReturn(DeleteCardResult.failed(List.of("Card not found"), false));
        when(linkedCardRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service().removeCard(USER_ID, id1);

        assertThat(target.isDeleted()).isTrue();
        verify(linkedCardRepository).save(target);
        verify(userRedemptionRepository, never()).save(any());
    }

    @Test
    void removeCard_unknownCard_throwsNotFound() {
        PartnerRedemption profile = PartnerRedemptionFixtures.enrolled(CLIENT_ID, USER_ID, "PAT-1").build();
        UUID unknown = UUID.randomUUID();
        when(enrollmentService.getOrCreateProfile(USER_ID)).thenReturn(profile);
        when(linkedCardRepository.findByIdAndUserIdAndClientId(unknown, USER_ID, CLIENT_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().removeCard(USER_ID, unknown))
                .isInstanceOf(ResourceNotFoundException.class);

        verifyNoInteractions(xtrmApiClient);
    }

    // ---- setDefaultCard ----

    @Test
    void setDefaultCard_repointsDefaultToChosenCard() {
        UUID id2 = UUID.randomUUID();
        PartnerRedemption profile = PartnerRedemptionFixtures.enrolled(CLIENT_ID, USER_ID, "PAT-1")
                .partnerLinkedCardId("TOK-1").linkedCardLabel("Visa ••1111").build();
        when(enrollmentService.getOrCreateProfile(USER_ID)).thenReturn(profile);
        when(linkedCardRepository.findByIdAndUserIdAndClientId(id2, USER_ID, CLIENT_ID))
                .thenReturn(Optional.of(card(id2, "TOK-2", "2222", "Mastercard")));
        when(userRedemptionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        PartnerRedemption result = service().setDefaultCard(USER_ID, id2);

        assertThat(result.getPartnerLinkedCardId()).isEqualTo("TOK-2");
        assertThat(result.getLinkedCardLabel()).isEqualTo("Mastercard ••2222");
    }

    @Test
    void setDefaultCard_unknownCard_throwsNotFound() {
        PartnerRedemption profile = PartnerRedemptionFixtures.enrolled(CLIENT_ID, USER_ID, "PAT-1").build();
        UUID unknown = UUID.randomUUID();
        when(enrollmentService.getOrCreateProfile(USER_ID)).thenReturn(profile);
        when(linkedCardRepository.findByIdAndUserIdAndClientId(unknown, USER_ID, CLIENT_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().setDefaultCard(USER_ID, unknown))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(userRedemptionRepository, never()).save(any());
    }
}

package com.tenxengage.app.service.xtrm;

import com.tenxengage.app.dto.request.xtrm.LinkBankAccountRequest;
import com.tenxengage.app.dto.response.xtrm.LinkedBankResponse;
import com.tenxengage.app.entity.enums.xtrm.RedemptionPayoutMethod;
import com.tenxengage.app.entity.xtrm.PartnerLinkedBank;
import com.tenxengage.app.entity.xtrm.PartnerRedemption;
import com.tenxengage.app.exception.BusinessRuleException;
import com.tenxengage.app.exception.ExternalServiceException;
import com.tenxengage.app.exception.ResourceNotFoundException;
import com.tenxengage.app.repository.xtrm.PartnerLinkedBankRepository;
import com.tenxengage.app.repository.xtrm.PartnerRedemptionRepository;
import com.tenxengage.app.service.BankTransferCardService;
import com.tenxengage.app.service.xtrm.XtrmApiClient.DeleteBankCommand;
import com.tenxengage.app.service.xtrm.XtrmApiClient.DeleteBankResult;
import com.tenxengage.app.service.xtrm.XtrmApiClient.LinkBankCommand;
import com.tenxengage.app.service.xtrm.XtrmApiClient.LinkBankResult;
import com.tenxengage.app.testdata.xtrm.PartnerRedemptionFixtures;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class XtrmBankServiceTest {

    @Mock
    private PartnerRedemptionRepository userRedemptionRepository;
    @Mock
    private PartnerLinkedBankRepository linkedBankRepository;
    @Mock
    private XtrmApiClient xtrmApiClient;
    @Mock
    private XtrmEnrollmentService enrollmentService;
    @Mock
    private BankTransferCardService bankTransferCardService;

    private static final UUID CLIENT_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();

    private XtrmBankService service() {
        return new XtrmBankService(userRedemptionRepository, linkedBankRepository, xtrmApiClient, enrollmentService,
                bankTransferCardService);
    }

    private LinkBankAccountRequest request() {
        return new LinkBankAccountRequest(
                "Ada Lovelace", "14085551234", "123456789", "021000021", null, "Wells Fargo",
                "123 Main St", null, "Los Angeles", "CA", "90001", "US", "ACH");
    }

    private PartnerLinkedBank bank(UUID id, String beneficiaryId, String label) {
        PartnerLinkedBank b = PartnerLinkedBank.builder()
                .clientId(CLIENT_ID).userId(USER_ID)
                .xtrmBeneficiaryId(beneficiaryId).maskedLabel(label)
                .currency("USD").countryIso2("US").withdrawType("ACH").build();
        b.setId(id);
        return b;
    }

    // ---- addBank ----

    @Test
    void addBank_firstBank_insertsRowAndBecomesDefault() {
        PartnerRedemption profile = PartnerRedemptionFixtures.enrolled(CLIENT_ID, USER_ID, "PAT-1").build();
        when(enrollmentService.ensureEnrolledForPayout(USER_ID)).thenReturn("PAT-1");
        when(enrollmentService.getOrCreateProfile(USER_ID)).thenReturn(profile);
        when(xtrmApiClient.linkBankBeneficiary(any(LinkBankCommand.class)))
                .thenReturn(LinkBankResult.ok("BANK-REF-1"));
        when(userRedemptionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        PartnerRedemption result = service().addBank(USER_ID, request());

        // First bank → becomes the default pointer on partner_redemption.
        assertThat(result.getPartnerLinkedBankId()).isEqualTo("BANK-REF-1");
        assertThat(result.getLinkedBankLabel()).isEqualTo("Wells Fargo ••6789");

        // A local row is persisted from the form input (masked, never the full account number).
        ArgumentCaptor<PartnerLinkedBank> row = ArgumentCaptor.forClass(PartnerLinkedBank.class);
        verify(linkedBankRepository).save(row.capture());
        assertThat(row.getValue().getXtrmBeneficiaryId()).isEqualTo("BANK-REF-1");
        assertThat(row.getValue().getMaskedLabel()).isEqualTo("Wells Fargo ••6789");
        assertThat(row.getValue().getMaskedLabel()).doesNotContain("123456789");
        assertThat(row.getValue().getCurrency()).isEqualTo("USD");
        assertThat(row.getValue().getWithdrawType()).isEqualTo("ACH");
    }

    // BU-3: a successful bank link provisions the client's hidden bank-transfer card (idempotent).
    @Test
    void addBank_provisionsBankTransferCard() {
        PartnerRedemption profile = PartnerRedemptionFixtures.enrolled(CLIENT_ID, USER_ID, "PAT-1").build();
        when(enrollmentService.ensureEnrolledForPayout(USER_ID)).thenReturn("PAT-1");
        when(enrollmentService.getOrCreateProfile(USER_ID)).thenReturn(profile);
        when(xtrmApiClient.linkBankBeneficiary(any(LinkBankCommand.class)))
                .thenReturn(LinkBankResult.ok("BANK-REF-1"));
        when(userRedemptionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service().addBank(USER_ID, request());

        verify(bankTransferCardService).ensureBankTransferCard(profile.getClientId());
    }

    // BU-3: card provisioning failure must NOT fail the bank link (non-fatal, own transaction).
    @Test
    void addBank_cardProvisionFailure_isNonFatal() {
        PartnerRedemption profile = PartnerRedemptionFixtures.enrolled(CLIENT_ID, USER_ID, "PAT-1").build();
        when(enrollmentService.ensureEnrolledForPayout(USER_ID)).thenReturn("PAT-1");
        when(enrollmentService.getOrCreateProfile(USER_ID)).thenReturn(profile);
        when(xtrmApiClient.linkBankBeneficiary(any(LinkBankCommand.class)))
                .thenReturn(LinkBankResult.ok("BANK-REF-1"));
        when(userRedemptionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(bankTransferCardService.ensureBankTransferCard(any()))
                .thenThrow(new RuntimeException("provisioning blew up"));

        PartnerRedemption result = service().addBank(USER_ID, request());

        assertThat(result.getPartnerLinkedBankId()).isEqualTo("BANK-REF-1");
    }

    @Test
    void addBank_secondBank_appendsWithoutOverwritingDefault() {
        PartnerRedemption profile = PartnerRedemptionFixtures.enrolled(CLIENT_ID, USER_ID, "PAT-1")
                .partnerLinkedBankId("EXISTING-REF").linkedBankLabel("Chase ••1111").build();
        when(enrollmentService.ensureEnrolledForPayout(USER_ID)).thenReturn("PAT-1");
        when(enrollmentService.getOrCreateProfile(USER_ID)).thenReturn(profile);
        when(xtrmApiClient.linkBankBeneficiary(any(LinkBankCommand.class)))
                .thenReturn(LinkBankResult.ok("BANK-REF-2"));

        PartnerRedemption result = service().addBank(USER_ID, request());

        // The existing default is untouched (append-not-replace)...
        assertThat(result.getPartnerLinkedBankId()).isEqualTo("EXISTING-REF");
        // ...the new bank is still persisted, but the profile default is not re-saved.
        verify(linkedBankRepository).save(any(PartnerLinkedBank.class));
        verify(userRedemptionRepository, never()).save(any());
    }

    @Test
    void addBank_xtrmDuplicate_throws422AndInsertsNoRow() {
        PartnerRedemption profile = PartnerRedemptionFixtures.enrolled(CLIENT_ID, USER_ID, "PAT-1").build();
        when(enrollmentService.ensureEnrolledForPayout(USER_ID)).thenReturn("PAT-1");
        when(enrollmentService.getOrCreateProfile(USER_ID)).thenReturn(profile);
        when(xtrmApiClient.linkBankBeneficiary(any(LinkBankCommand.class)))
                .thenReturn(LinkBankResult.failed("XTRM_BANK_DUPLICATE", List.of("Bank already linked"), false));

        assertThatThrownBy(() -> service().addBank(USER_ID, request()))
                .isInstanceOf(BusinessRuleException.class)
                .hasFieldOrPropertyWithValue("errorCode", "XTRM_BANK_DUPLICATE");

        verify(linkedBankRepository, never()).save(any());
        verify(userRedemptionRepository, never()).save(any());
    }

    @Test
    void addBank_localUniqueViolation_throws422Duplicate() {
        PartnerRedemption profile = PartnerRedemptionFixtures.enrolled(CLIENT_ID, USER_ID, "PAT-1").build();
        when(enrollmentService.ensureEnrolledForPayout(USER_ID)).thenReturn("PAT-1");
        when(enrollmentService.getOrCreateProfile(USER_ID)).thenReturn(profile);
        when(xtrmApiClient.linkBankBeneficiary(any(LinkBankCommand.class)))
                .thenReturn(LinkBankResult.ok("BANK-REF-1"));
        when(linkedBankRepository.save(any())).thenThrow(new DataIntegrityViolationException("dup"));

        assertThatThrownBy(() -> service().addBank(USER_ID, request()))
                .isInstanceOf(BusinessRuleException.class)
                .hasFieldOrPropertyWithValue("errorCode", "XTRM_BANK_DUPLICATE");

        verify(userRedemptionRepository, never()).save(any());
    }

    @Test
    void addBank_xtrmTransientOutage_throws503() {
        PartnerRedemption profile = PartnerRedemptionFixtures.enrolled(CLIENT_ID, USER_ID, "PAT-1").build();
        when(enrollmentService.ensureEnrolledForPayout(USER_ID)).thenReturn("PAT-1");
        when(enrollmentService.getOrCreateProfile(USER_ID)).thenReturn(profile);
        when(xtrmApiClient.linkBankBeneficiary(any(LinkBankCommand.class)))
                .thenReturn(LinkBankResult.failed("XTRM_BANK_LINK_FAILED", List.of("Could not reach XTRM"), true));

        assertThatThrownBy(() -> service().addBank(USER_ID, request()))
                .isInstanceOf(ExternalServiceException.class)
                .hasFieldOrPropertyWithValue("errorCode", "XTRM_UNAVAILABLE");

        verify(linkedBankRepository, never()).save(any());
        verify(userRedemptionRepository, never()).save(any());
    }

    @Test
    void addBank_notEnrolled_propagatesAndSkipsXtrmCall() {
        when(enrollmentService.ensureEnrolledForPayout(USER_ID))
                .thenThrow(new BusinessRuleException("XTRM_NOT_ENROLLED", "not set up"));

        assertThatThrownBy(() -> service().addBank(USER_ID, request()))
                .isInstanceOf(BusinessRuleException.class)
                .hasFieldOrPropertyWithValue("errorCode", "XTRM_NOT_ENROLLED");

        verifyNoInteractions(xtrmApiClient, linkedBankRepository);
        verify(userRedemptionRepository, never()).save(any());
    }

    // ---- listBanks ----

    @Test
    void listBanks_marksTheDefaultBank() {
        PartnerRedemption profile = PartnerRedemptionFixtures.enrolled(CLIENT_ID, USER_ID, "PAT-1")
                .partnerLinkedBankId("REF-2").build();
        when(enrollmentService.getProfileView(USER_ID)).thenReturn(profile);
        when(linkedBankRepository.findByUserIdAndClientIdOrderByCreatedAtAsc(USER_ID, CLIENT_ID))
                .thenReturn(List.of(
                        bank(UUID.randomUUID(), "REF-1", "Wells Fargo ••1111"),
                        bank(UUID.randomUUID(), "REF-2", "SBI ••2222")));

        List<LinkedBankResponse> banks = service().listBanks(USER_ID);

        assertThat(banks).hasSize(2);
        assertThat(banks.get(0).label()).isEqualTo("Wells Fargo ••1111");
        assertThat(banks.get(0).isDefault()).isFalse();
        assertThat(banks.get(1).isDefault()).isTrue();
    }

    // ---- removeBank ----

    @Test
    void removeBank_default_softDeletesAndAutoPromotesOldestRemaining() {
        UUID id1 = UUID.randomUUID();
        PartnerRedemption profile = PartnerRedemptionFixtures.enrolled(CLIENT_ID, USER_ID, "PAT-1")
                .partnerLinkedBankId("REF-1").linkedBankLabel("Wells Fargo ••1111")
                .payoutMethod(RedemptionPayoutMethod.BANK).build();
        PartnerLinkedBank removed = bank(id1, "REF-1", "Wells Fargo ••1111");
        when(enrollmentService.getOrCreateProfile(USER_ID)).thenReturn(profile);
        when(linkedBankRepository.findByIdAndUserIdAndClientId(id1, USER_ID, CLIENT_ID))
                .thenReturn(java.util.Optional.of(removed));
        when(xtrmApiClient.deleteBankBeneficiary(any(DeleteBankCommand.class))).thenReturn(DeleteBankResult.ok());
        when(linkedBankRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(linkedBankRepository.findByUserIdAndClientIdOrderByCreatedAtAsc(USER_ID, CLIENT_ID))
                .thenReturn(List.of(bank(UUID.randomUUID(), "REF-2", "SBI ••2222")));
        when(userRedemptionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        PartnerRedemption result = service().removeBank(USER_ID, id1);

        assertThat(removed.isDeleted()).isTrue();
        assertThat(result.getPartnerLinkedBankId()).isEqualTo("REF-2");
        assertThat(result.getLinkedBankLabel()).isEqualTo("SBI ••2222");
        assertThat(result.getPayoutMethod()).isEqualTo(RedemptionPayoutMethod.BANK);
    }

    @Test
    void removeBank_lastBank_clearsDefaultAndResetsRailToAnypay() {
        UUID id1 = UUID.randomUUID();
        PartnerRedemption profile = PartnerRedemptionFixtures.enrolled(CLIENT_ID, USER_ID, "PAT-1")
                .partnerLinkedBankId("REF-1").linkedBankLabel("Wells Fargo ••1111")
                .payoutMethod(RedemptionPayoutMethod.BANK).build();
        PartnerLinkedBank removed = bank(id1, "REF-1", "Wells Fargo ••1111");
        when(enrollmentService.getOrCreateProfile(USER_ID)).thenReturn(profile);
        when(linkedBankRepository.findByIdAndUserIdAndClientId(id1, USER_ID, CLIENT_ID))
                .thenReturn(java.util.Optional.of(removed));
        when(xtrmApiClient.deleteBankBeneficiary(any(DeleteBankCommand.class))).thenReturn(DeleteBankResult.ok());
        when(linkedBankRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(linkedBankRepository.findByUserIdAndClientIdOrderByCreatedAtAsc(USER_ID, CLIENT_ID))
                .thenReturn(List.of());
        when(userRedemptionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        PartnerRedemption result = service().removeBank(USER_ID, id1);

        assertThat(result.getPartnerLinkedBankId()).isNull();
        assertThat(result.getLinkedBankLabel()).isNull();
        assertThat(result.getPayoutMethod()).isEqualTo(RedemptionPayoutMethod.ANYPAY);
    }

    @Test
    void removeBank_transientXtrm_throws503AndKeepsRow() {
        UUID id1 = UUID.randomUUID();
        PartnerRedemption profile = PartnerRedemptionFixtures
                .enrolledWithBank(CLIENT_ID, USER_ID, "PAT-1", "REF-1").build();
        PartnerLinkedBank target = bank(id1, "REF-1", "Wells Fargo ••1111");
        when(enrollmentService.getOrCreateProfile(USER_ID)).thenReturn(profile);
        when(linkedBankRepository.findByIdAndUserIdAndClientId(id1, USER_ID, CLIENT_ID))
                .thenReturn(java.util.Optional.of(target));
        when(xtrmApiClient.deleteBankBeneficiary(any(DeleteBankCommand.class)))
                .thenReturn(DeleteBankResult.failed(List.of("Could not reach XTRM"), true));

        assertThatThrownBy(() -> service().removeBank(USER_ID, id1))
                .isInstanceOf(ExternalServiceException.class)
                .hasFieldOrPropertyWithValue("errorCode", "XTRM_UNAVAILABLE");

        assertThat(target.isDeleted()).isFalse();
        verify(linkedBankRepository, never()).save(any());
        verify(userRedemptionRepository, never()).save(any());
    }

    @Test
    void removeBank_nonRetryableXtrm_idempotentlySoftDeletesLocally() {
        UUID id1 = UUID.randomUUID();
        // The removed bank is NOT the default → no promote, just a soft-delete.
        PartnerRedemption profile = PartnerRedemptionFixtures.enrolled(CLIENT_ID, USER_ID, "PAT-1")
                .partnerLinkedBankId("OTHER-REF").build();
        PartnerLinkedBank target = bank(id1, "REF-1", "Wells Fargo ••1111");
        when(enrollmentService.getOrCreateProfile(USER_ID)).thenReturn(profile);
        when(linkedBankRepository.findByIdAndUserIdAndClientId(id1, USER_ID, CLIENT_ID))
                .thenReturn(java.util.Optional.of(target));
        when(xtrmApiClient.deleteBankBeneficiary(any(DeleteBankCommand.class)))
                .thenReturn(DeleteBankResult.failed(List.of("Beneficiary not found"), false));
        when(linkedBankRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service().removeBank(USER_ID, id1);

        assertThat(target.isDeleted()).isTrue();
        verify(linkedBankRepository).save(target);
        verify(userRedemptionRepository, never()).save(any());
    }

    @Test
    void removeBank_unknownBank_throwsNotFound() {
        PartnerRedemption profile = PartnerRedemptionFixtures.enrolled(CLIENT_ID, USER_ID, "PAT-1").build();
        UUID unknown = UUID.randomUUID();
        when(enrollmentService.getOrCreateProfile(USER_ID)).thenReturn(profile);
        when(linkedBankRepository.findByIdAndUserIdAndClientId(unknown, USER_ID, CLIENT_ID))
                .thenReturn(java.util.Optional.empty());

        assertThatThrownBy(() -> service().removeBank(USER_ID, unknown))
                .isInstanceOf(ResourceNotFoundException.class);

        verifyNoInteractions(xtrmApiClient);
    }

    // ---- setDefaultBank ----

    @Test
    void setDefaultBank_repointsDefaultToChosenBank() {
        UUID id2 = UUID.randomUUID();
        PartnerRedemption profile = PartnerRedemptionFixtures.enrolled(CLIENT_ID, USER_ID, "PAT-1")
                .partnerLinkedBankId("REF-1").linkedBankLabel("Wells Fargo ••1111").build();
        when(enrollmentService.getOrCreateProfile(USER_ID)).thenReturn(profile);
        when(linkedBankRepository.findByIdAndUserIdAndClientId(id2, USER_ID, CLIENT_ID))
                .thenReturn(java.util.Optional.of(bank(id2, "REF-2", "SBI ••2222")));
        when(userRedemptionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        PartnerRedemption result = service().setDefaultBank(USER_ID, id2);

        assertThat(result.getPartnerLinkedBankId()).isEqualTo("REF-2");
        assertThat(result.getLinkedBankLabel()).isEqualTo("SBI ••2222");
    }

    @Test
    void setDefaultBank_unknownBank_throwsNotFound() {
        PartnerRedemption profile = PartnerRedemptionFixtures.enrolled(CLIENT_ID, USER_ID, "PAT-1").build();
        UUID unknown = UUID.randomUUID();
        when(enrollmentService.getOrCreateProfile(USER_ID)).thenReturn(profile);
        when(linkedBankRepository.findByIdAndUserIdAndClientId(unknown, USER_ID, CLIENT_ID))
                .thenReturn(java.util.Optional.empty());

        assertThatThrownBy(() -> service().setDefaultBank(USER_ID, unknown))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(userRedemptionRepository, never()).save(any());
    }

    // ---- setPayoutMethod ----

    @Test
    void setPayoutMethod_bankWithoutLinkedBank_throwsBankNotLinked() {
        PartnerRedemption profile = PartnerRedemptionFixtures.enrolled(CLIENT_ID, USER_ID, "PAT-1").build();
        when(enrollmentService.getOrCreateProfile(USER_ID)).thenReturn(profile);

        assertThatThrownBy(() -> service().setPayoutMethod(USER_ID, RedemptionPayoutMethod.BANK))
                .isInstanceOf(BusinessRuleException.class)
                .hasFieldOrPropertyWithValue("errorCode", "BANK_NOT_LINKED");

        verify(userRedemptionRepository, never()).save(any());
    }

    @Test
    void setPayoutMethod_bankWithLinkedBank_updates() {
        PartnerRedemption profile = PartnerRedemptionFixtures.enrolled(CLIENT_ID, USER_ID, "PAT-1")
                .partnerLinkedBankId("BANK-REF-1").linkedBankLabel("Wells Fargo ••6789")
                .payoutMethod(RedemptionPayoutMethod.ANYPAY).build();
        when(enrollmentService.getOrCreateProfile(USER_ID)).thenReturn(profile);
        when(userRedemptionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        PartnerRedemption result = service().setPayoutMethod(USER_ID, RedemptionPayoutMethod.BANK);

        assertThat(result.getPayoutMethod()).isEqualTo(RedemptionPayoutMethod.BANK);
    }

    @Test
    void setPayoutMethod_cardWithoutLinkedCard_throwsCardNotLinked() {
        PartnerRedemption profile = PartnerRedemptionFixtures.enrolled(CLIENT_ID, USER_ID, "PAT-1").build();
        when(enrollmentService.getOrCreateProfile(USER_ID)).thenReturn(profile);

        assertThatThrownBy(() -> service().setPayoutMethod(USER_ID, RedemptionPayoutMethod.CARD))
                .isInstanceOf(BusinessRuleException.class)
                .hasFieldOrPropertyWithValue("errorCode", "CARD_NOT_LINKED");

        verify(userRedemptionRepository, never()).save(any());
    }
}

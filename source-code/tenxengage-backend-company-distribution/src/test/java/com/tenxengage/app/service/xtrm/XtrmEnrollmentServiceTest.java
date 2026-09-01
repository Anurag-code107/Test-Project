package com.tenxengage.app.service.xtrm;

import com.tenxengage.app.dto.request.xtrm.SaveRedemptionAddressRequest;
import com.tenxengage.app.entity.User;
import com.tenxengage.app.entity.enums.AuditAction;
import com.tenxengage.app.entity.enums.AuditResourceType;
import com.tenxengage.app.entity.enums.xtrm.XtrmEnrollmentStatus;
import com.tenxengage.app.entity.xtrm.PartnerRedemption;
import com.tenxengage.app.exception.BusinessRuleException;
import com.tenxengage.app.repository.UserRepository;
import com.tenxengage.app.repository.xtrm.PartnerRedemptionRepository;
import com.tenxengage.app.service.AuditLogService;
import com.tenxengage.app.service.xtrm.XtrmApiClient.CreateUserCommand;
import com.tenxengage.app.service.xtrm.XtrmApiClient.CreateUserResult;
import com.tenxengage.app.testdata.xtrm.PartnerRedemptionFixtures;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class XtrmEnrollmentServiceTest {

    @Mock
    private PartnerRedemptionRepository userRedemptionRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private XtrmApiClient xtrmApiClient;
    @Mock
    private AuditLogService auditLogService;
    @Mock
    private SellerEnrollmentIssuerResolver issuerResolver;

    private static final UUID CLIENT_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();

    private XtrmEnrollmentService service() {
        // These cases predate company-scoped enrollment and are about the enrollment mechanics themselves,
        // so the issuer is stubbed to the platform — which is what they have always exercised.
        org.mockito.Mockito.lenient()
                .when(issuerResolver.resolve(org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any()))
                .thenReturn(new SellerEnrollmentIssuerResolver.EnrollmentIssuer.UseAccount(
                        new XtrmCredentials("platform-id", "s", "SPN26237883", "203871", "2314")));
        return new XtrmEnrollmentService(userRedemptionRepository, userRepository, xtrmApiClient,
                auditLogService, issuerResolver);
    }

    // ---- getOrCreateProfile ----

    @Test
    void getOrCreateProfile_existing_returnsItWithoutSaving() {
        PartnerRedemption existing = PartnerRedemptionFixtures.notEnrolled(CLIENT_ID, USER_ID).build();
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user()));
        when(userRedemptionRepository.findByUserIdAndClientId(USER_ID, CLIENT_ID)).thenReturn(Optional.of(existing));

        PartnerRedemption result = service().getOrCreateProfile(USER_ID);

        assertThat(result).isSameAs(existing);
        verify(userRedemptionRepository, never()).save(any());
    }

    @Test
    void getOrCreateProfile_absent_createsNotEnrolledShell() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user()));
        when(userRedemptionRepository.findByUserIdAndClientId(USER_ID, CLIENT_ID)).thenReturn(Optional.empty());
        when(userRedemptionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        PartnerRedemption result = service().getOrCreateProfile(USER_ID);

        assertThat(result.getClientId()).isEqualTo(CLIENT_ID);
        assertThat(result.getUserId()).isEqualTo(USER_ID);
        assertThat(result.getEnrollmentStatus()).isEqualTo(XtrmEnrollmentStatus.NOT_ENROLLED);
    }

    // ---- enrollIfNeeded ----

    @Test
    void enrollIfNeeded_alreadyEnrolled_isNoOp() {
        PartnerRedemption enrolled = PartnerRedemptionFixtures.enrolled(CLIENT_ID, USER_ID, "PAT-EXISTING").build();
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user()));
        when(userRedemptionRepository.findByUserIdAndClientId(USER_ID, CLIENT_ID)).thenReturn(Optional.of(enrolled));

        service().enrollIfNeeded(USER_ID);

        verifyNoInteractions(xtrmApiClient);
        verify(userRedemptionRepository, never()).save(any());
    }

    @Test
    void enrollIfNeeded_missingAddress_skipsXtrmCall() {
        PartnerRedemption noAddress = PartnerRedemptionFixtures.notEnrolled(CLIENT_ID, USER_ID)
                .address(null).build();
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user()));
        when(userRedemptionRepository.findByUserIdAndClientId(USER_ID, CLIENT_ID)).thenReturn(Optional.of(noAddress));

        service().enrollIfNeeded(USER_ID);

        verifyNoInteractions(xtrmApiClient);
        assertThat(noAddress.getEnrollmentStatus()).isEqualTo(XtrmEnrollmentStatus.NOT_ENROLLED);
    }

    @Test
    void enrollIfNeeded_success_marksEnrolledStoresPatAndAudits() {
        PartnerRedemption profile = PartnerRedemptionFixtures.notEnrolled(CLIENT_ID, USER_ID).build();
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user()));
        when(userRedemptionRepository.findByUserIdAndClientId(USER_ID, CLIENT_ID)).thenReturn(Optional.of(profile));
        when(xtrmApiClient.createUser(any(CreateUserCommand.class), any(XtrmCredentials.class)))
                .thenReturn(CreateUserResult.ok("PAT-NEW-123", "Standard"));

        service().enrollIfNeeded(USER_ID);

        assertThat(profile.getEnrollmentStatus()).isEqualTo(XtrmEnrollmentStatus.ENROLLED);
        assertThat(profile.getRecipientUserId()).isEqualTo("PAT-NEW-123");
        assertThat(profile.getIdentityLevel()).isEqualTo("Standard");
        assertThat(profile.getEnrolledAt()).isNotNull();
        assertThat(profile.getEnrollmentError()).isNull();
        verify(userRedemptionRepository).save(profile);
        verify(auditLogService).logAsync(eq(AuditAction.ENROLLED), eq(AuditResourceType.PARTNER_REDEMPTION),
                any(), any(), any(), any());
    }

    @Test
    void enrollIfNeeded_domainFailure_marksFailedAndDoesNotThrow() {
        PartnerRedemption profile = PartnerRedemptionFixtures.notEnrolled(CLIENT_ID, USER_ID).build();
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user()));
        when(userRedemptionRepository.findByUserIdAndClientId(USER_ID, CLIENT_ID)).thenReturn(Optional.of(profile));
        when(xtrmApiClient.createUser(any(CreateUserCommand.class), any(XtrmCredentials.class)))
                .thenReturn(CreateUserResult.failed(List.of("Address is invalid"), false));

        assertThatCode(() -> service().enrollIfNeeded(USER_ID)).doesNotThrowAnyException();

        assertThat(profile.getEnrollmentStatus()).isEqualTo(XtrmEnrollmentStatus.FAILED);
        assertThat(profile.getEnrollmentError()).contains("Address is invalid");
        verify(auditLogService, never()).logAsync(any(), any(), any(), any(), any(), any());
    }

    @Test
    void enrollIfNeeded_apiThrows_marksFailedAndDoesNotThrow() {
        PartnerRedemption profile = PartnerRedemptionFixtures.notEnrolled(CLIENT_ID, USER_ID).build();
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user()));
        when(userRedemptionRepository.findByUserIdAndClientId(USER_ID, CLIENT_ID)).thenReturn(Optional.of(profile));
        when(xtrmApiClient.createUser(any(CreateUserCommand.class), any(XtrmCredentials.class)))
                .thenThrow(new RuntimeException("connection reset"));

        assertThatCode(() -> service().enrollIfNeeded(USER_ID)).doesNotThrowAnyException();

        assertThat(profile.getEnrollmentStatus()).isEqualTo(XtrmEnrollmentStatus.FAILED);
    }

    // ---- ensureEnrolledForPayout ----

    @Test
    void ensureEnrolledForPayout_alreadyEnrolled_returnsPatWithoutReEnrolling() {
        PartnerRedemption enrolled = PartnerRedemptionFixtures.enrolled(CLIENT_ID, USER_ID, "PAT-READY").build();
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user()));
        when(userRedemptionRepository.findByUserIdAndClientId(USER_ID, CLIENT_ID)).thenReturn(Optional.of(enrolled));

        String pat = service().ensureEnrolledForPayout(USER_ID);

        assertThat(pat).isEqualTo("PAT-READY");
        verifyNoInteractions(xtrmApiClient);
    }

    @Test
    void ensureEnrolledForPayout_notEnrolled_lazilyEnrollsThenReturnsPat() {
        PartnerRedemption profile = PartnerRedemptionFixtures.notEnrolled(CLIENT_ID, USER_ID).build();
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user()));
        when(userRedemptionRepository.findByUserIdAndClientId(USER_ID, CLIENT_ID)).thenReturn(Optional.of(profile));
        when(xtrmApiClient.createUser(any(CreateUserCommand.class), any(XtrmCredentials.class)))
                .thenReturn(CreateUserResult.ok("PAT-LAZY", "Standard"));

        String pat = service().ensureEnrolledForPayout(USER_ID);

        assertThat(pat).isEqualTo("PAT-LAZY");
        verify(xtrmApiClient, times(1)).createUser(any(CreateUserCommand.class), any(XtrmCredentials.class));
    }

    @Test
    void ensureEnrolledForPayout_stillNotEnrolled_throwsXtrmNotEnrolled() {
        PartnerRedemption noAddress = PartnerRedemptionFixtures.notEnrolled(CLIENT_ID, USER_ID)
                .address(null).build();
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user()));
        when(userRedemptionRepository.findByUserIdAndClientId(USER_ID, CLIENT_ID)).thenReturn(Optional.of(noAddress));

        assertThatThrownBy(() -> service().ensureEnrolledForPayout(USER_ID))
                .isInstanceOf(BusinessRuleException.class)
                .hasFieldOrPropertyWithValue("errorCode", "XTRM_NOT_ENROLLED");
    }

    // ---- saveAddressAndEnroll ----

    @Test
    void saveAddressAndEnroll_persistsAddressThenEnrolls() {
        PartnerRedemption profile = PartnerRedemptionFixtures.notEnrolled(CLIENT_ID, USER_ID)
                .address(null).build();
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user()));
        when(userRedemptionRepository.findByUserIdAndClientId(USER_ID, CLIENT_ID)).thenReturn(Optional.of(profile));
        when(xtrmApiClient.createUser(any(CreateUserCommand.class), any(XtrmCredentials.class)))
                .thenReturn(CreateUserResult.ok("PAT-ADDR", "Standard"));

        PartnerRedemption result = service().saveAddressAndEnroll(USER_ID, addressRequest());

        assertThat(profile.getAddress().getLine1()).isEqualTo("742 Evergreen Terrace");
        assertThat(profile.getAddress().getCountryIso2()).isEqualTo("US");
        assertThat(result.getEnrollmentStatus()).isEqualTo(XtrmEnrollmentStatus.ENROLLED);
        assertThat(result.getRecipientUserId()).isEqualTo("PAT-ADDR");
    }

    @Test
    void saveAddressAndEnroll_enrollmentFailure_addressStillSaved() {
        PartnerRedemption profile = PartnerRedemptionFixtures.notEnrolled(CLIENT_ID, USER_ID)
                .address(null).build();
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user()));
        when(userRedemptionRepository.findByUserIdAndClientId(USER_ID, CLIENT_ID)).thenReturn(Optional.of(profile));
        when(xtrmApiClient.createUser(any(CreateUserCommand.class), any(XtrmCredentials.class)))
                .thenReturn(CreateUserResult.failed(List.of("XTRM unavailable"), true));

        PartnerRedemption result = service().saveAddressAndEnroll(USER_ID, addressRequest());

        assertThat(result.getAddress().getLine1()).isEqualTo("742 Evergreen Terrace");
        assertThat(result.getEnrollmentStatus()).isEqualTo(XtrmEnrollmentStatus.FAILED);
    }

    // ---- helpers ----

    private SaveRedemptionAddressRequest addressRequest() {
        return new SaveRedemptionAddressRequest(
                "742 Evergreen Terrace", null, "Springfield", "OR", "97403", "US");
    }

    private User user() {
        User u = User.builder()
                .clientId(CLIENT_ID)
                .firstName("Ada").lastName("Lovelace").email("ada@example.com").phone("+15551234567")
                .build();
        u.setId(USER_ID);
        return u;
    }
}

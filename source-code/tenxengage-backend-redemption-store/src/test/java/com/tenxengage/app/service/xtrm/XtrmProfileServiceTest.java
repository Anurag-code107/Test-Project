package com.tenxengage.app.service.xtrm;

import com.tenxengage.app.dto.response.PhoneUpdateResponse;
import com.tenxengage.app.entity.User;
import com.tenxengage.app.entity.xtrm.PartnerRedemption;
import com.tenxengage.app.exception.BusinessRuleException;
import com.tenxengage.app.exception.ExternalServiceException;
import com.tenxengage.app.repository.UserRepository;
import com.tenxengage.app.service.xtrm.XtrmApiClient.UpdateUserCommand;
import com.tenxengage.app.service.xtrm.XtrmApiClient.UpdateUserResult;
import com.tenxengage.app.testdata.xtrm.PartnerRedemptionFixtures;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
class XtrmProfileServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private XtrmApiClient xtrmApiClient;
    @Mock
    private XtrmEnrollmentService enrollmentService;

    private static final UUID CLIENT_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();
    private static final String PHONE = "4085551284";
    private static final String ISO2 = "US";

    private XtrmProfileService service() {
        return new XtrmProfileService(userRepository, xtrmApiClient, enrollmentService);
    }

    private User user() {
        User u = User.builder().firstName("Ada").lastName("Lovelace").clientId(CLIENT_ID).build();
        u.setId(USER_ID);
        return u;
    }

    // ---- initiate ----

    @Test
    void initiate_enrolled_sendsOtpAndDoesNotSaveYet() {
        PartnerRedemption profile = PartnerRedemptionFixtures.enrolled(CLIENT_ID, USER_ID, "PAT-1").build();
        when(enrollmentService.getProfileView(USER_ID)).thenReturn(profile);
        when(userRepository.findByIdAndClientId(USER_ID, CLIENT_ID)).thenReturn(Optional.of(user()));
        when(xtrmApiClient.updateUser(any(UpdateUserCommand.class))).thenReturn(UpdateUserResult.otpSent());

        PhoneUpdateResponse res = service().initiate(USER_ID, PHONE, ISO2);

        assertThat(res.otpRequired()).isTrue();
        // Phone is NOT persisted until XTRM confirms.
        verify(userRepository, never()).save(any());

        ArgumentCaptor<UpdateUserCommand> cmd = ArgumentCaptor.forClass(UpdateUserCommand.class);
        verify(xtrmApiClient).updateUser(cmd.capture());
        assertThat(cmd.getValue().recipientUserId()).isEqualTo("PAT-1");
        assertThat(cmd.getValue().mobileCountryIso2()).isEqualTo("US");
        assertThat(cmd.getValue().mobileNumber()).isEqualTo(PHONE);
        assertThat(cmd.getValue().otp()).isNull();
    }

    @Test
    void initiate_notEnrolled_savesLocallyAndSkipsXtrm() {
        PartnerRedemption profile = PartnerRedemptionFixtures.notEnrolled(CLIENT_ID, USER_ID).build();
        when(enrollmentService.getProfileView(USER_ID)).thenReturn(profile);
        when(userRepository.findByIdAndClientId(USER_ID, CLIENT_ID)).thenReturn(Optional.of(user()));
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        PhoneUpdateResponse res = service().initiate(USER_ID, PHONE, ISO2);

        assertThat(res.otpRequired()).isFalse();
        assertThat(res.phone()).isEqualTo(PHONE);
        verifyNoInteractions(xtrmApiClient);
        verify(userRepository).save(any(User.class));
    }

    @Test
    void initiate_unsupportedCountry_throws422AndSkipsEverything() {
        assertThatThrownBy(() -> service().initiate(USER_ID, PHONE, "ZZ"))
                .isInstanceOf(BusinessRuleException.class)
                .hasFieldOrPropertyWithValue("errorCode", "UNSUPPORTED_MOBILE_COUNTRY");

        verifyNoInteractions(xtrmApiClient, enrollmentService, userRepository);
    }

    // ---- confirm ----

    @Test
    void confirm_enrolled_appliesAtXtrmAndPersists() {
        PartnerRedemption profile = PartnerRedemptionFixtures.enrolled(CLIENT_ID, USER_ID, "PAT-1").build();
        when(enrollmentService.getProfileView(USER_ID)).thenReturn(profile);
        User user = user();
        when(userRepository.findByIdAndClientId(USER_ID, CLIENT_ID)).thenReturn(Optional.of(user));
        when(xtrmApiClient.updateUser(any(UpdateUserCommand.class)))
                .thenReturn(UpdateUserResult.applied("PAT-1", "Basic"));
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        PhoneUpdateResponse res = service().confirm(USER_ID, PHONE, ISO2, "123456");

        assertThat(res.otpRequired()).isFalse();
        assertThat(res.phone()).isEqualTo(PHONE);
        assertThat(res.phoneCountryIso2()).isEqualTo(ISO2);
        // Persisted with the new number + country.
        assertThat(user.getPhone()).isEqualTo(PHONE);
        assertThat(user.getPhoneCountryIso2()).isEqualTo(ISO2);

        ArgumentCaptor<UpdateUserCommand> cmd = ArgumentCaptor.forClass(UpdateUserCommand.class);
        verify(xtrmApiClient).updateUser(cmd.capture());
        assertThat(cmd.getValue().otp()).isEqualTo("123456");
    }

    @Test
    void confirm_wrongOtp_throws422AndDoesNotPersist() {
        PartnerRedemption profile = PartnerRedemptionFixtures.enrolled(CLIENT_ID, USER_ID, "PAT-1").build();
        when(enrollmentService.getProfileView(USER_ID)).thenReturn(profile);
        when(userRepository.findByIdAndClientId(USER_ID, CLIENT_ID)).thenReturn(Optional.of(user()));
        // XTRM replies "OTP sent" again → the code wasn't accepted.
        when(xtrmApiClient.updateUser(any(UpdateUserCommand.class))).thenReturn(UpdateUserResult.otpSent());

        assertThatThrownBy(() -> service().confirm(USER_ID, PHONE, ISO2, "000000"))
                .isInstanceOf(BusinessRuleException.class)
                .hasFieldOrPropertyWithValue("errorCode", "XTRM_PROFILE_OTP_INVALID");

        verify(userRepository, never()).save(any());
    }

    @Test
    void confirm_transientXtrm_throws503() {
        PartnerRedemption profile = PartnerRedemptionFixtures.enrolled(CLIENT_ID, USER_ID, "PAT-1").build();
        when(enrollmentService.getProfileView(USER_ID)).thenReturn(profile);
        when(userRepository.findByIdAndClientId(USER_ID, CLIENT_ID)).thenReturn(Optional.of(user()));
        when(xtrmApiClient.updateUser(any(UpdateUserCommand.class)))
                .thenReturn(UpdateUserResult.failed(List.of("Could not reach XTRM"), true));

        assertThatThrownBy(() -> service().confirm(USER_ID, PHONE, ISO2, "123456"))
                .isInstanceOf(ExternalServiceException.class)
                .hasFieldOrPropertyWithValue("errorCode", "XTRM_UNAVAILABLE");

        verify(userRepository, never()).save(any());
    }
}

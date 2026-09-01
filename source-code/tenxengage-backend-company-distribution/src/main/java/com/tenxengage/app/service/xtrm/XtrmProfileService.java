package com.tenxengage.app.service.xtrm;

import com.tenxengage.app.dto.response.PhoneUpdateResponse;
import com.tenxengage.app.entity.User;
import com.tenxengage.app.entity.xtrm.PartnerAddress;
import com.tenxengage.app.entity.xtrm.PartnerRedemption;
import com.tenxengage.app.exception.BusinessRuleException;
import com.tenxengage.app.exception.ExternalServiceException;
import com.tenxengage.app.exception.ResourceNotFoundException;
import com.tenxengage.app.repository.UserRepository;
import com.tenxengage.app.service.xtrm.XtrmApiClient.UpdateUserCommand;
import com.tenxengage.app.service.xtrm.XtrmApiClient.UpdateUserResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Changes the current user's mobile number and keeps XTRM in sync. For an XTRM-enrolled payee this is a
 * 2-step OTP {@code UpdateUser} (XTRM texts the code to the NEW number they're setting); for a not-yet-enrolled
 * user the number is just persisted (it flows to XTRM at enrollment via {@code CreateUser}). We persist our copy
 * only after XTRM confirms, so the two never drift. Mobile is country (ISO2) + national number.
 */
@Service
public class XtrmProfileService {

    private static final Logger log = LoggerFactory.getLogger(XtrmProfileService.class);

    private final UserRepository userRepository;
    private final XtrmApiClient xtrmApiClient;
    private final XtrmEnrollmentService enrollmentService;

    public XtrmProfileService(UserRepository userRepository, XtrmApiClient xtrmApiClient,
                              XtrmEnrollmentService enrollmentService) {
        this.userRepository = userRepository;
        this.xtrmApiClient = xtrmApiClient;
        this.enrollmentService = enrollmentService;
    }

    /** Step 1: enrolled → send OTP via XTRM (no local save yet); not enrolled → persist immediately. */
    @Transactional
    public PhoneUpdateResponse initiate(UUID userId, String phone, String iso2) {
        requireSupported(iso2);
        PartnerRedemption profile = enrollmentService.getProfileView(userId);
        User user = loadUser(userId, profile.getClientId());
        if (isBlank(profile.getRecipientUserId())) {
            savePhone(user, phone, iso2);
            log.info("[step=xtrm_profile_phone] userId={} not enrolled — saved locally", userId);
            return PhoneUpdateResponse.updated(phone, iso2);
        }
        UpdateUserResult result = xtrmApiClient.updateUser(command(user, profile, phone, iso2, null));
        check(result);
        if (result.otpRequired()) {
            log.info("[step=xtrm_profile_phone_otp_sent] userId={}", userId);
            return PhoneUpdateResponse.otpSent();
        }
        // XTRM applied without an OTP round-trip — persist.
        savePhone(user, phone, iso2);
        return PhoneUpdateResponse.updated(phone, iso2);
    }

    /** Step 2: confirm with the OTP → XTRM applies → persist locally. */
    @Transactional
    public PhoneUpdateResponse confirm(UUID userId, String phone, String iso2, String otp) {
        requireSupported(iso2);
        PartnerRedemption profile = enrollmentService.getProfileView(userId);
        User user = loadUser(userId, profile.getClientId());
        if (isBlank(profile.getRecipientUserId())) {
            savePhone(user, phone, iso2); // not enrolled — no OTP needed
            return PhoneUpdateResponse.updated(phone, iso2);
        }
        UpdateUserResult result = xtrmApiClient.updateUser(command(user, profile, phone, iso2, otp));
        check(result);
        if (result.otpRequired()) {
            throw new BusinessRuleException("XTRM_PROFILE_OTP_INVALID",
                    "That code wasn't accepted. Please request a new code and try again.");
        }
        savePhone(user, phone, iso2);
        log.info("[step=xtrm_profile_phone_updated] userId={}", userId);
        return PhoneUpdateResponse.updated(phone, iso2);
    }

    // ---------------------------------------------------------------------

    private UpdateUserCommand command(User user, PartnerRedemption profile, String phone, String iso2, String otp) {
        PartnerAddress a = profile.getAddress();
        return new UpdateUserCommand(
                profile.getRecipientUserId(),
                user.getFirstName(), user.getLastName(),
                a == null ? null : a.getLine1(), a == null ? null : a.getLine2(),
                a == null ? null : a.getCity(), a == null ? null : a.getRegion(),
                a == null ? null : a.getPostalCode(), a == null ? null : a.getCountryIso2(),
                iso2, phone, otp);
    }

    private void savePhone(User user, String phone, String iso2) {
        user.setPhone(phone);
        user.setPhoneCountryIso2(iso2);
        userRepository.save(user);
    }

    private User loadUser(UUID userId, UUID clientId) {
        return userRepository.findByIdAndClientId(userId, clientId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));
    }

    private static void requireSupported(String iso2) {
        // The number must belong to a country we can build XTRM's MobilePhone (dial code) for at enrollment.
        if (!PhoneDialCodes.isSupported(iso2)) {
            throw new BusinessRuleException("UNSUPPORTED_MOBILE_COUNTRY",
                    "That country isn't supported for mobile payouts yet.");
        }
    }

    private static void check(UpdateUserResult result) {
        if (result.success()) {
            return;
        }
        if (result.retryable()) {
            throw new ExternalServiceException("XTRM_UNAVAILABLE",
                    "Payouts are temporarily unavailable. Please try again shortly.");
        }
        throw new BusinessRuleException("XTRM_PROFILE_UPDATE_FAILED",
                "We couldn't update your mobile number. Please check it and try again.");
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}

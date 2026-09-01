package com.tenxengage.app.dto.response;

/**
 * Result of a mobile-number change step. On initiate for an enrolled payee → {@code otpRequired=true} (the
 * client prompts for the code and calls confirm). Otherwise (not enrolled, or confirm) → {@code otpRequired=false}
 * with the applied {@code phone} + {@code phoneCountryIso2}.
 */
public record PhoneUpdateResponse(boolean otpRequired, String phone, String phoneCountryIso2) {

    public static PhoneUpdateResponse otpSent() {
        return new PhoneUpdateResponse(true, null, null);
    }

    public static PhoneUpdateResponse updated(String phone, String phoneCountryIso2) {
        return new PhoneUpdateResponse(false, phone, phoneCountryIso2);
    }
}

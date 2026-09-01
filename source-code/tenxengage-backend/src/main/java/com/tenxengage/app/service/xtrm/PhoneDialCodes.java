package com.tenxengage.app.service.xtrm;

import java.util.Map;

/**
 * Maps ISO-3166 alpha-2 country codes to their E.164 dial codes (digits only, no {@code +}), for building
 * XTRM mobile fields. XTRM {@code CreateUser} wants {@code MobilePhone} = dial code + national number;
 * {@code UpdateUser} wants the ISO2 + national number split. Covers common countries; an unknown code yields
 * {@code null} so the caller omits the field rather than sending a malformed number.
 */
public final class PhoneDialCodes {

    private PhoneDialCodes() {
    }

    private static final Map<String, String> DIAL = Map.ofEntries(
            Map.entry("US", "1"), Map.entry("CA", "1"), Map.entry("GB", "44"), Map.entry("IN", "91"),
            Map.entry("AU", "61"), Map.entry("DE", "49"), Map.entry("FR", "33"), Map.entry("ES", "34"),
            Map.entry("IT", "39"), Map.entry("NL", "31"), Map.entry("SE", "46"), Map.entry("NO", "47"),
            Map.entry("DK", "45"), Map.entry("FI", "358"), Map.entry("IE", "353"), Map.entry("CH", "41"),
            Map.entry("AT", "43"), Map.entry("BE", "32"), Map.entry("PT", "351"), Map.entry("PL", "48"),
            Map.entry("BR", "55"), Map.entry("MX", "52"), Map.entry("AR", "54"), Map.entry("JP", "81"),
            Map.entry("CN", "86"), Map.entry("KR", "82"), Map.entry("SG", "65"), Map.entry("HK", "852"),
            Map.entry("MY", "60"), Map.entry("ID", "62"), Map.entry("PH", "63"), Map.entry("TH", "66"),
            Map.entry("VN", "84"), Map.entry("AE", "971"), Map.entry("SA", "966"), Map.entry("ZA", "27"),
            Map.entry("NG", "234"), Map.entry("KE", "254"), Map.entry("EG", "20"), Map.entry("NZ", "64"),
            Map.entry("IL", "972"), Map.entry("TR", "90"), Map.entry("RU", "7"), Map.entry("UA", "380"),
            Map.entry("PK", "92"), Map.entry("BD", "880"), Map.entry("LK", "94"), Map.entry("NP", "977"));

    /** Dial code (digits, no {@code +}) for an ISO2 country, or {@code null} if unknown/blank. */
    public static String dialCode(String iso2) {
        if (iso2 == null) {
            return null;
        }
        return DIAL.get(iso2.trim().toUpperCase());
    }

    /** True when the ISO2 country is known to this table. */
    public static boolean isSupported(String iso2) {
        return dialCode(iso2) != null;
    }

    /**
     * Full XTRM {@code MobilePhone} (dial code + national number, digits only), or {@code null} if either
     * input is blank or the country is unknown. E.g. {@code ("US","4085551284") → "14085551284"}.
     */
    public static String mobilePhone(String iso2, String nationalNumber) {
        String code = dialCode(iso2);
        if (code == null || nationalNumber == null || nationalNumber.isBlank()) {
            return null;
        }
        return code + nationalNumber.trim();
    }
}

package com.tenxengage.app.dto.request.xtrm;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request to link a card for the current user's XTRM card payout rail / withdrawal destination.
 *
 * <p>⚠️ <b>PCI — pass-through only, NEVER persisted or logged.</b> The raw card fields
 * ({@code cardNumber}, {@code cvv}, {@code expMonth}, {@code expYear}) are forwarded once to XTRM
 * {@code LinkCard}; only the returned {@code CardToken} + a masked last-4 are stored on
 * {@code partner_linked_card}. {@link #toString()} is overridden to redact every card secret so an
 * accidental log line (validation error, request dump) can never leak a PAN/CVV.</p>
 *
 * <p>Validation is <b>structural only</b>. Domain rejections (invalid card, declined) come from XTRM.</p>
 */
public record AddCardRequest(

        @NotBlank
        @Pattern(regexp = "[0-9]{12,19}", message = "cardNumber must be 12–19 digits, no spaces")
        String cardNumber,

        @NotBlank
        @Pattern(regexp = "0[1-9]|1[0-2]", message = "expMonth must be 01–12")
        String expMonth,

        @NotBlank
        @Pattern(regexp = "[0-9]{4}", message = "expYear must be 4 digits (e.g. 2029)")
        String expYear,

        @NotBlank
        @Pattern(regexp = "[0-9]{3,4}", message = "cvv must be 3–4 digits")
        String cvv,

        @NotBlank
        @Size(max = 30)
        String cardType,

        @NotBlank
        @Size(max = 140)
        String nameOnCard,

        @NotBlank
        @Size(max = 140)
        String firstName,

        @NotBlank
        @Size(max = 140)
        String lastName,

        @NotBlank
        @Size(max = 255)
        String addressLine1,

        @Size(max = 255)
        String addressLine2,

        @NotBlank
        @Size(max = 120)
        String city,

        @NotBlank
        @Size(max = 120)
        String region,

        @NotBlank
        @Size(max = 20)
        String postalCode,

        @NotBlank
        @Pattern(regexp = "[A-Z]{2}", message = "countryIso2 must be a 2-letter uppercase ISO code")
        String countryIso2
) {

    /** Last 4 of the PAN — the ONLY card-number fragment we may retain (PCI-allowed) for the masked label. */
    public String last4() {
        return cardNumber != null && cardNumber.length() >= 4
                ? cardNumber.substring(cardNumber.length() - 4) : "";
    }

    /**
     * ⚠️ PCI: never render the PAN/CVV/expiry. This redacted form is what any framework log or error
     * dump will emit for this record.
     */
    @Override
    public String toString() {
        return "AddCardRequest[cardNumber=****" + last4() + ", cvv=***, exp=**/****, cardType=" + cardType
                + ", nameOnCard=" + nameOnCard + ", city=" + city + ", region=" + region
                + ", countryIso2=" + countryIso2 + "]";
    }
}

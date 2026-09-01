package com.tenxengage.app.entity.xtrm;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Payout address (PII) required by XTRM {@code CreateUser}, grouped as a value object mapped to the
 * existing {@code partner_redemption} address columns (no schema change vs the prior 6 flat columns).
 * {@code line1} + {@code countryIso2} are the minimum XTRM requires — see {@link #isEnrollable()}.
 */
@Embeddable
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class PartnerAddress {

    @Column(name = "address_line1", length = 255)
    private String line1;

    @Column(name = "address_line2", length = 255)
    private String line2;

    @Column(name = "city", length = 120)
    private String city;

    @Column(name = "region", length = 120)
    private String region;

    @Column(name = "postal_code", length = 20)
    private String postalCode;

    @Column(name = "country_iso2", length = 2)
    private String countryIso2;

    /** XTRM {@code CreateUser} requires at least {@code line1} + a 2-letter country. */
    public boolean isEnrollable() {
        return line1 != null && !line1.isBlank()
                && countryIso2 != null && !countryIso2.isBlank();
    }
}

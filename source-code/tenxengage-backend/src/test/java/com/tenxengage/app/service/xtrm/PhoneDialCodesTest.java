package com.tenxengage.app.service.xtrm;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PhoneDialCodesTest {

    @Test
    void mobilePhone_combinesDialCodeAndNationalNumber() {
        assertThat(PhoneDialCodes.mobilePhone("US", "4085551284")).isEqualTo("14085551284");
        assertThat(PhoneDialCodes.mobilePhone("IN", "8377906689")).isEqualTo("918377906689");
        assertThat(PhoneDialCodes.mobilePhone("GB", "7911123456")).isEqualTo("447911123456");
    }

    @Test
    void mobilePhone_isCaseInsensitiveOnCountry() {
        assertThat(PhoneDialCodes.mobilePhone("us", "4085551284")).isEqualTo("14085551284");
    }

    @Test
    void mobilePhone_nullWhenUnknownCountryOrBlankNumber() {
        assertThat(PhoneDialCodes.mobilePhone("ZZ", "4085551284")).isNull();
        assertThat(PhoneDialCodes.mobilePhone("US", "")).isNull();
        assertThat(PhoneDialCodes.mobilePhone("US", null)).isNull();
        assertThat(PhoneDialCodes.mobilePhone(null, "4085551284")).isNull();
    }

    @Test
    void isSupported_reflectsTheTable() {
        assertThat(PhoneDialCodes.isSupported("US")).isTrue();
        assertThat(PhoneDialCodes.isSupported("in")).isTrue();
        assertThat(PhoneDialCodes.isSupported("ZZ")).isFalse();
        assertThat(PhoneDialCodes.isSupported(null)).isFalse();
    }
}

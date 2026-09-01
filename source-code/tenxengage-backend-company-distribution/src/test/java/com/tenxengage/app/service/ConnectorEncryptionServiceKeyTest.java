package com.tenxengage.app.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The key protects the authority to move a partner company's money.
 *
 * <p>An unset property used to fall back to a value committed to this repository, which is
 * indistinguishable from no encryption at all for anyone who can read the source. That was tolerable while
 * this only wrapped connector config; it stopped being tolerable when it started wrapping the credentials
 * that let us pay a company's sellers out of that company's wallet.</p>
 */
class ConnectorEncryptionServiceKeyTest {

    private static final String DEV_DEFAULT = "0123456789abcdef0123456789abcdef";
    private static final String REAL_KEY = "abcdefghijklmnopqrstuvwxyz012345";

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void refusesToStartOnTheDevDefaultOutsideLocal() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("prod");

        assertThatThrownBy(() -> new ConnectorEncryptionService(DEV_DEFAULT, objectMapper, env))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("app.connector.encryption-key");
    }

    @Test
    void refusesToStartOnABlankKeyOutsideLocal() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("prod");

        assertThatThrownBy(() -> new ConnectorEncryptionService("", objectMapper, env))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void allowsTheDevDefaultInLocal() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("local");

        assertThatCode(() -> new ConnectorEncryptionService(DEV_DEFAULT, objectMapper, env))
                .doesNotThrowAnyException();
    }

    @Test
    void allowsARealKeyAnywhere() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("prod");

        assertThatCode(() -> new ConnectorEncryptionService(REAL_KEY, objectMapper, env))
                .doesNotThrowAnyException();
    }

    /**
     * No active profile is the bare {@code java -jar} case. It is not local, so it must not get the
     * development default — a deployment that forgot to set its profile is exactly the one that also forgot
     * to set its key.
     */
    @Test
    void refusesTheDevDefaultWhenNoProfileIsActive() {
        assertThatThrownBy(() -> new ConnectorEncryptionService(DEV_DEFAULT, objectMapper, new MockEnvironment()))
                .isInstanceOf(IllegalStateException.class);
    }
}

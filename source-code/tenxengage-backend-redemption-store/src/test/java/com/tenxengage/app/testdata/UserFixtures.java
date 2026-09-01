package com.tenxengage.app.testdata;

import com.tenxengage.app.entity.User;
import com.tenxengage.app.entity.enums.UserStatus;

import java.util.UUID;

public final class UserFixtures {

    private static final String BCRYPT_PLACEHOLDER =
            "$2a$10$dummyBcryptHashForTesting123456789012345678901234";

    private UserFixtures() {
    }

    public static User.UserBuilder activeUser(UUID clientId, UUID partnerCompanyId) {
        return User.builder()
                .email("user-" + UUID.randomUUID() + "@test.com")
                .firstName("Test")
                .lastName("User")
                .passwordHash(BCRYPT_PLACEHOLDER)
                .status(UserStatus.ACTIVE)
                .clientId(clientId)
                .partnerCompanyId(partnerCompanyId)
                .countryCode("US");
    }

    public static User.UserBuilder pendingUser(UUID clientId, UUID partnerCompanyId) {
        return User.builder()
                .email("pending-" + UUID.randomUUID() + "@test.com")
                .firstName("Pending")
                .lastName("User")
                .passwordHash("$2a$10$placeholder")
                .status(UserStatus.PENDING_VERIFICATION)
                .clientId(clientId)
                .partnerCompanyId(partnerCompanyId);
    }

    public static User.UserBuilder anonymizedUser(UUID clientId, UUID partnerCompanyId) {
        return User.builder()
                .email("anon-" + UUID.randomUUID() + "@redacted.local")
                .firstName("REDACTED")
                .lastName("REDACTED")
                .passwordHash(BCRYPT_PLACEHOLDER)
                .status(UserStatus.ANONYMIZED)
                .clientId(clientId)
                .partnerCompanyId(partnerCompanyId);
    }

    public static User.UserBuilder suspendedUser(UUID clientId, UUID partnerCompanyId) {
        return User.builder()
                .email("suspended-" + UUID.randomUUID() + "@test.com")
                .firstName("Suspended")
                .lastName("User")
                .passwordHash(BCRYPT_PLACEHOLDER)
                .status(UserStatus.SUSPENDED)
                .clientId(clientId)
                .partnerCompanyId(partnerCompanyId);
    }

    public static User.UserBuilder restrictedUser(UUID clientId, UUID partnerCompanyId) {
        return User.builder()
                .email("restricted-" + UUID.randomUUID() + "@test.com")
                .firstName("Restricted")
                .lastName("User")
                .passwordHash(BCRYPT_PLACEHOLDER)
                .status(UserStatus.RESTRICTED)
                .clientId(clientId)
                .partnerCompanyId(partnerCompanyId);
    }
}

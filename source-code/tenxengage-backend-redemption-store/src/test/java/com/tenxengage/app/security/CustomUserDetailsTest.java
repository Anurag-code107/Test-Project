package com.tenxengage.app.security;

import com.tenxengage.app.entity.ClientRole;
import com.tenxengage.app.entity.User;
import com.tenxengage.app.entity.enums.UserStatus;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class CustomUserDetailsTest {

    @Test
    void authorities_containRoleUser_andRoleDerivedFromBaseRoleName() {
        User user = baseUser();
        ClientRole role = ClientRole.builder()
                .clientId(UUID.randomUUID())
                .name("Client Admin")
                .baseRoleName("CLIENT_ADMIN")
                .build();
        user.setClientRole(role);

        Set<String> granted = new CustomUserDetails(user).getAuthorities().stream()
                .map(Object::toString)
                .collect(Collectors.toSet());

        assertThat(granted).containsExactlyInAnyOrder("ROLE_USER", "ROLE_CLIENT_ADMIN");
    }

    @Test
    void authorities_grantPartnerAdminRole_forPartnerAdminBaseRole() {
        User user = baseUser();
        user.setClientRole(ClientRole.builder().baseRoleName("PARTNER_ADMIN").build());

        Set<String> granted = new CustomUserDetails(user).getAuthorities().stream()
                .map(Object::toString)
                .collect(Collectors.toSet());

        assertThat(granted).containsExactlyInAnyOrder("ROLE_USER", "ROLE_PARTNER_ADMIN");
    }

    @Test
    void authorities_grantPartnerSellerRole_forPartnerSellerBaseRole() {
        User user = baseUser();
        user.setClientRole(ClientRole.builder().baseRoleName("PARTNER_SELLER").build());

        Set<String> granted = new CustomUserDetails(user).getAuthorities().stream()
                .map(Object::toString)
                .collect(Collectors.toSet());

        assertThat(granted).containsExactlyInAnyOrder("ROLE_USER", "ROLE_PARTNER_SELLER");
    }

    @Test
    void authorities_onlyRoleUser_whenClientRoleIsNull() {
        User user = baseUser();
        user.setClientRole(null);

        Set<String> granted = new CustomUserDetails(user).getAuthorities().stream()
                .map(Object::toString)
                .collect(Collectors.toSet());

        assertThat(granted).containsExactly("ROLE_USER");
    }

    @Test
    void authorities_onlyRoleUser_whenBaseRoleNameIsNull() {
        User user = baseUser();
        user.setClientRole(ClientRole.builder().name("Custom Role").baseRoleName(null).build());

        Set<String> granted = new CustomUserDetails(user).getAuthorities().stream()
                .map(Object::toString)
                .collect(Collectors.toSet());

        assertThat(granted).containsExactly("ROLE_USER");
    }

    @Test
    void isTenxAdmin_trueWhenClientIdIsNull() {
        User user = baseUser();
        user.setClientId(null);

        assertThat(new CustomUserDetails(user).isTenxAdmin()).isTrue();
    }

    @Test
    void isTenxAdmin_falseWhenClientIdPresent() {
        User user = baseUser();
        user.setClientId(UUID.randomUUID());

        assertThat(new CustomUserDetails(user).isTenxAdmin()).isFalse();
    }

    private User baseUser() {
        User user = User.builder()
                .email("test@example.com")
                .firstName("Test")
                .lastName("User")
                .passwordHash("$2a$10$hash")
                .status(UserStatus.ACTIVE)
                .clientId(UUID.randomUUID())
                .build();
        user.setId(UUID.randomUUID());
        return user;
    }
}

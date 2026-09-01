package com.tenxengage.app.dto.response;

import com.tenxengage.app.entity.ClientRole;
import com.tenxengage.app.entity.User;
import com.tenxengage.app.entity.enums.UserStatus;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pure unit coverage for the {@link UserResponse#from} factories.
 * BUG-086: the no-arg {@code from(User)} overload (used by every read in
 * {@code UserService.getUsers} / {@code getUserById} / {@code createUser} /
 * {@code updateUser}) hard-coded {@code clientRoleName} to {@code null},
 * which surfaced on the frontend as "No role" for every row in the
 * Client Admin → Settings → Users tab. This test fails if that overload
 * regresses to dropping the resolved role name.
 */
class UserResponseTest {

    @Test
    void from_user_resolvesClientRoleNameWhenClientRoleIsLoaded() {
        ClientRole role = new ClientRole();
        role.setId(UUID.randomUUID());
        role.setName("Activity Approver");
        role.setBaseRoleName("ACTIVITY_APPROVER");

        User user = baseUser();
        user.setClientRoleId(role.getId());
        user.setClientRole(role);

        UserResponse response = UserResponse.from(user);

        assertThat(response.clientRoleId()).isEqualTo(role.getId());
        assertThat(response.clientRoleName()).isEqualTo("Activity Approver");
    }

    @Test
    void from_user_returnsNullClientRoleNameWhenUserHasNoRole() {
        User user = baseUser();

        UserResponse response = UserResponse.from(user);

        assertThat(response.clientRoleId()).isNull();
        assertThat(response.clientRoleName()).isNull();
    }

    private User baseUser() {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail("user@example.com");
        user.setFirstName("Test");
        user.setLastName("User");
        user.setStatus(UserStatus.ACTIVE);
        user.setClientId(UUID.randomUUID());
        user.setMetadata("{}");
        return user;
    }
}

package com.tenxengage.app.integration;

import com.tenxengage.app.AbstractLocalIntegrationTest;
import com.tenxengage.app.entity.User;
import com.tenxengage.app.repository.UserRepository;
import com.tenxengage.app.service.PermissionService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * BUG-007 regression guard. The Builder Config and Activity Category write endpoints
 * are annotated {@code @RequiresPermission("action.builder.manage")}. Without the V5
 * seed migration the key was neither registered in the permission catalog nor granted
 * to any role, so {@link PermissionService#resolveEffectivePermissions(java.util.UUID)}
 * returned a set that did not contain it for every caller, and PermissionAspect threw
 * AccessDeniedException (HTTP 403) on every write.
 *
 * <p>This test walks the real permission-resolution pipeline against the seeded
 * Client Admin user and asserts the key resolves. It fails on pre-V5 code and passes
 * only when both the {@code permissions} and {@code client_permission_grants} /
 * {@code client_role_permissions} rows from V5 are in place, closing the exact
 * regression described in the bug.
 */
@Tag("integration")
class BuilderManagePermissionIntegrationTest extends AbstractLocalIntegrationTest {

    private static final String CLIENT_ADMIN_EMAIL = "clientadmin@acme.com";
    private static final String BUILDER_MANAGE_KEY = "action.builder.manage";

    @Autowired private UserRepository userRepository;
    @Autowired private PermissionService permissionService;

    @Test
    void clientAdmin_resolvesActionBuilderManage() {
        User clientAdmin = userRepository.findByEmail(CLIENT_ADMIN_EMAIL)
                .orElseThrow(() -> new AssertionError(
                        "Seeded Client Admin user " + CLIENT_ADMIN_EMAIL
                                + " is missing — V3 baseline seed did not run."));

        Set<String> effective = permissionService.resolveEffectivePermissions(clientAdmin.getId());

        assertThat(effective)
                .as("Client Admin must hold action.builder.manage — otherwise every "
                        + "BuilderConfig/ActivityCategory write endpoint returns 403.")
                .contains(BUILDER_MANAGE_KEY);
    }
}

package com.tenxengage.app.service;

import com.tenxengage.app.entity.ClientPermissionGrant;
import com.tenxengage.app.entity.ClientRole;
import com.tenxengage.app.entity.ClientRolePermission;
import com.tenxengage.app.entity.CompanyPermissionOverride;
import com.tenxengage.app.entity.Permission;
import com.tenxengage.app.entity.User;
import com.tenxengage.app.entity.UserPermissionOverride;
import com.tenxengage.app.entity.enums.UserStatus;
import com.tenxengage.app.exception.BusinessRuleException;
import com.tenxengage.app.repository.ClientPermissionGrantRepository;
import com.tenxengage.app.repository.ClientRolePermissionRepository;
import com.tenxengage.app.repository.ClientRoleRepository;
import com.tenxengage.app.repository.CompanyPermissionOverrideRepository;
import com.tenxengage.app.repository.PermissionRepository;
import com.tenxengage.app.repository.UserPermissionOverrideRepository;
import com.tenxengage.app.repository.UserRepository;
import com.tenxengage.app.security.TenantValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PermissionServiceTest {

    @Mock
    private PermissionRepository permissionRepository;
    @Mock
    private ClientRoleRepository clientRoleRepository;
    @Mock
    private ClientRolePermissionRepository clientRolePermissionRepository;
    @Mock
    private CompanyPermissionOverrideRepository companyOverrideRepository;
    @Mock
    private UserPermissionOverrideRepository userOverrideRepository;
    @Mock
    private ClientPermissionGrantRepository clientPermissionGrantRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private TenantValidator tenantValidator;

    @InjectMocks
    private PermissionService permissionService;

    private UUID userId;
    private UUID clientId;
    private UUID partnerCompanyId;
    private UUID clientRoleId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        clientId = UUID.randomUUID();
        partnerCompanyId = UUID.randomUUID();
        clientRoleId = UUID.randomUUID();
    }

    // -------------------------------------------------------------------------
    // resolveEffectivePermissions() - 4-layer model
    // -------------------------------------------------------------------------

    @Test
    void resolveEffectivePermissions_grantsTenxAdminAllPermissions() {
        // TENX_ADMIN: no clientId and no clientRoleId
        User tenxAdmin = User.builder()
                .email("admin@tenx.com").firstName("Admin").lastName("User")
                .passwordHash("hash").status(UserStatus.ACTIVE)
                .build();
        tenxAdmin.setId(userId);

        Permission p1 = Permission.builder().permissionKey("action.claim.submit").build();
        Permission p2 = Permission.builder().permissionKey("action.users.manage").build();

        when(userRepository.findByIdWithClientRole(userId)).thenReturn(Optional.of(tenxAdmin));
        when(permissionRepository.findAllByOrderBySortOrderAsc()).thenReturn(List.of(p1, p2));

        Set<String> permissions = permissionService.resolveEffectivePermissions(userId);

        assertThat(permissions).containsExactlyInAnyOrder("action.claim.submit", "action.users.manage");
    }

    @Test
    void resolveEffectivePermissions_resolvesRolePermissions() {
        User user = buildPartnerUser();
        user.setClientRoleId(clientRoleId);

        ClientRolePermission grant1 = ClientRolePermission.builder()
                .clientRoleId(clientRoleId).permissionKey("action.claim.submit").granted(true).build();
        ClientRolePermission grant2 = ClientRolePermission.builder()
                .clientRoleId(clientRoleId).permissionKey("action.claim.view").granted(true).build();

        when(userRepository.findByIdWithClientRole(userId)).thenReturn(Optional.of(user));
        when(clientPermissionGrantRepository.findByClientId(clientId)).thenReturn(List.of(
                ClientPermissionGrant.builder().clientId(clientId).permissionKey("action.claim.submit").granted(true).build(),
                ClientPermissionGrant.builder().clientId(clientId).permissionKey("action.claim.view").granted(true).build()
        ));
        when(clientRolePermissionRepository.findByClientRoleId(clientRoleId)).thenReturn(List.of(grant1, grant2));
        when(companyOverrideRepository.findByClientIdAndPartnerCompanyId(clientId, partnerCompanyId))
                .thenReturn(List.of());
        when(userOverrideRepository.findByClientIdAndUserId(clientId, userId)).thenReturn(List.of());

        Set<String> permissions = permissionService.resolveEffectivePermissions(userId);

        assertThat(permissions).containsExactlyInAnyOrder("action.claim.submit", "action.claim.view");
    }

    @Test
    void resolveEffectivePermissions_companyOverrideRestricts() {
        User user = buildPartnerUser();
        user.setClientRoleId(clientRoleId);

        ClientRolePermission grant = ClientRolePermission.builder()
                .clientRoleId(clientRoleId).permissionKey("action.claim.submit").granted(true).build();
        CompanyPermissionOverride restriction = CompanyPermissionOverride.builder()
                .clientId(clientId).partnerCompanyId(partnerCompanyId)
                .permissionKey("action.claim.submit").granted(false).build();

        when(userRepository.findByIdWithClientRole(userId)).thenReturn(Optional.of(user));
        when(clientPermissionGrantRepository.findByClientId(clientId)).thenReturn(List.of(
                ClientPermissionGrant.builder().clientId(clientId).permissionKey("action.claim.submit").granted(true).build()
        ));
        when(clientRolePermissionRepository.findByClientRoleId(clientRoleId)).thenReturn(List.of(grant));
        when(companyOverrideRepository.findByClientIdAndPartnerCompanyId(clientId, partnerCompanyId))
                .thenReturn(List.of(restriction));
        when(userOverrideRepository.findByClientIdAndUserId(clientId, userId)).thenReturn(List.of());

        Set<String> permissions = permissionService.resolveEffectivePermissions(userId);

        assertThat(permissions).isEmpty();
    }

    @Test
    void resolveEffectivePermissions_userOverrideRestricts() {
        User user = buildPartnerUser();
        user.setClientRoleId(clientRoleId);

        ClientRolePermission grant = ClientRolePermission.builder()
                .clientRoleId(clientRoleId).permissionKey("action.users.manage").granted(true).build();
        UserPermissionOverride restriction = UserPermissionOverride.builder()
                .clientId(clientId).userId(userId)
                .permissionKey("action.users.manage").granted(false).build();

        when(userRepository.findByIdWithClientRole(userId)).thenReturn(Optional.of(user));
        when(clientPermissionGrantRepository.findByClientId(clientId)).thenReturn(List.of(
                ClientPermissionGrant.builder().clientId(clientId).permissionKey("action.users.manage").granted(true).build()
        ));
        when(clientRolePermissionRepository.findByClientRoleId(clientRoleId)).thenReturn(List.of(grant));
        when(companyOverrideRepository.findByClientIdAndPartnerCompanyId(clientId, partnerCompanyId))
                .thenReturn(List.of());
        when(userOverrideRepository.findByClientIdAndUserId(clientId, userId)).thenReturn(List.of(restriction));

        Set<String> permissions = permissionService.resolveEffectivePermissions(userId);

        assertThat(permissions).isEmpty();
    }

    // -------------------------------------------------------------------------
    // checkPermission() and requirePermission()
    // -------------------------------------------------------------------------

    @Test
    void checkPermission_returnsTrueForGranted() {
        User user = buildPartnerUser();
        user.setClientRoleId(clientRoleId);

        ClientRolePermission grant = ClientRolePermission.builder()
                .clientRoleId(clientRoleId).permissionKey("action.claim.submit").granted(true).build();

        when(userRepository.findByIdWithClientRole(userId)).thenReturn(Optional.of(user));
        when(clientPermissionGrantRepository.findByClientId(clientId)).thenReturn(List.of(
                ClientPermissionGrant.builder().clientId(clientId).permissionKey("action.claim.submit").granted(true).build()
        ));
        when(clientRolePermissionRepository.findByClientRoleId(clientRoleId)).thenReturn(List.of(grant));
        when(companyOverrideRepository.findByClientIdAndPartnerCompanyId(clientId, partnerCompanyId))
                .thenReturn(List.of());
        when(userOverrideRepository.findByClientIdAndUserId(clientId, userId)).thenReturn(List.of());

        assertThat(permissionService.checkPermission(userId, "action.claim.submit")).isTrue();
    }

    @Test
    void checkPermission_returnsFalseForDenied() {
        User user = buildPartnerUser();
        user.setClientRoleId(clientRoleId);

        when(userRepository.findByIdWithClientRole(userId)).thenReturn(Optional.of(user));
        when(clientPermissionGrantRepository.findByClientId(clientId)).thenReturn(List.of(
                ClientPermissionGrant.builder().clientId(clientId).permissionKey("action.users.manage").granted(true).build()
        ));
        when(clientRolePermissionRepository.findByClientRoleId(clientRoleId)).thenReturn(List.of());
        when(companyOverrideRepository.findByClientIdAndPartnerCompanyId(clientId, partnerCompanyId))
                .thenReturn(List.of());
        when(userOverrideRepository.findByClientIdAndUserId(clientId, userId)).thenReturn(List.of());

        assertThat(permissionService.checkPermission(userId, "action.users.manage")).isFalse();
    }

    @Test
    void requirePermission_throwsAccessDeniedWhenMissing() {
        User user = buildPartnerUser();
        user.setClientRoleId(clientRoleId);

        when(tenantValidator.getCurrentUserId()).thenReturn(userId);
        when(userRepository.findByIdWithClientRole(userId)).thenReturn(Optional.of(user));
        when(clientPermissionGrantRepository.findByClientId(clientId)).thenReturn(List.of());
        when(clientRolePermissionRepository.findByClientRoleId(clientRoleId)).thenReturn(List.of());
        when(companyOverrideRepository.findByClientIdAndPartnerCompanyId(clientId, partnerCompanyId))
                .thenReturn(List.of());
        when(userOverrideRepository.findByClientIdAndUserId(clientId, userId)).thenReturn(List.of());

        assertThatThrownBy(() -> permissionService.requirePermission("action.users.manage"))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("action.users.manage");
    }

    // -------------------------------------------------------------------------
    // createClientRole()
    // -------------------------------------------------------------------------

    @Test
    void createClientRole_rejectsDuplicateName() {
        when(clientRoleRepository.existsByClientIdAndName(clientId, "Existing")).thenReturn(true);

        assertThatThrownBy(() -> permissionService.createClientRole(
                clientId, "Existing", "desc", "EXTERNAL", Map.of()))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    void createClientRole_savesRoleWithCorrectRoleType() {
        when(clientRoleRepository.existsByClientIdAndName(clientId, "Internal Role")).thenReturn(false);
        when(clientRoleRepository.save(any(ClientRole.class))).thenAnswer(invocation -> {
            ClientRole role = invocation.getArgument(0);
            role.setId(UUID.randomUUID());
            return role;
        });

        ClientRole result = permissionService.createClientRole(
                clientId, "Internal Role", "desc", "INTERNAL", Map.of());

        assertThat(result.getRoleType()).isEqualTo("INTERNAL");
        assertThat(result.getName()).isEqualTo("Internal Role");
        assertThat(result.isSystem()).isFalse();
    }

    @Test
    void createClientRole_savesInitialPermissions() {
        when(clientRoleRepository.existsByClientIdAndName(clientId, "New Role")).thenReturn(false);
        when(clientRoleRepository.save(any(ClientRole.class))).thenAnswer(invocation -> {
            ClientRole role = invocation.getArgument(0);
            role.setId(UUID.randomUUID());
            return role;
        });

        Map<String, Boolean> initialPerms = Map.of(
                "module.home", true,
                "action.users.create", true
        );

        permissionService.createClientRole(clientId, "New Role", "desc", "EXTERNAL", initialPerms);

        verify(clientRolePermissionRepository).saveAll(argThat((List<ClientRolePermission> perms) ->
                perms.size() == 2));
    }

    @Test
    void createClientRole_createsRoleWithEmptyPermissions() {
        when(clientRoleRepository.existsByClientIdAndName(clientId, "Empty")).thenReturn(false);
        when(clientRoleRepository.save(any(ClientRole.class))).thenAnswer(invocation -> {
            ClientRole role = invocation.getArgument(0);
            role.setId(UUID.randomUUID());
            return role;
        });

        ClientRole result = permissionService.createClientRole(clientId, "Empty", null, "EXTERNAL", Map.of());

        assertThat(result).isNotNull();
        verify(clientRolePermissionRepository, never()).saveAll(any());
    }

    // -------------------------------------------------------------------------
    // cloneClientRole()
    // -------------------------------------------------------------------------

    @Test
    void cloneClientRole_copiesAllPermissionsFromSource() {
        ClientRole source = ClientRole.builder()
                .clientId(clientId).name("Source").roleType("EXTERNAL")
                .system(false).defaultRole(false).build();
        source.setId(clientRoleId);

        ClientRolePermission sp1 = ClientRolePermission.builder()
                .clientRoleId(clientRoleId).permissionKey("module.home").granted(true).build();
        ClientRolePermission sp2 = ClientRolePermission.builder()
                .clientRoleId(clientRoleId).permissionKey("action.claim.submit").granted(true).build();

        when(clientRoleRepository.findById(clientRoleId)).thenReturn(Optional.of(source));
        when(clientRoleRepository.existsByClientIdAndName(clientId, "Cloned")).thenReturn(false);
        when(clientRoleRepository.save(any(ClientRole.class))).thenAnswer(invocation -> {
            ClientRole role = invocation.getArgument(0);
            role.setId(UUID.randomUUID());
            return role;
        });
        when(clientRolePermissionRepository.findByClientRoleId(clientRoleId))
                .thenReturn(List.of(sp1, sp2));

        permissionService.cloneClientRole(clientRoleId, "Cloned", "desc");

        verify(clientRolePermissionRepository).saveAll(argThat((List<ClientRolePermission> perms) ->
                perms.size() == 2));
    }

    @Test
    void cloneClientRole_setsCorrectRoleType() {
        ClientRole source = ClientRole.builder()
                .clientId(clientId).name("Source").roleType("INTERNAL")
                .system(false).defaultRole(false).build();
        source.setId(clientRoleId);

        when(clientRoleRepository.findById(clientRoleId)).thenReturn(Optional.of(source));
        when(clientRoleRepository.existsByClientIdAndName(clientId, "Cloned")).thenReturn(false);
        when(clientRoleRepository.save(any(ClientRole.class))).thenAnswer(invocation -> {
            ClientRole role = invocation.getArgument(0);
            role.setId(UUID.randomUUID());
            return role;
        });
        when(clientRolePermissionRepository.findByClientRoleId(clientRoleId))
                .thenReturn(List.of());

        ClientRole result = permissionService.cloneClientRole(clientRoleId, "Cloned", "desc");

        assertThat(result.getRoleType()).isEqualTo("INTERNAL");
    }

    @Test
    void cloneClientRole_rejectsDuplicateName() {
        ClientRole source = ClientRole.builder()
                .clientId(clientId).name("Source").roleType("EXTERNAL")
                .system(false).defaultRole(false).build();
        source.setId(clientRoleId);

        when(clientRoleRepository.findById(clientRoleId)).thenReturn(Optional.of(source));
        when(clientRoleRepository.existsByClientIdAndName(clientId, "Duplicate")).thenReturn(true);

        assertThatThrownBy(() -> permissionService.cloneClientRole(clientRoleId, "Duplicate", "desc"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("already exists");
    }

    // -------------------------------------------------------------------------
    // deleteClientRole()
    // -------------------------------------------------------------------------

    @Test
    void deleteClientRole_rejectsSystemRoles() {
        ClientRole systemRole = ClientRole.builder()
                .clientId(clientId).name("System Role").system(true)
                .baseRoleName("PARTNER_SELLER").build();
        systemRole.setId(clientRoleId);

        when(clientRoleRepository.findById(clientRoleId)).thenReturn(Optional.of(systemRole));

        assertThatThrownBy(() -> permissionService.deleteClientRole(clientRoleId))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("system roles");
    }

    // -------------------------------------------------------------------------
    // updateRolePermissions()
    // -------------------------------------------------------------------------

    @Test
    void updateRolePermissions_rejectsRemovingImmutableAdminPermission() {
        ClientRole adminRole = ClientRole.builder()
                .clientId(clientId).name("Client Admin").system(true)
                .baseRoleName("CLIENT_ADMIN").build();
        adminRole.setId(clientRoleId);

        when(clientRoleRepository.findById(clientRoleId)).thenReturn(Optional.of(adminRole));

        // Attempting to remove an immutable permission from CLIENT_ADMIN role should fail
        assertThatThrownBy(() -> permissionService.updateRolePermissions(
                clientRoleId, Map.of("action.roles.view", false)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("action.roles.view");
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private User buildPartnerUser() {
        User user = User.builder()
                .email("seller@test.com").firstName("Test").lastName("User")
                .passwordHash("hash").status(UserStatus.ACTIVE)
                .clientId(clientId).partnerCompanyId(partnerCompanyId)
                .build();
        user.setId(userId);
        return user;
    }
}

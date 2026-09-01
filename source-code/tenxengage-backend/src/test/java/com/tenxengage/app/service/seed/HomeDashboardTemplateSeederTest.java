package com.tenxengage.app.service.seed;

import com.tenxengage.app.entity.ClientRole;
import com.tenxengage.app.entity.HomeDashboardTemplate;
import com.tenxengage.app.repository.ClientRoleRepository;
import com.tenxengage.app.repository.HomeDashboardTemplateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class HomeDashboardTemplateSeederTest {

    @Mock
    private HomeDashboardTemplateRepository templateRepository;

    @Mock
    private ClientRoleRepository clientRoleRepository;

    @InjectMocks
    private HomeDashboardTemplateSeeder seeder;

    private final UUID clientId = UUID.fromString("a0000000-0000-0000-0000-000000000001");

    @BeforeEach
    void setUp() {
        // default: no templates exist
        when(templateRepository.findByClientIdAndName(eq(clientId), any()))
                .thenReturn(Optional.empty());
        when(templateRepository.save(any(HomeDashboardTemplate.class)))
                .thenAnswer(inv -> {
                    HomeDashboardTemplate t = inv.getArgument(0);
                    t.setId(UUID.randomUUID());
                    return t;
                });
        when(clientRoleRepository.save(any(ClientRole.class)))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void seedForClient_freshTenant_createsAllThreeTemplates() {
        // No roles exist in this test variant
        when(clientRoleRepository.findByClientIdAndBaseRoleNameAndSystemTrue(eq(clientId), any()))
                .thenReturn(Optional.empty());

        seeder.seedForClient(clientId);

        verify(templateRepository, times(3)).save(any(HomeDashboardTemplate.class));
    }

    @Test
    void seedForClient_assignsSeededRolesToCorrectTemplates() {
        Map<String, ClientRole> rolesByBaseName = new HashMap<>();
        for (String baseName : new String[]{"CLIENT_ADMIN", "PARTNER_ADMIN", "PARTNER_SELLER", "ACTIVITY_APPROVER"}) {
            ClientRole role = ClientRole.builder()
                    .clientId(clientId)
                    .name(baseName)
                    .baseRoleName(baseName)
                    .system(true)
                    .roleType(baseName.startsWith("PARTNER") ? "EXTERNAL" : "INTERNAL")
                    .build();
            role.setId(UUID.randomUUID());
            rolesByBaseName.put(baseName, role);
        }
        when(clientRoleRepository.findByClientIdAndBaseRoleNameAndSystemTrue(eq(clientId), any()))
                .thenAnswer(inv -> Optional.ofNullable(rolesByBaseName.get((String) inv.getArgument(1))));

        seeder.seedForClient(clientId);

        // All 4 seeded roles got an assignment.
        verify(clientRoleRepository, times(4)).save(any(ClientRole.class));
        assertThat(rolesByBaseName.get("CLIENT_ADMIN").getHomeDashboardTemplateId()).isNotNull();
        assertThat(rolesByBaseName.get("PARTNER_ADMIN").getHomeDashboardTemplateId()).isNotNull();
        assertThat(rolesByBaseName.get("PARTNER_SELLER").getHomeDashboardTemplateId()).isNotNull();
        assertThat(rolesByBaseName.get("ACTIVITY_APPROVER").getHomeDashboardTemplateId()).isNotNull();

        // Partner Admin and Partner Seller share the same template (Partner User).
        assertThat(rolesByBaseName.get("PARTNER_ADMIN").getHomeDashboardTemplateId())
                .isEqualTo(rolesByBaseName.get("PARTNER_SELLER").getHomeDashboardTemplateId());

        // Client Admin and Activity Approver get different INTERNAL templates.
        assertThat(rolesByBaseName.get("CLIENT_ADMIN").getHomeDashboardTemplateId())
                .isNotEqualTo(rolesByBaseName.get("ACTIVITY_APPROVER").getHomeDashboardTemplateId());
    }

    @Test
    void seedForClient_isIdempotent_whenTemplatesAlreadyExist() {
        HomeDashboardTemplate existing = HomeDashboardTemplate.builder()
                .clientId(clientId).name("Client Admin").roleType("INTERNAL")
                .layout("{\"rows\":[]}").system(true).build();
        existing.setId(UUID.randomUUID());
        when(templateRepository.findByClientIdAndName(eq(clientId), any()))
                .thenAnswer(inv -> {
                    String name = inv.getArgument(1);
                    return "Client Admin".equals(name) ? Optional.of(existing) : Optional.empty();
                });
        when(clientRoleRepository.findByClientIdAndBaseRoleNameAndSystemTrue(eq(clientId), any()))
                .thenReturn(Optional.empty());

        seeder.seedForClient(clientId);

        // Existing Client Admin template NOT re-saved; only 2 new templates created.
        verify(templateRepository, times(2)).save(any(HomeDashboardTemplate.class));
    }

    @Test
    void seedForClient_skipsRoleAssignmentWhenAlreadySet() {
        UUID existingTemplateId = UUID.randomUUID();
        ClientRole clientAdminRole = ClientRole.builder()
                .clientId(clientId)
                .name("Client Admin")
                .baseRoleName("CLIENT_ADMIN")
                .system(true)
                .roleType("INTERNAL")
                .homeDashboardTemplateId(existingTemplateId)   // already assigned
                .build();
        clientAdminRole.setId(UUID.randomUUID());
        when(clientRoleRepository.findByClientIdAndBaseRoleNameAndSystemTrue(eq(clientId), eq("CLIENT_ADMIN")))
                .thenReturn(Optional.of(clientAdminRole));
        when(clientRoleRepository.findByClientIdAndBaseRoleNameAndSystemTrue(eq(clientId), any()))
                .thenAnswer(inv -> "CLIENT_ADMIN".equals(inv.getArgument(1))
                        ? Optional.of(clientAdminRole)
                        : Optional.empty());

        seeder.seedForClient(clientId);

        // Client Admin role NOT re-saved (already had a template assignment).
        verify(clientRoleRepository, never()).save(any(ClientRole.class));
        assertThat(clientAdminRole.getHomeDashboardTemplateId()).isEqualTo(existingTemplateId);
    }
}

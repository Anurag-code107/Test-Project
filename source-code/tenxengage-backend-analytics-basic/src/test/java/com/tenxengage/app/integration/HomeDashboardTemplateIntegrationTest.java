package com.tenxengage.app.integration;

import com.tenxengage.app.AbstractLocalIntegrationTest;
import com.tenxengage.app.entity.Client;
import com.tenxengage.app.entity.ClientRole;
import com.tenxengage.app.entity.HomeDashboardTemplate;
import com.tenxengage.app.entity.enums.ClientStatus;
import com.tenxengage.app.entity.enums.SubscriptionTier;
import com.tenxengage.app.exception.BusinessRuleException;
import com.tenxengage.app.repository.ClientRepository;
import com.tenxengage.app.repository.ClientRoleRepository;
import com.tenxengage.app.repository.HomeDashboardTemplateRepository;
import com.tenxengage.app.service.HomeDashboardTemplateService;
import com.tenxengage.app.service.seed.HomeDashboardTemplateSeeder;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("integration")
@Transactional
class HomeDashboardTemplateIntegrationTest extends AbstractLocalIntegrationTest {

    private static final UUID DEMO_TENANT = UUID.fromString("a0000000-0000-0000-0000-000000000001");
    private static final UUID CLIENT_ADMIN_ROLE = UUID.fromString("13710c8a-51e4-4822-9919-58f433690093");
    private static final UUID ACTIVITY_APPROVER_ROLE = UUID.fromString("478820ab-e135-4ee3-9bdf-85bc4108544b");
    private static final UUID PARTNER_ADMIN_ROLE = UUID.fromString("8133300d-f503-4657-91f0-e1aae07dd8f6");
    private static final UUID PARTNER_SELLER_ROLE = UUID.fromString("4e47112e-fc97-4ede-8075-71c1ee61707e");

    @Autowired
    private HomeDashboardTemplateRepository templateRepository;

    @Autowired
    private ClientRoleRepository clientRoleRepository;

    @Autowired
    private ClientRepository clientRepository;

    @Autowired
    private HomeDashboardTemplateService templateService;

    @Autowired
    private HomeDashboardTemplateSeeder seeder;

    @Test
    void baselineMigrations_seedThreeTemplatesForDemoTenant() {
        List<HomeDashboardTemplate> templates = templateRepository.findByClientIdOrderByNameAsc(DEMO_TENANT);

        assertThat(templates).extracting(HomeDashboardTemplate::getName)
                .containsExactlyInAnyOrder("Client Admin", "Partner User", "Approver");

        HomeDashboardTemplate clientAdmin = findByName(templates, "Client Admin");
        assertThat(clientAdmin.getRoleType()).isEqualTo("INTERNAL");
        assertThat(clientAdmin.isSystem()).isTrue();
        assertThat(clientAdmin.getLayout())
                .contains("ai_assistant")
                .contains("program_performance");

        HomeDashboardTemplate partnerUser = findByName(templates, "Partner User");
        assertThat(partnerUser.getRoleType()).isEqualTo("EXTERNAL");
        assertThat(partnerUser.getLayout())
                .contains("half-half")
                .contains("rewards_balances")
                .contains("tenx_suggestions");

        HomeDashboardTemplate approver = findByName(templates, "Approver");
        assertThat(approver.getRoleType()).isEqualTo("INTERNAL");
        assertThat(approver.getLayout()).contains("approvals");
    }

    @Test
    void baselineMigrations_assignSeededRolesToCorrectTemplates() {
        HomeDashboardTemplate clientAdminTemplate =
                templateRepository.findByClientIdAndName(DEMO_TENANT, "Client Admin").orElseThrow();
        HomeDashboardTemplate partnerUserTemplate =
                templateRepository.findByClientIdAndName(DEMO_TENANT, "Partner User").orElseThrow();
        HomeDashboardTemplate approverTemplate =
                templateRepository.findByClientIdAndName(DEMO_TENANT, "Approver").orElseThrow();

        assertThat(clientRoleRepository.findById(CLIENT_ADMIN_ROLE).orElseThrow().getHomeDashboardTemplateId())
                .isEqualTo(clientAdminTemplate.getId());
        assertThat(clientRoleRepository.findById(ACTIVITY_APPROVER_ROLE).orElseThrow().getHomeDashboardTemplateId())
                .isEqualTo(approverTemplate.getId());
        assertThat(clientRoleRepository.findById(PARTNER_ADMIN_ROLE).orElseThrow().getHomeDashboardTemplateId())
                .isEqualTo(partnerUserTemplate.getId());
        assertThat(clientRoleRepository.findById(PARTNER_SELLER_ROLE).orElseThrow().getHomeDashboardTemplateId())
                .isEqualTo(partnerUserTemplate.getId());
    }

    @Test
    void resolveForRole_returnsAssignedTemplate() {
        ClientRole clientAdmin = clientRoleRepository.findById(CLIENT_ADMIN_ROLE).orElseThrow();

        Optional<HomeDashboardTemplate> resolved = templateService.resolveForRole(clientAdmin);

        assertThat(resolved).isPresent();
        assertThat(resolved.get().getName()).isEqualTo("Client Admin");
    }

    @Test
    void resolveForRole_withNullFk_fallsBackToDefaultForRoleType() {
        ClientRole role = clientRoleRepository.findById(CLIENT_ADMIN_ROLE).orElseThrow();
        UUID originalTemplate = role.getHomeDashboardTemplateId();
        role.setHomeDashboardTemplateId(null);
        clientRoleRepository.save(role);
        try {
            Optional<HomeDashboardTemplate> resolved = templateService.resolveForRole(role);
            assertThat(resolved).isPresent();
            assertThat(resolved.get().getName()).isEqualTo("Client Admin");
        } finally {
            role.setHomeDashboardTemplateId(originalTemplate);
            clientRoleRepository.save(role);
        }
    }

    @Test
    void assignToRole_rejectsRoleTypeMismatch() {
        HomeDashboardTemplate external =
                templateRepository.findByClientIdAndName(DEMO_TENANT, "Partner User").orElseThrow();

        assertThatThrownBy(() -> templateService.assignToRole(CLIENT_ADMIN_ROLE, external.getId()))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("INTERNAL role");
    }

    @Test
    void assignToRole_swapsTemplateForMatchingRoleType() {
        HomeDashboardTemplate approver =
                templateRepository.findByClientIdAndName(DEMO_TENANT, "Approver").orElseThrow();

        ClientRole updated = templateService.assignToRole(CLIENT_ADMIN_ROLE, approver.getId());

        assertThat(updated.getHomeDashboardTemplateId()).isEqualTo(approver.getId());
    }

    @Test
    void seeder_freshTenant_producesIdenticalState() {
        Client newTenant = clientRepository.save(Client.builder()
                .name("Seeder Test Tenant " + UUID.randomUUID())
                .subdomain("seeder-test-" + UUID.randomUUID().toString().substring(0, 8))
                .status(ClientStatus.ACTIVE)
                .subscriptionTier(SubscriptionTier.STARTER)
                .build());
        UUID newTenantId = newTenant.getId();

        ClientRole newClientAdminRole = clientRoleRepository.save(ClientRole.builder()
                .clientId(newTenantId)
                .name("Client Admin")
                .baseRoleName("CLIENT_ADMIN")
                .system(true)
                .defaultRole(true)
                .roleType("INTERNAL")
                .build());

        seeder.seedForClient(newTenantId);

        List<HomeDashboardTemplate> seededTemplates =
                templateRepository.findByClientIdOrderByNameAsc(newTenantId);
        assertThat(seededTemplates).extracting(HomeDashboardTemplate::getName)
                .containsExactlyInAnyOrder("Client Admin", "Partner User", "Approver");

        ClientRole reloaded = clientRoleRepository.findById(newClientAdminRole.getId()).orElseThrow();
        HomeDashboardTemplate expectedTemplate =
                templateRepository.findByClientIdAndName(newTenantId, "Client Admin").orElseThrow();
        assertThat(reloaded.getHomeDashboardTemplateId()).isEqualTo(expectedTemplate.getId());
    }

    @Test
    void listForTenantAndRoleType_filtersCorrectly() {
        List<HomeDashboardTemplate> internal = templateService.listForTenantAndRoleType(DEMO_TENANT, "INTERNAL");
        List<HomeDashboardTemplate> external = templateService.listForTenantAndRoleType(DEMO_TENANT, "EXTERNAL");

        assertThat(internal).extracting(HomeDashboardTemplate::getName)
                .containsExactlyInAnyOrder("Client Admin", "Approver");
        assertThat(external).extracting(HomeDashboardTemplate::getName)
                .containsExactly("Partner User");
    }

    private HomeDashboardTemplate findByName(List<HomeDashboardTemplate> list, String name) {
        return list.stream().filter(t -> name.equals(t.getName())).findFirst().orElseThrow();
    }
}

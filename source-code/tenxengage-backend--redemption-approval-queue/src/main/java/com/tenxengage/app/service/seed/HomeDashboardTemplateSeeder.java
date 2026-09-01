package com.tenxengage.app.service.seed;

import com.tenxengage.app.entity.ClientRole;
import com.tenxengage.app.entity.HomeDashboardTemplate;
import com.tenxengage.app.repository.ClientRoleRepository;
import com.tenxengage.app.repository.HomeDashboardTemplateRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Provisions the default home dashboard templates (Client Admin, Partner User, Approver)
 * for a tenant and assigns the seeded system roles to their defaults.
 *
 * The demo tenant in V3 baseline receives these directly via Flyway. This seeder exists
 * for tenants created post-production (when a tenant-bootstrap service exists), and is
 * invoked by integration tests to verify the programmatic path matches the migration.
 *
 * All operations are idempotent.
 */
@Component
public class HomeDashboardTemplateSeeder {

    private static final Logger log = LoggerFactory.getLogger(HomeDashboardTemplateSeeder.class);

    static final String TEMPLATE_CLIENT_ADMIN = "Client Admin";
    static final String TEMPLATE_PARTNER_USER = "Partner User";
    static final String TEMPLATE_APPROVER = "Approver";

    private static final String LAYOUT_CLIENT_ADMIN =
            "{\"rows\":["
                    + "{\"layout\":\"full\",\"slots\":[{\"widgetKey\":\"ai_assistant\"}]},"
                    + "{\"layout\":\"full\",\"slots\":[{\"widgetKey\":\"program_performance\"}]}"
                    + "]}";

    private static final String LAYOUT_PARTNER_USER =
            "{\"rows\":["
                    + "{\"layout\":\"half-half\",\"slots\":["
                    + "{\"widgetKey\":\"ai_assistant\"},{\"widgetKey\":\"rewards_balances\"}]},"
                    + "{\"layout\":\"full\",\"slots\":[{\"widgetKey\":\"tenx_suggestions\"}]}"
                    + "]}";

    private static final String LAYOUT_APPROVER =
            "{\"rows\":[{\"layout\":\"full\",\"slots\":[{\"widgetKey\":\"approvals\"}]}]}";

    private final HomeDashboardTemplateRepository templateRepository;
    private final ClientRoleRepository clientRoleRepository;

    public HomeDashboardTemplateSeeder(HomeDashboardTemplateRepository templateRepository,
                                       ClientRoleRepository clientRoleRepository) {
        this.templateRepository = templateRepository;
        this.clientRoleRepository = clientRoleRepository;
    }

    @Transactional
    public void seedForClient(UUID clientId) {
        HomeDashboardTemplate clientAdmin = ensureTemplate(
                clientId, TEMPLATE_CLIENT_ADMIN, "INTERNAL",
                "Default home dashboard for Client Admin roles", LAYOUT_CLIENT_ADMIN);
        HomeDashboardTemplate partnerUser = ensureTemplate(
                clientId, TEMPLATE_PARTNER_USER, "EXTERNAL",
                "Default home dashboard for Partner Admin and Partner Seller roles", LAYOUT_PARTNER_USER);
        HomeDashboardTemplate approver = ensureTemplate(
                clientId, TEMPLATE_APPROVER, "INTERNAL",
                "Default home dashboard for Activity Approver roles", LAYOUT_APPROVER);

        assignIfUnassigned(clientId, "CLIENT_ADMIN", clientAdmin);
        assignIfUnassigned(clientId, "PARTNER_ADMIN", partnerUser);
        assignIfUnassigned(clientId, "PARTNER_SELLER", partnerUser);
        assignIfUnassigned(clientId, "ACTIVITY_APPROVER", approver);

        log.info("HomeDashboardTemplateSeeder: tenant {} seeded (3 templates, role defaults set)", clientId);
    }

    private HomeDashboardTemplate ensureTemplate(UUID clientId, String name, String roleType,
                                                 String description, String layoutJson) {
        return templateRepository.findByClientIdAndName(clientId, name)
                .orElseGet(() -> templateRepository.save(HomeDashboardTemplate.builder()
                        .clientId(clientId)
                        .name(name)
                        .description(description)
                        .roleType(roleType)
                        .layout(layoutJson)
                        .system(true)
                        .build()));
    }

    private void assignIfUnassigned(UUID clientId, String baseRoleName, HomeDashboardTemplate template) {
        clientRoleRepository.findByClientIdAndBaseRoleNameAndSystemTrue(clientId, baseRoleName)
                .ifPresent(role -> {
                    if (role.getHomeDashboardTemplateId() == null) {
                        role.setHomeDashboardTemplateId(template.getId());
                        clientRoleRepository.save(role);
                    }
                });
    }
}

package com.tenxengage.app.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tenxengage.app.entity.ClientRole;
import com.tenxengage.app.entity.HomeDashboardTemplate;
import com.tenxengage.app.exception.BusinessRuleException;
import com.tenxengage.app.exception.ResourceNotFoundException;
import com.tenxengage.app.repository.ClientRoleRepository;
import com.tenxengage.app.repository.HomeDashboardTemplateRepository;
import com.tenxengage.app.service.validation.HomeDashboardTemplateValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HomeDashboardTemplateServiceTest {

    @Mock
    private HomeDashboardTemplateRepository templateRepository;

    @Mock
    private ClientRoleRepository clientRoleRepository;

    private HomeDashboardTemplateValidator validator;

    @InjectMocks
    private HomeDashboardTemplateService service;

    private final UUID tenantA = UUID.fromString("a0000000-0000-0000-0000-000000000001");
    private final UUID tenantB = UUID.fromString("a0000000-0000-0000-0000-000000000002");

    @BeforeEach
    void setUp() {
        validator = new HomeDashboardTemplateValidator(new ObjectMapper());
        service = new HomeDashboardTemplateService(templateRepository, clientRoleRepository, validator);
    }

    @Test
    void listForTenant_delegatesToRepository() {
        when(templateRepository.findByClientIdOrderByNameAsc(tenantA)).thenReturn(List.of(template("Client Admin", "INTERNAL")));

        List<HomeDashboardTemplate> result = service.listForTenant(tenantA);

        assertThat(result).hasSize(1);
    }

    @Test
    void listForTenantAndRoleType_validatesRoleType() {
        assertThatThrownBy(() -> service.listForTenantAndRoleType(tenantA, "BOGUS"))
                .isInstanceOf(BusinessRuleException.class);
        verify(templateRepository, never()).findByClientIdAndRoleTypeOrderByNameAsc(any(), any());
    }

    @Test
    void getById_throwsWhenMissing() {
        UUID id = UUID.randomUUID();
        when(templateRepository.findById(id)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.getById(id))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void resolveForRole_withExplicitTemplate_returnsIt() {
        UUID templateId = UUID.randomUUID();
        HomeDashboardTemplate template = template("Client Admin", "INTERNAL");
        ClientRole role = role("INTERNAL", templateId);
        when(templateRepository.findById(templateId)).thenReturn(Optional.of(template));

        Optional<HomeDashboardTemplate> resolved = service.resolveForRole(role);

        assertThat(resolved).containsSame(template);
    }

    @Test
    void resolveForRole_internalFallbackToClientAdminDefault() {
        ClientRole role = role("INTERNAL", null);
        HomeDashboardTemplate def = template("Client Admin", "INTERNAL");
        when(templateRepository.findByClientIdAndName(tenantA, "Client Admin")).thenReturn(Optional.of(def));

        Optional<HomeDashboardTemplate> resolved = service.resolveForRole(role);

        assertThat(resolved).containsSame(def);
    }

    @Test
    void resolveForRole_externalFallbackToPartnerUserDefault() {
        ClientRole role = role("EXTERNAL", null);
        HomeDashboardTemplate def = template("Partner User", "EXTERNAL");
        when(templateRepository.findByClientIdAndName(tenantA, "Partner User")).thenReturn(Optional.of(def));

        Optional<HomeDashboardTemplate> resolved = service.resolveForRole(role);

        assertThat(resolved).containsSame(def);
    }

    @Test
    void resolveForRole_nullRoleReturnsEmpty() {
        assertThat(service.resolveForRole(null)).isEmpty();
    }

    @Test
    void assignToRole_matchingRoleType_persistsAssignment() {
        UUID roleId = UUID.randomUUID();
        UUID templateId = UUID.randomUUID();
        ClientRole role = role("INTERNAL", null);
        role.setClientId(tenantA);
        HomeDashboardTemplate template = template("Client Admin", "INTERNAL");
        template.setId(templateId);
        template.setClientId(tenantA);
        when(clientRoleRepository.findById(roleId)).thenReturn(Optional.of(role));
        when(templateRepository.findById(templateId)).thenReturn(Optional.of(template));
        when(clientRoleRepository.save(any(ClientRole.class))).thenAnswer(inv -> inv.getArgument(0));

        service.assignToRole(roleId, templateId);

        ArgumentCaptor<ClientRole> captor = ArgumentCaptor.forClass(ClientRole.class);
        verify(clientRoleRepository).save(captor.capture());
        assertThat(captor.getValue().getHomeDashboardTemplateId()).isEqualTo(templateId);
    }

    @Test
    void assignToRole_rejectsRoleTypeMismatch() {
        UUID roleId = UUID.randomUUID();
        UUID templateId = UUID.randomUUID();
        ClientRole role = role("INTERNAL", null);
        role.setClientId(tenantA);
        HomeDashboardTemplate template = template("Partner User", "EXTERNAL");
        template.setId(templateId);
        template.setClientId(tenantA);
        when(clientRoleRepository.findById(roleId)).thenReturn(Optional.of(role));
        when(templateRepository.findById(templateId)).thenReturn(Optional.of(template));

        assertThatThrownBy(() -> service.assignToRole(roleId, templateId))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Cannot assign EXTERNAL template to INTERNAL role");

        verify(clientRoleRepository, never()).save(any());
    }

    @Test
    void assignToRole_rejectsCrossTenant() {
        UUID roleId = UUID.randomUUID();
        UUID templateId = UUID.randomUUID();
        ClientRole role = role("INTERNAL", null);
        role.setClientId(tenantA);
        HomeDashboardTemplate template = template("Client Admin", "INTERNAL");
        template.setId(templateId);
        template.setClientId(tenantB);
        when(clientRoleRepository.findById(roleId)).thenReturn(Optional.of(role));
        when(templateRepository.findById(templateId)).thenReturn(Optional.of(template));

        assertThatThrownBy(() -> service.assignToRole(roleId, templateId))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("different tenants");
    }

    @Test
    void assignToRole_missingRoleThrows() {
        UUID roleId = UUID.randomUUID();
        when(clientRoleRepository.findById(roleId)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.assignToRole(roleId, UUID.randomUUID()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void assignToRole_missingTemplateThrows() {
        UUID roleId = UUID.randomUUID();
        UUID templateId = UUID.randomUUID();
        ClientRole role = role("INTERNAL", null);
        when(clientRoleRepository.findById(roleId)).thenReturn(Optional.of(role));
        when(templateRepository.findById(templateId)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.assignToRole(roleId, templateId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void clearFromRole_nullsOutFk() {
        UUID roleId = UUID.randomUUID();
        ClientRole role = role("INTERNAL", UUID.randomUUID());
        when(clientRoleRepository.findById(roleId)).thenReturn(Optional.of(role));
        when(clientRoleRepository.save(any(ClientRole.class))).thenAnswer(inv -> inv.getArgument(0));

        service.clearFromRole(roleId);

        assertThat(role.getHomeDashboardTemplateId()).isNull();
    }

    private HomeDashboardTemplate template(String name, String roleType) {
        HomeDashboardTemplate t = HomeDashboardTemplate.builder()
                .clientId(tenantA)
                .name(name)
                .roleType(roleType)
                .layout("{\"rows\":[]}")
                .system(true)
                .build();
        t.setId(UUID.randomUUID());
        return t;
    }

    private ClientRole role(String roleType, UUID templateId) {
        ClientRole role = ClientRole.builder()
                .clientId(tenantA)
                .name("Some Role")
                .roleType(roleType)
                .homeDashboardTemplateId(templateId)
                .build();
        role.setId(UUID.randomUUID());
        return role;
    }
}

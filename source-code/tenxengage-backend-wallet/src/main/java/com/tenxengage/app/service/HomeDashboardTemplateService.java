package com.tenxengage.app.service;

import com.tenxengage.app.entity.ClientRole;
import com.tenxengage.app.entity.HomeDashboardTemplate;
import com.tenxengage.app.exception.BusinessRuleException;
import com.tenxengage.app.exception.ResourceNotFoundException;
import com.tenxengage.app.repository.ClientRoleRepository;
import com.tenxengage.app.repository.HomeDashboardTemplateRepository;
import com.tenxengage.app.service.validation.HomeDashboardTemplateValidator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class HomeDashboardTemplateService {

    static final String DEFAULT_INTERNAL_TEMPLATE_NAME = "Client Admin";
    static final String DEFAULT_EXTERNAL_TEMPLATE_NAME = "Partner User";

    private final HomeDashboardTemplateRepository templateRepository;
    private final ClientRoleRepository clientRoleRepository;
    private final HomeDashboardTemplateValidator validator;

    public HomeDashboardTemplateService(HomeDashboardTemplateRepository templateRepository,
                                        ClientRoleRepository clientRoleRepository,
                                        HomeDashboardTemplateValidator validator) {
        this.templateRepository = templateRepository;
        this.clientRoleRepository = clientRoleRepository;
        this.validator = validator;
    }

    @Transactional(readOnly = true)
    public List<HomeDashboardTemplate> listForTenant(UUID clientId) {
        return templateRepository.findByClientIdOrderByNameAsc(clientId);
    }

    @Transactional(readOnly = true)
    public List<HomeDashboardTemplate> listForTenantAndRoleType(UUID clientId, String roleType) {
        validator.validateRoleType(roleType);
        return templateRepository.findByClientIdAndRoleTypeOrderByNameAsc(clientId, roleType);
    }

    @Transactional(readOnly = true)
    public HomeDashboardTemplate getById(UUID id) {
        return templateRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("HomeDashboardTemplate", "id", id));
    }

    /**
     * Resolve the template for a role: explicit FK first, otherwise default-for-role-type.
     * Returns empty if no default exists for this tenant/role-type.
     */
    @Transactional(readOnly = true)
    public Optional<HomeDashboardTemplate> resolveForRole(ClientRole role) {
        if (role == null) {
            return Optional.empty();
        }
        if (role.getHomeDashboardTemplateId() != null) {
            return templateRepository.findById(role.getHomeDashboardTemplateId());
        }
        String defaultName = "EXTERNAL".equals(role.getRoleType())
                ? DEFAULT_EXTERNAL_TEMPLATE_NAME
                : DEFAULT_INTERNAL_TEMPLATE_NAME;
        return templateRepository.findByClientIdAndName(role.getClientId(), defaultName);
    }

    @Transactional
    public ClientRole assignToRole(UUID roleId, UUID templateId) {
        ClientRole role = clientRoleRepository.findById(roleId)
                .orElseThrow(() -> new ResourceNotFoundException("ClientRole", "id", roleId));
        HomeDashboardTemplate template = templateRepository.findById(templateId)
                .orElseThrow(() -> new ResourceNotFoundException("HomeDashboardTemplate", "id", templateId));

        if (!template.getClientId().equals(role.getClientId())) {
            throw new BusinessRuleException("Template and role belong to different tenants");
        }
        if (!template.getRoleType().equals(role.getRoleType())) {
            throw new BusinessRuleException(
                    "Cannot assign " + template.getRoleType() + " template to "
                            + role.getRoleType() + " role");
        }

        role.setHomeDashboardTemplateId(template.getId());
        return clientRoleRepository.save(role);
    }

    @Transactional
    public ClientRole clearFromRole(UUID roleId) {
        ClientRole role = clientRoleRepository.findById(roleId)
                .orElseThrow(() -> new ResourceNotFoundException("ClientRole", "id", roleId));
        role.setHomeDashboardTemplateId(null);
        return clientRoleRepository.save(role);
    }
}

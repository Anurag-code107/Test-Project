package com.tenxengage.app.service;

import com.tenxengage.app.entity.ClientPermissionGrant;
import com.tenxengage.app.entity.ClientRole;
import com.tenxengage.app.entity.ClientRolePermission;
import com.tenxengage.app.entity.CompanyPermissionOverride;
import com.tenxengage.app.entity.Permission;
import com.tenxengage.app.entity.User;
import com.tenxengage.app.entity.UserPermissionOverride;
import com.tenxengage.app.exception.BusinessRuleException;
import com.tenxengage.app.exception.ResourceNotFoundException;
import com.tenxengage.app.security.PermissionConstants;
import com.tenxengage.app.repository.ClientPermissionGrantRepository;
import com.tenxengage.app.repository.ClientRolePermissionRepository;
import com.tenxengage.app.repository.ClientRoleRepository;
import com.tenxengage.app.repository.CompanyPermissionOverrideRepository;
import com.tenxengage.app.repository.PermissionRepository;
import com.tenxengage.app.repository.UserPermissionOverrideRepository;
import com.tenxengage.app.repository.UserRepository;
import com.tenxengage.app.security.TenantValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class PermissionService {

    private static final Logger log = LoggerFactory.getLogger(PermissionService.class);

    private final PermissionRepository permissionRepository;
    private final ClientRoleRepository clientRoleRepository;
    private final ClientRolePermissionRepository clientRolePermissionRepository;
    private final CompanyPermissionOverrideRepository companyOverrideRepository;
    private final UserPermissionOverrideRepository userOverrideRepository;
    private final ClientPermissionGrantRepository clientPermissionGrantRepository;
    private final UserRepository userRepository;
    private final TenantValidator tenantValidator;
    private final CacheManager cacheManager;

    public PermissionService(PermissionRepository permissionRepository,
                             ClientRoleRepository clientRoleRepository,
                             ClientRolePermissionRepository clientRolePermissionRepository,
                             CompanyPermissionOverrideRepository companyOverrideRepository,
                             UserPermissionOverrideRepository userOverrideRepository,
                             ClientPermissionGrantRepository clientPermissionGrantRepository,
                             UserRepository userRepository,
                             TenantValidator tenantValidator,
                             CacheManager cacheManager) {
        this.permissionRepository = permissionRepository;
        this.clientRoleRepository = clientRoleRepository;
        this.clientRolePermissionRepository = clientRolePermissionRepository;
        this.companyOverrideRepository = companyOverrideRepository;
        this.userOverrideRepository = userOverrideRepository;
        this.clientPermissionGrantRepository = clientPermissionGrantRepository;
        this.userRepository = userRepository;
        this.tenantValidator = tenantValidator;
        this.cacheManager = cacheManager;
    }

    // ── Permission Catalog ──────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<Permission> getAllPermissions() {
        return permissionRepository.findAllByOrderBySortOrderAsc();
    }

    @Transactional(readOnly = true)
    public List<Permission> getPermissionsByScope(String roleType) {
        if ("INTERNAL".equals(roleType)) {
            return permissionRepository.findByScopeInOrderBySortOrderAsc(List.of("INTERNAL", "ALL"));
        } else if ("EXTERNAL".equals(roleType)) {
            return permissionRepository.findByScopeInOrderBySortOrderAsc(List.of("EXTERNAL", "ALL"));
        }
        return permissionRepository.findAllByOrderBySortOrderAsc();
    }

    // ── Effective Permission Resolution (5-layer model) ─────────────────────

    /**
     * Resolves the effective permissions for a user by applying the 5-layer model:
     * 0. Tenant permission grants (which permissions the tenant has access to)
     * 1. Role permissions (from client_role_permissions)
     * 2. Company overrides (can only restrict)
     * 3. User overrides (can only restrict)
     *
     * TENX_ADMIN bypasses all layers and receives all permissions.
     */
    @Transactional(readOnly = true)
    @Cacheable(value = "effectivePermissions", key = "#userId")
    public Set<String> resolveEffectivePermissions(UUID userId) {
        User user = userRepository.findByIdWithClientRole(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));

        // TENX_ADMIN gets all permissions implicitly (no client_role needed)
        if (isTenxAdmin(user)) {
            return permissionRepository.findAllByOrderBySortOrderAsc().stream()
                    .map(Permission::getPermissionKey)
                    .collect(Collectors.toSet());
        }

        // Layer 0: Tenant permission grants
        Set<String> tenantPermissions = getTenantPermissionKeys(user.getClientId());

        // Layer 1: Role permissions
        Set<String> rolePermissions = getRolePermissionKeys(user);

        // Intersect: role permissions cannot exceed tenant grants
        Set<String> effective = new HashSet<>(rolePermissions);
        effective.retainAll(tenantPermissions);

        // Layer 2: Company overrides (only for partner users)
        if (user.getPartnerCompanyId() != null && user.getClientId() != null) {
            List<CompanyPermissionOverride> companyOverrides =
                    companyOverrideRepository.findByClientIdAndPartnerCompanyId(
                            user.getClientId(), user.getPartnerCompanyId());
            for (CompanyPermissionOverride override : companyOverrides) {
                if (!override.isGranted()) {
                    effective.remove(override.getPermissionKey());
                }
            }
        }

        // Layer 3: User overrides
        if (user.getClientId() != null) {
            List<UserPermissionOverride> userOverrides =
                    userOverrideRepository.findByClientIdAndUserId(user.getClientId(), userId);
            for (UserPermissionOverride override : userOverrides) {
                if (!override.isGranted()) {
                    effective.remove(override.getPermissionKey());
                }
            }
        }

        return effective;
    }

    /**
     * Check if a specific user has a specific permission.
     */
    @Transactional(readOnly = true)
    public boolean checkPermission(UUID userId, String permissionKey) {
        return resolveEffectivePermissions(userId).contains(permissionKey);
    }

    /**
     * Check permission for the currently authenticated user. Throws AccessDeniedException if denied.
     */
    public void requirePermission(String permissionKey) {
        UUID userId = tenantValidator.getCurrentUserId();
        if (!checkPermission(userId, permissionKey)) {
            throw new AccessDeniedException(
                    "Permission denied: " + permissionKey);
        }
    }

    // ── Tenant Permission Grants (Layer 0) ──────────────────────────────────

    @Transactional(readOnly = true)
    public Set<String> getTenantPermissionKeys(UUID clientId) {
        if (clientId == null) {
            return Set.of();
        }
        return clientPermissionGrantRepository.findByClientId(clientId).stream()
                .filter(ClientPermissionGrant::isGranted)
                .map(ClientPermissionGrant::getPermissionKey)
                .collect(Collectors.toSet());
    }

    @Transactional(readOnly = true)
    public List<ClientPermissionGrant> getTenantPermissionGrants(UUID clientId) {
        return clientPermissionGrantRepository.findByClientId(clientId);
    }

    @Transactional
    public void updateTenantPermissions(UUID clientId, Map<String, Boolean> permissions) {
        clientPermissionGrantRepository.deleteByClientId(clientId);

        List<ClientPermissionGrant> grants = permissions.entrySet().stream()
                .map(e -> ClientPermissionGrant.builder()
                        .clientId(clientId)
                        .permissionKey(e.getKey())
                        .granted(e.getValue())
                        .build())
                .toList();
        clientPermissionGrantRepository.saveAll(grants);

        // Evict permission cache for ALL users in this tenant
        evictPermissionCacheForTenant(clientId);
        log.info("Updated tenant permissions for client {} ({} grants)", clientId, grants.size());
    }

    // ── Role Permission Management ──────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<ClientRole> getClientRoles(UUID clientId) {
        return clientRoleRepository.findByClientIdOrderByNameAsc(clientId);
    }

    @Transactional(readOnly = true)
    public ClientRole getClientRole(UUID clientRoleId) {
        return clientRoleRepository.findById(clientRoleId)
                .orElseThrow(() -> new ResourceNotFoundException("ClientRole", "id", clientRoleId));
    }

    @Transactional(readOnly = true)
    public List<ClientRolePermission> getRolePermissions(UUID clientRoleId) {
        return clientRolePermissionRepository.findByClientRoleId(clientRoleId);
    }

    @Transactional
    public ClientRole createClientRole(UUID clientId, String name, String description,
                                       String roleType, Map<String, Boolean> initialPermissions) {
        if (clientRoleRepository.existsByClientIdAndName(clientId, name)) {
            throw new BusinessRuleException("A role with name '" + name + "' already exists");
        }

        ClientRole role = ClientRole.builder()
                .clientId(clientId)
                .name(name)
                .description(description)
                .system(false)
                .defaultRole(false)
                .roleType(roleType)
                .build();

        ClientRole saved = clientRoleRepository.save(role);

        // Seed initial permissions if provided
        if (initialPermissions != null && !initialPermissions.isEmpty()) {
            List<ClientRolePermission> grants = initialPermissions.entrySet().stream()
                    .map(e -> ClientRolePermission.builder()
                            .clientRoleId(saved.getId())
                            .permissionKey(e.getKey())
                            .granted(e.getValue())
                            .build())
                    .toList();
            clientRolePermissionRepository.saveAll(grants);
        }

        log.info("Created custom role '{}' (type={}) for client {}", name, roleType, clientId);
        return saved;
    }

    @Transactional
    public ClientRole cloneClientRole(UUID sourceRoleId, String name, String description) {
        ClientRole source = getClientRole(sourceRoleId);
        UUID clientId = source.getClientId();

        if (clientRoleRepository.existsByClientIdAndName(clientId, name)) {
            throw new BusinessRuleException("A role with name '" + name + "' already exists");
        }

        ClientRole clone = ClientRole.builder()
                .clientId(clientId)
                .name(name)
                .description(description)
                .roleType(source.getRoleType())
                .system(false)
                .defaultRole(false)
                .build();

        ClientRole saved = clientRoleRepository.save(clone);

        // Copy all permissions from source
        List<ClientRolePermission> sourcePerms = clientRolePermissionRepository.findByClientRoleId(sourceRoleId);
        List<ClientRolePermission> clonedPerms = sourcePerms.stream()
                .map(sp -> ClientRolePermission.builder()
                        .clientRoleId(saved.getId())
                        .permissionKey(sp.getPermissionKey())
                        .granted(sp.isGranted())
                        .build())
                .toList();
        clientRolePermissionRepository.saveAll(clonedPerms);

        log.info("Cloned role '{}' from '{}' (id={}) for client {}", name, source.getName(), sourceRoleId, clientId);
        return saved;
    }

    @Transactional
    public ClientRole updateClientRole(UUID clientRoleId, String name, String description) {
        ClientRole role = getClientRole(clientRoleId);
        if (role.isSystem() && name != null && !name.equals(role.getName())) {
            throw new BusinessRuleException("Cannot rename system roles");
        }
        if (name != null) {
            role.setName(name);
        }
        if (description != null) {
            role.setDescription(description);
        }
        return clientRoleRepository.save(role);
    }

    @Transactional
    public void updateRolePermissions(UUID clientRoleId, Map<String, Boolean> permissionGrants) {
        ClientRole role = getClientRole(clientRoleId);

        // Hardening: immutable permissions on system Client Admin role
        if (role.isSystem() && "CLIENT_ADMIN".equals(role.getBaseRoleName())) {
            for (String key : PermissionConstants.IMMUTABLE_ADMIN_PERMISSIONS) {
                if (permissionGrants.containsKey(key) && !permissionGrants.get(key)) {
                    throw new BusinessRuleException(
                            "Cannot remove '" + key + "' from the system Client Admin role");
                }
            }
        }

        // Hardening: self-lock prevention — user editing their own role
        UUID currentUserId = tenantValidator.getCurrentUserId();
        User currentUser = userRepository.findById(currentUserId).orElse(null);
        if (currentUser != null && clientRoleId.equals(currentUser.getClientRoleId())) {
            Set<String> wouldRemove = permissionGrants.entrySet().stream()
                    .filter(e -> !e.getValue())
                    .map(Map.Entry::getKey)
                    .collect(Collectors.toSet());
            Set<String> criticalLost = new HashSet<>(wouldRemove);
            criticalLost.retainAll(PermissionConstants.SELF_LOCK_CRITICAL_PERMISSIONS);
            if (!criticalLost.isEmpty()) {
                throw new BusinessRuleException(
                        "Cannot remove your own ability to manage roles and permissions: "
                                + String.join(", ", criticalLost)
                                + ". Have another admin make this change.");
            }
        }

        // Hardening: scope enforcement — permission scope must match role scope
        Set<String> tenantPerms = getTenantPermissionKeys(role.getClientId());
        for (Map.Entry<String, Boolean> entry : permissionGrants.entrySet()) {
            if (!entry.getValue()) continue; // denying is always safe
            String permKey = entry.getKey();

            Permission perm = permissionRepository.findByPermissionKey(permKey)
                    .orElseThrow(() -> new ResourceNotFoundException("Permission", "key", permKey));
            if (!"ALL".equals(perm.getScope()) && !perm.getScope().equals(role.getRoleType())) {
                throw new BusinessRuleException(
                        "Permission '" + permKey + "' (scope: " + perm.getScope()
                                + ") cannot be assigned to " + role.getRoleType() + " role '"
                                + role.getName() + "'");
            }
            if (!tenantPerms.contains(permKey)) {
                throw new BusinessRuleException(
                        "Permission '" + permKey + "' is not available for this tenant");
            }
        }

        clientRolePermissionRepository.deleteByClientRoleId(clientRoleId);

        List<ClientRolePermission> grants = permissionGrants.entrySet().stream()
                .map(e -> ClientRolePermission.builder()
                        .clientRoleId(clientRoleId)
                        .permissionKey(e.getKey())
                        .granted(e.getValue())
                        .build())
                .toList();
        clientRolePermissionRepository.saveAll(grants);

        // Evict permission cache for all users on this role
        evictPermissionCacheForRole(clientRoleId);
        log.info("Updated permissions for role {} ({} grants)", role.getName(), grants.size());
    }

    @Transactional
    public void deleteClientRole(UUID clientRoleId) {
        ClientRole role = getClientRole(clientRoleId);
        if (role.isSystem()) {
            throw new BusinessRuleException("Cannot delete system roles");
        }

        // Reassign affected users to the default role for the same scope
        List<User> affectedUsers = userRepository.findByClientRoleId(clientRoleId);
        if (!affectedUsers.isEmpty()) {
            ClientRole defaultRole = clientRoleRepository
                    .findByClientIdAndRoleTypeAndDefaultRoleTrue(role.getClientId(), role.getRoleType())
                    .orElseThrow(() -> new BusinessRuleException(
                            "No default " + role.getRoleType() + " role exists for this tenant"));
            for (User user : affectedUsers) {
                user.setClientRoleId(defaultRole.getId());
                userRepository.save(user);
            }
            log.info("Reassigned {} users from role '{}' to default role '{}'",
                    affectedUsers.size(), role.getName(), defaultRole.getName());
        }

        clientRolePermissionRepository.deleteByClientRoleId(clientRoleId);
        clientRoleRepository.delete(role);
        log.info("Deleted custom role '{}' (id={})", role.getName(), clientRoleId);
    }

    // ── Company Permission Overrides ────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<CompanyPermissionOverride> getCompanyOverrides(UUID clientId, UUID partnerCompanyId) {
        return companyOverrideRepository.findByClientIdAndPartnerCompanyId(clientId, partnerCompanyId);
    }

    @Transactional
    public void updateCompanyOverrides(UUID clientId, UUID partnerCompanyId,
                                       Map<String, Boolean> overrides) {
        companyOverrideRepository.deleteByClientIdAndPartnerCompanyId(clientId, partnerCompanyId);

        List<CompanyPermissionOverride> entities = overrides.entrySet().stream()
                .map(e -> CompanyPermissionOverride.builder()
                        .clientId(clientId)
                        .partnerCompanyId(partnerCompanyId)
                        .permissionKey(e.getKey())
                        .granted(e.getValue())
                        .build())
                .toList();
        companyOverrideRepository.saveAll(entities);

        // Evict permission cache for all users in this company
        evictPermissionCacheForCompany(clientId, partnerCompanyId);
        log.info("Updated company overrides for partner company {} ({} overrides)",
                partnerCompanyId, entities.size());
    }

    // ── User Permission Overrides ───────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<UserPermissionOverride> getUserOverrides(UUID clientId, UUID userId) {
        return userOverrideRepository.findByClientIdAndUserId(clientId, userId);
    }

    @Transactional
    public void updateUserOverrides(UUID clientId, UUID userId,
                                    Map<String, Boolean> overrides) {
        userOverrideRepository.deleteByClientIdAndUserId(clientId, userId);

        List<UserPermissionOverride> entities = overrides.entrySet().stream()
                .map(e -> UserPermissionOverride.builder()
                        .clientId(clientId)
                        .userId(userId)
                        .permissionKey(e.getKey())
                        .granted(e.getValue())
                        .build())
                .toList();
        userOverrideRepository.saveAll(entities);

        // Evict permission cache for this specific user
        evictPermissionCache(userId);
        log.info("Updated user overrides for user {} ({} overrides)", userId, entities.size());
    }

    // ── Private helpers ─────────────────────────────────────────────────────

    private boolean isTenxAdmin(User user) {
        // TENX_ADMIN users have no clientId (they're platform-level)
        // and no clientRoleId (they bypass the permission system)
        return user.getClientId() == null && user.getClientRoleId() == null;
    }

    private Set<String> getRolePermissionKeys(User user) {
        if (user.getClientRoleId() != null) {
            return clientRolePermissionRepository.findByClientRoleId(user.getClientRoleId()).stream()
                    .filter(ClientRolePermission::isGranted)
                    .map(ClientRolePermission::getPermissionKey)
                    .collect(Collectors.toCollection(HashSet::new));
        }

        // Fallback: if no client_role_id assigned, try to find default system role
        if (user.getClientId() != null) {
            String roleType = user.getPartnerCompanyId() != null ? "EXTERNAL" : "INTERNAL";
            return clientRoleRepository.findByClientIdAndRoleTypeAndDefaultRoleTrue(
                            user.getClientId(), roleType)
                    .map(cr -> clientRolePermissionRepository.findByClientRoleId(cr.getId()).stream()
                            .filter(ClientRolePermission::isGranted)
                            .map(ClientRolePermission::getPermissionKey)
                            .collect(Collectors.toCollection(HashSet::new)))
                    .orElseGet(HashSet::new);
        }

        return new HashSet<>();
    }

    // ── Cache eviction helpers ──────────────────────────────────────────────

    /**
     * Evict permission cache for a single user.
     */
    public void evictPermissionCache(UUID userId) {
        Cache cache = cacheManager.getCache("effectivePermissions");
        if (cache != null) {
            cache.evict(userId);
        }
    }

    /**
     * Evict permission cache for all users assigned to a specific role.
     */
    private void evictPermissionCacheForRole(UUID clientRoleId) {
        List<User> affectedUsers = userRepository.findByClientRoleId(clientRoleId);
        Cache cache = cacheManager.getCache("effectivePermissions");
        if (cache != null) {
            for (User user : affectedUsers) {
                cache.evict(user.getId());
            }
        }
        log.debug("Evicted permission cache for {} users on role {}", affectedUsers.size(), clientRoleId);
    }

    /**
     * Evict permission cache for all users in a partner company.
     */
    private void evictPermissionCacheForCompany(UUID clientId, UUID partnerCompanyId) {
        List<User> affectedUsers = userRepository.findByClientIdAndPartnerCompanyId(clientId, partnerCompanyId);
        Cache cache = cacheManager.getCache("effectivePermissions");
        if (cache != null) {
            for (User user : affectedUsers) {
                cache.evict(user.getId());
            }
        }
        log.debug("Evicted permission cache for {} users in company {}", affectedUsers.size(), partnerCompanyId);
    }

    /**
     * Evict permission cache for ALL users in a tenant.
     * Used when tenant-level permissions change.
     */
    private void evictPermissionCacheForTenant(UUID clientId) {
        Cache cache = cacheManager.getCache("effectivePermissions");
        if (cache != null) {
            cache.clear();
        }
        log.debug("Evicted all permission caches for tenant {}", clientId);
    }
}

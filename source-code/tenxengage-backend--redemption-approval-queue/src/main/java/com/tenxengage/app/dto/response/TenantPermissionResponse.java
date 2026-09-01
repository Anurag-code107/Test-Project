package com.tenxengage.app.dto.response;

import com.tenxengage.app.entity.Client;
import com.tenxengage.app.entity.Permission;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public record TenantPermissionResponse(
    UUID clientId,
    String clientName,
    String subscriptionTier,
    List<PermissionGroup> permissionGroups
) {
    public record PermissionGroup(
        String groupName,
        String groupKey,
        boolean allGranted,
        List<PermissionEntry> permissions
    ) {}

    public record PermissionEntry(
        String key,
        String displayName,
        String description,
        String scope,
        boolean granted
    ) {}

    public static TenantPermissionResponse from(Client client, List<Permission> allPermissions,
                                                  Set<String> grantedKeys) {
        // Group by category, excluding PLATFORM permissions
        Map<String, List<Permission>> grouped = allPermissions.stream()
                .filter(p -> !"PLATFORM".equals(p.getScope()))
                .collect(Collectors.groupingBy(Permission::getCategory));

        List<PermissionGroup> groups = grouped.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> {
                    List<PermissionEntry> entries = entry.getValue().stream()
                            .map(p -> new PermissionEntry(
                                    p.getPermissionKey(),
                                    p.getDisplayName(),
                                    p.getDescription(),
                                    p.getScope(),
                                    grantedKeys.contains(p.getPermissionKey())))
                            .toList();
                    boolean allGranted = entries.stream().allMatch(PermissionEntry::granted);
                    return new PermissionGroup(
                            formatGroupName(entry.getKey()),
                            entry.getKey(),
                            allGranted,
                            entries);
                })
                .toList();

        return new TenantPermissionResponse(
                client.getId(),
                client.getName(),
                client.getSubscriptionTier() != null ? client.getSubscriptionTier().name() : null,
                groups);
    }

    private static String formatGroupName(String category) {
        return category.replace("_", " ")
                .toLowerCase()
                .replaceFirst(".", String.valueOf(Character.toUpperCase(category.charAt(0))));
    }
}

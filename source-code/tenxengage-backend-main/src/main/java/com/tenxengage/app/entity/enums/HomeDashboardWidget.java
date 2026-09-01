package com.tenxengage.app.entity.enums;

import java.util.Arrays;
import java.util.Optional;
import java.util.Set;

public enum HomeDashboardWidget {
    AI_ASSISTANT("ai_assistant", Set.of("INTERNAL", "EXTERNAL")),
    PROGRAM_PERFORMANCE("program_performance", Set.of("INTERNAL")),
    TENX_SUGGESTIONS("tenx_suggestions", Set.of("EXTERNAL")),
    REWARDS_BALANCES("rewards_balances", Set.of("EXTERNAL")),
    APPROVALS("approvals", Set.of("INTERNAL"));

    private final String key;
    private final Set<String> supportedRoleTypes;

    HomeDashboardWidget(String key, Set<String> supportedRoleTypes) {
        this.key = key;
        this.supportedRoleTypes = supportedRoleTypes;
    }

    public String getKey() {
        return key;
    }

    public Set<String> getSupportedRoleTypes() {
        return supportedRoleTypes;
    }

    public boolean supportsRoleType(String roleType) {
        return supportedRoleTypes.contains(roleType);
    }

    public static Optional<HomeDashboardWidget> fromKey(String key) {
        return Arrays.stream(values())
                .filter(w -> w.key.equals(key))
                .findFirst();
    }
}

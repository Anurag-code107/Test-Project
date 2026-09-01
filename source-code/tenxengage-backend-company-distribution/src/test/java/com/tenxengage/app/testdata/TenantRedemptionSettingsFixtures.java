package com.tenxengage.app.testdata;

import com.tenxengage.app.entity.TenantRedemptionSettings;
import com.tenxengage.app.entity.enums.BatchCadence;

import java.util.UUID;

public final class TenantRedemptionSettingsFixtures {

    private TenantRedemptionSettingsFixtures() {
    }

    public static TenantRedemptionSettings.TenantRedemptionSettingsBuilder defaultSettings(UUID clientId) {
        return TenantRedemptionSettings.builder()
                .clientId(clientId)
                .batchCadence(BatchCadence.DAILY);
    }

    public static TenantRedemptionSettings.TenantRedemptionSettingsBuilder dailySettings(UUID clientId) {
        return TenantRedemptionSettings.builder()
                .clientId(clientId)
                .batchCadence(BatchCadence.DAILY);
    }

    public static TenantRedemptionSettings.TenantRedemptionSettingsBuilder weeklySettings(UUID clientId) {
        return TenantRedemptionSettings.builder()
                .clientId(clientId)
                .batchCadence(BatchCadence.WEEKLY);
    }
}

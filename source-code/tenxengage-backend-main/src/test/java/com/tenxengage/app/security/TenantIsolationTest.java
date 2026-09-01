package com.tenxengage.app.security;

import com.tenxengage.app.entity.TenantAware;
import org.hibernate.annotations.Filter;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Validates that tenant isolation annotations are present on all entities
 * that implement TenantAware, and that TenantContext works correctly.
 */
class TenantIsolationTest {

    private static final Set<String> TENANT_AWARE_ENTITIES = Set.of(
            "com.tenxengage.app.entity.User",
            "com.tenxengage.app.entity.Incentive",
            "com.tenxengage.app.entity.PartnerCompany",
            "com.tenxengage.app.entity.ClaimAction",
            "com.tenxengage.app.entity.PurchaseOrder",
            "com.tenxengage.app.entity.Notification",
            "com.tenxengage.app.entity.RewardTransaction",
            "com.tenxengage.app.entity.RewardBalance",
            "com.tenxengage.app.entity.Connector",
            "com.tenxengage.app.entity.DataObject",
            "com.tenxengage.app.entity.Currency",
            "com.tenxengage.app.entity.FiscalYearConfig",
            "com.tenxengage.app.entity.LocationLevel",
            "com.tenxengage.app.entity.LocationValue",
            "com.tenxengage.app.entity.Product",
            "com.tenxengage.app.entity.LegalPolicy",
            "com.tenxengage.app.entity.PartnerKycRecord",
            "com.tenxengage.app.entity.PoEligibilityMapping",
            "com.tenxengage.app.entity.RecommendationConfig",
            "com.tenxengage.app.entity.RecommendationInteraction",
            "com.tenxengage.app.entity.RecommendationScore",
            "com.tenxengage.app.entity.ComplianceAlert",
            "com.tenxengage.app.entity.ClientNotificationRoleConfig",
            "com.tenxengage.app.entity.UserNotificationPreference",
            "com.tenxengage.app.entity.UserNotificationSetting",
            "com.tenxengage.app.entity.ClientFeatureOverride",
            "com.tenxengage.app.entity.RetentionPolicy",
            "com.tenxengage.app.entity.DataUpload",
            "com.tenxengage.app.entity.UserCourseCompletion",
            "com.tenxengage.app.entity.SyncSchedule",
            "com.tenxengage.app.entity.TaggingJob"
    );

    @Test
    void allTenantAwareEntities_haveClientIdField() {
        for (String className : TENANT_AWARE_ENTITIES) {
            try {
                Class<?> clazz = Class.forName(className);
                assertThat(TenantAware.class.isAssignableFrom(clazz))
                        .as("Entity %s must implement TenantAware", className)
                        .isTrue();

                boolean hasClientId = false;
                for (Field field : clazz.getDeclaredFields()) {
                    if ("clientId".equals(field.getName())) {
                        hasClientId = true;
                        break;
                    }
                }
                assertThat(hasClientId)
                        .as("Entity %s must have a clientId field for tenant isolation", className)
                        .isTrue();
            } catch (ClassNotFoundException e) {
                throw new AssertionError("TenantAware entity class not found: " + className, e);
            }
        }
    }

    @Test
    void allTenantAwareEntities_haveFilterAnnotation() {
        for (String className : TENANT_AWARE_ENTITIES) {
            try {
                Class<?> clazz = Class.forName(className);
                Filter filter = clazz.getAnnotation(Filter.class);
                assertThat(filter)
                        .as("Entity %s implements TenantAware but is missing @Filter annotation", className)
                        .isNotNull();
                assertThat(filter.name())
                        .as("Entity %s @Filter must use the 'tenantFilter' name", className)
                        .isEqualTo("tenantFilter");
                assertThat(filter.condition())
                        .as("Entity %s @Filter must filter by client_id", className)
                        .contains("client_id");
            } catch (ClassNotFoundException e) {
                throw new AssertionError("TenantAware entity class not found: " + className, e);
            }
        }
    }

    @Test
    void tenantContext_setAndClearWorksCorrectly() {
        UUID clientId = UUID.randomUUID();

        TenantContext.setClientId(clientId);
        assertThat(TenantContext.getClientId()).isEqualTo(clientId);

        TenantContext.clear();
        assertThat(TenantContext.getClientId()).isNull();
    }

    @Test
    void tenantContext_threadLocalIsolation() throws InterruptedException {
        UUID clientA = UUID.randomUUID();
        UUID clientB = UUID.randomUUID();

        TenantContext.setClientId(clientA);

        AtomicReference<UUID> otherThreadValue = new AtomicReference<>();

        Thread otherThread = new Thread(() -> {
            TenantContext.setClientId(clientB);
            otherThreadValue.set(TenantContext.getClientId());
            TenantContext.clear();
        });
        otherThread.start();
        otherThread.join();

        assertThat(TenantContext.getClientId()).isEqualTo(clientA);
        assertThat(otherThreadValue.get()).isEqualTo(clientB);

        TenantContext.clear();
    }

    @Test
    void tenantContext_subdomainSetAndClear() {
        TenantContext.setSubdomain("test-client");
        assertThat(TenantContext.getSubdomain()).isEqualTo("test-client");

        TenantContext.clear();
        assertThat(TenantContext.getSubdomain()).isNull();
    }
}

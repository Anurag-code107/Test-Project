package com.tenxengage.app.controller;

import com.tenxengage.app.config.SecurityConfig;
import com.tenxengage.app.security.CustomUserDetailsService;
import com.tenxengage.app.security.JwtTokenProvider;
import com.tenxengage.app.security.PermissionAspect;
import com.tenxengage.app.security.PermissionMetrics;
import com.tenxengage.app.security.TenantValidator;
import com.tenxengage.app.service.ClientService;
import com.tenxengage.app.service.DataOperationsService;
import com.tenxengage.app.service.PermissionService;
import com.tenxengage.app.service.forecast.ForecastAccuracyService;
import com.tenxengage.app.service.forecast.ForecastAggregationService;
import com.tenxengage.app.service.recommendation.RecommendationScoringService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.aop.AopAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Set;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ForecastAdminController.class)
@Import({SecurityConfig.class, PermissionAspect.class, AopAutoConfiguration.class})
class ForecastAdminControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean private ForecastAggregationService aggregationService;
    @MockBean private ForecastAccuracyService accuracyService;
    @MockBean private RecommendationScoringService recommendationScoringService;
    @MockBean private DataOperationsService dataOperationsService;
    @MockBean private TenantValidator tenantValidator;
    @MockBean private PermissionService permissionService;
    @MockBean private PermissionMetrics permissionMetrics;
    @MockBean private JwtTokenProvider jwtTokenProvider;
    @MockBean private CustomUserDetailsService customUserDetailsService;
    @MockBean private ClientService clientService;
    @MockBean private JpaMetamodelMappingContext jpaMetamodelMappingContext;

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID CLIENT_ID = UUID.randomUUID();

    private void withPermission(String permission) {
        when(tenantValidator.getCurrentUserId()).thenReturn(USER_ID);
        when(tenantValidator.getCurrentClientId()).thenReturn(CLIENT_ID);
        when(permissionService.resolveEffectivePermissions(USER_ID)).thenReturn(Set.of(permission));
    }

    // ── POST /trigger ────────────────────────────────────────────────────────

    @Test
    void triggerAggregation_returns401_whenUnauthenticated() throws Exception {
        mockMvc.perform(post("/api/v1/admin/forecast-aggregation/trigger"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser
    void triggerAggregation_returns200_whenAuthenticated() throws Exception {
        withPermission("action.incentive.forecast");

        mockMvc.perform(post("/api/v1/admin/forecast-aggregation/trigger"))
                .andExpect(status().isOk());
    }
}

package com.tenxengage.app.controller;

import com.tenxengage.app.config.SecurityConfig;
import com.tenxengage.app.security.CustomUserDetailsService;
import com.tenxengage.app.security.JwtTokenProvider;
import com.tenxengage.app.security.PermissionAspect;
import com.tenxengage.app.security.PermissionMetrics;
import com.tenxengage.app.security.TenantValidator;
import com.tenxengage.app.service.ClientService;
import com.tenxengage.app.service.PermissionService;
import com.tenxengage.app.service.recommendation.RecommendationConfigService;
import com.tenxengage.app.service.recommendation.RecommendationScoringService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.aop.AopAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(RecommendationAdminController.class)
@Import({SecurityConfig.class, PermissionAspect.class, AopAutoConfiguration.class})
class RecommendationAdminControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean private RecommendationConfigService configService;
    @MockBean private RecommendationScoringService scoringService;
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

    // ── GET /config ──────────────────────────────────────────────────────────

    @Test
    void getConfig_returns401_whenUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/v1/admin/recommendations/config"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser
    void getConfig_returns200_whenAuthenticated() throws Exception {
        withPermission("action.recommendations.config");
        when(configService.getConfig(CLIENT_ID)).thenReturn(null);

        mockMvc.perform(get("/api/v1/admin/recommendations/config"))
                .andExpect(status().isOk());
    }

    // ── PUT /config ──────────────────────────────────────────────────────────

    @Test
    void saveConfig_returns401_whenUnauthenticated() throws Exception {
        mockMvc.perform(put("/api/v1/admin/recommendations/config")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser
    void saveConfig_returns200_whenAuthenticated() throws Exception {
        withPermission("action.recommendations.config");
        when(configService.saveConfig(eq(CLIENT_ID), any())).thenReturn(null);

        mockMvc.perform(put("/api/v1/admin/recommendations/config")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"trainingEnabled\": true, \"incentiveEnabled\": true, " +
                                 "\"maxTrainingRecs\": 5, \"maxIncentiveRecs\": 5, " +
                                 "\"trainingWeights\": {}, \"incentiveWeights\": {}}"))
                .andExpect(status().isOk());
    }

    // ── POST /refresh ────────────────────────────────────────────────────────

    @Test
    void triggerRefresh_returns401_whenUnauthenticated() throws Exception {
        mockMvc.perform(post("/api/v1/admin/recommendations/refresh"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser
    void triggerRefresh_returns200_whenAuthenticated() throws Exception {
        withPermission("action.recommendations.config");

        mockMvc.perform(post("/api/v1/admin/recommendations/refresh"))
                .andExpect(status().isOk());
    }
}

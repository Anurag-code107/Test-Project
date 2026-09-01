package com.tenxengage.app.controller;

import com.tenxengage.app.config.SecurityConfig;
import com.tenxengage.app.security.CustomUserDetailsService;
import com.tenxengage.app.security.JwtTokenProvider;
import com.tenxengage.app.security.PermissionAspect;
import com.tenxengage.app.security.PermissionMetrics;
import com.tenxengage.app.security.TenantValidator;
import com.tenxengage.app.service.ClientService;
import com.tenxengage.app.service.PermissionService;
import com.tenxengage.app.service.recommendation.RecommendationInsightService;
import com.tenxengage.app.service.recommendation.RecommendationService;
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

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(RecommendationController.class)
@Import({SecurityConfig.class, PermissionAspect.class, AopAutoConfiguration.class})
class RecommendationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean private RecommendationService recommendationService;
    @MockBean private RecommendationInsightService insightService;
    @MockBean private TenantValidator tenantValidator;
    @MockBean private PermissionService permissionService;
    @MockBean private PermissionMetrics permissionMetrics;
    @MockBean private JwtTokenProvider jwtTokenProvider;
    @MockBean private CustomUserDetailsService customUserDetailsService;
    @MockBean private ClientService clientService;
    @MockBean private JpaMetamodelMappingContext jpaMetamodelMappingContext;

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID CLIENT_ID = UUID.randomUUID();
    private static final UUID TARGET_ID = UUID.randomUUID();

    private void withPermission(String permission) {
        when(tenantValidator.getCurrentUserId()).thenReturn(USER_ID);
        when(tenantValidator.getCurrentClientId()).thenReturn(CLIENT_ID);
        when(permissionService.resolveEffectivePermissions(USER_ID)).thenReturn(Set.of(permission));
    }

    // ── GET /training ────────────────────────────────────────────────────────

    @Test
    void getTrainingRecommendations_returns401_whenUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/v1/recommendations/training"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser
    void getTrainingRecommendations_returns200_whenAuthenticated() throws Exception {
        withPermission("action.recommendations.view");
        when(recommendationService.getTrainingRecommendations(CLIENT_ID, USER_ID))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/v1/recommendations/training"))
                .andExpect(status().isOk());
    }

    // ── GET /incentives ──────────────────────────────────────────────────────

    @Test
    void getIncentiveRecommendations_returns401_whenUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/v1/recommendations/incentives"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser
    void getIncentiveRecommendations_returns200_whenAuthenticated() throws Exception {
        withPermission("action.recommendations.view");
        when(recommendationService.getIncentiveRecommendations(CLIENT_ID, USER_ID))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/v1/recommendations/incentives"))
                .andExpect(status().isOk());
    }

    // ── POST /{type}/{targetId}/interactions ─────────────────────────────────

    @Test
    void recordInteraction_returns401_whenUnauthenticated() throws Exception {
        mockMvc.perform(post("/api/v1/recommendations/training/" + TARGET_ID + "/interactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser
    void recordInteraction_returns200_whenAuthenticated() throws Exception {
        withPermission("action.recommendations.interact");

        mockMvc.perform(post("/api/v1/recommendations/training/" + TARGET_ID + "/interactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"interactionType\": \"VIEW\"}"))
                .andExpect(status().isOk());
    }

    // ── POST /{type}/{targetId}/complete ─────────────────────────────────────

    @Test
    void completeRecommendation_returns401_whenUnauthenticated() throws Exception {
        mockMvc.perform(post("/api/v1/recommendations/training/" + TARGET_ID + "/complete"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser
    void completeRecommendation_returns200_whenAuthenticated() throws Exception {
        withPermission("action.recommendations.interact");
        when(recommendationService.recordCompletion(eq(CLIENT_ID), eq(USER_ID), eq(TARGET_ID), any()))
                .thenReturn(null);

        mockMvc.perform(post("/api/v1/recommendations/training/" + TARGET_ID + "/complete"))
                .andExpect(status().isOk());
    }

    // ── POST /{type}/{targetId}/insight ──────────────────────────────────────

    @Test
    void streamInsight_returns401_whenUnauthenticated() throws Exception {
        mockMvc.perform(post("/api/v1/recommendations/training/" + TARGET_ID + "/insight"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser
    void streamInsight_returns200_whenAuthenticated() throws Exception {
        withPermission("action.recommendations.interact");

        mockMvc.perform(post("/api/v1/recommendations/training/" + TARGET_ID + "/insight"))
                .andExpect(status().isOk());
    }
}

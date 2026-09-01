package com.tenxengage.app.controller;

import com.tenxengage.app.config.SecurityConfig;
import com.tenxengage.app.security.CustomUserDetailsService;
import com.tenxengage.app.security.JwtTokenProvider;
import com.tenxengage.app.service.ClientService;
import com.tenxengage.app.service.NotificationConfigService;
import com.tenxengage.app.service.PermissionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(NotificationConfigController.class)
@Import(SecurityConfig.class)
class NotificationConfigControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockBean private NotificationConfigService notificationConfigService;
    @MockBean private PermissionService permissionService;
    @MockBean private JwtTokenProvider jwtTokenProvider;
    @MockBean private CustomUserDetailsService customUserDetailsService;
    @MockBean private ClientService clientService;
    @MockBean private JpaMetamodelMappingContext jpaMetamodelMappingContext;

    // GET /api/v1/notification-configs
    @Test
    void getConfigs_returns401ForUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/v1/notification-configs"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser
    void getConfigs_returns200ForAuthenticatedUser() throws Exception {
        when(notificationConfigService.getConfigs()).thenReturn(Collections.emptyList());
        mockMvc.perform(get("/api/v1/notification-configs"))
                .andExpect(status().isOk());
    }

    // PUT /api/v1/notification-configs
    @Test
    void updateConfig_returns401ForUnauthenticated() throws Exception {
        mockMvc.perform(put("/api/v1/notification-configs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"notificationTypeCode\":\"INCENTIVE_CREATED\",\"roles\":[\"MANAGER\"]}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser
    void updateConfig_returns400ForInvalidBody() throws Exception {
        mockMvc.perform(put("/api/v1/notification-configs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    // GET /api/v1/notification-configs/retention
    @Test
    void getRetention_returns401ForUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/v1/notification-configs/retention"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser
    void getRetention_returns200ForAuthenticatedUser() throws Exception {
        when(notificationConfigService.getRetentionDays()).thenReturn(30);
        mockMvc.perform(get("/api/v1/notification-configs/retention"))
                .andExpect(status().isOk());
    }

    // PUT /api/v1/notification-configs/retention
    @Test
    void updateRetention_returns401ForUnauthenticated() throws Exception {
        mockMvc.perform(put("/api/v1/notification-configs/retention")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"retentionDays\":30}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser
    void updateRetention_returns200ForAuthenticatedUser() throws Exception {
        when(notificationConfigService.updateRetentionDays(
                org.mockito.ArgumentMatchers.any())).thenReturn(30);
        mockMvc.perform(put("/api/v1/notification-configs/retention")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"retentionDays\":30}"))
                .andExpect(status().isOk());
    }
}

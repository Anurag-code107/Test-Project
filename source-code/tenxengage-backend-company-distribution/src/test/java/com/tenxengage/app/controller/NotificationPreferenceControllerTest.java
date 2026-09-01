package com.tenxengage.app.controller;

import com.tenxengage.app.config.SecurityConfig;
import com.tenxengage.app.security.CustomUserDetailsService;
import com.tenxengage.app.security.JwtTokenProvider;
import com.tenxengage.app.service.ClientService;
import com.tenxengage.app.service.NotificationPreferenceService;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(NotificationPreferenceController.class)
@Import(SecurityConfig.class)
class NotificationPreferenceControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockBean private NotificationPreferenceService notificationPreferenceService;
    @MockBean private PermissionService permissionService;
    @MockBean private JwtTokenProvider jwtTokenProvider;
    @MockBean private CustomUserDetailsService customUserDetailsService;
    @MockBean private ClientService clientService;
    @MockBean private JpaMetamodelMappingContext jpaMetamodelMappingContext;

    // GET /api/v1/notification-preferences/global
    @Test
    void getGlobalSetting_returns401ForUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/v1/notification-preferences/global"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser
    void getGlobalSetting_returns200ForAuthenticatedUser() throws Exception {
        when(notificationPreferenceService.getGlobalSetting()).thenReturn(null);
        mockMvc.perform(get("/api/v1/notification-preferences/global"))
                .andExpect(status().isOk());
    }

    // PUT /api/v1/notification-preferences/global
    @Test
    void updateGlobalSetting_returns401ForUnauthenticated() throws Exception {
        mockMvc.perform(put("/api/v1/notification-preferences/global")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"notificationsEnabled\":true}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser
    void updateGlobalSetting_returns200ForAuthenticatedUser() throws Exception {
        when(notificationPreferenceService.updateGlobalSetting(any())).thenReturn(null);
        mockMvc.perform(put("/api/v1/notification-preferences/global")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"notificationsEnabled\":true}"))
                .andExpect(status().isOk());
    }

    // GET /api/v1/notification-preferences
    @Test
    void getPreferences_returns401ForUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/v1/notification-preferences"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser
    void getPreferences_returns200ForAuthenticatedUser() throws Exception {
        when(notificationPreferenceService.getPreferences()).thenReturn(Collections.emptyList());
        mockMvc.perform(get("/api/v1/notification-preferences"))
                .andExpect(status().isOk());
    }

    // PUT /api/v1/notification-preferences
    @Test
    void updatePreference_returns401ForUnauthenticated() throws Exception {
        mockMvc.perform(put("/api/v1/notification-preferences")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"notificationTypeId\":\"3fa85f64-5717-4562-b3fc-2c963f66afa6\",\"optedOut\":false}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser
    void updatePreference_returns200ForAuthenticatedUser() throws Exception {
        when(notificationPreferenceService.updatePreference(any())).thenReturn(null);
        mockMvc.perform(put("/api/v1/notification-preferences")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"notificationTypeId\":\"3fa85f64-5717-4562-b3fc-2c963f66afa6\",\"optedOut\":false}"))
                .andExpect(status().isOk());
    }

    // PUT /api/v1/notification-preferences/bulk
    @Test
    void bulkUpdatePreferences_returns401ForUnauthenticated() throws Exception {
        mockMvc.perform(put("/api/v1/notification-preferences/bulk")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"preferences\":[{\"notificationTypeId\":\"3fa85f64-5717-4562-b3fc-2c963f66afa6\",\"optedOut\":false}]}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser
    void bulkUpdatePreferences_returns200ForAuthenticatedUser() throws Exception {
        when(notificationPreferenceService.bulkUpdatePreferences(any())).thenReturn(Collections.emptyList());
        mockMvc.perform(put("/api/v1/notification-preferences/bulk")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"preferences\":[{\"notificationTypeId\":\"3fa85f64-5717-4562-b3fc-2c963f66afa6\",\"optedOut\":false}]}"))
                .andExpect(status().isOk());
    }
}

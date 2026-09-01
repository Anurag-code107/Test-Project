package com.tenxengage.app.controller;

import com.tenxengage.app.config.SecurityConfig;
import com.tenxengage.app.security.CustomUserDetailsService;
import com.tenxengage.app.security.JwtTokenProvider;
import com.tenxengage.app.service.ClientService;
import com.tenxengage.app.service.NotificationService;
import com.tenxengage.app.service.PermissionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(NotificationController.class)
@Import(SecurityConfig.class)
class NotificationControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockBean private NotificationService notificationService;
    @MockBean private PermissionService permissionService;
    @MockBean private JwtTokenProvider jwtTokenProvider;
    @MockBean private CustomUserDetailsService customUserDetailsService;
    @MockBean private ClientService clientService;
    @MockBean private JpaMetamodelMappingContext jpaMetamodelMappingContext;

    @Test
    void getNotifications_returns401ForUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/v1/notifications")).andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "PARTNER_SELLER")
    void getNotifications_returns200ForAuthenticated() throws Exception {
        mockMvc.perform(get("/api/v1/notifications")).andExpect(status().isOk());
    }

    @Test
    void getUnreadCount_returns401ForUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/v1/notifications/unread-count")).andExpect(status().isUnauthorized());
    }

    @Test
    void markAllAsRead_returns401ForUnauthenticated() throws Exception {
        mockMvc.perform(put("/api/v1/notifications/read-all")).andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "PARTNER_SELLER")
    void markAllAsRead_returns200ForAuthenticated() throws Exception {
        mockMvc.perform(put("/api/v1/notifications/read-all")).andExpect(status().isOk());
    }
}

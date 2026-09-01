package com.tenxengage.app.controller;

import com.tenxengage.app.config.SecurityConfig;
import com.tenxengage.app.security.CustomUserDetailsService;
import com.tenxengage.app.security.JwtTokenProvider;
import com.tenxengage.app.service.ClientService;
import com.tenxengage.app.service.NotificationTypeService;
import com.tenxengage.app.service.PermissionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(NotificationTypeController.class)
@Import(SecurityConfig.class)
class NotificationTypeControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockBean private NotificationTypeService notificationTypeService;
    @MockBean private PermissionService permissionService;
    @MockBean private JwtTokenProvider jwtTokenProvider;
    @MockBean private CustomUserDetailsService customUserDetailsService;
    @MockBean private ClientService clientService;
    @MockBean private JpaMetamodelMappingContext jpaMetamodelMappingContext;

    // GET /api/v1/notification-types
    @Test
    void getAllTypes_returns401ForUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/v1/notification-types"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser
    void getAllTypes_returns200ForAuthenticatedUser() throws Exception {
        when(notificationTypeService.getAllTypes()).thenReturn(Collections.emptyList());
        mockMvc.perform(get("/api/v1/notification-types"))
                .andExpect(status().isOk());
    }

    // GET /api/v1/notification-types/by-category
    @Test
    void getTypesByCategory_returns401ForUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/v1/notification-types/by-category")
                        .param("category", "INCENTIVE"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser
    void getTypesByCategory_returns200ForAuthenticatedUser() throws Exception {
        when(notificationTypeService.getTypesByCategory(any())).thenReturn(Collections.emptyList());
        mockMvc.perform(get("/api/v1/notification-types/by-category")
                        .param("category", "INCENTIVE"))
                .andExpect(status().isOk());
    }
}

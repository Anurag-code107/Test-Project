package com.tenxengage.app.controller;

import com.tenxengage.app.config.SecurityConfig;
import com.tenxengage.app.security.CustomUserDetailsService;
import com.tenxengage.app.security.JwtTokenProvider;
import com.tenxengage.app.service.ActivityCategoryService;
import com.tenxengage.app.service.ClientService;
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

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ActivityCategoryController.class)
@Import(SecurityConfig.class)
class ActivityCategoryControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockBean private ActivityCategoryService activityCategoryService;
    @MockBean private PermissionService permissionService;
    @MockBean private JwtTokenProvider jwtTokenProvider;
    @MockBean private CustomUserDetailsService customUserDetailsService;
    @MockBean private ClientService clientService;
    @MockBean private JpaMetamodelMappingContext jpaMetamodelMappingContext;

    @Test
    void getCategories_returns401ForUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/v1/activity-categories"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser
    void getCategories_returns200ForAuthenticatedUser() throws Exception {
        when(activityCategoryService.getCategories()).thenReturn(List.of());
        mockMvc.perform(get("/api/v1/activity-categories"))
                .andExpect(status().isOk());
    }

    @Test
    void createCategory_returns401ForUnauthenticated() throws Exception {
        mockMvc.perform(post("/api/v1/activity-categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Test\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser
    void createCategory_returns201ForAuthenticatedUser() throws Exception {
        when(activityCategoryService.createCategory(any())).thenReturn(null);
        mockMvc.perform(post("/api/v1/activity-categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Test\"}"))
                .andExpect(status().isCreated());
    }

    @Test
    void updateCategory_returns401ForUnauthenticated() throws Exception {
        mockMvc.perform(put("/api/v1/activity-categories/{id}", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Updated\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser
    void updateCategory_returns200ForAuthenticatedUser() throws Exception {
        when(activityCategoryService.updateCategory(any(), any())).thenReturn(null);
        mockMvc.perform(put("/api/v1/activity-categories/{id}", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Updated\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void deleteCategory_returns401ForUnauthenticated() throws Exception {
        mockMvc.perform(delete("/api/v1/activity-categories/{id}", UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser
    void deleteCategory_returns204ForAuthenticatedUser() throws Exception {
        mockMvc.perform(delete("/api/v1/activity-categories/{id}", UUID.randomUUID()))
                .andExpect(status().isNoContent());
    }
}

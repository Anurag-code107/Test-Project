package com.tenxengage.app.controller;

import com.tenxengage.app.config.SecurityConfig;
import com.tenxengage.app.security.CustomUserDetailsService;
import com.tenxengage.app.security.JwtTokenProvider;
import com.tenxengage.app.service.ClientService;
import com.tenxengage.app.service.DataOperationsService;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DataOperationsController.class)
@Import(SecurityConfig.class)
class DataOperationsControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockBean private DataOperationsService dataOperationsService;
    @MockBean private PermissionService permissionService;
    @MockBean private JwtTokenProvider jwtTokenProvider;
    @MockBean private CustomUserDetailsService customUserDetailsService;
    @MockBean private ClientService clientService;
    @MockBean private JpaMetamodelMappingContext jpaMetamodelMappingContext;

    private static final UUID DATA_OBJECT_ID = UUID.randomUUID();

    // --- Upload history ---

    @Test
    void getUploadHistory_returns401ForUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/v1/data-operations/{id}/uploads", DATA_OBJECT_ID))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser
    void getUploadHistory_returns200ForAuthenticatedUser() throws Exception {
        when(dataOperationsService.getUploadHistory(any())).thenReturn(List.of());
        mockMvc.perform(get("/api/v1/data-operations/{id}/uploads", DATA_OBJECT_ID))
                .andExpect(status().isOk());
    }

    // --- Template download ---

    @Test
    void downloadTemplate_returns401ForUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/v1/data-operations/{id}/template", DATA_OBJECT_ID))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser
    void downloadTemplate_returns200ForAuthenticatedUser() throws Exception {
        when(dataOperationsService.generateTemplate(any())).thenReturn("col1,col2\n");
        mockMvc.perform(get("/api/v1/data-operations/{id}/template", DATA_OBJECT_ID))
                .andExpect(status().isOk());
    }

    // --- Connector pull ---

    @Test
    void triggerPull_returns401ForUnauthenticated() throws Exception {
        mockMvc.perform(post("/api/v1/data-operations/{id}/pull", DATA_OBJECT_ID))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser
    void triggerPull_returns200ForAuthenticatedUser() throws Exception {
        when(dataOperationsService.triggerConnectorPull(any())).thenReturn(null);
        mockMvc.perform(post("/api/v1/data-operations/{id}/pull", DATA_OBJECT_ID))
                .andExpect(status().isOk());
    }

    // --- Tagging ---

    @Test
    void getTaggingHistory_returns401ForUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/v1/data-operations/tagging/history"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser
    void getTaggingHistory_returns200ForAuthenticatedUser() throws Exception {
        when(dataOperationsService.getTaggingHistory()).thenReturn(List.of());
        mockMvc.perform(get("/api/v1/data-operations/tagging/history"))
                .andExpect(status().isOk());
    }

    @Test
    void runTaggingJob_returns401ForUnauthenticated() throws Exception {
        mockMvc.perform(post("/api/v1/data-operations/tagging/run"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser
    void runTaggingJob_returns200ForAuthenticatedUser() throws Exception {
        when(dataOperationsService.triggerTaggingJob()).thenReturn(null);
        mockMvc.perform(post("/api/v1/data-operations/tagging/run"))
                .andExpect(status().isOk());
    }

    // --- Sync schedule ---

    @Test
    void getSyncSchedule_returns401ForUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/v1/data-operations/{id}/sync-schedule", DATA_OBJECT_ID))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser
    void getSyncSchedule_returns200ForAuthenticatedUser() throws Exception {
        when(dataOperationsService.getSyncSchedule(any())).thenReturn(null);
        mockMvc.perform(get("/api/v1/data-operations/{id}/sync-schedule", DATA_OBJECT_ID))
                .andExpect(status().isOk());
    }

    @Test
    void updateSyncSchedule_returns401ForUnauthenticated() throws Exception {
        mockMvc.perform(put("/api/v1/data-operations/{id}/sync-schedule", DATA_OBJECT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":true,\"cadence\":\"DAILY\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser
    void updateSyncSchedule_returns200ForAuthenticatedUser() throws Exception {
        when(dataOperationsService.updateSyncSchedule(any(), any())).thenReturn(null);
        mockMvc.perform(put("/api/v1/data-operations/{id}/sync-schedule", DATA_OBJECT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":true,\"cadence\":\"DAILY\"}"))
                .andExpect(status().isOk());
    }
}

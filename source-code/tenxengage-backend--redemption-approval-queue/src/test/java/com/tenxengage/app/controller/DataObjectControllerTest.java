package com.tenxengage.app.controller;

import com.tenxengage.app.config.SecurityConfig;
import com.tenxengage.app.security.CustomUserDetailsService;
import com.tenxengage.app.security.JwtTokenProvider;
import com.tenxengage.app.service.ClientService;
import com.tenxengage.app.service.DataObjectService;
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

@WebMvcTest(DataObjectController.class)
@Import(SecurityConfig.class)
class DataObjectControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockBean private DataObjectService dataObjectService;
    @MockBean private PermissionService permissionService;
    @MockBean private JwtTokenProvider jwtTokenProvider;
    @MockBean private CustomUserDetailsService customUserDetailsService;
    @MockBean private ClientService clientService;
    @MockBean private JpaMetamodelMappingContext jpaMetamodelMappingContext;

    private static final UUID OBJECT_ID = UUID.randomUUID();
    private static final UUID FIELD_ID = UUID.randomUUID();

    // --- Data Object CRUD ---

    @Test
    void getDataObjects_returns401ForUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/v1/data-objects"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser
    void getDataObjects_returns200ForAuthenticatedUser() throws Exception {
        when(dataObjectService.getDataObjects()).thenReturn(List.of());
        mockMvc.perform(get("/api/v1/data-objects"))
                .andExpect(status().isOk());
    }

    @Test
    void getDataObject_returns401ForUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/v1/data-objects/{id}", OBJECT_ID))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser
    void getDataObject_returns200ForAuthenticatedUser() throws Exception {
        when(dataObjectService.getDataObject(any())).thenReturn(null);
        mockMvc.perform(get("/api/v1/data-objects/{id}", OBJECT_ID))
                .andExpect(status().isOk());
    }

    @Test
    void createDataObject_returns401ForUnauthenticated() throws Exception {
        mockMvc.perform(post("/api/v1/data-objects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"TestObj\",\"displayName\":\"Test\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser
    void createDataObject_returns201ForAuthenticatedUser() throws Exception {
        when(dataObjectService.createDataObject(any())).thenReturn(null);
        mockMvc.perform(post("/api/v1/data-objects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"TestObj\",\"displayName\":\"Test\"}"))
                .andExpect(status().isCreated());
    }

    @Test
    void updateDataObject_returns401ForUnauthenticated() throws Exception {
        mockMvc.perform(put("/api/v1/data-objects/{id}", OBJECT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser
    void updateDataObject_returns200ForAuthenticatedUser() throws Exception {
        when(dataObjectService.updateDataObject(any(), any())).thenReturn(null);
        mockMvc.perform(put("/api/v1/data-objects/{id}", OBJECT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk());
    }

    @Test
    void deleteDataObject_returns401ForUnauthenticated() throws Exception {
        mockMvc.perform(delete("/api/v1/data-objects/{id}", OBJECT_ID))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser
    void deleteDataObject_returns204ForAuthenticatedUser() throws Exception {
        mockMvc.perform(delete("/api/v1/data-objects/{id}", OBJECT_ID))
                .andExpect(status().isNoContent());
    }

    // --- Field CRUD ---

    @Test
    void addField_returns401ForUnauthenticated() throws Exception {
        mockMvc.perform(post("/api/v1/data-objects/{id}/fields", OBJECT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"field1\",\"displayName\":\"Field 1\",\"dataType\":\"TEXT\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser
    void addField_returns201ForAuthenticatedUser() throws Exception {
        when(dataObjectService.addField(any(), any())).thenReturn(null);
        mockMvc.perform(post("/api/v1/data-objects/{id}/fields", OBJECT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"field1\",\"displayName\":\"Field 1\",\"dataType\":\"TEXT\"}"))
                .andExpect(status().isCreated());
    }

    @Test
    void updateField_returns401ForUnauthenticated() throws Exception {
        mockMvc.perform(put("/api/v1/data-objects/{id}/fields/{fieldId}", OBJECT_ID, FIELD_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser
    void updateField_returns200ForAuthenticatedUser() throws Exception {
        when(dataObjectService.updateField(any(), any(), any())).thenReturn(null);
        mockMvc.perform(put("/api/v1/data-objects/{id}/fields/{fieldId}", OBJECT_ID, FIELD_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk());
    }

    @Test
    void deleteField_returns401ForUnauthenticated() throws Exception {
        mockMvc.perform(delete("/api/v1/data-objects/{id}/fields/{fieldId}", OBJECT_ID, FIELD_ID))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser
    void deleteField_returns204ForAuthenticatedUser() throws Exception {
        mockMvc.perform(delete("/api/v1/data-objects/{id}/fields/{fieldId}", OBJECT_ID, FIELD_ID))
                .andExpect(status().isNoContent());
    }

    // --- Connector Mapping ---

    @Test
    void setConnectorMapping_returns401ForUnauthenticated() throws Exception {
        mockMvc.perform(put("/api/v1/data-objects/{id}/connector-mapping", OBJECT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"connectorId\":\"" + UUID.randomUUID() + "\",\"mappings\":[]}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser
    void setConnectorMapping_returns200ForAuthenticatedUser() throws Exception {
        mockMvc.perform(put("/api/v1/data-objects/{id}/connector-mapping", OBJECT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"connectorId\":\"" + UUID.randomUUID() + "\",\"mappings\":[]}"))
                .andExpect(status().isOk());
    }

    @Test
    void removeConnectorMapping_returns401ForUnauthenticated() throws Exception {
        mockMvc.perform(delete("/api/v1/data-objects/{id}/connector-mapping", OBJECT_ID))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser
    void removeConnectorMapping_returns204ForAuthenticatedUser() throws Exception {
        mockMvc.perform(delete("/api/v1/data-objects/{id}/connector-mapping", OBJECT_ID))
                .andExpect(status().isNoContent());
    }

    // --- Rule Fields ---

    @Test
    void getRuleFields_returns401ForUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/v1/data-objects/rule-fields"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser
    void getRuleFields_returns200ForAuthenticatedUser() throws Exception {
        when(dataObjectService.getRuleFields(any(), any())).thenReturn(List.of());
        mockMvc.perform(get("/api/v1/data-objects/rule-fields"))
                .andExpect(status().isOk());
    }
}

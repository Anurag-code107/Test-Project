package com.tenxengage.app.controller;

import com.tenxengage.app.config.SecurityConfig;
import com.tenxengage.app.security.CustomUserDetailsService;
import com.tenxengage.app.security.JwtTokenProvider;
import com.tenxengage.app.service.BuilderConfigService;
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

@WebMvcTest(BuilderConfigController.class)
@Import(SecurityConfig.class)
class BuilderConfigControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockBean private BuilderConfigService builderConfigService;
    @MockBean private PermissionService permissionService;
    @MockBean private JwtTokenProvider jwtTokenProvider;
    @MockBean private CustomUserDetailsService customUserDetailsService;
    @MockBean private ClientService clientService;
    @MockBean private JpaMetamodelMappingContext jpaMetamodelMappingContext;

    private static final UUID SECTION_ID = UUID.randomUUID();
    private static final UUID FIELD_ID = UUID.randomUUID();

    @Test
    void getBuilderConfig_returns401ForUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/v1/builder-config/{type}", "SPIFF"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser
    void getBuilderConfig_returns200ForAuthenticatedUser() throws Exception {
        when(builderConfigService.getBuilderConfig(any())).thenReturn(null);
        mockMvc.perform(get("/api/v1/builder-config/{type}", "SPIFF"))
                .andExpect(status().isOk());
    }

    @Test
    void updateSection_returns401ForUnauthenticated() throws Exception {
        mockMvc.perform(put("/api/v1/builder-config/sections/{id}", SECTION_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser
    void updateSection_returns200ForAuthenticatedUser() throws Exception {
        when(builderConfigService.updateSection(any(), any())).thenReturn(null);
        mockMvc.perform(put("/api/v1/builder-config/sections/{id}", SECTION_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk());
    }

    @Test
    void addField_returns401ForUnauthenticated() throws Exception {
        mockMvc.perform(post("/api/v1/builder-config/sections/{id}/fields", SECTION_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fieldKey\":\"test\",\"displayName\":\"Test\",\"fieldType\":\"TEXT\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser
    void addField_returns201ForAuthenticatedUser() throws Exception {
        when(builderConfigService.addField(any(), any())).thenReturn(null);
        mockMvc.perform(post("/api/v1/builder-config/sections/{id}/fields", SECTION_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fieldKey\":\"test\",\"displayName\":\"Test\",\"fieldType\":\"TEXT\"}"))
                .andExpect(status().isCreated());
    }

    @Test
    void updateField_returns401ForUnauthenticated() throws Exception {
        mockMvc.perform(put("/api/v1/builder-config/fields/{id}", FIELD_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser
    void updateField_returns200ForAuthenticatedUser() throws Exception {
        when(builderConfigService.updateField(any(), any())).thenReturn(null);
        mockMvc.perform(put("/api/v1/builder-config/fields/{id}", FIELD_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk());
    }

    @Test
    void removeField_returns401ForUnauthenticated() throws Exception {
        mockMvc.perform(delete("/api/v1/builder-config/fields/{id}", FIELD_ID))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser
    void removeField_returns204ForAuthenticatedUser() throws Exception {
        mockMvc.perform(delete("/api/v1/builder-config/fields/{id}", FIELD_ID))
                .andExpect(status().isNoContent());
    }

    @Test
    void resolveFieldValues_returns401ForUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/v1/builder-config/fields/{id}/values", FIELD_ID))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser
    void resolveFieldValues_returns200ForAuthenticatedUser() throws Exception {
        when(builderConfigService.resolveFieldValues(any(), any())).thenReturn(List.of());
        mockMvc.perform(get("/api/v1/builder-config/fields/{id}/values", FIELD_ID))
                .andExpect(status().isOk());
    }
}

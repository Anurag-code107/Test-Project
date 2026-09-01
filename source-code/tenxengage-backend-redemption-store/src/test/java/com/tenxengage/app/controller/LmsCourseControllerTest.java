package com.tenxengage.app.controller;

import com.tenxengage.app.config.SecurityConfig;
import com.tenxengage.app.security.CustomUserDetailsService;
import com.tenxengage.app.security.JwtTokenProvider;
import com.tenxengage.app.service.ClientService;
import com.tenxengage.app.service.LmsCourseService;
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

@WebMvcTest(LmsCourseController.class)
@Import(SecurityConfig.class)
class LmsCourseControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockBean private LmsCourseService lmsCourseService;
    @MockBean private PermissionService permissionService;
    @MockBean private JwtTokenProvider jwtTokenProvider;
    @MockBean private CustomUserDetailsService customUserDetailsService;
    @MockBean private ClientService clientService;
    @MockBean private JpaMetamodelMappingContext jpaMetamodelMappingContext;

    // GET /api/v1/lms-courses
    @Test
    void getCourses_returns401ForUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/v1/lms-courses"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser
    void getCourses_returns200ForAuthenticatedUser() throws Exception {
        when(lmsCourseService.getCourses(any(), any())).thenReturn(Collections.emptyList());
        mockMvc.perform(get("/api/v1/lms-courses"))
                .andExpect(status().isOk());
    }

    // GET /api/v1/lms-courses/categories
    @Test
    void getCategories_returns401ForUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/v1/lms-courses/categories"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser
    void getCategories_returns200ForAuthenticatedUser() throws Exception {
        when(lmsCourseService.getCategories()).thenReturn(Collections.emptyList());
        mockMvc.perform(get("/api/v1/lms-courses/categories"))
                .andExpect(status().isOk());
    }
}

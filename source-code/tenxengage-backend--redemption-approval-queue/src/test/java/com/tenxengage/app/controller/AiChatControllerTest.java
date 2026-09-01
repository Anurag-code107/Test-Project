package com.tenxengage.app.controller;

import com.tenxengage.app.config.SecurityConfig;
import com.tenxengage.app.security.CustomUserDetailsService;
import com.tenxengage.app.security.JwtTokenProvider;
import com.tenxengage.app.security.PermissionAspect;
import com.tenxengage.app.security.PermissionMetrics;
import com.tenxengage.app.security.TenantValidator;
import com.tenxengage.app.service.AiChatService;
import com.tenxengage.app.service.ClientService;
import com.tenxengage.app.service.DocumentTextExtractor;
import com.tenxengage.app.service.PermissionService;
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

import java.util.Set;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AiChatController.class)
@Import({SecurityConfig.class, PermissionAspect.class, AopAutoConfiguration.class})
class AiChatControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean private AiChatService aiChatService;
    @MockBean private DocumentTextExtractor documentTextExtractor;
    @MockBean private TenantValidator tenantValidator;
    @MockBean private PermissionService permissionService;
    @MockBean private PermissionMetrics permissionMetrics;
    @MockBean private JwtTokenProvider jwtTokenProvider;
    @MockBean private CustomUserDetailsService customUserDetailsService;
    @MockBean private ClientService clientService;
    @MockBean private JpaMetamodelMappingContext jpaMetamodelMappingContext;

    private static final UUID USER_ID = UUID.randomUUID();

    private void withPermission(String permission) {
        when(tenantValidator.getCurrentUserId()).thenReturn(USER_ID);
        when(permissionService.resolveEffectivePermissions(USER_ID)).thenReturn(Set.of(permission));
    }

    private static final String CHAT_REQUEST_JSON =
            "{\"conversationHistory\": [{\"role\": \"user\", \"content\": \"hello\"}], " +
            "\"incentiveType\": \"SALES\", " +
            "\"currentState\": {}}";

    // ── POST /chat ───────────────────────────────────────────────────────────

    @Test
    void chat_returns401_whenUnauthenticated() throws Exception {
        mockMvc.perform(post("/api/v1/ai/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CHAT_REQUEST_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser
    void chat_returns200_whenAuthenticated() throws Exception {
        withPermission("action.ai.copilot");
        when(aiChatService.isAvailable()).thenReturn(false);

        mockMvc.perform(post("/api/v1/ai/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CHAT_REQUEST_JSON))
                .andExpect(status().isOk());
    }

    // ── POST /chat-with-document ─────────────────────────────────────────────

    @Test
    void chatWithDocument_returns401_whenUnauthenticated() throws Exception {
        mockMvc.perform(post("/api/v1/ai/chat-with-document")
                        .contentType(MediaType.MULTIPART_FORM_DATA))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser
    void chatWithDocument_returns200_whenAuthenticatedAndServiceUnavailable() throws Exception {
        withPermission("action.ai.assistant");
        when(aiChatService.isAvailable()).thenReturn(false);

        org.springframework.mock.web.MockMultipartFile file =
                new org.springframework.mock.web.MockMultipartFile(
                        "file", "test.pdf", "application/pdf", new byte[]{1, 2, 3});
        org.springframework.mock.web.MockMultipartFile requestPart =
                new org.springframework.mock.web.MockMultipartFile(
                        "request", "", MediaType.APPLICATION_JSON_VALUE,
                        CHAT_REQUEST_JSON.getBytes());

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .multipart("/api/v1/ai/chat-with-document")
                        .file(file)
                        .file(requestPart))
                .andExpect(status().isOk());
    }
}

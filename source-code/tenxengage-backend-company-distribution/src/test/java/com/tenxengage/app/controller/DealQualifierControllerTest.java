package com.tenxengage.app.controller;

import com.tenxengage.app.config.SecurityConfig;
import com.tenxengage.app.security.CustomUserDetailsService;
import com.tenxengage.app.security.JwtTokenProvider;
import com.tenxengage.app.service.ClientService;
import com.tenxengage.app.service.DealQualifierInsightService;
import com.tenxengage.app.service.DealQualifierService;
import com.tenxengage.app.service.InvoiceExtractionService;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DealQualifierController.class)
@Import(SecurityConfig.class)
class DealQualifierControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockBean private DealQualifierService dealQualifierService;
    @MockBean private InvoiceExtractionService invoiceExtractionService;
    @MockBean private DealQualifierInsightService insightService;
    @MockBean private PermissionService permissionService;
    @MockBean private JwtTokenProvider jwtTokenProvider;
    @MockBean private CustomUserDetailsService customUserDetailsService;
    @MockBean private ClientService clientService;
    @MockBean private JpaMetamodelMappingContext jpaMetamodelMappingContext;

    private static final UUID INCENTIVE_ID = UUID.randomUUID();

    private static final String EVALUATE_BODY =
            "{\"dealValue\":100.00,\"productSkus\":[\"SKU001\"],\"customerSegment\":\"ENTERPRISE\",\"closeDate\":\"2026-12-31T00:00:00Z\"}";

    // --- POST /evaluate ---

    @Test
    void evaluateDeal_returns401ForUnauthenticated() throws Exception {
        mockMvc.perform(post("/api/v1/deal-qualifier/evaluate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(EVALUATE_BODY))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser
    void evaluateDeal_returns200ForAuthenticatedUser() throws Exception {
        when(dealQualifierService.evaluateDeal(any())).thenReturn(
                new com.tenxengage.app.dto.response.DealQualifierResponse(List.of(), "US", null));
        mockMvc.perform(post("/api/v1/deal-qualifier/evaluate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(EVALUATE_BODY))
                .andExpect(status().isOk());
    }

    // --- POST /upload-invoice ---

    @Test
    void uploadInvoice_returns401ForUnauthenticated() throws Exception {
        mockMvc.perform(post("/api/v1/deal-qualifier/upload-invoice"))
                .andExpect(status().isUnauthorized());
    }

    // --- POST /{incentiveId}/insights ---

    @Test
    void streamInsights_returns401ForUnauthenticated() throws Exception {
        mockMvc.perform(post("/api/v1/deal-qualifier/{id}/insights", INCENTIVE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(EVALUATE_BODY))
                .andExpect(status().isUnauthorized());
    }

    // --- GET /partner-context ---

    @Test
    void getPartnerContext_returns401ForUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/v1/deal-qualifier/partner-context"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser
    void getPartnerContext_returns200ForAuthenticatedUser() throws Exception {
        when(dealQualifierService.getPartnerContext()).thenReturn(null);
        mockMvc.perform(get("/api/v1/deal-qualifier/partner-context"))
                .andExpect(status().isOk());
    }
}

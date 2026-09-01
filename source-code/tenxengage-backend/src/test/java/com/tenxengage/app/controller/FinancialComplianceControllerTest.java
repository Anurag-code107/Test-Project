package com.tenxengage.app.controller;

import com.tenxengage.app.config.SecurityConfig;
import com.tenxengage.app.dto.request.CreateWhistleblowerReportRequest;
import com.tenxengage.app.entity.WhistleblowerReport;
import com.tenxengage.app.security.CustomUserDetailsService;
import com.tenxengage.app.security.JwtTokenProvider;
import com.tenxengage.app.security.TenantValidator;
import com.tenxengage.app.service.ClientService;
import com.tenxengage.app.service.TaxReportingService;
import com.tenxengage.app.service.WhistleblowerService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(FinancialComplianceController.class)
@Import(SecurityConfig.class)
class FinancialComplianceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private JwtTokenProvider jwtTokenProvider;

    @MockBean
    private CustomUserDetailsService customUserDetailsService;

    @MockBean
    private ClientService clientService;

    @MockBean
    private TenantValidator tenantValidator;

    @MockBean
    private WhistleblowerService whistleblowerService;

    @MockBean
    private TaxReportingService taxReportingService;

    @MockBean
    private org.springframework.data.jpa.mapping.JpaMetamodelMappingContext jpaMetamodelMappingContext;

    @Test
    @DisplayName("POST /api/v1/compliance/financial/whistleblower/report is public - no auth needed")
    void submitWhistleblowerReportIsPublic() throws Exception {
        WhistleblowerReport mockReport = WhistleblowerReport.builder()
            .trackingNumber("WB-2026-TEST01")
            .build();
        when(whistleblowerService.submitReport(any(CreateWhistleblowerReportRequest.class)))
            .thenReturn(mockReport);

        mockMvc.perform(post("/api/v1/compliance/financial/whistleblower/report")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reportType\": \"FRAUD\", \"description\": \"Suspected fraudulent activity in the rewards program\", \"anonymous\": true}"))
            .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /api/v1/compliance/financial/whistleblower/status/{trackingNumber} is public")
    void checkWhistleblowerStatusIsPublic() throws Exception {
        WhistleblowerReport mockReport = WhistleblowerReport.builder()
            .trackingNumber("WB-2026-ABC123")
            .build();
        when(whistleblowerService.getReportByTrackingNumber(anyString()))
            .thenReturn(mockReport);
        when(whistleblowerService.getCaseUpdates(any()))
            .thenReturn(List.of());

        mockMvc.perform(get("/api/v1/compliance/financial/whistleblower/status/WB-2026-ABC123"))
            .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /api/v1/compliance/financial/whistleblower/reports requires auth - returns 401 without auth")
    void listActiveReportsRequiresAuth() throws Exception {
        mockMvc.perform(get("/api/v1/compliance/financial/whistleblower/reports"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /api/v1/compliance/financial/tax/annual-summary returns 200 for authenticated user")
    void taxAnnualSummaryAccessibleForAuthenticatedUser() throws Exception {
        mockMvc.perform(get("/api/v1/compliance/financial/tax/annual-summary")
                .param("year", "2025")
                .with(SecurityMockMvcRequestPostProcessors.user("seller@test.com").roles("USER")))
            .andExpect(status().isOk());
    }
}

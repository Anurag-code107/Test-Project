package com.tenxengage.app.controller;

import com.tenxengage.app.config.SecurityConfig;
import com.tenxengage.app.security.CustomUserDetailsService;
import com.tenxengage.app.security.JwtTokenProvider;
import com.tenxengage.app.service.ClientService;
import com.tenxengage.app.service.PermissionService;
import com.tenxengage.app.service.RewardTransactionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(RewardTransactionController.class)
@Import(SecurityConfig.class)
class RewardTransactionControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockBean private RewardTransactionService rewardTransactionService;
    @MockBean private com.tenxengage.app.security.TenantValidator tenantValidator;
    @MockBean private PermissionService permissionService;
    @MockBean private JwtTokenProvider jwtTokenProvider;
    @MockBean private CustomUserDetailsService customUserDetailsService;
    @MockBean private ClientService clientService;
    @MockBean private JpaMetamodelMappingContext jpaMetamodelMappingContext;

    @Test
    void getMyTransactions_returns401ForUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/v1/reward-transactions")).andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "PARTNER_SELLER")
    void getMyTransactions_returns200ForPartnerSeller() throws Exception {
        when(rewardTransactionService.getTransactions(any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of()));
        mockMvc.perform(get("/api/v1/reward-transactions")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "PARTNER_ADMIN")
    void getMyTransactions_returns200ForPartnerAdmin() throws Exception {
        when(rewardTransactionService.getTransactions(any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of()));
        mockMvc.perform(get("/api/v1/reward-transactions")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "PARTNER_SELLER")
    void getMyTransactions_acceptsDateRangeParams() throws Exception {
        when(rewardTransactionService.getTransactions(any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of()));
        mockMvc.perform(get("/api/v1/reward-transactions")
                        .param("startDate", "2026-01-01")
                        .param("endDate", "2026-01-31"))
                .andExpect(status().isOk());
    }

    @Test
    void getUserTransactions_returns401ForUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/v1/reward-transactions/{userId}", UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "CLIENT_ADMIN")
    void getUserTransactions_returns200WhenAuthenticated() throws Exception {
        when(rewardTransactionService.getTransactions(any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of()));
        mockMvc.perform(get("/api/v1/reward-transactions/{userId}", UUID.randomUUID()))
                .andExpect(status().isOk());
    }
}

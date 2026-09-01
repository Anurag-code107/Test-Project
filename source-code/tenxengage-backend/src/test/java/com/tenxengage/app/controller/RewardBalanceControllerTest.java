package com.tenxengage.app.controller;

import com.tenxengage.app.config.SecurityConfig;
import com.tenxengage.app.security.CustomUserDetailsService;
import com.tenxengage.app.security.JwtTokenProvider;
import com.tenxengage.app.service.ClientService;
import com.tenxengage.app.service.PermissionService;
import com.tenxengage.app.service.WalletService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(RewardBalanceController.class)
@Import(SecurityConfig.class)
class RewardBalanceControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockBean private WalletService walletService;
    @MockBean private PermissionService permissionService;
    @MockBean private JwtTokenProvider jwtTokenProvider;
    @MockBean private CustomUserDetailsService customUserDetailsService;
    @MockBean private ClientService clientService;
    @MockBean private JpaMetamodelMappingContext jpaMetamodelMappingContext;

    @Test
    void getBalances_returns401ForUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/v1/reward-balances")).andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "PARTNER_SELLER")
    void getBalances_returns200ForAuthenticated() throws Exception {
        mockMvc.perform(get("/api/v1/reward-balances")).andExpect(status().isOk());
    }
}

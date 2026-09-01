package com.tenxengage.app.controller;

import com.tenxengage.app.config.SecurityConfig;
import com.tenxengage.app.dto.response.RewardWalletResponse;
import com.tenxengage.app.exception.ResourceNotFoundException;
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
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(WalletController.class)
@Import(SecurityConfig.class)
class WalletControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockBean private WalletService walletService;
    @MockBean private PermissionService permissionService;
    @MockBean private JwtTokenProvider jwtTokenProvider;
    @MockBean private CustomUserDetailsService customUserDetailsService;
    @MockBean private ClientService clientService;
    @MockBean private JpaMetamodelMappingContext jpaMetamodelMappingContext;

    private static final UUID WALLET_ID = UUID.fromString("11111111-0000-0000-0000-000000000001");
    private static final UUID COMPANY_ID = UUID.fromString("22222222-0000-0000-0000-000000000002");
    private static final UUID USER_ID = UUID.fromString("33333333-0000-0000-0000-000000000003");

    private static final RewardWalletResponse SAMPLE_WALLET =
        new RewardWalletResponse(WALLET_ID, "INDIVIDUAL", "cash", "100.00", "0");

    // -------------------------------------------------------------------------
    // GET /api/v1/wallets/me  (AC-1, AC-5)
    // -------------------------------------------------------------------------

    @Test
    @WithMockUser
    void getMyWallets_returns200_whenAuthenticated() throws Exception {
        when(walletService.getMyWallets()).thenReturn(List.of(SAMPLE_WALLET));

        mockMvc.perform(get("/api/v1/wallets/me"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].walletType").value("INDIVIDUAL"))
            .andExpect(jsonPath("$.data[0].currencyId").value("cash"))
            .andExpect(jsonPath("$.data[0].availableBalance").value("100.00"))
            .andExpect(jsonPath("$.data[0].reservedBalance").value("0"))
            .andExpect(jsonPath("$.data[0].clientId").doesNotExist())
            .andExpect(jsonPath("$.data[0].userId").doesNotExist())
            .andExpect(jsonPath("$.data[0].version").doesNotExist());
    }

    @Test
    void getMyWallets_returns401_whenNotAuthenticated() throws Exception {
        mockMvc.perform(get("/api/v1/wallets/me"))
            .andExpect(status().isUnauthorized());
    }

    // -------------------------------------------------------------------------
    // GET /api/v1/wallets/company/{companyId}  (AC-2)
    // -------------------------------------------------------------------------

    @Test
    @WithMockUser(roles = "PARTNER_ADMIN")
    void getCompanyWallets_returns200_forPartnerAdminOwnCompany() throws Exception {
        RewardWalletResponse companyWallet =
            new RewardWalletResponse(WALLET_ID, "COMPANY", "cash", "500.00", "0");
        when(walletService.getCompanyWallets(COMPANY_ID)).thenReturn(List.of(companyWallet));

        mockMvc.perform(get("/api/v1/wallets/company/{companyId}", COMPANY_ID))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].walletType").value("COMPANY"));
    }

    @Test
    @WithMockUser(roles = "PARTNER_ADMIN")
    void getCompanyWallets_returns403_forPartnerAdminWrongCompany() throws Exception {
        when(walletService.getCompanyWallets(any()))
            .thenThrow(new AccessDeniedException("Access denied: company mismatch"));

        mockMvc.perform(get("/api/v1/wallets/company/{companyId}", COMPANY_ID))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "PARTNER_SELLER")
    void getCompanyWallets_returns403_forPartnerSeller() throws Exception {
        when(walletService.getCompanyWallets(any()))
            .thenThrow(new AccessDeniedException("Partner sellers cannot access company wallets"));

        mockMvc.perform(get("/api/v1/wallets/company/{companyId}", COMPANY_ID))
            .andExpect(status().isForbidden());
    }

    // -------------------------------------------------------------------------
    // GET /api/v1/wallets/users/{userId}  (AC-3)
    // -------------------------------------------------------------------------

    @Test
    @WithMockUser(roles = "CLIENT_ADMIN")
    void getUserWallets_returns200_forClientAdmin() throws Exception {
        when(walletService.getUserWallets(USER_ID)).thenReturn(List.of(SAMPLE_WALLET));

        mockMvc.perform(get("/api/v1/wallets/users/{userId}", USER_ID))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].currencyId").value("cash"));
    }

    @Test
    @WithMockUser(roles = "PARTNER_SELLER")
    void getUserWallets_returns403_forPartnerSeller() throws Exception {
        when(walletService.getUserWallets(any()))
            .thenThrow(new AccessDeniedException("Missing required permission"));

        mockMvc.perform(get("/api/v1/wallets/users/{userId}", USER_ID))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "CLIENT_ADMIN")
    void getUserWallets_returns404_whenUserNotInTenant() throws Exception {
        when(walletService.getUserWallets(USER_ID))
            .thenThrow(new ResourceNotFoundException("RewardWallet", "userId", USER_ID));

        mockMvc.perform(get("/api/v1/wallets/users/{userId}", USER_ID))
            .andExpect(status().isNotFound());
    }
}

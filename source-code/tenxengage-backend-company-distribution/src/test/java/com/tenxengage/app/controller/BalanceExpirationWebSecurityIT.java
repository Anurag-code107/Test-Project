package com.tenxengage.app.controller;

import com.tenxengage.app.AbstractLocalIntegrationTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * T1 web-layer security: unauthenticated requests to the balance-expiration endpoints are
 * rejected by the real security filter chain with 401 — the gap the per-story {@code @WebMvcTest}
 * units (which use {@code @WithMockUser}) cannot cover. 200/403/400/422 per-endpoint behaviour
 * is covered by {@code BalanceExpirationControllerTest} (@WebMvcTest).
 */
@AutoConfigureMockMvc
@Tag("integration")
class BalanceExpirationWebSecurityIT extends AbstractLocalIntegrationTest {

    @Autowired private MockMvc mockMvc;

    @Test
    void getPolicies_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/redemption/expiration/policies"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getExpiringSoon_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/redemption/expiration/expiring-soon"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void upsertPolicy_unauthenticated_returns401() throws Exception {
        mockMvc.perform(put("/api/v1/redemption/expiration/policies/points")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":true,\"expirationMode\":\"FIXED_DATE\","
                                + "\"fixedExpiryDate\":\"2099-01-01\",\"leadTimeDays\":30}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getBreakage_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/redemption/expiration/breakage")
                        .param("from", "2026-01-01").param("to", "2026-01-31"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void breakageExport_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/redemption/expiration/breakage/export")
                        .param("from", "2026-01-01").param("to", "2026-01-31"))
                .andExpect(status().isUnauthorized());
    }
}

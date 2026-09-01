package com.tenxengage.app.controller.xtrm;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tenxengage.app.config.SecurityConfig;
import com.tenxengage.app.dto.request.xtrm.AddCardRequest;
import com.tenxengage.app.dto.request.xtrm.ConfirmWithdrawalRequest;
import com.tenxengage.app.dto.request.xtrm.InitiateWithdrawalRequest;
import com.tenxengage.app.dto.request.xtrm.LinkBankAccountRequest;
import com.tenxengage.app.dto.request.xtrm.SaveRedemptionAddressRequest;
import com.tenxengage.app.dto.request.xtrm.SetDefaultBankRequest;
import com.tenxengage.app.dto.request.xtrm.SetDefaultCardRequest;
import com.tenxengage.app.dto.request.xtrm.SetPayoutMethodRequest;
import com.tenxengage.app.dto.response.xtrm.DigitalWalletResponse;
import com.tenxengage.app.dto.response.xtrm.LinkedBankResponse;
import com.tenxengage.app.dto.response.xtrm.LinkedCardResponse;
import com.tenxengage.app.dto.response.PaginatedResponse;
import com.tenxengage.app.dto.response.xtrm.WithdrawalHistoryResponse;
import com.tenxengage.app.dto.response.xtrm.WithdrawalResultResponse;
import com.tenxengage.app.entity.enums.xtrm.RedemptionPayoutMethod;
import com.tenxengage.app.entity.xtrm.PartnerAddress;
import com.tenxengage.app.entity.xtrm.PartnerRedemption;
import com.tenxengage.app.exception.BusinessRuleException;
import com.tenxengage.app.exception.ExternalServiceException;
import com.tenxengage.app.security.CustomUserDetailsService;
import com.tenxengage.app.security.JwtTokenProvider;
import com.tenxengage.app.security.PermissionAspect;
import com.tenxengage.app.security.PermissionMetrics;
import com.tenxengage.app.security.RateLimitFilter;
import com.tenxengage.app.security.TenantValidator;
import com.tenxengage.app.service.ClientService;
import com.tenxengage.app.service.PermissionService;
import com.tenxengage.app.service.xtrm.XtrmBankService;
import com.tenxengage.app.service.xtrm.XtrmCardService;
import com.tenxengage.app.service.xtrm.XtrmEnrollmentService;
import com.tenxengage.app.service.xtrm.XtrmWalletService;
import com.tenxengage.app.testdata.xtrm.PartnerRedemptionFixtures;
import org.junit.jupiter.api.BeforeEach;
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

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(RedemptionProfileController.class)
@Import({SecurityConfig.class, PermissionAspect.class, AopAutoConfiguration.class})
class RedemptionProfileControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private RateLimitFilter rateLimitFilter;

    @MockBean private XtrmEnrollmentService enrollmentService;
    @MockBean private XtrmBankService bankService;
    @MockBean private XtrmCardService cardService;
    @MockBean private XtrmWalletService walletService;
    @MockBean private PermissionService permissionService;
    @MockBean private TenantValidator tenantValidator;
    @MockBean private PermissionMetrics permissionMetrics;
    @MockBean private JwtTokenProvider jwtTokenProvider;
    @MockBean private CustomUserDetailsService customUserDetailsService;
    @MockBean private ClientService clientService;
    @MockBean private JpaMetamodelMappingContext jpaMetamodelMappingContext;

    private static final UUID CLIENT_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();

    @BeforeEach
    void resetRateLimiter() {
        // The bank-account path is rate-limited (5/min per IP) and its buckets are process-wide, so
        // reset between tests — otherwise the shared MockMvc IP trips the limit across the POST/DELETE cases.
        rateLimitFilter.clearBuckets();
    }

    private void withPermission(String... permissions) {
        when(tenantValidator.getCurrentUserId()).thenReturn(USER_ID);
        when(permissionService.resolveEffectivePermissions(USER_ID)).thenReturn(Set.of(permissions));
    }

    private void withoutPermission() {
        when(tenantValidator.getCurrentUserId()).thenReturn(USER_ID);
        when(permissionService.resolveEffectivePermissions(USER_ID)).thenReturn(Set.of());
    }

    private PartnerRedemption enrolledWithBank() {
        return PartnerRedemptionFixtures.enrolledWithBank(CLIENT_ID, USER_ID, "PAT-SECRET", "BANK-SECRET-1")
                .address(PartnerAddress.builder()
                        .line1("742 Evergreen Terrace").city("Springfield").region("OR")
                        .postalCode("97403").countryIso2("US").build())
                .build();
    }

    // ---- GET /profile ----

    @Test
    @WithMockUser
    void GET_200_returnsProfile_andLeaksNoSecrets() throws Exception {
        withPermission("action.redemption.redeem");
        when(enrollmentService.getProfileView(USER_ID)).thenReturn(enrolledWithBank());

        mockMvc.perform(get("/api/v1/redemption/profile"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.enrollmentStatus").value("ENROLLED"))
                .andExpect(jsonPath("$.data.payoutMethod").value("BANK"))
                .andExpect(jsonPath("$.data.bankLinked").value(true))
                .andExpect(jsonPath("$.data.linkedBankLabel").value("Wells Fargo ••1898"))
                .andExpect(jsonPath("$.data.identityLevel").value("Standard"))
                // The user's own saved address is returned (self-only endpoint) so the form can pre-fill.
                .andExpect(jsonPath("$.data.addressLine1").value("742 Evergreen Terrace"))
                .andExpect(jsonPath("$.data.city").value("Springfield"))
                .andExpect(jsonPath("$.data.countryIso2").value("US"))
                // The PAT and linked-bank id must never be serialized.
                .andExpect(jsonPath("$.data.recipientUserId").doesNotExist())
                .andExpect(jsonPath("$.data.partnerLinkedBankId").doesNotExist())
                .andExpect(jsonPath("$.data.clientId").doesNotExist());
    }


    @Test
    @WithMockUser
    void GET_403_missingPermission() throws Exception {
        withoutPermission();

        mockMvc.perform(get("/api/v1/redemption/profile"))
                .andExpect(status().isForbidden());
    }

    // ---- PUT /profile/address ----

    private SaveRedemptionAddressRequest validAddressRequest() {
        return new SaveRedemptionAddressRequest(
                "742 Evergreen Terrace", null, "Springfield", "OR", "97403", "US");
    }

    @Test
    @WithMockUser
    void PUT_address_200_savesAndReturnsProfile() throws Exception {
        withPermission("action.redemption.redeem");
        when(enrollmentService.saveAddressAndEnroll(eq(USER_ID), any(SaveRedemptionAddressRequest.class)))
                .thenReturn(PartnerRedemptionFixtures.enrolled(CLIENT_ID, USER_ID, "PAT-1").build());

        mockMvc.perform(put("/api/v1/redemption/profile/address")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validAddressRequest())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.enrollmentStatus").value("ENROLLED"));
    }

    @Test
    @WithMockUser
    void PUT_address_400_missingLine1() throws Exception {
        withPermission("action.redemption.redeem");
        SaveRedemptionAddressRequest bad = new SaveRedemptionAddressRequest(
                "", null, "Springfield", "OR", "97403", "US");

        mockMvc.perform(put("/api/v1/redemption/profile/address")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(bad)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser
    void PUT_address_400_invalidCountry() throws Exception {
        withPermission("action.redemption.redeem");
        SaveRedemptionAddressRequest bad = new SaveRedemptionAddressRequest(
                "742 Evergreen Terrace", null, "Springfield", "OR", "97403", "usa");

        mockMvc.perform(put("/api/v1/redemption/profile/address")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(bad)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser
    void PUT_address_403_missingPermission() throws Exception {
        withoutPermission();

        mockMvc.perform(put("/api/v1/redemption/profile/address")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validAddressRequest())))
                .andExpect(status().isForbidden());
    }

    // ---- PUT /profile/payout-method ----

    @Test
    @WithMockUser
    void PUT_200_setPayoutMethod() throws Exception {
        withPermission("action.redemption.redeem");
        when(bankService.setPayoutMethod(eq(USER_ID), eq(RedemptionPayoutMethod.ANYPAY)))
                .thenReturn(PartnerRedemptionFixtures.enrolled(CLIENT_ID, USER_ID, "PAT-1").build());

        mockMvc.perform(put("/api/v1/redemption/profile/payout-method")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SetPayoutMethodRequest(RedemptionPayoutMethod.ANYPAY))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.payoutMethod").value("ANYPAY"));
    }

    @Test
    @WithMockUser
    void PUT_422_bankNotLinked() throws Exception {
        withPermission("action.redemption.redeem");
        when(bankService.setPayoutMethod(eq(USER_ID), eq(RedemptionPayoutMethod.BANK)))
                .thenThrow(new BusinessRuleException("BANK_NOT_LINKED", "Link a bank account first."));

        mockMvc.perform(put("/api/v1/redemption/profile/payout-method")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SetPayoutMethodRequest(RedemptionPayoutMethod.BANK))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("BANK_NOT_LINKED"));
    }

    @Test
    @WithMockUser
    void PUT_400_nullPayoutMethod() throws Exception {
        withPermission("action.redemption.redeem");

        mockMvc.perform(put("/api/v1/redemption/profile/payout-method")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser
    void PUT_400_invalidPayoutMethod() throws Exception {
        withPermission("action.redemption.redeem");

        mockMvc.perform(put("/api/v1/redemption/profile/payout-method")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"payoutMethod\":\"BOGUS\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser
    void PUT_403_missingPermission() throws Exception {
        withoutPermission();

        mockMvc.perform(put("/api/v1/redemption/profile/payout-method")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SetPayoutMethodRequest(RedemptionPayoutMethod.ANYPAY))))
                .andExpect(status().isForbidden());
    }

    // ---- POST /profile/bank-account ----

    private LinkBankAccountRequest validBankRequest() {
        return new LinkBankAccountRequest(
                "Ada Lovelace", "14085551234", "123456789", "021000021", null, "Wells Fargo",
                "123 Main St", null, "Los Angeles", "CA", "90001", "US", "ACH");
    }

    @Test
    @WithMockUser
    void POST_201_linkBank() throws Exception {
        withPermission("action.redemption.redeem");
        when(bankService.addBank(eq(USER_ID), any(LinkBankAccountRequest.class)))
                .thenReturn(enrolledWithBank());

        mockMvc.perform(post("/api/v1/redemption/profile/bank-account")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validBankRequest())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.bankLinked").value(true))
                .andExpect(jsonPath("$.data.linkedBankLabel").value("Wells Fargo ••1898"))
                .andExpect(jsonPath("$.data.partnerLinkedBankId").doesNotExist());
    }

    @Test
    @WithMockUser
    void POST_422_duplicateBank() throws Exception {
        withPermission("action.redemption.redeem");
        when(bankService.addBank(eq(USER_ID), any(LinkBankAccountRequest.class)))
                .thenThrow(new BusinessRuleException("XTRM_BANK_DUPLICATE", "This bank account is already linked."));

        mockMvc.perform(post("/api/v1/redemption/profile/bank-account")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validBankRequest())))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("XTRM_BANK_DUPLICATE"));
    }

    @Test
    @WithMockUser
    void POST_503_xtrmUnavailable() throws Exception {
        withPermission("action.redemption.redeem");
        when(bankService.addBank(eq(USER_ID), any(LinkBankAccountRequest.class)))
                .thenThrow(new ExternalServiceException("XTRM_UNAVAILABLE",
                        "Payouts are temporarily unavailable. Please try again shortly."));

        mockMvc.perform(post("/api/v1/redemption/profile/bank-account")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validBankRequest())))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.errorCode").value("XTRM_UNAVAILABLE"));
    }

    @Test
    @WithMockUser
    void POST_400_missingRequiredFields() throws Exception {
        withPermission("action.redemption.redeem");
        // Blank contactName + bad countryIso2 → bean-validation failure.
        LinkBankAccountRequest bad = new LinkBankAccountRequest(
                "", "14085551234", "123456789", "021000021", null, "Wells Fargo",
                "123 Main St", null, "Los Angeles", "CA", "90001", "usa", "ACH");

        mockMvc.perform(post("/api/v1/redemption/profile/bank-account")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(bad)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser
    void POST_403_missingPermission() throws Exception {
        withoutPermission();

        mockMvc.perform(post("/api/v1/redemption/profile/bank-account")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validBankRequest())))
                .andExpect(status().isForbidden());
    }

    // ---- GET /profile/banks ----

    @Test
    @WithMockUser
    void GET_banks_200_listsBanksWithDefaultFlag() throws Exception {
        withPermission("action.redemption.redeem");
        when(bankService.listBanks(USER_ID)).thenReturn(List.of(
                new LinkedBankResponse(UUID.randomUUID(), "Wells Fargo ••1898", "USD", true),
                new LinkedBankResponse(UUID.randomUUID(), "SBI ••7820", "USD", false)));

        mockMvc.perform(get("/api/v1/redemption/profile/banks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].label").value("Wells Fargo ••1898"))
                .andExpect(jsonPath("$.data[0].isDefault").value(true))
                .andExpect(jsonPath("$.data[1].isDefault").value(false));
    }

    @Test
    @WithMockUser
    void GET_banks_403_missingPermission() throws Exception {
        withoutPermission();

        mockMvc.perform(get("/api/v1/redemption/profile/banks"))
                .andExpect(status().isForbidden());
    }

    // ---- DELETE /profile/banks/{bankId} ----

    @Test
    @WithMockUser
    void DELETE_bank_200_removesSpecificBank() throws Exception {
        withPermission("action.redemption.redeem");
        when(bankService.removeBank(eq(USER_ID), any(UUID.class)))
                .thenReturn(PartnerRedemptionFixtures.enrolled(CLIENT_ID, USER_ID, "PAT-1").build());

        mockMvc.perform(delete("/api/v1/redemption/profile/banks/" + UUID.randomUUID()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.bankLinked").value(false));
    }

    @Test
    @WithMockUser
    void DELETE_bank_403_missingPermission() throws Exception {
        withoutPermission();

        mockMvc.perform(delete("/api/v1/redemption/profile/banks/" + UUID.randomUUID()))
                .andExpect(status().isForbidden());
    }

    // ---- PUT /profile/banks/default ----

    @Test
    @WithMockUser
    void PUT_default_200_setsDefaultBank() throws Exception {
        withPermission("action.redemption.redeem");
        when(bankService.setDefaultBank(eq(USER_ID), any(UUID.class)))
                .thenReturn(enrolledWithBank());

        mockMvc.perform(put("/api/v1/redemption/profile/banks/default")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SetDefaultBankRequest(UUID.randomUUID()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.bankLinked").value(true));
    }

    @Test
    @WithMockUser
    void PUT_default_400_missingBankId() throws Exception {
        withPermission("action.redemption.redeem");

        mockMvc.perform(put("/api/v1/redemption/profile/banks/default")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser
    void PUT_default_403_missingPermission() throws Exception {
        withoutPermission();

        mockMvc.perform(put("/api/v1/redemption/profile/banks/default")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SetDefaultBankRequest(UUID.randomUUID()))))
                .andExpect(status().isForbidden());
    }

    // ---- GET /profile/wallets ----

    @Test
    @WithMockUser
    void GET_wallets_200_listsWallets() throws Exception {
        withPermission("action.redemption.redeem");
        when(walletService.listWallets(USER_ID)).thenReturn(List.of(
                new DigitalWalletResponse("203871", "Wallet - USD", "USD", new BigDecimal("25.00")),
                new DigitalWalletResponse("203872", "Wallet - INR", "INR", new BigDecimal("0.00"))));

        mockMvc.perform(get("/api/v1/redemption/profile/wallets"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].name").value("Wallet - USD"))
                .andExpect(jsonPath("$.data[0].currency").value("USD"))
                .andExpect(jsonPath("$.data[1].currency").value("INR"));
    }

    @Test
    @WithMockUser
    void GET_wallets_422_notEnrolled() throws Exception {
        withPermission("action.redemption.redeem");
        when(walletService.listWallets(USER_ID))
                .thenThrow(new BusinessRuleException("XTRM_NOT_ENROLLED", "not set up"));

        mockMvc.perform(get("/api/v1/redemption/profile/wallets"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("XTRM_NOT_ENROLLED"));
    }

    @Test
    @WithMockUser
    void GET_wallets_403_missingPermission() throws Exception {
        withoutPermission();

        mockMvc.perform(get("/api/v1/redemption/profile/wallets"))
                .andExpect(status().isForbidden());
    }

    // ---- Linked cards ----

    private AddCardRequest validCardRequest() {
        return new AddCardRequest(
                "4111111111111111", "12", "2029", "123", "Visa", "Ada Lovelace",
                "Ada", "Lovelace", "123 Main St", null, "Los Angeles", "CA", "90001", "US");
    }

    private PartnerRedemption enrolledWithCard() {
        return PartnerRedemptionFixtures.enrolled(CLIENT_ID, USER_ID, "PAT-SECRET")
                .partnerLinkedCardId("CARD-TOK-SECRET").linkedCardLabel("Visa ••1111").build();
    }

    @Test
    @WithMockUser
    void GET_cards_200_listsCardsWithDefaultFlag() throws Exception {
        withPermission("action.redemption.redeem");
        when(cardService.listCards(USER_ID)).thenReturn(List.of(
                new LinkedCardResponse(UUID.randomUUID(), "Visa ••1111", "Visa", "Active", true),
                new LinkedCardResponse(UUID.randomUUID(), "Mastercard ••2222", "Mastercard", "Active", false)));

        mockMvc.perform(get("/api/v1/redemption/profile/cards"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].label").value("Visa ••1111"))
                .andExpect(jsonPath("$.data[0].isDefault").value(true))
                .andExpect(jsonPath("$.data[1].isDefault").value(false));
    }

    @Test
    @WithMockUser
    void POST_card_201_linksCard_andLeaksNoToken() throws Exception {
        withPermission("action.redemption.redeem");
        when(cardService.addCard(eq(USER_ID), any(AddCardRequest.class))).thenReturn(enrolledWithCard());

        mockMvc.perform(post("/api/v1/redemption/profile/card")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validCardRequest())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.cardLinked").value(true))
                .andExpect(jsonPath("$.data.linkedCardLabel").value("Visa ••1111"))
                // The raw CardToken must never be serialized.
                .andExpect(jsonPath("$.data.partnerLinkedCardId").doesNotExist());
    }

    @Test
    @WithMockUser
    void POST_card_400_invalidCardNumber() throws Exception {
        withPermission("action.redemption.redeem");
        AddCardRequest bad = new AddCardRequest(
                "not-a-number", "12", "2029", "123", "Visa", "Ada Lovelace",
                "Ada", "Lovelace", "123 Main St", null, "Los Angeles", "CA", "90001", "US");

        mockMvc.perform(post("/api/v1/redemption/profile/card")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(bad)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser
    void POST_card_503_xtrmUnavailable() throws Exception {
        withPermission("action.redemption.redeem");
        when(cardService.addCard(eq(USER_ID), any(AddCardRequest.class)))
                .thenThrow(new ExternalServiceException("XTRM_UNAVAILABLE",
                        "Payouts are temporarily unavailable. Please try again shortly."));

        mockMvc.perform(post("/api/v1/redemption/profile/card")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validCardRequest())))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.errorCode").value("XTRM_UNAVAILABLE"));
    }

    @Test
    @WithMockUser
    void POST_card_403_missingPermission() throws Exception {
        withoutPermission();

        mockMvc.perform(post("/api/v1/redemption/profile/card")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validCardRequest())))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser
    void DELETE_card_200_removesSpecificCard() throws Exception {
        withPermission("action.redemption.redeem");
        when(cardService.removeCard(eq(USER_ID), any(UUID.class)))
                .thenReturn(PartnerRedemptionFixtures.enrolled(CLIENT_ID, USER_ID, "PAT-1").build());

        mockMvc.perform(delete("/api/v1/redemption/profile/cards/" + UUID.randomUUID()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.cardLinked").value(false));
    }

    @Test
    @WithMockUser
    void PUT_defaultCard_200_setsDefaultCard() throws Exception {
        withPermission("action.redemption.redeem");
        when(cardService.setDefaultCard(eq(USER_ID), any(UUID.class))).thenReturn(enrolledWithCard());

        mockMvc.perform(put("/api/v1/redemption/profile/cards/default")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SetDefaultCardRequest(UUID.randomUUID()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.cardLinked").value(true))
                .andExpect(jsonPath("$.data.linkedCardLabel").value("Visa ••1111"));
    }

    @Test
    @WithMockUser
    void DELETE_card_403_missingPermission() throws Exception {
        withoutPermission();

        mockMvc.perform(delete("/api/v1/redemption/profile/cards/" + UUID.randomUUID()))
                .andExpect(status().isForbidden());
    }

    // ---- Wallet withdrawal ----

    @Test
    @WithMockUser
    void POST_withdrawInitiate_200_returnsOtpSent() throws Exception {
        withPermission("action.redemption.redeem");
        when(walletService.initiateWithdrawal(eq(USER_ID), any(InitiateWithdrawalRequest.class)))
                .thenReturn(WithdrawalResultResponse.otpSent());

        mockMvc.perform(post("/api/v1/redemption/profile/withdrawals/initiate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new InitiateWithdrawalRequest(new BigDecimal("100.00"), "BANK", UUID.randomUUID()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.otpRequired").value(true))
                .andExpect(jsonPath("$.data.transactionId").doesNotExist());
    }

    @Test
    @WithMockUser
    void POST_withdrawInitiate_400_badDestinationType() throws Exception {
        withPermission("action.redemption.redeem");

        mockMvc.perform(post("/api/v1/redemption/profile/withdrawals/initiate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":100.00,\"destinationType\":\"WIRE\",\"destinationId\":\""
                                + UUID.randomUUID() + "\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser
    void POST_withdrawConfirm_200_returnsExecutedAmounts() throws Exception {
        withPermission("action.redemption.redeem");
        when(walletService.confirmWithdrawal(eq(USER_ID), any(ConfirmWithdrawalRequest.class)))
                .thenReturn(new WithdrawalResultResponse(false, "WD-TX-1", "COMPLETED",
                        new BigDecimal("100.00"), new BigDecimal("2.00"), new BigDecimal("98.00"),
                        "USD", "Visa ••1111"));

        mockMvc.perform(post("/api/v1/redemption/profile/withdrawals/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ConfirmWithdrawalRequest(
                                new BigDecimal("100.00"), "CARD", UUID.randomUUID(), "123456"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.otpRequired").value(false))
                .andExpect(jsonPath("$.data.transactionId").value("WD-TX-1"))
                .andExpect(jsonPath("$.data.amountNet").value(98.00))
                .andExpect(jsonPath("$.data.destinationLabel").value("Visa ••1111"));
    }

    @Test
    @WithMockUser
    void POST_withdrawConfirm_422_invalidOtp() throws Exception {
        withPermission("action.redemption.redeem");
        when(walletService.confirmWithdrawal(eq(USER_ID), any(ConfirmWithdrawalRequest.class)))
                .thenThrow(new BusinessRuleException("XTRM_WITHDRAW_OTP_INVALID", "That code wasn't accepted."));

        mockMvc.perform(post("/api/v1/redemption/profile/withdrawals/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ConfirmWithdrawalRequest(
                                new BigDecimal("100.00"), "CARD", UUID.randomUUID(), "000000"))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("XTRM_WITHDRAW_OTP_INVALID"));
    }

    @Test
    @WithMockUser
    void GET_withdrawals_200_listsHistory() throws Exception {
        withPermission("action.redemption.redeem");
        when(walletService.listWithdrawals(eq(USER_ID), anyInt(), anyInt())).thenReturn(
                new PaginatedResponse<>(List.of(
                        new WithdrawalHistoryResponse(UUID.randomUUID(), new BigDecimal("100.00"),
                                new BigDecimal("2.00"), new BigDecimal("98.00"), "USD", "CARD",
                                "Visa ••1111", "COMPLETED", Instant.now())),
                        0, 5, 1L, 1, false, false));

        mockMvc.perform(get("/api/v1/redemption/profile/withdrawals"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.data[0].destinationType").value("CARD"))
                .andExpect(jsonPath("$.data.data[0].destinationLabel").value("Visa ••1111"))
                .andExpect(jsonPath("$.data.data[0].status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.totalElements").value(1));
    }

    @Test
    @WithMockUser
    void POST_withdrawInitiate_403_missingPermission() throws Exception {
        withoutPermission();

        mockMvc.perform(post("/api/v1/redemption/profile/withdrawals/initiate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new InitiateWithdrawalRequest(new BigDecimal("100.00"), "BANK", UUID.randomUUID()))))
                .andExpect(status().isForbidden());
    }
}

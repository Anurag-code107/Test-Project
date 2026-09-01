package com.tenxengage.app.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tenxengage.app.dto.request.UnclaimRequest;
import com.tenxengage.app.entity.ClaimAction;
import com.tenxengage.app.entity.ClientRole;
import com.tenxengage.app.entity.EligibilityPayout;
import com.tenxengage.app.entity.Incentive;
import com.tenxengage.app.entity.IncentiveAudienceRule;
import com.tenxengage.app.entity.PoEligibilityMapping;
import com.tenxengage.app.entity.PurchaseOrder;
import com.tenxengage.app.entity.RewardTransaction;
import com.tenxengage.app.entity.User;
import com.tenxengage.app.entity.enums.ClaimStatus;
import com.tenxengage.app.entity.enums.IncentiveStatus;
import com.tenxengage.app.entity.enums.IncentiveType;
import com.tenxengage.app.entity.enums.UserStatus;
import com.tenxengage.app.event.NotificationEventProducer;
import com.tenxengage.app.repository.BudgetUtilizationRepository;
import com.tenxengage.app.repository.ClaimActionRepository;
import com.tenxengage.app.repository.CurrencyRepository;
import com.tenxengage.app.repository.IncentiveRepository;
import com.tenxengage.app.repository.PartnerCompanyRepository;
import com.tenxengage.app.repository.PoEligibilityMappingRepository;
import com.tenxengage.app.repository.PurchaseOrderRepository;
import com.tenxengage.app.repository.RewardTransactionRepository;
import com.tenxengage.app.repository.UserRepository;
import com.tenxengage.app.security.CustomUserDetails;
import com.tenxengage.app.security.TenantValidator;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClaimServiceTest {

    @Mock
    private ClaimActionRepository claimActionRepository;
    @Mock
    private PoEligibilityMappingRepository eligibilityMappingRepository;
    @Mock
    private IncentiveRepository incentiveRepository;
    @Mock
    private PurchaseOrderRepository purchaseOrderRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private RewardTransactionRepository rewardTransactionRepository;
    @Mock
    private RewardBalanceService rewardBalanceService;
    @Mock
    private BudgetUtilizationRepository budgetUtilizationRepository;
    @Mock
    private CurrencyService currencyService;
    @Mock
    private TenantValidator tenantValidator;
    @Mock
    private JdbcTemplate jdbcTemplate;
    @Mock
    private ObjectMapper objectMapper;
    @Mock
    private NotificationEventProducer notificationEventProducer;
    @Mock
    private ParticipantEligibilityChecker eligibilityChecker;
    @Mock
    private PartnerCompanyRepository partnerCompanyRepository;

    @InjectMocks
    private ClaimService claimService;

    private UUID clientId;
    private UUID userId;
    private UUID poId;
    private UUID partnerCompanyId;
    private CustomUserDetails userDetails;
    private PurchaseOrder purchaseOrder;
    private User currentUser;

    @BeforeEach
    void setUp() {
        clientId = UUID.randomUUID();
        userId = UUID.randomUUID();
        poId = UUID.randomUUID();
        partnerCompanyId = UUID.randomUUID();

        currentUser = User.builder()
                .email("seller@test.com")
                .firstName("Test")
                .lastName("Seller")
                .passwordHash("$2a$10$hash")
                .status(UserStatus.ACTIVE)
                .clientId(clientId)
                .partnerCompanyId(partnerCompanyId)
                .build();
        currentUser.setId(userId);

        userDetails = new CustomUserDetails(currentUser);

        purchaseOrder = PurchaseOrder.builder()
                .clientId(clientId)
                .orderNumber("PO-001")
                .partnerCompanyId(partnerCompanyId)
                .totalAmount(new BigDecimal("10000"))
                .build();
        purchaseOrder.setId(poId);
    }

    // -------------------------------------------------------------------------
    // claimDeal()
    // -------------------------------------------------------------------------

    @Test
    void claimDeal_throwsWhenPONotFound() {
        when(tenantValidator.getCurrentUserDetails()).thenReturn(userDetails);
        when(purchaseOrderRepository.findByIdAndClientId(poId, clientId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> claimService.claimDeal(poId))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void claimDeal_throwsWhenAlreadyClaimed() {
        when(tenantValidator.getCurrentUserDetails()).thenReturn(userDetails);
        when(purchaseOrderRepository.findByIdAndClientId(poId, clientId))
                .thenReturn(Optional.of(purchaseOrder));
        when(claimActionRepository.existsByClientIdAndPurchaseOrderIdAndUserId(clientId, poId, userId))
                .thenReturn(true);

        assertThatThrownBy(() -> claimService.claimDeal(poId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Already claimed");
    }

    @Test
    void claimDeal_throwsWhenMaxClaimersReached() {
        when(tenantValidator.getCurrentUserDetails()).thenReturn(userDetails);
        when(purchaseOrderRepository.findByIdAndClientId(poId, clientId))
                .thenReturn(Optional.of(purchaseOrder));
        when(claimActionRepository.existsByClientIdAndPurchaseOrderIdAndUserId(clientId, poId, userId))
                .thenReturn(false);
        // getMaxClaimersForPO uses eligibilityMappingRepository internally
        Incentive incentive = Incentive.builder()
                .name("Test")
                .incentiveType(IncentiveType.SALES)
                .status(IncentiveStatus.ACTIVE)
                .clientId(clientId)
                .createdBy(UUID.randomUUID())
                .maxClaimersPerDeal(1)
                .build();
        incentive.setId(UUID.randomUUID());
        PoEligibilityMapping mapping = PoEligibilityMapping.builder()
                .eligible(true)
                .incentiveId(incentive.getId())
                .build();
        when(eligibilityMappingRepository.findByPurchaseOrderIdAndEligible(poId, true))
                .thenReturn(List.of(mapping));
        when(incentiveRepository.findById(incentive.getId())).thenReturn(Optional.of(incentive));
        when(claimActionRepository.countByClientIdAndPurchaseOrderId(clientId, poId)).thenReturn(1L);

        assertThatThrownBy(() -> claimService.claimDeal(poId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Maximum claimers");
    }

    // -------------------------------------------------------------------------
    // claimDeal() — BUG-020 regression
    //
    // ROLE audience rules now store ClientRole.id (UUID) in rule_value, matching
    // LOCATION. Three paths must hold:
    //   (1) user's role id matches rule_value UUID → awardPayout fires, non-zero amount
    //   (2) user's role id does NOT match → zero-amount audit rows, no credit
    //   (3) rule_value is a legacy display-name string (external writer or regressed
    //       seed path) → transitional fallback matches against ClientRole.name
    // -------------------------------------------------------------------------

    @Test
    void claimDeal_awardsPayoutWhenUserRoleIdMatchesAudienceRuleUuid() {
        UUID roleId = UUID.randomUUID();
        ClientRole role = ClientRole.builder()
                .name("Partner Seller")
                .baseRoleName("PARTNER_SELLER")
                .build();
        role.setId(roleId);
        currentUser.setClientRole(role);

        Incentive incentive = buildIncentiveWithRoleAudience(roleId.toString());
        EligibilityPayout payout = EligibilityPayout.builder()
                .currencyId("cash")
                .payoutAmount(new BigDecimal("500"))
                .build();
        PoEligibilityMapping mapping = PoEligibilityMapping.builder()
                .eligible(true)
                .incentiveId(incentive.getId())
                .payouts(List.of(payout))
                .build();

        stubClaimDealHappyPath(incentive, mapping);

        claimService.claimDeal(poId);

        ArgumentCaptor<RewardTransaction> txCaptor = ArgumentCaptor.forClass(RewardTransaction.class);
        verify(rewardTransactionRepository).save(txCaptor.capture());
        RewardTransaction saved = txCaptor.getValue();
        assertThat(saved.getAmountAwarded())
                .as("BUG-020: role id match must drive awardPayout and award the configured amount")
                .isEqualByComparingTo(new BigDecimal("500"));
        assertThat(saved.getAmountPotential())
                .as("amount_potential must equal configured payout when role check passes")
                .isEqualByComparingTo(new BigDecimal("500"));
        verify(rewardBalanceService).credit(eq(clientId), eq(userId), eq("cash"), eq(new BigDecimal("500")), any(), any());
    }

    @Test
    void claimDeal_writesZeroAuditRowsWhenUserRoleIdDoesNotMatchAudienceRuleUuid() {
        UUID audienceRoleId = UUID.randomUUID();
        UUID userHasDifferentRoleId = UUID.randomUUID();
        ClientRole role = ClientRole.builder()
                .name("Approver")
                .baseRoleName("ACTIVITY_APPROVER")
                .build();
        role.setId(userHasDifferentRoleId);
        currentUser.setClientRole(role);

        Incentive incentive = buildIncentiveWithRoleAudience(audienceRoleId.toString());
        EligibilityPayout payout = EligibilityPayout.builder()
                .currencyId("cash")
                .payoutAmount(new BigDecimal("500"))
                .build();
        PoEligibilityMapping mapping = PoEligibilityMapping.builder()
                .eligible(true)
                .incentiveId(incentive.getId())
                .payouts(List.of(payout))
                .build();

        stubClaimDealHappyPath(incentive, mapping);

        claimService.claimDeal(poId);

        ArgumentCaptor<RewardTransaction> txCaptor = ArgumentCaptor.forClass(RewardTransaction.class);
        verify(rewardTransactionRepository).save(txCaptor.capture());
        RewardTransaction saved = txCaptor.getValue();
        assertThat(saved.getAmountAwarded())
                .as("role id mismatch must route to the zero-amount audit branch")
                .isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(saved.getAmountPotential())
                .as("audit rows carry amount_potential = 0 (distinguishes from a budget-cap zero)")
                .isEqualByComparingTo(BigDecimal.ZERO);
        verify(rewardBalanceService, never()).credit(any(), any(), any(), any(), any(), any());
    }

    @Test
    void claimDeal_transitionalFallbackMatchesDisplayNameWhenRuleValueIsNotAUuid() {
        // Simulates a rule row where rule_value holds a display name instead of a UUID
        // (external writer, regressed seed path, etc.). The app-layer fallback must
        // match case-insensitively against ClientRole.name so the claim still awards
        // correctly while the offending writer is tracked down.
        ClientRole role = ClientRole.builder()
                .name("Partner Seller")
                .baseRoleName("PARTNER_SELLER")
                .build();
        role.setId(UUID.randomUUID());
        currentUser.setClientRole(role);

        Incentive incentive = buildIncentiveWithRoleAudience("Partner Seller");
        EligibilityPayout payout = EligibilityPayout.builder()
                .currencyId("cash")
                .payoutAmount(new BigDecimal("250"))
                .build();
        PoEligibilityMapping mapping = PoEligibilityMapping.builder()
                .eligible(true)
                .incentiveId(incentive.getId())
                .payouts(List.of(payout))
                .build();

        stubClaimDealHappyPath(incentive, mapping);

        claimService.claimDeal(poId);

        ArgumentCaptor<RewardTransaction> txCaptor = ArgumentCaptor.forClass(RewardTransaction.class);
        verify(rewardTransactionRepository).save(txCaptor.capture());
        assertThat(txCaptor.getValue().getAmountAwarded())
                .as("transitional fallback must accept a legacy display-name rule_value")
                .isEqualByComparingTo(new BigDecimal("250"));
        verify(rewardBalanceService).credit(eq(clientId), eq(userId), eq("cash"), eq(new BigDecimal("250")), any(), any());
    }

    // Shared stubbing for a claimDeal happy-path invocation: one eligible mapping,
    // one incentive (no budget so applyBudgetCap short-circuits), buildClaimDetail
    // returns cleanly.
    private void stubClaimDealHappyPath(Incentive incentive, PoEligibilityMapping mapping) {
        when(tenantValidator.getCurrentUserDetails()).thenReturn(userDetails);
        when(purchaseOrderRepository.findByIdAndClientId(poId, clientId))
                .thenReturn(Optional.of(purchaseOrder));
        when(claimActionRepository.existsByClientIdAndPurchaseOrderIdAndUserId(clientId, poId, userId))
                .thenReturn(false);
        when(claimActionRepository.countByClientIdAndPurchaseOrderId(clientId, poId)).thenReturn(0L);
        when(eligibilityMappingRepository.findByPurchaseOrderIdAndEligible(poId, true))
                .thenReturn(List.of(mapping));
        when(incentiveRepository.findById(incentive.getId())).thenReturn(Optional.of(incentive));
        when(incentiveRepository.findAllById(any())).thenReturn(List.of(incentive));
        when(userRepository.findById(userId)).thenReturn(Optional.of(currentUser));
        when(claimActionRepository.save(any(ClaimAction.class))).thenAnswer(inv -> {
            ClaimAction ca = inv.getArgument(0);
            ca.setId(UUID.randomUUID());
            return ca;
        });
        // lenient: only consulted when incentive.maxPerPartner != null (checkMaxPerPartner)
        org.mockito.Mockito.lenient().when(
                rewardTransactionRepository.sumAwardedByClientIdAndIncentiveIdAndPartnerCompanyId(
                        any(), any(), any())).thenReturn(BigDecimal.ZERO);
        when(currencyService.getMonetaryCodes(clientId)).thenReturn(Set.of("cash"));
        // buildClaimDetail at the tail of claimDeal
        when(claimActionRepository.findByClientIdAndPurchaseOrderId(clientId, poId))
                .thenReturn(List.of());
        when(eligibilityMappingRepository.findByPurchaseOrderIdWithPayouts(poId))
                .thenReturn(List.of());
        when(rewardTransactionRepository.findByClientIdAndPurchaseOrderId(clientId, poId))
                .thenReturn(List.of());
        when(incentiveRepository.searchByClientId(any(), any(), any(), any(), any()))
                .thenReturn(org.springframework.data.domain.Page.empty());
    }

    private Incentive buildIncentiveWithRoleAudience(String ruleValue) {
        IncentiveAudienceRule rule = IncentiveAudienceRule.builder()
                .ruleType("ROLE")
                .ruleValue(ruleValue)
                .build();
        Incentive incentive = Incentive.builder()
                .name("BUG-020 regression incentive")
                .incentiveType(IncentiveType.SALES)
                .status(IncentiveStatus.ACTIVE)
                .clientId(clientId)
                .createdBy(UUID.randomUUID())
                .maxClaimersPerDeal(3)
                .build();
        incentive.setId(UUID.randomUUID());
        incentive.setAudienceRules(new java.util.ArrayList<>(List.of(rule)));
        return incentive;
    }

    // -------------------------------------------------------------------------
    // unclaimDeal()
    // -------------------------------------------------------------------------

    @Test
    void unclaimDeal_requiresAdminRole() {
        CustomUserDetails sellerDetails = mock(CustomUserDetails.class);
        org.mockito.Mockito.doReturn(Set.of(new SimpleGrantedAuthority("ROLE_PARTNER_SELLER"))).when(sellerDetails).getAuthorities();
        when(tenantValidator.getCurrentClientId()).thenReturn(clientId);
        when(tenantValidator.isTenxAdmin()).thenReturn(false);
        when(tenantValidator.getCurrentUserDetails()).thenReturn(sellerDetails);

        assertThatThrownBy(() -> claimService.unclaimDeal(poId, new UnclaimRequest("reason")))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Only admins");
    }

    @Test
    void unclaimDeal_throwsWhenNoClaimsExist() {
        CustomUserDetails adminDetails = mock(CustomUserDetails.class);
        org.mockito.Mockito.doReturn(Set.of(new SimpleGrantedAuthority("ROLE_CLIENT_ADMIN"))).when(adminDetails).getAuthorities();
        when(tenantValidator.getCurrentClientId()).thenReturn(clientId);
        when(tenantValidator.isTenxAdmin()).thenReturn(false);
        when(tenantValidator.getCurrentUserDetails()).thenReturn(adminDetails);
        when(purchaseOrderRepository.findByIdAndClientId(poId, clientId))
                .thenReturn(Optional.of(purchaseOrder));
        when(claimActionRepository.findByClientIdAndPurchaseOrderId(clientId, poId))
                .thenReturn(List.of());

        assertThatThrownBy(() -> claimService.unclaimDeal(poId, new UnclaimRequest("reason")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No claims exist");
    }

    @Test
    void unclaimDeal_reversesRewardTransactions() {
        CustomUserDetails adminDetails = mock(CustomUserDetails.class);
        org.mockito.Mockito.doReturn(Set.of(new SimpleGrantedAuthority("ROLE_CLIENT_ADMIN"))).when(adminDetails).getAuthorities();
        when(tenantValidator.getCurrentClientId()).thenReturn(clientId);
        when(tenantValidator.isTenxAdmin()).thenReturn(false);
        when(tenantValidator.getCurrentUserDetails()).thenReturn(adminDetails);
        when(tenantValidator.getCurrentUserId()).thenReturn(UUID.randomUUID());
        when(purchaseOrderRepository.findByIdAndClientId(poId, clientId))
                .thenReturn(Optional.of(purchaseOrder));

        UUID claimActionId = UUID.randomUUID();
        ClaimAction claimAction = ClaimAction.builder()
                .clientId(clientId)
                .purchaseOrderId(poId)
                .userId(userId)
                .build();
        claimAction.setId(claimActionId);

        RewardTransaction tx = RewardTransaction.builder()
                .clientId(clientId)
                .userId(userId)
                .currencyId("USD_CASH")
                .amountAwarded(new BigDecimal("100.00"))
                .incentiveId(UUID.randomUUID())
                .build();
        tx.setId(UUID.randomUUID());

        when(claimActionRepository.findByClientIdAndPurchaseOrderId(clientId, poId))
                .thenReturn(List.of(claimAction));
        when(rewardTransactionRepository.findByClaimActionId(claimActionId))
                .thenReturn(List.of(tx));
        when(currencyService.getMonetaryCodes(clientId)).thenReturn(Set.of());
        // buildClaimDetail requires many more mocks (it builds the full response).
        // Mock the minimum needed so the response building doesn't NPE.
        when(eligibilityMappingRepository.findByPurchaseOrderIdAndEligible(eq(poId), any(Boolean.class)))
                .thenReturn(List.of());
        when(incentiveRepository.searchByClientId(any(), any(), any(), any(), any()))
                .thenReturn(org.springframework.data.domain.Page.empty());

        claimService.unclaimDeal(poId, new UnclaimRequest("Incorrect claim"));

        verify(rewardBalanceService).debit(eq(clientId), eq(userId), eq("USD_CASH"), eq(new BigDecimal("100.00")), any(), any());
        verify(rewardTransactionRepository).deleteByClaimActionId(claimActionId);
        verify(claimActionRepository).deleteAll(List.of(claimAction));
        verify(notificationEventProducer).publish(any());
    }

    // -------------------------------------------------------------------------
    // getClaimDetail()
    // -------------------------------------------------------------------------

    @Test
    void getClaimDetail_throwsWhenPONotInTenant() {
        when(tenantValidator.getCurrentClientId()).thenReturn(clientId);
        when(purchaseOrderRepository.findByIdAndClientId(poId, clientId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> claimService.getClaimDetail(poId))
                .isInstanceOf(EntityNotFoundException.class);
    }

    // -------------------------------------------------------------------------
    // getClaims() — regression coverage for BUG-001
    //
    // mapClaimResponse reads rs.getString("partner_region"). If the SELECT
    // clause omits that alias, JDBC throws SQLException and the endpoint 500s.
    // These tests capture the SQL passed to JdbcTemplate and assert every
    // column label the row mapper reads is present, for both admin and
    // non-admin callers (same mapper is used in both paths).
    // -------------------------------------------------------------------------

    @Test
    void getClaims_selectClauseExposesEveryColumnTheMapperReads_forAdmin() {
        CustomUserDetails adminDetails = mock(CustomUserDetails.class);
        when(adminDetails.getClientId()).thenReturn(clientId);
        when(adminDetails.getUserId()).thenReturn(userId);
        when(adminDetails.isTenxAdmin()).thenReturn(false);
        org.mockito.Mockito.doReturn(Set.of(new SimpleGrantedAuthority("ROLE_CLIENT_ADMIN")))
                .when(adminDetails).getAuthorities();
        when(tenantValidator.getCurrentUserDetails()).thenReturn(adminDetails);
        when(currencyService.getMonetaryCodes(clientId)).thenReturn(Set.of());
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), any(Object[].class)))
                .thenReturn(1L);
        when(jdbcTemplate.query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<?>>any(),
                any(Object[].class))).thenReturn(List.of());

        claimService.getClaims(null, null, null, null, null, null, null, PageRequest.of(0, 20));

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).query(sqlCaptor.capture(),
                org.mockito.ArgumentMatchers.<RowMapper<?>>any(), any(Object[].class));
        assertSelectExposesMapperColumns(sqlCaptor.getValue());
    }

    @Test
    void getClaims_selectClauseExposesEveryColumnTheMapperReads_forNonAdmin() {
        CustomUserDetails sellerDetails = mock(CustomUserDetails.class);
        when(sellerDetails.getClientId()).thenReturn(clientId);
        when(sellerDetails.getUserId()).thenReturn(userId);
        when(sellerDetails.getPartnerCompanyId()).thenReturn(partnerCompanyId);
        when(sellerDetails.isTenxAdmin()).thenReturn(false);
        org.mockito.Mockito.doReturn(Set.of(new SimpleGrantedAuthority("ROLE_PARTNER_SELLER")))
                .when(sellerDetails).getAuthorities();
        when(tenantValidator.getCurrentUserDetails()).thenReturn(sellerDetails);
        when(currencyService.getMonetaryCodes(clientId)).thenReturn(Set.of());
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), any(Object[].class)))
                .thenReturn(1L);
        when(jdbcTemplate.query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<?>>any(),
                any(Object[].class))).thenReturn(List.of());
        // BUG-021: non-admin callers trigger buildAudiencePassIdsLiteral which reads user,
        // partner company, and active SALES incentives to compute the passIds array bound
        // into the SQL. Empty passIds is fine for this SELECT-clause shape assertion.
        when(userRepository.findById(userId)).thenReturn(Optional.empty());
        when(incentiveRepository.searchByClientId(eq(clientId), any(), any(), any(), any()))
                .thenReturn(org.springframework.data.domain.Page.empty());

        claimService.getClaims(null, null, null, null, null, null, null, PageRequest.of(0, 20));

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).query(sqlCaptor.capture(),
                org.mockito.ArgumentMatchers.<RowMapper<?>>any(), any(Object[].class));
        assertSelectExposesMapperColumns(sqlCaptor.getValue());
    }

    // -------------------------------------------------------------------------
    // getClaims() — BUG-021 regression
    //
    // Non-admin callers must have the elig-aggregate columns (eligible_count,
    // total_payout, incentive_names, primary_incentive_name, payout_by_currency)
    // filtered by the current user's audience-passing incentive set. Admin
    // callers must not — they get the tenant-wide aggregate.
    // -------------------------------------------------------------------------

    @Test
    void getClaims_forNonAdmin_injectsAudiencePassIdsArrayIntoEligSubquery() {
        CustomUserDetails sellerDetails = mock(CustomUserDetails.class);
        when(sellerDetails.getClientId()).thenReturn(clientId);
        when(sellerDetails.getUserId()).thenReturn(userId);
        when(sellerDetails.getPartnerCompanyId()).thenReturn(partnerCompanyId);
        when(sellerDetails.isTenxAdmin()).thenReturn(false);
        org.mockito.Mockito.doReturn(Set.of(new SimpleGrantedAuthority("ROLE_PARTNER_SELLER")))
                .when(sellerDetails).getAuthorities();
        when(tenantValidator.getCurrentUserDetails()).thenReturn(sellerDetails);
        when(currencyService.getMonetaryCodes(clientId)).thenReturn(Set.of());
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), any(Object[].class)))
                .thenReturn(1L);
        when(jdbcTemplate.query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<?>>any(),
                any(Object[].class))).thenReturn(List.of());
        when(userRepository.findById(userId)).thenReturn(Optional.empty());
        when(incentiveRepository.searchByClientId(eq(clientId), any(), any(), any(), any()))
                .thenReturn(org.springframework.data.domain.Page.empty());

        claimService.getClaims(null, null, null, null, null, null, null, PageRequest.of(0, 20));

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).query(sqlCaptor.capture(),
                org.mockito.ArgumentMatchers.<RowMapper<?>>any(), any(Object[].class));
        String sql = sqlCaptor.getValue();

        assertThat(sql)
                .as("BUG-021: the elig outer WHERE must restrict to audience-passing incentives")
                .contains("pem.incentive_id = ANY(?::uuid[])");
        assertThat(sql)
                .as("BUG-021: total_payout subquery must restrict to audience-passing incentives")
                .contains("pem2.incentive_id = ANY(?::uuid[])");
        assertThat(sql)
                .as("BUG-021: payout_by_currency subquery must restrict to audience-passing incentives")
                .contains("pem3.incentive_id = ANY(?::uuid[])");
    }

    @Test
    void getClaims_forClientAdmin_doesNotInjectAudiencePassIdsFilter() {
        CustomUserDetails adminDetails = mock(CustomUserDetails.class);
        when(adminDetails.getClientId()).thenReturn(clientId);
        when(adminDetails.getUserId()).thenReturn(userId);
        when(adminDetails.isTenxAdmin()).thenReturn(false);
        org.mockito.Mockito.doReturn(Set.of(new SimpleGrantedAuthority("ROLE_CLIENT_ADMIN")))
                .when(adminDetails).getAuthorities();
        when(tenantValidator.getCurrentUserDetails()).thenReturn(adminDetails);
        when(currencyService.getMonetaryCodes(clientId)).thenReturn(Set.of());
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), any(Object[].class)))
                .thenReturn(1L);
        when(jdbcTemplate.query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<?>>any(),
                any(Object[].class))).thenReturn(List.of());

        claimService.getClaims(null, null, null, null, null, null, null, PageRequest.of(0, 20));

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).query(sqlCaptor.capture(),
                org.mockito.ArgumentMatchers.<RowMapper<?>>any(), any(Object[].class));
        String sql = sqlCaptor.getValue();

        assertThat(sql)
                .as("BUG-021: Client Admin's tenant-wide view must not be narrowed to a user's audience")
                .doesNotContain("incentive_id = ANY(?::uuid[])");
    }

    /**
     * Every label read by ClaimService.mapClaimResponse must be projected by
     * the SELECT clause — otherwise rs.getString(label) throws at runtime.
     * Adding a new rs.getXxx(...) call in the mapper without updating the SQL
     * is exactly how BUG-001 landed; this assertion catches that class of
     * regression.
     */
    // -------------------------------------------------------------------------
    // getClaimSummary() — regression coverage for BUG-012
    //
    // Prior to BUG-012, CustomUserDetails only granted ROLE_USER, so the
    // isAdmin check in getClaimSummary never matched and every caller fell
    // into the seller scoping branch (AND rt.user_id = ?). Client Admins,
    // who never claim deals themselves, saw Total Earnings = $0 even when
    // the tenant had plenty of claim data. These tests use real
    // CustomUserDetails objects (not mocks) so a regression in the authority
    // derivation surfaces here as well.
    // -------------------------------------------------------------------------

    @Test
    void getClaimSummary_forClientAdmin_usesTenantWideScopeNotUserScope() {
        UUID clientAdminId = UUID.randomUUID();
        User clientAdmin = User.builder()
                .email("clientadmin@acme.com")
                .firstName("Alice")
                .lastName("Admin")
                .passwordHash("$2a$10$hash")
                .status(UserStatus.ACTIVE)
                .clientId(clientId)
                .build();
        clientAdmin.setId(clientAdminId);
        clientAdmin.setClientRole(ClientRole.builder()
                .name("Client Admin")
                .baseRoleName("CLIENT_ADMIN")
                .build());

        CustomUserDetails realDetails = new CustomUserDetails(clientAdmin);
        when(tenantValidator.getCurrentUserDetails()).thenReturn(realDetails);
        when(jdbcTemplate.queryForList(anyString(), any(Object[].class)))
                .thenReturn(List.of());
        Map<String, Object> countRow = new HashMap<>();
        countRow.put("claimed", 0L);
        countRow.put("unclaimed", 0L);
        when(jdbcTemplate.queryForMap(anyString(), any(Object[].class)))
                .thenReturn(countRow);

        claimService.getClaimSummary(null, null, null, null);

        ArgumentCaptor<String> earningsSqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).queryForList(earningsSqlCaptor.capture(), any(Object[].class));
        String earningsSql = earningsSqlCaptor.getValue();

        assertThat(earningsSql)
                .as("Client Admin must not be scoped to their own user_id")
                .doesNotContain("rt.user_id = ?");
        assertThat(earningsSql)
                .as("Client Admin must not be scoped to a single partner company")
                .doesNotContain("po.partner_company_id = ?");
        assertThat(earningsSql)
                .as("Tenant scoping via client_id must still be present")
                .contains("rt.client_id = ?");
    }

    @Test
    void getClaimSummary_forPartnerSeller_scopesToUserId() {
        UUID sellerId = UUID.randomUUID();
        User seller = User.builder()
                .email("seller@techpartners.com")
                .firstName("Sam")
                .lastName("Seller")
                .passwordHash("$2a$10$hash")
                .status(UserStatus.ACTIVE)
                .clientId(clientId)
                .partnerCompanyId(partnerCompanyId)
                .build();
        seller.setId(sellerId);
        seller.setClientRole(ClientRole.builder()
                .name("Partner Seller")
                .baseRoleName("PARTNER_SELLER")
                .build());

        CustomUserDetails realDetails = new CustomUserDetails(seller);
        when(tenantValidator.getCurrentUserDetails()).thenReturn(realDetails);
        when(jdbcTemplate.queryForList(anyString(), any(Object[].class)))
                .thenReturn(List.of());
        Map<String, Object> countRow = new HashMap<>();
        countRow.put("claimed", 0L);
        countRow.put("unclaimed", 0L);
        when(jdbcTemplate.queryForMap(anyString(), any(Object[].class)))
                .thenReturn(countRow);

        claimService.getClaimSummary(null, null, null, null);

        ArgumentCaptor<String> earningsSqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).queryForList(earningsSqlCaptor.capture(), any(Object[].class));
        String earningsSql = earningsSqlCaptor.getValue();

        assertThat(earningsSql)
                .as("Partner Seller must still be scoped to their own user_id")
                .contains("rt.user_id = ?");
    }

    // -------------------------------------------------------------------------
    // getClaimSummary() — regression coverage for BUG-014
    //
    // The /claims/summary endpoint initially did not accept a `region` query
    // param and the service did not filter earnings or counts by region. The
    // Manage Claims "Total Earnings" card was therefore fixed at tenant-wide
    // regardless of the Region filter selection. These tests pin both the
    // null / GLOBAL no-op behavior and the active-filter behavior (SQL shape +
    // param binding) for both the earnings query and the count query.
    // -------------------------------------------------------------------------

    @Test
    void getClaimSummary_regionNull_omitsRegionFilterFromBothQueries() {
        CustomUserDetails adminDetails = buildClientAdminDetails();
        when(tenantValidator.getCurrentUserDetails()).thenReturn(adminDetails);
        when(jdbcTemplate.queryForList(anyString(), any(Object[].class)))
                .thenReturn(List.of());
        Map<String, Object> countRow = new HashMap<>();
        countRow.put("claimed", 0L);
        countRow.put("unclaimed", 0L);
        when(jdbcTemplate.queryForMap(anyString(), any(Object[].class)))
                .thenReturn(countRow);

        claimService.getClaimSummary(null, null, null, null);

        ArgumentCaptor<String> earningsSqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> earningsParamsCaptor = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate).queryForList(earningsSqlCaptor.capture(), earningsParamsCaptor.capture());
        assertThat(earningsSqlCaptor.getValue())
                .as("Earnings SQL must not include the region subquery when region is null")
                .doesNotContain("partner_company_locations");

        ArgumentCaptor<String> countSqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> countParamsCaptor = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate).queryForMap(countSqlCaptor.capture(), countParamsCaptor.capture());
        assertThat(countSqlCaptor.getValue())
                .as("Count SQL must not include the region subquery when region is null")
                .doesNotContain("partner_company_locations");
    }

    @Test
    void getClaimSummary_regionGlobal_omitsRegionFilter() {
        CustomUserDetails adminDetails = buildClientAdminDetails();
        when(tenantValidator.getCurrentUserDetails()).thenReturn(adminDetails);
        when(jdbcTemplate.queryForList(anyString(), any(Object[].class)))
                .thenReturn(List.of());
        Map<String, Object> countRow = new HashMap<>();
        countRow.put("claimed", 0L);
        countRow.put("unclaimed", 0L);
        when(jdbcTemplate.queryForMap(anyString(), any(Object[].class)))
                .thenReturn(countRow);

        claimService.getClaimSummary(null, null, null, "GLOBAL");

        ArgumentCaptor<String> earningsSqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).queryForList(earningsSqlCaptor.capture(), any(Object[].class));
        assertThat(earningsSqlCaptor.getValue())
                .as("Sentinel 'GLOBAL' must be treated as no filter")
                .doesNotContain("partner_company_locations");
    }

    @Test
    void getClaimSummary_regionUuid_appliesRegionFilterToEarningsAndCountQueries() {
        CustomUserDetails adminDetails = buildClientAdminDetails();
        when(tenantValidator.getCurrentUserDetails()).thenReturn(adminDetails);
        when(jdbcTemplate.queryForList(anyString(), any(Object[].class)))
                .thenReturn(List.of());
        Map<String, Object> countRow = new HashMap<>();
        countRow.put("claimed", 0L);
        countRow.put("unclaimed", 0L);
        when(jdbcTemplate.queryForMap(anyString(), any(Object[].class)))
                .thenReturn(countRow);

        String regionUuid = UUID.randomUUID().toString();
        claimService.getClaimSummary(null, null, null, regionUuid);

        ArgumentCaptor<String> earningsSqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> earningsParamsCaptor = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate).queryForList(earningsSqlCaptor.capture(), earningsParamsCaptor.capture());
        String earningsSql = earningsSqlCaptor.getValue();
        assertThat(earningsSql)
                .as("Earnings SQL must filter by partner_company_locations when region is a UUID")
                .contains("partner_company_locations")
                .contains("location_value_id = ?::uuid")
                .contains("po.partner_company_id IN");
        assertThat(earningsParamsCaptor.getValue())
                .as("Region UUID must be bound into the earnings-query params")
                .contains(regionUuid);

        ArgumentCaptor<String> countSqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> countParamsCaptor = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate).queryForMap(countSqlCaptor.capture(), countParamsCaptor.capture());
        String countSql = countSqlCaptor.getValue();
        assertThat(countSql)
                .as("Count SQL must also filter by partner_company_locations when region is a UUID")
                .contains("partner_company_locations")
                .contains("location_value_id = ?::uuid")
                .contains("po.partner_company_id IN");
        assertThat(countParamsCaptor.getValue())
                .as("Region UUID must be bound into the count-query params")
                .contains(regionUuid);
    }

    private CustomUserDetails buildClientAdminDetails() {
        User clientAdmin = User.builder()
                .email("clientadmin@acme.com")
                .firstName("Alice")
                .lastName("Admin")
                .passwordHash("$2a$10$hash")
                .status(UserStatus.ACTIVE)
                .clientId(clientId)
                .build();
        clientAdmin.setId(UUID.randomUUID());
        clientAdmin.setClientRole(ClientRole.builder()
                .name("Client Admin")
                .baseRoleName("CLIENT_ADMIN")
                .build());
        return new CustomUserDetails(clientAdmin);
    }

    @Test
    void getClaimSummary_forPartnerAdmin_scopesToPartnerCompanyNotUser() {
        UUID partnerAdminId = UUID.randomUUID();
        User partnerAdmin = User.builder()
                .email("partneradmin@techpartners.com")
                .firstName("Paul")
                .lastName("Partner")
                .passwordHash("$2a$10$hash")
                .status(UserStatus.ACTIVE)
                .clientId(clientId)
                .partnerCompanyId(partnerCompanyId)
                .build();
        partnerAdmin.setId(partnerAdminId);
        partnerAdmin.setClientRole(ClientRole.builder()
                .name("Partner Admin")
                .baseRoleName("PARTNER_ADMIN")
                .build());

        CustomUserDetails realDetails = new CustomUserDetails(partnerAdmin);
        when(tenantValidator.getCurrentUserDetails()).thenReturn(realDetails);
        when(jdbcTemplate.queryForList(anyString(), any(Object[].class)))
                .thenReturn(List.of());
        Map<String, Object> countRow = new HashMap<>();
        countRow.put("claimed", 0L);
        countRow.put("unclaimed", 0L);
        when(jdbcTemplate.queryForMap(anyString(), any(Object[].class)))
                .thenReturn(countRow);

        claimService.getClaimSummary(null, null, null, null);

        ArgumentCaptor<String> earningsSqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).queryForList(earningsSqlCaptor.capture(), any(Object[].class));
        String earningsSql = earningsSqlCaptor.getValue();

        assertThat(earningsSql)
                .as("Partner Admin must be scoped to their partner company")
                .contains("po.partner_company_id = ?");
        assertThat(earningsSql)
                .as("Partner Admin must not be scoped to their own user_id")
                .doesNotContain("rt.user_id = ?");
    }

    private void assertSelectExposesMapperColumns(String sql) {
        List<String> mapperColumns = List.of(
                "po_id",
                "order_number",
                "order_date",
                "total_amount",
                "customer_name",
                "partner_company_id",
                "partner_name",
                "partner_region",
                "created_at",
                "updated_at",
                "eligible_count",
                "total_payout",
                "incentive_names",
                "primary_incentive_name",
                "payout_by_currency",
                "claimer_count",
                "claimer_data",
                "status"
        );
        for (String column : mapperColumns) {
            assertThat(sql)
                    .as("SELECT clause must expose '%s' because ClaimService.mapClaimResponse reads it", column)
                    .contains(column);
        }
    }
}

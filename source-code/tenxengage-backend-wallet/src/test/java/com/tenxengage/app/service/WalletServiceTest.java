package com.tenxengage.app.service;

import com.tenxengage.app.dto.response.RewardWalletResponse;
import com.tenxengage.app.entity.RewardWallet;
import com.tenxengage.app.entity.enums.WalletType;
import com.tenxengage.app.exception.BusinessRuleException;
import com.tenxengage.app.exception.ResourceNotFoundException;
import com.tenxengage.app.repository.LedgerEntryRepository;
import com.tenxengage.app.repository.PartnerCompanyRepository;
import com.tenxengage.app.repository.RewardWalletRepository;
import com.tenxengage.app.security.CustomUserDetails;
import com.tenxengage.app.security.TenantContext;
import com.tenxengage.app.security.TenantValidator;
import com.tenxengage.app.testdata.RewardWalletFixtures;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WalletServiceTest {

    @Mock private RewardWalletRepository rewardWalletRepository;
    @Mock private LedgerEntryRepository ledgerEntryRepository;
    @Mock private PartnerCompanyRepository partnerCompanyRepository;
    @Mock private TenantValidator tenantValidator;

    private WalletService walletService;

    private final UUID CLIENT_ID   = UUID.randomUUID();
    private final UUID USER_ID     = UUID.randomUUID();
    private final UUID COMPANY_ID  = UUID.randomUUID();
    private final UUID WALLET_ID   = UUID.randomUUID();
    private final UUID REFERENCE_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        TenantContext.setClientId(CLIENT_ID);
        WalletMutationDelegate delegate = new WalletMutationDelegate(
            rewardWalletRepository, ledgerEntryRepository);
        walletService = new WalletService(
            rewardWalletRepository, ledgerEntryRepository,
            partnerCompanyRepository, tenantValidator, delegate);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    // -------------------------------------------------------------------------
    // getMyWallets
    // -------------------------------------------------------------------------

    @Test
    void getMyWallets_returnsList_whenWalletsExist() {
        when(tenantValidator.getCurrentUserId()).thenReturn(USER_ID);
        RewardWallet wallet = RewardWalletFixtures
            .individualWalletWithBalance(CLIENT_ID, USER_ID, new BigDecimal("50.00"))
            .build();
        when(rewardWalletRepository.findByClientIdAndUserIdAndWalletType(
                CLIENT_ID, USER_ID, WalletType.INDIVIDUAL))
            .thenReturn(List.of(wallet));

        List<RewardWalletResponse> result = walletService.getMyWallets();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).walletType()).isEqualTo("INDIVIDUAL");
        assertThat(result.get(0).currencyId()).isEqualTo("cash");
        assertThat(result.get(0).availableBalance()).isEqualTo("50.00");
        assertThat(result.get(0).reservedBalance()).isEqualTo("0");
    }

    @Test
    void getMyWallets_returnsEmpty_whenNoWallets() {
        when(tenantValidator.getCurrentUserId()).thenReturn(USER_ID);
        when(rewardWalletRepository.findByClientIdAndUserIdAndWalletType(
                CLIENT_ID, USER_ID, WalletType.INDIVIDUAL))
            .thenReturn(List.of());

        List<RewardWalletResponse> result = walletService.getMyWallets();

        assertThat(result).isEmpty();
    }

    // -------------------------------------------------------------------------
    // getCompanyWallets
    // -------------------------------------------------------------------------

    @Test
    void getCompanyWallets_returns403_whenCompanyMismatch() {
        CustomUserDetails partnerAdmin = mockPartnerAdmin(UUID.randomUUID()); // different company
        when(tenantValidator.getCurrentUserDetails()).thenReturn(partnerAdmin);

        assertThatThrownBy(() -> walletService.getCompanyWallets(COMPANY_ID))
            .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void getCompanyWallets_returns403_whenCallerHasNoCompanyClaim() {
        CustomUserDetails partnerAdmin = mockPartnerAdmin(null); // no partnerCompanyId
        when(tenantValidator.getCurrentUserDetails()).thenReturn(partnerAdmin);

        assertThatThrownBy(() -> walletService.getCompanyWallets(COMPANY_ID))
            .isInstanceOf(AccessDeniedException.class)
            .hasMessageContaining("no associated partner company");
    }

    @Test
    void getCompanyWallets_returnsWallets_whenPartnerAdminOwnsCompany() {
        CustomUserDetails partnerAdmin = mockPartnerAdmin(COMPANY_ID);
        when(tenantValidator.getCurrentUserDetails()).thenReturn(partnerAdmin);
        RewardWallet wallet = RewardWalletFixtures.companyWallet(CLIENT_ID, COMPANY_ID).build();
        when(rewardWalletRepository.findByClientIdAndPartnerCompanyIdAndWalletType(
                CLIENT_ID, COMPANY_ID, WalletType.COMPANY))
            .thenReturn(List.of(wallet));

        List<RewardWalletResponse> result = walletService.getCompanyWallets(COMPANY_ID);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).walletType()).isEqualTo("COMPANY");
    }

    // -------------------------------------------------------------------------
    // getUserWallets
    // -------------------------------------------------------------------------

    @Test
    void getUserWallets_returns404_whenUserNotInTenant() {
        UUID otherUserId = UUID.randomUUID();
        when(rewardWalletRepository.findByClientIdAndUserId(CLIENT_ID, otherUserId))
            .thenReturn(List.of());

        assertThatThrownBy(() -> walletService.getUserWallets(otherUserId))
            .isInstanceOf(ResourceNotFoundException.class);
    }

    // -------------------------------------------------------------------------
    // credit — AC-1, AC-2
    // -------------------------------------------------------------------------

    @Test
    void credit_writesLedgerEntry_andUpdatesBalance() {
        RewardWallet wallet = RewardWalletFixtures
            .individualWalletWithBalance(CLIENT_ID, USER_ID, new BigDecimal("50.00"))
            .build();
        wallet.setId(WALLET_ID);

        when(rewardWalletRepository.findForUpdate(CLIENT_ID, USER_ID, "cash", WalletType.INDIVIDUAL))
            .thenReturn(Optional.of(wallet));
        when(ledgerEntryRepository.existsByRewardWalletIdAndReferenceTypeAndReferenceId(
                WALLET_ID, "INCENTIVE", REFERENCE_ID))
            .thenReturn(false);
        when(rewardWalletRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(ledgerEntryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        RewardWallet result = walletService.credit(
            CLIENT_ID, USER_ID, "cash", new BigDecimal("50.00"), "INCENTIVE", REFERENCE_ID, null);

        assertThat(result.getAvailableBalance()).isEqualByComparingTo("100.00");
        verify(ledgerEntryRepository).save(any());
        verify(rewardWalletRepository).save(wallet);
    }

    @Test
    void credit_isIdempotent_whenSameReferenceIdDeliveredTwice() {
        RewardWallet wallet = RewardWalletFixtures
            .individualWalletWithBalance(CLIENT_ID, USER_ID, new BigDecimal("100.00"))
            .build();
        wallet.setId(WALLET_ID);

        when(rewardWalletRepository.findForUpdate(CLIENT_ID, USER_ID, "cash", WalletType.INDIVIDUAL))
            .thenReturn(Optional.of(wallet));
        when(ledgerEntryRepository.existsByRewardWalletIdAndReferenceTypeAndReferenceId(
                WALLET_ID, "INCENTIVE", REFERENCE_ID))
            .thenReturn(true); // duplicate

        RewardWallet result = walletService.credit(
            CLIENT_ID, USER_ID, "cash", new BigDecimal("50.00"), "INCENTIVE", REFERENCE_ID, null);

        assertThat(result.getAvailableBalance()).isEqualByComparingTo("100.00"); // unchanged
        verify(ledgerEntryRepository, never()).save(any());
        verify(rewardWalletRepository, never()).save(any());
    }

    @Test
    void credit_autoCreatesWallet_onFirstCreditForUserCurrency() {
        when(rewardWalletRepository.findForUpdate(CLIENT_ID, USER_ID, "cash", WalletType.INDIVIDUAL))
            .thenReturn(Optional.empty());
        when(rewardWalletRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(ledgerEntryRepository.existsByRewardWalletIdAndReferenceTypeAndReferenceId(
                any(), any(), any()))
            .thenReturn(false);
        when(ledgerEntryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        RewardWallet result = walletService.credit(
            CLIENT_ID, USER_ID, "cash", new BigDecimal("25.00"), "INCENTIVE", REFERENCE_ID, null);

        assertThat(result.getAvailableBalance()).isEqualByComparingTo("25.00");
        assertThat(result.getWalletType()).isEqualTo(WalletType.INDIVIDUAL);
        verify(rewardWalletRepository, times(2)).save(any()); // once for create, once for balance update
    }

    // -------------------------------------------------------------------------
    // reserve — AC-3
    // -------------------------------------------------------------------------

    @Test
    void reserve_decreasesAvailableBalance_andIncreasesReservedBalance() {
        RewardWallet wallet = RewardWalletFixtures
            .individualWalletWithBalance(CLIENT_ID, USER_ID, new BigDecimal("100.00"))
            .build();
        wallet.setId(WALLET_ID);
        when(rewardWalletRepository.findByIdForUpdate(WALLET_ID)).thenReturn(Optional.of(wallet));
        when(rewardWalletRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(ledgerEntryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        RewardWallet result = walletService.reserve(
            WALLET_ID, new BigDecimal("40.00"), "ORDER", REFERENCE_ID);

        assertThat(result.getAvailableBalance()).isEqualByComparingTo("60.00");
        assertThat(result.getReservedBalance()).isEqualByComparingTo("40.00");
        verify(ledgerEntryRepository).save(any());
    }

    @Test
    void reserve_throwsBusinessRuleException_whenInsufficientBalance() {
        RewardWallet wallet = RewardWalletFixtures
            .individualWalletWithBalance(CLIENT_ID, USER_ID, new BigDecimal("10.00"))
            .build();
        wallet.setId(WALLET_ID);
        when(rewardWalletRepository.findByIdForUpdate(WALLET_ID)).thenReturn(Optional.of(wallet));

        assertThatThrownBy(() -> walletService.reserve(
                WALLET_ID, new BigDecimal("50.00"), "ORDER", REFERENCE_ID))
            .isInstanceOf(BusinessRuleException.class)
            .hasMessageContaining("Insufficient available balance for cash");
    }

    // -------------------------------------------------------------------------
    // debit — AC-4
    // -------------------------------------------------------------------------

    @Test
    void debit_decreasesReservedBalance() {
        RewardWallet wallet = RewardWalletFixtures
            .individualWalletWithBalance(CLIENT_ID, USER_ID, new BigDecimal("60.00"))
            .reservedBalance(new BigDecimal("40.00"))
            .build();
        wallet.setId(WALLET_ID);
        when(rewardWalletRepository.findByIdForUpdate(WALLET_ID)).thenReturn(Optional.of(wallet));
        when(rewardWalletRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(ledgerEntryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        RewardWallet result = walletService.debit(
            WALLET_ID, new BigDecimal("40.00"), "ORDER", REFERENCE_ID);

        assertThat(result.getReservedBalance()).isEqualByComparingTo("0");
        assertThat(result.getAvailableBalance()).isEqualByComparingTo("60.00"); // unchanged
    }

    @Test
    void debit_throwsBusinessRuleException_whenInsufficientReservedBalance() {
        RewardWallet wallet = RewardWalletFixtures
            .individualWalletWithBalance(CLIENT_ID, USER_ID, new BigDecimal("100.00"))
            .reservedBalance(new BigDecimal("10.00"))
            .build();
        wallet.setId(WALLET_ID);
        when(rewardWalletRepository.findByIdForUpdate(WALLET_ID)).thenReturn(Optional.of(wallet));

        assertThatThrownBy(() -> walletService.debit(
                WALLET_ID, new BigDecimal("50.00"), "ORDER", REFERENCE_ID))
            .isInstanceOf(BusinessRuleException.class)
            .hasMessageContaining("Reserved balance insufficient");
    }

    // -------------------------------------------------------------------------
    // release — AC-4
    // -------------------------------------------------------------------------

    @Test
    void release_restoresAvailableBalance() {
        RewardWallet wallet = RewardWalletFixtures
            .individualWalletWithBalance(CLIENT_ID, USER_ID, new BigDecimal("60.00"))
            .reservedBalance(new BigDecimal("40.00"))
            .build();
        wallet.setId(WALLET_ID);
        when(rewardWalletRepository.findByIdForUpdate(WALLET_ID)).thenReturn(Optional.of(wallet));
        when(rewardWalletRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(ledgerEntryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        RewardWallet result = walletService.release(
            WALLET_ID, new BigDecimal("40.00"), "ORDER", REFERENCE_ID);

        assertThat(result.getAvailableBalance()).isEqualByComparingTo("100.00");
        assertThat(result.getReservedBalance()).isEqualByComparingTo("0");
    }

    // -------------------------------------------------------------------------
    // returnCredit — AC-5
    // -------------------------------------------------------------------------

    @Test
    void returnCredit_increasesAvailableBalance() {
        RewardWallet wallet = RewardWalletFixtures
            .individualWalletWithBalance(CLIENT_ID, USER_ID, new BigDecimal("50.00"))
            .build();
        wallet.setId(WALLET_ID);
        when(rewardWalletRepository.findByIdForUpdate(WALLET_ID)).thenReturn(Optional.of(wallet));
        when(rewardWalletRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(ledgerEntryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        RewardWallet result = walletService.returnCredit(
            WALLET_ID, new BigDecimal("20.00"), "RETURN", REFERENCE_ID);

        assertThat(result.getAvailableBalance()).isEqualByComparingTo("70.00");
    }

    // -------------------------------------------------------------------------
    // creditInCurrentTx — same-tx credit for grantReward atomicity
    // -------------------------------------------------------------------------

    @Test
    void creditInCurrentTx_writesLedgerEntry_andUpdatesBalance() {
        RewardWallet wallet = RewardWalletFixtures
            .individualWalletWithBalance(CLIENT_ID, USER_ID, new BigDecimal("50.00"))
            .build();
        wallet.setId(WALLET_ID);

        when(rewardWalletRepository.findForUpdate(CLIENT_ID, USER_ID, "cash", WalletType.INDIVIDUAL))
            .thenReturn(Optional.of(wallet));
        when(ledgerEntryRepository.existsByRewardWalletIdAndReferenceTypeAndReferenceId(
                WALLET_ID, "INCENTIVE", REFERENCE_ID))
            .thenReturn(false);
        when(rewardWalletRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(ledgerEntryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        RewardWallet result = walletService.creditInCurrentTx(
            CLIENT_ID, USER_ID, "cash", new BigDecimal("30.00"), "INCENTIVE", REFERENCE_ID, null);

        assertThat(result.getAvailableBalance()).isEqualByComparingTo("80.00");
        verify(rewardWalletRepository).ensureIndividualWalletExists(CLIENT_ID, USER_ID, "cash");
        verify(ledgerEntryRepository).save(any());
        verify(rewardWalletRepository).save(wallet);
    }

    @Test
    void creditInCurrentTx_isIdempotent_whenDuplicateReferenceId() {
        RewardWallet wallet = RewardWalletFixtures
            .individualWalletWithBalance(CLIENT_ID, USER_ID, new BigDecimal("100.00"))
            .build();
        wallet.setId(WALLET_ID);

        when(rewardWalletRepository.findForUpdate(CLIENT_ID, USER_ID, "cash", WalletType.INDIVIDUAL))
            .thenReturn(Optional.of(wallet));
        when(ledgerEntryRepository.existsByRewardWalletIdAndReferenceTypeAndReferenceId(
                WALLET_ID, "INCENTIVE", REFERENCE_ID))
            .thenReturn(true);

        RewardWallet result = walletService.creditInCurrentTx(
            CLIENT_ID, USER_ID, "cash", new BigDecimal("50.00"), "INCENTIVE", REFERENCE_ID, null);

        assertThat(result.getAvailableBalance()).isEqualByComparingTo("100.00");
        verify(ledgerEntryRepository, never()).save(any());
        verify(rewardWalletRepository, never()).save(any());
    }

    @Test
    void creditInCurrentTx_usesEnsureExistsPattern_onFirstCredit() {
        RewardWallet newWallet = RewardWalletFixtures
            .individualWalletWithBalance(CLIENT_ID, USER_ID, BigDecimal.ZERO)
            .build();
        newWallet.setId(WALLET_ID);

        when(rewardWalletRepository.findForUpdate(CLIENT_ID, USER_ID, "cash", WalletType.INDIVIDUAL))
            .thenReturn(Optional.of(newWallet));
        when(ledgerEntryRepository.existsByRewardWalletIdAndReferenceTypeAndReferenceId(
                any(), any(), any()))
            .thenReturn(false);
        when(rewardWalletRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(ledgerEntryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        RewardWallet result = walletService.creditInCurrentTx(
            CLIENT_ID, USER_ID, "cash", new BigDecimal("25.00"), "INCENTIVE", REFERENCE_ID, null);

        assertThat(result.getAvailableBalance()).isEqualByComparingTo("25.00");
        verify(rewardWalletRepository).ensureIndividualWalletExists(CLIENT_ID, USER_ID, "cash");
    }

    // -------------------------------------------------------------------------
    // creditCompany — company wallet credit (covers doCreditCompanyInTx)
    // -------------------------------------------------------------------------

    @Test
    void creditCompany_writesLedgerEntry_andUpdatesBalance() {
        RewardWallet wallet = RewardWalletFixtures
            .companyWalletWithBalance(CLIENT_ID, COMPANY_ID, new BigDecimal("200.00"))
            .build();
        wallet.setId(WALLET_ID);

        when(rewardWalletRepository.findForUpdateByCompany(CLIENT_ID, COMPANY_ID, "cash", WalletType.COMPANY))
            .thenReturn(Optional.of(wallet));
        when(ledgerEntryRepository.existsByRewardWalletIdAndReferenceTypeAndReferenceId(
                WALLET_ID, "INCENTIVE", REFERENCE_ID))
            .thenReturn(false);
        when(rewardWalletRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(ledgerEntryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        RewardWallet result = walletService.creditCompany(
            CLIENT_ID, COMPANY_ID, "cash", new BigDecimal("50.00"), "INCENTIVE", REFERENCE_ID, null);

        assertThat(result.getAvailableBalance()).isEqualByComparingTo("250.00");
        verify(ledgerEntryRepository).save(any());
        verify(rewardWalletRepository).save(wallet);
    }

    @Test
    void creditCompany_isIdempotent_whenDuplicateReferenceId() {
        RewardWallet wallet = RewardWalletFixtures
            .companyWalletWithBalance(CLIENT_ID, COMPANY_ID, new BigDecimal("200.00"))
            .build();
        wallet.setId(WALLET_ID);

        when(rewardWalletRepository.findForUpdateByCompany(CLIENT_ID, COMPANY_ID, "cash", WalletType.COMPANY))
            .thenReturn(Optional.of(wallet));
        when(ledgerEntryRepository.existsByRewardWalletIdAndReferenceTypeAndReferenceId(
                WALLET_ID, "INCENTIVE", REFERENCE_ID))
            .thenReturn(true);

        RewardWallet result = walletService.creditCompany(
            CLIENT_ID, COMPANY_ID, "cash", new BigDecimal("50.00"), "INCENTIVE", REFERENCE_ID, null);

        assertThat(result.getAvailableBalance()).isEqualByComparingTo("200.00");
        verify(ledgerEntryRepository, never()).save(any());
        verify(rewardWalletRepository, never()).save(any());
    }

    // -------------------------------------------------------------------------
    // optimistic lock retry — AC-5
    // -------------------------------------------------------------------------

    @Test
    void credit_retriesOnOptimisticLockException_upTo3Times() {
        RewardWallet wallet = RewardWalletFixtures
            .individualWalletWithBalance(CLIENT_ID, USER_ID, BigDecimal.ZERO)
            .build();
        wallet.setId(WALLET_ID);

        when(rewardWalletRepository.findForUpdate(CLIENT_ID, USER_ID, "cash", WalletType.INDIVIDUAL))
            .thenReturn(Optional.of(wallet));
        when(ledgerEntryRepository.existsByRewardWalletIdAndReferenceTypeAndReferenceId(
                any(), any(), any()))
            .thenReturn(false);
        when(ledgerEntryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(rewardWalletRepository.save(any()))
            .thenThrow(new ObjectOptimisticLockingFailureException(RewardWallet.class, WALLET_ID));

        assertThatThrownBy(() -> walletService.credit(
                CLIENT_ID, USER_ID, "cash", new BigDecimal("10.00"), "INCENTIVE", REFERENCE_ID, null))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("Service temporarily unavailable");

        verify(rewardWalletRepository, times(3)).save(any());
    }

    // -------------------------------------------------------------------------
    // helpers
    // -------------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    private CustomUserDetails mockPartnerAdmin(UUID partnerCompanyId) {
        CustomUserDetails user = mock(CustomUserDetails.class);
        when(user.getPartnerCompanyId()).thenReturn(partnerCompanyId);
        java.util.Collection authorities = List.of(
            new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_USER"),
            new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_PARTNER_ADMIN")
        );
        org.mockito.Mockito.doReturn(authorities).when(user).getAuthorities();
        return user;
    }
}

package com.tenxengage.app.service.xtrm;

import com.tenxengage.app.dto.request.xtrm.ConfirmWithdrawalRequest;
import com.tenxengage.app.dto.request.xtrm.InitiateWithdrawalRequest;
import com.tenxengage.app.dto.response.xtrm.DigitalWalletResponse;
import com.tenxengage.app.dto.response.xtrm.WithdrawalResultResponse;
import com.tenxengage.app.entity.xtrm.PartnerLinkedBank;
import com.tenxengage.app.entity.xtrm.PartnerLinkedCard;
import com.tenxengage.app.entity.xtrm.PartnerRedemption;
import com.tenxengage.app.entity.xtrm.PartnerWithdrawal;
import com.tenxengage.app.exception.BusinessRuleException;
import com.tenxengage.app.exception.ExternalServiceException;
import com.tenxengage.app.exception.ResourceNotFoundException;
import com.tenxengage.app.repository.xtrm.PartnerLinkedBankRepository;
import com.tenxengage.app.repository.xtrm.PartnerLinkedCardRepository;
import com.tenxengage.app.repository.xtrm.PartnerWithdrawalRepository;
import com.tenxengage.app.service.xtrm.XtrmApiClient.GetWalletsCommand;
import com.tenxengage.app.service.xtrm.XtrmApiClient.GetWalletsResult;
import com.tenxengage.app.service.xtrm.XtrmApiClient.UserWithdrawCommand;
import com.tenxengage.app.service.xtrm.XtrmApiClient.UserWithdrawResult;
import com.tenxengage.app.service.xtrm.XtrmApiClient.WalletInfo;
import com.tenxengage.app.testdata.xtrm.PartnerRedemptionFixtures;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class XtrmWalletServiceTest {

    @Mock
    private XtrmApiClient xtrmApiClient;
    @Mock
    private XtrmEnrollmentService enrollmentService;
    @Mock
    private PartnerLinkedBankRepository linkedBankRepository;
    @Mock
    private PartnerLinkedCardRepository linkedCardRepository;
    @Mock
    private PartnerWithdrawalRepository withdrawalRepository;

    private static final UUID CLIENT_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID BANK_ID = UUID.randomUUID();
    private static final UUID CARD_ID = UUID.randomUUID();

    private XtrmWalletService service() {
        XtrmWalletService service = new XtrmWalletService(
                xtrmApiClient, enrollmentService, linkedBankRepository, linkedCardRepository, withdrawalRepository);
        ReflectionTestUtils.setField(service, "bankPaymentMethodId", "XTR94500");
        ReflectionTestUtils.setField(service, "rapidTransferPaymentMethodId", "XTR94508");
        return service;
    }

    private static PartnerLinkedBank bank() {
        PartnerLinkedBank bank = PartnerLinkedBank.builder()
                .clientId(CLIENT_ID).userId(USER_ID)
                .xtrmBeneficiaryId("BEN-1").maskedLabel("Wells Fargo ••1898")
                .currency("USD").countryIso2("US").withdrawType("ACH").build();
        bank.setId(BANK_ID);
        return bank;
    }

    private static PartnerLinkedCard card() {
        PartnerLinkedCard card = PartnerLinkedCard.builder()
                .clientId(CLIENT_ID).userId(USER_ID)
                .cardToken("CARD-TOK-1").maskedLast4("1111").cardType("Visa").status("Active").build();
        card.setId(CARD_ID);
        return card;
    }

    // ---- listWallets ----------------------------------------------------

    @Test
    void listWallets_enrolled_returnsAllWalletsMapped() {
        PartnerRedemption profile = PartnerRedemptionFixtures.enrolled(CLIENT_ID, USER_ID, "PAT-1").build();
        when(enrollmentService.getProfileView(USER_ID)).thenReturn(profile);
        when(xtrmApiClient.getBeneficiaryWallets(any(GetWalletsCommand.class))).thenReturn(
                GetWalletsResult.ok(List.of(
                        new WalletInfo("203871", "Wallet - USD", "USD", new BigDecimal("25.00")),
                        new WalletInfo("203872", "Wallet - INR", "INR", new BigDecimal("0.00")))));

        List<DigitalWalletResponse> wallets = service().listWallets(USER_ID);

        assertThat(wallets).hasSize(2);
        assertThat(wallets.get(0).name()).isEqualTo("Wallet - USD");
        assertThat(wallets.get(0).balance()).isEqualByComparingTo("25.00");
        assertThat(wallets.get(1).currency()).isEqualTo("INR");
    }

    @Test
    void listWallets_notEnrolled_throws422AndSkipsXtrm() {
        PartnerRedemption profile = PartnerRedemptionFixtures.notEnrolled(CLIENT_ID, USER_ID).build();
        when(enrollmentService.getProfileView(USER_ID)).thenReturn(profile);

        assertThatThrownBy(() -> service().listWallets(USER_ID))
                .isInstanceOf(BusinessRuleException.class)
                .hasFieldOrPropertyWithValue("errorCode", "XTRM_NOT_ENROLLED");

        verifyNoInteractions(xtrmApiClient);
    }

    @Test
    void listWallets_transientXtrm_throws503() {
        PartnerRedemption profile = PartnerRedemptionFixtures.enrolled(CLIENT_ID, USER_ID, "PAT-1").build();
        when(enrollmentService.getProfileView(USER_ID)).thenReturn(profile);
        when(xtrmApiClient.getBeneficiaryWallets(any(GetWalletsCommand.class)))
                .thenReturn(GetWalletsResult.failed(List.of("Could not reach XTRM"), true));

        assertThatThrownBy(() -> service().listWallets(USER_ID))
                .isInstanceOf(ExternalServiceException.class)
                .hasFieldOrPropertyWithValue("errorCode", "XTRM_UNAVAILABLE");
    }

    @Test
    void listWallets_nonRetryableFailure_throws422() {
        PartnerRedemption profile = PartnerRedemptionFixtures.enrolled(CLIENT_ID, USER_ID, "PAT-1").build();
        when(enrollmentService.getProfileView(USER_ID)).thenReturn(profile);
        when(xtrmApiClient.getBeneficiaryWallets(any(GetWalletsCommand.class)))
                .thenReturn(GetWalletsResult.failed(List.of("boom"), false));

        assertThatThrownBy(() -> service().listWallets(USER_ID))
                .isInstanceOf(BusinessRuleException.class)
                .hasFieldOrPropertyWithValue("errorCode", "XTRM_WALLETS_FAILED");
    }

    // ---- initiateWithdrawal --------------------------------------------

    @Test
    void initiateWithdrawal_bank_sendsBankRailWithoutOtp_returnsOtpSent() {
        PartnerRedemption profile = PartnerRedemptionFixtures.enrolled(CLIENT_ID, USER_ID, "PAT-1").build();
        when(enrollmentService.getProfileView(USER_ID)).thenReturn(profile);
        when(linkedBankRepository.findByIdAndUserIdAndClientId(BANK_ID, USER_ID, CLIENT_ID))
                .thenReturn(Optional.of(bank()));
        when(xtrmApiClient.userWithdrawFund(any(UserWithdrawCommand.class)))
                .thenReturn(UserWithdrawResult.otpSent());

        InitiateWithdrawalRequest request =
                new InitiateWithdrawalRequest(new BigDecimal("100.00"), "BANK", BANK_ID);
        WithdrawalResultResponse response = service().initiateWithdrawal(USER_ID, request);

        assertThat(response.otpRequired()).isTrue();
        assertThat(response.transactionId()).isNull();

        ArgumentCaptor<UserWithdrawCommand> cmd = ArgumentCaptor.forClass(UserWithdrawCommand.class);
        verify(xtrmApiClient).userWithdrawFund(cmd.capture());
        assertThat(cmd.getValue().userLinkedBankId()).isEqualTo("BEN-1");
        assertThat(cmd.getValue().cardToken()).isNull();
        assertThat(cmd.getValue().paymentMethodId()).isEqualTo("XTR94500");
        assertThat(cmd.getValue().otp()).isNull();
        verify(withdrawalRepository, never()).save(any());
    }

    @Test
    void initiateWithdrawal_notEnrolled_throws422AndSkipsXtrm() {
        PartnerRedemption profile = PartnerRedemptionFixtures.notEnrolled(CLIENT_ID, USER_ID).build();
        when(enrollmentService.getProfileView(USER_ID)).thenReturn(profile);

        InitiateWithdrawalRequest request =
                new InitiateWithdrawalRequest(new BigDecimal("100.00"), "BANK", BANK_ID);
        assertThatThrownBy(() -> service().initiateWithdrawal(USER_ID, request))
                .isInstanceOf(BusinessRuleException.class)
                .hasFieldOrPropertyWithValue("errorCode", "XTRM_NOT_ENROLLED");

        verifyNoInteractions(xtrmApiClient);
    }

    @Test
    void initiateWithdrawal_unknownDestination_throws404() {
        PartnerRedemption profile = PartnerRedemptionFixtures.enrolled(CLIENT_ID, USER_ID, "PAT-1").build();
        when(enrollmentService.getProfileView(USER_ID)).thenReturn(profile);
        when(linkedBankRepository.findByIdAndUserIdAndClientId(BANK_ID, USER_ID, CLIENT_ID))
                .thenReturn(Optional.empty());

        InitiateWithdrawalRequest request =
                new InitiateWithdrawalRequest(new BigDecimal("100.00"), "BANK", BANK_ID);
        assertThatThrownBy(() -> service().initiateWithdrawal(USER_ID, request))
                .isInstanceOf(ResourceNotFoundException.class);

        verifyNoInteractions(xtrmApiClient);
    }

    // ---- confirmWithdrawal ---------------------------------------------

    @Test
    void confirmWithdrawal_card_persistsCompletedWithdrawal() {
        PartnerRedemption profile = PartnerRedemptionFixtures.enrolled(CLIENT_ID, USER_ID, "PAT-1").build();
        when(enrollmentService.getProfileView(USER_ID)).thenReturn(profile);
        when(linkedCardRepository.findByIdAndUserIdAndClientId(CARD_ID, USER_ID, CLIENT_ID))
                .thenReturn(Optional.of(card()));
        when(xtrmApiClient.userWithdrawFund(any(UserWithdrawCommand.class))).thenReturn(
                UserWithdrawResult.completed("WD-TX-1", "Completed",
                        new BigDecimal("98.00"), new BigDecimal("2.00"), new BigDecimal("100.00"), "USD"));
        when(withdrawalRepository.save(any(PartnerWithdrawal.class))).thenAnswer(inv -> inv.getArgument(0));

        ConfirmWithdrawalRequest request =
                new ConfirmWithdrawalRequest(new BigDecimal("100.00"), "CARD", CARD_ID, "123456");
        WithdrawalResultResponse response = service().confirmWithdrawal(USER_ID, request);

        assertThat(response.otpRequired()).isFalse();
        assertThat(response.transactionId()).isEqualTo("WD-TX-1");
        assertThat(response.amountGross()).isEqualByComparingTo("100.00");
        assertThat(response.fee()).isEqualByComparingTo("2.00");
        assertThat(response.amountNet()).isEqualByComparingTo("98.00");
        assertThat(response.destinationLabel()).isEqualTo("Visa ••1111");

        // The command carried the card rail + OTP.
        ArgumentCaptor<UserWithdrawCommand> cmd = ArgumentCaptor.forClass(UserWithdrawCommand.class);
        verify(xtrmApiClient).userWithdrawFund(cmd.capture());
        assertThat(cmd.getValue().cardToken()).isEqualTo("CARD-TOK-1");
        assertThat(cmd.getValue().userLinkedBankId()).isNull();
        assertThat(cmd.getValue().paymentMethodId()).isEqualTo("XTR94508");
        assertThat(cmd.getValue().bankPaymentMethod()).isEqualTo("ACH");
        assertThat(cmd.getValue().otp()).isEqualTo("123456");

        // The persisted history row is card-typed and points to our card PK.
        ArgumentCaptor<PartnerWithdrawal> saved = ArgumentCaptor.forClass(PartnerWithdrawal.class);
        verify(withdrawalRepository).save(saved.capture());
        assertThat(saved.getValue().getDestinationType()).isEqualTo("CARD");
        assertThat(saved.getValue().getDestinationRef()).isEqualTo(CARD_ID);
        assertThat(saved.getValue().getStatus()).isEqualTo("COMPLETED");
        assertThat(saved.getValue().getClientId()).isEqualTo(CLIENT_ID);
        assertThat(saved.getValue().getUserId()).isEqualTo(USER_ID);
    }

    @Test
    void confirmWithdrawal_wrongOtp_throws422() {
        PartnerRedemption profile = PartnerRedemptionFixtures.enrolled(CLIENT_ID, USER_ID, "PAT-1").build();
        when(enrollmentService.getProfileView(USER_ID)).thenReturn(profile);
        when(linkedCardRepository.findByIdAndUserIdAndClientId(CARD_ID, USER_ID, CLIENT_ID))
                .thenReturn(Optional.of(card()));
        // XTRM replies "OTP sent" again → the submitted code was not accepted.
        when(xtrmApiClient.userWithdrawFund(any(UserWithdrawCommand.class)))
                .thenReturn(UserWithdrawResult.otpSent());

        ConfirmWithdrawalRequest request =
                new ConfirmWithdrawalRequest(new BigDecimal("100.00"), "CARD", CARD_ID, "000000");
        assertThatThrownBy(() -> service().confirmWithdrawal(USER_ID, request))
                .isInstanceOf(BusinessRuleException.class)
                .hasFieldOrPropertyWithValue("errorCode", "XTRM_WITHDRAW_OTP_INVALID");

        verify(withdrawalRepository, never()).save(any());
    }

    @Test
    void confirmWithdrawal_transientXtrm_throws503() {
        PartnerRedemption profile = PartnerRedemptionFixtures.enrolled(CLIENT_ID, USER_ID, "PAT-1").build();
        when(enrollmentService.getProfileView(USER_ID)).thenReturn(profile);
        when(linkedBankRepository.findByIdAndUserIdAndClientId(BANK_ID, USER_ID, CLIENT_ID))
                .thenReturn(Optional.of(bank()));
        when(xtrmApiClient.userWithdrawFund(any(UserWithdrawCommand.class)))
                .thenReturn(UserWithdrawResult.failed(List.of("Could not reach XTRM"), true));

        ConfirmWithdrawalRequest request =
                new ConfirmWithdrawalRequest(new BigDecimal("50.00"), "BANK", BANK_ID, "123456");
        assertThatThrownBy(() -> service().confirmWithdrawal(USER_ID, request))
                .isInstanceOf(ExternalServiceException.class)
                .hasFieldOrPropertyWithValue("errorCode", "XTRM_UNAVAILABLE");

        verify(withdrawalRepository, never()).save(any());
    }
}

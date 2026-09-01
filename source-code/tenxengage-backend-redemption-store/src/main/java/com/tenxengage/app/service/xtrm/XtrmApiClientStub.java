package com.tenxengage.app.service.xtrm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Canned XTRM client for localtest/test — mirrors {@code XoxodayApiClientStub}. Never active in prod
 * ({@link XtrmApiClientImpl} carries the inverse profile expression).
 *
 * <p>All responses are deterministic (derived from the command, no randomness) so tests can assert on the
 * generated reference ids. The stub never performs I/O.</p>
 */
@Component
// Stub used ONLY for localtest/test. `local` deliberately runs the REAL client (XtrmApiClientImpl) so demos
// exercise the live XTRM sandbox.
@Profile({"localtest", "test"})
public class XtrmApiClientStub implements XtrmApiClient {

    private static final Logger log = LoggerFactory.getLogger(XtrmApiClientStub.class);

    @Override
    public CreateUserResult createUser(CreateUserCommand cmd) {
        String pat = "PAT-STUB-" + token(cmd.email());
        log.info("[stub] XTRM CreateUser -> {}", pat);
        return CreateUserResult.ok(pat, "Standard");
    }

    @Override
    public UpdateUserResult updateUser(UpdateUserCommand cmd) {
        if (cmd.otp() == null) {
            log.info("[stub] XTRM UpdateUser initiate -> OTP sent");
            return UpdateUserResult.otpSent();
        }
        log.info("[stub] XTRM UpdateUser confirm -> applied");
        return UpdateUserResult.applied(cmd.recipientUserId(), "Basic");
    }

    @Override
    public TransferFundResult transferFund(TransferFundCommand cmd) {
        String txId = "STUB-TX-" + cmd.issuerTransactionId();
        String beneficiaryTxId = "STUB-BEN-" + cmd.issuerTransactionId();
        log.info("[stub] XTRM TransferFund issuerTxn={} -> {}", cmd.issuerTransactionId(), txId);
        return TransferFundResult.ok(txId, beneficiaryTxId);
    }

    @Override
    public BatchTransferResult batchTransfer(BatchTransferCommand cmd) {
        List<BatchItemResult> items = new ArrayList<>();
        for (BatchItem item : cmd.items()) {
            items.add(new BatchItemResult(item.customerTransactionId(), true, null));
        }
        log.info("[stub] XTRM BatchTransfer batchId={} accepted {} items", cmd.customerBatchId(), items.size());
        return BatchTransferResult.ok(items);
    }

    @Override
    public TransactionStatusResult getTransactionDetails(GetTransactionDetailsCommand cmd) {
        // Deterministic: report completed so localtest reconciliation can settle.
        log.info("[stub] XTRM GetUserWalletTransactionDetails txnId={} -> Completed", cmd.transactionId());
        return TransactionStatusResult.of("Completed");
    }

    @Override
    public BatchStatusResult getBatchStatus(GetBatchStatusCommand cmd) {
        // No persisted batch state in the stub — return an empty page (recon leaves items pending).
        log.info("[stub] XTRM GetBatchStatus batchId={} -> empty", cmd.customerBatchId());
        return BatchStatusResult.ok(List.of(), false, 0);
    }

    @Override
    public LinkBankResult linkBankBeneficiary(LinkBankCommand cmd) {
        String bankId = "STUB-BANK-" + token(cmd.recipientUserId() + cmd.accountNumber());
        log.info("[stub] XTRM LinkBankBeneficiary -> {}", bankId);
        return LinkBankResult.ok(bankId);
    }

    @Override
    public DeleteBankResult deleteBankBeneficiary(DeleteBankCommand cmd) {
        log.info("[stub] XTRM DeleteBankBeneficiary bank={}", cmd.beneficiaryBankId());
        return DeleteBankResult.ok();
    }

    @Override
    public GetWalletsResult getBeneficiaryWallets(GetWalletsCommand cmd) {
        log.info("[stub] XTRM GetBeneficiaryWallets for {}", cmd.recipientUserId());
        // XTRM auto-creates a USD wallet at enrollment; mirror that deterministically.
        return GetWalletsResult.ok(List.of(
                new WalletInfo("STUB-USD", "Wallet - USD", "USD", new java.math.BigDecimal("25.00"))));
    }

    @Override
    public LinkCardResult linkCard(LinkCardCommand cmd) {
        String cardToken = "STUB-CARD-" + token(cmd.recipientUserId() + cmd.cardNumber());
        // Never logs the raw card — only the derived token.
        log.info("[stub] XTRM LinkCard -> {}", cardToken);
        return LinkCardResult.ok(cardToken, "Active");
    }

    @Override
    public DeleteCardResult deleteCard(DeleteCardCommand cmd) {
        log.info("[stub] XTRM DeleteCard card={}", cmd.cardToken());
        return DeleteCardResult.ok();
    }

    @Override
    public UserWithdrawResult userWithdrawFund(UserWithdrawCommand cmd) {
        if (cmd.otp() == null) {
            // Initiate step: OTP dispatched, no transaction yet.
            log.info("[stub] XTRM UserWithdrawFund initiate -> OTP sent");
            return UserWithdrawResult.otpSent();
        }
        // Confirm step: gross = requested amount; a flat 2% fee is deducted (seller bears it).
        String txId = "STUB-WD-" + token(cmd.recipientUserId() + cmd.amount());
        java.math.BigDecimal gross = cmd.amount();
        java.math.BigDecimal fee = gross.multiply(new java.math.BigDecimal("0.02"))
                .setScale(2, java.math.RoundingMode.HALF_UP);
        java.math.BigDecimal net = gross.subtract(fee);
        log.info("[stub] XTRM UserWithdrawFund confirm -> {}", txId);
        return UserWithdrawResult.completed(txId, "Completed", net, fee, gross, cmd.currency());
    }

    @Override
    public GetDigitalGiftCardsResult getDigitalGiftCards(GetDigitalGiftCardsCommand cmd) {
        log.info("[stub] XTRM GetDigitalGiftCards currency={}", cmd.currency());
        return GetDigitalGiftCardsResult.ok(List.of(
                new GiftCardCatalogItem("U-STUB-FIX10", "Stub Gift Card $10", "StubBrand", null,
                        "USD", "FIXED_VALUE", "gift card", "Active",
                        new java.math.BigDecimal("10.00"), java.math.BigDecimal.ZERO, java.math.BigDecimal.ZERO),
                new GiftCardCatalogItem("U-STUB-VAR", "Stub Variable Card", "StubBrand", null,
                        "USD", "VARIABLE_VALUE", "gift card", "Active",
                        java.math.BigDecimal.ZERO, new java.math.BigDecimal("5.00"), new java.math.BigDecimal("500.00")),
                // Non-gift-card + non-USD rows the service must filter out:
                new GiftCardCatalogItem("U-STUB-DON", "Stub Donation", "StubCharity", null,
                        "USD", "VARIABLE_VALUE", "donation", "Active",
                        java.math.BigDecimal.ZERO, java.math.BigDecimal.ZERO, new java.math.BigDecimal("1000.00"))));
    }

    private static String token(String seed) {
        return Integer.toHexString(Math.abs(String.valueOf(seed).hashCode()));
    }
}

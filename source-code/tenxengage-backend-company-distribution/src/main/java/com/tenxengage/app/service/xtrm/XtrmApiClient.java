package com.tenxengage.app.service.xtrm;

import java.math.BigDecimal;
import java.util.List;

/**
 * Boundary to the XTRM XAPI v4 platform for the redemption payout enhancement (F-03).
 *
 * <p>Two beans implement this contract, selected by Spring profile:
 * <ul>
 *   <li>{@link XtrmApiClientImpl} — real HTTP client (active in every profile except local/localtest/test).</li>
 *   <li>{@link XtrmApiClientStub} — canned responses for local/localtest/test (never prod).</li>
 * </ul>
 *
 * <p><b>No-throw contract for vendor outcomes.</b> Each method returns a typed {@code *Result} that
 * carries {@code success} + sanitized {@code errors} + a {@code retryable} flag (true for transport /
 * timeout failures, false for a definitive XTRM domain rejection). Implementations do <b>not</b> throw
 * for XTRM or transport errors — callers translate a result into the appropriate domain outcome
 * (non-blocking enrollment failure, a 422 business-rule rejection, a held/retryable payout, etc.).</p>
 *
 * <p>All calls run <b>outside</b> any {@code @Transactional} scope; callers persist the outcome in a
 * short follow-up transaction. Implementations must never log the PAT, bank account/routing numbers,
 * or the raw XTRM response body.</p>
 */
public interface XtrmApiClient {

    /** Enroll a payee in XTRM ({@code CreateUser}); returns the recipient PAT + identity level. */
    CreateUserResult createUser(CreateUserCommand command);

    /**
     * Enroll a payee as a specific account.
     *
     * <p>XTRM binds the new user to whichever account creates them, and will not create a second user with
     * the same email — so the account chosen here is permanent for that person. A seller must be created by
     * their own partner company for that company to be able to pay them.</p>
     */
    CreateUserResult createUser(CreateUserCommand command, XtrmCredentials credentials);

    /**
     * Create a beneficiary <em>company</em> ({@code Beneficiary/CreateBeneficiary}).
     *
     * <p>Returns the company's SPN <b>and</b> the pseudo credentials it will later authenticate with. This
     * is a different thing from {@link #createUser}, which enrolls an individual payee and returns a PAT.</p>
     */
    CreateBeneficiaryResult createBeneficiary(CreateBeneficiaryCommand command);

    /** Single AnyPay/Bank payout ({@code TransferFund}) — INSTANT / APPROVAL modes. */
    TransferFundResult transferFund(TransferFundCommand command);

    /**
     * Transfer authenticated as a specific account rather than the platform.
     *
     * <p>Needed because XTRM will not let one account spend another's balance: a partner company paying its
     * own sellers must present that company's credentials, with that company's SPN and wallet. Calling the
     * single-argument overload for such a payout is what returns {@code 400 Invalid wallet id}.</p>
     */
    TransferFundResult transferFund(TransferFundCommand command, XtrmCredentials credentials);

    /** Batched payout ({@code BatchTransfer}) — BATCH mode; per-item accepted/rejected results. */
    BatchTransferResult batchTransfer(BatchTransferCommand command);

    /** Poll a single payout's status ({@code GetUserWalletTransactionDetails}) — INSTANT/APPROVAL reconciliation. */
    TransactionStatusResult getTransactionDetails(GetTransactionDetailsCommand command);

    /**
     * Poll a single payout as a specific remitter.
     *
     * <p>Required once a partner company can be the remitter: the transaction belongs to whichever account
     * paid, so polling as the platform for a company-remitted payout finds nothing — and an item that is
     * never found is never settled, leaving the recipient's share reserved indefinitely.</p>
     */
    TransactionStatusResult getTransactionDetails(GetTransactionDetailsCommand command, XtrmCredentials credentials);

    /** Poll a whole batch's item statuses ({@code GET /Fund/BatchTransfer/{id}}) — BATCH reconciliation. */
    BatchStatusResult getBatchStatus(GetBatchStatusCommand command);

    /**
     * Poll a batch as a specific remitter.
     *
     * <p>Required once a partner company can be the remitter: the batch belongs to whichever account sent
     * it, so polling as the platform for a company-sent batch finds nothing.</p>
     */
    BatchStatusResult getBatchStatus(GetBatchStatusCommand command, XtrmCredentials credentials);

    /** Link a bank/ACH beneficiary ({@code LinkBankBeneficiary}); returns the UserLinkedBankID reference. */
    LinkBankResult linkBankBeneficiary(LinkBankCommand command);

    /** Remove a bank/ACH beneficiary ({@code DeleteBankBeneficiary}) at XTRM (multi-bank enhancement). */
    DeleteBankResult deleteBankBeneficiary(DeleteBankCommand command);

    /** List a payee's XTRM digital wallets ({@code GetBeneficiaryWallets}) — VIEW-only (digital-wallet enhancement). */
    GetWalletsResult getBeneficiaryWallets(GetWalletsCommand command);

    /** Link a card ({@code LinkCard}) → returns the tokenized {@code CardToken}; raw card is pass-through, never stored. */
    LinkCardResult linkCard(LinkCardCommand command);

    /** Remove a linked card ({@code DeleteCard}) at XTRM. */
    DeleteCardResult deleteCard(DeleteCardCommand command);

    /** Withdraw wallet funds to a bank/card ({@code UserWithdrawFund}) — 2-step OTP. */
    UserWithdrawResult userWithdrawFund(UserWithdrawCommand command);

    /** Update a payee's XTRM profile ({@code UpdateUser}) — 2-step OTP; used to sync a changed mobile. */
    UpdateUserResult updateUser(UpdateUserCommand command);

    /** Fetch the XTRM digital gift-card catalog (brands + their SKU items). Read-only. */
    GetDigitalGiftCardsResult getDigitalGiftCards(GetDigitalGiftCardsCommand command);

    record GetDigitalGiftCardsCommand(String currency) {
    }

    /** One flattened brand-item from the XTRM gift-card catalog (fields as XTRM returns them). */
    record GiftCardCatalogItem(
            String sku,
            String rewardName,
            String brandName,
            String brandImageUrl,
            String currencyCode,
            String valueType,
            String rewardType,
            String status,
            BigDecimal faceValue,
            BigDecimal minValue,
            BigDecimal maxValue) {
    }

    record GetDigitalGiftCardsResult(boolean success, List<GiftCardCatalogItem> items,
                                     List<String> errors, boolean retryable) {
        public static GetDigitalGiftCardsResult ok(List<GiftCardCatalogItem> items) {
            return new GetDigitalGiftCardsResult(true, items == null ? List.of() : items, List.of(), false);
        }

        public static GetDigitalGiftCardsResult failed(List<String> errors, boolean retryable) {
            return new GetDigitalGiftCardsResult(false, List.of(), errors == null ? List.of() : errors, retryable);
        }
    }

    // ---------------------------------------------------------------------
    // CreateUser
    // ---------------------------------------------------------------------

    /**
     * Identity + address forwarded to XTRM {@code CreateUser}. {@code addressLine1} + {@code countryIso2}
     * required. {@code phone} is the national mobile number and {@code phoneCountryIso2} its country — combined
     * into XTRM's single {@code MobilePhone} (dial code + national). XTRM needs a mobile for withdrawals.
     */
    record CreateUserCommand(
            String firstName,
            String lastName,
            String email,
            String phone,
            String phoneCountryIso2,
            String addressLine1,
            String addressLine2,
            String city,
            String region,
            String postalCode,
            String countryIso2) {
    }

    // ---------------------------------------------------------------------
    // UpdateUser (2-step OTP profile update)
    // ---------------------------------------------------------------------

    /**
     * A profile update forwarded to XTRM {@code UpdateUser}. Mobile is split into {@code mobileCountryIso2}
     * (ISO2) + {@code mobileNumber} (national) — XTRM's UpdateUser shape. {@code otp} is null on the initiate
     * call (XTRM texts the code to {@code mobileNumber}), then set on the confirm call.
     */
    record UpdateUserCommand(
            String recipientUserId,
            String firstName,
            String lastName,
            String addressLine1,
            String addressLine2,
            String city,
            String region,
            String postalCode,
            String countryIso2,
            String mobileCountryIso2,
            String mobileNumber,
            String otp) {
    }

    /**
     * Result of {@code UpdateUser}. {@code otpRequired} = the initiate step (OTP sent; resubmit with OTP).
     * On completion the update is applied and the PAT + identity level are echoed back.
     */
    record UpdateUserResult(
            boolean success,
            boolean otpRequired,
            String recipientUserId,
            String identityLevel,
            List<String> errors,
            boolean retryable) {

        public static UpdateUserResult otpSent() {
            return new UpdateUserResult(true, true, null, null, List.of(), false);
        }

        public static UpdateUserResult applied(String recipientUserId, String identityLevel) {
            return new UpdateUserResult(true, false, recipientUserId, identityLevel, List.of(), false);
        }

        public static UpdateUserResult failed(List<String> errors, boolean retryable) {
            return new UpdateUserResult(false, false, null, null, errors == null ? List.of() : errors, retryable);
        }
    }

    // ---------------------------------------------------------------------
    // CreateBeneficiary (a beneficiary COMPANY, not a payee)
    // ---------------------------------------------------------------------

    /**
     * Company + admin details forwarded to XTRM {@code Beneficiary/CreateBeneficiary}.
     *
     * <p>{@code adminMobileNumber} is the national number; the implementation prefixes the dial code for
     * {@code adminCountryIso2} through {@code PhoneDialCodes}, the same way {@code CreateUser} does.</p>
     */
    record CreateBeneficiaryCommand(
            String companyName,
            String webAddress,
            String adminFirstName,
            String adminLastName,
            String adminEmail,
            String adminMobileNumber,
            String adminCountryIso2,
            String adminCity,
            String adminRegion,
            String adminPostalCode,
            boolean emailNotification
    ) {
    }

    /**
     * Result of {@code CreateBeneficiary}: the company's SPN, its pseudo credentials, and its KYC tier.
     *
     * <p><b>{@code clientSecret} is a secret, and XTRM returns it exactly once.</b> The call cannot be
     * replayed for the same company — the name is taken on a second attempt — so a lost secret is
     * recoverable only through XTRM support. Persist it before doing anything else, never log this record,
     * and do not remove the redacting {@code toString()} below.</p>
     */
    record CreateBeneficiaryResult(
            boolean success,
            String beneficiaryAccountNumber,
            String clientId,
            String clientSecret,
            String accountIdentityLevel,
            List<String> errors,
            boolean retryable
    ) {
        public static CreateBeneficiaryResult ok(String beneficiaryAccountNumber, String clientId,
                                                 String clientSecret, String accountIdentityLevel) {
            return new CreateBeneficiaryResult(true, beneficiaryAccountNumber, clientId, clientSecret,
                    accountIdentityLevel, List.of(), false);
        }

        public static CreateBeneficiaryResult failed(List<String> errors, boolean retryable) {
            return new CreateBeneficiaryResult(false, null, null, null, null,
                    errors == null ? List.of() : errors, retryable);
        }

        /**
         * Redacted on purpose. A record's generated {@code toString()} prints every component, which would
         * put a live client secret into any log line or exception that touched this object.
         */
        @Override
        public String toString() {
            return "CreateBeneficiaryResult[success=" + success
                    + ", beneficiaryAccountNumber=" + beneficiaryAccountNumber
                    + ", clientId=" + clientId
                    + ", accountIdentityLevel=" + accountIdentityLevel
                    + ", errors=" + errors
                    + ", retryable=" + retryable
                    + ", clientSecret=***]";
        }
    }

    /** Result of {@code CreateUser}: the recipient PAT + XTRM identity level, or a failure. */
    record CreateUserResult(
            boolean success,
            String recipientUserId,
            String identityLevel,
            List<String> errors,
            boolean retryable) {

        public static CreateUserResult ok(String recipientUserId, String identityLevel) {
            return new CreateUserResult(true, recipientUserId, identityLevel, List.of(), false);
        }

        public static CreateUserResult failed(List<String> errors, boolean retryable) {
            return new CreateUserResult(false, null, null, errors == null ? List.of() : errors, retryable);
        }
    }

    // ---------------------------------------------------------------------
    // TransferFund
    // ---------------------------------------------------------------------

    /**
     * A single payout. {@code paymentMethodId} selects the rail (Bank {@code XTR94500} or digital
     * gift card {@code XTR94505}); {@code partnerLinkedBankId} is required only for the Bank rail;
     * {@code sku} + {@code giftCardEmail} are required only for the digital-gift-card rail.
     */
    record TransferFundCommand(
            String issuerTransactionId,
            String recipientUserId,
            String paymentMethodId,
            String partnerLinkedBankId,
            String cardToken,
            String sku,
            String giftCardEmail,
            BigDecimal amount,
            String currency,
            String description) {
    }

    /**
     * Result of {@code TransferFund}. {@code transactionId} is the payment-side {@code PaymentTransactionId}
     * (stored as {@code vendorReferenceId}); {@code beneficiaryTransactionId} is the beneficiary-side id that
     * {@code GetUserWalletTransactionDetails} accepts for reconciliation (may be null on rails that don't return it).
     */
    record TransferFundResult(
            boolean success,
            String transactionId,
            String beneficiaryTransactionId,
            List<String> errors,
            boolean retryable) {

        public static TransferFundResult ok(String transactionId, String beneficiaryTransactionId) {
            return new TransferFundResult(true, transactionId, beneficiaryTransactionId, List.of(), false);
        }

        public static TransferFundResult failed(List<String> errors, boolean retryable) {
            return new TransferFundResult(false, null, null, errors == null ? List.of() : errors, retryable);
        }
    }

    // ---------------------------------------------------------------------
    // BatchTransfer
    // ---------------------------------------------------------------------

    /**
     * One line item in a real XTRM {@code BatchTransfer}. {@code customerTransactionId} is OUR id (echoed back
     * by the batch status API for matching). Exactly one destination id is set per rail: {@code bankBeneficiaryId}
     * (BANK), {@code walletId} (ANYPAY), or {@code cardToken} (CARD).
     */
    record BatchItem(
            String customerTransactionId,
            String recipientUserId,
            BigDecimal amount,
            String sendMethodId,
            String bankBeneficiaryId,
            String walletId,
            String cardToken,
            String description) {
    }

    /** A batch of payouts. {@code customerBatchId} is OUR id (used later by the batch status API). */
    record BatchTransferCommand(
            String customerBatchId,
            List<BatchItem> items) {
    }

    /** Per-item submission outcome — {@code customerTransactionId} maps the result back to its redemption. */
    record BatchItemResult(
            String customerTransactionId,
            boolean accepted,
            String error) {
    }

    /**
     * Result of {@code BatchTransfer}. {@code success} = the batch was accepted for processing; individual items
     * may still be rejected — inspect {@code items} so a partial failure never fails the whole batch (FR-12).
     */
    record BatchTransferResult(
            boolean success,
            List<BatchItemResult> items,
            List<String> errors,
            boolean retryable) {

        public static BatchTransferResult ok(List<BatchItemResult> items) {
            return new BatchTransferResult(true, items == null ? List.of() : items, List.of(), false);
        }

        public static BatchTransferResult failed(List<String> errors, boolean retryable) {
            return new BatchTransferResult(false, List.of(), errors == null ? List.of() : errors, retryable);
        }
    }

    // ---------------------------------------------------------------------
    // Status checks (reconciliation)
    // ---------------------------------------------------------------------

    /** {@code recipientUserId} = payee PAT; {@code transactionId} = our stored {@code vendorReferenceId}. */
    record GetTransactionDetailsCommand(String recipientUserId, String transactionId) {
    }

    /**
     * Result of {@code GetUserWalletTransactionDetails}. {@code found} = the transaction was located and its
     * {@code status} (the "Transaction Status" field) read. {@code found=false} = not found or call error
     * ({@code retryable} distinguishes a transient outage from a definitive not-found).
     */
    record TransactionStatusResult(boolean found, String status, boolean retryable) {
        public static TransactionStatusResult of(String status) {
            return new TransactionStatusResult(true, status, false);
        }
        public static TransactionStatusResult notFound() {
            return new TransactionStatusResult(false, null, false);
        }
        public static TransactionStatusResult error(boolean retryable) {
            return new TransactionStatusResult(false, null, retryable);
        }
    }

    /** One page request of a batch's item statuses. */
    record GetBatchStatusCommand(String customerBatchId, int recordsToSkip, int recordsToTake) {
    }

    /**
     * One item's status within a batch (from the whole-batch list API). {@code errorReason} carries the
     * latest attempt's error code (e.g. {@code SEND_LIMIT_EXCEEDED}) when the item failed — populated only
     * when the status call is made with {@code history=true}; null for success/processing items.
     */
    record BatchStatusItem(String customerTransactionId, String status, String errorReason) {
        /** For items with no failure detail (success/processing, or the stub). */
        public BatchStatusItem(String customerTransactionId, String status) {
            this(customerTransactionId, status, null);
        }
    }

    /** One page of a batch's item statuses. */
    record BatchStatusResult(
            boolean success,
            List<BatchStatusItem> items,
            boolean hasMore,
            int nextRecordsToSkip,
            boolean retryable) {

        public static BatchStatusResult ok(List<BatchStatusItem> items, boolean hasMore, int nextRecordsToSkip) {
            return new BatchStatusResult(true, items == null ? List.of() : items, hasMore, nextRecordsToSkip, false);
        }

        public static BatchStatusResult failed(boolean retryable) {
            return new BatchStatusResult(false, List.of(), false, 0, retryable);
        }
    }

    // ---------------------------------------------------------------------
    // LinkBankBeneficiary
    // ---------------------------------------------------------------------

    /** Bank + address forwarded to XTRM {@code LinkBankBeneficiary}. Raw numbers are pass-through, never stored. */
    record LinkBankCommand(
            String recipientUserId,
            String contactName,
            String contactPhone,
            String accountNumber,
            String routingNumber,
            String swiftBic,
            String institutionName,
            String addressLine1,
            String addressLine2,
            String city,
            String region,
            String postalCode,
            String countryIso2,
            String withdrawType) {
    }

    /**
     * Result of {@code LinkBankBeneficiary}: the XTRM {@code UserLinkedBankID} reference, or a failure.
     * {@code errorCode} carries a normalized code (e.g. {@code XTRM_BANK_DUPLICATE}) when XTRM signals a
     * recognizable domain rejection, so the caller can surface it inline; null otherwise.
     */
    record LinkBankResult(
            boolean success,
            String partnerLinkedBankId,
            String errorCode,
            List<String> errors,
            boolean retryable) {

        public static LinkBankResult ok(String partnerLinkedBankId) {
            return new LinkBankResult(true, partnerLinkedBankId, null, List.of(), false);
        }

        public static LinkBankResult failed(String errorCode, List<String> errors, boolean retryable) {
            return new LinkBankResult(false, null, errorCode, errors == null ? List.of() : errors, retryable);
        }
    }

    // ---------------------------------------------------------------------
    // DeleteBankBeneficiary
    // ---------------------------------------------------------------------

    /**
     * Remove a linked beneficiary at XTRM. {@code recipientUserId} is the payee PAT (sent as
     * {@code RecipientAccountNumber} — XTRM's delete-specific field name); {@code beneficiaryBankId} is the
     * XTRM {@code BeneficiaryId} returned by {@link #linkBankBeneficiary}.
     */
    record DeleteBankCommand(
            String recipientUserId,
            String beneficiaryBankId) {
    }

    /** Result of {@code DeleteBankBeneficiary}. {@code retryable} is true for transient/transport failures. */
    record DeleteBankResult(
            boolean success,
            List<String> errors,
            boolean retryable) {

        public static DeleteBankResult ok() {
            return new DeleteBankResult(true, List.of(), false);
        }

        public static DeleteBankResult failed(List<String> errors, boolean retryable) {
            return new DeleteBankResult(false, errors == null ? List.of() : errors, retryable);
        }
    }

    // ---------------------------------------------------------------------
    // GetBeneficiaryWallets (view-only)
    // ---------------------------------------------------------------------

    /** {@code recipientUserId} is the payee PAT (sent as {@code BeneficiaryAccountNumber} for this call). */
    record GetWalletsCommand(String recipientUserId) {
    }

    /** One XTRM digital wallet — the safe subset shown to the user (no EntityID / IsBankLinked / Type). */
    record WalletInfo(String id, String name, String currency, BigDecimal balance) {
    }

    /** Result of {@code GetBeneficiaryWallets}: the wallet list, or a failure. */
    record GetWalletsResult(
            boolean success,
            List<WalletInfo> wallets,
            List<String> errors,
            boolean retryable) {

        public static GetWalletsResult ok(List<WalletInfo> wallets) {
            return new GetWalletsResult(true, wallets == null ? List.of() : wallets, List.of(), false);
        }

        public static GetWalletsResult failed(List<String> errors, boolean retryable) {
            return new GetWalletsResult(false, List.of(), errors == null ? List.of() : errors, retryable);
        }
    }

    // ---------------------------------------------------------------------
    // LinkCard / DeleteCard (card instruments)
    // ---------------------------------------------------------------------

    /** Card + holder details forwarded to XTRM {@code LinkCard}. Raw card is pass-through — NEVER stored/logged. */
    record LinkCardCommand(
            String recipientUserId,
            String cardNumber,
            String expMonth,
            String expYear,
            String cvv,
            String cardType,
            String nameOnCard,
            String firstName,
            String lastName,
            String addressLine1,
            String addressLine2,
            String city,
            String region,
            String postalCode,
            String countryIso2) {
    }

    /** Result of {@code LinkCard}: the tokenized card reference + status, or a failure. */
    record LinkCardResult(
            boolean success,
            String cardToken,
            String cardStatus,
            List<String> errors,
            boolean retryable) {

        public static LinkCardResult ok(String cardToken, String cardStatus) {
            return new LinkCardResult(true, cardToken, cardStatus, List.of(), false);
        }

        public static LinkCardResult failed(List<String> errors, boolean retryable) {
            return new LinkCardResult(false, null, null, errors == null ? List.of() : errors, retryable);
        }
    }

    /** {@code recipientUserId} is the payee PAT; {@code cardToken} the token from {@code LinkCard}. */
    record DeleteCardCommand(String recipientUserId, String cardToken) {
    }

    record DeleteCardResult(boolean success, List<String> errors, boolean retryable) {
        public static DeleteCardResult ok() {
            return new DeleteCardResult(true, List.of(), false);
        }
        public static DeleteCardResult failed(List<String> errors, boolean retryable) {
            return new DeleteCardResult(false, errors == null ? List.of() : errors, retryable);
        }
    }

    // ---------------------------------------------------------------------
    // UserWithdrawFund (wallet cash-out, 2-step OTP)
    // ---------------------------------------------------------------------

    /**
     * A wallet withdrawal. Exactly one destination: {@code userLinkedBankId} (bank rail) OR {@code cardToken}
     * (card rail). {@code otp} is null on the initiate call, then set on the confirm call.
     */
    record UserWithdrawCommand(
            String recipientUserId,
            BigDecimal amount,
            String currency,
            String paymentMethodId,
            String userLinkedBankId,
            String cardToken,
            String bankPaymentMethod,
            String otp,
            String description) {
    }

    /**
     * Result of {@code UserWithdrawFund}. {@code otpRequired} = the initiate step (OTP sent; resubmit with OTP).
     * On completion the amounts are populated ({@code amountNet} delivered, {@code fee}, {@code totalGross}).
     */
    record UserWithdrawResult(
            boolean success,
            boolean otpRequired,
            String paymentTransactionId,
            String paymentStatus,
            BigDecimal amountNet,
            BigDecimal fee,
            BigDecimal totalGross,
            String currency,
            List<String> errors,
            boolean retryable) {

        public static UserWithdrawResult otpSent() {
            return new UserWithdrawResult(true, true, null, null, null, null, null, null, List.of(), false);
        }

        public static UserWithdrawResult completed(String txnId, String status, BigDecimal net, BigDecimal fee,
                                                   BigDecimal gross, String currency) {
            return new UserWithdrawResult(true, false, txnId, status, net, fee, gross, currency, List.of(), false);
        }

        public static UserWithdrawResult failed(List<String> errors, boolean retryable) {
            return new UserWithdrawResult(false, false, null, null, null, null, null, null,
                    errors == null ? List.of() : errors, retryable);
        }
    }
}

-- Beneficiary-side transaction id captured from the XTRM TransferFund response (single-dispatch payouts:
-- INSTANT / APPROVAL). Reconciliation polls GetUserWalletTransactionDetails by THIS id — the wallet status
-- API is keyed on the beneficiary transaction, NOT the payment-side PaymentTransactionId we store in
-- vendor_reference_id (that id is not found by the wallet API). Null for BATCH items, which reconcile by
-- customer_batch_id via the batch status API instead.
ALTER TABLE redemption_requests
    ADD COLUMN beneficiary_transaction_id VARCHAR(50);

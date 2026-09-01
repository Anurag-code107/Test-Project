-- Real XTRM BatchTransfer support (F-03 reconciliation). We supply CustomerBatchId (per batch run) +
-- CustomerTransactionId (per item); XTRM echoes them and the batch status API is keyed on them. Stored so the
-- reconciliation cron can rebuild GET /Fund/BatchTransfer/{customer_batch_id}. Nullable — set only for BATCH
-- items dispatched via the real batch API.
ALTER TABLE redemption_requests ADD COLUMN customer_batch_id VARCHAR(100);
ALTER TABLE redemption_requests ADD COLUMN customer_transaction_id VARCHAR(50);

-- Look up in-flight batch items by their batch (reconciliation groups by this).
CREATE INDEX IF NOT EXISTS idx_redemption_requests_customer_batch_id
    ON redemption_requests (customer_batch_id) WHERE customer_batch_id IS NOT NULL;

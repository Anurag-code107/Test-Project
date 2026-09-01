CREATE TABLE redemption_export_jobs (
    id              UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    client_id       UUID          NOT NULL REFERENCES clients(id),
    requested_by    UUID          NOT NULL REFERENCES users(id),
    status          VARCHAR(50)   NOT NULL DEFAULT 'PENDING',
    format          VARCHAR(10)   NOT NULL,
    scope           VARCHAR(20)   NOT NULL,
    filter_snapshot JSONB         NOT NULL DEFAULT '{}',
    row_count       INTEGER       NULL,
    file_key        VARCHAR(500)  NULL,
    expires_at      TIMESTAMPTZ   NULL,
    failure_reason  VARCHAR(500)  NULL,
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    deleted         BOOLEAN       NOT NULL DEFAULT false,
    version         BIGINT        NOT NULL DEFAULT 0
);

CREATE INDEX idx_redemption_export_jobs_client_id        ON redemption_export_jobs(client_id);
CREATE INDEX idx_redemption_export_jobs_client_requester ON redemption_export_jobs(client_id, requested_by);
CREATE INDEX idx_redemption_export_jobs_client_status    ON redemption_export_jobs(client_id, status);
CREATE INDEX idx_redemption_export_jobs_client_created   ON redemption_export_jobs(client_id, created_at DESC);

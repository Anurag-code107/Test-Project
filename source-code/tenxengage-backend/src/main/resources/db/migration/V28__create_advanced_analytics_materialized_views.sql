-- ============================================================
-- Advanced Redemption Analytics: Tracking table
-- ============================================================
CREATE TABLE analytics_mv_refresh_log (
    id                UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    mv_name           VARCHAR(100) NOT NULL,
    last_refreshed_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    duration_ms       BIGINT      NOT NULL DEFAULT 0,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_analytics_mv_refresh_log_mv_name UNIQUE (mv_name)
);

-- ============================================================
-- Helper view: resolve each user's region (top-level location).
-- A user's region = the name of their partner company's depth-0 location
-- value (LIMIT 1 when a company spans several). Mirrors the lateral join in
-- ClaimService. Users with no partner company / no location resolve to NULL.
-- ============================================================
CREATE VIEW v_user_region AS
SELECT
    u.id        AS user_id,
    u.client_id,
    region.name AS region
FROM users u
LEFT JOIN LATERAL (
    SELECT lv.name
    FROM   partner_company_locations pcl
    JOIN   location_values lv ON lv.id = pcl.location_value_id
    JOIN   location_levels ll ON ll.id = lv.level_id
    WHERE  pcl.partner_company_id = u.partner_company_id
      AND  ll.depth = 0
    LIMIT  1
) region ON true;

-- ============================================================
-- MV 1: Item-level redemption breakdown (FR-08.1, FR-08.7)
-- ============================================================
CREATE MATERIALIZED VIEW mv_item_redemption_breakdown AS
SELECT
    rr.client_id,
    rr.catalog_item_id,
    ci.name                                                                   AS catalog_item_name,
    rr.currency_id                                                            AS currency_type,
    ur.region,
    rr.processing_mode,
    DATE_TRUNC('day', rr.submitted_at AT TIME ZONE 'UTC')::DATE               AS period_date,
    COUNT(*)                                                                   AS total_redeemed_count,
    COALESCE(SUM(rr.amount), 0)                                               AS total_redeemed_amount,
    CASE
        WHEN COUNT(*) = 0 THEN 0
        ELSE ROUND(
            COUNT(*) FILTER (WHERE rr.status = 'COMPLETED') * 100.0
            / NULLIF(COUNT(*), 0),
            2
        )
    END                                                                        AS redemption_rate,
    COUNT(*) FILTER (WHERE rr.status = 'FAILED')                              AS failed_count,
    COUNT(*) FILTER (WHERE rr.status = 'CANCELLED')                           AS cancelled_count
FROM redemption_requests rr
JOIN redemption_catalog_items ci ON ci.id = rr.catalog_item_id AND ci.is_active = true
JOIN users u                     ON u.id  = rr.user_id          AND u.client_id = rr.client_id
LEFT JOIN v_user_region ur       ON ur.user_id = rr.user_id
WHERE rr.deleted = false
GROUP BY
    rr.client_id, rr.catalog_item_id, ci.name, rr.currency_id,
    ur.region, rr.processing_mode,
    DATE_TRUNC('day', rr.submitted_at AT TIME ZONE 'UTC')::DATE;

-- Unique index required for REFRESH MATERIALIZED VIEW CONCURRENTLY.
-- COALESCE(region,'') keeps the index total even when region is NULL.
CREATE UNIQUE INDEX uq_mv_item_redemption_breakdown
    ON mv_item_redemption_breakdown
    (client_id, catalog_item_id, currency_type, COALESCE(region, ''), processing_mode, period_date);

CREATE INDEX idx_mv_item_redemption_breakdown_client_date
    ON mv_item_redemption_breakdown (client_id, period_date);

-- ============================================================
-- MV 2: Segment breakdown by region x role x currency (FR-08.2)
-- (tier dropped -- no backing data; see header note)
-- ============================================================
CREATE MATERIALIZED VIEW mv_segment_redemption_breakdown AS
SELECT
    rr.client_id,
    ur.region,
    cr.base_role_name                                                         AS role,
    rr.currency_id                                                            AS currency_type,
    DATE_TRUNC('day', rr.submitted_at AT TIME ZONE 'UTC')::DATE               AS period_date,
    COUNT(*)                                                                   AS total_redeemed_count,
    COALESCE(SUM(rr.amount), 0)                                               AS total_redeemed_amount,
    CASE
        WHEN COUNT(*) = 0 THEN 0
        ELSE ROUND(
            COUNT(*) FILTER (WHERE rr.status = 'COMPLETED') * 100.0
            / NULLIF(COUNT(*), 0),
            2
        )
    END                                                                        AS redemption_rate
FROM redemption_requests rr
JOIN users u              ON u.id = rr.user_id AND u.client_id = rr.client_id
LEFT JOIN client_roles cr ON cr.id = u.client_role_id
LEFT JOIN v_user_region ur ON ur.user_id = rr.user_id
WHERE rr.deleted = false
GROUP BY
    rr.client_id, ur.region, cr.base_role_name, rr.currency_id,
    DATE_TRUNC('day', rr.submitted_at AT TIME ZONE 'UTC')::DATE;

CREATE UNIQUE INDEX uq_mv_segment_redemption_breakdown
    ON mv_segment_redemption_breakdown
    (client_id, COALESCE(region, ''), COALESCE(role, ''), currency_type, period_date);

CREATE INDEX idx_mv_segment_redemption_breakdown_client_date
    ON mv_segment_redemption_breakdown (client_id, period_date);

-- ============================================================
-- MV 3: Time-to-first-redemption by region (FR-08.3)
-- (regrouped from tier -> region -- see header note)
-- ============================================================
CREATE MATERIALIZED VIEW mv_time_to_first_redemption AS
SELECT
    u.client_id,
    ur.region,
    DATE_TRUNC('day', first_rr.first_submitted_at AT TIME ZONE 'UTC')::DATE   AS first_redemption_date,
    AVG(
        EXTRACT(EPOCH FROM (first_rr.first_submitted_at - u.created_at)) / 3600.0
    )                                                                          AS avg_hours_to_first_redemption,
    PERCENTILE_CONT(0.5) WITHIN GROUP (
        ORDER BY EXTRACT(EPOCH FROM (first_rr.first_submitted_at - u.created_at)) / 3600.0
    )                                                                          AS median_hours_to_first_redemption,
    SUM(
        EXTRACT(EPOCH FROM (first_rr.first_submitted_at - u.created_at)) / 3600.0
    )                                                                          AS sum_hours_to_first_redemption,
    COUNT(*)                                                                   AS sample_count
FROM users u
JOIN LATERAL (
    SELECT rr.user_id, MIN(rr.submitted_at) AS first_submitted_at
    FROM   redemption_requests rr
    WHERE  rr.user_id   = u.id
      AND  rr.client_id = u.client_id
      AND  rr.status    = 'COMPLETED'
      AND  rr.deleted   = false
    GROUP  BY rr.user_id
) first_rr ON true
LEFT JOIN v_user_region ur ON ur.user_id = u.id
GROUP BY
    u.client_id, ur.region,
    DATE_TRUNC('day', first_rr.first_submitted_at AT TIME ZONE 'UTC')::DATE;

CREATE UNIQUE INDEX uq_mv_time_to_first_redemption
    ON mv_time_to_first_redemption (client_id, COALESCE(region, ''), first_redemption_date);

CREATE INDEX idx_mv_time_to_first_redemption_client
    ON mv_time_to_first_redemption (client_id);

-- ============================================================
-- MV 4: Redemption rate daily trend (FR-08.4)
-- ============================================================
CREATE MATERIALIZED VIEW mv_redemption_rate_trend AS
SELECT
    rr.client_id,
    DATE_TRUNC('day', rr.submitted_at AT TIME ZONE 'UTC')::DATE               AS period_date,
    rr.currency_id                                                            AS currency_type,
    COUNT(*)                                                                   AS redeemed_count,
    CASE
        WHEN COUNT(*) = 0 THEN 0
        ELSE ROUND(
            COUNT(*) FILTER (WHERE rr.status = 'COMPLETED') * 100.0
            / NULLIF(COUNT(*), 0),
            2
        )
    END                                                                        AS redemption_rate
FROM redemption_requests rr
WHERE rr.deleted = false
GROUP BY
    rr.client_id,
    DATE_TRUNC('day', rr.submitted_at AT TIME ZONE 'UTC')::DATE,
    rr.currency_id;

CREATE UNIQUE INDEX uq_mv_redemption_rate_trend
    ON mv_redemption_rate_trend (client_id, period_date, currency_type);

CREATE INDEX idx_mv_redemption_rate_trend_client_date
    ON mv_redemption_rate_trend (client_id, period_date);

-- ============================================================
-- T5: Liability trend snapshot table (FR-08.5)
-- NOTE: This is a regular TABLE, not a MATERIALIZED VIEW.
-- PostgreSQL MVs are read-only; the scheduler appends snapshot rows
-- on each cycle via INSERT ON CONFLICT DO UPDATE, accumulating history.
-- ============================================================
CREATE TABLE mv_liability_trend (
    id                       UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    client_id                UUID          NOT NULL,
    period_date              DATE          NOT NULL,
    currency_type            VARCHAR(50)   NOT NULL,
    total_unredeemed_balance NUMERIC(19,4) NOT NULL DEFAULT 0,
    captured_at              TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_mv_liability_trend_key UNIQUE (client_id, period_date, currency_type)
);

CREATE INDEX idx_mv_liability_trend_client_date
    ON mv_liability_trend (client_id, period_date);

-- ============================================================
-- MV 6: Failure mode breakdown (FR-08.7)
-- ============================================================
CREATE MATERIALIZED VIEW mv_failure_mode_breakdown AS
SELECT
    rr.client_id,
    rr.processing_mode,
    rr.catalog_item_id,
    ci.name                                                                   AS catalog_item_name,
    rr.currency_id                                                            AS currency_type,
    ur.region,
    DATE_TRUNC('day', rr.submitted_at AT TIME ZONE 'UTC')::DATE               AS period_date,
    COUNT(*) FILTER (WHERE rr.status = 'FAILED')                              AS failed_count,
    COUNT(*) FILTER (WHERE rr.status = 'CANCELLED')                           AS cancelled_count,
    COUNT(*)                                                                   AS total_count,
    CASE
        WHEN COUNT(*) = 0 THEN 0
        ELSE ROUND(
            (COUNT(*) FILTER (WHERE rr.status IN ('FAILED','CANCELLED'))) * 100.0
            / NULLIF(COUNT(*), 0),
            2
        )
    END                                                                        AS failure_rate
FROM redemption_requests rr
JOIN redemption_catalog_items ci ON ci.id = rr.catalog_item_id AND ci.is_active = true
JOIN users u                     ON u.id  = rr.user_id          AND u.client_id = rr.client_id
LEFT JOIN v_user_region ur       ON ur.user_id = rr.user_id
WHERE rr.deleted = false
GROUP BY
    rr.client_id, rr.processing_mode, rr.catalog_item_id, ci.name,
    rr.currency_id, ur.region,
    DATE_TRUNC('day', rr.submitted_at AT TIME ZONE 'UTC')::DATE;

CREATE UNIQUE INDEX uq_mv_failure_mode_breakdown
    ON mv_failure_mode_breakdown
    (client_id, processing_mode, catalog_item_id, currency_type, COALESCE(region, ''), period_date);

CREATE INDEX idx_mv_failure_mode_breakdown_client_date
    ON mv_failure_mode_breakdown (client_id, period_date);

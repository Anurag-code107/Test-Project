-- ============================================================
-- Fix total_redeemed_count in mv_item_redemption_breakdown and
-- mv_segment_redemption_breakdown to count COMPLETED redemptions
-- only.  Previously COUNT(*) counted ALL statuses
-- (FAILED, CANCELLED, PENDING, etc.) which inflated the metric.
--
-- redemption_rate is kept exactly as V28 (numerator = COMPLETED,
-- denominator = COUNT(*) over all requests).
-- ============================================================

-- ── MV 1: Item-level redemption breakdown ────────────────────

DROP MATERIALIZED VIEW mv_item_redemption_breakdown;

CREATE MATERIALIZED VIEW mv_item_redemption_breakdown AS
SELECT
    rr.client_id,
    rr.catalog_item_id,
    ci.name                                                                   AS catalog_item_name,
    rr.currency_id                                                            AS currency_type,
    ur.region,
    rr.processing_mode,
    DATE_TRUNC('day', rr.submitted_at AT TIME ZONE 'UTC')::DATE               AS period_date,
    COUNT(*) FILTER (WHERE rr.status = 'COMPLETED')                           AS total_redeemed_count,
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

CREATE UNIQUE INDEX uq_mv_item_redemption_breakdown
    ON mv_item_redemption_breakdown
    (client_id, catalog_item_id, currency_type, COALESCE(region, ''), processing_mode, period_date);

CREATE INDEX idx_mv_item_redemption_breakdown_client_date
    ON mv_item_redemption_breakdown (client_id, period_date);

-- ── MV 2: Segment breakdown by region x role x currency ──────

DROP MATERIALIZED VIEW mv_segment_redemption_breakdown;

CREATE MATERIALIZED VIEW mv_segment_redemption_breakdown AS
SELECT
    rr.client_id,
    ur.region,
    cr.base_role_name                                                         AS role,
    rr.currency_id                                                            AS currency_type,
    DATE_TRUNC('day', rr.submitted_at AT TIME ZONE 'UTC')::DATE               AS period_date,
    COUNT(*) FILTER (WHERE rr.status = 'COMPLETED')                           AS total_redeemed_count,
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

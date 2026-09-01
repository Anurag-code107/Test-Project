-- Keep company distributions out of redemption analytics.
--
-- Distribution payout legs are rows in redemption_requests (origin = 'COMPANY_DISTRIBUTION'), so every
-- analytics matview that reads that table silently absorbed them the moment V51 shipped. The product
-- decision is that distributions do not appear in redemption analytics at all, so each view now filters
-- to origin = 'SELF' — the personal redemptions these metrics were designed to measure.
--
-- The most damaging one is mv_time_to_first_redemption: it measures how long a user takes to redeem
-- something themselves. A gift card their admin distributed to them is not an act of redemption, but it
-- is a COMPLETED redemption_requests row against their user_id, so it would be counted as their "first
-- redemption" and pull the average down for reasons no one could explain from the UI. The rate/failure
-- views are less dramatic but equally wrong: a distribution's success is the admin's outcome, not the
-- seller's redemption behaviour.
--
-- A matview's query cannot be altered in place, so each is dropped and recreated. The definitions below
-- are the live ones (pg_get_viewdef) with the origin predicate added and nothing else changed — Postgres'
-- normalised form, so they read differently from V28 while selecting the same rows.
--
-- Both indexes per view are recreated afterwards, byte-for-byte as they were.
--
-- A correction to what an earlier draft of this file claimed: the uq_mv_* indexes are NOT what makes the
-- refresh work. REFRESH MATERIALIZED VIEW CONCURRENTLY needs a UNIQUE index built from plain column names,
-- and four of these key on COALESCE(region,'') / COALESCE(role,'') — expression indexes, because region and
-- role are nullable. That disqualifies them, so AnalyticsMvRefreshScheduler deliberately issues a PLAIN
-- REFRESH (see its comment, which has said so all along) and needs no unique index at all.
--
-- They are recreated because they are part of the original schema and back the client_id/period_date lookups
-- the dashboard does — not to enable a concurrent refresh that never happens. V28's own comment above them
-- makes the same wrong claim; it is wrong there too.
--
-- mv_liability_trend is deliberately untouched — despite the mv_ prefix it is an ordinary table, not a
-- matview, and it does not read redemption_requests.

DROP MATERIALIZED VIEW IF EXISTS mv_failure_mode_breakdown;
DROP MATERIALIZED VIEW IF EXISTS mv_item_redemption_breakdown;
DROP MATERIALIZED VIEW IF EXISTS mv_redemption_rate_trend;
DROP MATERIALIZED VIEW IF EXISTS mv_segment_redemption_breakdown;
DROP MATERIALIZED VIEW IF EXISTS mv_time_to_first_redemption;

-- ─────────────────────────────────────────────────────────── mv_item_redemption_breakdown

CREATE MATERIALIZED VIEW mv_item_redemption_breakdown AS
SELECT rr.client_id,
       rr.catalog_item_id,
       ci.name AS catalog_item_name,
       rr.currency_id AS currency_type,
       ur.region,
       rr.processing_mode,
       date_trunc('day', (rr.submitted_at AT TIME ZONE 'UTC'))::date AS period_date,
       count(*) FILTER (WHERE rr.status::text = 'COMPLETED') AS total_redeemed_count,
       COALESCE(sum(rr.amount), 0::numeric) AS total_redeemed_amount,
       CASE
           WHEN count(*) = 0 THEN 0::numeric
           ELSE round(count(*) FILTER (WHERE rr.status::text = 'COMPLETED')::numeric * 100.0
                      / NULLIF(count(*), 0)::numeric, 2)
       END AS redemption_rate,
       count(*) FILTER (WHERE rr.status::text = 'FAILED') AS failed_count,
       count(*) FILTER (WHERE rr.status::text = 'CANCELLED') AS cancelled_count
FROM redemption_requests rr
    JOIN redemption_catalog_items ci ON ci.id = rr.catalog_item_id AND ci.is_active = true
    JOIN users u ON u.id = rr.user_id AND u.client_id = rr.client_id
    LEFT JOIN v_user_region ur ON ur.user_id = rr.user_id
WHERE rr.deleted = false
  AND rr.origin = 'SELF'
GROUP BY rr.client_id, rr.catalog_item_id, ci.name, rr.currency_id, ur.region, rr.processing_mode,
         (date_trunc('day', (rr.submitted_at AT TIME ZONE 'UTC'))::date);

CREATE UNIQUE INDEX uq_mv_item_redemption_breakdown ON mv_item_redemption_breakdown
    (client_id, catalog_item_id, currency_type, COALESCE(region, ''::character varying),
     processing_mode, period_date);
CREATE INDEX idx_mv_item_redemption_breakdown_client_date ON mv_item_redemption_breakdown
    (client_id, period_date);

-- ─────────────────────────────────────────────────────────── mv_segment_redemption_breakdown

CREATE MATERIALIZED VIEW mv_segment_redemption_breakdown AS
SELECT rr.client_id,
       ur.region,
       cr.base_role_name AS role,
       rr.currency_id AS currency_type,
       date_trunc('day', (rr.submitted_at AT TIME ZONE 'UTC'))::date AS period_date,
       count(*) FILTER (WHERE rr.status::text = 'COMPLETED') AS total_redeemed_count,
       COALESCE(sum(rr.amount), 0::numeric) AS total_redeemed_amount,
       CASE
           WHEN count(*) = 0 THEN 0::numeric
           ELSE round(count(*) FILTER (WHERE rr.status::text = 'COMPLETED')::numeric * 100.0
                      / NULLIF(count(*), 0)::numeric, 2)
       END AS redemption_rate
FROM redemption_requests rr
    JOIN users u ON u.id = rr.user_id AND u.client_id = rr.client_id
    LEFT JOIN client_roles cr ON cr.id = u.client_role_id
    LEFT JOIN v_user_region ur ON ur.user_id = rr.user_id
WHERE rr.deleted = false
  AND rr.origin = 'SELF'
GROUP BY rr.client_id, ur.region, cr.base_role_name, rr.currency_id,
         (date_trunc('day', (rr.submitted_at AT TIME ZONE 'UTC'))::date);

CREATE UNIQUE INDEX uq_mv_segment_redemption_breakdown ON mv_segment_redemption_breakdown
    (client_id, COALESCE(region, ''::character varying), COALESCE(role, ''::character varying),
     currency_type, period_date);
CREATE INDEX idx_mv_segment_redemption_breakdown_client_date ON mv_segment_redemption_breakdown
    (client_id, period_date);

-- ─────────────────────────────────────────────────────────── mv_redemption_rate_trend

CREATE MATERIALIZED VIEW mv_redemption_rate_trend AS
SELECT client_id,
       date_trunc('day', (submitted_at AT TIME ZONE 'UTC'))::date AS period_date,
       currency_id AS currency_type,
       count(*) AS redeemed_count,
       CASE
           WHEN count(*) = 0 THEN 0::numeric
           ELSE round(count(*) FILTER (WHERE status::text = 'COMPLETED')::numeric * 100.0
                      / NULLIF(count(*), 0)::numeric, 2)
       END AS redemption_rate
FROM redemption_requests rr
WHERE deleted = false
  AND origin = 'SELF'
GROUP BY client_id, (date_trunc('day', (submitted_at AT TIME ZONE 'UTC'))::date), currency_id;

CREATE UNIQUE INDEX uq_mv_redemption_rate_trend ON mv_redemption_rate_trend
    (client_id, period_date, currency_type);
CREATE INDEX idx_mv_redemption_rate_trend_client_date ON mv_redemption_rate_trend
    (client_id, period_date);

-- ─────────────────────────────────────────────────────────── mv_time_to_first_redemption

CREATE MATERIALIZED VIEW mv_time_to_first_redemption AS
SELECT u.client_id,
       ur.region,
       date_trunc('day', (first_rr.first_submitted_at AT TIME ZONE 'UTC'))::date AS first_redemption_date,
       avg(EXTRACT(epoch FROM first_rr.first_submitted_at - u.created_at) / 3600.0)
           AS avg_hours_to_first_redemption,
       percentile_cont(0.5::double precision) WITHIN GROUP (
           ORDER BY ((EXTRACT(epoch FROM first_rr.first_submitted_at - u.created_at) / 3600.0)::double precision))
           AS median_hours_to_first_redemption,
       sum(EXTRACT(epoch FROM first_rr.first_submitted_at - u.created_at) / 3600.0)
           AS sum_hours_to_first_redemption,
       count(*) AS sample_count
FROM users u
    JOIN LATERAL (
        SELECT rr.user_id,
               min(rr.submitted_at) AS first_submitted_at
        FROM redemption_requests rr
        WHERE rr.user_id = u.id
          AND rr.client_id = u.client_id
          AND rr.status::text = 'COMPLETED'
          AND rr.deleted = false
          AND rr.origin = 'SELF'
        GROUP BY rr.user_id) first_rr ON true
    LEFT JOIN v_user_region ur ON ur.user_id = u.id
GROUP BY u.client_id, ur.region,
         (date_trunc('day', (first_rr.first_submitted_at AT TIME ZONE 'UTC'))::date);

CREATE UNIQUE INDEX uq_mv_time_to_first_redemption ON mv_time_to_first_redemption
    (client_id, COALESCE(region, ''::character varying), first_redemption_date);
CREATE INDEX idx_mv_time_to_first_redemption_client ON mv_time_to_first_redemption (client_id);

-- ─────────────────────────────────────────────────────────── mv_failure_mode_breakdown

CREATE MATERIALIZED VIEW mv_failure_mode_breakdown AS
SELECT rr.client_id,
       rr.processing_mode,
       rr.catalog_item_id,
       ci.name AS catalog_item_name,
       rr.currency_id AS currency_type,
       ur.region,
       date_trunc('day', (rr.submitted_at AT TIME ZONE 'UTC'))::date AS period_date,
       count(*) FILTER (WHERE rr.status::text = 'FAILED') AS failed_count,
       count(*) FILTER (WHERE rr.status::text = 'CANCELLED') AS cancelled_count,
       count(*) AS total_count,
       CASE
           WHEN count(*) = 0 THEN 0::numeric
           ELSE round(count(*) FILTER (WHERE rr.status::text = ANY (ARRAY['FAILED', 'CANCELLED']))::numeric
                      * 100.0 / NULLIF(count(*), 0)::numeric, 2)
       END AS failure_rate
FROM redemption_requests rr
    JOIN redemption_catalog_items ci ON ci.id = rr.catalog_item_id AND ci.is_active = true
    JOIN users u ON u.id = rr.user_id AND u.client_id = rr.client_id
    LEFT JOIN v_user_region ur ON ur.user_id = rr.user_id
WHERE rr.deleted = false
  AND rr.origin = 'SELF'
GROUP BY rr.client_id, rr.processing_mode, rr.catalog_item_id, ci.name, rr.currency_id, ur.region,
         (date_trunc('day', (rr.submitted_at AT TIME ZONE 'UTC'))::date);

CREATE UNIQUE INDEX uq_mv_failure_mode_breakdown ON mv_failure_mode_breakdown
    (client_id, processing_mode, catalog_item_id, currency_type,
     COALESCE(region, ''::character varying), period_date);
CREATE INDEX idx_mv_failure_mode_breakdown_client_date ON mv_failure_mode_breakdown
    (client_id, period_date);

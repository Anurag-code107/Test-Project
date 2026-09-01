-- V1: Baseline Schema
-- Consolidated from 108 Flyway migrations (V1-V108).
-- Contains all DDL: 91 tables, indexes, constraints, views, extensions.
-- Pre-production consolidation — no real client data exists.

--
-- Name: uuid-ossp; Type: EXTENSION; Schema: -; Owner: -
--

CREATE EXTENSION IF NOT EXISTS "uuid-ossp" WITH SCHEMA public;


--
-- Name: EXTENSION "uuid-ossp"; Type: COMMENT; Schema: -; Owner: -
--


--
-- Name: activity_categories; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE activity_categories (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    client_id uuid NOT NULL,
    name character varying(100) NOT NULL,
    description character varying(500),
    sort_order integer DEFAULT 0 NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: activity_definitions; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE activity_definitions (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    incentive_id uuid NOT NULL,
    name character varying(255) NOT NULL,
    description character varying(2000),
    category_id character varying(100) NOT NULL,
    sort_order integer NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: activity_document_requirements; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE activity_document_requirements (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    activity_definition_id uuid NOT NULL,
    name character varying(255) NOT NULL,
    description character varying(1000),
    required boolean DEFAULT true NOT NULL,
    sort_order integer NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: approval_decisions; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE approval_decisions (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    incentive_id uuid NOT NULL,
    approver_email character varying(255) NOT NULL,
    decision character varying(20) NOT NULL,
    decided_at timestamp with time zone DEFAULT now() NOT NULL,
    token_id uuid NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    comment text
);


--
-- Name: audit_logs; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE audit_logs (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    client_id uuid NOT NULL,
    actor_type character varying(20) NOT NULL,
    actor_id uuid,
    actor_email character varying(255),
    actor_name character varying(200),
    user_type character varying(20),
    company_name character varying(255),
    action character varying(50) NOT NULL,
    resource_type character varying(50) NOT NULL,
    resource_id uuid,
    resource_name character varying(500),
    description character varying(2000),
    ip_address character varying(45),
    request_id uuid,
    metadata jsonb,
    created_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: breach_incidents; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE breach_incidents (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    description text NOT NULL,
    severity character varying(20) NOT NULL,
    data_affected text,
    detected_at timestamp with time zone NOT NULL,
    reported_to_authority_at timestamp with time zone,
    individuals_notified_at timestamp with time zone,
    status character varying(20) DEFAULT 'DETECTED'::character varying NOT NULL,
    resolution_notes text,
    created_by uuid,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: budget_utilizations; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE budget_utilizations (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    incentive_id uuid NOT NULL,
    currency_id character varying(50) NOT NULL,
    utilized numeric(15,2) DEFAULT 0 NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    location_value_id uuid
);


--
-- Name: builder_field_configs; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE builder_field_configs (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    section_config_id uuid NOT NULL,
    field_key character varying(100) NOT NULL,
    display_name character varying(255) NOT NULL,
    field_type character varying(30) NOT NULL,
    helper_text character varying(500),
    is_mandatory boolean DEFAULT false NOT NULL,
    is_system boolean DEFAULT false NOT NULL,
    is_eligibility boolean DEFAULT false NOT NULL,
    data_object_field_id uuid,
    value_source character varying(50),
    value_source_config jsonb,
    supports_excel_upload boolean DEFAULT false NOT NULL,
    sort_order integer DEFAULT 0 NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: builder_section_configs; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE builder_section_configs (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    client_id uuid NOT NULL,
    incentive_type character varying(20) NOT NULL,
    section_key character varying(50) NOT NULL,
    display_name character varying(255) NOT NULL,
    subtitle character varying(500),
    sort_order integer DEFAULT 0 NOT NULL,
    is_locked boolean DEFAULT false NOT NULL,
    is_visible boolean DEFAULT true NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: claim_actions; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE claim_actions (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    client_id uuid NOT NULL,
    purchase_order_id uuid NOT NULL,
    user_id uuid NOT NULL,
    claimed_at timestamp with time zone DEFAULT now() NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: client_branding; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE client_branding (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    client_id uuid NOT NULL,
    primary_hsl character varying(32) NOT NULL,
    primary_light_hsl character varying(32) NOT NULL,
    secondary_hsl character varying(32) NOT NULL,
    accent_hsl character varying(32) NOT NULL,
    success_hsl character varying(32) NOT NULL,
    warning_hsl character varying(32) NOT NULL,
    destructive_hsl character varying(32) NOT NULL,
    background_hsl character varying(32) NOT NULL,
    foreground_hsl character varying(32) NOT NULL,
    muted_hsl character varying(32) NOT NULL,
    muted_foreground_hsl character varying(32) NOT NULL,
    card_hsl character varying(32) NOT NULL,
    card_foreground_hsl character varying(32) NOT NULL,
    border_hsl character varying(32) NOT NULL,
    heading_font character varying(64) NOT NULL,
    body_font character varying(64) NOT NULL,
    logo_url character varying(512),
    logo_object_key character varying(512),
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: client_feature_overrides; Type: TABLE; Schema: public; Owner: -


CREATE TABLE client_feature_overrides (
    id uuid DEFAULT uuid_generate_v4() NOT NULL,
    client_id uuid NOT NULL,
    feature_flag_id uuid NOT NULL,
    enabled boolean NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: client_notification_role_configs; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE client_notification_role_configs (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    client_id uuid NOT NULL,
    notification_type_id uuid NOT NULL,
    role_name character varying(50) NOT NULL,
    enabled boolean NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: client_permission_grants; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE client_permission_grants (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    client_id uuid NOT NULL,
    permission_key character varying(100) NOT NULL,
    granted boolean DEFAULT true NOT NULL,
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    updated_at timestamp without time zone DEFAULT now() NOT NULL
);


--
-- Name: client_role_permissions; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE client_role_permissions (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    client_role_id uuid NOT NULL,
    permission_key character varying(100) NOT NULL,
    granted boolean DEFAULT true NOT NULL,
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    updated_at timestamp without time zone DEFAULT now() NOT NULL
);


--
-- Name: client_roles; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE client_roles (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    client_id uuid NOT NULL,
    name character varying(100) NOT NULL,
    description text,
    base_role_name character varying(50),
    is_system boolean DEFAULT false NOT NULL,
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    updated_at timestamp without time zone DEFAULT now() NOT NULL,
    role_type character varying(20) DEFAULT 'INTERNAL'::character varying,
    is_default boolean DEFAULT false NOT NULL,
    home_dashboard_template_id uuid
);


--
-- Name: home_dashboard_templates; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE home_dashboard_templates (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    client_id uuid NOT NULL,
    name character varying(100) NOT NULL,
    description text,
    role_type character varying(20) NOT NULL,
    layout jsonb DEFAULT '{"rows": []}'::jsonb NOT NULL,
    is_system boolean DEFAULT false NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: clients; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE clients (
    id uuid DEFAULT uuid_generate_v4() NOT NULL,
    name character varying(255) NOT NULL,
    subdomain character varying(63) NOT NULL,
    logo_url character varying(500),
    status character varying(20) DEFAULT 'ACTIVE'::character varying NOT NULL,
    subscription_tier character varying(20) DEFAULT 'STARTER'::character varying NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    notification_retention_days integer DEFAULT 90 NOT NULL,
    dpa_signed_at timestamp with time zone,
    dpa_version character varying(20),
    dpa_signed_by character varying(255),
    data_region character varying(10)
);


--
-- Name: company_permission_overrides; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE company_permission_overrides (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    client_id uuid NOT NULL,
    partner_company_id uuid NOT NULL,
    permission_key character varying(100) NOT NULL,
    granted boolean DEFAULT true NOT NULL,
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    updated_at timestamp without time zone DEFAULT now() NOT NULL
);


--
-- Name: compliance_alerts; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE compliance_alerts (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    client_id uuid,
    alert_type character varying(50) NOT NULL,
    severity character varying(20) NOT NULL,
    user_id uuid,
    partner_company_id uuid,
    incentive_id uuid,
    description text NOT NULL,
    status character varying(20) DEFAULT 'NEW'::character varying NOT NULL,
    resolved_at timestamp with time zone,
    resolved_by uuid,
    resolution_notes text,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: compliance_value_caps; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE compliance_value_caps (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    country_code character varying(10) NOT NULL,
    annual_cap_amount numeric(15,2) NOT NULL,
    annual_cap_currency character varying(10) DEFAULT 'USD'::character varying NOT NULL,
    enhanced_approval_threshold numeric(15,2) NOT NULL,
    client_id uuid,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: connector_field_mappings; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE connector_field_mappings (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    data_object_id uuid NOT NULL,
    connector_id uuid NOT NULL,
    field_id uuid NOT NULL,
    source_table character varying(255) NOT NULL,
    source_field character varying(255) NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: connectors; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE connectors (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    client_id uuid NOT NULL,
    connector_type character varying(30) NOT NULL,
    name character varying(255) NOT NULL,
    status character varying(20) DEFAULT 'DISCONNECTED'::character varying NOT NULL,
    config text NOT NULL,
    auth_type character varying(30),
    last_sync_at timestamp with time zone,
    last_sync_status character varying(50),
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: consent_records; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE consent_records (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    client_id uuid NOT NULL,
    user_id uuid NOT NULL,
    consent_type character varying(50) NOT NULL,
    granted boolean NOT NULL,
    recorded_at timestamp with time zone DEFAULT now() NOT NULL,
    ip_address character varying(45),
    consent_version character varying(20),
    CONSTRAINT valid_consent_type CHECK (((consent_type)::text = ANY ((ARRAY['AI_RECOMMENDATIONS'::character varying, 'MARKETING_EMAIL'::character varying, 'ANALYTICS'::character varying])::text[])))
);


--
-- Name: TABLE consent_records; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE consent_records IS 'Append-only consent audit trail for optional processing';


--
-- Name: course_product_mappings; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE course_product_mappings (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    course_id uuid NOT NULL,
    product_category character varying(100) NOT NULL,
    relevance_score numeric(3,2) DEFAULT 1.00 NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: currencies; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE currencies (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    client_id uuid NOT NULL,
    code character varying(50) NOT NULL,
    name character varying(100) NOT NULL,
    type character varying(20) NOT NULL,
    conversion_rate numeric(15,4),
    unit character varying(20) DEFAULT ''::character varying NOT NULL,
    is_currency_formatted boolean DEFAULT false NOT NULL,
    is_default boolean DEFAULT false NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT currencies_type_check CHECK (((type)::text = ANY ((ARRAY['MONETARY'::character varying, 'NON_MONETARY'::character varying])::text[])))
);


--
-- Name: data_object_fields; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE data_object_fields (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    data_object_id uuid NOT NULL,
    name character varying(255) NOT NULL,
    description character varying(1000),
    data_type character varying(20) NOT NULL,
    rule_label character varying(255),
    exclude_from_rules boolean DEFAULT false NOT NULL,
    sample_values text,
    sort_order integer DEFAULT 0 NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    mandatory boolean DEFAULT false NOT NULL,
    rule_widget character varying(30),
    visible_on_profile boolean DEFAULT false NOT NULL,
    editable_by_user boolean DEFAULT false NOT NULL
);


--
-- Name: data_objects; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE data_objects (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    client_id uuid NOT NULL,
    name character varying(255) NOT NULL,
    description character varying(1000),
    is_default boolean DEFAULT false NOT NULL,
    sort_order integer DEFAULT 0 NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: data_uploads; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE data_uploads (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    client_id uuid NOT NULL,
    data_object_id uuid NOT NULL,
    file_name character varying(255) NOT NULL,
    source character varying(20) DEFAULT 'MANUAL'::character varying NOT NULL,
    status character varying(20) DEFAULT 'PROCESSING'::character varying NOT NULL,
    total_rows integer DEFAULT 0 NOT NULL,
    new_rows integer DEFAULT 0 NOT NULL,
    updated_rows integer DEFAULT 0 NOT NULL,
    skipped_rows integer DEFAULT 0 NOT NULL,
    error_message text,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: eligibility_payouts; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE eligibility_payouts (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    eligibility_mapping_id uuid NOT NULL,
    requirement_id uuid NOT NULL,
    currency_id character varying(50) NOT NULL,
    payout_amount numeric(15,2) NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: eligibility_rule_groups; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE eligibility_rule_groups (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    requirement_id uuid NOT NULL,
    sort_order integer NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: eligibility_rules; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE eligibility_rules (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    rule_group_id uuid NOT NULL,
    rule_type character varying(40) NOT NULL,
    operator character varying(30),
    value character varying(500),
    value_max character varying(255),
    selected_products text,
    sort_order integer NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    field_id uuid
);


--
-- Name: feature_flags; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE feature_flags (
    id uuid DEFAULT uuid_generate_v4() NOT NULL,
    feature_key character varying(100) NOT NULL,
    description character varying(500),
    starter_enabled boolean DEFAULT false NOT NULL,
    professional_enabled boolean DEFAULT false NOT NULL,
    enterprise_enabled boolean DEFAULT true NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    category character varying(50) DEFAULT 'general'::character varying NOT NULL
);


--
-- Name: fiscal_year_configs; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE fiscal_year_configs (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    client_id uuid NOT NULL,
    label character varying(20) NOT NULL,
    start_date date NOT NULL,
    end_date date NOT NULL,
    quarter_method character varying(10) DEFAULT 'MONTHS'::character varying NOT NULL,
    quarter_size integer,
    q1_start_date date NOT NULL,
    q1_end_date date NOT NULL,
    q2_start_date date NOT NULL,
    q2_end_date date NOT NULL,
    q3_start_date date NOT NULL,
    q3_end_date date NOT NULL,
    q4_start_date date NOT NULL,
    q4_end_date date NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT chk_fiscal_year_dates CHECK ((end_date > start_date)),
    CONSTRAINT fiscal_year_configs_quarter_method_check CHECK (((quarter_method)::text = ANY ((ARRAY['MONTHS'::character varying, 'WEEKS'::character varying, 'DAYS'::character varying, 'CUSTOM'::character varying])::text[])))
);


--
-- Name: forecast_accuracy_records; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE forecast_accuracy_records (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    client_id uuid NOT NULL,
    incentive_id uuid NOT NULL,
    forecast_id uuid NOT NULL,
    predicted_roi numeric(10,2),
    actual_roi numeric(10,2),
    predicted_net_new_deals integer,
    actual_net_new_deals integer,
    predicted_net_new_bookings numeric(15,2),
    actual_net_new_bookings numeric(15,2),
    predicted_participation_rate numeric(5,2),
    actual_participation_rate numeric(5,2),
    predicted_budget_util_pct numeric(5,2),
    actual_budget_util_pct numeric(5,2),
    bookings_error_pct numeric(10,2),
    roi_error_pct numeric(10,2),
    participation_error_pct numeric(10,2),
    overall_accuracy_score numeric(5,2),
    model_version character varying(20),
    evaluated_at timestamp with time zone DEFAULT now() NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: TABLE forecast_accuracy_records; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE forecast_accuracy_records IS 'Tracks forecast accuracy by comparing predicted vs actual outcomes for completed incentives';


--
-- Name: COLUMN forecast_accuracy_records.overall_accuracy_score; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN forecast_accuracy_records.overall_accuracy_score IS '0-100 composite accuracy: weighted average of individual metric MAPEs';


--
-- Name: forecast_incentive_outcomes; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE forecast_incentive_outcomes (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    client_id uuid NOT NULL,
    incentive_id uuid NOT NULL,
    incentive_type character varying(20) NOT NULL,
    start_date date,
    end_date date,
    duration_days integer,
    total_budget numeric(15,2),
    actual_utilization_rate numeric(5,2),
    actual_participation_count integer,
    actual_participation_rate numeric(5,2),
    actual_revenue numeric(15,2),
    actual_cost numeric(15,2),
    actual_roi numeric(10,2),
    product_categories text,
    payout_type character varying(20),
    avg_payout_value numeric(15,2),
    partner_types text,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    name character varying(255),
    actual_lift_pct numeric(10,2),
    claim_rate numeric(5,2),
    avg_days_to_claim integer,
    budget_exhaustion_pct_at_midpoint numeric(5,2),
    target_location_value_ids jsonb
);


--
-- Name: COLUMN forecast_incentive_outcomes.name; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN forecast_incentive_outcomes.name IS 'Incentive display name for Claude context';


--
-- Name: COLUMN forecast_incentive_outcomes.actual_lift_pct; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN forecast_incentive_outcomes.actual_lift_pct IS 'Actual incremental lift % above baseline: (incremental_revenue / baseline_revenue) * 100';


--
-- Name: COLUMN forecast_incentive_outcomes.claim_rate; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN forecast_incentive_outcomes.claim_rate IS 'Claims / eligible POs as percentage';


--
-- Name: COLUMN forecast_incentive_outcomes.avg_days_to_claim; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN forecast_incentive_outcomes.avg_days_to_claim IS 'Average days from PO order_date to claim_actions.claimed_at';


--
-- Name: COLUMN forecast_incentive_outcomes.budget_exhaustion_pct_at_midpoint; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN forecast_incentive_outcomes.budget_exhaustion_pct_at_midpoint IS 'Percent of budget utilized at the midpoint of the incentive duration';


--
-- Name: forecast_region_distributions; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE forecast_region_distributions (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    client_id uuid NOT NULL,
    active_partner_count integer DEFAULT 0 NOT NULL,
    trailing_12m_revenue numeric(15,2) DEFAULT 0 NOT NULL,
    trailing_12m_order_count integer DEFAULT 0 NOT NULL,
    revenue_weight numeric(5,4) DEFAULT 0 NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    location_value_id uuid
);


--
-- Name: forecast_sales_aggregates; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE forecast_sales_aggregates (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    client_id uuid NOT NULL,
    product_category character varying(100),
    year_month date NOT NULL,
    deal_count integer DEFAULT 0 NOT NULL,
    total_revenue numeric(15,2) DEFAULT 0 NOT NULL,
    avg_deal_size numeric(15,2) DEFAULT 0 NOT NULL,
    unique_partners integer DEFAULT 0 NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    location_value_id uuid
);


--
-- Name: forecast_training_correlations; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE forecast_training_correlations (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    client_id uuid NOT NULL,
    product_category character varying(100) NOT NULL,
    trained_seller_count integer DEFAULT 0 NOT NULL,
    untrained_seller_count integer DEFAULT 0 NOT NULL,
    trained_avg_deal_size numeric(15,2) DEFAULT 0 NOT NULL,
    untrained_avg_deal_size numeric(15,2) DEFAULT 0 NOT NULL,
    trained_avg_deal_count integer DEFAULT 0 NOT NULL,
    untrained_avg_deal_count integer DEFAULT 0 NOT NULL,
    data_driven_lift_pct numeric(10,2),
    organic_training_lift_pct numeric(10,2),
    incentive_training_lift_pct numeric(10,2),
    sample_size integer DEFAULT 0 NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: TABLE forecast_training_correlations; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE forecast_training_correlations IS 'Pre-aggregated training impact data: compares trained vs untrained seller performance per product category';


--
-- Name: COLUMN forecast_training_correlations.data_driven_lift_pct; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN forecast_training_correlations.data_driven_lift_pct IS 'Measured lift: (trained_avg - untrained_avg) / untrained_avg * 100';


--
-- Name: COLUMN forecast_training_correlations.organic_training_lift_pct; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN forecast_training_correlations.organic_training_lift_pct IS 'Lift for sellers who completed training organically (not via incentive)';


--
-- Name: COLUMN forecast_training_correlations.incentive_training_lift_pct; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN forecast_training_correlations.incentive_training_lift_pct IS 'Lift for sellers who completed training as part of an incentive';


--
-- Name: government_segment_config; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE government_segment_config (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    client_id uuid NOT NULL,
    segment_value character varying(100) NOT NULL,
    is_government boolean DEFAULT true NOT NULL
);


--
-- Name: incentive_approvers; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE incentive_approvers (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    incentive_id uuid NOT NULL,
    email character varying(255) NOT NULL,
    category character varying(100) NOT NULL,
    sort_order integer DEFAULT 0 NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: incentive_audience_rules; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE incentive_audience_rules (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    incentive_id uuid NOT NULL,
    rule_type character varying(20) NOT NULL,
    rule_value character varying(255) NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    location_level_id uuid,
    CONSTRAINT chk_rule_type CHECK (((rule_type)::text = ANY ((ARRAY['REGION'::character varying, 'COUNTRY'::character varying, 'ROLE'::character varying, 'PARTNER_TYPE'::character varying, 'SPECIFIC_PARTNER'::character varying, 'LOCATION'::character varying])::text[])))
);


--
-- Name: incentive_budgets; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE incentive_budgets (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    incentive_id uuid NOT NULL,
    total_budget numeric(15,2) NOT NULL,
    currency_id character varying(50) NOT NULL,
    allocation_method character varying(20) NOT NULL,
    budget_mode character varying(20) DEFAULT 'GLOBAL'::character varying NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    budget_location_level_id uuid
);


--
-- Name: incentive_documents; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE incentive_documents (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    incentive_id uuid NOT NULL,
    name character varying(255) NOT NULL,
    document_type character varying(50) NOT NULL,
    file_type character varying(10) NOT NULL,
    size character varying(20) NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    storage_path character varying(500)
);


--
-- Name: incentive_forecasts; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE incentive_forecasts (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    incentive_id uuid NOT NULL,
    estimated_roi numeric(10,2) NOT NULL,
    estimated_participation integer NOT NULL,
    estimated_participation_rate numeric(5,2) NOT NULL,
    estimated_total_cost numeric(15,2) NOT NULL,
    estimated_revenue numeric(15,2) NOT NULL,
    confidence_score numeric(5,2) NOT NULL,
    monthly_projections jsonb,
    generated_at timestamp with time zone NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    estimated_net_new_deals integer,
    estimated_net_new_bookings numeric(15,2),
    location_breakdown jsonb,
    similar_incentive_ids jsonb,
    ai_insights text,
    top_level_insights jsonb,
    model_version character varying(20) DEFAULT 'v1'::character varying,
    data_quality_score numeric(5,2)
);


--
-- Name: incentives; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE incentives (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    name character varying(255) NOT NULL,
    description character varying(2000),
    incentive_type character varying(20) NOT NULL,
    status character varying(20) DEFAULT 'DRAFT'::character varying NOT NULL,
    client_id uuid NOT NULL,
    created_by uuid NOT NULL,
    start_date timestamp with time zone,
    end_date timestamp with time zone,
    timezone character varying(50),
    journey_sequential boolean DEFAULT true,
    deleted boolean DEFAULT false NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    reward_currencies character varying(500),
    reward_message character varying(500),
    reward_amounts text,
    fiscal_years character varying(500),
    fiscal_quarters character varying(500),
    training_required_count integer,
    countries_text text,
    specific_partners text,
    requires_approval boolean DEFAULT false,
    required_approvals integer DEFAULT 0,
    approval_round integer DEFAULT 1 NOT NULL,
    status_changed_at timestamp with time zone,
    max_per_partner numeric(15,2),
    max_per_user numeric(15,2),
    max_claimers_per_deal integer DEFAULT 1 NOT NULL,
    business_objective text,
    compliance_risk_level character varying(20),
    compliance_approved_at timestamp with time zone,
    compliance_approved_by uuid,
    max_per_partner_by_currency text,
    max_per_user_by_currency text,
    custom_field_values jsonb
);


--
-- Name: journey_stages; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE journey_stages (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    incentive_id uuid NOT NULL,
    linked_incentive_id uuid NOT NULL,
    sort_order integer NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: kyc_region_config; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE kyc_region_config (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    region_code character varying(20) NOT NULL,
    tier1_required boolean DEFAULT false NOT NULL,
    tier2_required boolean DEFAULT false NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: legal_policies; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE legal_policies (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    client_id uuid NOT NULL,
    policy_type character varying(50) NOT NULL,
    version character varying(20) NOT NULL,
    title character varying(255) NOT NULL,
    content_url character varying(500),
    summary text,
    effective_date timestamp with time zone DEFAULT now() NOT NULL,
    is_active boolean DEFAULT true NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: TABLE legal_policies; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE legal_policies IS 'Versioned legal policies (privacy notice, ToS, anti-bribery) per tenant';


--
-- Name: COLUMN legal_policies.policy_type; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN legal_policies.policy_type IS 'PRIVACY_NOTICE, TERMS_OF_SERVICE, ANTI_BRIBERY_POLICY';


--
-- Name: lms_courses; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE lms_courses (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    name character varying(255) NOT NULL,
    description text,
    category character varying(100) NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    metadata jsonb DEFAULT '{}'::jsonb,
    external_course_id character varying(100)
);


--
-- Name: location_budget_allocations; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE location_budget_allocations (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    budget_id uuid NOT NULL,
    location_value_id uuid NOT NULL,
    amount numeric(15,2) NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: location_levels; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE location_levels (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    client_id uuid NOT NULL,
    name character varying(100) NOT NULL,
    depth integer NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    use_in_builder boolean DEFAULT true NOT NULL,
    use_in_filters boolean DEFAULT true NOT NULL,
    is_required boolean DEFAULT true NOT NULL,
    CONSTRAINT location_levels_depth_check CHECK ((depth >= 0))
);


--
-- Name: location_values; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE location_values (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    client_id uuid NOT NULL,
    level_id uuid NOT NULL,
    parent_id uuid,
    name character varying(255) NOT NULL,
    code character varying(50),
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: notification_types; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE notification_types (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    key character varying(80) NOT NULL,
    category character varying(40) NOT NULL,
    title character varying(200) NOT NULL,
    description character varying(500),
    default_roles character varying(200) NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: notifications; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE notifications (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    client_id uuid NOT NULL,
    user_id uuid NOT NULL,
    notification_type_id uuid NOT NULL,
    title character varying(300) NOT NULL,
    message character varying(2000),
    resource_type character varying(50),
    resource_id uuid,
    is_read boolean DEFAULT false NOT NULL,
    read_at timestamp with time zone,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: onboarding_tokens; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE onboarding_tokens (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    client_id uuid NOT NULL,
    user_id uuid NOT NULL,
    token_hash character varying(255) NOT NULL,
    expires_at timestamp with time zone NOT NULL,
    completed_at timestamp with time zone,
    current_step integer DEFAULT 0 NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: TABLE onboarding_tokens; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE onboarding_tokens IS 'Secure tokens for user onboarding flow with step tracking';


--
-- Name: COLUMN onboarding_tokens.current_step; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN onboarding_tokens.current_step IS '0=not started, 1=password set, 2=profile done, 3=policies accepted, 4=consent set, 5=complete';


--
-- Name: partner_beneficial_owners; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE partner_beneficial_owners (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    kyc_record_id uuid NOT NULL,
    full_name character varying(255) NOT NULL,
    nationality character varying(10),
    ownership_percentage numeric(5,2),
    is_pep boolean DEFAULT false,
    created_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: partner_companies; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE partner_companies (
    id uuid DEFAULT uuid_generate_v4() NOT NULL,
    name character varying(255) NOT NULL,
    client_id uuid NOT NULL,
    status character varying(20) DEFAULT 'ACTIVE'::character varying NOT NULL,
    website character varying(500),
    contact_phone character varying(20),
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    external_partner_id character varying(100) NOT NULL,
    metadata jsonb DEFAULT '{}'::jsonb,
    anti_bribery_acknowledged_at timestamp with time zone,
    anti_bribery_policy_version character varying(20),
    government_deal_restriction_mode character varying(20) DEFAULT 'NONE'::character varying,
    CONSTRAINT chk_partner_status CHECK (((status)::text = ANY ((ARRAY['ACTIVE'::character varying, 'INACTIVE'::character varying])::text[])))
);


--
-- Name: partner_company_locations; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE partner_company_locations (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    client_id uuid NOT NULL,
    partner_company_id uuid NOT NULL,
    location_value_id uuid NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: partner_kyc_records; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE partner_kyc_records (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    client_id uuid NOT NULL,
    partner_company_id uuid NOT NULL,
    legal_entity_name character varying(255),
    registration_number character varying(100),
    incorporation_country character varying(10),
    tax_id character varying(100),
    kyc_status character varying(20) DEFAULT 'NOT_STARTED'::character varying NOT NULL,
    approved_by uuid,
    approved_at timestamp with time zone,
    expires_at timestamp with time zone,
    rejection_reason text,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: partner_program_acknowledgments; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE partner_program_acknowledgments (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    client_id uuid NOT NULL,
    partner_company_id uuid NOT NULL,
    incentive_id uuid NOT NULL,
    acknowledged_by uuid NOT NULL,
    acknowledged_at timestamp with time zone DEFAULT now() NOT NULL,
    policy_version character varying(20)
);


--
-- Name: payout_bands; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE payout_bands (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    payout_config_id uuid NOT NULL,
    min_amount numeric(15,2) NOT NULL,
    max_amount numeric(15,2),
    payout_value numeric(15,2) NOT NULL,
    sort_order integer NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: payout_configs; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE payout_configs (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    requirement_id uuid NOT NULL,
    currency_id character varying(50) NOT NULL,
    payout_type character varying(20) NOT NULL,
    against character varying(30),
    max_per_deal numeric(15,2),
    sort_order integer NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: permissions; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE permissions (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    permission_key character varying(100) NOT NULL,
    display_name character varying(150) NOT NULL,
    description text,
    category character varying(50) NOT NULL,
    permission_type character varying(20) NOT NULL,
    sort_order integer DEFAULT 0,
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    updated_at timestamp without time zone DEFAULT now() NOT NULL,
    scope character varying(20) DEFAULT 'ALL'::character varying NOT NULL
);


--
-- Name: po_eligibility_mappings; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE po_eligibility_mappings (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    client_id uuid NOT NULL,
    tagging_job_id uuid NOT NULL,
    purchase_order_id uuid NOT NULL,
    incentive_id uuid NOT NULL,
    eligible boolean NOT NULL,
    ineligibility_reason text,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: products; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE products (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    sku character varying(20) NOT NULL,
    name character varying(255) NOT NULL,
    category character varying(100) NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    client_id uuid NOT NULL
);


--
-- Name: purchase_order_lines; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE purchase_order_lines (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    purchase_order_id uuid NOT NULL,
    product_id uuid NOT NULL,
    quantity integer NOT NULL,
    unit_price numeric(12,2) NOT NULL,
    line_total numeric(14,2) NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    transaction_id character varying(30),
    metadata jsonb DEFAULT '{}'::jsonb
);


--
-- Name: purchase_orders; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE purchase_orders (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    client_id uuid NOT NULL,
    partner_company_id uuid NOT NULL,
    order_number character varying(30) NOT NULL,
    order_date date NOT NULL,
    status character varying(20) DEFAULT 'COMPLETED'::character varying NOT NULL,
    total_amount numeric(14,2) DEFAULT 0 NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    needs_retagging boolean DEFAULT true NOT NULL,
    metadata jsonb DEFAULT '{}'::jsonb
);


--
-- Name: recommendation_configs; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE recommendation_configs (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    client_id uuid NOT NULL,
    training_enabled boolean DEFAULT true NOT NULL,
    incentive_enabled boolean DEFAULT true NOT NULL,
    max_training_recommendations integer DEFAULT 5 NOT NULL,
    max_incentive_recommendations integer DEFAULT 5 NOT NULL,
    reward_currency_id character varying(50),
    training_completion_reward numeric(15,2) DEFAULT 0 NOT NULL,
    incentive_completion_reward numeric(15,2) DEFAULT 0 NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: recommendation_interactions; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE recommendation_interactions (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    client_id uuid NOT NULL,
    user_id uuid NOT NULL,
    recommendation_type character varying(20) NOT NULL,
    target_id uuid NOT NULL,
    interaction_type character varying(20) NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: COLUMN recommendation_interactions.interaction_type; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN recommendation_interactions.interaction_type IS 'VIEWED, DISMISSED, or COMPLETED';


--
-- Name: recommendation_scores; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE recommendation_scores (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    client_id uuid NOT NULL,
    user_id uuid NOT NULL,
    recommendation_type character varying(20) NOT NULL,
    target_id uuid NOT NULL,
    score numeric(8,4) DEFAULT 0 NOT NULL,
    score_breakdown jsonb DEFAULT '{}'::jsonb NOT NULL,
    rank integer DEFAULT 0 NOT NULL,
    reason_code character varying(50),
    computed_at timestamp with time zone DEFAULT now() NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: COLUMN recommendation_scores.recommendation_type; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN recommendation_scores.recommendation_type IS 'TRAINING or INCENTIVE';


--
-- Name: COLUMN recommendation_scores.target_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN recommendation_scores.target_id IS 'References lms_courses.id for TRAINING or incentives.id for INCENTIVE';


--
-- Name: COLUMN recommendation_scores.score_breakdown; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN recommendation_scores.score_breakdown IS 'JSON with individual signal scores: {"salesAlignment":0.35,"trainingLift":0.25,...}';


--
-- Name: COLUMN recommendation_scores.reason_code; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN recommendation_scores.reason_code IS 'Primary reason: SALES_ALIGNMENT, TRAINING_LIFT, INCENTIVE_REQUIRED, SKILL_GAP, BUDGET_ATTRACTIVE, SALES_PATTERN_MATCH';


--
-- Name: regional_compliance_config; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE regional_compliance_config (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    region_code character varying(20) NOT NULL,
    region_name character varying(100) NOT NULL,
    privacy_notice_required boolean DEFAULT true NOT NULL,
    terms_of_service_required boolean DEFAULT true NOT NULL,
    anti_bribery_required boolean DEFAULT true NOT NULL,
    consent_ai_visible boolean DEFAULT false NOT NULL,
    consent_marketing_visible boolean DEFAULT false NOT NULL,
    consent_analytics_visible boolean DEFAULT false NOT NULL,
    cookie_notice_visible boolean DEFAULT false NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: TABLE regional_compliance_config; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE regional_compliance_config IS 'TENX_ADMIN managed config for which onboarding steps appear per region';


--
-- Name: retention_policies; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE retention_policies (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    client_id uuid,
    data_category character varying(50) NOT NULL,
    retention_days integer NOT NULL,
    action_type character varying(20) DEFAULT 'ANONYMIZE'::character varying NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: retention_policy_bounds; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE retention_policy_bounds (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    data_category character varying(50) NOT NULL,
    min_days integer NOT NULL,
    max_days integer NOT NULL
);


--
-- Name: reward_balances; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE reward_balances (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    client_id uuid NOT NULL,
    user_id uuid NOT NULL,
    currency_id character varying(50) NOT NULL,
    balance numeric(15,2) DEFAULT 0 NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: reward_transactions; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE reward_transactions (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    client_id uuid NOT NULL,
    user_id uuid NOT NULL,
    incentive_id uuid,
    currency_id character varying(50) NOT NULL,
    amount_potential numeric(15,2) NOT NULL,
    amount_awarded numeric(15,2) NOT NULL,
    budget_capped boolean DEFAULT false NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    claim_action_id uuid,
    completion_id uuid
);


--
-- Name: sales_requirements; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE sales_requirements (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    incentive_id uuid NOT NULL,
    name character varying(255) NOT NULL,
    sort_order integer NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: sub_processors; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE sub_processors (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    name character varying(255) NOT NULL,
    purpose character varying(500) NOT NULL,
    data_processed character varying(500) NOT NULL,
    location character varying(100) NOT NULL,
    dpa_status character varying(20) DEFAULT 'PENDING'::character varying NOT NULL,
    scc_status character varying(20) DEFAULT 'NOT_REQUIRED'::character varying,
    added_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: sync_schedules; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE sync_schedules (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    client_id uuid NOT NULL,
    data_object_id uuid NOT NULL,
    enabled boolean DEFAULT false NOT NULL,
    cadence character varying(20) DEFAULT 'MANUAL'::character varying NOT NULL,
    last_run_at timestamp with time zone,
    next_run_at timestamp with time zone,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: tagging_jobs; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE tagging_jobs (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    client_id uuid NOT NULL,
    status character varying(20) DEFAULT 'RUNNING'::character varying NOT NULL,
    pos_analyzed integer DEFAULT 0 NOT NULL,
    eligible_deals integer DEFAULT 0 NOT NULL,
    incentives_matched integer DEFAULT 0 NOT NULL,
    error_message text,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    products_discovered integer DEFAULT 0 NOT NULL
);


--
-- Name: training_course_assignments; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE training_course_assignments (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    incentive_id uuid NOT NULL,
    course_id character varying(255) NOT NULL,
    course_name character varying(255) NOT NULL,
    course_category character varying(255),
    course_provider character varying(255),
    course_duration character varying(50),
    course_level character varying(20),
    required boolean DEFAULT true NOT NULL,
    sort_order integer NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: user_activity_document_submissions; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE user_activity_document_submissions (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    client_id uuid NOT NULL,
    user_id uuid NOT NULL,
    document_requirement_id uuid NOT NULL,
    activity_definition_id uuid NOT NULL,
    file_name character varying(500) NOT NULL,
    file_path character varying(1000) NOT NULL,
    file_size bigint,
    status character varying(20) DEFAULT 'PENDING'::character varying NOT NULL,
    reviewed_by uuid,
    reviewed_at timestamp with time zone,
    rejection_reason character varying(2000),
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: user_activity_progress; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE user_activity_progress (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    client_id uuid NOT NULL,
    user_id uuid NOT NULL,
    activity_definition_id uuid NOT NULL,
    incentive_id uuid NOT NULL,
    completed boolean DEFAULT false NOT NULL,
    completed_at timestamp with time zone,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: users; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE users (
    id uuid DEFAULT uuid_generate_v4() NOT NULL,
    email character varying(255) NOT NULL,
    first_name character varying(100) NOT NULL,
    last_name character varying(100) NOT NULL,
    phone character varying(20),
    avatar character varying(500),
    password_hash character varying(255) NOT NULL,
    status character varying(20) DEFAULT 'ACTIVE'::character varying NOT NULL,
    organization_id uuid,
    client_id uuid,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    partner_company_id uuid,
    client_role_id uuid,
    metadata jsonb DEFAULT '{}'::jsonb,
    onboarding_completed_at timestamp with time zone,
    country_code character varying(10),
    external_user_id character varying(100)
);


--
-- Name: user_annual_reward_summary; Type: VIEW; Schema: public; Owner: -
--

CREATE VIEW user_annual_reward_summary AS
 SELECT rt.client_id,
    rt.user_id,
    u.first_name,
    u.last_name,
    u.email,
    u.country_code,
    pc.name AS partner_company_name,
    rt.currency_id,
    EXTRACT(year FROM rt.created_at) AS reward_year,
    sum(rt.amount_awarded) AS total_awarded,
    count(*) AS transaction_count
   FROM ((reward_transactions rt
     JOIN users u ON ((u.id = rt.user_id)))
     LEFT JOIN partner_companies pc ON ((pc.id = u.partner_company_id)))
  GROUP BY rt.client_id, rt.user_id, u.first_name, u.last_name, u.email, u.country_code, pc.name, rt.currency_id, (EXTRACT(year FROM rt.created_at));


--
-- Name: user_course_completions; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE user_course_completions (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    client_id uuid NOT NULL,
    user_id uuid NOT NULL,
    course_id uuid NOT NULL,
    completed_at timestamp with time zone NOT NULL,
    source character varying(20) DEFAULT 'ORGANIC'::character varying NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    metadata jsonb DEFAULT '{}'::jsonb
);


--
-- Name: user_incentive_completions; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE user_incentive_completions (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    client_id uuid NOT NULL,
    incentive_id uuid NOT NULL,
    user_id uuid NOT NULL,
    completed_at timestamp with time zone DEFAULT now() NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: user_journey_stage_progress; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE user_journey_stage_progress (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    client_id uuid NOT NULL,
    user_id uuid NOT NULL,
    journey_id uuid NOT NULL,
    stage_id uuid NOT NULL,
    linked_incentive_id uuid NOT NULL,
    completed boolean DEFAULT false NOT NULL,
    completed_at timestamp with time zone,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: user_legal_acceptances; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE user_legal_acceptances (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    client_id uuid NOT NULL,
    user_id uuid NOT NULL,
    policy_id uuid NOT NULL,
    accepted_at timestamp with time zone DEFAULT now() NOT NULL,
    ip_address character varying(45),
    user_agent character varying(500)
);


--
-- Name: TABLE user_legal_acceptances; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE user_legal_acceptances IS 'Immutable record of user accepting legal policies';


--
-- Name: user_notification_preferences; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE user_notification_preferences (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    client_id uuid NOT NULL,
    user_id uuid NOT NULL,
    notification_type_id uuid NOT NULL,
    opted_out boolean DEFAULT true NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: user_notification_settings; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE user_notification_settings (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    client_id uuid NOT NULL,
    user_id uuid NOT NULL,
    notifications_enabled boolean DEFAULT true NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: user_permission_overrides; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE user_permission_overrides (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    client_id uuid NOT NULL,
    user_id uuid NOT NULL,
    permission_key character varying(100) NOT NULL,
    granted boolean DEFAULT true NOT NULL,
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    updated_at timestamp without time zone DEFAULT now() NOT NULL
);


--
-- Name: whistleblower_case_updates; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE whistleblower_case_updates (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    report_id uuid NOT NULL,
    update_text text NOT NULL,
    updated_by uuid,
    created_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: whistleblower_reports; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE whistleblower_reports (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    client_id uuid,
    report_type character varying(50) NOT NULL,
    description text NOT NULL,
    evidence_url character varying(500),
    reporter_email character varying(255),
    reporter_name character varying(255),
    is_anonymous boolean DEFAULT true NOT NULL,
    tracking_number character varying(20) NOT NULL,
    status character varying(20) DEFAULT 'NEW'::character varying NOT NULL,
    acknowledged_at timestamp with time zone,
    resolution_deadline timestamp with time zone,
    resolved_at timestamp with time zone,
    resolved_by uuid,
    resolution_notes text,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: activity_categories activity_categories_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY activity_categories
    ADD CONSTRAINT activity_categories_pkey PRIMARY KEY (id);


--
-- Name: activity_definitions activity_definitions_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY activity_definitions
    ADD CONSTRAINT activity_definitions_pkey PRIMARY KEY (id);


--
-- Name: activity_document_requirements activity_document_requirements_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY activity_document_requirements
    ADD CONSTRAINT activity_document_requirements_pkey PRIMARY KEY (id);


--
-- Name: approval_decisions approval_decisions_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY approval_decisions
    ADD CONSTRAINT approval_decisions_pkey PRIMARY KEY (id);


--
-- Name: audit_logs audit_logs_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY audit_logs
    ADD CONSTRAINT audit_logs_pkey PRIMARY KEY (id);


--
-- Name: breach_incidents breach_incidents_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY breach_incidents
    ADD CONSTRAINT breach_incidents_pkey PRIMARY KEY (id);


--
-- Name: budget_utilizations budget_utilizations_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY budget_utilizations
    ADD CONSTRAINT budget_utilizations_pkey PRIMARY KEY (id);


--
-- Name: builder_field_configs builder_field_configs_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY builder_field_configs
    ADD CONSTRAINT builder_field_configs_pkey PRIMARY KEY (id);


--
-- Name: builder_section_configs builder_section_configs_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY builder_section_configs
    ADD CONSTRAINT builder_section_configs_pkey PRIMARY KEY (id);


--
-- Name: claim_actions claim_actions_client_id_purchase_order_id_user_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY claim_actions
    ADD CONSTRAINT claim_actions_client_id_purchase_order_id_user_id_key UNIQUE (client_id, purchase_order_id, user_id);


--
-- Name: claim_actions claim_actions_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY claim_actions
    ADD CONSTRAINT claim_actions_pkey PRIMARY KEY (id);


--
-- Name: client_branding client_branding_client_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY client_branding
    ADD CONSTRAINT client_branding_client_id_key UNIQUE (client_id);


--
-- Name: client_branding client_branding_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY client_branding
    ADD CONSTRAINT client_branding_pkey PRIMARY KEY (id);


--
-- Name: client_feature_overrides client_feature_overrides_client_id_feature_flag_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY client_feature_overrides
    ADD CONSTRAINT client_feature_overrides_client_id_feature_flag_id_key UNIQUE (client_id, feature_flag_id);


--
-- Name: client_feature_overrides client_feature_overrides_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY client_feature_overrides
    ADD CONSTRAINT client_feature_overrides_pkey PRIMARY KEY (id);


--
-- Name: client_notification_role_configs client_notification_role_conf_client_id_notification_type_i_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY client_notification_role_configs
    ADD CONSTRAINT client_notification_role_conf_client_id_notification_type_i_key UNIQUE (client_id, notification_type_id, role_name);


--
-- Name: client_notification_role_configs client_notification_role_configs_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY client_notification_role_configs
    ADD CONSTRAINT client_notification_role_configs_pkey PRIMARY KEY (id);


--
-- Name: client_permission_grants client_permission_grants_client_id_permission_key_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY client_permission_grants
    ADD CONSTRAINT client_permission_grants_client_id_permission_key_key UNIQUE (client_id, permission_key);


--
-- Name: client_permission_grants client_permission_grants_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY client_permission_grants
    ADD CONSTRAINT client_permission_grants_pkey PRIMARY KEY (id);


--
-- Name: client_role_permissions client_role_permissions_client_role_id_permission_key_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY client_role_permissions
    ADD CONSTRAINT client_role_permissions_client_role_id_permission_key_key UNIQUE (client_role_id, permission_key);


--
-- Name: client_role_permissions client_role_permissions_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY client_role_permissions
    ADD CONSTRAINT client_role_permissions_pkey PRIMARY KEY (id);


--
-- Name: client_roles client_roles_client_id_name_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY client_roles
    ADD CONSTRAINT client_roles_client_id_name_key UNIQUE (client_id, name);


--
-- Name: client_roles client_roles_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY client_roles
    ADD CONSTRAINT client_roles_pkey PRIMARY KEY (id);


--
-- Name: home_dashboard_templates home_dashboard_templates_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY home_dashboard_templates
    ADD CONSTRAINT home_dashboard_templates_pkey PRIMARY KEY (id);


--
-- Name: home_dashboard_templates home_dashboard_templates_client_id_name_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY home_dashboard_templates
    ADD CONSTRAINT home_dashboard_templates_client_id_name_key UNIQUE (client_id, name);


--
-- Name: home_dashboard_templates home_dashboard_templates_role_type_check; Type: CHECK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY home_dashboard_templates
    ADD CONSTRAINT home_dashboard_templates_role_type_check CHECK (role_type IN ('INTERNAL', 'EXTERNAL'));


--
-- Name: clients clients_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY clients
    ADD CONSTRAINT clients_pkey PRIMARY KEY (id);


--
-- Name: clients clients_subdomain_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY clients
    ADD CONSTRAINT clients_subdomain_key UNIQUE (subdomain);


--
-- Name: company_permission_overrides company_permission_overrides_client_id_partner_company_id_p_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY company_permission_overrides
    ADD CONSTRAINT company_permission_overrides_client_id_partner_company_id_p_key UNIQUE (client_id, partner_company_id, permission_key);


--
-- Name: company_permission_overrides company_permission_overrides_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY company_permission_overrides
    ADD CONSTRAINT company_permission_overrides_pkey PRIMARY KEY (id);


--
-- Name: compliance_alerts compliance_alerts_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY compliance_alerts
    ADD CONSTRAINT compliance_alerts_pkey PRIMARY KEY (id);


--
-- Name: compliance_value_caps compliance_value_caps_country_code_client_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY compliance_value_caps
    ADD CONSTRAINT compliance_value_caps_country_code_client_id_key UNIQUE (country_code, client_id);


--
-- Name: compliance_value_caps compliance_value_caps_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY compliance_value_caps
    ADD CONSTRAINT compliance_value_caps_pkey PRIMARY KEY (id);


--
-- Name: connector_field_mappings connector_field_mappings_data_object_id_field_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY connector_field_mappings
    ADD CONSTRAINT connector_field_mappings_data_object_id_field_id_key UNIQUE (data_object_id, field_id);


--
-- Name: connector_field_mappings connector_field_mappings_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY connector_field_mappings
    ADD CONSTRAINT connector_field_mappings_pkey PRIMARY KEY (id);


--
-- Name: connectors connectors_client_id_name_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY connectors
    ADD CONSTRAINT connectors_client_id_name_key UNIQUE (client_id, name);


--
-- Name: connectors connectors_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY connectors
    ADD CONSTRAINT connectors_pkey PRIMARY KEY (id);


--
-- Name: consent_records consent_records_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY consent_records
    ADD CONSTRAINT consent_records_pkey PRIMARY KEY (id);


--
-- Name: course_product_mappings course_product_mappings_course_id_product_category_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY course_product_mappings
    ADD CONSTRAINT course_product_mappings_course_id_product_category_key UNIQUE (course_id, product_category);


--
-- Name: course_product_mappings course_product_mappings_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY course_product_mappings
    ADD CONSTRAINT course_product_mappings_pkey PRIMARY KEY (id);


--
-- Name: currencies currencies_client_id_code_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY currencies
    ADD CONSTRAINT currencies_client_id_code_key UNIQUE (client_id, code);


--
-- Name: currencies currencies_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY currencies
    ADD CONSTRAINT currencies_pkey PRIMARY KEY (id);


--
-- Name: data_object_fields data_object_fields_data_object_id_name_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY data_object_fields
    ADD CONSTRAINT data_object_fields_data_object_id_name_key UNIQUE (data_object_id, name);


--
-- Name: data_object_fields data_object_fields_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY data_object_fields
    ADD CONSTRAINT data_object_fields_pkey PRIMARY KEY (id);


--
-- Name: data_objects data_objects_client_id_name_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY data_objects
    ADD CONSTRAINT data_objects_client_id_name_key UNIQUE (client_id, name);


--
-- Name: data_objects data_objects_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY data_objects
    ADD CONSTRAINT data_objects_pkey PRIMARY KEY (id);


--
-- Name: data_uploads data_uploads_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY data_uploads
    ADD CONSTRAINT data_uploads_pkey PRIMARY KEY (id);


--
-- Name: eligibility_payouts eligibility_payouts_eligibility_mapping_id_requirement_id_c_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY eligibility_payouts
    ADD CONSTRAINT eligibility_payouts_eligibility_mapping_id_requirement_id_c_key UNIQUE (eligibility_mapping_id, requirement_id, currency_id);


--
-- Name: eligibility_payouts eligibility_payouts_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY eligibility_payouts
    ADD CONSTRAINT eligibility_payouts_pkey PRIMARY KEY (id);


--
-- Name: eligibility_rule_groups eligibility_rule_groups_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY eligibility_rule_groups
    ADD CONSTRAINT eligibility_rule_groups_pkey PRIMARY KEY (id);


--
-- Name: eligibility_rules eligibility_rules_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY eligibility_rules
    ADD CONSTRAINT eligibility_rules_pkey PRIMARY KEY (id);


--
-- Name: feature_flags feature_flags_feature_key_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY feature_flags
    ADD CONSTRAINT feature_flags_feature_key_key UNIQUE (feature_key);


--
-- Name: feature_flags feature_flags_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY feature_flags
    ADD CONSTRAINT feature_flags_pkey PRIMARY KEY (id);


--
-- Name: fiscal_year_configs fiscal_year_configs_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY fiscal_year_configs
    ADD CONSTRAINT fiscal_year_configs_pkey PRIMARY KEY (id);


--
-- Name: forecast_accuracy_records forecast_accuracy_records_incentive_id_forecast_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY forecast_accuracy_records
    ADD CONSTRAINT forecast_accuracy_records_incentive_id_forecast_id_key UNIQUE (incentive_id, forecast_id);


--
-- Name: forecast_accuracy_records forecast_accuracy_records_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY forecast_accuracy_records
    ADD CONSTRAINT forecast_accuracy_records_pkey PRIMARY KEY (id);


--
-- Name: forecast_incentive_outcomes forecast_incentive_outcomes_incentive_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY forecast_incentive_outcomes
    ADD CONSTRAINT forecast_incentive_outcomes_incentive_id_key UNIQUE (incentive_id);


--
-- Name: forecast_incentive_outcomes forecast_incentive_outcomes_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY forecast_incentive_outcomes
    ADD CONSTRAINT forecast_incentive_outcomes_pkey PRIMARY KEY (id);


--
-- Name: forecast_region_distributions forecast_region_distributions_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY forecast_region_distributions
    ADD CONSTRAINT forecast_region_distributions_pkey PRIMARY KEY (id);


--
-- Name: forecast_sales_aggregates forecast_sales_aggregates_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY forecast_sales_aggregates
    ADD CONSTRAINT forecast_sales_aggregates_pkey PRIMARY KEY (id);


--
-- Name: forecast_training_correlations forecast_training_correlations_client_id_product_category_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY forecast_training_correlations
    ADD CONSTRAINT forecast_training_correlations_client_id_product_category_key UNIQUE (client_id, product_category);


--
-- Name: forecast_training_correlations forecast_training_correlations_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY forecast_training_correlations
    ADD CONSTRAINT forecast_training_correlations_pkey PRIMARY KEY (id);


--
-- Name: government_segment_config government_segment_config_client_id_segment_value_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY government_segment_config
    ADD CONSTRAINT government_segment_config_client_id_segment_value_key UNIQUE (client_id, segment_value);


--
-- Name: government_segment_config government_segment_config_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY government_segment_config
    ADD CONSTRAINT government_segment_config_pkey PRIMARY KEY (id);


--
-- Name: incentive_approvers incentive_approvers_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY incentive_approvers
    ADD CONSTRAINT incentive_approvers_pkey PRIMARY KEY (id);


--
-- Name: incentive_audience_rules incentive_audience_rules_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY incentive_audience_rules
    ADD CONSTRAINT incentive_audience_rules_pkey PRIMARY KEY (id);


--
-- Name: incentive_budgets incentive_budgets_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY incentive_budgets
    ADD CONSTRAINT incentive_budgets_pkey PRIMARY KEY (id);


--
-- Name: incentive_documents incentive_documents_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY incentive_documents
    ADD CONSTRAINT incentive_documents_pkey PRIMARY KEY (id);


--
-- Name: incentive_forecasts incentive_forecasts_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY incentive_forecasts
    ADD CONSTRAINT incentive_forecasts_pkey PRIMARY KEY (id);


--
-- Name: incentives incentives_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY incentives
    ADD CONSTRAINT incentives_pkey PRIMARY KEY (id);


--
-- Name: journey_stages journey_stages_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY journey_stages
    ADD CONSTRAINT journey_stages_pkey PRIMARY KEY (id);


--
-- Name: kyc_region_config kyc_region_config_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY kyc_region_config
    ADD CONSTRAINT kyc_region_config_pkey PRIMARY KEY (id);


--
-- Name: kyc_region_config kyc_region_config_region_code_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY kyc_region_config
    ADD CONSTRAINT kyc_region_config_region_code_key UNIQUE (region_code);


--
-- Name: legal_policies legal_policies_client_id_policy_type_version_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY legal_policies
    ADD CONSTRAINT legal_policies_client_id_policy_type_version_key UNIQUE (client_id, policy_type, version);


--
-- Name: legal_policies legal_policies_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY legal_policies
    ADD CONSTRAINT legal_policies_pkey PRIMARY KEY (id);


--
-- Name: lms_courses lms_courses_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY lms_courses
    ADD CONSTRAINT lms_courses_pkey PRIMARY KEY (id);


--
-- Name: location_budget_allocations location_budget_allocations_budget_id_location_value_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY location_budget_allocations
    ADD CONSTRAINT location_budget_allocations_budget_id_location_value_id_key UNIQUE (budget_id, location_value_id);


--
-- Name: location_budget_allocations location_budget_allocations_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY location_budget_allocations
    ADD CONSTRAINT location_budget_allocations_pkey PRIMARY KEY (id);


--
-- Name: location_levels location_levels_client_id_depth_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY location_levels
    ADD CONSTRAINT location_levels_client_id_depth_key UNIQUE (client_id, depth);


--
-- Name: location_levels location_levels_client_id_name_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY location_levels
    ADD CONSTRAINT location_levels_client_id_name_key UNIQUE (client_id, name);


--
-- Name: location_levels location_levels_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY location_levels
    ADD CONSTRAINT location_levels_pkey PRIMARY KEY (id);


--
-- Name: location_values location_values_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY location_values
    ADD CONSTRAINT location_values_pkey PRIMARY KEY (id);


--
-- Name: notification_types notification_types_key_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY notification_types
    ADD CONSTRAINT notification_types_key_key UNIQUE (key);


--
-- Name: notification_types notification_types_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY notification_types
    ADD CONSTRAINT notification_types_pkey PRIMARY KEY (id);


--
-- Name: notifications notifications_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY notifications
    ADD CONSTRAINT notifications_pkey PRIMARY KEY (id);


--
-- Name: onboarding_tokens onboarding_tokens_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY onboarding_tokens
    ADD CONSTRAINT onboarding_tokens_pkey PRIMARY KEY (id);


--
-- Name: onboarding_tokens onboarding_tokens_user_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY onboarding_tokens
    ADD CONSTRAINT onboarding_tokens_user_id_key UNIQUE (user_id);


--
-- Name: partner_beneficial_owners partner_beneficial_owners_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY partner_beneficial_owners
    ADD CONSTRAINT partner_beneficial_owners_pkey PRIMARY KEY (id);


--
-- Name: partner_companies partner_companies_client_id_name_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY partner_companies
    ADD CONSTRAINT partner_companies_client_id_name_key UNIQUE (client_id, name);


--
-- Name: partner_companies partner_companies_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY partner_companies
    ADD CONSTRAINT partner_companies_pkey PRIMARY KEY (id);


--
-- Name: partner_company_locations partner_company_locations_partner_company_id_location_value_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY partner_company_locations
    ADD CONSTRAINT partner_company_locations_partner_company_id_location_value_key UNIQUE (partner_company_id, location_value_id);


--
-- Name: partner_company_locations partner_company_locations_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY partner_company_locations
    ADD CONSTRAINT partner_company_locations_pkey PRIMARY KEY (id);


--
-- Name: partner_kyc_records partner_kyc_records_partner_company_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY partner_kyc_records
    ADD CONSTRAINT partner_kyc_records_partner_company_id_key UNIQUE (partner_company_id);


--
-- Name: partner_kyc_records partner_kyc_records_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY partner_kyc_records
    ADD CONSTRAINT partner_kyc_records_pkey PRIMARY KEY (id);


--
-- Name: partner_program_acknowledgments partner_program_acknowledgmen_partner_company_id_incentive__key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY partner_program_acknowledgments
    ADD CONSTRAINT partner_program_acknowledgmen_partner_company_id_incentive__key UNIQUE (partner_company_id, incentive_id);


--
-- Name: partner_program_acknowledgments partner_program_acknowledgments_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY partner_program_acknowledgments
    ADD CONSTRAINT partner_program_acknowledgments_pkey PRIMARY KEY (id);


--
-- Name: payout_bands payout_bands_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY payout_bands
    ADD CONSTRAINT payout_bands_pkey PRIMARY KEY (id);


--
-- Name: payout_configs payout_configs_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY payout_configs
    ADD CONSTRAINT payout_configs_pkey PRIMARY KEY (id);


--
-- Name: permissions permissions_permission_key_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY permissions
    ADD CONSTRAINT permissions_permission_key_key UNIQUE (permission_key);


--
-- Name: permissions permissions_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY permissions
    ADD CONSTRAINT permissions_pkey PRIMARY KEY (id);


--
-- Name: po_eligibility_mappings po_eligibility_mappings_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY po_eligibility_mappings
    ADD CONSTRAINT po_eligibility_mappings_pkey PRIMARY KEY (id);


--
-- Name: po_eligibility_mappings po_eligibility_mappings_purchase_order_id_incentive_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY po_eligibility_mappings
    ADD CONSTRAINT po_eligibility_mappings_purchase_order_id_incentive_id_key UNIQUE (purchase_order_id, incentive_id);


--
-- Name: products products_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY products
    ADD CONSTRAINT products_pkey PRIMARY KEY (id);


--
-- Name: purchase_order_lines purchase_order_lines_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY purchase_order_lines
    ADD CONSTRAINT purchase_order_lines_pkey PRIMARY KEY (id);


--
-- Name: purchase_orders purchase_orders_client_id_order_number_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY purchase_orders
    ADD CONSTRAINT purchase_orders_client_id_order_number_key UNIQUE (client_id, order_number);


--
-- Name: purchase_orders purchase_orders_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY purchase_orders
    ADD CONSTRAINT purchase_orders_pkey PRIMARY KEY (id);


--
-- Name: recommendation_configs recommendation_configs_client_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY recommendation_configs
    ADD CONSTRAINT recommendation_configs_client_id_key UNIQUE (client_id);


--
-- Name: recommendation_configs recommendation_configs_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY recommendation_configs
    ADD CONSTRAINT recommendation_configs_pkey PRIMARY KEY (id);


--
-- Name: recommendation_interactions recommendation_interactions_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY recommendation_interactions
    ADD CONSTRAINT recommendation_interactions_pkey PRIMARY KEY (id);


--
-- Name: recommendation_scores recommendation_scores_client_id_user_id_recommendation_type_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY recommendation_scores
    ADD CONSTRAINT recommendation_scores_client_id_user_id_recommendation_type_key UNIQUE (client_id, user_id, recommendation_type, target_id);


--
-- Name: recommendation_scores recommendation_scores_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY recommendation_scores
    ADD CONSTRAINT recommendation_scores_pkey PRIMARY KEY (id);


--
-- Name: regional_compliance_config regional_compliance_config_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY regional_compliance_config
    ADD CONSTRAINT regional_compliance_config_pkey PRIMARY KEY (id);


--
-- Name: regional_compliance_config regional_compliance_config_region_code_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY regional_compliance_config
    ADD CONSTRAINT regional_compliance_config_region_code_key UNIQUE (region_code);


--
-- Name: retention_policies retention_policies_client_id_data_category_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY retention_policies
    ADD CONSTRAINT retention_policies_client_id_data_category_key UNIQUE (client_id, data_category);


--
-- Name: retention_policies retention_policies_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY retention_policies
    ADD CONSTRAINT retention_policies_pkey PRIMARY KEY (id);


--
-- Name: retention_policy_bounds retention_policy_bounds_data_category_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY retention_policy_bounds
    ADD CONSTRAINT retention_policy_bounds_data_category_key UNIQUE (data_category);


--
-- Name: retention_policy_bounds retention_policy_bounds_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY retention_policy_bounds
    ADD CONSTRAINT retention_policy_bounds_pkey PRIMARY KEY (id);


--
-- Name: reward_balances reward_balances_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY reward_balances
    ADD CONSTRAINT reward_balances_pkey PRIMARY KEY (id);


--
-- Name: reward_transactions reward_transactions_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY reward_transactions
    ADD CONSTRAINT reward_transactions_pkey PRIMARY KEY (id);


--
-- Name: sales_requirements sales_requirements_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY sales_requirements
    ADD CONSTRAINT sales_requirements_pkey PRIMARY KEY (id);


--
-- Name: sub_processors sub_processors_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY sub_processors
    ADD CONSTRAINT sub_processors_pkey PRIMARY KEY (id);


--
-- Name: sync_schedules sync_schedules_client_id_data_object_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY sync_schedules
    ADD CONSTRAINT sync_schedules_client_id_data_object_id_key UNIQUE (client_id, data_object_id);


--
-- Name: sync_schedules sync_schedules_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY sync_schedules
    ADD CONSTRAINT sync_schedules_pkey PRIMARY KEY (id);


--
-- Name: tagging_jobs tagging_jobs_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY tagging_jobs
    ADD CONSTRAINT tagging_jobs_pkey PRIMARY KEY (id);


--
-- Name: training_course_assignments training_course_assignments_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY training_course_assignments
    ADD CONSTRAINT training_course_assignments_pkey PRIMARY KEY (id);


--
-- Name: activity_categories uq_activity_category_client_name; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY activity_categories
    ADD CONSTRAINT uq_activity_category_client_name UNIQUE (client_id, name);


--
-- Name: approval_decisions uq_approval_decision_token; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY approval_decisions
    ADD CONSTRAINT uq_approval_decision_token UNIQUE (token_id);


--
-- Name: approval_decisions uq_approval_per_approver; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY approval_decisions
    ADD CONSTRAINT uq_approval_per_approver UNIQUE (incentive_id, approver_email);


--
-- Name: incentive_budgets uq_budget_incentive_currency; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY incentive_budgets
    ADD CONSTRAINT uq_budget_incentive_currency UNIQUE (incentive_id, currency_id);


--
-- Name: builder_field_configs uq_builder_field_section_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY builder_field_configs
    ADD CONSTRAINT uq_builder_field_section_key UNIQUE (section_config_id, field_key);


--
-- Name: builder_section_configs uq_builder_section_client_type_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY builder_section_configs
    ADD CONSTRAINT uq_builder_section_client_type_key UNIQUE (client_id, incentive_type, section_key);


--
-- Name: fiscal_year_configs uq_client_fiscal_label; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY fiscal_year_configs
    ADD CONSTRAINT uq_client_fiscal_label UNIQUE (client_id, label);


--
-- Name: products uq_products_client_sku; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY products
    ADD CONSTRAINT uq_products_client_sku UNIQUE (client_id, sku);


--
-- Name: reward_balances uq_reward_balances_client_user_currency; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY reward_balances
    ADD CONSTRAINT uq_reward_balances_client_user_currency UNIQUE (client_id, user_id, currency_id);


--
-- Name: user_activity_progress uq_user_activity_progress; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY user_activity_progress
    ADD CONSTRAINT uq_user_activity_progress UNIQUE (client_id, user_id, activity_definition_id);


--
-- Name: user_activity_document_submissions uq_user_document_submission; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY user_activity_document_submissions
    ADD CONSTRAINT uq_user_document_submission UNIQUE (client_id, user_id, document_requirement_id);


--
-- Name: user_journey_stage_progress uq_user_journey_stage; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY user_journey_stage_progress
    ADD CONSTRAINT uq_user_journey_stage UNIQUE (client_id, user_id, journey_id, stage_id);


--
-- Name: user_activity_document_submissions user_activity_document_submissions_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY user_activity_document_submissions
    ADD CONSTRAINT user_activity_document_submissions_pkey PRIMARY KEY (id);


--
-- Name: user_activity_progress user_activity_progress_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY user_activity_progress
    ADD CONSTRAINT user_activity_progress_pkey PRIMARY KEY (id);


--
-- Name: user_course_completions user_course_completions_client_id_user_id_course_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY user_course_completions
    ADD CONSTRAINT user_course_completions_client_id_user_id_course_id_key UNIQUE (client_id, user_id, course_id);


--
-- Name: user_course_completions user_course_completions_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY user_course_completions
    ADD CONSTRAINT user_course_completions_pkey PRIMARY KEY (id);


--
-- Name: user_incentive_completions user_incentive_completions_client_id_incentive_id_user_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY user_incentive_completions
    ADD CONSTRAINT user_incentive_completions_client_id_incentive_id_user_id_key UNIQUE (client_id, incentive_id, user_id);


--
-- Name: user_incentive_completions user_incentive_completions_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY user_incentive_completions
    ADD CONSTRAINT user_incentive_completions_pkey PRIMARY KEY (id);


--
-- Name: user_journey_stage_progress user_journey_stage_progress_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY user_journey_stage_progress
    ADD CONSTRAINT user_journey_stage_progress_pkey PRIMARY KEY (id);


--
-- Name: user_legal_acceptances user_legal_acceptances_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY user_legal_acceptances
    ADD CONSTRAINT user_legal_acceptances_pkey PRIMARY KEY (id);


--
-- Name: user_notification_preferences user_notification_preferences_client_id_user_id_notificatio_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY user_notification_preferences
    ADD CONSTRAINT user_notification_preferences_client_id_user_id_notificatio_key UNIQUE (client_id, user_id, notification_type_id);


--
-- Name: user_notification_preferences user_notification_preferences_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY user_notification_preferences
    ADD CONSTRAINT user_notification_preferences_pkey PRIMARY KEY (id);


--
-- Name: user_notification_settings user_notification_settings_client_id_user_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY user_notification_settings
    ADD CONSTRAINT user_notification_settings_client_id_user_id_key UNIQUE (client_id, user_id);


--
-- Name: user_notification_settings user_notification_settings_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY user_notification_settings
    ADD CONSTRAINT user_notification_settings_pkey PRIMARY KEY (id);


--
-- Name: user_permission_overrides user_permission_overrides_client_id_user_id_permission_key_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY user_permission_overrides
    ADD CONSTRAINT user_permission_overrides_client_id_user_id_permission_key_key UNIQUE (client_id, user_id, permission_key);


--
-- Name: user_permission_overrides user_permission_overrides_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY user_permission_overrides
    ADD CONSTRAINT user_permission_overrides_pkey PRIMARY KEY (id);


--
-- Name: users users_email_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY users
    ADD CONSTRAINT users_email_key UNIQUE (email);


--
-- Name: users users_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY users
    ADD CONSTRAINT users_pkey PRIMARY KEY (id);


--
-- Name: whistleblower_case_updates whistleblower_case_updates_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY whistleblower_case_updates
    ADD CONSTRAINT whistleblower_case_updates_pkey PRIMARY KEY (id);


--
-- Name: whistleblower_reports whistleblower_reports_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY whistleblower_reports
    ADD CONSTRAINT whistleblower_reports_pkey PRIMARY KEY (id);


--
-- Name: whistleblower_reports whistleblower_reports_tracking_number_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY whistleblower_reports
    ADD CONSTRAINT whistleblower_reports_tracking_number_key UNIQUE (tracking_number);


--
-- Name: idx_activity_categories_client; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_activity_categories_client ON activity_categories USING btree (client_id);


--
-- Name: idx_activity_definitions_incentive_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_activity_definitions_incentive_id ON activity_definitions USING btree (incentive_id);


--
-- Name: idx_activity_docs_definition_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_activity_docs_definition_id ON activity_document_requirements USING btree (activity_definition_id);


--
-- Name: idx_approval_decisions_incentive; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_approval_decisions_incentive ON approval_decisions USING btree (incentive_id);


--
-- Name: idx_audience_rules_incentive_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_audience_rules_incentive_id ON incentive_audience_rules USING btree (incentive_id);


--
-- Name: idx_audit_logs_client_action; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_audit_logs_client_action ON audit_logs USING btree (client_id, action);


--
-- Name: idx_audit_logs_client_created; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_audit_logs_client_created ON audit_logs USING btree (client_id, created_at DESC);


--
-- Name: idx_audit_logs_client_user_type; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_audit_logs_client_user_type ON audit_logs USING btree (client_id, user_type);


--
-- Name: idx_audit_logs_resource; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_audit_logs_resource ON audit_logs USING btree (resource_type, resource_id);


--
-- Name: idx_breach_incidents_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_breach_incidents_status ON breach_incidents USING btree (status);


--
-- Name: idx_budget_util_currency; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_budget_util_currency ON budget_utilizations USING btree (currency_id);


--
-- Name: idx_budget_util_incentive; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_budget_util_incentive ON budget_utilizations USING btree (incentive_id);


--
-- Name: idx_builder_field_configs_data_obj; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_builder_field_configs_data_obj ON builder_field_configs USING btree (data_object_field_id);


--
-- Name: idx_builder_field_configs_section; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_builder_field_configs_section ON builder_field_configs USING btree (section_config_id);


--
-- Name: idx_builder_section_configs_client; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_builder_section_configs_client ON builder_section_configs USING btree (client_id);


--
-- Name: idx_builder_section_configs_type; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_builder_section_configs_type ON builder_section_configs USING btree (client_id, incentive_type);


--
-- Name: idx_claim_actions_claimed_at; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_claim_actions_claimed_at ON claim_actions USING btree (claimed_at);


--
-- Name: idx_claim_actions_client; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_claim_actions_client ON claim_actions USING btree (client_id);


--
-- Name: idx_claim_actions_po; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_claim_actions_po ON claim_actions USING btree (purchase_order_id);


--
-- Name: idx_claim_actions_po_user; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_claim_actions_po_user ON claim_actions USING btree (client_id, purchase_order_id, user_id);


--
-- Name: idx_claim_actions_user; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_claim_actions_user ON claim_actions USING btree (user_id);


--
-- Name: idx_client_feature_overrides_client_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_client_feature_overrides_client_id ON client_feature_overrides USING btree (client_id);


--
-- Name: idx_client_perm_grants_lookup; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_client_perm_grants_lookup ON client_permission_grants USING btree (client_id);


--
-- Name: idx_client_role_perms_role; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_client_role_perms_role ON client_role_permissions USING btree (client_role_id);


--
-- Name: idx_client_roles_client; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_client_roles_client ON client_roles USING btree (client_id);


--
-- Name: idx_clients_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_clients_status ON clients USING btree (status);


--
-- Name: idx_clients_subdomain; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_clients_subdomain ON clients USING btree (subdomain);


--
-- Name: idx_cnrc_client_type; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_cnrc_client_type ON client_notification_role_configs USING btree (client_id, notification_type_id);


--
-- Name: idx_company_perm_overrides_lookup; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_company_perm_overrides_lookup ON company_permission_overrides USING btree (client_id, partner_company_id);


--
-- Name: idx_compliance_alerts_client; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_compliance_alerts_client ON compliance_alerts USING btree (client_id);


--
-- Name: idx_compliance_alerts_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_compliance_alerts_status ON compliance_alerts USING btree (status);


--
-- Name: idx_connector_field_mappings_connector_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_connector_field_mappings_connector_id ON connector_field_mappings USING btree (connector_id);


--
-- Name: idx_connector_field_mappings_object_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_connector_field_mappings_object_id ON connector_field_mappings USING btree (data_object_id);


--
-- Name: idx_connectors_client_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_connectors_client_id ON connectors USING btree (client_id);


--
-- Name: idx_consent_records_client; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_consent_records_client ON consent_records USING btree (client_id);


--
-- Name: idx_consent_records_user; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_consent_records_user ON consent_records USING btree (user_id, consent_type);


--
-- Name: idx_cpm_category; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_cpm_category ON course_product_mappings USING btree (product_category);


--
-- Name: idx_cpm_course; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_cpm_course ON course_product_mappings USING btree (course_id);


--
-- Name: idx_currencies_client_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_currencies_client_id ON currencies USING btree (client_id);


--
-- Name: idx_data_object_fields_exclude_rule; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_data_object_fields_exclude_rule ON data_object_fields USING btree (exclude_from_rules, rule_label);


--
-- Name: idx_data_object_fields_object_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_data_object_fields_object_id ON data_object_fields USING btree (data_object_id);


--
-- Name: idx_data_objects_client_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_data_objects_client_id ON data_objects USING btree (client_id);


--
-- Name: idx_data_uploads_client_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_data_uploads_client_id ON data_uploads USING btree (client_id);


--
-- Name: idx_data_uploads_created_at; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_data_uploads_created_at ON data_uploads USING btree (created_at DESC);


--
-- Name: idx_data_uploads_data_object_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_data_uploads_data_object_id ON data_uploads USING btree (data_object_id);


--
-- Name: idx_eligibility_rules_group_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_eligibility_rules_group_id ON eligibility_rules USING btree (rule_group_id);


--
-- Name: idx_ep_currency; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_ep_currency ON eligibility_payouts USING btree (currency_id);


--
-- Name: idx_ep_mapping; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_ep_mapping ON eligibility_payouts USING btree (eligibility_mapping_id);


--
-- Name: idx_ep_requirement; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_ep_requirement ON eligibility_payouts USING btree (requirement_id);


--
-- Name: idx_far_client_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_far_client_id ON forecast_accuracy_records USING btree (client_id);


--
-- Name: idx_far_evaluated_at; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_far_evaluated_at ON forecast_accuracy_records USING btree (evaluated_at);


--
-- Name: idx_far_incentive_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_far_incentive_id ON forecast_accuracy_records USING btree (incentive_id);


--
-- Name: idx_fio_client_type; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_fio_client_type ON forecast_incentive_outcomes USING btree (client_id, incentive_type);


--
-- Name: idx_fiscal_year_configs_client; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_fiscal_year_configs_client ON fiscal_year_configs USING btree (client_id);


--
-- Name: idx_fiscal_year_configs_client_dates; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_fiscal_year_configs_client_dates ON fiscal_year_configs USING btree (client_id, start_date, end_date);


--
-- Name: idx_forecasts_incentive_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_forecasts_incentive_id ON incentive_forecasts USING btree (incentive_id);


--
-- Name: idx_frd_client; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_frd_client ON forecast_region_distributions USING btree (client_id);


--
-- Name: idx_fsa_client_month; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_fsa_client_month ON forecast_sales_aggregates USING btree (client_id, year_month);


--
-- Name: idx_ftc_client_category; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_ftc_client_category ON forecast_training_correlations USING btree (client_id, product_category);


--
-- Name: idx_ftc_client_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_ftc_client_id ON forecast_training_correlations USING btree (client_id);


--
-- Name: idx_incentive_approvers_incentive_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_incentive_approvers_incentive_id ON incentive_approvers USING btree (incentive_id);


--
-- Name: idx_incentive_documents_incentive_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_incentive_documents_incentive_id ON incentive_documents USING btree (incentive_id);


--
-- Name: idx_incentives_client_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_incentives_client_id ON incentives USING btree (client_id);


--
-- Name: idx_incentives_created_by; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_incentives_created_by ON incentives USING btree (created_by);


--
-- Name: idx_incentives_description_lower; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_incentives_description_lower ON incentives USING btree (lower((description)::text));


--
-- Name: idx_incentives_name_lower; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_incentives_name_lower ON incentives USING btree (lower((name)::text));


--
-- Name: idx_incentives_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_incentives_status ON incentives USING btree (status);


--
-- Name: idx_incentives_type; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_incentives_type ON incentives USING btree (incentive_type);


--
-- Name: idx_journey_stages_incentive_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_journey_stages_incentive_id ON journey_stages USING btree (incentive_id);


--
-- Name: idx_journey_stages_linked_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_journey_stages_linked_id ON journey_stages USING btree (linked_incentive_id);


--
-- Name: idx_lba_budget; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_lba_budget ON location_budget_allocations USING btree (budget_id);


--
-- Name: idx_legal_policies_client_active; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_legal_policies_client_active ON legal_policies USING btree (client_id, is_active);


--
-- Name: idx_lms_courses_category; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_lms_courses_category ON lms_courses USING btree (category);


--
-- Name: idx_lms_courses_external_course_id; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX idx_lms_courses_external_course_id ON lms_courses USING btree (external_course_id) WHERE (external_course_id IS NOT NULL);


--
-- Name: idx_lms_courses_name_lower; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_lms_courses_name_lower ON lms_courses USING btree (lower((name)::text));


--
-- Name: idx_location_values_client; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_location_values_client ON location_values USING btree (client_id);


--
-- Name: idx_location_values_level; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_location_values_level ON location_values USING btree (level_id);


--
-- Name: idx_location_values_parent; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_location_values_parent ON location_values USING btree (parent_id);


--
-- Name: idx_location_values_root_unique; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX idx_location_values_root_unique ON location_values USING btree (client_id, level_id, name) WHERE (parent_id IS NULL);


--
-- Name: idx_notifications_user_created; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_notifications_user_created ON notifications USING btree (client_id, user_id, created_at DESC);


--
-- Name: idx_notifications_user_unread; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_notifications_user_unread ON notifications USING btree (client_id, user_id, is_read, created_at DESC);


--
-- Name: idx_onboarding_tokens_hash; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_onboarding_tokens_hash ON onboarding_tokens USING btree (token_hash);


--
-- Name: idx_onboarding_tokens_user; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_onboarding_tokens_user ON onboarding_tokens USING btree (user_id);


--
-- Name: idx_partner_ack_company; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_partner_ack_company ON partner_program_acknowledgments USING btree (partner_company_id);


--
-- Name: idx_partner_ack_incentive; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_partner_ack_incentive ON partner_program_acknowledgments USING btree (incentive_id);


--
-- Name: idx_partner_companies_client_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_partner_companies_client_id ON partner_companies USING btree (client_id);


--
-- Name: idx_partner_companies_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_partner_companies_status ON partner_companies USING btree (status);


--
-- Name: idx_partner_kyc_client; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_partner_kyc_client ON partner_kyc_records USING btree (client_id);


--
-- Name: idx_partner_kyc_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_partner_kyc_status ON partner_kyc_records USING btree (kyc_status);


--
-- Name: idx_payout_bands_config_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_payout_bands_config_id ON payout_bands USING btree (payout_config_id);


--
-- Name: idx_payout_configs_requirement_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_payout_configs_requirement_id ON payout_configs USING btree (requirement_id);


--
-- Name: idx_pc_client_external_partner_id; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX idx_pc_client_external_partner_id ON partner_companies USING btree (client_id, external_partner_id) WHERE (external_partner_id IS NOT NULL);


--
-- Name: idx_pcl_client; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_pcl_client ON partner_company_locations USING btree (client_id);


--
-- Name: idx_pcl_location; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_pcl_location ON partner_company_locations USING btree (location_value_id);


--
-- Name: idx_pcl_partner; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_pcl_partner ON partner_company_locations USING btree (partner_company_id);


--
-- Name: idx_pem_client_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_pem_client_id ON po_eligibility_mappings USING btree (client_id);


--
-- Name: idx_pem_eligible; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_pem_eligible ON po_eligibility_mappings USING btree (eligible);


--
-- Name: idx_pem_incentive; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_pem_incentive ON po_eligibility_mappings USING btree (incentive_id);


--
-- Name: idx_pem_purchase_order; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_pem_purchase_order ON po_eligibility_mappings USING btree (purchase_order_id);


--
-- Name: idx_pem_tagging_job; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_pem_tagging_job ON po_eligibility_mappings USING btree (tagging_job_id);


--
-- Name: idx_po_eligibility_po_eligible; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_po_eligibility_po_eligible ON po_eligibility_mappings USING btree (purchase_order_id, eligible);


--
-- Name: idx_po_lines_order; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_po_lines_order ON purchase_order_lines USING btree (purchase_order_id);


--
-- Name: idx_po_lines_product; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_po_lines_product ON purchase_order_lines USING btree (product_id);


--
-- Name: idx_po_lines_transaction_id; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX idx_po_lines_transaction_id ON purchase_order_lines USING btree (transaction_id);


--
-- Name: idx_po_needs_retagging; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_po_needs_retagging ON purchase_orders USING btree (needs_retagging) WHERE (needs_retagging = true);


--
-- Name: idx_products_category; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_products_category ON products USING btree (category);


--
-- Name: idx_products_client_category_name; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_products_client_category_name ON products USING btree (client_id, category, name);


--
-- Name: idx_products_client_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_products_client_id ON products USING btree (client_id);


--
-- Name: idx_products_name_lower; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_products_name_lower ON products USING btree (lower((name)::text));


--
-- Name: idx_products_sku_lower; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_products_sku_lower ON products USING btree (lower((sku)::text));


--
-- Name: idx_purchase_orders_client_date; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_purchase_orders_client_date ON purchase_orders USING btree (client_id, order_date DESC);


--
-- Name: idx_purchase_orders_client_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_purchase_orders_client_id ON purchase_orders USING btree (client_id);


--
-- Name: idx_purchase_orders_date; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_purchase_orders_date ON purchase_orders USING btree (order_date);


--
-- Name: idx_purchase_orders_partner; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_purchase_orders_partner ON purchase_orders USING btree (partner_company_id);


--
-- Name: idx_rec_config_client; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_rec_config_client ON recommendation_configs USING btree (client_id);


--
-- Name: idx_rec_interactions_target; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_rec_interactions_target ON recommendation_interactions USING btree (client_id, target_id, interaction_type);


--
-- Name: idx_rec_interactions_user; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_rec_interactions_user ON recommendation_interactions USING btree (client_id, user_id, recommendation_type);


--
-- Name: idx_rec_scores_computed; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_rec_scores_computed ON recommendation_scores USING btree (computed_at);


--
-- Name: idx_rec_scores_user_type; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_rec_scores_user_type ON recommendation_scores USING btree (client_id, user_id, recommendation_type, rank);


--
-- Name: idx_reward_balances_client_user; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_reward_balances_client_user ON reward_balances USING btree (client_id, user_id);


--
-- Name: idx_reward_transactions_completion; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_reward_transactions_completion ON reward_transactions USING btree (completion_id);


--
-- Name: idx_reward_tx_claim_action; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_reward_tx_claim_action ON reward_transactions USING btree (claim_action_id);


--
-- Name: idx_reward_tx_incentive; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_reward_tx_incentive ON reward_transactions USING btree (client_id, incentive_id);


--
-- Name: idx_reward_tx_user_incentive_currency; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_reward_tx_user_incentive_currency ON reward_transactions USING btree (client_id, user_id, incentive_id, currency_id);


--
-- Name: idx_reward_txn_client_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_reward_txn_client_id ON reward_transactions USING btree (client_id);


--
-- Name: idx_reward_txn_incentive_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_reward_txn_incentive_id ON reward_transactions USING btree (incentive_id);


--
-- Name: idx_reward_txn_user_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_reward_txn_user_id ON reward_transactions USING btree (user_id);


--
-- Name: idx_rt_claim_action; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_rt_claim_action ON reward_transactions USING btree (claim_action_id);


--
-- Name: idx_rule_groups_requirement_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_rule_groups_requirement_id ON eligibility_rule_groups USING btree (requirement_id);


--
-- Name: idx_sales_requirements_incentive_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_sales_requirements_incentive_id ON sales_requirements USING btree (incentive_id);


--
-- Name: idx_sync_schedules_next_run; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_sync_schedules_next_run ON sync_schedules USING btree (next_run_at) WHERE (enabled = true);


--
-- Name: idx_tagging_jobs_client_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_tagging_jobs_client_id ON tagging_jobs USING btree (client_id);


--
-- Name: idx_tagging_jobs_created_at; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_tagging_jobs_created_at ON tagging_jobs USING btree (created_at DESC);


--
-- Name: idx_tmp_po_client_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_tmp_po_client_id ON purchase_orders USING btree (client_id);


--
-- Name: idx_tmp_pol_po_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_tmp_pol_po_id ON purchase_order_lines USING btree (purchase_order_id);


--
-- Name: idx_tmp_pol_product_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_tmp_pol_product_id ON purchase_order_lines USING btree (product_id);


--
-- Name: idx_training_courses_incentive_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_training_courses_incentive_id ON training_course_assignments USING btree (incentive_id);


--
-- Name: idx_uads_client; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_uads_client ON user_activity_document_submissions USING btree (client_id);


--
-- Name: idx_uads_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_uads_status ON user_activity_document_submissions USING btree (status);


--
-- Name: idx_uads_user_activity; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_uads_user_activity ON user_activity_document_submissions USING btree (user_id, activity_definition_id);


--
-- Name: idx_uap_activity; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_uap_activity ON user_activity_progress USING btree (activity_definition_id);


--
-- Name: idx_uap_client; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_uap_client ON user_activity_progress USING btree (client_id);


--
-- Name: idx_uap_user_incentive; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_uap_user_incentive ON user_activity_progress USING btree (user_id, incentive_id);


--
-- Name: idx_ucc_client; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_ucc_client ON user_course_completions USING btree (client_id);


--
-- Name: idx_ucc_completed; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_ucc_completed ON user_course_completions USING btree (completed_at);


--
-- Name: idx_ucc_course; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_ucc_course ON user_course_completions USING btree (course_id);


--
-- Name: idx_ucc_source; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_ucc_source ON user_course_completions USING btree (source);


--
-- Name: idx_ucc_user; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_ucc_user ON user_course_completions USING btree (user_id);


--
-- Name: idx_uic_client; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_uic_client ON user_incentive_completions USING btree (client_id);


--
-- Name: idx_uic_incentive; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_uic_incentive ON user_incentive_completions USING btree (incentive_id);


--
-- Name: idx_uic_user_client; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_uic_user_client ON user_incentive_completions USING btree (user_id, client_id);


--
-- Name: idx_ujsp_client; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_ujsp_client ON user_journey_stage_progress USING btree (client_id);


--
-- Name: idx_ujsp_linked; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_ujsp_linked ON user_journey_stage_progress USING btree (linked_incentive_id);


--
-- Name: idx_ujsp_user_journey; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_ujsp_user_journey ON user_journey_stage_progress USING btree (user_id, journey_id);


--
-- Name: idx_unp_client_user; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_unp_client_user ON user_notification_preferences USING btree (client_id, user_id);


--
-- Name: idx_user_legal_acceptances_client; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_user_legal_acceptances_client ON user_legal_acceptances USING btree (client_id);


--
-- Name: idx_user_legal_acceptances_policy; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_user_legal_acceptances_policy ON user_legal_acceptances USING btree (policy_id);


--
-- Name: idx_user_legal_acceptances_user; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_user_legal_acceptances_user ON user_legal_acceptances USING btree (user_id);


--
-- Name: idx_user_perm_overrides_lookup; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_user_perm_overrides_lookup ON user_permission_overrides USING btree (client_id, user_id);


--
-- Name: idx_users_client_external_user_id; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX idx_users_client_external_user_id ON users USING btree (client_id, external_user_id) WHERE (external_user_id IS NOT NULL);


--
-- Name: idx_users_client_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_users_client_id ON users USING btree (client_id);


--
-- Name: idx_users_client_name; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_users_client_name ON users USING btree (client_id, first_name, last_name);


--
-- Name: idx_users_client_role; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_users_client_role ON users USING btree (client_role_id);


--
-- Name: idx_users_email; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_users_email ON users USING btree (email);


--
-- Name: idx_users_partner_company_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_users_partner_company_id ON users USING btree (partner_company_id);


--
-- Name: idx_users_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_users_status ON users USING btree (status);


--
-- Name: idx_whistleblower_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_whistleblower_status ON whistleblower_reports USING btree (status);


--
-- Name: idx_whistleblower_tracking; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_whistleblower_tracking ON whistleblower_reports USING btree (tracking_number);


--
-- Name: activity_categories activity_categories_client_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY activity_categories
    ADD CONSTRAINT activity_categories_client_id_fkey FOREIGN KEY (client_id) REFERENCES clients(id) ON DELETE CASCADE;


--
-- Name: activity_definitions activity_definitions_incentive_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY activity_definitions
    ADD CONSTRAINT activity_definitions_incentive_id_fkey FOREIGN KEY (incentive_id) REFERENCES incentives(id) ON DELETE CASCADE;


--
-- Name: activity_document_requirements activity_document_requirements_activity_definition_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY activity_document_requirements
    ADD CONSTRAINT activity_document_requirements_activity_definition_id_fkey FOREIGN KEY (activity_definition_id) REFERENCES activity_definitions(id) ON DELETE CASCADE;


--
-- Name: approval_decisions approval_decisions_incentive_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY approval_decisions
    ADD CONSTRAINT approval_decisions_incentive_id_fkey FOREIGN KEY (incentive_id) REFERENCES incentives(id) ON DELETE CASCADE;


--
-- Name: audit_logs audit_logs_client_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY audit_logs
    ADD CONSTRAINT audit_logs_client_id_fkey FOREIGN KEY (client_id) REFERENCES clients(id) ON DELETE CASCADE;


--
-- Name: breach_incidents breach_incidents_created_by_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY breach_incidents
    ADD CONSTRAINT breach_incidents_created_by_fkey FOREIGN KEY (created_by) REFERENCES users(id);


--
-- Name: budget_utilizations budget_utilizations_incentive_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY budget_utilizations
    ADD CONSTRAINT budget_utilizations_incentive_id_fkey FOREIGN KEY (incentive_id) REFERENCES incentives(id) ON DELETE CASCADE;


--
-- Name: budget_utilizations budget_utilizations_location_value_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY budget_utilizations
    ADD CONSTRAINT budget_utilizations_location_value_id_fkey FOREIGN KEY (location_value_id) REFERENCES location_values(id);


--
-- Name: builder_field_configs builder_field_configs_data_object_field_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY builder_field_configs
    ADD CONSTRAINT builder_field_configs_data_object_field_id_fkey FOREIGN KEY (data_object_field_id) REFERENCES data_object_fields(id) ON DELETE SET NULL;


--
-- Name: builder_field_configs builder_field_configs_section_config_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY builder_field_configs
    ADD CONSTRAINT builder_field_configs_section_config_id_fkey FOREIGN KEY (section_config_id) REFERENCES builder_section_configs(id) ON DELETE CASCADE;


--
-- Name: builder_section_configs builder_section_configs_client_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY builder_section_configs
    ADD CONSTRAINT builder_section_configs_client_id_fkey FOREIGN KEY (client_id) REFERENCES clients(id) ON DELETE CASCADE;


--
-- Name: claim_actions claim_actions_client_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY claim_actions
    ADD CONSTRAINT claim_actions_client_id_fkey FOREIGN KEY (client_id) REFERENCES clients(id) ON DELETE CASCADE;


--
-- Name: claim_actions claim_actions_purchase_order_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY claim_actions
    ADD CONSTRAINT claim_actions_purchase_order_id_fkey FOREIGN KEY (purchase_order_id) REFERENCES purchase_orders(id);


--
-- Name: claim_actions claim_actions_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY claim_actions
    ADD CONSTRAINT claim_actions_user_id_fkey FOREIGN KEY (user_id) REFERENCES users(id);


--
-- Name: client_branding client_branding_client_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY client_branding
    ADD CONSTRAINT client_branding_client_id_fkey FOREIGN KEY (client_id) REFERENCES clients(id) ON DELETE CASCADE;


--
-- Name: client_feature_overrides client_feature_overrides_client_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY client_feature_overrides
    ADD CONSTRAINT client_feature_overrides_client_id_fkey FOREIGN KEY (client_id) REFERENCES clients(id) ON DELETE CASCADE;


--
-- Name: client_feature_overrides client_feature_overrides_feature_flag_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY client_feature_overrides
    ADD CONSTRAINT client_feature_overrides_feature_flag_id_fkey FOREIGN KEY (feature_flag_id) REFERENCES feature_flags(id) ON DELETE CASCADE;


--
-- Name: client_notification_role_configs client_notification_role_configs_client_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY client_notification_role_configs
    ADD CONSTRAINT client_notification_role_configs_client_id_fkey FOREIGN KEY (client_id) REFERENCES clients(id) ON DELETE CASCADE;


--
-- Name: client_notification_role_configs client_notification_role_configs_notification_type_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY client_notification_role_configs
    ADD CONSTRAINT client_notification_role_configs_notification_type_id_fkey FOREIGN KEY (notification_type_id) REFERENCES notification_types(id) ON DELETE CASCADE;


--
-- Name: client_permission_grants client_permission_grants_client_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY client_permission_grants
    ADD CONSTRAINT client_permission_grants_client_id_fkey FOREIGN KEY (client_id) REFERENCES clients(id) ON DELETE CASCADE;


--
-- Name: client_role_permissions client_role_permissions_client_role_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY client_role_permissions
    ADD CONSTRAINT client_role_permissions_client_role_id_fkey FOREIGN KEY (client_role_id) REFERENCES client_roles(id) ON DELETE CASCADE;


--
-- Name: client_roles client_roles_client_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY client_roles
    ADD CONSTRAINT client_roles_client_id_fkey FOREIGN KEY (client_id) REFERENCES clients(id) ON DELETE CASCADE;


--
-- Name: client_roles client_roles_home_dashboard_template_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY client_roles
    ADD CONSTRAINT client_roles_home_dashboard_template_id_fkey FOREIGN KEY (home_dashboard_template_id) REFERENCES home_dashboard_templates(id) ON DELETE SET NULL;


--
-- Name: home_dashboard_templates home_dashboard_templates_client_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY home_dashboard_templates
    ADD CONSTRAINT home_dashboard_templates_client_id_fkey FOREIGN KEY (client_id) REFERENCES clients(id) ON DELETE CASCADE;


--
-- Name: company_permission_overrides company_permission_overrides_client_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY company_permission_overrides
    ADD CONSTRAINT company_permission_overrides_client_id_fkey FOREIGN KEY (client_id) REFERENCES clients(id) ON DELETE CASCADE;


--
-- Name: company_permission_overrides company_permission_overrides_partner_company_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY company_permission_overrides
    ADD CONSTRAINT company_permission_overrides_partner_company_id_fkey FOREIGN KEY (partner_company_id) REFERENCES partner_companies(id) ON DELETE CASCADE;


--
-- Name: compliance_alerts compliance_alerts_client_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY compliance_alerts
    ADD CONSTRAINT compliance_alerts_client_id_fkey FOREIGN KEY (client_id) REFERENCES clients(id) ON DELETE CASCADE;


--
-- Name: compliance_alerts compliance_alerts_incentive_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY compliance_alerts
    ADD CONSTRAINT compliance_alerts_incentive_id_fkey FOREIGN KEY (incentive_id) REFERENCES incentives(id);


--
-- Name: compliance_alerts compliance_alerts_partner_company_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY compliance_alerts
    ADD CONSTRAINT compliance_alerts_partner_company_id_fkey FOREIGN KEY (partner_company_id) REFERENCES partner_companies(id);


--
-- Name: compliance_alerts compliance_alerts_resolved_by_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY compliance_alerts
    ADD CONSTRAINT compliance_alerts_resolved_by_fkey FOREIGN KEY (resolved_by) REFERENCES users(id);


--
-- Name: compliance_alerts compliance_alerts_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY compliance_alerts
    ADD CONSTRAINT compliance_alerts_user_id_fkey FOREIGN KEY (user_id) REFERENCES users(id);


--
-- Name: compliance_value_caps compliance_value_caps_client_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY compliance_value_caps
    ADD CONSTRAINT compliance_value_caps_client_id_fkey FOREIGN KEY (client_id) REFERENCES clients(id) ON DELETE CASCADE;


--
-- Name: connector_field_mappings connector_field_mappings_connector_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY connector_field_mappings
    ADD CONSTRAINT connector_field_mappings_connector_id_fkey FOREIGN KEY (connector_id) REFERENCES connectors(id) ON DELETE CASCADE;


--
-- Name: connector_field_mappings connector_field_mappings_data_object_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY connector_field_mappings
    ADD CONSTRAINT connector_field_mappings_data_object_id_fkey FOREIGN KEY (data_object_id) REFERENCES data_objects(id) ON DELETE CASCADE;


--
-- Name: connector_field_mappings connector_field_mappings_field_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY connector_field_mappings
    ADD CONSTRAINT connector_field_mappings_field_id_fkey FOREIGN KEY (field_id) REFERENCES data_object_fields(id) ON DELETE CASCADE;


--
-- Name: connectors connectors_client_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY connectors
    ADD CONSTRAINT connectors_client_id_fkey FOREIGN KEY (client_id) REFERENCES clients(id) ON DELETE CASCADE;


--
-- Name: consent_records consent_records_client_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY consent_records
    ADD CONSTRAINT consent_records_client_id_fkey FOREIGN KEY (client_id) REFERENCES clients(id) ON DELETE CASCADE;


--
-- Name: consent_records consent_records_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY consent_records
    ADD CONSTRAINT consent_records_user_id_fkey FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE;


--
-- Name: course_product_mappings course_product_mappings_course_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY course_product_mappings
    ADD CONSTRAINT course_product_mappings_course_id_fkey FOREIGN KEY (course_id) REFERENCES lms_courses(id) ON DELETE CASCADE;


--
-- Name: currencies currencies_client_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY currencies
    ADD CONSTRAINT currencies_client_id_fkey FOREIGN KEY (client_id) REFERENCES clients(id) ON DELETE CASCADE;


--
-- Name: data_object_fields data_object_fields_data_object_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY data_object_fields
    ADD CONSTRAINT data_object_fields_data_object_id_fkey FOREIGN KEY (data_object_id) REFERENCES data_objects(id) ON DELETE CASCADE;


--
-- Name: data_objects data_objects_client_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY data_objects
    ADD CONSTRAINT data_objects_client_id_fkey FOREIGN KEY (client_id) REFERENCES clients(id) ON DELETE CASCADE;


--
-- Name: data_uploads data_uploads_client_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY data_uploads
    ADD CONSTRAINT data_uploads_client_id_fkey FOREIGN KEY (client_id) REFERENCES clients(id) ON DELETE CASCADE;


--
-- Name: data_uploads data_uploads_data_object_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY data_uploads
    ADD CONSTRAINT data_uploads_data_object_id_fkey FOREIGN KEY (data_object_id) REFERENCES data_objects(id);


--
-- Name: eligibility_payouts eligibility_payouts_eligibility_mapping_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY eligibility_payouts
    ADD CONSTRAINT eligibility_payouts_eligibility_mapping_id_fkey FOREIGN KEY (eligibility_mapping_id) REFERENCES po_eligibility_mappings(id) ON DELETE CASCADE;


--
-- Name: eligibility_payouts eligibility_payouts_requirement_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY eligibility_payouts
    ADD CONSTRAINT eligibility_payouts_requirement_id_fkey FOREIGN KEY (requirement_id) REFERENCES sales_requirements(id);


--
-- Name: eligibility_rule_groups eligibility_rule_groups_requirement_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY eligibility_rule_groups
    ADD CONSTRAINT eligibility_rule_groups_requirement_id_fkey FOREIGN KEY (requirement_id) REFERENCES sales_requirements(id) ON DELETE CASCADE;


--
-- Name: eligibility_rules eligibility_rules_field_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY eligibility_rules
    ADD CONSTRAINT eligibility_rules_field_id_fkey FOREIGN KEY (field_id) REFERENCES data_object_fields(id) ON DELETE SET NULL;


--
-- Name: eligibility_rules eligibility_rules_rule_group_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY eligibility_rules
    ADD CONSTRAINT eligibility_rules_rule_group_id_fkey FOREIGN KEY (rule_group_id) REFERENCES eligibility_rule_groups(id) ON DELETE CASCADE;


--
-- Name: fiscal_year_configs fiscal_year_configs_client_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY fiscal_year_configs
    ADD CONSTRAINT fiscal_year_configs_client_id_fkey FOREIGN KEY (client_id) REFERENCES clients(id) ON DELETE CASCADE;


--
-- Name: products fk_products_client; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY products
    ADD CONSTRAINT fk_products_client FOREIGN KEY (client_id) REFERENCES clients(id) ON DELETE CASCADE;


--
-- Name: users fk_users_client; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY users
    ADD CONSTRAINT fk_users_client FOREIGN KEY (client_id) REFERENCES clients(id) ON DELETE CASCADE;


--
-- Name: users fk_users_partner_company; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY users
    ADD CONSTRAINT fk_users_partner_company FOREIGN KEY (partner_company_id) REFERENCES partner_companies(id);


--
-- Name: forecast_accuracy_records forecast_accuracy_records_client_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY forecast_accuracy_records
    ADD CONSTRAINT forecast_accuracy_records_client_id_fkey FOREIGN KEY (client_id) REFERENCES clients(id) ON DELETE CASCADE;


--
-- Name: forecast_accuracy_records forecast_accuracy_records_forecast_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY forecast_accuracy_records
    ADD CONSTRAINT forecast_accuracy_records_forecast_id_fkey FOREIGN KEY (forecast_id) REFERENCES incentive_forecasts(id) ON DELETE CASCADE;


--
-- Name: forecast_accuracy_records forecast_accuracy_records_incentive_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY forecast_accuracy_records
    ADD CONSTRAINT forecast_accuracy_records_incentive_id_fkey FOREIGN KEY (incentive_id) REFERENCES incentives(id) ON DELETE CASCADE;


--
-- Name: forecast_incentive_outcomes forecast_incentive_outcomes_client_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY forecast_incentive_outcomes
    ADD CONSTRAINT forecast_incentive_outcomes_client_id_fkey FOREIGN KEY (client_id) REFERENCES clients(id) ON DELETE CASCADE;


--
-- Name: forecast_incentive_outcomes forecast_incentive_outcomes_incentive_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY forecast_incentive_outcomes
    ADD CONSTRAINT forecast_incentive_outcomes_incentive_id_fkey FOREIGN KEY (incentive_id) REFERENCES incentives(id) ON DELETE CASCADE;


--
-- Name: forecast_region_distributions forecast_region_distributions_client_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY forecast_region_distributions
    ADD CONSTRAINT forecast_region_distributions_client_id_fkey FOREIGN KEY (client_id) REFERENCES clients(id) ON DELETE CASCADE;


--
-- Name: forecast_region_distributions forecast_region_distributions_location_value_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY forecast_region_distributions
    ADD CONSTRAINT forecast_region_distributions_location_value_id_fkey FOREIGN KEY (location_value_id) REFERENCES location_values(id);


--
-- Name: forecast_sales_aggregates forecast_sales_aggregates_client_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY forecast_sales_aggregates
    ADD CONSTRAINT forecast_sales_aggregates_client_id_fkey FOREIGN KEY (client_id) REFERENCES clients(id) ON DELETE CASCADE;


--
-- Name: forecast_sales_aggregates forecast_sales_aggregates_location_value_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY forecast_sales_aggregates
    ADD CONSTRAINT forecast_sales_aggregates_location_value_id_fkey FOREIGN KEY (location_value_id) REFERENCES location_values(id);


--
-- Name: forecast_training_correlations forecast_training_correlations_client_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY forecast_training_correlations
    ADD CONSTRAINT forecast_training_correlations_client_id_fkey FOREIGN KEY (client_id) REFERENCES clients(id) ON DELETE CASCADE;


--
-- Name: government_segment_config government_segment_config_client_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY government_segment_config
    ADD CONSTRAINT government_segment_config_client_id_fkey FOREIGN KEY (client_id) REFERENCES clients(id) ON DELETE CASCADE;


--
-- Name: incentive_approvers incentive_approvers_incentive_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY incentive_approvers
    ADD CONSTRAINT incentive_approvers_incentive_id_fkey FOREIGN KEY (incentive_id) REFERENCES incentives(id) ON DELETE CASCADE;


--
-- Name: incentive_audience_rules incentive_audience_rules_incentive_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY incentive_audience_rules
    ADD CONSTRAINT incentive_audience_rules_incentive_id_fkey FOREIGN KEY (incentive_id) REFERENCES incentives(id) ON DELETE CASCADE;


--
-- Name: incentive_audience_rules incentive_audience_rules_location_level_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY incentive_audience_rules
    ADD CONSTRAINT incentive_audience_rules_location_level_id_fkey FOREIGN KEY (location_level_id) REFERENCES location_levels(id);


--
-- Name: incentive_budgets incentive_budgets_budget_location_level_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY incentive_budgets
    ADD CONSTRAINT incentive_budgets_budget_location_level_id_fkey FOREIGN KEY (budget_location_level_id) REFERENCES location_levels(id);


--
-- Name: incentive_budgets incentive_budgets_incentive_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY incentive_budgets
    ADD CONSTRAINT incentive_budgets_incentive_id_fkey FOREIGN KEY (incentive_id) REFERENCES incentives(id) ON DELETE CASCADE;


--
-- Name: incentive_documents incentive_documents_incentive_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY incentive_documents
    ADD CONSTRAINT incentive_documents_incentive_id_fkey FOREIGN KEY (incentive_id) REFERENCES incentives(id) ON DELETE CASCADE;


--
-- Name: incentive_forecasts incentive_forecasts_incentive_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY incentive_forecasts
    ADD CONSTRAINT incentive_forecasts_incentive_id_fkey FOREIGN KEY (incentive_id) REFERENCES incentives(id) ON DELETE CASCADE;


--
-- Name: incentives incentives_client_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY incentives
    ADD CONSTRAINT incentives_client_id_fkey FOREIGN KEY (client_id) REFERENCES clients(id) ON DELETE CASCADE;


--
-- Name: incentives incentives_created_by_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY incentives
    ADD CONSTRAINT incentives_created_by_fkey FOREIGN KEY (created_by) REFERENCES users(id);


--
-- Name: journey_stages journey_stages_incentive_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY journey_stages
    ADD CONSTRAINT journey_stages_incentive_id_fkey FOREIGN KEY (incentive_id) REFERENCES incentives(id) ON DELETE CASCADE;


--
-- Name: journey_stages journey_stages_linked_incentive_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY journey_stages
    ADD CONSTRAINT journey_stages_linked_incentive_id_fkey FOREIGN KEY (linked_incentive_id) REFERENCES incentives(id);


--
-- Name: legal_policies legal_policies_client_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY legal_policies
    ADD CONSTRAINT legal_policies_client_id_fkey FOREIGN KEY (client_id) REFERENCES clients(id) ON DELETE CASCADE;


--
-- Name: location_budget_allocations location_budget_allocations_budget_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY location_budget_allocations
    ADD CONSTRAINT location_budget_allocations_budget_id_fkey FOREIGN KEY (budget_id) REFERENCES incentive_budgets(id) ON DELETE CASCADE;


--
-- Name: location_budget_allocations location_budget_allocations_location_value_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY location_budget_allocations
    ADD CONSTRAINT location_budget_allocations_location_value_id_fkey FOREIGN KEY (location_value_id) REFERENCES location_values(id);


--
-- Name: location_levels location_levels_client_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY location_levels
    ADD CONSTRAINT location_levels_client_id_fkey FOREIGN KEY (client_id) REFERENCES clients(id) ON DELETE CASCADE;


--
-- Name: location_values location_values_client_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY location_values
    ADD CONSTRAINT location_values_client_id_fkey FOREIGN KEY (client_id) REFERENCES clients(id) ON DELETE CASCADE;


--
-- Name: location_values location_values_level_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY location_values
    ADD CONSTRAINT location_values_level_id_fkey FOREIGN KEY (level_id) REFERENCES location_levels(id) ON DELETE CASCADE;


--
-- Name: location_values location_values_parent_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY location_values
    ADD CONSTRAINT location_values_parent_id_fkey FOREIGN KEY (parent_id) REFERENCES location_values(id) ON DELETE CASCADE;


--
-- Name: notifications notifications_client_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY notifications
    ADD CONSTRAINT notifications_client_id_fkey FOREIGN KEY (client_id) REFERENCES clients(id) ON DELETE CASCADE;


--
-- Name: notifications notifications_notification_type_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY notifications
    ADD CONSTRAINT notifications_notification_type_id_fkey FOREIGN KEY (notification_type_id) REFERENCES notification_types(id);


--
-- Name: notifications notifications_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY notifications
    ADD CONSTRAINT notifications_user_id_fkey FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE;


--
-- Name: onboarding_tokens onboarding_tokens_client_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY onboarding_tokens
    ADD CONSTRAINT onboarding_tokens_client_id_fkey FOREIGN KEY (client_id) REFERENCES clients(id) ON DELETE CASCADE;


--
-- Name: onboarding_tokens onboarding_tokens_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY onboarding_tokens
    ADD CONSTRAINT onboarding_tokens_user_id_fkey FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE;


--
-- Name: partner_beneficial_owners partner_beneficial_owners_kyc_record_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY partner_beneficial_owners
    ADD CONSTRAINT partner_beneficial_owners_kyc_record_id_fkey FOREIGN KEY (kyc_record_id) REFERENCES partner_kyc_records(id) ON DELETE CASCADE;


--
-- Name: partner_companies partner_companies_client_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY partner_companies
    ADD CONSTRAINT partner_companies_client_id_fkey FOREIGN KEY (client_id) REFERENCES clients(id) ON DELETE CASCADE;


--
-- Name: partner_company_locations partner_company_locations_client_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY partner_company_locations
    ADD CONSTRAINT partner_company_locations_client_id_fkey FOREIGN KEY (client_id) REFERENCES clients(id);


--
-- Name: partner_company_locations partner_company_locations_location_value_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY partner_company_locations
    ADD CONSTRAINT partner_company_locations_location_value_id_fkey FOREIGN KEY (location_value_id) REFERENCES location_values(id) ON DELETE CASCADE;


--
-- Name: partner_company_locations partner_company_locations_partner_company_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY partner_company_locations
    ADD CONSTRAINT partner_company_locations_partner_company_id_fkey FOREIGN KEY (partner_company_id) REFERENCES partner_companies(id) ON DELETE CASCADE;


--
-- Name: partner_kyc_records partner_kyc_records_approved_by_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY partner_kyc_records
    ADD CONSTRAINT partner_kyc_records_approved_by_fkey FOREIGN KEY (approved_by) REFERENCES users(id);


--
-- Name: partner_kyc_records partner_kyc_records_client_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY partner_kyc_records
    ADD CONSTRAINT partner_kyc_records_client_id_fkey FOREIGN KEY (client_id) REFERENCES clients(id) ON DELETE CASCADE;


--
-- Name: partner_kyc_records partner_kyc_records_partner_company_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY partner_kyc_records
    ADD CONSTRAINT partner_kyc_records_partner_company_id_fkey FOREIGN KEY (partner_company_id) REFERENCES partner_companies(id) ON DELETE CASCADE;


--
-- Name: partner_program_acknowledgments partner_program_acknowledgments_acknowledged_by_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY partner_program_acknowledgments
    ADD CONSTRAINT partner_program_acknowledgments_acknowledged_by_fkey FOREIGN KEY (acknowledged_by) REFERENCES users(id);


--
-- Name: partner_program_acknowledgments partner_program_acknowledgments_client_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY partner_program_acknowledgments
    ADD CONSTRAINT partner_program_acknowledgments_client_id_fkey FOREIGN KEY (client_id) REFERENCES clients(id) ON DELETE CASCADE;


--
-- Name: partner_program_acknowledgments partner_program_acknowledgments_incentive_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY partner_program_acknowledgments
    ADD CONSTRAINT partner_program_acknowledgments_incentive_id_fkey FOREIGN KEY (incentive_id) REFERENCES incentives(id) ON DELETE CASCADE;


--
-- Name: partner_program_acknowledgments partner_program_acknowledgments_partner_company_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY partner_program_acknowledgments
    ADD CONSTRAINT partner_program_acknowledgments_partner_company_id_fkey FOREIGN KEY (partner_company_id) REFERENCES partner_companies(id) ON DELETE CASCADE;


--
-- Name: payout_bands payout_bands_payout_config_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY payout_bands
    ADD CONSTRAINT payout_bands_payout_config_id_fkey FOREIGN KEY (payout_config_id) REFERENCES payout_configs(id) ON DELETE CASCADE;


--
-- Name: payout_configs payout_configs_requirement_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY payout_configs
    ADD CONSTRAINT payout_configs_requirement_id_fkey FOREIGN KEY (requirement_id) REFERENCES sales_requirements(id) ON DELETE CASCADE;


--
-- Name: po_eligibility_mappings po_eligibility_mappings_client_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY po_eligibility_mappings
    ADD CONSTRAINT po_eligibility_mappings_client_id_fkey FOREIGN KEY (client_id) REFERENCES clients(id) ON DELETE CASCADE;


--
-- Name: po_eligibility_mappings po_eligibility_mappings_incentive_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY po_eligibility_mappings
    ADD CONSTRAINT po_eligibility_mappings_incentive_id_fkey FOREIGN KEY (incentive_id) REFERENCES incentives(id);


--
-- Name: po_eligibility_mappings po_eligibility_mappings_purchase_order_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY po_eligibility_mappings
    ADD CONSTRAINT po_eligibility_mappings_purchase_order_id_fkey FOREIGN KEY (purchase_order_id) REFERENCES purchase_orders(id);


--
-- Name: po_eligibility_mappings po_eligibility_mappings_tagging_job_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY po_eligibility_mappings
    ADD CONSTRAINT po_eligibility_mappings_tagging_job_id_fkey FOREIGN KEY (tagging_job_id) REFERENCES tagging_jobs(id) ON DELETE CASCADE;


--
-- Name: purchase_order_lines purchase_order_lines_product_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY purchase_order_lines
    ADD CONSTRAINT purchase_order_lines_product_id_fkey FOREIGN KEY (product_id) REFERENCES products(id);


--
-- Name: purchase_order_lines purchase_order_lines_purchase_order_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY purchase_order_lines
    ADD CONSTRAINT purchase_order_lines_purchase_order_id_fkey FOREIGN KEY (purchase_order_id) REFERENCES purchase_orders(id) ON DELETE CASCADE;


--
-- Name: purchase_orders purchase_orders_client_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY purchase_orders
    ADD CONSTRAINT purchase_orders_client_id_fkey FOREIGN KEY (client_id) REFERENCES clients(id) ON DELETE CASCADE;


--
-- Name: purchase_orders purchase_orders_partner_company_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY purchase_orders
    ADD CONSTRAINT purchase_orders_partner_company_id_fkey FOREIGN KEY (partner_company_id) REFERENCES partner_companies(id) ON DELETE CASCADE;


--
-- Name: recommendation_configs recommendation_configs_client_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY recommendation_configs
    ADD CONSTRAINT recommendation_configs_client_id_fkey FOREIGN KEY (client_id) REFERENCES clients(id) ON DELETE CASCADE;


--
-- Name: recommendation_interactions recommendation_interactions_client_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY recommendation_interactions
    ADD CONSTRAINT recommendation_interactions_client_id_fkey FOREIGN KEY (client_id) REFERENCES clients(id) ON DELETE CASCADE;


--
-- Name: recommendation_interactions recommendation_interactions_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY recommendation_interactions
    ADD CONSTRAINT recommendation_interactions_user_id_fkey FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE;


--
-- Name: recommendation_scores recommendation_scores_client_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY recommendation_scores
    ADD CONSTRAINT recommendation_scores_client_id_fkey FOREIGN KEY (client_id) REFERENCES clients(id) ON DELETE CASCADE;


--
-- Name: recommendation_scores recommendation_scores_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY recommendation_scores
    ADD CONSTRAINT recommendation_scores_user_id_fkey FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE;


--
-- Name: retention_policies retention_policies_client_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY retention_policies
    ADD CONSTRAINT retention_policies_client_id_fkey FOREIGN KEY (client_id) REFERENCES clients(id) ON DELETE CASCADE;


--
-- Name: reward_balances reward_balances_client_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY reward_balances
    ADD CONSTRAINT reward_balances_client_id_fkey FOREIGN KEY (client_id) REFERENCES clients(id) ON DELETE CASCADE;


--
-- Name: reward_balances reward_balances_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY reward_balances
    ADD CONSTRAINT reward_balances_user_id_fkey FOREIGN KEY (user_id) REFERENCES users(id);


--
-- Name: reward_transactions reward_transactions_claim_action_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY reward_transactions
    ADD CONSTRAINT reward_transactions_claim_action_id_fkey FOREIGN KEY (claim_action_id) REFERENCES claim_actions(id);


--
-- Name: reward_transactions reward_transactions_client_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY reward_transactions
    ADD CONSTRAINT reward_transactions_client_id_fkey FOREIGN KEY (client_id) REFERENCES clients(id) ON DELETE CASCADE;


--
-- Name: reward_transactions reward_transactions_completion_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY reward_transactions
    ADD CONSTRAINT reward_transactions_completion_id_fkey FOREIGN KEY (completion_id) REFERENCES user_incentive_completions(id);


--
-- Name: reward_transactions reward_transactions_incentive_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY reward_transactions
    ADD CONSTRAINT reward_transactions_incentive_id_fkey FOREIGN KEY (incentive_id) REFERENCES incentives(id);


--
-- Name: reward_transactions reward_transactions_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY reward_transactions
    ADD CONSTRAINT reward_transactions_user_id_fkey FOREIGN KEY (user_id) REFERENCES users(id);


--
-- Name: sales_requirements sales_requirements_incentive_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY sales_requirements
    ADD CONSTRAINT sales_requirements_incentive_id_fkey FOREIGN KEY (incentive_id) REFERENCES incentives(id) ON DELETE CASCADE;


--
-- Name: sync_schedules sync_schedules_client_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY sync_schedules
    ADD CONSTRAINT sync_schedules_client_id_fkey FOREIGN KEY (client_id) REFERENCES clients(id) ON DELETE CASCADE;


--
-- Name: sync_schedules sync_schedules_data_object_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY sync_schedules
    ADD CONSTRAINT sync_schedules_data_object_id_fkey FOREIGN KEY (data_object_id) REFERENCES data_objects(id);


--
-- Name: tagging_jobs tagging_jobs_client_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY tagging_jobs
    ADD CONSTRAINT tagging_jobs_client_id_fkey FOREIGN KEY (client_id) REFERENCES clients(id) ON DELETE CASCADE;


--
-- Name: training_course_assignments training_course_assignments_incentive_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY training_course_assignments
    ADD CONSTRAINT training_course_assignments_incentive_id_fkey FOREIGN KEY (incentive_id) REFERENCES incentives(id) ON DELETE CASCADE;


--
-- Name: user_activity_document_submissions user_activity_document_submissions_activity_definition_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY user_activity_document_submissions
    ADD CONSTRAINT user_activity_document_submissions_activity_definition_id_fkey FOREIGN KEY (activity_definition_id) REFERENCES activity_definitions(id) ON DELETE CASCADE;


--
-- Name: user_activity_document_submissions user_activity_document_submissions_client_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY user_activity_document_submissions
    ADD CONSTRAINT user_activity_document_submissions_client_id_fkey FOREIGN KEY (client_id) REFERENCES clients(id);


--
-- Name: user_activity_document_submissions user_activity_document_submissions_document_requirement_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY user_activity_document_submissions
    ADD CONSTRAINT user_activity_document_submissions_document_requirement_id_fkey FOREIGN KEY (document_requirement_id) REFERENCES activity_document_requirements(id) ON DELETE CASCADE;


--
-- Name: user_activity_document_submissions user_activity_document_submissions_reviewed_by_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY user_activity_document_submissions
    ADD CONSTRAINT user_activity_document_submissions_reviewed_by_fkey FOREIGN KEY (reviewed_by) REFERENCES users(id);


--
-- Name: user_activity_document_submissions user_activity_document_submissions_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY user_activity_document_submissions
    ADD CONSTRAINT user_activity_document_submissions_user_id_fkey FOREIGN KEY (user_id) REFERENCES users(id);


--
-- Name: user_activity_progress user_activity_progress_activity_definition_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY user_activity_progress
    ADD CONSTRAINT user_activity_progress_activity_definition_id_fkey FOREIGN KEY (activity_definition_id) REFERENCES activity_definitions(id) ON DELETE CASCADE;


--
-- Name: user_activity_progress user_activity_progress_client_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY user_activity_progress
    ADD CONSTRAINT user_activity_progress_client_id_fkey FOREIGN KEY (client_id) REFERENCES clients(id);


--
-- Name: user_activity_progress user_activity_progress_incentive_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY user_activity_progress
    ADD CONSTRAINT user_activity_progress_incentive_id_fkey FOREIGN KEY (incentive_id) REFERENCES incentives(id) ON DELETE CASCADE;


--
-- Name: user_activity_progress user_activity_progress_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY user_activity_progress
    ADD CONSTRAINT user_activity_progress_user_id_fkey FOREIGN KEY (user_id) REFERENCES users(id);


--
-- Name: user_course_completions user_course_completions_client_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY user_course_completions
    ADD CONSTRAINT user_course_completions_client_id_fkey FOREIGN KEY (client_id) REFERENCES clients(id) ON DELETE CASCADE;


--
-- Name: user_course_completions user_course_completions_course_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY user_course_completions
    ADD CONSTRAINT user_course_completions_course_id_fkey FOREIGN KEY (course_id) REFERENCES lms_courses(id) ON DELETE CASCADE;


--
-- Name: user_course_completions user_course_completions_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY user_course_completions
    ADD CONSTRAINT user_course_completions_user_id_fkey FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE;


--
-- Name: user_incentive_completions user_incentive_completions_client_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY user_incentive_completions
    ADD CONSTRAINT user_incentive_completions_client_id_fkey FOREIGN KEY (client_id) REFERENCES clients(id) ON DELETE CASCADE;


--
-- Name: user_incentive_completions user_incentive_completions_incentive_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY user_incentive_completions
    ADD CONSTRAINT user_incentive_completions_incentive_id_fkey FOREIGN KEY (incentive_id) REFERENCES incentives(id);


--
-- Name: user_incentive_completions user_incentive_completions_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY user_incentive_completions
    ADD CONSTRAINT user_incentive_completions_user_id_fkey FOREIGN KEY (user_id) REFERENCES users(id);


--
-- Name: user_journey_stage_progress user_journey_stage_progress_client_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY user_journey_stage_progress
    ADD CONSTRAINT user_journey_stage_progress_client_id_fkey FOREIGN KEY (client_id) REFERENCES clients(id);


--
-- Name: user_journey_stage_progress user_journey_stage_progress_journey_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY user_journey_stage_progress
    ADD CONSTRAINT user_journey_stage_progress_journey_id_fkey FOREIGN KEY (journey_id) REFERENCES incentives(id) ON DELETE CASCADE;


--
-- Name: user_journey_stage_progress user_journey_stage_progress_linked_incentive_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY user_journey_stage_progress
    ADD CONSTRAINT user_journey_stage_progress_linked_incentive_id_fkey FOREIGN KEY (linked_incentive_id) REFERENCES incentives(id);


--
-- Name: user_journey_stage_progress user_journey_stage_progress_stage_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY user_journey_stage_progress
    ADD CONSTRAINT user_journey_stage_progress_stage_id_fkey FOREIGN KEY (stage_id) REFERENCES journey_stages(id) ON DELETE CASCADE;


--
-- Name: user_journey_stage_progress user_journey_stage_progress_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY user_journey_stage_progress
    ADD CONSTRAINT user_journey_stage_progress_user_id_fkey FOREIGN KEY (user_id) REFERENCES users(id);


--
-- Name: user_legal_acceptances user_legal_acceptances_client_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY user_legal_acceptances
    ADD CONSTRAINT user_legal_acceptances_client_id_fkey FOREIGN KEY (client_id) REFERENCES clients(id) ON DELETE CASCADE;


--
-- Name: user_legal_acceptances user_legal_acceptances_policy_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY user_legal_acceptances
    ADD CONSTRAINT user_legal_acceptances_policy_id_fkey FOREIGN KEY (policy_id) REFERENCES legal_policies(id) ON DELETE CASCADE;


--
-- Name: user_legal_acceptances user_legal_acceptances_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY user_legal_acceptances
    ADD CONSTRAINT user_legal_acceptances_user_id_fkey FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE;


--
-- Name: user_notification_preferences user_notification_preferences_client_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY user_notification_preferences
    ADD CONSTRAINT user_notification_preferences_client_id_fkey FOREIGN KEY (client_id) REFERENCES clients(id) ON DELETE CASCADE;


--
-- Name: user_notification_preferences user_notification_preferences_notification_type_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY user_notification_preferences
    ADD CONSTRAINT user_notification_preferences_notification_type_id_fkey FOREIGN KEY (notification_type_id) REFERENCES notification_types(id) ON DELETE CASCADE;


--
-- Name: user_notification_preferences user_notification_preferences_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY user_notification_preferences
    ADD CONSTRAINT user_notification_preferences_user_id_fkey FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE;


--
-- Name: user_notification_settings user_notification_settings_client_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY user_notification_settings
    ADD CONSTRAINT user_notification_settings_client_id_fkey FOREIGN KEY (client_id) REFERENCES clients(id) ON DELETE CASCADE;


--
-- Name: user_notification_settings user_notification_settings_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY user_notification_settings
    ADD CONSTRAINT user_notification_settings_user_id_fkey FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE;


--
-- Name: user_permission_overrides user_permission_overrides_client_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY user_permission_overrides
    ADD CONSTRAINT user_permission_overrides_client_id_fkey FOREIGN KEY (client_id) REFERENCES clients(id) ON DELETE CASCADE;


--
-- Name: user_permission_overrides user_permission_overrides_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY user_permission_overrides
    ADD CONSTRAINT user_permission_overrides_user_id_fkey FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE;


--
-- Name: users users_client_role_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY users
    ADD CONSTRAINT users_client_role_id_fkey FOREIGN KEY (client_role_id) REFERENCES client_roles(id);


--
-- Name: whistleblower_case_updates whistleblower_case_updates_report_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY whistleblower_case_updates
    ADD CONSTRAINT whistleblower_case_updates_report_id_fkey FOREIGN KEY (report_id) REFERENCES whistleblower_reports(id) ON DELETE CASCADE;


--
-- Name: whistleblower_case_updates whistleblower_case_updates_updated_by_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY whistleblower_case_updates
    ADD CONSTRAINT whistleblower_case_updates_updated_by_fkey FOREIGN KEY (updated_by) REFERENCES users(id);


--
-- Name: whistleblower_reports whistleblower_reports_client_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY whistleblower_reports
    ADD CONSTRAINT whistleblower_reports_client_id_fkey FOREIGN KEY (client_id) REFERENCES clients(id) ON DELETE SET NULL;


--
-- Name: whistleblower_reports whistleblower_reports_resolved_by_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY whistleblower_reports
    ADD CONSTRAINT whistleblower_reports_resolved_by_fkey FOREIGN KEY (resolved_by) REFERENCES users(id);


--
--

package com.tenxengage.app.batch.seed;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Handles cleanup of seed data before a full reseed.
 * Fixed: references to dropped tables (user_roles, contact_email, region).
 */
@Component
public class SeedCleanupService {

    private static final Logger log = LoggerFactory.getLogger(SeedCleanupService.class);

    private final JdbcTemplate jdbc;

    public SeedCleanupService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Full cleanup of all seed-generated data for a client. */
    public void cleanupAllData(UUID clientId) {
        log.info("Cleaning up all seed data for client {}...", clientId);

        // Completions and rewards
        jdbc.update("DELETE FROM user_course_completions WHERE client_id = ?", clientId);
        jdbc.update("DELETE FROM user_incentive_completions WHERE client_id = ?", clientId);
        jdbc.update("DELETE FROM reward_transactions WHERE client_id = ?", clientId);
        jdbc.update("DELETE FROM reward_wallets WHERE client_id = ?", clientId);
        jdbc.update("DELETE FROM claim_actions WHERE client_id = ?", clientId);

        // Eligibility
        jdbc.update("DELETE FROM eligibility_payouts WHERE eligibility_mapping_id IN " +
                "(SELECT id FROM po_eligibility_mappings WHERE client_id = ?)", clientId);
        jdbc.update("DELETE FROM po_eligibility_mappings WHERE client_id = ?", clientId);

        // Budget utilization
        jdbc.update("DELETE FROM budget_utilizations WHERE incentive_id IN " +
                "(SELECT id FROM incentives WHERE client_id = ?)", clientId);
        jdbc.update("DELETE FROM tagging_jobs WHERE client_id = ?", clientId);

        // Payout structure
        jdbc.update("DELETE FROM payout_bands WHERE payout_config_id IN " +
                "(SELECT pc.id FROM payout_configs pc JOIN sales_requirements sr ON pc.requirement_id = sr.id " +
                "JOIN incentives i ON sr.incentive_id = i.id WHERE i.client_id = ?)", clientId);
        jdbc.update("DELETE FROM payout_configs WHERE requirement_id IN " +
                "(SELECT sr.id FROM sales_requirements sr JOIN incentives i ON sr.incentive_id = i.id " +
                "WHERE i.client_id = ?)", clientId);

        // Eligibility rules
        jdbc.update("DELETE FROM eligibility_rules WHERE rule_group_id IN " +
                "(SELECT erg.id FROM eligibility_rule_groups erg JOIN sales_requirements sr " +
                "ON erg.requirement_id = sr.id JOIN incentives i ON sr.incentive_id = i.id " +
                "WHERE i.client_id = ?)", clientId);
        jdbc.update("DELETE FROM eligibility_rule_groups WHERE requirement_id IN " +
                "(SELECT sr.id FROM sales_requirements sr JOIN incentives i ON sr.incentive_id = i.id " +
                "WHERE i.client_id = ?)", clientId);
        jdbc.update("DELETE FROM sales_requirements WHERE incentive_id IN " +
                "(SELECT id FROM incentives WHERE client_id = ?)", clientId);

        // Incentive configuration
        jdbc.update("DELETE FROM incentive_audience_rules WHERE incentive_id IN " +
                "(SELECT id FROM incentives WHERE client_id = ?)", clientId);
        jdbc.update("DELETE FROM incentive_documents WHERE incentive_id IN " +
                "(SELECT id FROM incentives WHERE client_id = ?)", clientId);
        // Location budget allocations
        jdbc.update("DELETE FROM location_budget_allocations WHERE budget_id IN " +
                "(SELECT ib.id FROM incentive_budgets ib JOIN incentives i ON ib.incentive_id = i.id " +
                "WHERE i.client_id = ?)", clientId);
        jdbc.update("DELETE FROM incentive_budgets WHERE incentive_id IN " +
                "(SELECT id FROM incentives WHERE client_id = ?)", clientId);

        // Journey stages
        jdbc.update("DELETE FROM journey_stages WHERE incentive_id IN " +
                "(SELECT id FROM incentives WHERE client_id = ?)", clientId);
        jdbc.update("DELETE FROM journey_stages WHERE linked_incentive_id IN " +
                "(SELECT id FROM incentives WHERE client_id = ?)", clientId);

        // Activity data
        jdbc.update("DELETE FROM activity_document_requirements WHERE activity_definition_id IN " +
                "(SELECT ad.id FROM activity_definitions ad JOIN incentives i ON ad.incentive_id = i.id " +
                "WHERE i.client_id = ?)", clientId);
        jdbc.update("DELETE FROM activity_definitions WHERE incentive_id IN " +
                "(SELECT id FROM incentives WHERE client_id = ?)", clientId);

        // Training data
        jdbc.update("DELETE FROM training_course_assignments WHERE incentive_id IN " +
                "(SELECT id FROM incentives WHERE client_id = ?)", clientId);

        // Approval workflow
        jdbc.update("DELETE FROM incentive_approvers WHERE incentive_id IN " +
                "(SELECT id FROM incentives WHERE client_id = ?)", clientId);
        jdbc.update("DELETE FROM approval_decisions WHERE incentive_id IN " +
                "(SELECT id FROM incentives WHERE client_id = ?)", clientId);

        // Forecasts
        jdbc.update("DELETE FROM incentive_forecasts WHERE incentive_id IN " +
                "(SELECT id FROM incentives WHERE client_id = ?)", clientId);

        // Incentives themselves
        jdbc.update("DELETE FROM incentives WHERE client_id = ?", clientId);

        // Purchase orders
        jdbc.update("DELETE FROM purchase_order_lines WHERE purchase_order_id IN " +
                "(SELECT id FROM purchase_orders WHERE client_id = ?)", clientId);
        jdbc.update("DELETE FROM purchase_orders WHERE client_id = ?", clientId);

        // Users (seed-generated only: @example.com emails)
        jdbc.update("DELETE FROM users WHERE client_id = ? AND email LIKE '%@example.com'", clientId);

        // Partner locations for seed-generated partners (before deleting partners).
        // Contact email lives in metadata->>'Contact Email' (no dedicated column).
        // BUG-018 FIX: mirror the user-reference guard used below so V3-baseline
        // partners (e.g. TechPartners Inc, referenced by seeded login users) keep
        // their location assignments across reseeds.
        jdbc.update("DELETE FROM partner_company_locations WHERE partner_company_id IN " +
                "(SELECT id FROM partner_companies WHERE client_id = ? " +
                "AND metadata->>'Contact Email' LIKE '%.example.com' " +
                "AND id NOT IN (SELECT DISTINCT partner_company_id FROM users " +
                "WHERE partner_company_id IS NOT NULL))", clientId);
        jdbc.update("DELETE FROM partner_companies WHERE client_id = ? " +
                "AND metadata->>'Contact Email' LIKE '%.example.com' " +
                "AND id NOT IN (SELECT DISTINCT partner_company_id FROM users WHERE partner_company_id IS NOT NULL)",
                clientId);

        // Products
        jdbc.update("DELETE FROM products WHERE client_id = ?", clientId);

        // Seed state
        jdbc.update("DELETE FROM seed_state WHERE client_id = ?", clientId);

        log.info("Cleanup complete for client {}", clientId);
    }
}

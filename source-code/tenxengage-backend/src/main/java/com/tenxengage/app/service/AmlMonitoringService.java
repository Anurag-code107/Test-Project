package com.tenxengage.app.service;

import com.tenxengage.app.entity.enums.ComplianceAlertType;
import com.tenxengage.app.repository.ClientRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Anti-Money Laundering monitoring service.
 * Runs daily anomaly detection checks across all clients and creates ComplianceAlerts
 * when suspicious patterns are detected.
 *
 * Anomaly checks:
 * - Concentration: >50% of incentive rewards going to a single user or partner
 * - Proportionality: individual reward exceeds 20% of deal value
 */
@Service
public class AmlMonitoringService {

    private static final Logger log = LoggerFactory.getLogger(AmlMonitoringService.class);

    private static final BigDecimal CONCENTRATION_THRESHOLD = new BigDecimal("0.50");
    private static final BigDecimal PROPORTIONALITY_THRESHOLD = new BigDecimal("0.20");

    private final JdbcTemplate jdbcTemplate;
    private final ComplianceAlertService complianceAlertService;
    private final ClientRepository clientRepository;

    public AmlMonitoringService(JdbcTemplate jdbcTemplate,
                                 ComplianceAlertService complianceAlertService,
                                 ClientRepository clientRepository) {
        this.jdbcTemplate = jdbcTemplate;
        this.complianceAlertService = complianceAlertService;
        this.clientRepository = clientRepository;
    }

    /**
     * Scheduled daily at 3 AM to detect anomalies across all clients.
     */
    @Scheduled(cron = "0 0 3 * * *")
    @Transactional
    public void runScheduledAnomalyDetection() {
        log.info("Starting scheduled AML anomaly detection");
        List<UUID> clientIds = clientRepository.findAll().stream()
                .map(client -> client.getId())
                .toList();

        int totalAlerts = 0;
        for (UUID clientId : clientIds) {
            totalAlerts += detectAnomalies(clientId);
        }

        log.info("AML anomaly detection complete: checked {} clients, created {} alerts",
                clientIds.size(), totalAlerts);
    }

    /**
     * Runs anomaly detection checks for a specific client.
     *
     * @param clientId the client to check
     * @return number of alerts created
     */
    @Transactional
    public int detectAnomalies(UUID clientId) {
        log.debug("Running AML anomaly detection for clientId={}", clientId);
        int alertCount = 0;
        alertCount += detectConcentrationAnomalies(clientId);
        alertCount += detectProportionalityAnomalies(clientId);

        if (alertCount > 0) {
            log.warn("AML anomaly detection: clientId={}, alertsCreated={}", clientId, alertCount);
        }
        return alertCount;
    }

    /**
     * Detects concentration anomalies: a single user or partner receiving >50%
     * of an incentive's total rewards.
     */
    private int detectConcentrationAnomalies(UUID clientId) {
        // Check user-level concentration per incentive
        String userConcentrationSql = """
            SELECT rt.incentive_id, rt.user_id,
                   SUM(rt.amount_awarded) as user_total,
                   (SELECT SUM(rt2.amount_awarded)
                    FROM reward_transactions rt2
                    WHERE rt2.client_id = rt.client_id
                      AND rt2.incentive_id = rt.incentive_id) as incentive_total
            FROM reward_transactions rt
            WHERE rt.client_id = ?
            GROUP BY rt.client_id, rt.incentive_id, rt.user_id
            HAVING SUM(rt.amount_awarded) > 0
            """;

        List<Map<String, Object>> userRows = jdbcTemplate.queryForList(
                userConcentrationSql, clientId);

        int alertCount = 0;

        for (Map<String, Object> row : userRows) {
            BigDecimal userTotal = (BigDecimal) row.get("user_total");
            BigDecimal incentiveTotal = (BigDecimal) row.get("incentive_total");

            if (incentiveTotal.compareTo(BigDecimal.ZERO) == 0) {
                continue;
            }

            BigDecimal ratio = userTotal.divide(incentiveTotal, 4, RoundingMode.HALF_UP);

            if (ratio.compareTo(CONCENTRATION_THRESHOLD) > 0) {
                UUID incentiveId = (UUID) row.get("incentive_id");
                UUID userId = (UUID) row.get("user_id");

                String description = String.format(
                        "Concentration alert: User %s received %.1f%% of total rewards "
                                + "for incentive %s (threshold: 50%%)",
                        userId, ratio.multiply(BigDecimal.valueOf(100)).doubleValue(), incentiveId);

                complianceAlertService.createAlert(
                        clientId,
                        ComplianceAlertType.CONCENTRATION_ALERT,
                        "HIGH",
                        description,
                        userId,
                        null,
                        incentiveId);
                alertCount++;
            }
        }

        // Check partner-level concentration per incentive
        String partnerConcentrationSql = """
            SELECT rt.incentive_id,
                   po.partner_company_id,
                   SUM(rt.amount_awarded) as partner_total,
                   (SELECT SUM(rt2.amount_awarded)
                    FROM reward_transactions rt2
                    WHERE rt2.client_id = rt.client_id
                      AND rt2.incentive_id = rt.incentive_id) as incentive_total
            FROM reward_transactions rt
            JOIN claim_actions ca ON ca.id = rt.claim_action_id
            JOIN purchase_orders po ON po.id = ca.purchase_order_id
            WHERE rt.client_id = ?
              AND po.partner_company_id IS NOT NULL
            GROUP BY rt.client_id, rt.incentive_id, po.partner_company_id
            HAVING SUM(rt.amount_awarded) > 0
            """;

        List<Map<String, Object>> partnerRows = jdbcTemplate.queryForList(
                partnerConcentrationSql, clientId);

        for (Map<String, Object> row : partnerRows) {
            BigDecimal partnerTotal = (BigDecimal) row.get("partner_total");
            BigDecimal incentiveTotal = (BigDecimal) row.get("incentive_total");

            if (incentiveTotal.compareTo(BigDecimal.ZERO) == 0) {
                continue;
            }

            BigDecimal ratio = partnerTotal.divide(incentiveTotal, 4, RoundingMode.HALF_UP);

            if (ratio.compareTo(CONCENTRATION_THRESHOLD) > 0) {
                UUID incentiveId = (UUID) row.get("incentive_id");
                UUID partnerId = (UUID) row.get("partner_company_id");

                String description = String.format(
                        "Concentration alert: Partner %s received %.1f%% of total rewards "
                                + "for incentive %s (threshold: 50%%)",
                        partnerId, ratio.multiply(BigDecimal.valueOf(100)).doubleValue(),
                        incentiveId);

                complianceAlertService.createAlert(
                        clientId,
                        ComplianceAlertType.CONCENTRATION_ALERT,
                        "HIGH",
                        description,
                        null,
                        partnerId,
                        incentiveId);
                alertCount++;
            }
        }

        return alertCount;
    }

    /**
     * Detects proportionality anomalies: individual reward exceeding 20% of deal value.
     */
    private int detectProportionalityAnomalies(UUID clientId) {
        String sql = """
            SELECT rt.user_id, rt.incentive_id, rt.amount_awarded,
                   po.total_value as deal_value, po.id as purchase_order_id,
                   u.partner_company_id
            FROM reward_transactions rt
            JOIN claim_actions ca ON ca.id = rt.claim_action_id
            JOIN purchase_orders po ON po.id = ca.purchase_order_id
            JOIN users u ON u.id = rt.user_id
            WHERE rt.client_id = ?
              AND po.total_value > 0
              AND rt.amount_awarded > 0
            """;

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, clientId);
        int alertCount = 0;

        for (Map<String, Object> row : rows) {
            BigDecimal amountAwarded = (BigDecimal) row.get("amount_awarded");
            BigDecimal dealValue = (BigDecimal) row.get("deal_value");

            BigDecimal ratio = amountAwarded.divide(dealValue, 4, RoundingMode.HALF_UP);

            if (ratio.compareTo(PROPORTIONALITY_THRESHOLD) > 0) {
                UUID userId = (UUID) row.get("user_id");
                UUID incentiveId = (UUID) row.get("incentive_id");
                UUID partnerId = row.get("partner_company_id") != null
                        ? (UUID) row.get("partner_company_id") : null;

                String description = String.format(
                        "Proportionality alert: Reward of %s to user %s is %.1f%% of deal "
                                + "value %s (threshold: 20%%)",
                        amountAwarded.toPlainString(), userId,
                        ratio.multiply(BigDecimal.valueOf(100)).doubleValue(),
                        dealValue.toPlainString());

                complianceAlertService.createAlert(
                        clientId,
                        ComplianceAlertType.DISPROPORTIONATE_REWARD,
                        "MEDIUM",
                        description,
                        userId,
                        partnerId,
                        incentiveId);
                alertCount++;
            }
        }

        return alertCount;
    }
}

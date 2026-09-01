package com.tenxengage.app.service;

import com.tenxengage.app.entity.Client;
import com.tenxengage.app.entity.RedemptionRequest;
import com.tenxengage.app.entity.enums.RedemptionCategory;
import com.tenxengage.app.entity.enums.RedemptionStatus;
import com.tenxengage.app.entity.enums.WalletType;
import com.tenxengage.app.entity.xtrm.PartnerRedemption;
import com.tenxengage.app.repository.ClientRepository;
import com.tenxengage.app.repository.RedemptionRequestRepository;
import com.tenxengage.app.repository.xtrm.PartnerRedemptionRepository;
import com.tenxengage.app.service.xtrm.XtrmApiClient;
import com.tenxengage.app.service.xtrm.XtrmApiClient.BatchStatusItem;
import com.tenxengage.app.service.xtrm.XtrmApiClient.BatchStatusResult;
import com.tenxengage.app.service.xtrm.XtrmApiClient.GetBatchStatusCommand;
import com.tenxengage.app.service.xtrm.XtrmApiClient.GetTransactionDetailsCommand;
import com.tenxengage.app.service.xtrm.XtrmApiClient.TransactionStatusResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Recovers CASH payouts whose finalizing webhook was missed. On a schedule, it polls XTRM for the status of
 * in-flight payouts (INSTANT/APPROVAL via {@code GetUserWalletTransactionDetails}; BATCH via the whole-batch
 * status API) and settles each through the shared {@link RedemptionWebhookService#settle} — so a late webhook
 * and the cron can never double-settle (terminal-state guard). Never touches company payouts (deferred). Past
 * the cap it stops polling and alerts for manual review — it never auto-releases a possibly-paid transfer.
 */
@Component
public class RedemptionReconciliationService {

    private static final Logger log = LoggerFactory.getLogger(RedemptionReconciliationService.class);
    private static final List<RedemptionStatus> IN_FLIGHT =
            List.of(RedemptionStatus.PROCESSING, RedemptionStatus.RESERVED);

    private final ClientRepository clientRepository;
    private final RedemptionRequestRepository redemptionRequestRepository;
    private final PartnerRedemptionRepository partnerRedemptionRepository;
    private final XtrmApiClient xtrmApiClient;
    private final RedemptionWebhookService redemptionWebhookService;

    private final Set<String> successStatuses;
    private final Set<String> failedStatuses;
    private final int capDays;
    private final int batchPageSize;

    public RedemptionReconciliationService(
            ClientRepository clientRepository,
            RedemptionRequestRepository redemptionRequestRepository,
            PartnerRedemptionRepository partnerRedemptionRepository,
            XtrmApiClient xtrmApiClient,
            RedemptionWebhookService redemptionWebhookService,
            @Value("${redemption.reconciliation.success-statuses:Success,Completed,Released}") String successRaw,
            @Value("${redemption.reconciliation.failed-statuses:Failed}") String failedRaw,
            @Value("${redemption.reconciliation.cap-days:3}") int capDays,
            @Value("${redemption.reconciliation.batch-page-size:50}") int batchPageSize) {
        this.clientRepository = clientRepository;
        this.redemptionRequestRepository = redemptionRequestRepository;
        this.partnerRedemptionRepository = partnerRedemptionRepository;
        this.xtrmApiClient = xtrmApiClient;
        this.redemptionWebhookService = redemptionWebhookService;
        this.successStatuses = toSet(successRaw);
        this.failedStatuses = toSet(failedRaw);
        this.capDays = capDays;
        this.batchPageSize = batchPageSize;
    }

    // Default cadence every 30 min; override redemption.reconciliation.cron (e.g. every minute) for a local demo.
    @Scheduled(cron = "${redemption.reconciliation.cron:0 */30 * * * *}")
    public void reconcile() {
        Instant cutoff = Instant.now().minus(capDays, ChronoUnit.DAYS);
        List<Client> clients = clientRepository.findAll();
        log.info("[step=recon_start] scanning in-flight payouts (clients={} cap={}d)", clients.size(), capDays);
        int inFlightTotal = 0;
        int settledTotal = 0;
        long pastCapTotal = 0;
        for (Client client : clients) {
            try {
                List<RedemptionRequest> inFlight = redemptionRequestRepository.findInFlightForReconciliation(
                        client.getId(), RedemptionCategory.CASH, WalletType.INDIVIDUAL, IN_FLIGHT, cutoff);
                inFlightTotal += inFlight.size();
                if (!inFlight.isEmpty()) {
                    settledTotal += reconcileClient(inFlight);
                }
                long pastCap = redemptionRequestRepository.countStuckPastCap(
                        client.getId(), RedemptionCategory.CASH, WalletType.INDIVIDUAL, IN_FLIGHT, cutoff);
                pastCapTotal += pastCap;
                if (pastCap > 0) {
                    log.warn("[step=recon_past_cap] clientId={} count={} — non-terminal payouts past {}d cap, "
                            + "needs manual review", client.getId(), pastCap, capDays);
                }
            } catch (Exception e) {
                log.error("[step=recon_client_failed] clientId={}", client.getId(), e);
            }
        }
        log.info("[step=recon_done] inFlight={} settled={} pastCap={}", inFlightTotal, settledTotal, pastCapTotal);
    }

    private int reconcileClient(List<RedemptionRequest> inFlight) {
        // Route by which identifier the item carries: batch (customerBatchId) vs single (beneficiaryTransactionId).
        // Single payouts poll the wallet API, which is keyed on the beneficiary id — NOT the payment-side
        // vendorReferenceId. An item dispatched without a beneficiary id can't be polled → wait for cap → manual.
        Map<String, List<RedemptionRequest>> byBatch = new LinkedHashMap<>();
        List<RedemptionRequest> single = new ArrayList<>();
        for (RedemptionRequest r : inFlight) {
            if (r.getCustomerBatchId() != null) {
                byBatch.computeIfAbsent(r.getCustomerBatchId(), k -> new ArrayList<>()).add(r);
            } else if (r.getBeneficiaryTransactionId() != null) {
                single.add(r);
            } else {
                // Dispatched but no beneficiary id (XTRM unreachable, or rail returned none) — can't poll; cap → manual.
                log.warn("[step=recon_ambiguous] redemptionId={} has no beneficiaryTransactionId/customerBatchId,"
                        + " vendorRef={}", r.getId(), r.getVendorReferenceId());
            }
        }
        int settled = 0;
        for (RedemptionRequest r : single) {
            settled += reconcileSingle(r);
        }
        for (Map.Entry<String, List<RedemptionRequest>> e : byBatch.entrySet()) {
            settled += reconcileBatch(e.getKey(), e.getValue());
        }
        return settled;
    }

    private int reconcileSingle(RedemptionRequest r) {
        String pat = partnerRedemptionRepository.findByUserIdAndClientId(r.getUserId(), r.getClientId())
                .map(PartnerRedemption::getRecipientUserId).orElse(null);
        if (pat == null || pat.isBlank()) {
            log.warn("[step=recon_single_no_pat] redemptionId={} — cannot poll status", r.getId());
            return 0;
        }
        // The wallet status API is keyed on the beneficiary transaction id, not the payment-side vendorReferenceId.
        String beneficiaryTxn = r.getBeneficiaryTransactionId();
        TransactionStatusResult res = xtrmApiClient.getTransactionDetails(
                new GetTransactionDetailsCommand(pat, beneficiaryTxn));
        log.info("[step=recon_poll_single] redemptionId={} beneficiaryTxn={} vendorRef={} found={} status={}",
                r.getId(), beneficiaryTxn, r.getVendorReferenceId(), res.found(), res.status());
        if (!res.found()) {
            // Not found or transient outage → leave in flight; retry next run (or cap → manual).
            return 0;
        }
        return applyStatus(r.getId(), res.status()) ? 1 : 0;
    }

    private int reconcileBatch(String customerBatchId, List<RedemptionRequest> items) {
        Map<String, String> statusByTxn = new HashMap<>();
        Map<String, String> reasonByTxn = new HashMap<>();
        int skip = 0;
        boolean more = true;
        while (more) {
            BatchStatusResult res = xtrmApiClient.getBatchStatus(
                    new GetBatchStatusCommand(customerBatchId, skip, batchPageSize));
            if (!res.success()) {
                // Transient — leave the whole batch's items in flight; retry next run.
                return 0;
            }
            for (BatchStatusItem it : res.items()) {
                statusByTxn.put(it.customerTransactionId(), it.status());
                if (it.errorReason() != null) {
                    reasonByTxn.put(it.customerTransactionId(), it.errorReason());
                }
            }
            more = res.hasMore();
            skip = res.nextRecordsToSkip() > skip ? res.nextRecordsToSkip() : skip + batchPageSize;
        }
        int settled = 0;
        for (RedemptionRequest r : items) {
            String status = statusByTxn.get(r.getCustomerTransactionId());
            String reason = reasonByTxn.get(r.getCustomerTransactionId());
            log.info("[step=recon_poll_batch] redemptionId={} batchId={} customerTxn={} status={} reason={}",
                    r.getId(), customerBatchId, r.getCustomerTransactionId(), status, reason);
            if (status == null) {
                continue; // item not yet in the batch listing → still pending
            }
            if (applyStatus(r.getId(), status)) {
                settled++;
            }
        }
        return settled;
    }

    /**
     * Map an XTRM status string to a settlement (idempotent via the shared terminal-state guard). Returns true
     * only when this run actually drove the redemption to a terminal state (COMPLETED/FAILED) — so the cron
     * summary counts real settlements, not idempotent no-ops.
     */
    private boolean applyStatus(UUID redemptionId, String status) {
        if (status == null) {
            return false;
        }
        String key = status.trim().toLowerCase(Locale.ROOT);
        RedemptionWebhookService.SettlementOutcome outcome;
        if (successStatuses.contains(key)) {
            outcome = redemptionWebhookService.settle(redemptionId, true, null);
        } else if (failedStatuses.contains(key)) {
            outcome = redemptionWebhookService.settle(redemptionId, false, "Reconciled as failed: " + status);
        } else {
            // pending / unknown → leave in flight, retry next run.
            return false;
        }
        log.info("[step=recon_settled] redemptionId={} status={} outcome={}", redemptionId, status, outcome);
        return outcome == RedemptionWebhookService.SettlementOutcome.COMPLETED
                || outcome == RedemptionWebhookService.SettlementOutcome.FAILED;
    }

    private static Set<String> toSet(String csv) {
        return Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(s -> s.toLowerCase(Locale.ROOT))
                .collect(Collectors.toUnmodifiableSet());
    }
}

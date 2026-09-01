package com.tenxengage.app.controller.dev;

import com.tenxengage.app.entity.RedemptionRequest;
import com.tenxengage.app.entity.RedemptionWebhookEvent;
import com.tenxengage.app.entity.enums.WebhookStatus;
import com.tenxengage.app.exception.ResourceNotFoundException;
import com.tenxengage.app.repository.RedemptionRequestRepository;
import com.tenxengage.app.repository.RedemptionWebhookEventRepository;
import com.tenxengage.app.service.BatchRedemptionProcessor;
import com.tenxengage.app.service.RedemptionReconciliationService;
import com.tenxengage.app.service.RedemptionWebhookService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * LOCAL-ONLY DEV &amp; DEMO HELPER — retained intentionally (needed for future demos).
 *
 * <p><b>Purpose:</b> manually finalize a CASH INSTANT redemption stuck in {@code PROCESSING} by
 * simulating the XTRM completion webhook. When testing the real XTRM sandbox in {@code local}, the
 * outbound {@code TransferFund} succeeds, but XTRM delivers the payout result via an <em>inbound</em>
 * webhook to a public URL — which cannot reach a developer's {@code localhost}. So the callback never
 * arrives and the redemption sits in PROCESSING forever. This endpoint stands in for that callback.
 *
 * <p>It builds a {@code RECEIVED} {@link RedemptionWebhookEvent} and calls
 * {@link RedemptionWebhookService#process} — the SAME finalization path the real webhook uses — which
 * settles the reservation, writes the {@code DEBIT}, and flips the redemption to COMPLETED (or releases
 * the reservation and marks it FAILED when {@code outcome=failure}). It does NOT contact XTRM; it only
 * reflects the outcome into our system.
 *
 * <p>Safety: registered ONLY under the {@code local} Spring profile, so it is never instantiated in
 * {@code test}, {@code localtest}, staging, or prod. Deliberately has NO HMAC (unlike the real
 * {@code POST /api/v1/webhooks/redemption/xtrm}) so it can be called directly from Postman/cURL.
 *
 * <p><b>Kept on purpose for local demos.</b> Safe to ship: {@code @Profile("local")} means it is never
 * instantiated in {@code test}, {@code localtest}, staging, or prod — unreachable in any deployed environment.
 * If the demo tooling is ever no longer needed, the whole {@code controller/dev} package can simply be deleted.
 */
@Profile("local")
@RestController
@RequestMapping("/api/v1/dev/redemption")
public class DevRedemptionController {

    private static final Logger log = LoggerFactory.getLogger(DevRedemptionController.class);

    private final RedemptionWebhookService webhookService;
    private final RedemptionRequestRepository redemptionRequestRepository;
    private final RedemptionWebhookEventRepository webhookEventRepository;
    private final BatchRedemptionProcessor batchProcessor;
    private final RedemptionReconciliationService reconciliationService;

    public DevRedemptionController(RedemptionWebhookService webhookService,
                                   RedemptionRequestRepository redemptionRequestRepository,
                                   RedemptionWebhookEventRepository webhookEventRepository,
                                   BatchRedemptionProcessor batchProcessor,
                                   RedemptionReconciliationService reconciliationService) {
        this.webhookService = webhookService;
        this.redemptionRequestRepository = redemptionRequestRepository;
        this.webhookEventRepository = webhookEventRepository;
        this.batchProcessor = batchProcessor;
        this.reconciliationService = reconciliationService;
    }

    /**
     * Simulate the XTRM completion callback for a redemption.
     * <pre>POST /api/v1/dev/redemption/{id}/complete?outcome=success
     * POST /api/v1/dev/redemption/{id}/complete?outcome=failure&amp;reason=...</pre>
     */
    @PostMapping("/{id}/complete")
    public ResponseEntity<Map<String, String>> complete(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "success") String outcome,
            @RequestParam(required = false) String reason) {

        RedemptionRequest request = redemptionRequestRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("RedemptionRequest", "id", id));

        boolean completed = !"failure".equalsIgnoreCase(outcome);
        String failureReason = completed ? null : (reason != null ? reason : "Dev-simulated XTRM failure");

        RedemptionWebhookEvent event = webhookEventRepository.save(RedemptionWebhookEvent.builder()
                .clientId(request.getClientId())
                .vendor("xtrm")
                .redemptionRequestId(id)
                .idempotencyKey("dev-" + id + "-" + Instant.now().toEpochMilli())
                .payload("{\"dev\":true,\"outcome\":\"" + outcome + "\"}")
                .status(WebhookStatus.RECEIVED)
                .receivedAt(Instant.now())
                .build());

        log.warn("DEV: simulating XTRM '{}' completion webhook for redemptionId={}", outcome, id);
        webhookService.process(id, event, completed, failureReason);

        String resultStatus = redemptionRequestRepository.findById(id)
                .map(r -> r.getStatus().name()).orElse("UNKNOWN");
        return ResponseEntity.ok(Map.of(
                "redemptionId", id.toString(),
                "outcome", outcome,
                "resultStatus", resultStatus));
    }

    /**
     * Runs the redemption batch sweep on demand so a developer can dispatch RESERVED+BATCH requests
     * without waiting for the {@code 0 0 2 * * *} cron. The sweep only picks up items whose
     * {@code scheduled_batch_date <= today}; a freshly-submitted DAILY batch is dated for <em>tomorrow</em>,
     * so backdate it first:
     * {@code UPDATE redemption_requests SET scheduled_batch_date = CURRENT_DATE WHERE id = '<id>';}
     * Then finalize each dispatched item via {@code POST /{id}/complete?outcome=success}.
     * <pre>POST /api/v1/dev/redemption/run-batch</pre>
     */
    @PostMapping("/run-batch")
    public ResponseEntity<Map<String, String>> runBatch() {
        log.warn("DEV: manual redemption batch sweep triggered via /api/v1/dev/redemption/run-batch");
        batchProcessor.processBatch();
        return ResponseEntity.ok(Map.of(
                "status", "batch sweep triggered",
                "note", "Dispatched RESERVED+BATCH requests with scheduled_batch_date <= today "
                        + "(fresh DAILY batches are dated tomorrow — backdate to CURRENT_DATE first). "
                        + "Check logs for 'step=batch_dispatch_sent', then finalize each via "
                        + "POST /api/v1/dev/redemption/{id}/complete?outcome=success."));
    }

    /**
     * Runs the missed-webhook reconciliation poll on demand so a developer can settle in-flight CASH
     * payouts without waiting for the reconciliation cron (every 30 min by default). Polls XTRM for the
     * status of every in-flight (PROCESSING/RESERVED) individual CASH payout within the cap window and
     * settles the terminal ones through the shared webhook path — identical to what the scheduled
     * {@link RedemptionReconciliationService#reconcile()} does; the cron keeps running as configured.
     * <pre>POST /api/v1/dev/redemption/run-reconciliation</pre>
     */
    @PostMapping("/run-reconciliation")
    public ResponseEntity<Map<String, String>> runReconciliation() {
        log.warn("DEV: manual reconciliation poll triggered via /api/v1/dev/redemption/run-reconciliation");
        reconciliationService.reconcile();
        return ResponseEntity.ok(Map.of(
                "status", "reconciliation poll triggered",
                "note", "Polled XTRM for in-flight CASH payouts (PROCESSING/RESERVED) within the cap window "
                        + "and settled terminal ones via the shared webhook path. Check logs for "
                        + "'[step=recon_start]' … '[step=recon_done] inFlight=… settled=… pastCap=…'."));
    }
}

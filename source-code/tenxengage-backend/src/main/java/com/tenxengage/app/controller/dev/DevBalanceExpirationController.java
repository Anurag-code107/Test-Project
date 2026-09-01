package com.tenxengage.app.controller.dev;

import com.tenxengage.app.service.redemption.BalanceExpiryBatchService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * LOCAL-ONLY DEV &amp; DEMO HELPER — committed intentionally (manual balance-expiration sweep trigger).
 *
 * <p>Fires {@link BalanceExpiryBatchService#runExpirySweep()} on demand so a developer can produce
 * {@code EXPIRY} ledger entries without waiting for the {@code 0 0 2 * * *} (02:00 UTC) cron.
 *
 * <p>Safety: registered ONLY under the {@code local} Spring profile ({@link Profile @Profile("local")}),
 * so it is never instantiated in {@code test}, {@code localtest}, staging, or prod — unreachable in any
 * deployed environment. Reachable by any authenticated caller (SecurityConfig default is
 * {@code anyRequest().authenticated()}); deliberately has no permission annotation since the sweep is a
 * cross-tenant SYSTEM operation.
 *
 * <p>For anything to actually expire, the usual preconditions still apply: Kafka up (so the warn
 * notice can reach NOTIFIED), an enabled policy whose grace window has passed, a due
 * {@code scheduledExpiryDate}, and a wallet with {@code availableBalance > 0}.
 *
 * <p><b>Kept on purpose for local demos.</b> Safe to ship: {@code @Profile("local")} means it is never
 * instantiated outside local. If the demo tooling is ever no longer needed, the whole {@code dev}
 * package can simply be deleted.
 */
@Profile("local")
@RestController
@RequestMapping("/api/v1/dev/balance-expiration")
public class DevBalanceExpirationController {

    private static final Logger log = LoggerFactory.getLogger(DevBalanceExpirationController.class);

    private final BalanceExpiryBatchService batchService;

    public DevBalanceExpirationController(BalanceExpiryBatchService batchService) {
        this.batchService = batchService;
    }

    /**
     * Runs the warn + expire phases for every enabled policy, immediately.
     * POST /api/v1/dev/balance-expiration/run-sweep
     */
    @PostMapping("/run-sweep")
    public ResponseEntity<Map<String, String>> runSweep() {
        log.warn("DEV: manual balance-expiration sweep triggered via /api/v1/dev/balance-expiration/run-sweep");
        batchService.runExpirySweep();
        return ResponseEntity.ok(Map.of(
                "status", "sweep triggered",
                "note", "Check logs for 'step=balance_expiry_batch_finished warnedCount=.. expiredCount=..' "
                        + "then query: SELECT * FROM ledger_entries WHERE entry_type='EXPIRY'. "
                        + "Nothing expires unless Kafka is up + a backdated enabled policy + a funded wallet exist."));
    }
}

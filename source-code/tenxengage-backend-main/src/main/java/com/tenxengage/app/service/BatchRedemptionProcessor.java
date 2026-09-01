package com.tenxengage.app.service;

import com.tenxengage.app.entity.Client;
import com.tenxengage.app.entity.LedgerEntry;
import com.tenxengage.app.entity.RedemptionRequest;
import com.tenxengage.app.entity.RewardWallet;
import com.tenxengage.app.entity.enums.LedgerEntryType;
import com.tenxengage.app.entity.enums.RedemptionProcessingMode;
import com.tenxengage.app.entity.enums.RedemptionStatus;
import com.tenxengage.app.exception.ResourceNotFoundException;
import com.tenxengage.app.repository.ClientRepository;
import com.tenxengage.app.repository.LedgerEntryRepository;
import com.tenxengage.app.repository.RedemptionRequestRepository;
import com.tenxengage.app.repository.RewardWalletRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Component
public class BatchRedemptionProcessor {

    private static final Logger log = LoggerFactory.getLogger(BatchRedemptionProcessor.class);

    private final ClientRepository clientRepository;
    private final RedemptionRequestRepository redemptionRequestRepository;
    private final RewardWalletRepository walletRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final RedemptionOrchestrationService orchestrationService;

    private BatchRedemptionProcessor self;

    public BatchRedemptionProcessor(ClientRepository clientRepository,
                                    RedemptionRequestRepository redemptionRequestRepository,
                                    RewardWalletRepository walletRepository,
                                    LedgerEntryRepository ledgerEntryRepository,
                                    RedemptionOrchestrationService orchestrationService) {
        this.clientRepository = clientRepository;
        this.redemptionRequestRepository = redemptionRequestRepository;
        this.walletRepository = walletRepository;
        this.ledgerEntryRepository = ledgerEntryRepository;
        this.orchestrationService = orchestrationService;
    }

    @Autowired
    @Lazy
    public void setSelf(BatchRedemptionProcessor self) {
        this.self = self;
    }

    @Scheduled(cron = "${redemption.batch.cron:0 0 2 * * *}")
    public void processBatch() {
        LocalDate today = LocalDate.now();
        List<Client> clients = clientRepository.findAll();

        for (Client client : clients) {
            List<RedemptionRequest> eligible = redemptionRequestRepository
                    .findByClientIdAndStatusAndProcessingModeAndScheduledBatchDateLessThanEqual(
                            client.getId(), RedemptionStatus.RESERVED, RedemptionProcessingMode.BATCH, today);

            for (RedemptionRequest request : eligible) {
                try {
                    self.dispatchItem(request.getId());
                } catch (Exception e) {
                    log.error("[step=batch_dispatch_failed] redemptionId={}", request.getId(), e);
                }
            }
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void dispatchItem(UUID requestId) {
        RedemptionRequest request = redemptionRequestRepository.findByIdForUpdate(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("RedemptionRequest", "id", requestId));

        // Terminal-state guard AFTER lock — two concurrent callers could both read RESERVED without this
        if (request.getStatus() != RedemptionStatus.RESERVED) {
            log.info("[step=batch_dispatch_skip] redemptionId={}, status={}", requestId, request.getStatus());
            return;
        }

        request.setStatus(RedemptionStatus.PROCESSING);
        redemptionRequestRepository.save(request);

        // Outbox pattern deferred — dispatch currently always throws (US-05 BE-1: XTRM API not working;
        // US-06 BE-1: Xoxoday credentials unavailable). Duplicate-dispatch risk via committed PROCESSING
        // + failed DB commit will be addressed when vendor integrations are unblocked by adding a
        // pre-committed outbox/dispatchAttempt record and moving the vendor call outside this transaction.
        try {
            orchestrationService.dispatch(request);
            log.info("[step=batch_dispatch_sent] redemptionId={}, clientId={}",
                    request.getId(), request.getClientId());
        } catch (Exception e) {
            log.error("[step=batch_dispatch_failed] redemptionId={}", request.getId(), e);

            RewardWallet wallet = walletRepository.findByIdForUpdate(request.getWalletId())
                    .orElseThrow(() -> new ResourceNotFoundException("RewardWallet", "id", request.getWalletId()));

            BigDecimal availBefore = wallet.getAvailableBalance();
            BigDecimal resvBefore = wallet.getReservedBalance();
            wallet.setAvailableBalance(availBefore.add(request.getAmount()));
            wallet.setReservedBalance(resvBefore.subtract(request.getAmount()));
            walletRepository.save(wallet);

            ledgerEntryRepository.save(LedgerEntry.builder()
                    .clientId(request.getClientId())
                    .rewardWalletId(request.getWalletId())
                    .entryType(LedgerEntryType.RELEASE)
                    .amount(request.getAmount())
                    .currencyId(request.getCurrencyId())
                    .referenceType("REDEMPTION_REQUEST")
                    .referenceId(request.getId())
                    .availableBalanceBefore(availBefore)
                    .availableBalanceAfter(wallet.getAvailableBalance())
                    .reservedBalanceBefore(resvBefore)
                    .reservedBalanceAfter(wallet.getReservedBalance())
                    .build());

            request.setStatus(RedemptionStatus.FAILED);
            request.setFailureReason("Dispatch failure: " + e.getMessage());
            redemptionRequestRepository.save(request);
        }
    }
}

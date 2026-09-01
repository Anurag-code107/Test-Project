package com.tenxengage.app.service;

import com.tenxengage.app.dto.response.RewardTransactionResponse;
import com.tenxengage.app.repository.RewardTransactionRepository;
import com.tenxengage.app.repository.RewardTransactionRepository.UserTransactionRow;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;

@Service
public class RewardTransactionService {

    // Sentinel bounds used when the caller passes no date filter. We can't push raw
    // `:date IS NULL` checks into the JPQL — Postgres can't infer the parameter type
    // from `:startDate IS NULL` alone and the driver throws "could not determine data
    // type of parameter". The query always takes a concrete pair of bounds and this
    // service substitutes wide-open instants when the caller passes nothing.
    private static final Instant FAR_PAST = Instant.parse("1970-01-01T00:00:00Z");
    private static final Instant FAR_FUTURE = Instant.parse("9999-12-31T23:59:59Z");

    private final RewardTransactionRepository rewardTransactionRepository;

    public RewardTransactionService(RewardTransactionRepository rewardTransactionRepository) {
        this.rewardTransactionRepository = rewardTransactionRepository;
    }

    @Transactional(readOnly = true)
    public Page<RewardTransactionResponse> getTransactions(UUID clientId, UUID userId,
                                                           LocalDate startDate, LocalDate endDate,
                                                           Pageable pageable) {
        Instant startInstant = startDate != null
                ? startDate.atStartOfDay(ZoneOffset.UTC).toInstant()
                : FAR_PAST;
        Instant endInstant = endDate != null
                ? endDate.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant()
                : FAR_FUTURE;

        Page<UserTransactionRow> page = rewardTransactionRepository.findUserTransactions(
                clientId, userId, startInstant, endInstant, pageable);

        return page.map(RewardTransactionService::toResponse);
    }

    private static RewardTransactionResponse toResponse(UserTransactionRow row) {
        BigDecimal amount = row.getAmountAwarded() != null ? row.getAmountAwarded() : BigDecimal.ZERO;
        return new RewardTransactionResponse(
                row.getId(),
                row.getCreatedAt(),
                "earned",
                row.getCurrencyId(),
                amount.toPlainString(),
                row.getIncentiveId(),
                row.getIncentiveName(),
                row.getClaimActionId(),
                row.getOrderNumber()
        );
    }
}

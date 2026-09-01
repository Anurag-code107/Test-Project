package com.tenxengage.app.service;

import com.tenxengage.app.dto.response.RewardTransactionResponse;
import com.tenxengage.app.repository.RewardTransactionRepository;
import com.tenxengage.app.repository.RewardTransactionRepository.UserTransactionRow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RewardTransactionServiceTest {

    @Mock private RewardTransactionRepository repository;
    @InjectMocks private RewardTransactionService service;

    private UUID clientId;
    private UUID userId;

    @BeforeEach
    void setup() {
        clientId = UUID.randomUUID();
        userId = UUID.randomUUID();
    }

    @Test
    void getTransactions_mapsRowFieldsToResponseAndStampsTypeAsEarned() {
        UUID txId = UUID.randomUUID();
        UUID incentiveId = UUID.randomUUID();
        UUID claimActionId = UUID.randomUUID();
        Instant createdAt = Instant.parse("2026-04-15T10:30:00Z");
        UserTransactionRow row = mockRow(txId, createdAt, "cash", new BigDecimal("250.00"),
                incentiveId, "Q2 SPIFF", claimActionId, "PO-1234");

        when(repository.findUserTransactions(any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(row)));

        Page<RewardTransactionResponse> page = service.getTransactions(
                clientId, userId, null, null, PageRequest.of(0, 50));

        assertThat(page.getContent()).hasSize(1);
        RewardTransactionResponse r = page.getContent().get(0);
        assertThat(r.id()).isEqualTo(txId);
        assertThat(r.date()).isEqualTo(createdAt);
        assertThat(r.type()).isEqualTo("earned");
        assertThat(r.currencyId()).isEqualTo("cash");
        assertThat(r.amount()).isEqualTo("250.00");
        assertThat(r.incentiveId()).isEqualTo(incentiveId);
        assertThat(r.incentiveName()).isEqualTo("Q2 SPIFF");
        assertThat(r.claimActionId()).isEqualTo(claimActionId);
        assertThat(r.purchaseOrderNumber()).isEqualTo("PO-1234");
    }

    @Test
    void getTransactions_handlesNullClaimActionAndOrderNumber() {
        UserTransactionRow row = mockRow(UUID.randomUUID(), Instant.now(), "points",
                new BigDecimal("500"), UUID.randomUUID(), "Q3 Promo", null, null);
        when(repository.findUserTransactions(any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(row)));

        Page<RewardTransactionResponse> page = service.getTransactions(
                clientId, userId, null, null, PageRequest.of(0, 50));

        RewardTransactionResponse r = page.getContent().get(0);
        assertThat(r.claimActionId()).isNull();
        assertThat(r.purchaseOrderNumber()).isNull();
    }

    @Test
    void getTransactions_substitutesWideOpenSentinelsWhenBoundsAreNull() {
        // The JPQL can't take raw nulls (Postgres can't infer the parameter type),
        // so the service maps null → wide-open sentinel instants.
        when(repository.findUserTransactions(any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of()));

        service.getTransactions(clientId, userId, null, null, PageRequest.of(0, 50));

        ArgumentCaptor<Instant> startCap = ArgumentCaptor.forClass(Instant.class);
        ArgumentCaptor<Instant> endCap = ArgumentCaptor.forClass(Instant.class);
        verify(repository).findUserTransactions(eq(clientId), eq(userId),
                startCap.capture(), endCap.capture(), any(Pageable.class));
        assertThat(startCap.getValue()).isEqualTo(Instant.parse("1970-01-01T00:00:00Z"));
        assertThat(endCap.getValue()).isEqualTo(Instant.parse("9999-12-31T23:59:59Z"));
    }

    @Test
    void getTransactions_convertsLocalDateBoundsToUtcInstants() {
        when(repository.findUserTransactions(any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of()));

        LocalDate start = LocalDate.of(2026, 4, 1);
        LocalDate end = LocalDate.of(2026, 4, 30);
        service.getTransactions(clientId, userId, start, end, PageRequest.of(0, 50));

        ArgumentCaptor<Instant> startCap = ArgumentCaptor.forClass(Instant.class);
        ArgumentCaptor<Instant> endCap = ArgumentCaptor.forClass(Instant.class);
        verify(repository).findUserTransactions(eq(clientId), eq(userId),
                startCap.capture(), endCap.capture(), any(Pageable.class));
        assertThat(startCap.getValue()).isEqualTo(start.atStartOfDay(ZoneOffset.UTC).toInstant());
        // endDate is exclusive — service shifts +1 day so the whole endDate is included
        assertThat(endCap.getValue()).isEqualTo(end.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant());
    }

    private UserTransactionRow mockRow(UUID id, Instant createdAt, String currencyId,
                                       BigDecimal amount, UUID incentiveId, String incentiveName,
                                       UUID claimActionId, String orderNumber) {
        return new UserTransactionRow() {
            @Override public UUID getId() { return id; }
            @Override public Instant getCreatedAt() { return createdAt; }
            @Override public String getCurrencyId() { return currencyId; }
            @Override public BigDecimal getAmountAwarded() { return amount; }
            @Override public UUID getIncentiveId() { return incentiveId; }
            @Override public String getIncentiveName() { return incentiveName; }
            @Override public UUID getClaimActionId() { return claimActionId; }
            @Override public String getOrderNumber() { return orderNumber; }
        };
    }
}

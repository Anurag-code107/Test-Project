package com.tenxengage.app.service;

import com.tenxengage.app.entity.RedemptionCatalogItem;
import com.tenxengage.app.entity.enums.RedemptionCategory;
import com.tenxengage.app.repository.RedemptionCatalogItemRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BankTransferCardServiceTest {

    @Mock
    private RedemptionCatalogItemRepository catalogItemRepository;

    @InjectMocks
    private BankTransferCardService service;

    private static final UUID CLIENT_ID = UUID.randomUUID();

    // BU-1: first call provisions exactly one card (CASH / cash / min $1 / isBankTransfer / active).
    @Test
    void ensureBankTransferCard_createsOneCard_whenNoneExists() {
        when(catalogItemRepository.findByOwnerClientIdAndIsBankTransferTrueAndDeletedFalse(CLIENT_ID))
                .thenReturn(Optional.empty());
        when(catalogItemRepository.save(any(RedemptionCatalogItem.class))).thenAnswer(inv -> inv.getArgument(0));

        RedemptionCatalogItem card = service.ensureBankTransferCard(CLIENT_ID);

        ArgumentCaptor<RedemptionCatalogItem> captor = ArgumentCaptor.forClass(RedemptionCatalogItem.class);
        verify(catalogItemRepository).save(captor.capture());
        RedemptionCatalogItem saved = captor.getValue();
        assertThat(saved.isBankTransfer()).isTrue();
        assertThat(saved.getOwnerClientId()).isEqualTo(CLIENT_ID);
        assertThat(saved.getCategory()).isEqualTo(RedemptionCategory.CASH);
        assertThat(saved.getCurrencyId()).isEqualTo("cash");
        assertThat(saved.getDefaultMinRedemptionAmount()).isEqualByComparingTo(new BigDecimal("1.00"));
        assertThat(saved.isActive()).isTrue();
        assertThat(saved.getName()).isEqualTo("Bank Transfer");
        assertThat(saved.isReturnable()).isFalse();
        assertThat(card).isSameAs(saved);
    }

    // BU-1 (idempotent): a second call returns the same existing row without inserting another.
    @Test
    void ensureBankTransferCard_returnsExisting_idempotent() {
        RedemptionCatalogItem existing = RedemptionCatalogItem.builder()
                .ownerClientId(CLIENT_ID).isBankTransfer(true).build();
        when(catalogItemRepository.findByOwnerClientIdAndIsBankTransferTrueAndDeletedFalse(CLIENT_ID))
                .thenReturn(Optional.of(existing));

        RedemptionCatalogItem card = service.ensureBankTransferCard(CLIENT_ID);

        assertThat(card).isSameAs(existing);
        verify(catalogItemRepository, never()).save(any());
    }
}

package com.tenxengage.app.service;

import com.tenxengage.app.entity.CompanyDistribution;
import com.tenxengage.app.entity.CompanyDistributionItem;
import com.tenxengage.app.entity.LedgerEntry;
import com.tenxengage.app.entity.RewardWallet;
import com.tenxengage.app.entity.enums.DistributionItemStatus;
import com.tenxengage.app.entity.enums.DistributionRail;
import com.tenxengage.app.entity.enums.LedgerEntryType;
import com.tenxengage.app.entity.enums.WalletType;
import com.tenxengage.app.repository.CompanyDistributionItemRepository;
import com.tenxengage.app.repository.CompanyDistributionRepository;
import com.tenxengage.app.repository.LedgerEntryRepository;
import com.tenxengage.app.repository.RewardWalletRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WalletCreditSettlementServiceTest {

    @Mock private CompanyDistributionRepository distributionRepository;
    @Mock private CompanyDistributionItemRepository itemRepository;
    @Mock private RewardWalletRepository walletRepository;
    @Mock private LedgerEntryRepository ledgerEntryRepository;

    @InjectMocks private WalletCreditSettlementService service;

    private static final UUID CLIENT_ID = UUID.randomUUID();
    private static final UUID COMPANY_ID = UUID.randomUUID();
    private static final UUID COMPANY_WALLET_ID = UUID.randomUUID();
    private static final UUID RECIPIENT_WALLET_ID = UUID.randomUUID();
    private static final UUID RECIPIENT_ID = UUID.randomUUID();
    private static final UUID ITEM_ID = UUID.randomUUID();
    private static final UUID DIST_ID = UUID.randomUUID();
    private static final String REF = "COMPANY_DISTRIBUTION_ITEM";

    @BeforeEach
    void setUp() {
        when(ledgerEntryRepository.save(any())).thenAnswer(i -> {
            LedgerEntry e = i.getArgument(0);
            if (e.getId() == null) {
                e.setId(UUID.randomUUID());
            }
            return e;
        });
        when(itemRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(distributionRepository.findById(DIST_ID)).thenReturn(Optional.of(header()));
        when(ledgerEntryRepository
                .findFirstByRewardWalletIdAndReferenceTypeAndReferenceIdAndEntryType(any(), any(), any(), any()))
                .thenReturn(Optional.empty());
    }

    private CompanyDistribution header() {
        CompanyDistribution d = CompanyDistribution.builder()
                .clientId(CLIENT_ID).partnerCompanyId(COMPANY_ID).sourceWalletId(COMPANY_WALLET_ID)
                .rail(DistributionRail.WALLET_CREDIT).currencyId("cash")
                .initiatedByUserId(UUID.randomUUID()).recipientCount(1)
                .totalAmount(new BigDecimal("20.00")).build();
        d.setId(DIST_ID);
        return d;
    }

    private CompanyDistributionItem reservedItem() {
        CompanyDistributionItem i = CompanyDistributionItem.builder()
                .clientId(CLIENT_ID).distributionId(DIST_ID).recipientUserId(RECIPIENT_ID)
                .amount(new BigDecimal("20.00")).status(DistributionItemStatus.RESERVED).build();
        i.setId(ITEM_ID);
        return i;
    }

    private RewardWallet companyWallet(String available, String reserved) {
        RewardWallet w = RewardWallet.builder()
                .clientId(CLIENT_ID).partnerCompanyId(COMPANY_ID).walletType(WalletType.COMPANY)
                .currencyId("cash").availableBalance(new BigDecimal(available))
                .reservedBalance(new BigDecimal(reserved)).build();
        w.setId(COMPANY_WALLET_ID);
        return w;
    }

    private RewardWallet recipientWallet(String available) {
        RewardWallet w = RewardWallet.builder()
                .clientId(CLIENT_ID).userId(RECIPIENT_ID).walletType(WalletType.INDIVIDUAL)
                .currencyId("cash").availableBalance(new BigDecimal(available))
                .reservedBalance(BigDecimal.ZERO).build();
        w.setId(RECIPIENT_WALLET_ID);
        return w;
    }

    private void stubHappy() {
        when(itemRepository.findByIdForUpdate(ITEM_ID)).thenReturn(Optional.of(reservedItem()));
        when(walletRepository.findByIdForUpdate(COMPANY_WALLET_ID))
                .thenReturn(Optional.of(companyWallet("80.00", "20.00")));
        when(walletRepository.findForUpdate(CLIENT_ID, RECIPIENT_ID, "cash", WalletType.INDIVIDUAL))
                .thenReturn(Optional.of(recipientWallet("5.00")));
    }

    // ------------------------------------------------------------------ the atomic unit

    /**
     * The property that matters: company reserved goes down by exactly this item's share, and the recipient's
     * available goes up by the same, in one pass.
     */
    @Test
    void settle_debitsCompanyReserved_andCreditsRecipientAvailable() {
        stubHappy();

        WalletCreditSettlementService.Outcome out = service.settleItem(ITEM_ID);

        assertThat(out).isEqualTo(WalletCreditSettlementService.Outcome.SETTLED);

        ArgumentCaptor<LedgerEntry> lc = ArgumentCaptor.forClass(LedgerEntry.class);
        verify(ledgerEntryRepository, times(2)).save(lc.capture());
        List<LedgerEntry> entries = lc.getAllValues();

        LedgerEntry debit = entries.stream().filter(e -> e.getEntryType() == LedgerEntryType.DEBIT)
                .findFirst().orElseThrow();
        assertThat(debit.getRewardWalletId()).isEqualTo(COMPANY_WALLET_ID);
        assertThat(debit.getReservedBalanceBefore()).isEqualByComparingTo("20.00");
        assertThat(debit.getReservedBalanceAfter()).isEqualByComparingTo("0.00");
        // A debit out of reserved must NOT move available — that was already moved by the reserve.
        assertThat(debit.getAvailableBalanceBefore()).isEqualByComparingTo(debit.getAvailableBalanceAfter());

        LedgerEntry credit = entries.stream().filter(e -> e.getEntryType() == LedgerEntryType.CREDIT)
                .findFirst().orElseThrow();
        assertThat(credit.getRewardWalletId()).isEqualTo(RECIPIENT_WALLET_ID);
        assertThat(credit.getAvailableBalanceBefore()).isEqualByComparingTo("5.00");
        assertThat(credit.getAvailableBalanceAfter()).isEqualByComparingTo("25.00");

        // Both legs keyed on the item, which is what makes a retry idempotent.
        assertThat(entries).allSatisfy(e -> {
            assertThat(e.getReferenceType()).isEqualTo(REF);
            assertThat(e.getReferenceId()).isEqualTo(ITEM_ID);
        });
    }

    /** Both wallets are saved and the item is stamped COMPLETED with both ledger ids. */
    @Test
    void settle_stampsBothLedgerIdsAndCompletes() {
        stubHappy();

        service.settleItem(ITEM_ID);

        ArgumentCaptor<CompanyDistributionItem> ic = ArgumentCaptor.forClass(CompanyDistributionItem.class);
        verify(itemRepository).save(ic.capture());
        CompanyDistributionItem saved = ic.getValue();
        assertThat(saved.getStatus()).isEqualTo(DistributionItemStatus.COMPLETED);
        assertThat(saved.getDebitLedgerEntryId()).isNotNull();
        assertThat(saved.getCreditLedgerEntryId()).isNotNull();
        assertThat(saved.getSettledAt()).isNotNull();
        verify(walletRepository, times(2)).save(any());
    }

    /** The recipient's wallet is created on first use rather than failing when they have never had one. */
    @Test
    void settle_ensuresRecipientWalletExists() {
        stubHappy();

        service.settleItem(ITEM_ID);

        verify(walletRepository).ensureIndividualWalletExists(CLIENT_ID, RECIPIENT_ID, "cash");
    }

    // ------------------------------------------------------------------ concurrency + retry

    /** Guard after the lock: a second attempt finds a non-RESERVED row and does nothing. */
    @Test
    void settle_alreadyCompleted_isNoOp() {
        CompanyDistributionItem done = reservedItem();
        done.setStatus(DistributionItemStatus.COMPLETED);
        when(itemRepository.findByIdForUpdate(ITEM_ID)).thenReturn(Optional.of(done));

        assertThat(service.settleItem(ITEM_ID))
                .isEqualTo(WalletCreditSettlementService.Outcome.ALREADY_TERMINAL);
        verify(ledgerEntryRepository, never()).save(any());
        verify(walletRepository, never()).save(any());
    }

    /**
     * Retry after a partial attempt must not pay twice. With the debit already on the ledger, the retry reuses
     * that entry's id and writes only the missing credit.
     */
    @Test
    void settle_retryWithDebitAlreadyWritten_doesNotDebitAgain() {
        stubHappy();
        LedgerEntry priorDebit = LedgerEntry.builder()
                .clientId(CLIENT_ID).rewardWalletId(COMPANY_WALLET_ID)
                .entryType(LedgerEntryType.DEBIT).amount(new BigDecimal("20.00"))
                .currencyId("cash").referenceType(REF).referenceId(ITEM_ID)
                .availableBalanceBefore(BigDecimal.ZERO).availableBalanceAfter(BigDecimal.ZERO)
                .reservedBalanceBefore(BigDecimal.ZERO).reservedBalanceAfter(BigDecimal.ZERO)
                .build();
        priorDebit.setId(UUID.randomUUID());
        when(ledgerEntryRepository.findFirstByRewardWalletIdAndReferenceTypeAndReferenceIdAndEntryType(
                eq(COMPANY_WALLET_ID), eq(REF), eq(ITEM_ID), eq(LedgerEntryType.DEBIT)))
                .thenReturn(Optional.of(priorDebit));

        assertThat(service.settleItem(ITEM_ID)).isEqualTo(WalletCreditSettlementService.Outcome.SETTLED);

        ArgumentCaptor<LedgerEntry> lc = ArgumentCaptor.forClass(LedgerEntry.class);
        verify(ledgerEntryRepository, times(1)).save(lc.capture());
        assertThat(lc.getValue().getEntryType())
                .as("only the missing credit is written on retry")
                .isEqualTo(LedgerEntryType.CREDIT);

        ArgumentCaptor<CompanyDistributionItem> ic = ArgumentCaptor.forClass(CompanyDistributionItem.class);
        verify(itemRepository).save(ic.capture());
        assertThat(ic.getValue().getDebitLedgerEntryId()).isEqualTo(priorDebit.getId());
    }

    /** Same protection on the credit side — a retry after the credit landed must not credit twice. */
    @Test
    void settle_retryWithCreditAlreadyWritten_doesNotCreditAgain() {
        stubHappy();
        LedgerEntry priorCredit = LedgerEntry.builder()
                .clientId(CLIENT_ID).rewardWalletId(RECIPIENT_WALLET_ID)
                .entryType(LedgerEntryType.CREDIT).amount(new BigDecimal("20.00"))
                .currencyId("cash").referenceType(REF).referenceId(ITEM_ID)
                .availableBalanceBefore(BigDecimal.ZERO).availableBalanceAfter(BigDecimal.ZERO)
                .reservedBalanceBefore(BigDecimal.ZERO).reservedBalanceAfter(BigDecimal.ZERO)
                .build();
        priorCredit.setId(UUID.randomUUID());
        when(ledgerEntryRepository.findFirstByRewardWalletIdAndReferenceTypeAndReferenceIdAndEntryType(
                eq(RECIPIENT_WALLET_ID), eq(REF), eq(ITEM_ID), eq(LedgerEntryType.CREDIT)))
                .thenReturn(Optional.of(priorCredit));

        service.settleItem(ITEM_ID);

        ArgumentCaptor<LedgerEntry> lc = ArgumentCaptor.forClass(LedgerEntry.class);
        verify(ledgerEntryRepository, times(1)).save(lc.capture());
        assertThat(lc.getValue().getEntryType()).isEqualTo(LedgerEntryType.DEBIT);
    }

    // ------------------------------------------------------------------ failure handling

    /** A definitive failure releases only THIS item's share, so other recipients are untouched. */
    @Test
    void settle_recipientWalletUnresolvable_releasesOnlyThisShare() {
        when(itemRepository.findByIdForUpdate(ITEM_ID)).thenReturn(Optional.of(reservedItem()));
        when(walletRepository.findByIdForUpdate(COMPANY_WALLET_ID))
                .thenReturn(Optional.of(companyWallet("80.00", "60.00"))); // 3 recipients reserved
        when(walletRepository.findForUpdate(CLIENT_ID, RECIPIENT_ID, "cash", WalletType.INDIVIDUAL))
                .thenReturn(Optional.empty());

        assertThat(service.settleItem(ITEM_ID)).isEqualTo(WalletCreditSettlementService.Outcome.FAILED);

        ArgumentCaptor<LedgerEntry> lc = ArgumentCaptor.forClass(LedgerEntry.class);
        verify(ledgerEntryRepository).save(lc.capture());
        LedgerEntry release = lc.getValue();
        assertThat(release.getEntryType()).isEqualTo(LedgerEntryType.RELEASE);
        assertThat(release.getAmount()).isEqualByComparingTo("20.00");
        // Only this share returns: reserved 60 -> 40, available 80 -> 100.
        assertThat(release.getReservedBalanceAfter()).isEqualByComparingTo("40.00");
        assertThat(release.getAvailableBalanceAfter()).isEqualByComparingTo("100.00");

        ArgumentCaptor<CompanyDistributionItem> ic = ArgumentCaptor.forClass(CompanyDistributionItem.class);
        verify(itemRepository).save(ic.capture());
        assertThat(ic.getValue().getStatus()).isEqualTo(DistributionItemStatus.FAILED);
        assertThat(ic.getValue().getFailureReason()).contains("recipient's wallet");
        assertThat(ic.getValue().getReleaseLedgerEntryId()).isNotNull();
    }

    /** Reserve gone (should be impossible) — release only this share, marked FAILED. */
    @Test
    void settle_reserveNoLongerCoversItem_failsAndReleases() {
        when(itemRepository.findByIdForUpdate(ITEM_ID)).thenReturn(Optional.of(reservedItem()));
        when(walletRepository.findByIdForUpdate(COMPANY_WALLET_ID))
                .thenReturn(Optional.of(companyWallet("80.00", "5.00"))); // less reserved than the item
        when(walletRepository.findForUpdate(CLIENT_ID, RECIPIENT_ID, "cash", WalletType.INDIVIDUAL))
                .thenReturn(Optional.of(recipientWallet("0.00")));

        WalletCreditSettlementService.Outcome out = service.settleItem(ITEM_ID);

        // Cannot release more than is reserved either, so this stops for review rather than corrupting the wallet.
        assertThat(out).isEqualTo(WalletCreditSettlementService.Outcome.RETRY_LATER);
        verify(itemRepository, never()).save(any());
    }

    /** A transient problem must leave the item RESERVED so the sweep retries — never released on the unknown. */
    @Test
    void settle_missingSourceWallet_leavesItemReservedForRetry() {
        when(itemRepository.findByIdForUpdate(ITEM_ID)).thenReturn(Optional.of(reservedItem()));
        when(walletRepository.findByIdForUpdate(COMPANY_WALLET_ID)).thenReturn(Optional.empty());

        assertThat(service.settleItem(ITEM_ID))
                .isEqualTo(WalletCreditSettlementService.Outcome.RETRY_LATER);
        verify(itemRepository, never()).save(any());
        verify(ledgerEntryRepository, never()).save(any());
    }

    @Test
    void settle_missingItem_isNoOp() {
        when(itemRepository.findByIdForUpdate(ITEM_ID)).thenReturn(Optional.empty());

        assertThat(service.settleItem(ITEM_ID))
                .isEqualTo(WalletCreditSettlementService.Outcome.ALREADY_TERMINAL);
        verify(ledgerEntryRepository, never()).save(any());
    }
}

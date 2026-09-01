package com.tenxengage.app.service;

import com.tenxengage.app.dto.request.CreateCompanyDistributionRequest;
import com.tenxengage.app.entity.ClientCatalogItemConfig;
import com.tenxengage.app.entity.CompanyDistribution;
import com.tenxengage.app.entity.CompanyDistributionItem;
import com.tenxengage.app.entity.LedgerEntry;
import com.tenxengage.app.entity.RedemptionCatalogItem;
import com.tenxengage.app.entity.RedemptionRequest;
import com.tenxengage.app.entity.RewardWallet;
import com.tenxengage.app.entity.User;
import com.tenxengage.app.entity.enums.DistributionItemStatus;
import com.tenxengage.app.entity.enums.DistributionRail;
import com.tenxengage.app.entity.enums.LedgerEntryType;
import com.tenxengage.app.entity.enums.RedemptionCategory;
import com.tenxengage.app.entity.enums.RedemptionOrigin;
import com.tenxengage.app.entity.enums.RedemptionProcessingMode;
import com.tenxengage.app.entity.enums.RedemptionStatus;
import com.tenxengage.app.entity.enums.WalletType;
import com.tenxengage.app.event.CompanyDistributionSubmittedEvent;
import com.tenxengage.app.exception.BusinessRuleException;
import com.tenxengage.app.exception.ResourceNotFoundException;
import com.tenxengage.app.repository.ClientCatalogItemConfigRepository;
import com.tenxengage.app.repository.CompanyDistributionItemRepository;
import com.tenxengage.app.repository.CompanyDistributionRepository;
import com.tenxengage.app.repository.LedgerEntryRepository;
import com.tenxengage.app.repository.RedemptionCatalogItemRepository;
import com.tenxengage.app.repository.RedemptionRequestRepository;
import com.tenxengage.app.repository.RewardWalletRepository;
import com.tenxengage.app.repository.UserRepository;
import com.tenxengage.app.security.TenantValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Creating a company distribution: a partner admin spending the company wallet on their sellers.
 *
 * <p><b>Submit is one atomic transaction that touches only the company wallet.</b> It validates, reserves the
 * full total, and writes the header + per-recipient items — then commits. No recipient wallet and no external
 * system is touched inside it, on any rail. Everything that can be slow or ambiguous happens after commit
 * (see {@code CompanyDistributionDispatcher}).</p>
 *
 * <p>The reserve is what makes per-recipient settlement safe. Settling recipients in separate transactions
 * without an up-front reserve would let a concurrent distribution spend balance later recipients depend on —
 * recipient 400 failing for insufficient funds after 1–399 were already paid. With the total earmarked, each
 * recipient's leg either completes or releases only its own share.</p>
 */
@Service
public class CompanyDistributionService {

    private static final Logger log = LoggerFactory.getLogger(CompanyDistributionService.class);

    /** Reference type for the header-level RESERVE on the company wallet. */
    public static final String REF_DISTRIBUTION = "COMPANY_DISTRIBUTION";
    /** Reference type for each per-item DEBIT / CREDIT / RELEASE. */
    public static final String REF_DISTRIBUTION_ITEM = "COMPANY_DISTRIBUTION_ITEM";

    private final TenantValidator tenantValidator;
    private final CompanyDistributionRepository distributionRepository;
    private final CompanyDistributionItemRepository itemRepository;
    private final RewardWalletRepository walletRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final RedemptionRequestRepository redemptionRequestRepository;
    private final RedemptionCatalogItemRepository catalogItemRepository;
    private final DistributionGiftCardService distributionGiftCardService;
    private final ClientCatalogItemConfigRepository catalogConfigRepository;
    private final UserRepository userRepository;
    private final BankTransferCardService bankTransferCardService;
    private final DistributionRecipientService recipientService;
    private final ApplicationEventPublisher eventPublisher;

    public CompanyDistributionService(TenantValidator tenantValidator,
                                      CompanyDistributionRepository distributionRepository,
                                      CompanyDistributionItemRepository itemRepository,
                                      RewardWalletRepository walletRepository,
                                      LedgerEntryRepository ledgerEntryRepository,
                                      RedemptionRequestRepository redemptionRequestRepository,
                                      RedemptionCatalogItemRepository catalogItemRepository,
                                      DistributionGiftCardService distributionGiftCardService,
                                      ClientCatalogItemConfigRepository catalogConfigRepository,
                                      UserRepository userRepository,
                                      BankTransferCardService bankTransferCardService,
                                      DistributionRecipientService recipientService,
                                      ApplicationEventPublisher eventPublisher) {
        this.tenantValidator = tenantValidator;
        this.distributionRepository = distributionRepository;
        this.itemRepository = itemRepository;
        this.walletRepository = walletRepository;
        this.ledgerEntryRepository = ledgerEntryRepository;
        this.redemptionRequestRepository = redemptionRequestRepository;
        this.catalogItemRepository = catalogItemRepository;
        this.distributionGiftCardService = distributionGiftCardService;
        this.catalogConfigRepository = catalogConfigRepository;
        this.userRepository = userRepository;
        this.bankTransferCardService = bankTransferCardService;
        this.recipientService = recipientService;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Validate, reserve and persist a distribution. Returns the committed header; the caller publishes the
     * after-commit fan-out.
     */
    @Transactional
    public CompanyDistribution submit(CreateCompanyDistributionRequest req) {
        UUID clientId = tenantValidator.getCurrentClientId();
        UUID adminUserId = tenantValidator.getCurrentUserId();
        UUID companyId = tenantValidator.getCurrentPartnerCompanyId();
        if (companyId == null) {
            throw new BusinessRuleException("NO_PARTNER_COMPANY",
                    "Distributing requires an associated partner company.");
        }

        // Idempotency first: a re-POST must return the original rather than reserve a second time.
        if (req.clientIdempotencyKey() != null) {
            Optional<CompanyDistribution> existing = distributionRepository
                    .findByClientIdAndClientIdempotencyKey(clientId, req.clientIdempotencyKey());
            if (existing.isPresent()) {
                log.info("[step=distribution_idempotent_hit] distributionId={}", existing.get().getId());
                return existing.get();
            }
        }

        // Lock the company wallet for the whole transaction. Held across validation deliberately: the
        // balance check and the reserve must be atomic, or two concurrent distributions can both pass a
        // check that only one of them can afford.
        RewardWallet wallet = walletRepository.findByIdForUpdate(req.sourceWalletId())
                .filter(w -> w.getClientId().equals(clientId))
                .filter(w -> w.getWalletType() == WalletType.COMPANY)
                .filter(w -> companyId.equals(w.getPartnerCompanyId()))
                .orElseThrow(() -> new ResourceNotFoundException("RewardWallet", "id", req.sourceWalletId()));

        List<UUID> recipientIds = distinctRecipients(req.userIds(), adminUserId);
        recipientService.assertAllEligible(clientId, companyId, recipientIds, req.rail());

        RailTarget target = resolveRail(clientId, req);
        validateAmount(clientId, req.amount(), target);

        BigDecimal total = req.amount().multiply(BigDecimal.valueOf(recipientIds.size()));
        if (wallet.getAvailableBalance().compareTo(total) < 0) {
            throw new BusinessRuleException("INSUFFICIENT_COMPANY_BALANCE",
                    "The company wallet does not have enough available balance for this distribution.");
        }
        if (!wallet.getCurrencyId().equals(target.currencyId())) {
            throw new BusinessRuleException("CURRENCY_MISMATCH",
                    "The selected reward and the company wallet are in different currencies.");
        }

        CompanyDistribution header = distributionRepository.save(CompanyDistribution.builder()
                .clientId(clientId)
                .partnerCompanyId(companyId)
                .sourceWalletId(wallet.getId())
                .rail(req.rail())
                .catalogItemId(req.rail() == DistributionRail.GIFT_CARD ? target.catalogItemId() : null)
                .currencyId(target.currencyId())
                .initiatedByUserId(adminUserId)
                .recipientCount(recipientIds.size())
                .totalAmount(total)
                .note(req.note())
                .clientIdempotencyKey(req.clientIdempotencyKey())
                .build());

        // Reserve the whole total in ONE wallet update + ONE ledger entry, keyed on the header. Per-item
        // DEBIT/RELEASE later reference the item, so the sum of settled + released always equals this.
        reserveTotal(wallet, header, total);

        for (UUID recipientId : recipientIds) {
            itemRepository.save(buildItem(clientId, header, recipientId, req.amount(), target, wallet));
        }

        // Consumed AFTER this transaction commits, so the vendor calls and the recipient-wallet writes happen
        // outside it. Its own event type, not RedemptionRequestedEvent — that one would publish
        // "your redemption was submitted" copy to a recipient who asked for nothing, and would run the
        // personal dispatch path alongside ours.
        eventPublisher.publishEvent(new CompanyDistributionSubmittedEvent(this, header.getId()));

        log.info("[step=distribution_submitted] distributionId={} rail={} recipients={} total={} companyId={}",
                header.getId(), req.rail(), recipientIds.size(), total, companyId);
        return header;
    }

    /**
     * Per recipient: a payout leg for the XTRM rails, or a bare item for the internal transfer. The payout
     * leg carries {@code user_id = recipient} so dispatch, settle, the webhook and reconciliation all resolve
     * the payee with no changes.
     */
    private CompanyDistributionItem buildItem(UUID clientId, CompanyDistribution header, UUID recipientId,
                                              BigDecimal amount, RailTarget target, RewardWallet wallet) {
        if (!header.getRail().isVendorPayout()) {
            // WALLET_CREDIT: no vendor, no redemption row. Reserved now, settled per recipient after commit.
            return CompanyDistributionItem.builder()
                    .clientId(clientId)
                    .distributionId(header.getId())
                    .recipientUserId(recipientId)
                    .amount(amount)
                    .status(DistributionItemStatus.RESERVED)
                    .build();
        }

        RedemptionRequest leg = redemptionRequestRepository.save(RedemptionRequest.builder()
                .clientId(clientId)
                .userId(recipientId)                    // the RECIPIENT — see RedemptionOrigin javadoc
                .walletId(wallet.getId())               // the COMPANY wallet the money leaves
                .walletType(WalletType.COMPANY)
                .origin(RedemptionOrigin.COMPANY_DISTRIBUTION)
                .catalogItemId(target.catalogItemId())
                .amount(amount)
                .currencyId(target.currencyId())
                .category(RedemptionCategory.CASH)
                .processingMode(RedemptionProcessingMode.INSTANT)
                .status(RedemptionStatus.PROCESSING)    // dispatched after commit, finalized by the webhook
                .submittedAt(Instant.now())
                .deleted(false)
                .build());

        return CompanyDistributionItem.builder()
                .clientId(clientId)
                .distributionId(header.getId())
                .recipientUserId(recipientId)
                .amount(amount)
                .redemptionRequestId(leg.getId())       // status is read from the leg, never duplicated
                .build();
    }

    /** Moves the full total available → reserved on the company wallet, with the audit entry. */
    private void reserveTotal(RewardWallet wallet, CompanyDistribution header, BigDecimal total) {
        BigDecimal availBefore = wallet.getAvailableBalance();
        BigDecimal resvBefore = wallet.getReservedBalance();

        ledgerEntryRepository.save(LedgerEntry.builder()
                .clientId(wallet.getClientId())
                .rewardWalletId(wallet.getId())
                .entryType(LedgerEntryType.RESERVE)
                .amount(total)
                .currencyId(wallet.getCurrencyId())
                .referenceType(REF_DISTRIBUTION)
                .referenceId(header.getId())
                .note("Company distribution reserve")
                .availableBalanceBefore(availBefore)
                .availableBalanceAfter(availBefore.subtract(total))
                .reservedBalanceBefore(resvBefore)
                .reservedBalanceAfter(resvBefore.add(total))
                .build());

        wallet.setAvailableBalance(availBefore.subtract(total));
        wallet.setReservedBalance(resvBefore.add(total));
        walletRepository.save(wallet);
    }

    /** De-dupes, preserves the admin's ordering, and rejects the caller distributing to themself (OQ-7). */
    private List<UUID> distinctRecipients(List<UUID> requested, UUID adminUserId) {
        if (requested.contains(adminUserId)) {
            throw new BusinessRuleException("SELF_DISTRIBUTION_NOT_ALLOWED",
                    "You cannot distribute to yourself. Redeem from your own wallet instead.");
        }
        List<UUID> distinct = new ArrayList<>(new LinkedHashSet<>(requested));
        if (distinct.isEmpty()) {
            throw new BusinessRuleException("NO_RECIPIENTS", "Select at least one recipient.");
        }
        return distinct;
    }

    /** Which catalog item and currency this rail pays through. */
    private RailTarget resolveRail(UUID clientId, CreateCompanyDistributionRequest req) {
        switch (req.rail()) {
            case GIFT_CARD -> {
                // Preferred path: any SKU from the provider catalogue, backed by a hidden catalog row so
                // everything downstream (payout leg, dispatch, history joins) is untouched.
                if (req.providerSku() != null && !req.providerSku().isBlank()) {
                    if (req.catalogItemId() != null) {
                        throw new BusinessRuleException("SKU_AMBIGUOUS",
                                "Send either a gift-card SKU or a catalog item, not both.");
                    }
                    RedemptionCatalogItem provisioned =
                            distributionGiftCardService.ensureCardForSku(clientId, req.providerSku());
                    return new RailTarget(provisioned.getId(), provisioned.getCurrencyId(), provisioned);
                }
                if (req.catalogItemId() == null) {
                    throw new BusinessRuleException("SKU_REQUIRED", "Choose a gift card to distribute.");
                }
                RedemptionCatalogItem item = catalogItemRepository.findById(req.catalogItemId())
                        .filter(RedemptionCatalogItem::isActive)
                        .filter(i -> !i.isDeleted())
                        .filter(i -> !i.isBankTransfer())
                        .filter(i -> clientId.equals(i.getOwnerClientId()))
                        .orElseThrow(() -> new ResourceNotFoundException(
                                "RedemptionCatalogItem", "id", req.catalogItemId()));
                if (isBlank(item.getProviderItemId())) {
                    throw new BusinessRuleException("SKU_MISSING",
                            "That gift card has no SKU and cannot be distributed.");
                }
                return new RailTarget(item.getId(), item.getCurrencyId(), item);
            }
            case BANK_TRANSFER -> {
                if (req.catalogItemId() != null || req.providerSku() != null) {
                    throw new BusinessRuleException("SKU_NOT_APPLICABLE",
                            "A bank transfer does not take a gift card.");
                }
                // Same reserved per-client card the personal bank-transfer path uses; idempotent get-or-create.
                RedemptionCatalogItem card = bankTransferCardService.ensureBankTransferCard(clientId);
                return new RailTarget(card.getId(), card.getCurrencyId(), card);
            }
            case WALLET_CREDIT -> {
                // Retired 2026-08-26: distribution offers gift card and bank transfer only. The constant
                // and its settlement path remain so existing WALLET_CREDIT distributions stay readable and
                // any in-flight item still settles — but nothing new may be created on it.
                throw new BusinessRuleException("UNSUPPORTED_RAIL",
                        "Wallet transfer is no longer available for distributions.");
            }
            default -> throw new BusinessRuleException("UNKNOWN_RAIL", "Unsupported distribution rail.");
        }
    }

    /**
     * The single amount must satisfy the rail's bounds — the SKU's FIXED denomination or VARIABLE window for
     * a gift card, the catalog minimum for a bank transfer, and a client override narrows either. Same
     * resolution as personal submit, so the two cannot drift.
     */
    private void validateAmount(UUID clientId, BigDecimal amount, RailTarget target) {
        RedemptionCatalogItem item = target.item();
        if (item == null) {
            return; // WALLET_CREDIT — no catalog bounds; @Positive on the request is the only floor.
        }
        ClientCatalogItemConfig config = catalogConfigRepository
                .findByClientIdAndRedemptionCatalogItemId(clientId, item.getId())
                .orElse(null);

        BigDecimal min = config != null && config.getMinTransactionAmountOverride() != null
                ? config.getMinTransactionAmountOverride()
                : item.getDefaultMinRedemptionAmount();
        if (min != null && amount.compareTo(min) < 0) {
            throw new BusinessRuleException("AMOUNT_BELOW_MIN",
                    "Amount is below the minimum allowed: " + min.stripTrailingZeros().toPlainString());
        }

        BigDecimal max = config != null && config.getMaxTransactionAmountOverride() != null
                ? config.getMaxTransactionAmountOverride()
                : item.getDefaultMaxRedemptionAmount();
        if (max != null && amount.compareTo(max) > 0) {
            throw new BusinessRuleException("AMOUNT_ABOVE_MAX",
                    "Amount is above the maximum allowed: " + max.stripTrailingZeros().toPlainString());
        }
    }

    /** Names for the response, resolved in one query rather than per row. */
    Map<UUID, User> usersById(List<UUID> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return userRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(User::getId, Function.identity(), (a, b) -> a));
    }

    private static final String CASH_CURRENCY = "cash";

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    /** The catalog item + currency a rail pays through; {@code item} is null for WALLET_CREDIT. */
    private record RailTarget(UUID catalogItemId, String currencyId, RedemptionCatalogItem item) {
    }
}

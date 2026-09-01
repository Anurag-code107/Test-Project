package com.tenxengage.app.service;

import com.tenxengage.app.dto.request.FundCompanyWalletRequest;
import com.tenxengage.app.entity.PartnerCompany;
import com.tenxengage.app.entity.RewardWallet;
import com.tenxengage.app.entity.enums.WalletType;
import com.tenxengage.app.exception.ResourceNotFoundException;
import com.tenxengage.app.repository.PartnerCompanyRepository;
import com.tenxengage.app.security.TenantValidator;
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
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CompanyWalletFundingServiceTest {

    @Mock private WalletService walletService;
    @Mock private PartnerCompanyRepository partnerCompanyRepository;
    @Mock private TenantValidator tenantValidator;

    @InjectMocks private CompanyWalletFundingService service;

    private static final UUID CLIENT_ID = UUID.randomUUID();
    private static final UUID COMPANY_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        when(tenantValidator.getCurrentClientId()).thenReturn(CLIENT_ID);
        PartnerCompany pc = new PartnerCompany();
        pc.setId(COMPANY_ID);
        pc.setClientId(CLIENT_ID);
        when(partnerCompanyRepository.findByIdAndClientId(COMPANY_ID, CLIENT_ID)).thenReturn(Optional.of(pc));
        when(walletService.creditCompany(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(wallet("500.00"));
    }

    private RewardWallet wallet(String available) {
        RewardWallet w = RewardWallet.builder()
                .clientId(CLIENT_ID).partnerCompanyId(COMPANY_ID).walletType(WalletType.COMPANY)
                .currencyId("cash").availableBalance(new BigDecimal(available))
                .reservedBalance(BigDecimal.ZERO).build();
        w.setId(UUID.randomUUID());
        return w;
    }

    private FundCompanyWalletRequest req(String amount, String reference) {
        return new FundCompanyWalletRequest("cash", new BigDecimal(amount), reference, null);
    }

    @Test
    void fund_creditsTheCompanyWalletWithTheFundingReferenceType() {
        service.fund(COMPANY_ID, req("1000.00", "PO-4471"));

        verify(walletService).creditCompany(
                eq(CLIENT_ID), eq(COMPANY_ID), eq("cash"), eq(new BigDecimal("1000.00")),
                eq("COMPANY_WALLET_FUNDING"), any(UUID.class), any());
    }

    /**
     * The whole point of requiring a reference: the same one must resolve to the same UUID every time, because
     * that UUID is what the ledger's unique index deduplicates on. If this drifted, a retry would double-fund
     * the one endpoint that creates balance from nothing.
     */
    @Test
    void referenceToUuid_isStableForTheSameReference() {
        UUID a = CompanyWalletFundingService.referenceToUuid(CLIENT_ID, COMPANY_ID, "PO-4471");
        UUID b = CompanyWalletFundingService.referenceToUuid(CLIENT_ID, COMPANY_ID, "PO-4471");
        assertThat(a).isEqualTo(b);
    }

    /** Surrounding whitespace must not create a second identity for the same funding. */
    @Test
    void referenceToUuid_ignoresSurroundingWhitespace() {
        assertThat(CompanyWalletFundingService.referenceToUuid(CLIENT_ID, COMPANY_ID, "  PO-4471 "))
                .isEqualTo(CompanyWalletFundingService.referenceToUuid(CLIENT_ID, COMPANY_ID, "PO-4471"));
    }

    /** Two companies must be able to use "PO-1" without one blocking the other. */
    @Test
    void referenceToUuid_isScopedPerCompany() {
        UUID other = UUID.randomUUID();
        assertThat(CompanyWalletFundingService.referenceToUuid(CLIENT_ID, COMPANY_ID, "PO-1"))
                .isNotEqualTo(CompanyWalletFundingService.referenceToUuid(CLIENT_ID, other, "PO-1"));
    }

    /** And two tenants likewise. */
    @Test
    void referenceToUuid_isScopedPerClient() {
        assertThat(CompanyWalletFundingService.referenceToUuid(CLIENT_ID, COMPANY_ID, "PO-1"))
                .isNotEqualTo(CompanyWalletFundingService.referenceToUuid(UUID.randomUUID(), COMPANY_ID, "PO-1"));
    }

    /** Different references are genuinely different fundings and must both land. */
    @Test
    void referenceToUuid_differsForDifferentReferences() {
        assertThat(CompanyWalletFundingService.referenceToUuid(CLIENT_ID, COMPANY_ID, "PO-1"))
                .isNotEqualTo(CompanyWalletFundingService.referenceToUuid(CLIENT_ID, COMPANY_ID, "PO-2"));
    }

    /** A company from another tenant must not be fundable, even with a valid-looking id. */
    @Test
    void fund_companyOutsideTenant_is404_andCreditsNothing() {
        when(partnerCompanyRepository.findByIdAndClientId(COMPANY_ID, CLIENT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.fund(COMPANY_ID, req("100.00", "PO-1")))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(walletService, never()).creditCompany(any(), any(), any(), any(), any(), any(), any());
    }

    /** With no note, the reference is carried into the ledger note so the entry is self-explanatory. */
    @Test
    void fund_withoutNote_recordsTheReferenceOnTheLedgerEntry() {
        service.fund(COMPANY_ID, req("250.00", "PO-9"));

        ArgumentCaptor<String> note = ArgumentCaptor.forClass(String.class);
        verify(walletService).creditCompany(any(), any(), any(), any(), any(), any(), note.capture());
        assertThat(note.getValue()).contains("PO-9");
    }
}

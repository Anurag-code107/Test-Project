package com.tenxengage.app.service.xtrm;

import com.tenxengage.app.entity.CompanyDistribution;
import com.tenxengage.app.entity.CompanyDistributionItem;
import com.tenxengage.app.exception.BusinessRuleException;
import com.tenxengage.app.repository.CompanyDistributionItemRepository;
import com.tenxengage.app.repository.CompanyDistributionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Who pays for a given redemption.
 *
 * <p>Two things must hold. A distribution leg pays from its own company, or XTRM rejects the wallet against
 * the authenticated account. And a personal redemption keeps paying from the platform, byte for byte —
 * that path has real successful payouts behind it, and this feature must not disturb it.</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class XtrmRemitterResolverTest {

    @Mock private CompanyDistributionItemRepository itemRepository;
    @Mock private CompanyDistributionRepository distributionRepository;
    @Mock private XtrmCredentialsResolver credentialsResolver;
    @InjectMocks private XtrmRemitterResolver resolver;

    private static final UUID REDEMPTION_ID = UUID.randomUUID();
    private static final UUID CLIENT_ID = UUID.randomUUID();
    private static final UUID COMPANY_ID = UUID.randomUUID();
    private static final UUID DISTRIBUTION_ID = UUID.randomUUID();

    private final XtrmCredentials platform =
            new XtrmCredentials("platform-id", "s", "SPN26237883", "203871", "2314");
    private final XtrmCredentials company =
            new XtrmCredentials("company-id", "s", "SPN26241004", "206415", "2314");

    private void isADistributionLeg() {
        CompanyDistributionItem item = new CompanyDistributionItem();
        item.setDistributionId(DISTRIBUTION_ID);
        when(itemRepository.findByRedemptionRequestId(REDEMPTION_ID)).thenReturn(Optional.of(item));

        CompanyDistribution distribution = new CompanyDistribution();
        distribution.setClientId(CLIENT_ID);
        distribution.setPartnerCompanyId(COMPANY_ID);
        when(distributionRepository.findById(DISTRIBUTION_ID)).thenReturn(Optional.of(distribution));
    }

    @Test
    void aDistributionLegPaysFromItsCompany() {
        isADistributionLeg();
        when(credentialsResolver.forCompany(CLIENT_ID, COMPANY_ID)).thenReturn(company);

        assertThat(resolver.forRedemption(REDEMPTION_ID)).isEqualTo(company);
    }

    @Test
    void aPersonalRedemptionPaysFromThePlatform() {
        when(itemRepository.findByRedemptionRequestId(REDEMPTION_ID)).thenReturn(Optional.empty());
        when(credentialsResolver.platform()).thenReturn(platform);

        assertThat(resolver.forRedemption(REDEMPTION_ID)).isEqualTo(platform);
        verify(credentialsResolver, never()).forCompany(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void anOrphanedItemFallsBackToThePlatformRatherThanFailing() {
        CompanyDistributionItem item = new CompanyDistributionItem();
        item.setDistributionId(DISTRIBUTION_ID);
        when(itemRepository.findByRedemptionRequestId(REDEMPTION_ID)).thenReturn(Optional.of(item));
        when(distributionRepository.findById(DISTRIBUTION_ID)).thenReturn(Optional.empty());
        when(credentialsResolver.platform()).thenReturn(platform);

        assertThat(resolver.forRedemption(REDEMPTION_ID)).isEqualTo(platform);
    }

    /**
     * Never silently falls back for a company that is not connected. A fallback would look like success
     * while moving the client's money instead of the company's.
     */
    @Test
    void propagatesTheNotConnectedFailureRatherThanFallingBack() {
        isADistributionLeg();
        when(credentialsResolver.forCompany(CLIENT_ID, COMPANY_ID))
                .thenThrow(new BusinessRuleException("XTRM_COMPANY_NOT_CONNECTED", "not set up"));

        assertThatThrownBy(() -> resolver.forRedemption(REDEMPTION_ID))
                .isInstanceOf(BusinessRuleException.class);

        verify(credentialsResolver, never()).platform();
    }
}

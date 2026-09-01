package com.tenxengage.app.service.xtrm;

import com.tenxengage.app.repository.CompanyDistributionItemRepository;
import com.tenxengage.app.repository.CompanyDistributionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * The single answer to "which XTRM account pays for this redemption?"
 *
 * <p>Dispatch and reconciliation both ask, and they must get the same answer. Two implementations that
 * merely agree today would drift, and the drift is invisible until it strands money: reconciliation would
 * poll as the wrong account, never find the transaction, and leave the item {@code PROCESSING} with the
 * recipient's share reserved forever. That is worse than either failing or releasing, because nothing
 * reports it.</p>
 */
@Service
public class XtrmRemitterResolver {

    private final CompanyDistributionItemRepository itemRepository;
    private final CompanyDistributionRepository distributionRepository;
    private final XtrmCredentialsResolver credentialsResolver;

    public XtrmRemitterResolver(CompanyDistributionItemRepository itemRepository,
                                CompanyDistributionRepository distributionRepository,
                                XtrmCredentialsResolver credentialsResolver) {
        this.itemRepository = itemRepository;
        this.distributionRepository = distributionRepository;
        this.credentialsResolver = credentialsResolver;
    }

    /**
     * Company credentials for a distribution leg, platform credentials for everything else.
     *
     * <p>No distribution row means a personal redemption, which must keep behaving exactly as it does today
     * — that path has real payouts behind it and this feature must not disturb it.</p>
     *
     * <p>{@code forCompany} throws for a company that is not connected rather than falling back to the
     * platform. That is deliberate: a fallback would look like success while paying the seller out of the
     * <em>client's</em> money — a real transfer from the wrong pocket that nothing downstream would notice.</p>
     */
    @Transactional(readOnly = true)
    public XtrmCredentials forRedemption(UUID redemptionRequestId) {
        return itemRepository.findByRedemptionRequestId(redemptionRequestId)
                .flatMap(item -> distributionRepository.findById(item.getDistributionId()))
                .map(d -> credentialsResolver.forCompany(d.getClientId(), d.getPartnerCompanyId()))
                .orElseGet(credentialsResolver::platform);
    }
}

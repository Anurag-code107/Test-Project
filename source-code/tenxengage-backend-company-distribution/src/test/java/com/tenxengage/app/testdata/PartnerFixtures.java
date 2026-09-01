package com.tenxengage.app.testdata;

import com.tenxengage.app.entity.PartnerCompany;

import java.util.UUID;

public final class PartnerFixtures {

    private PartnerFixtures() {
    }

    public static PartnerCompany.PartnerCompanyBuilder activeReseller(UUID clientId) {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        return PartnerCompany.builder()
                .name("Test Partner " + suffix)
                .clientId(clientId)
                .metadata("{\"Partner Type\":\"RESELLER\"}")
                .externalPartnerId("CT-TEST-" + suffix);
    }

    public static PartnerCompany.PartnerCompanyBuilder activeDistributor(UUID clientId) {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        return PartnerCompany.builder()
                .name("Distributor " + suffix)
                .clientId(clientId)
                .metadata("{\"Partner Type\":\"DISTRIBUTOR\"}")
                .externalPartnerId("CT-DIST-" + suffix);
    }
}

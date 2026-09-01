package com.tenxengage.app.testdata;

import com.tenxengage.app.entity.Client;
import com.tenxengage.app.entity.enums.ClientStatus;
import com.tenxengage.app.entity.enums.SubscriptionTier;

import java.util.UUID;

public final class ClientFixtures {

    private ClientFixtures() {
    }

    public static Client.ClientBuilder activeEnterprise() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        return Client.builder()
                .name("Test Client " + suffix)
                .subdomain("test-" + suffix)
                .status(ClientStatus.ACTIVE)
                .subscriptionTier(SubscriptionTier.ENTERPRISE);
    }

    public static Client.ClientBuilder activeProfessional() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        return Client.builder()
                .name("Pro Client " + suffix)
                .subdomain("pro-" + suffix)
                .status(ClientStatus.ACTIVE)
                .subscriptionTier(SubscriptionTier.PROFESSIONAL);
    }

    public static Client.ClientBuilder activeStarter() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        return Client.builder()
                .name("Starter Client " + suffix)
                .subdomain("starter-" + suffix)
                .status(ClientStatus.ACTIVE)
                .subscriptionTier(SubscriptionTier.STARTER);
    }

    public static Client.ClientBuilder suspended() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        return Client.builder()
                .name("Suspended Client " + suffix)
                .subdomain("suspended-" + suffix)
                .status(ClientStatus.SUSPENDED)
                .subscriptionTier(SubscriptionTier.ENTERPRISE);
    }
}

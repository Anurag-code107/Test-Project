package com.tenxengage.app.integration;

import com.tenxengage.app.dto.response.RewardWalletResponse;
import com.tenxengage.app.entity.RewardWallet;
import com.tenxengage.app.testdata.RewardWalletFixtures;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that RewardWalletResponse matches the shape defined in
 * tenxengage-contracts/endpoints/wallet.yaml: required fields present,
 * internal fields (clientId, userId, partnerCompanyId, version) absent.
 *
 * Plain unit test — no DB or Spring context needed.
 */
class WalletContractConformanceTest {

    private RewardWallet walletWithId(RewardWallet.RewardWalletBuilder builder) {
        RewardWallet w = builder.build();
        w.setId(UUID.randomUUID());
        return w;
    }

    @Test
    void rewardWalletResponse_hasAllContractRequiredFields() {
        RewardWallet wallet = walletWithId(
                RewardWalletFixtures.individualWalletWithBalance(
                        UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("150.00"))
                        .reservedBalance(new BigDecimal("30.00")));

        RewardWalletResponse response = RewardWalletResponse.from(wallet);

        assertThat(response.id()).isNotNull();
        assertThat(response.walletType()).isIn("INDIVIDUAL", "COMPANY");
        assertThat(response.currencyId()).isNotBlank();
        assertThat(response.availableBalance()).isNotNull();
        assertThat(response.reservedBalance()).isNotNull();
    }

    @Test
    void rewardWalletResponse_balancesAreDecimalStrings() {
        RewardWallet wallet = walletWithId(
                RewardWalletFixtures.individualWalletWithBalance(
                        UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("150.50"))
                        .reservedBalance(new BigDecimal("30.25")));

        RewardWalletResponse response = RewardWalletResponse.from(wallet);

        assertThat(response.availableBalance()).matches("[0-9]+\\.[0-9]+");
        assertThat(response.reservedBalance()).matches("[0-9]+\\.[0-9]+");
    }

    @Test
    void rewardWalletResponse_doesNotExposeInternalFields() {
        RewardWallet wallet = walletWithId(
                RewardWalletFixtures.individualWallet(UUID.randomUUID(), UUID.randomUUID()));

        RewardWalletResponse response = RewardWalletResponse.from(wallet);

        var components = response.getClass().getRecordComponents();
        assertThat(components).hasSize(5);

        java.util.Set<String> names = new java.util.HashSet<>();
        for (var c : components) names.add(c.getName());
        assertThat(names).containsExactlyInAnyOrder(
                "id", "walletType", "currencyId", "availableBalance", "reservedBalance");
        assertThat(names).doesNotContain("clientId", "userId", "partnerCompanyId", "version");
    }

    @Test
    void rewardWalletResponse_walletType_matchesContractEnum() {
        RewardWallet individual = walletWithId(
                RewardWalletFixtures.individualWallet(UUID.randomUUID(), UUID.randomUUID()));
        RewardWallet company = walletWithId(
                RewardWalletFixtures.companyWallet(UUID.randomUUID(), UUID.randomUUID()));

        assertThat(RewardWalletResponse.from(individual).walletType()).isEqualTo("INDIVIDUAL");
        assertThat(RewardWalletResponse.from(company).walletType()).isEqualTo("COMPANY");
    }
}

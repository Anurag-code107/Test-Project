package com.tenxengage.app.service.recommendation;

import com.tenxengage.app.dto.request.SaveRecommendationConfigRequest;
import com.tenxengage.app.dto.response.RecommendationConfigResponse;
import com.tenxengage.app.entity.RecommendationConfig;
import com.tenxengage.app.repository.RecommendationConfigRepository;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RecommendationConfigServiceTest {

    @Mock
    private RecommendationConfigRepository configRepo;

    @InjectMocks
    private RecommendationConfigService service;

    // -------------------------------------------------------------------------
    // getConfig
    // -------------------------------------------------------------------------

    @Test
    void getConfig_configExists_returnsFromMapping() {
        UUID clientId = UUID.randomUUID();
        UUID configId = UUID.randomUUID();

        RecommendationConfig config = RecommendationConfig.builder()
                .clientId(clientId)
                .trainingEnabled(true)
                .incentiveEnabled(false)
                .maxTrainingRecommendations(3)
                .maxIncentiveRecommendations(7)
                .rewardCurrencyId("USD")
                .trainingCompletionReward(new BigDecimal("10.00"))
                .incentiveCompletionReward(new BigDecimal("20.00"))
                .build();
        // Set the id via BaseEntity setter (Lombok @Setter on BaseEntity)
        config.setId(configId);

        when(configRepo.findByClientId(clientId)).thenReturn(Optional.of(config));

        RecommendationConfigResponse response = service.getConfig(clientId);

        assertThat(response).isNotNull();
        assertThat(response.id()).isEqualTo(configId);
        assertThat(response.trainingEnabled()).isTrue();
        assertThat(response.incentiveEnabled()).isFalse();
        assertThat(response.maxTrainingRecommendations()).isEqualTo(3);
        assertThat(response.maxIncentiveRecommendations()).isEqualTo(7);
        assertThat(response.rewardCurrencyId()).isEqualTo("USD");
        assertThat(response.trainingCompletionReward()).isEqualByComparingTo("10.00");
        assertThat(response.incentiveCompletionReward()).isEqualByComparingTo("20.00");
    }

    @Test
    void getConfig_configNotFound_returnsDefaults() {
        UUID clientId = UUID.randomUUID();
        when(configRepo.findByClientId(clientId)).thenReturn(Optional.empty());

        RecommendationConfigResponse response = service.getConfig(clientId);

        RecommendationConfigResponse expected = RecommendationConfigResponse.defaults();
        assertThat(response).isEqualTo(expected);
        assertThat(response.id()).isNull();
        assertThat(response.trainingEnabled()).isTrue();
        assertThat(response.incentiveEnabled()).isTrue();
        assertThat(response.maxTrainingRecommendations()).isEqualTo(5);
        assertThat(response.maxIncentiveRecommendations()).isEqualTo(5);
    }

    // -------------------------------------------------------------------------
    // saveConfig
    // -------------------------------------------------------------------------

    @Test
    void saveConfig_existingConfig_updatesAndSaves() {
        UUID clientId = UUID.randomUUID();
        UUID configId = UUID.randomUUID();

        RecommendationConfig existing = RecommendationConfig.builder()
                .clientId(clientId)
                .trainingEnabled(true)
                .incentiveEnabled(true)
                .maxTrainingRecommendations(5)
                .maxIncentiveRecommendations(5)
                .trainingCompletionReward(BigDecimal.ZERO)
                .incentiveCompletionReward(BigDecimal.ZERO)
                .build();
        existing.setId(configId);

        SaveRecommendationConfigRequest request = new SaveRecommendationConfigRequest(
                false, true, 8, 10, "EUR",
                new BigDecimal("15.00"), new BigDecimal("25.00")
        );

        when(configRepo.findByClientId(clientId)).thenReturn(Optional.of(existing));
        when(configRepo.save(any(RecommendationConfig.class))).thenAnswer(inv -> inv.getArgument(0));

        RecommendationConfigResponse response = service.saveConfig(clientId, request);

        ArgumentCaptor<RecommendationConfig> captor = ArgumentCaptor.forClass(RecommendationConfig.class);
        verify(configRepo).save(captor.capture());

        RecommendationConfig saved = captor.getValue();
        assertThat(saved.getClientId()).isEqualTo(clientId);
        assertThat(saved.isTrainingEnabled()).isFalse();
        assertThat(saved.isIncentiveEnabled()).isTrue();
        assertThat(saved.getMaxTrainingRecommendations()).isEqualTo(8);
        assertThat(saved.getMaxIncentiveRecommendations()).isEqualTo(10);
        assertThat(saved.getRewardCurrencyId()).isEqualTo("EUR");
        assertThat(saved.getTrainingCompletionReward()).isEqualByComparingTo("15.00");
        assertThat(saved.getIncentiveCompletionReward()).isEqualByComparingTo("25.00");

        // Response should reflect the updated values
        assertThat(response.trainingEnabled()).isFalse();
        assertThat(response.rewardCurrencyId()).isEqualTo("EUR");
    }

    @Test
    void saveConfig_noExistingConfig_createsNew() {
        UUID clientId = UUID.randomUUID();

        SaveRecommendationConfigRequest request = new SaveRecommendationConfigRequest(
                true, false, 4, 6, "GBP",
                new BigDecimal("5.00"), new BigDecimal("10.00")
        );

        when(configRepo.findByClientId(clientId)).thenReturn(Optional.empty());
        when(configRepo.save(any(RecommendationConfig.class))).thenAnswer(inv -> inv.getArgument(0));

        service.saveConfig(clientId, request);

        ArgumentCaptor<RecommendationConfig> captor = ArgumentCaptor.forClass(RecommendationConfig.class);
        verify(configRepo).save(captor.capture());

        RecommendationConfig created = captor.getValue();
        assertThat(created.getClientId()).isEqualTo(clientId);
        assertThat(created.isTrainingEnabled()).isTrue();
        assertThat(created.isIncentiveEnabled()).isFalse();
        assertThat(created.getMaxTrainingRecommendations()).isEqualTo(4);
        assertThat(created.getMaxIncentiveRecommendations()).isEqualTo(6);
        assertThat(created.getRewardCurrencyId()).isEqualTo("GBP");
        assertThat(created.getTrainingCompletionReward()).isEqualByComparingTo("5.00");
        assertThat(created.getIncentiveCompletionReward()).isEqualByComparingTo("10.00");
    }
}

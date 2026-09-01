package com.tenxengage.app.service.recommendation;

import com.tenxengage.app.dto.request.SaveRecommendationConfigRequest;
import com.tenxengage.app.dto.response.RecommendationConfigResponse;
import com.tenxengage.app.entity.RecommendationConfig;
import com.tenxengage.app.repository.RecommendationConfigRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class RecommendationConfigService {

    private static final Logger log = LoggerFactory.getLogger(RecommendationConfigService.class);

    private final RecommendationConfigRepository configRepo;

    public RecommendationConfigService(RecommendationConfigRepository configRepo) {
        this.configRepo = configRepo;
    }

    public RecommendationConfigResponse getConfig(UUID clientId) {
        return configRepo.findByClientId(clientId)
                .map(RecommendationConfigResponse::from)
                .orElse(RecommendationConfigResponse.defaults());
    }

    @Transactional
    public RecommendationConfigResponse saveConfig(UUID clientId, SaveRecommendationConfigRequest request) {
        RecommendationConfig config = configRepo.findByClientId(clientId)
                .orElseGet(() -> RecommendationConfig.builder()
                        .clientId(clientId)
                        .build());

        config.setTrainingEnabled(request.trainingEnabled());
        config.setIncentiveEnabled(request.incentiveEnabled());

        if (request.maxTrainingRecommendations() != null) {
            config.setMaxTrainingRecommendations(request.maxTrainingRecommendations());
        }
        if (request.maxIncentiveRecommendations() != null) {
            config.setMaxIncentiveRecommendations(request.maxIncentiveRecommendations());
        }
        if (request.rewardCurrencyId() != null) {
            config.setRewardCurrencyId(request.rewardCurrencyId());
        }
        if (request.trainingCompletionReward() != null) {
            config.setTrainingCompletionReward(request.trainingCompletionReward());
        }
        if (request.incentiveCompletionReward() != null) {
            config.setIncentiveCompletionReward(request.incentiveCompletionReward());
        }

        config = configRepo.save(config);
        log.info("Saved recommendation config for client {}", clientId);
        return RecommendationConfigResponse.from(config);
    }
}

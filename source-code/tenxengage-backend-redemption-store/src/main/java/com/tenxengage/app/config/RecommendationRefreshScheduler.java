package com.tenxengage.app.config;

import com.tenxengage.app.entity.Client;
import com.tenxengage.app.repository.ClientRepository;
import com.tenxengage.app.service.recommendation.RecommendationScoringService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class RecommendationRefreshScheduler {

    private static final Logger log = LoggerFactory.getLogger(RecommendationRefreshScheduler.class);

    private final ClientRepository clientRepository;
    private final RecommendationScoringService scoringService;

    public RecommendationRefreshScheduler(ClientRepository clientRepository,
                                           RecommendationScoringService scoringService) {
        this.clientRepository = clientRepository;
        this.scoringService = scoringService;
    }

    @Scheduled(cron = "0 30 2 * * *")
    public void refreshRecommendationScores() {
        log.info("Starting nightly recommendation score refresh");
        long start = System.currentTimeMillis();

        List<Client> clients = clientRepository.findAll();
        int success = 0;
        int failed = 0;

        for (Client client : clients) {
            try {
                scoringService.scoreTrainingForClient(client.getId());
                scoringService.scoreIncentivesForClient(client.getId());
                success++;
            } catch (Exception e) {
                failed++;
                log.error("Recommendation scoring failed for client {}: {}",
                        client.getId(), e.getMessage(), e);
            }
        }

        long elapsed = System.currentTimeMillis() - start;
        log.info("Recommendation refresh completed: {} succeeded, {} failed, {}ms total",
                success, failed, elapsed);
    }
}

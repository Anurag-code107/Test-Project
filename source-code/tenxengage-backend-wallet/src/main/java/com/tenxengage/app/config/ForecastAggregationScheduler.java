package com.tenxengage.app.config;

import com.tenxengage.app.entity.Client;
import com.tenxengage.app.repository.ClientRepository;
import com.tenxengage.app.service.forecast.ForecastAccuracyService;
import com.tenxengage.app.service.forecast.ForecastAggregationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ForecastAggregationScheduler {

    private static final Logger log = LoggerFactory.getLogger(ForecastAggregationScheduler.class);

    private final ClientRepository clientRepository;
    private final ForecastAggregationService aggregationService;
    private final ForecastAccuracyService accuracyService;

    public ForecastAggregationScheduler(ClientRepository clientRepository,
                                         ForecastAggregationService aggregationService,
                                         ForecastAccuracyService accuracyService) {
        this.clientRepository = clientRepository;
        this.aggregationService = aggregationService;
        this.accuracyService = accuracyService;
    }

    @Scheduled(cron = "0 0 2 * * *")
    public void refreshForecastAggregations() {
        log.info("Starting nightly forecast aggregation refresh");
        long start = System.currentTimeMillis();

        List<Client> clients = clientRepository.findAll();
        int success = 0;
        int failed = 0;

        for (Client client : clients) {
            try {
                aggregationService.aggregateForClient(client.getId());
                // Evaluate forecast accuracy for completed incentives
                accuracyService.evaluateForClient(client.getId());
                success++;
            } catch (Exception e) {
                failed++;
                log.error("Forecast aggregation failed for client {}: {}", client.getId(), e.getMessage(), e);
            }
        }

        long elapsed = System.currentTimeMillis() - start;
        log.info("Forecast aggregation refresh completed: {} succeeded, {} failed, {}ms total",
                success, failed, elapsed);
    }
}

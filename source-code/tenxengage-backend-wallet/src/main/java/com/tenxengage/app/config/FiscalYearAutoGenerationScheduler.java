package com.tenxengage.app.config;

import com.tenxengage.app.entity.Client;
import com.tenxengage.app.entity.FiscalYearConfig;
import com.tenxengage.app.repository.ClientRepository;
import com.tenxengage.app.repository.FiscalYearConfigRepository;
import com.tenxengage.app.service.FiscalYearConfigService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Component
public class FiscalYearAutoGenerationScheduler {

    private static final Logger log = LoggerFactory.getLogger(FiscalYearAutoGenerationScheduler.class);

    private final ClientRepository clientRepository;
    private final FiscalYearConfigRepository fiscalYearConfigRepository;
    private final FiscalYearConfigService fiscalYearConfigService;

    public FiscalYearAutoGenerationScheduler(ClientRepository clientRepository,
                                              FiscalYearConfigRepository fiscalYearConfigRepository,
                                              FiscalYearConfigService fiscalYearConfigService) {
        this.clientRepository = clientRepository;
        this.fiscalYearConfigRepository = fiscalYearConfigRepository;
        this.fiscalYearConfigService = fiscalYearConfigService;
    }

    @Scheduled(cron = "0 0 5 * * *")
    @Transactional
    public void generateUpcomingFiscalYears() {
        LocalDate threshold = LocalDate.now().plusDays(30);
        List<Client> clients = clientRepository.findAll();
        int generated = 0;

        for (Client client : clients) {
            List<FiscalYearConfig> configs = fiscalYearConfigRepository
                .findByClientIdOrderByStartDateAsc(client.getId());
            if (configs.isEmpty()) {
                continue;
            }

            FiscalYearConfig latest = configs.get(configs.size() - 1);
            if (!latest.getEndDate().isAfter(threshold)) {
                FiscalYearConfig next = fiscalYearConfigService.generateNextConfig(client.getId());
                if (next != null) {
                    generated++;
                }
            }
        }

        if (generated > 0) {
            log.info("Auto-generated {} fiscal year config(s)", generated);
        }
    }
}

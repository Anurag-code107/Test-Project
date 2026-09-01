package com.tenxengage.admin.service;

import com.tenxengage.admin.entity.Currency;
import com.tenxengage.admin.entity.enums.CurrencyType;
import com.tenxengage.admin.repository.CurrencyRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
public class CurrencyService {

    private static final Logger log = LoggerFactory.getLogger(CurrencyService.class);

    private final CurrencyRepository repository;

    public CurrencyService(CurrencyRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public void seedDefaultCurrencies(UUID clientId) {
        if (!repository.findByClientIdOrderByTypeAscCodeAsc(clientId).isEmpty()) {
            return; // already seeded
        }

        List<Currency> defaults = List.of(
            Currency.builder().clientId(clientId).code("cash").name("Cash")
                .type(CurrencyType.MONETARY).conversionRate(BigDecimal.ONE)
                .unit("").isCurrencyFormatted(true).isDefault(true).build(),
            Currency.builder().clientId(clientId).code("points").name("Points")
                .type(CurrencyType.MONETARY).conversionRate(new BigDecimal("200.0000"))
                .unit("pts").isCurrencyFormatted(false).isDefault(true).build(),
            Currency.builder().clientId(clientId).code("tickets").name("Tickets")
                .type(CurrencyType.NON_MONETARY).conversionRate(null)
                .unit("tickets").isCurrencyFormatted(false).isDefault(true).build(),
            Currency.builder().clientId(clientId).code("credits").name("Credits")
                .type(CurrencyType.NON_MONETARY).conversionRate(null)
                .unit("credits").isCurrencyFormatted(false).isDefault(true).build()
        );

        repository.saveAll(defaults);
        log.info("Seeded default currencies for client {}", clientId);
    }
}

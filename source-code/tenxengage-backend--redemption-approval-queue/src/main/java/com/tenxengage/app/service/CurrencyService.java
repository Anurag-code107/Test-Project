package com.tenxengage.app.service;

import com.tenxengage.app.dto.request.SaveCurrencyRequest;
import com.tenxengage.app.dto.response.CurrencyResponse;
import com.tenxengage.app.entity.Currency;
import com.tenxengage.app.entity.enums.CurrencyType;
import com.tenxengage.app.exception.BusinessRuleException;
import com.tenxengage.app.exception.ResourceNotFoundException;
import com.tenxengage.app.repository.CurrencyRepository;
import com.tenxengage.app.security.TenantValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class CurrencyService {

    private static final Logger log = LoggerFactory.getLogger(CurrencyService.class);

    private final CurrencyRepository repository;
    private final TenantValidator tenantValidator;
    private final JdbcTemplate jdbcTemplate;

    public CurrencyService(CurrencyRepository repository,
                           TenantValidator tenantValidator,
                           JdbcTemplate jdbcTemplate) {
        this.repository = repository;
        this.tenantValidator = tenantValidator;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional(readOnly = true)
    public List<CurrencyResponse> listCurrencies() {
        UUID clientId = tenantValidator.getCurrentClientId();
        return repository.findByClientIdOrderByTypeAscCodeAsc(clientId).stream()
            .map(CurrencyResponse::from)
            .toList();
    }

    @Transactional(readOnly = true)
    public CurrencyResponse getCurrency(UUID id) {
        Currency currency = repository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Currency", "id", id));
        tenantValidator.validateClientAccess(currency.getClientId());
        return CurrencyResponse.from(currency);
    }

    @Transactional(readOnly = true)
    public CurrencyResponse getCurrencyByCode(String code) {
        UUID clientId = tenantValidator.getCurrentClientId();
        Currency currency = repository.findByClientIdAndCode(clientId, code)
            .orElseThrow(() -> new ResourceNotFoundException("Currency", "code", code));
        return CurrencyResponse.from(currency);
    }

    @Transactional(readOnly = true)
    public Set<String> getMonetaryCodes(UUID clientId) {
        return repository.findByClientIdAndType(clientId, CurrencyType.MONETARY).stream()
            .map(Currency::getCode)
            .collect(Collectors.toSet());
    }

    @Transactional
    public CurrencyResponse createCurrency(SaveCurrencyRequest request) {
        UUID clientId = tenantValidator.getCurrentClientId();

        if (repository.existsByClientIdAndCode(clientId, request.code())) {
            throw new BusinessRuleException(
                "A currency with code '" + request.code() + "' already exists");
        }

        validateConversionRate(request);

        Currency currency = Currency.builder()
            .clientId(clientId)
            .code(request.code())
            .name(request.name())
            .type(request.type())
            .conversionRate(request.type() == CurrencyType.MONETARY ? request.conversionRate() : null)
            .unit(request.unit() != null ? request.unit() : "")
            .isCurrencyFormatted(request.isCurrencyFormatted() != null && request.isCurrencyFormatted())
            .isDefault(false)
            .build();

        currency = repository.save(currency);
        log.info("Created currency '{}' for client {}", currency.getCode(), clientId);
        return CurrencyResponse.from(currency);
    }

    @Transactional
    public CurrencyResponse updateCurrency(UUID id, SaveCurrencyRequest request) {
        Currency currency = repository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Currency", "id", id));
        tenantValidator.validateClientAccess(currency.getClientId());

        // Cannot change code of default currencies
        if (currency.isDefault() && !currency.getCode().equals(request.code())) {
            throw new BusinessRuleException("Cannot change the code of a default currency");
        }

        // Check code uniqueness if changed
        if (!currency.getCode().equals(request.code())
                && repository.existsByClientIdAndCode(currency.getClientId(), request.code())) {
            throw new BusinessRuleException(
                "A currency with code '" + request.code() + "' already exists");
        }

        validateConversionRate(request);

        currency.setCode(request.code());
        currency.setName(request.name());
        currency.setType(request.type());
        currency.setConversionRate(request.type() == CurrencyType.MONETARY ? request.conversionRate() : null);
        if (request.unit() != null) {
            currency.setUnit(request.unit());
        }
        if (request.isCurrencyFormatted() != null) {
            currency.setCurrencyFormatted(request.isCurrencyFormatted());
        }

        currency = repository.save(currency);
        log.info("Updated currency '{}' for client {}", currency.getCode(), currency.getClientId());
        return CurrencyResponse.from(currency);
    }

    @Transactional
    public void deleteCurrency(UUID id) {
        Currency currency = repository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Currency", "id", id));
        tenantValidator.validateClientAccess(currency.getClientId());

        if (currency.isDefault()) {
            throw new BusinessRuleException("Cannot delete a default currency");
        }

        if (isCurrencyInUse(currency.getClientId(), currency.getCode())) {
            throw new BusinessRuleException(
                "Cannot delete currency '" + currency.getCode()
                    + "' because it is in use by existing incentives or transactions");
        }

        repository.delete(currency);
        log.info("Deleted currency '{}' for client {}", currency.getCode(), currency.getClientId());
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

    private void validateConversionRate(SaveCurrencyRequest request) {
        if (request.type() == CurrencyType.MONETARY) {
            if (request.conversionRate() == null || request.conversionRate().compareTo(BigDecimal.ZERO) <= 0) {
                throw new BusinessRuleException(
                    "Monetary currencies require a positive conversion rate");
            }
        }
    }

    private boolean isCurrencyInUse(UUID clientId, String code) {
        String sql = """
            SELECT EXISTS (
                SELECT 1 FROM reward_transactions WHERE client_id = ? AND currency_id = ?
                UNION ALL
                SELECT 1 FROM reward_wallets WHERE client_id = ? AND currency_id = ?
                UNION ALL
                SELECT 1 FROM incentive_budgets ib
                  JOIN incentives i ON i.id = ib.incentive_id
                  WHERE i.client_id = ? AND ib.currency_id = ?
                UNION ALL
                SELECT 1 FROM payout_configs pc
                  JOIN sales_requirements sr ON sr.id = pc.requirement_id
                  JOIN incentives i ON i.id = sr.incentive_id
                  WHERE i.client_id = ? AND pc.currency_id = ?
            )
            """;
        return Boolean.TRUE.equals(
            jdbcTemplate.queryForObject(sql, Boolean.class,
                clientId, code, clientId, code, clientId, code, clientId, code));
    }
}

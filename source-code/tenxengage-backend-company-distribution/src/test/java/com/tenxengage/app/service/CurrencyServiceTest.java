package com.tenxengage.app.service;

import com.tenxengage.app.entity.Currency;
import com.tenxengage.app.entity.enums.CurrencyType;
import com.tenxengage.app.exception.BusinessRuleException;
import com.tenxengage.app.exception.ResourceNotFoundException;
import com.tenxengage.app.repository.CurrencyRepository;
import com.tenxengage.app.security.TenantValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CurrencyServiceTest {

    @Mock private CurrencyRepository currencyRepository;
    @Mock private TenantValidator tenantValidator;
    @Mock private JdbcTemplate jdbcTemplate;

    @InjectMocks private CurrencyService currencyService;

    private UUID clientId;

    @BeforeEach
    void setUp() {
        clientId = UUID.randomUUID();
    }

    @Test
    void getMonetaryCodes_returnsOnlyMonetaryTypes() {
        Currency cash = Currency.builder().code("USD_CASH").type(CurrencyType.MONETARY)
                .clientId(clientId).build();

        when(currencyRepository.findByClientIdAndType(clientId, CurrencyType.MONETARY))
                .thenReturn(List.of(cash));

        Set<String> codes = currencyService.getMonetaryCodes(clientId);

        assertThat(codes).containsExactly("USD_CASH");
    }

    @Test
    void getCurrencyByCode_throwsWhenNotFound() {
        when(tenantValidator.getCurrentClientId()).thenReturn(clientId);
        when(currencyRepository.findByClientIdAndCode(clientId, "MISSING"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> currencyService.getCurrencyByCode("MISSING"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void deleteCurrency_throwsForDefaultCurrency() {
        UUID currencyId = UUID.randomUUID();
        Currency defaultCurrency = Currency.builder()
                .code("USD_CASH").type(CurrencyType.MONETARY)
                .clientId(clientId).isDefault(true).build();
        defaultCurrency.setId(currencyId);

        when(currencyRepository.findById(currencyId)).thenReturn(Optional.of(defaultCurrency));

        assertThatThrownBy(() -> currencyService.deleteCurrency(currencyId))
                .isInstanceOf(BusinessRuleException.class);
    }
}

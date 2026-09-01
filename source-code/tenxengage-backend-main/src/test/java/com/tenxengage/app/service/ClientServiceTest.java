package com.tenxengage.app.service;

import com.tenxengage.app.dto.request.CreateClientRequest;
import com.tenxengage.app.entity.Client;
import com.tenxengage.app.entity.enums.ClientStatus;
import com.tenxengage.app.entity.enums.SubscriptionTier;
import com.tenxengage.app.exception.BusinessRuleException;
import com.tenxengage.app.exception.ResourceNotFoundException;
import com.tenxengage.app.repository.ClientRepository;
import com.tenxengage.app.security.TenantValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClientServiceTest {

    @Mock
    private ClientRepository clientRepository;
    @Mock
    private TenantValidator tenantValidator;
    @Mock
    private CurrencyService currencyService;

    @InjectMocks
    private ClientService clientService;

    private UUID clientId;

    @BeforeEach
    void setUp() {
        clientId = UUID.randomUUID();
    }

    @Test
    void getClientById_throwsWhenNotTenxAdmin() {
        when(tenantValidator.isTenxAdmin()).thenReturn(false);

        assertThatThrownBy(() -> clientService.getClientById(clientId))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void getClientById_throwsWhenNotFound() {
        when(tenantValidator.isTenxAdmin()).thenReturn(true);
        when(clientRepository.findById(clientId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> clientService.getClientById(clientId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void createClient_throwsWhenSubdomainExists() {
        when(tenantValidator.isTenxAdmin()).thenReturn(true);
        when(clientRepository.existsBySubdomain("existing")).thenReturn(true);

        assertThatThrownBy(() -> clientService.createClient(
                new CreateClientRequest("New Client", "existing", null, null, SubscriptionTier.ENTERPRISE)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Subdomain already in use");
    }

    @Test
    void createClient_seedsDefaultCurrencies() {
        when(tenantValidator.isTenxAdmin()).thenReturn(true);
        when(clientRepository.existsBySubdomain("new-client")).thenReturn(false);
        Client saved = Client.builder()
                .name("New Client").subdomain("new-client")
                .status(ClientStatus.ACTIVE).subscriptionTier(SubscriptionTier.ENTERPRISE)
                .build();
        saved.setId(clientId);
        when(clientRepository.save(any())).thenReturn(saved);

        clientService.createClient(
                new CreateClientRequest("New Client", "new-client", null, null, SubscriptionTier.ENTERPRISE));

        verify(currencyService).seedDefaultCurrencies(clientId);
    }

    @Test
    void deleteClient_throwsWhenNotTenxAdmin() {
        when(tenantValidator.isTenxAdmin()).thenReturn(false);

        assertThatThrownBy(() -> clientService.deleteClient(clientId))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void findClientIdBySubdomain_returnsIdWhenFound() {
        Client client = Client.builder().name("Test").subdomain("test")
                .status(ClientStatus.ACTIVE).subscriptionTier(SubscriptionTier.ENTERPRISE)
                .build();
        client.setId(clientId);
        when(clientRepository.findBySubdomain("test")).thenReturn(Optional.of(client));

        String result = clientService.findClientIdBySubdomain("test");

        assertThat(result).isEqualTo(clientId.toString());
    }

    @Test
    void findClientIdBySubdomain_returnsNullWhenNotFound() {
        when(clientRepository.findBySubdomain("missing")).thenReturn(Optional.empty());

        String result = clientService.findClientIdBySubdomain("missing");

        assertThat(result).isNull();
    }
}

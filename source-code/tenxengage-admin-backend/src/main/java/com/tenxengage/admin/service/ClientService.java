package com.tenxengage.admin.service;

import com.tenxengage.admin.config.CacheInvalidationPublisher;
import com.tenxengage.admin.dto.request.CreateClientRequest;
import com.tenxengage.admin.dto.request.UpdateClientRequest;
import com.tenxengage.admin.dto.response.ClientResponse;
import com.tenxengage.admin.dto.response.ClientStatsResponse;
import com.tenxengage.admin.entity.Client;
import com.tenxengage.admin.entity.enums.ClientStatus;
import com.tenxengage.admin.exception.BusinessRuleException;
import com.tenxengage.admin.exception.ResourceNotFoundException;
import com.tenxengage.admin.repository.ClientRepository;
import com.tenxengage.admin.security.TenantValidator;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class ClientService {

    private final ClientRepository clientRepository;
    private final TenantValidator tenantValidator;
    private final CurrencyService currencyService;
    private final CacheInvalidationPublisher cacheInvalidationPublisher;

    public ClientService(ClientRepository clientRepository,
                         TenantValidator tenantValidator,
                         CurrencyService currencyService,
                         CacheInvalidationPublisher cacheInvalidationPublisher) {
        this.clientRepository = clientRepository;
        this.tenantValidator = tenantValidator;
        this.currencyService = currencyService;
        this.cacheInvalidationPublisher = cacheInvalidationPublisher;
    }

    private void requireTenxAdmin() {
        if (!tenantValidator.isTenxAdmin()) {
            throw new AccessDeniedException("Only TENX_ADMIN can manage clients");
        }
    }

    @Transactional(readOnly = true)
    public Page<ClientResponse> getClients(Pageable pageable, String search) {
        requireTenxAdmin();
        return clientRepository.searchClients(search, pageable)
            .map(ClientResponse::from);
    }

    @Transactional(readOnly = true)
    public ClientResponse getClientById(UUID id) {
        requireTenxAdmin();
        Client client = clientRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Client", "id", id));
        return ClientResponse.from(client);
    }

    @Transactional
    public ClientResponse createClient(CreateClientRequest request) {
        requireTenxAdmin();
        if (clientRepository.existsBySubdomain(request.subdomain())) {
            throw new BusinessRuleException("Subdomain already in use: " + request.subdomain());
        }

        Client client = Client.builder()
            .name(request.name())
            .subdomain(request.subdomain())
            .logoUrl(request.logoUrl())
            .status(request.status() != null ? request.status() : ClientStatus.ACTIVE)
            .subscriptionTier(request.subscriptionTier())
            .build();

        Client saved = clientRepository.save(client);
        currencyService.seedDefaultCurrencies(saved.getId());
        return ClientResponse.from(saved);
    }

    @Transactional
    public ClientResponse updateClient(UUID id, UpdateClientRequest request) {
        requireTenxAdmin();
        Client client = clientRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Client", "id", id));

        if (request.name() != null) {
            client.setName(request.name());
        }
        if (request.logoUrl() != null) {
            client.setLogoUrl(request.logoUrl());
        }
        if (request.status() != null) {
            client.setStatus(request.status());
        }
        if (request.subscriptionTier() != null) {
            client.setSubscriptionTier(request.subscriptionTier());
        }

        Client updated = clientRepository.save(client);
        evictClientSubdomainCache(updated.getSubdomain());
        cacheInvalidationPublisher.evict("clientBySubdomain", updated.getSubdomain());
        return ClientResponse.from(updated);
    }

    @Transactional
    public void deleteClient(UUID id) {
        requireTenxAdmin();
        Client client = clientRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Client", "id", id));
        clientRepository.deleteById(id);
        evictClientSubdomainCache(client.getSubdomain());
        cacheInvalidationPublisher.evict("clientBySubdomain", client.getSubdomain());
    }

    @Cacheable(value = "clientBySubdomain", key = "#subdomain", unless = "#result == null")
    @Transactional(readOnly = true)
    public String findClientIdBySubdomain(String subdomain) {
        return clientRepository.findBySubdomain(subdomain)
            .map(c -> c.getId().toString())
            .orElse(null);
    }

    @Transactional(readOnly = true)
    public ClientStatsResponse getClientStats() {
        requireTenxAdmin();

        long totalClients = clientRepository.count();

        Map<String, Long> countByStatus = new LinkedHashMap<>();
        for (Object[] row : clientRepository.countByStatusGrouped()) {
            countByStatus.put(row[0].toString(), (Long) row[1]);
        }

        Map<String, Long> countByTier = new LinkedHashMap<>();
        for (Object[] row : clientRepository.countByTierGrouped()) {
            countByTier.put(row[0].toString(), (Long) row[1]);
        }

        return new ClientStatsResponse(totalClients, countByStatus, countByTier);
    }

    @CacheEvict(value = "clientBySubdomain", key = "#subdomain")
    public void evictClientSubdomainCache(String subdomain) {
        // eviction only
    }
}

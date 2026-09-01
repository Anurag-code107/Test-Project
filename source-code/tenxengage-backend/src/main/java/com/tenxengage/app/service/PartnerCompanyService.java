package com.tenxengage.app.service;

import com.tenxengage.app.dto.request.CreatePartnerCompanyRequest;
import com.tenxengage.app.dto.request.UpdatePartnerCompanyRequest;
import com.tenxengage.app.dto.response.PartnerCompanyResponse;
import com.tenxengage.app.entity.Client;
import com.tenxengage.app.entity.LocationValue;
import com.tenxengage.app.entity.PartnerCompany;
import com.tenxengage.app.entity.PartnerCompanyLocation;
import com.tenxengage.app.entity.enums.PartnerCompanyStatus;
import com.tenxengage.app.exception.BusinessRuleException;
import com.tenxengage.app.exception.ResourceNotFoundException;
import com.tenxengage.app.repository.ClientRepository;
import com.tenxengage.app.repository.LocationValueRepository;
import com.tenxengage.app.repository.PartnerCompanyRepository;
import com.tenxengage.app.repository.UserRepository;
import com.tenxengage.app.security.TenantValidator;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class PartnerCompanyService {

    private final PartnerCompanyRepository partnerCompanyRepository;
    private final ClientRepository clientRepository;
    private final UserRepository userRepository;
    private final LocationValueRepository locationValueRepository;
    private final TenantValidator tenantValidator;

    public PartnerCompanyService(PartnerCompanyRepository partnerCompanyRepository,
                                  ClientRepository clientRepository,
                                  UserRepository userRepository,
                                  LocationValueRepository locationValueRepository,
                                  TenantValidator tenantValidator) {
        this.partnerCompanyRepository = partnerCompanyRepository;
        this.clientRepository = clientRepository;
        this.userRepository = userRepository;
        this.locationValueRepository = locationValueRepository;
        this.tenantValidator = tenantValidator;
    }

    @Transactional(readOnly = true)
    public Page<PartnerCompanyResponse> getPartnerCompanies(Pageable pageable, String search,
                                                             PartnerCompanyStatus status) {
        UUID clientId = tenantValidator.getCurrentClientId();
        Client client = clientRepository.findById(clientId)
            .orElseThrow(() -> new ResourceNotFoundException("Client", "id", clientId));

        // PARTNER_ADMIN and PARTNER_SELLER can only see their own partner company
        UUID partnerCompanyId = tenantValidator.getCurrentPartnerCompanyId();
        if (partnerCompanyId != null) {
            return partnerCompanyRepository.findByIdAndClientId(partnerCompanyId, clientId)
                .<Page<PartnerCompanyResponse>>map(pc -> new org.springframework.data.domain.PageImpl<>(
                    java.util.List.of(PartnerCompanyResponse.from(pc, client.getName())), pageable, 1))
                .orElseGet(() -> Page.empty(pageable));
        }

        Page<PartnerCompany> page = partnerCompanyRepository.searchByClientId(clientId, search, status, pageable);
        Map<UUID, Long> userCounts = getUserCountMap(clientId, page.getContent());
        return page.map(pc -> PartnerCompanyResponse.from(pc, client.getName(),
            userCounts.getOrDefault(pc.getId(), 0L)));
    }

    private Map<UUID, Long> getUserCountMap(UUID clientId, List<PartnerCompany> companies) {
        if (companies.isEmpty()) return Map.of();
        List<UUID> ids = companies.stream().map(PartnerCompany::getId).toList();
        return userRepository.countActiveUsersByPartnerCompanyIds(clientId, ids).stream()
            .collect(Collectors.toMap(
                row -> (UUID) row[0],
                row -> (Long) row[1]
            ));
    }

    @Transactional(readOnly = true)
    public PartnerCompanyResponse getPartnerCompanyById(UUID id) {
        UUID clientId = tenantValidator.getCurrentClientId();

        // PARTNER_ADMIN and PARTNER_SELLER can only access their own partner company
        UUID partnerCompanyId = tenantValidator.getCurrentPartnerCompanyId();
        if (partnerCompanyId != null) {
            tenantValidator.validatePartnerCompanyAccess(id);
        }

        PartnerCompany pc = partnerCompanyRepository.findByIdAndClientId(id, clientId)
            .orElseThrow(() -> new ResourceNotFoundException("PartnerCompany", "id", id));

        Client client = clientRepository.findById(clientId)
            .orElseThrow(() -> new ResourceNotFoundException("Client", "id", clientId));

        return PartnerCompanyResponse.from(pc, client.getName());
    }

    @Transactional
    public PartnerCompanyResponse createPartnerCompany(CreatePartnerCompanyRequest request) {
        UUID clientId = tenantValidator.getCurrentClientId();

        if (partnerCompanyRepository.existsByClientIdAndName(clientId, request.name())) {
            throw new BusinessRuleException("Partner company name already exists: " + request.name());
        }

        if (request.externalPartnerId() != null
                && partnerCompanyRepository.existsByClientIdAndExternalPartnerId(
                        clientId, request.externalPartnerId())) {
            throw new BusinessRuleException("Partner ID already exists: " + request.externalPartnerId());
        }

        Client client = clientRepository.findById(clientId)
            .orElseThrow(() -> new ResourceNotFoundException("Client", "id", clientId));

        String metadata = request.metadata() != null ? request.metadata() : "{}";
        metadata = mergeMetadataFields(metadata, request.partnerType(), request.contactEmail());

        PartnerCompany pc = PartnerCompany.builder()
            .name(request.name())
            .externalPartnerId(request.externalPartnerId())
            .clientId(clientId)
            .status(request.status() != null ? request.status() : PartnerCompanyStatus.ACTIVE)
            .website(request.website())
            .contactPhone(request.contactPhone())
            .metadata(metadata)
            .build();

        PartnerCompany saved = partnerCompanyRepository.save(pc);

        // Assign location values
        if (request.locationValueIds() != null) {
            assignLocationValues(saved, request.locationValueIds(), clientId);
        }

        return PartnerCompanyResponse.from(saved, client.getName());
    }

    @Transactional
    public PartnerCompanyResponse updatePartnerCompany(UUID id, UpdatePartnerCompanyRequest request) {
        UUID clientId = tenantValidator.getCurrentClientId();
        PartnerCompany pc = partnerCompanyRepository.findByIdAndClientId(id, clientId)
            .orElseThrow(() -> new ResourceNotFoundException("PartnerCompany", "id", id));

        Client client = clientRepository.findById(clientId)
            .orElseThrow(() -> new ResourceNotFoundException("Client", "id", clientId));

        if (request.name() != null) {
            if (!request.name().equals(pc.getName())
                    && partnerCompanyRepository.existsByClientIdAndName(clientId, request.name())) {
                throw new BusinessRuleException("Partner company name already exists: " + request.name());
            }
            pc.setName(request.name());
        }
        if (request.status() != null) {
            pc.setStatus(request.status());
        }
        if (request.website() != null) {
            pc.setWebsite(request.website());
        }
        if (request.contactPhone() != null) {
            pc.setContactPhone(request.contactPhone());
        }
        if (request.externalPartnerId() != null) {
            if (!request.externalPartnerId().equals(pc.getExternalPartnerId())
                    && partnerCompanyRepository.existsByClientIdAndExternalPartnerId(
                            clientId, request.externalPartnerId())) {
                throw new BusinessRuleException("Partner ID already exists: " + request.externalPartnerId());
            }
            pc.setExternalPartnerId(request.externalPartnerId());
        }
        if (request.locationValueIds() != null) {
            pc.getLocationAssignments().clear();
            partnerCompanyRepository.flush();
            assignLocationValues(pc, request.locationValueIds(), clientId);
        }
        if (request.metadata() != null) {
            pc.setMetadata(request.metadata());
        }
        // Merge partnerType and contactEmail into metadata JSONB
        pc.setMetadata(mergeMetadataFields(pc.getMetadata(), request.partnerType(), request.contactEmail()));

        PartnerCompany updated = partnerCompanyRepository.save(pc);
        return PartnerCompanyResponse.from(updated, client.getName());
    }

    @Transactional
    public void deletePartnerCompany(UUID id) {
        UUID clientId = tenantValidator.getCurrentClientId();
        PartnerCompany pc = partnerCompanyRepository.findByIdAndClientId(id, clientId)
            .orElseThrow(() -> new ResourceNotFoundException("PartnerCompany", "id", id));
        partnerCompanyRepository.delete(pc);
    }

    private void assignLocationValues(PartnerCompany pc, List<UUID> locationValueIds, UUID clientId) {
        List<LocationValue> values = locationValueRepository.findByIdIn(locationValueIds);
        for (LocationValue lv : values) {
            if (!lv.getClientId().equals(clientId)) {
                throw new BusinessRuleException("Location value " + lv.getId() + " does not belong to this client");
            }
            PartnerCompanyLocation pcl = PartnerCompanyLocation.builder()
                .clientId(clientId)
                .partnerCompany(pc)
                .locationValue(lv)
                .build();
            pc.getLocationAssignments().add(pcl);
        }
    }

    private String mergeMetadataFields(String existingMetadata, String partnerType, String contactEmail) {
        try {
            var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            var map = mapper.readValue(
                existingMetadata != null && !existingMetadata.isBlank() ? existingMetadata : "{}",
                new com.fasterxml.jackson.core.type.TypeReference<java.util.LinkedHashMap<String, Object>>() {});
            if (partnerType != null) {
                map.put("Partner Type", partnerType);
            }
            if (contactEmail != null) {
                map.put("Contact Email", contactEmail);
            }
            return mapper.writeValueAsString(map);
        } catch (Exception e) {
            return existingMetadata != null ? existingMetadata : "{}";
        }
    }
}

package com.tenxengage.app.service;

import com.tenxengage.app.dto.request.CreatePartnerCompanyRequest;
import com.tenxengage.app.dto.request.UpdatePartnerCompanyRequest;
import com.tenxengage.app.dto.response.PartnerCompanyResponse;
import com.tenxengage.app.entity.Client;
import com.tenxengage.app.entity.PartnerCompany;
import com.tenxengage.app.entity.enums.PartnerCompanyStatus;
import com.tenxengage.app.exception.BusinessRuleException;
import com.tenxengage.app.exception.ResourceNotFoundException;
import com.tenxengage.app.repository.ClientRepository;
import com.tenxengage.app.repository.LocationValueRepository;
import com.tenxengage.app.repository.PartnerCompanyRepository;
import com.tenxengage.app.repository.UserRepository;
import com.tenxengage.app.security.TenantValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PartnerCompanyServiceTest {

    @Mock private PartnerCompanyRepository partnerCompanyRepository;
    @Mock private ClientRepository clientRepository;
    @Mock private UserRepository userRepository;
    @Mock private LocationValueRepository locationValueRepository;
    @Mock private TenantValidator tenantValidator;

    @InjectMocks private PartnerCompanyService service;

    private UUID clientId;
    private UUID companyId;

    @BeforeEach
    void setUp() {
        clientId = UUID.randomUUID();
        companyId = UUID.randomUUID();
    }

    // -------------------------------------------------------------------------
    // getPartnerCompanyById
    // -------------------------------------------------------------------------

    @Test
    void getPartnerCompanyById_found_returnsResponse() {
        PartnerCompany pc = buildPartnerCompany(companyId, "Acme Corp");
        Client client = buildClient(clientId, "TenX");

        when(tenantValidator.getCurrentClientId()).thenReturn(clientId);
        when(tenantValidator.getCurrentPartnerCompanyId()).thenReturn(null);
        when(partnerCompanyRepository.findByIdAndClientId(companyId, clientId))
                .thenReturn(Optional.of(pc));
        when(clientRepository.findById(clientId)).thenReturn(Optional.of(client));

        PartnerCompanyResponse result = service.getPartnerCompanyById(companyId);

        assertThat(result).isNotNull();
        assertThat(result.name()).isEqualTo("Acme Corp");
        assertThat(result.clientName()).isEqualTo("TenX");
    }

    @Test
    void getPartnerCompanyById_notFound_throwsResourceNotFoundException() {
        when(tenantValidator.getCurrentClientId()).thenReturn(clientId);
        when(tenantValidator.getCurrentPartnerCompanyId()).thenReturn(null);
        when(partnerCompanyRepository.findByIdAndClientId(any(), eq(clientId)))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getPartnerCompanyById(companyId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // -------------------------------------------------------------------------
    // getPartnerCompanies (list)
    // -------------------------------------------------------------------------

    @Test
    void getPartnerCompanies_noPartnerContext_returnsPage() {
        Pageable pageable = PageRequest.of(0, 10);
        PartnerCompany pc = buildPartnerCompany(companyId, "Acme Corp");
        Client client = buildClient(clientId, "TenX");
        Page<PartnerCompany> page = new PageImpl<>(List.of(pc), pageable, 1);

        when(tenantValidator.getCurrentClientId()).thenReturn(clientId);
        when(tenantValidator.getCurrentPartnerCompanyId()).thenReturn(null);
        when(clientRepository.findById(clientId)).thenReturn(Optional.of(client));
        when(partnerCompanyRepository.searchByClientId(eq(clientId), any(), any(), eq(pageable)))
                .thenReturn(page);
        when(userRepository.countActiveUsersByPartnerCompanyIds(eq(clientId), any()))
                .thenReturn(List.of());

        Page<PartnerCompanyResponse> result = service.getPartnerCompanies(pageable, null, null);

        assertThat(result).isNotNull();
        assertThat(result.getTotalElements()).isEqualTo(1);
    }

    // -------------------------------------------------------------------------
    // createPartnerCompany
    // -------------------------------------------------------------------------

    @Test
    void createPartnerCompany_newName_createsSuccessfully() {
        Client client = buildClient(clientId, "TenX");
        PartnerCompany saved = buildPartnerCompany(companyId, "New Partner");

        when(tenantValidator.getCurrentClientId()).thenReturn(clientId);
        when(partnerCompanyRepository.existsByClientIdAndName(clientId, "New Partner"))
                .thenReturn(false);
        when(clientRepository.findById(clientId)).thenReturn(Optional.of(client));
        when(partnerCompanyRepository.save(any(PartnerCompany.class))).thenReturn(saved);

        CreatePartnerCompanyRequest request = new CreatePartnerCompanyRequest(
                "New Partner", "EXT-001", null, "Reseller",
                PartnerCompanyStatus.ACTIVE, null, null, null, null);

        PartnerCompanyResponse result = service.createPartnerCompany(request);

        assertThat(result).isNotNull();
        assertThat(result.name()).isEqualTo("New Partner");
        verify(partnerCompanyRepository).save(any(PartnerCompany.class));
    }

    @Test
    void createPartnerCompany_duplicateName_throwsBusinessRuleException() {
        when(tenantValidator.getCurrentClientId()).thenReturn(clientId);
        when(partnerCompanyRepository.existsByClientIdAndName(clientId, "Duplicate"))
                .thenReturn(true);

        CreatePartnerCompanyRequest request = new CreatePartnerCompanyRequest(
                "Duplicate", null, null, "Reseller",
                null, null, null, null, null);

        assertThatThrownBy(() -> service.createPartnerCompany(request))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Duplicate");
    }

    // -------------------------------------------------------------------------
    // updatePartnerCompany
    // -------------------------------------------------------------------------

    @Test
    void updatePartnerCompany_notFound_throwsResourceNotFoundException() {
        when(tenantValidator.getCurrentClientId()).thenReturn(clientId);
        when(partnerCompanyRepository.findByIdAndClientId(any(), eq(clientId)))
                .thenReturn(Optional.empty());

        UpdatePartnerCompanyRequest request = new UpdatePartnerCompanyRequest(
                "Updated", null, null, null, null, null, null, null, null);

        assertThatThrownBy(() -> service.updatePartnerCompany(companyId, request))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void updatePartnerCompany_found_updatesAndReturns() {
        PartnerCompany pc = buildPartnerCompany(companyId, "Old Name");
        Client client = buildClient(clientId, "TenX");

        when(tenantValidator.getCurrentClientId()).thenReturn(clientId);
        when(partnerCompanyRepository.findByIdAndClientId(companyId, clientId))
                .thenReturn(Optional.of(pc));
        when(clientRepository.findById(clientId)).thenReturn(Optional.of(client));
        when(partnerCompanyRepository.existsByClientIdAndName(clientId, "New Name"))
                .thenReturn(false);
        when(partnerCompanyRepository.save(any(PartnerCompany.class))).thenReturn(pc);

        UpdatePartnerCompanyRequest request = new UpdatePartnerCompanyRequest(
                "New Name", null, null, null, null, null, null, null, null);

        PartnerCompanyResponse result = service.updatePartnerCompany(companyId, request);

        assertThat(result).isNotNull();
        assertThat(pc.getName()).isEqualTo("New Name");
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private PartnerCompany buildPartnerCompany(UUID id, String name) {
        PartnerCompany pc = PartnerCompany.builder()
                .name(name)
                .clientId(clientId)
                .status(PartnerCompanyStatus.ACTIVE)
                .metadata("{}")
                .build();
        pc.setId(id);
        pc.setCreatedAt(Instant.now());
        pc.setUpdatedAt(Instant.now());
        return pc;
    }

    private Client buildClient(UUID id, String name) {
        Client client = Client.builder().name(name).subdomain("tenx").build();
        client.setId(id);
        client.setCreatedAt(Instant.now());
        client.setUpdatedAt(Instant.now());
        return client;
    }
}

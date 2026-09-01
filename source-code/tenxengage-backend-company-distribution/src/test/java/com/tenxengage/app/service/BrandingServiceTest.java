package com.tenxengage.app.service;

import com.tenxengage.app.dto.request.UpdateBrandingRequest;
import com.tenxengage.app.dto.response.BrandingResponse;
import com.tenxengage.app.entity.ClientBranding;
import com.tenxengage.app.repository.ClientBrandingRepository;
import com.tenxengage.app.security.TenantValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BrandingServiceTest {

    @Mock
    private ClientBrandingRepository repository;

    @Mock
    private TenantValidator tenantValidator;

    @InjectMocks
    private BrandingService service;

    private UUID clientId;

    @BeforeEach
    void setUp() {
        clientId = UUID.randomUUID();
    }

    @Test
    void getBranding_noRow_returnsDefaults() {
        when(tenantValidator.getCurrentClientId()).thenReturn(clientId);
        when(repository.findByClientId(clientId)).thenReturn(Optional.empty());

        BrandingResponse result = service.getBranding();

        assertThat(result).isEqualTo(BrandingResponse.defaults());
        assertThat(result.primary()).isEqualTo("221 94% 56%");
        assertThat(result.headingFont()).isEqualTo("Inter");
    }

    @Test
    void getBranding_rowExists_returnsFromEntity() {
        ClientBranding entity = buildBranding("180 50% 50%", "Poppins");
        when(tenantValidator.getCurrentClientId()).thenReturn(clientId);
        when(repository.findByClientId(clientId)).thenReturn(Optional.of(entity));

        BrandingResponse result = service.getBranding();

        assertThat(result.primary()).isEqualTo("180 50% 50%");
        assertThat(result.headingFont()).isEqualTo("Poppins");
    }

    @Test
    void saveBranding_noRow_createsAndReturnsValues() {
        when(tenantValidator.getCurrentClientId()).thenReturn(clientId);
        when(repository.findByClientId(clientId)).thenReturn(Optional.empty());
        when(repository.save(any(ClientBranding.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        UpdateBrandingRequest request = buildRequest("0 100% 50%", "Roboto");
        BrandingResponse result = service.saveBranding(request);

        verify(repository).save(any(ClientBranding.class));
        assertThat(result.primary()).isEqualTo("0 100% 50%");
        assertThat(result.bodyFont()).isEqualTo("Roboto");
    }

    @Test
    void saveBranding_existingRow_updatesInPlace() {
        ClientBranding existing = buildBranding("180 50% 50%", "Inter");
        when(tenantValidator.getCurrentClientId()).thenReturn(clientId);
        when(repository.findByClientId(clientId)).thenReturn(Optional.of(existing));
        when(repository.save(any(ClientBranding.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        UpdateBrandingRequest request = buildRequest("300 80% 40%", "Lato");
        BrandingResponse result = service.saveBranding(request);

        assertThat(result.primary()).isEqualTo("300 80% 40%");
        assertThat(existing.getPrimaryHsl()).isEqualTo("300 80% 40%");
        assertThat(existing.getHeadingFont()).isEqualTo("Lato");
    }

    private ClientBranding buildBranding(String primary, String font) {
        ClientBranding entity = ClientBranding.builder()
            .clientId(clientId)
            .primaryHsl(primary)
            .primaryLightHsl(primary)
            .secondaryHsl(primary)
            .accentHsl(primary)
            .successHsl(primary)
            .warningHsl(primary)
            .destructiveHsl(primary)
            .backgroundHsl(primary)
            .foregroundHsl(primary)
            .mutedHsl(primary)
            .mutedForegroundHsl(primary)
            .cardHsl(primary)
            .cardForegroundHsl(primary)
            .borderHsl(primary)
            .headingFont(font)
            .bodyFont(font)
            .build();
        entity.setId(UUID.randomUUID());
        entity.setCreatedAt(Instant.now());
        entity.setUpdatedAt(Instant.now());
        return entity;
    }

    private UpdateBrandingRequest buildRequest(String color, String font) {
        return new UpdateBrandingRequest(
            color, color, color, color, color, color, color,
            color, color, color, color, color, color, color,
            font, font
        );
    }
}

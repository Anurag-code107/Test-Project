package com.tenxengage.app.service;

import com.tenxengage.app.dto.request.UpdateBrandingRequest;
import com.tenxengage.app.dto.response.BrandingResponse;
import com.tenxengage.app.entity.ClientBranding;
import com.tenxengage.app.exception.BusinessRuleException;
import com.tenxengage.app.exception.ResourceNotFoundException;
import com.tenxengage.app.repository.ClientBrandingRepository;
import com.tenxengage.app.security.TenantValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.UUID;

@Service
public class BrandingService {

    private static final Logger log = LoggerFactory.getLogger(BrandingService.class);

    private static final long MAX_LOGO_SIZE_BYTES = 2L * 1024 * 1024; // 2 MB

    private static final Map<String, String> ALLOWED_LOGO_TYPES = Map.of(
        "image/png", "png",
        "image/jpeg", "jpg",
        "image/svg+xml", "svg",
        "image/webp", "webp"
    );

    private final ClientBrandingRepository repository;
    private final TenantValidator tenantValidator;
    private final FileStorageService fileStorageService;

    public BrandingService(ClientBrandingRepository repository,
                           TenantValidator tenantValidator,
                           FileStorageService fileStorageService) {
        this.repository = repository;
        this.tenantValidator = tenantValidator;
        this.fileStorageService = fileStorageService;
    }

    @Transactional(readOnly = true)
    public BrandingResponse getBranding() {
        UUID clientId = tenantValidator.getCurrentClientId();
        return repository.findByClientId(clientId)
            .map(BrandingResponse::from)
            .orElseGet(BrandingResponse::defaults);
    }

    @Transactional
    public BrandingResponse saveBranding(UpdateBrandingRequest request) {
        UUID clientId = tenantValidator.getCurrentClientId();

        ClientBranding branding = repository.findByClientId(clientId)
            .orElseGet(() -> ClientBranding.builder().clientId(clientId).build());

        branding.setPrimaryHsl(request.primary());
        branding.setPrimaryLightHsl(request.primaryLight());
        branding.setSecondaryHsl(request.secondary());
        branding.setAccentHsl(request.accent());
        branding.setSuccessHsl(request.success());
        branding.setWarningHsl(request.warning());
        branding.setDestructiveHsl(request.destructive());
        branding.setBackgroundHsl(request.background());
        branding.setForegroundHsl(request.foreground());
        branding.setMutedHsl(request.muted());
        branding.setMutedForegroundHsl(request.mutedForeground());
        branding.setCardHsl(request.card());
        branding.setCardForegroundHsl(request.cardForeground());
        branding.setBorderHsl(request.border());
        branding.setHeadingFont(request.headingFont());
        branding.setBodyFont(request.bodyFont());

        ClientBranding saved = repository.save(branding);
        log.info("Saved branding for client {}", clientId);
        return BrandingResponse.from(saved);
    }

    @Transactional
    public BrandingResponse uploadLogo(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessRuleException("Logo file is required");
        }
        if (file.getSize() > MAX_LOGO_SIZE_BYTES) {
            throw new BusinessRuleException("Logo file exceeds the 2 MB size limit");
        }
        String contentType = file.getContentType();
        String extension = ALLOWED_LOGO_TYPES.get(contentType);
        if (extension == null) {
            throw new BusinessRuleException(
                "Unsupported logo file type. Allowed: PNG, JPEG, SVG, WebP");
        }

        UUID clientId = tenantValidator.getCurrentClientId();
        ClientBranding branding = repository.findByClientId(clientId)
            .orElseGet(() -> ClientBranding.builder().clientId(clientId).build());

        // Hydrate any unset required columns with defaults so a logo can be uploaded
        // before colors/fonts have been customised.
        applyDefaultsIfMissing(branding, clientId);

        long version = System.currentTimeMillis();
        String objectKey = "branding/" + clientId + "/logo-" + version + "." + extension;

        String previousObjectKey = branding.getLogoObjectKey();

        try (InputStream stream = file.getInputStream()) {
            fileStorageService.upload(objectKey, stream, file.getSize(), contentType);
        } catch (IOException e) {
            throw new BusinessRuleException("Failed to read uploaded logo: " + e.getMessage());
        }

        branding.setLogoObjectKey(objectKey);
        branding.setLogoUrl("/api/v1/branding/logo/file?v=" + version);
        ClientBranding saved = repository.save(branding);

        if (previousObjectKey != null && !previousObjectKey.equals(objectKey)) {
            try {
                fileStorageService.delete(previousObjectKey);
            } catch (Exception e) {
                log.warn("Failed to delete previous logo {} for client {}: {}",
                    previousObjectKey, clientId, e.getMessage());
            }
        }

        log.info("Uploaded logo for client {} at {}", clientId, objectKey);
        return BrandingResponse.from(saved);
    }

    @Transactional
    public BrandingResponse removeLogo() {
        UUID clientId = tenantValidator.getCurrentClientId();
        ClientBranding branding = repository.findByClientId(clientId)
            .orElseThrow(() -> new ResourceNotFoundException("No branding row exists for tenant"));

        String objectKey = branding.getLogoObjectKey();
        branding.setLogoObjectKey(null);
        branding.setLogoUrl(null);
        ClientBranding saved = repository.save(branding);

        if (objectKey != null) {
            try {
                fileStorageService.delete(objectKey);
            } catch (Exception e) {
                log.warn("Failed to delete logo {} for client {}: {}",
                    objectKey, clientId, e.getMessage());
            }
        }

        log.info("Removed logo for client {}", clientId);
        return BrandingResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public LogoStream streamLogo() {
        UUID clientId = tenantValidator.getCurrentClientId();
        ClientBranding branding = repository.findByClientId(clientId)
            .orElseThrow(() -> new ResourceNotFoundException("No custom logo set"));
        String objectKey = branding.getLogoObjectKey();
        if (objectKey == null) {
            throw new ResourceNotFoundException("No custom logo set");
        }
        String contentType = contentTypeForKey(objectKey);
        InputStream stream = fileStorageService.download(objectKey);
        return new LogoStream(stream, contentType);
    }

    private static String contentTypeForKey(String objectKey) {
        int dot = objectKey.lastIndexOf('.');
        if (dot < 0) {
            return "application/octet-stream";
        }
        String ext = objectKey.substring(dot + 1).toLowerCase();
        return switch (ext) {
            case "png" -> "image/png";
            case "jpg", "jpeg" -> "image/jpeg";
            case "svg" -> "image/svg+xml";
            case "webp" -> "image/webp";
            default -> "application/octet-stream";
        };
    }

    private static void applyDefaultsIfMissing(ClientBranding branding, UUID clientId) {
        if (branding.getPrimaryHsl() != null) {
            return;
        }
        BrandingResponse defaults = BrandingResponse.defaults();
        branding.setClientId(clientId);
        branding.setPrimaryHsl(defaults.primary());
        branding.setPrimaryLightHsl(defaults.primaryLight());
        branding.setSecondaryHsl(defaults.secondary());
        branding.setAccentHsl(defaults.accent());
        branding.setSuccessHsl(defaults.success());
        branding.setWarningHsl(defaults.warning());
        branding.setDestructiveHsl(defaults.destructive());
        branding.setBackgroundHsl(defaults.background());
        branding.setForegroundHsl(defaults.foreground());
        branding.setMutedHsl(defaults.muted());
        branding.setMutedForegroundHsl(defaults.mutedForeground());
        branding.setCardHsl(defaults.card());
        branding.setCardForegroundHsl(defaults.cardForeground());
        branding.setBorderHsl(defaults.border());
        branding.setHeadingFont(defaults.headingFont());
        branding.setBodyFont(defaults.bodyFont());
    }

    public record LogoStream(InputStream content, String contentType) {}
}

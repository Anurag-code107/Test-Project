package com.tenxengage.app.service.redemption;

import com.tenxengage.app.dto.request.redemption.TriggerExportRequest;
import com.tenxengage.app.dto.response.redemption.RedemptionExportJobDetailResponse;
import com.tenxengage.app.dto.response.redemption.RedemptionExportJobResponse;
import com.tenxengage.app.entity.RedemptionRequest;
import com.tenxengage.app.entity.User;
import com.tenxengage.app.entity.enums.RedemptionCategory;
import com.tenxengage.app.entity.enums.RedemptionProcessingMode;
import com.tenxengage.app.entity.enums.RedemptionStatus;
import com.tenxengage.app.entity.enums.WalletType;
import com.tenxengage.app.entity.enums.redemption.ExportFormat;
import com.tenxengage.app.entity.enums.redemption.ExportScope;
import com.tenxengage.app.entity.enums.redemption.RedemptionExportStatus;
import com.tenxengage.app.entity.redemption.RedemptionExportJob;
import com.tenxengage.app.exception.BusinessRuleException;
import com.tenxengage.app.exception.ResourceNotFoundException;
import com.tenxengage.app.repository.UserRepository;
import com.tenxengage.app.repository.redemption.RedemptionExportJobRepository;
import com.tenxengage.app.repository.redemption.RedemptionHistoryRepository;
import com.tenxengage.app.security.TenantValidator;
import com.tenxengage.app.service.FileStorageService;
import com.tenxengage.app.service.PermissionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RedemptionExportServiceTest {

    @Mock private RedemptionHistoryRepository historyRepository;
    @Mock private RedemptionExportJobRepository exportJobRepository;
    @Mock private UserRepository userRepository;
    @Mock private FileStorageService storageService;
    @Mock private PermissionService permissionService;
    @Mock private TenantValidator tenantValidator;

    @InjectMocks
    private RedemptionExportService service;

    private static final UUID CLIENT_ID   = UUID.randomUUID();
    private static final UUID USER_ID     = UUID.randomUUID();
    private static final UUID CATALOG_ID  = UUID.randomUUID();
    private static final UUID JOB_ID      = UUID.randomUUID();
    private static final UUID COMPANY_ID  = UUID.randomUUID();

    private User user;

    @BeforeEach
    void setUp() {
        // Wire self-reference so @Async self-invocation works in unit tests (mirrors Spring proxy behavior)
        ReflectionTestUtils.setField(service, "self", service);
        when(tenantValidator.getCurrentClientId()).thenReturn(CLIENT_ID);
        when(permissionService.resolveEffectivePermissions(any())).thenReturn(Set.of());

        user = new User();
        user.setId(USER_ID);
        when(userRepository.findByIdAndClientId(USER_ID, CLIENT_ID)).thenReturn(Optional.of(user));
    }

    private RedemptionRequest request() {
        RedemptionRequest r = new RedemptionRequest();
        r.setId(UUID.randomUUID());
        r.setClientId(CLIENT_ID);
        r.setUserId(USER_ID);
        r.setCatalogItemId(CATALOG_ID);
        r.setAmount(new BigDecimal("100.00"));
        r.setCurrencyId("cash");
        r.setStatus(RedemptionStatus.COMPLETED);
        r.setCategory(RedemptionCategory.CASH);
        r.setProcessingMode(RedemptionProcessingMode.INSTANT);
        r.setWalletType(WalletType.INDIVIDUAL);
        r.setSubmittedAt(Instant.now());
        r.setDeleted(false);
        return r;
    }

    private RedemptionExportJob job(RedemptionExportStatus status) {
        RedemptionExportJob j = new RedemptionExportJob();
        j.setId(JOB_ID);
        j.setClientId(CLIENT_ID);
        j.setRequestedBy(user);
        j.setFormat(ExportFormat.CSV);
        j.setScope("PERSONAL");
        j.setStatus(status);
        j.setFilterSnapshot(new java.util.HashMap<>());
        return j;
    }

    @Test
    void triggerExport_syncPath_returnsBytesWhenUnder1000() {
        when(historyRepository.countPersonalHistory(any(), any(), any(), any(), any(), any()))
                .thenReturn(500L);
        when(historyRepository.findPersonalHistory(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(request(), request())));

        TriggerExportRequest req = new TriggerExportRequest(ExportFormat.CSV, null, null, null, null);
        RedemptionExportService.ExportResult result = service.triggerExport(req, USER_ID);

        assertThat(result).isInstanceOf(RedemptionExportService.SyncExportResult.class);
        RedemptionExportService.SyncExportResult sync = (RedemptionExportService.SyncExportResult) result;
        assertThat(sync.data()).isNotEmpty();
        assertThat(sync.format()).isEqualTo(ExportFormat.CSV);
    }

    @Test
    void triggerExport_asyncPath_persistsJobWhenOver1000() {
        when(historyRepository.countPersonalHistory(any(), any(), any(), any(), any(), any()))
                .thenReturn(1_001L);
        RedemptionExportJob savedJob = job(RedemptionExportStatus.PENDING);
        savedJob.setId(JOB_ID);
        when(exportJobRepository.save(any())).thenReturn(savedJob);
        when(exportJobRepository.findById(JOB_ID)).thenReturn(Optional.of(savedJob));
        when(exportJobRepository.findByIdAndClientId(JOB_ID, CLIENT_ID))
                .thenReturn(Optional.of(savedJob));
        when(historyRepository.findPersonalHistory(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(request())));
        when(storageService.upload(any(), any(), any(long.class), any())).thenReturn("exports/key.csv");

        TriggerExportRequest req = new TriggerExportRequest(ExportFormat.CSV, null, null, null, null);
        RedemptionExportService.ExportResult result = service.triggerExport(req, USER_ID);

        assertThat(result).isInstanceOf(RedemptionExportService.AsyncExportResult.class);
        RedemptionExportService.AsyncExportResult async = (RedemptionExportService.AsyncExportResult) result;
        assertThat(async.jobId()).isEqualTo(JOB_ID);
    }

    @Test
    void triggerExport_partnerAdminRequestsPersonalScope_usesPersonalNotCompany() {
        // Regression: a Partner Admin (has redeem_company) exporting from the Personal tab must
        // export their PERSONAL history — not be silently forced into COMPANY scope, which returned
        // nothing when the company had no company-wallet redemptions.
        when(permissionService.resolveEffectivePermissions(USER_ID))
                .thenReturn(Set.of("action.redemption.redeem_company"));
        when(tenantValidator.getCurrentPartnerCompanyId()).thenReturn(COMPANY_ID);
        when(historyRepository.countPersonalHistory(any(), any(), any(), any(), any(), any()))
                .thenReturn(2L);
        when(historyRepository.findPersonalHistory(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(request(), request())));

        TriggerExportRequest req = new TriggerExportRequest(
                ExportFormat.CSV, null, null, null, null, ExportScope.PERSONAL);
        RedemptionExportService.ExportResult result = service.triggerExport(req, USER_ID);

        // Company path is never mocked — if it were taken, getTotalElements() on a null page would NPE.
        assertThat(result).isInstanceOf(RedemptionExportService.SyncExportResult.class);
        assertThat(((RedemptionExportService.SyncExportResult) result).data()).isNotEmpty();
    }

    @Test
    void triggerExport_partnerAdminNoScope_stillUsesCompany_legacyBehavior() {
        // Without an explicit scope, a Partner Admin still resolves to the widest permitted scope
        // (COMPANY) — preserving behavior for callers that predate the scope field.
        when(permissionService.resolveEffectivePermissions(USER_ID))
                .thenReturn(Set.of("action.redemption.redeem_company"));
        when(tenantValidator.getCurrentPartnerCompanyId()).thenReturn(COMPANY_ID);
        when(historyRepository.findCompanyHistoryByPartnerCompany(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(request()), PageRequest.of(0, 1), 1));

        TriggerExportRequest req = new TriggerExportRequest(ExportFormat.CSV, null, null, null, null);
        RedemptionExportService.ExportResult result = service.triggerExport(req, USER_ID);

        assertThat(result).isInstanceOf(RedemptionExportService.SyncExportResult.class);
        assertThat(((RedemptionExportService.SyncExportResult) result).data()).isNotEmpty();
    }

    @Test
    void triggerExport_zeroResults_throws422() {
        when(historyRepository.countPersonalHistory(any(), any(), any(), any(), any(), any()))
                .thenReturn(0L);

        TriggerExportRequest req = new TriggerExportRequest(ExportFormat.CSV, null, null, null, null);
        assertThatThrownBy(() -> service.triggerExport(req, USER_ID))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("No records match");
    }

    @Test
    void getExportJob_owner_returns200() {
        when(exportJobRepository.findByIdAndClientId(JOB_ID, CLIENT_ID))
                .thenReturn(Optional.of(job(RedemptionExportStatus.COMPLETED)));

        RedemptionExportJobResponse response = service.getExportJob(JOB_ID, USER_ID);
        assertThat(response.id()).isEqualTo(JOB_ID);
        assertThat(response.status()).isEqualTo("COMPLETED");
    }

    @Test
    void getExportJob_nonOwnerWithoutViewAll_throws404() {
        UUID otherId = UUID.randomUUID();
        User other = new User();
        other.setId(otherId);
        RedemptionExportJob j = job(RedemptionExportStatus.COMPLETED);
        j.setRequestedBy(other);
        when(exportJobRepository.findByIdAndClientId(JOB_ID, CLIENT_ID)).thenReturn(Optional.of(j));

        assertThatThrownBy(() -> service.getExportJob(JOB_ID, USER_ID))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getExportJob_clientAdminWithViewAll_returns200() {
        UUID adminId = UUID.randomUUID();
        when(permissionService.resolveEffectivePermissions(adminId))
                .thenReturn(Set.of("action.redemption.view_all_history"));

        User other = new User();
        other.setId(UUID.randomUUID());
        RedemptionExportJob j = job(RedemptionExportStatus.COMPLETED);
        j.setRequestedBy(other);
        when(exportJobRepository.findByIdAndClientId(JOB_ID, CLIENT_ID)).thenReturn(Optional.of(j));

        RedemptionExportJobResponse response = service.getExportJob(JOB_ID, adminId);
        assertThat(response.status()).isEqualTo("COMPLETED");
    }

    @Test
    void getExportJobWithDownloadUrl_completed_returnsPresignedUrl() {
        RedemptionExportJob j = job(RedemptionExportStatus.COMPLETED);
        j.setFileKey("exports/test/file.csv");
        j.setRowCount(50);
        j.setExpiresAt(Instant.now().plusSeconds(3600));
        when(exportJobRepository.findByIdAndClientId(JOB_ID, CLIENT_ID)).thenReturn(Optional.of(j));
        when(storageService.generatePresignedUrl(any(), any(int.class)))
                .thenReturn("https://storage.example.com/presigned");

        RedemptionExportJobDetailResponse response =
                service.getExportJobWithDownloadUrl(JOB_ID, USER_ID);

        assertThat(response.downloadUrl()).isEqualTo("https://storage.example.com/presigned");
        assertThat(response.rowCount()).isEqualTo(50);
    }

    @Test
    void getExportJobWithDownloadUrl_pending_downloadUrlNull() {
        when(exportJobRepository.findByIdAndClientId(JOB_ID, CLIENT_ID))
                .thenReturn(Optional.of(job(RedemptionExportStatus.PENDING)));

        RedemptionExportJobDetailResponse response =
                service.getExportJobWithDownloadUrl(JOB_ID, USER_ID);

        assertThat(response.downloadUrl()).isNull();
    }

    @Test
    void processExportJob_happyPath_completesJob() {
        RedemptionExportJob j = job(RedemptionExportStatus.PENDING);
        when(exportJobRepository.findById(JOB_ID)).thenReturn(Optional.of(j));
        when(exportJobRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(historyRepository.findPersonalHistory(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(request())));
        when(storageService.upload(any(), any(), any(long.class), any())).thenReturn("exports/key.csv");

        service.processExportJob(JOB_ID);

        assertThat(j.getStatus()).isEqualTo(RedemptionExportStatus.COMPLETED);
        assertThat(j.getRowCount()).isEqualTo(1);
        assertThat(j.getFileKey()).isNotNull();
        assertThat(j.getExpiresAt()).isNotNull();
    }

    @Test
    void processExportJob_storageFailure_setsStatusFailed() {
        RedemptionExportJob j = job(RedemptionExportStatus.PENDING);
        when(exportJobRepository.findById(JOB_ID)).thenReturn(Optional.of(j));
        when(exportJobRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(historyRepository.findPersonalHistory(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(request())));
        when(storageService.upload(any(), any(), any(long.class), any()))
                .thenThrow(new RuntimeException("Storage unavailable"));

        service.processExportJob(JOB_ID);

        assertThat(j.getStatus()).isEqualTo(RedemptionExportStatus.FAILED);
        assertThat(j.getFailureReason()).contains("Storage unavailable");
    }
}

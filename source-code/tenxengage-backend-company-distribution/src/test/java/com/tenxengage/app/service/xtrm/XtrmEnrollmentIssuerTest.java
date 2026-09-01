package com.tenxengage.app.service.xtrm;

import com.tenxengage.app.entity.User;
import com.tenxengage.app.entity.enums.xtrm.XtrmEnrollmentStatus;
import com.tenxengage.app.entity.xtrm.PartnerAddress;
import com.tenxengage.app.entity.xtrm.PartnerRedemption;
import com.tenxengage.app.repository.UserRepository;
import com.tenxengage.app.repository.xtrm.PartnerRedemptionRepository;
import com.tenxengage.app.service.AuditLogService;
import com.tenxengage.app.service.xtrm.SellerEnrollmentIssuerResolver.EnrollmentIssuer;
import com.tenxengage.app.service.xtrm.XtrmApiClient.CreateUserResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Enrolling a seller under their own company.
 *
 * <p>The property that matters is what happens when the company is <em>not</em> ready. Enrolling under the
 * platform then would succeed, look correct, and permanently exclude the seller from company distributions
 * — XTRM will not create a second user with the same email. So "not ready" must produce nothing at all.</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class XtrmEnrollmentIssuerTest {

    @Mock private PartnerRedemptionRepository redemptionRepository;
    @Mock private UserRepository userRepository;
    @Mock private XtrmApiClient xtrmApiClient;
    @Mock private AuditLogService auditLogService;
    @Mock private SellerEnrollmentIssuerResolver issuerResolver;

    private XtrmEnrollmentService service;

    private static final UUID CLIENT_ID = UUID.randomUUID();
    private static final UUID COMPANY_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();

    private final XtrmCredentials company =
            new XtrmCredentials("company-id", "s", "SPN26241004", "206415", "2314");

    @BeforeEach
    void setUp() {
        service = new XtrmEnrollmentService(redemptionRepository, userRepository, xtrmApiClient,
                auditLogService, issuerResolver);

        User user = new User();
        user.setId(USER_ID);
        user.setClientId(CLIENT_ID);
        user.setPartnerCompanyId(COMPANY_ID);
        user.setFirstName("Probe");
        user.setLastName("Seller");
        user.setEmail("probe@acme.test");
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));

        PartnerRedemption profile = PartnerRedemption.builder()
                .clientId(CLIENT_ID).userId(USER_ID)
                .enrollmentStatus(XtrmEnrollmentStatus.NOT_ENROLLED)
                .address(PartnerAddress.builder().line1("1 Market St").countryIso2("US").build())
                .build();
        when(redemptionRepository.findByUserIdAndClientId(USER_ID, CLIENT_ID))
                .thenReturn(Optional.of(profile));
        when(redemptionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private PartnerRedemption lastSaved() {
        ArgumentCaptor<PartnerRedemption> saved = ArgumentCaptor.forClass(PartnerRedemption.class);
        verify(redemptionRepository, atLeastOnce()).save(saved.capture());
        return saved.getValue();
    }

    @Test
    void enrolsUnderTheCompanyAndRecordsIt() {
        when(issuerResolver.resolve(CLIENT_ID, COMPANY_ID))
                .thenReturn(new EnrollmentIssuer.UseAccount(company));
        when(xtrmApiClient.createUser(any(), any())).thenReturn(CreateUserResult.ok("PAT26241022", "Basic"));

        service.enrollIfNeeded(USER_ID);

        verify(xtrmApiClient).createUser(any(), eq(company));
        assertThat(lastSaved().getRecipientUserId()).isEqualTo("PAT26241022");
        assertThat(lastSaved().getEnrolledIssuerAccountNumber()).isEqualTo("SPN26241004");
        assertThat(lastSaved().getEnrollmentStatus()).isEqualTo(XtrmEnrollmentStatus.ENROLLED);
    }

    /**
     * The single most important assertion in this change. A user created under the platform here would be
     * bound to it forever — XTRM refuses to create them again under their company.
     */
    @Test
    void createsNobodyWhenTheCompanyIsNotConnected() {
        when(issuerResolver.resolve(CLIENT_ID, COMPANY_ID))
                .thenReturn(new EnrollmentIssuer.Defer("This seller's company is not connected to XTRM yet."));

        service.enrollIfNeeded(USER_ID);

        verify(xtrmApiClient, never()).createUser(any());
        verify(xtrmApiClient, never()).createUser(any(), any());
    }

    @Test
    void leavesTheProfileUnenrolledWhenDeferring() {
        when(issuerResolver.resolve(CLIENT_ID, COMPANY_ID))
                .thenReturn(new EnrollmentIssuer.Defer("This seller's company is not connected to XTRM yet."));

        service.enrollIfNeeded(USER_ID);

        // NOT_ENROLLED, not FAILED: nothing went wrong and a retry will succeed once the company connects.
        // FAILED would read as a problem with the seller and invite someone to "fix" it manually.
        assertThat(lastSaved().getEnrollmentStatus()).isEqualTo(XtrmEnrollmentStatus.NOT_ENROLLED);
        assertThat(lastSaved().getEnrollmentError()).containsIgnoringCase("not connected");
        assertThat(lastSaved().getRecipientUserId()).isNull();
        assertThat(lastSaved().getEnrolledIssuerAccountNumber()).isNull();
    }

    @Test
    void recordsThePlatformForASellerWithNoCompany() {
        User noCompany = new User();
        noCompany.setId(USER_ID);
        noCompany.setClientId(CLIENT_ID);
        noCompany.setEmail("solo@acme.test");
        noCompany.setFirstName("Solo");
        noCompany.setLastName("User");
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(noCompany));

        XtrmCredentials platform =
                new XtrmCredentials("platform-id", "s", "SPN26237883", "203871", "2314");
        when(issuerResolver.resolve(CLIENT_ID, null))
                .thenReturn(new EnrollmentIssuer.UseAccount(platform));
        when(xtrmApiClient.createUser(any(), any())).thenReturn(CreateUserResult.ok("PAT9999", "Basic"));

        service.enrollIfNeeded(USER_ID);

        assertThat(lastSaved().getEnrolledIssuerAccountNumber()).isEqualTo("SPN26237883");
    }

    @Test
    void recordsNoIssuerWhenXtrmRejectsTheEnrollment() {
        when(issuerResolver.resolve(CLIENT_ID, COMPANY_ID))
                .thenReturn(new EnrollmentIssuer.UseAccount(company));
        when(xtrmApiClient.createUser(any(), any()))
                .thenReturn(CreateUserResult.failed(java.util.List.of("Email Already Exists"), false));

        service.enrollIfNeeded(USER_ID);

        // No PAT means no binding, so recording an issuer would claim something that did not happen.
        assertThat(lastSaved().getEnrolledIssuerAccountNumber()).isNull();
        assertThat(lastSaved().getEnrollmentStatus()).isEqualTo(XtrmEnrollmentStatus.FAILED);
    }
}

package com.tenxengage.app.service;

import com.tenxengage.app.dto.request.CompleteCompanyAdminProfileRequest;
import com.tenxengage.app.entity.PartnerCompany;
import com.tenxengage.app.entity.PartnerCompanyXtrmAccount;
import com.tenxengage.app.exception.BusinessRuleException;
import com.tenxengage.app.repository.PartnerCompanyRepository;
import com.tenxengage.app.repository.PartnerCompanyXtrmAccountRepository;
import com.tenxengage.app.security.CustomUserDetails;
import com.tenxengage.app.security.TenantValidator;
import com.tenxengage.app.service.xtrm.XtrmCompanyProvisioningService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.access.AccessDeniedException;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The company admin completing their own profile is what provisions the company's XTRM beneficiary.
 *
 * <p>They supply the address; the identity was set when their login was created. Provisioning fires here
 * rather than at company creation because these are the fields only they know — and because the email that
 * goes to XTRM cannot be corrected later, since XTRM refuses to reuse an address.</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CompanyAdminProfileServiceTest {

    @Mock private PartnerCompanyRepository companyRepository;
    @Mock private PartnerCompanyXtrmAccountRepository xtrmAccountRepository;
    @Mock private TenantValidator tenantValidator;
    @Mock private XtrmCompanyProvisioningService provisioningService;
    @Mock private CustomUserDetails caller;

    private CompanyAdminProfileService service;

    private static final UUID CLIENT_ID = UUID.randomUUID();
    private static final UUID COMPANY_ID = UUID.randomUUID();
    private static final String ADMIN_EMAIL = "admin@acme.test";

    @BeforeEach
    void setUp() {
        service = new CompanyAdminProfileService(companyRepository, xtrmAccountRepository,
                tenantValidator, provisioningService);
        // Mockito builds the bean directly, so there is no Spring proxy and `self` would be null.
        service.setSelf(service);

        // By default the caller IS the admin on file; the cases that matter override this.
        when(tenantValidator.getCurrentUserDetails()).thenReturn(this.caller);
        when(this.caller.getUsername()).thenReturn(ADMIN_EMAIL);
        when(tenantValidator.getCurrentClientId()).thenReturn(CLIENT_ID);
        when(tenantValidator.getCurrentPartnerCompanyId()).thenReturn(COMPANY_ID);
        when(companyRepository.findByIdAndClientId(COMPANY_ID, CLIENT_ID))
                .thenReturn(Optional.of(companyWithIdentity()));
        when(companyRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(xtrmAccountRepository.findByClientIdAndPartnerCompanyId(CLIENT_ID, COMPANY_ID))
                .thenReturn(Optional.empty());
    }

    private PartnerCompany companyWithIdentity() {
        PartnerCompany pc = PartnerCompany.builder()
                .name("Acme Corp").clientId(CLIENT_ID)
                .adminFirstName("TestP").adminLastName("Singh").adminEmail(ADMIN_EMAIL)
                .adminMobileNumber("4085556245").adminCountryIso2("US")
                .build();
        pc.setId(COMPANY_ID);
        return pc;
    }

    private CompleteCompanyAdminProfileRequest address() {
        return new CompleteCompanyAdminProfileRequest("San Francisco", "CA", "94105");
    }

    @Test
    void savesTheAddressAndProvisions() {
        service.completeProfile(address());

        verify(provisioningService).claim(CLIENT_ID, COMPANY_ID);
        verify(provisioningService).provision(CLIENT_ID, COMPANY_ID);
    }

    @Test
    void persistsWhatTheAdminSupplied() {
        PartnerCompany company = companyWithIdentity();
        when(companyRepository.findByIdAndClientId(COMPANY_ID, CLIENT_ID)).thenReturn(Optional.of(company));

        service.completeProfile(address());

        assertThat(company.getAdminCity()).isEqualTo("San Francisco");
        assertThat(company.getAdminRegion()).isEqualTo("CA");
        assertThat(company.getAdminPostalCode()).isEqualTo("94105");
        assertThat(company.hasCompleteAdminDetails()).isTrue();
    }

    @Test
    void claimsBeforeItProvisions() {
        service.completeProfile(address());

        // The claim reserves the slot before any vendor call, so a unique constraint rather than XTRM
        // settles two admins submitting at once.
        InOrder ordered = inOrder(provisioningService);
        ordered.verify(provisioningService).claim(CLIENT_ID, COMPANY_ID);
        ordered.verify(provisioningService).provision(CLIENT_ID, COMPANY_ID);
    }

    @Test
    void doesNotClaimTwiceWhenAlreadyClaimed() {
        when(xtrmAccountRepository.findByClientIdAndPartnerCompanyId(CLIENT_ID, COMPANY_ID))
                .thenReturn(Optional.of(new PartnerCompanyXtrmAccount()));

        service.completeProfile(address());

        // uq_xtrm_account_per_company would reject a second claim; resubmitting a profile must retry the
        // provisioning, not fail on the row that is already ours.
        verify(provisioningService, never()).claim(any(), any());
        verify(provisioningService).provision(CLIENT_ID, COMPANY_ID);
    }

    @Test
    void refusesACallerWhoBelongsToNoCompany() {
        when(tenantValidator.getCurrentPartnerCompanyId()).thenReturn(null);

        assertThatThrownBy(() -> service.completeProfile(address()))
                .isInstanceOf(BusinessRuleException.class);

        verify(provisioningService, never()).provision(any(), any());
    }

    @Test
    void refusesWhenTheCompanyHasNoAdminIdentity() {
        // Email on file so the caller matches, but the rest of the identity is missing: nothing to send, and
        // those fields are set when the login is created rather than supplied here.
        PartnerCompany partial = PartnerCompany.builder()
                .name("Bare").clientId(CLIENT_ID).adminEmail(ADMIN_EMAIL).build();
        partial.setId(COMPANY_ID);
        when(companyRepository.findByIdAndClientId(COMPANY_ID, CLIENT_ID)).thenReturn(Optional.of(partial));

        assertThatThrownBy(() -> service.completeProfile(address()))
                .isInstanceOf(BusinessRuleException.class);

        verify(provisioningService, never()).provision(any(), any());
    }

    @Test
    void refusesAnAdminWhoIsNotTheOneOnFile() {
        // A second PARTNER_ADMIN holds exactly the same permissions as the first, so only the email tells
        // them apart. Letting this through would overwrite the real admin's address on the shared company row
        // while the email already spent at XTRM stayed the first admin's.
        when(caller.getUsername()).thenReturn("someone.else@acme.test");

        assertThatThrownBy(() -> service.completeProfile(address()))
                .isInstanceOf(AccessDeniedException.class);

        verify(provisioningService, never()).claim(any(), any());
        verify(provisioningService, never()).provision(any(), any());
    }

    @Test
    void hidesTheProfileFromAnAdminWhoIsNotTheOneOnFile() {
        // The read is gated too, not just the write: the UI decides whether to offer the tab by whether this
        // call succeeds, so a permissive GET would put the tab in front of every admin.
        when(caller.getUsername()).thenReturn("someone.else@acme.test");

        assertThatThrownBy(() -> service.getProfile()).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void matchesTheAdminEmailRegardlessOfCasing() {
        // Logins are not case-sensitive; a capitalised sign-in must not lock the admin out of their own setup.
        when(caller.getUsername()).thenReturn("Admin@Acme.Test");

        service.completeProfile(address());

        verify(provisioningService).provision(CLIENT_ID, COMPANY_ID);
    }

    @Test
    void reportsWhetherTheProfileIsComplete() {
        // Identity only so far — the address has not been supplied, so this company cannot be provisioned.
        assertThat(service.getProfile().complete()).isFalse();
        assertThat(service.getProfile().adminEmail()).isEqualTo("admin@acme.test");
        assertThat(service.getProfile().companyName()).isEqualTo("Acme Corp");
    }
}

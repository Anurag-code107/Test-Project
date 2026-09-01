package com.tenxengage.app.service;

import com.tenxengage.app.dto.request.CreatePartnerCompanyRequest;
import com.tenxengage.app.entity.Client;
import com.tenxengage.app.entity.PartnerCompany;
import com.tenxengage.app.entity.PartnerCompanyXtrmAccount;
import com.tenxengage.app.entity.enums.PartnerCompanyStatus;
import com.tenxengage.app.entity.enums.XtrmAccountStatus;
import com.tenxengage.app.repository.ClientRepository;
import com.tenxengage.app.repository.LocationValueRepository;
import com.tenxengage.app.repository.PartnerCompanyRepository;
import com.tenxengage.app.repository.PartnerCompanyXtrmAccountRepository;
import com.tenxengage.app.repository.UserRepository;
import com.tenxengage.app.security.TenantValidator;
import com.tenxengage.app.service.xtrm.XtrmCompanyProvisioningService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * What creating and deleting a company does about XTRM.
 *
 * <p>Creating one gives the default admin a login and provisions nothing: that waits until the admin has
 * supplied their own address. Deleting one has to take the XTRM row with it, or the foreign key from
 * {@code partner_company_xtrm_accounts} makes every provisioned company undeletable behind a generic
 * integrity error that names nothing.</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PartnerCompanyProvisioningWiringTest {

    @Mock private PartnerCompanyRepository partnerCompanyRepository;
    @Mock private ClientRepository clientRepository;
    @Mock private UserRepository userRepository;
    @Mock private LocationValueRepository locationValueRepository;
    @Mock private TenantValidator tenantValidator;
    @Mock private XtrmCompanyProvisioningService provisioningService;
    @Mock private PartnerCompanyXtrmAccountRepository xtrmAccountRepository;
    @Mock private UserService userService;
    @Mock private com.tenxengage.app.repository.ClientRoleRepository clientRoleRepository;

    private PartnerCompanyService service;

    private static final UUID CLIENT_ID = UUID.randomUUID();
    private static final UUID COMPANY_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new PartnerCompanyService(partnerCompanyRepository, clientRepository, userRepository,
                locationValueRepository, tenantValidator, provisioningService, xtrmAccountRepository,
                userService, clientRoleRepository);
        service.setSelf(service);

        com.tenxengage.app.entity.ClientRole role = new com.tenxengage.app.entity.ClientRole();
        role.setId(UUID.randomUUID());
        when(clientRoleRepository.findByClientIdAndBaseRoleNameAndSystemTrue(CLIENT_ID, "PARTNER_ADMIN"))
                .thenReturn(Optional.of(role));

        when(tenantValidator.getCurrentClientId()).thenReturn(CLIENT_ID);
        Client client = new Client();
        client.setName("Apple");
        when(clientRepository.findById(CLIENT_ID)).thenReturn(Optional.of(client));
        when(partnerCompanyRepository.save(any())).thenAnswer(inv -> {
            PartnerCompany pc = inv.getArgument(0);
            pc.setId(COMPANY_ID);
            return pc;
        });
    }

    private CreatePartnerCompanyRequest withAdmin() {
        return new CreatePartnerCompanyRequest(
                "Acme Corp", "EXT-1", List.of(), "RESELLER", PartnerCompanyStatus.ACTIVE,
                "https://acme.test", "contact@acme.test", "1234567890", "{}",
                "TestP", "Singh", "admin@acme.test", "4085556245", "US");
    }

    private CreatePartnerCompanyRequest withoutAdmin() {
        return new CreatePartnerCompanyRequest(
                "Bare Corp", "EXT-2", List.of(), "RESELLER", PartnerCompanyStatus.ACTIVE,
                null, null, null, "{}", null, null, null, null, null);
    }

    /**
     * Provisioning moved to profile completion (D-16). Creating a company gives the admin a login and
     * stops — the address XTRM needs is theirs to supply, and the email that reaches XTRM cannot be
     * corrected once spent. {@code CompanyAdminProfileServiceTest} covers the claim-then-provision order
     * where it now happens.
     */
    @Test
    void createsTheAdminLoginAndProvisionsNothing() {
        service.createPartnerCompany(withAdmin());

        verify(userService).createUser(any());
        verify(provisioningService, never()).claim(any(), any());
        verify(provisioningService, never()).provision(any(), any());
    }

    @Test
    void doesNothingAtAllWhenThereIsNoAdminIdentity() {
        service.createPartnerCompany(withoutAdmin());

        verify(userService, never()).createUser(any());
        verify(provisioningService, never()).claim(any(), any());
        verify(provisioningService, never()).provision(any(), any());
    }

    @Test
    void deletesTheXtrmRowWithTheCompany() {
        PartnerCompany pc = PartnerCompany.builder().name("Acme Corp").clientId(CLIENT_ID).build();
        pc.setId(COMPANY_ID);
        when(partnerCompanyRepository.findByIdAndClientId(COMPANY_ID, CLIENT_ID)).thenReturn(Optional.of(pc));

        PartnerCompanyXtrmAccount account = PartnerCompanyXtrmAccount.builder()
                .clientId(CLIENT_ID).partnerCompanyId(COMPANY_ID).status(XtrmAccountStatus.CONNECTED).build();
        when(xtrmAccountRepository.findByClientIdAndPartnerCompanyId(CLIENT_ID, COMPANY_ID))
                .thenReturn(Optional.of(account));

        service.deletePartnerCompany(COMPANY_ID);

        verify(xtrmAccountRepository).delete(account);
        verify(partnerCompanyRepository).delete(pc);
    }

    @Test
    void deletesCleanlyWhenThereIsNoXtrmRow() {
        PartnerCompany pc = PartnerCompany.builder().name("Bare Corp").clientId(CLIENT_ID).build();
        pc.setId(COMPANY_ID);
        when(partnerCompanyRepository.findByIdAndClientId(COMPANY_ID, CLIENT_ID)).thenReturn(Optional.of(pc));
        when(xtrmAccountRepository.findByClientIdAndPartnerCompanyId(CLIENT_ID, COMPANY_ID))
                .thenReturn(Optional.empty());

        service.deletePartnerCompany(COMPANY_ID);

        verify(partnerCompanyRepository).delete(pc);
    }
}

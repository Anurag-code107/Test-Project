package com.tenxengage.app.service;

import com.tenxengage.app.dto.request.CreatePartnerCompanyRequest;
import com.tenxengage.app.dto.request.CreateUserRequest;
import com.tenxengage.app.entity.Client;
import com.tenxengage.app.entity.ClientRole;
import com.tenxengage.app.entity.PartnerCompany;
import com.tenxengage.app.entity.enums.PartnerCompanyStatus;
import com.tenxengage.app.exception.BusinessRuleException;
import com.tenxengage.app.repository.ClientRepository;
import com.tenxengage.app.repository.ClientRoleRepository;
import com.tenxengage.app.repository.LocationValueRepository;
import com.tenxengage.app.repository.PartnerCompanyRepository;
import com.tenxengage.app.repository.PartnerCompanyXtrmAccountRepository;
import com.tenxengage.app.repository.UserRepository;
import com.tenxengage.app.security.TenantValidator;
import com.tenxengage.app.service.xtrm.XtrmCompanyProvisioningService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Creating a company now creates its default admin's login.
 *
 * <p>Provisioning deliberately does <b>not</b> happen here any more. The admin still has to supply the
 * address XTRM needs, and firing CreateBeneficiary with a client admin's guess at those fields is what this
 * change exists to stop — a mistyped admin email cannot be undone, because XTRM refuses to reuse it.</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PartnerCompanyAdminUserTest {

    @Mock private PartnerCompanyRepository partnerCompanyRepository;
    @Mock private ClientRepository clientRepository;
    @Mock private UserRepository userRepository;
    @Mock private LocationValueRepository locationValueRepository;
    @Mock private TenantValidator tenantValidator;
    @Mock private XtrmCompanyProvisioningService provisioningService;
    @Mock private PartnerCompanyXtrmAccountRepository xtrmAccountRepository;
    @Mock private UserService userService;
    @Mock private ClientRoleRepository clientRoleRepository;

    private PartnerCompanyService service;

    private static final UUID CLIENT_ID = UUID.randomUUID();
    private static final UUID COMPANY_ID = UUID.randomUUID();
    private static final UUID ROLE_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new PartnerCompanyService(partnerCompanyRepository, clientRepository, userRepository,
                locationValueRepository, tenantValidator, provisioningService, xtrmAccountRepository,
                userService, clientRoleRepository);
        service.setSelf(service);

        when(tenantValidator.getCurrentClientId()).thenReturn(CLIENT_ID);
        Client client = new Client();
        client.setName("Apple");
        when(clientRepository.findById(CLIENT_ID)).thenReturn(Optional.of(client));
        when(partnerCompanyRepository.save(any())).thenAnswer(inv -> {
            PartnerCompany pc = inv.getArgument(0);
            pc.setId(COMPANY_ID);
            return pc;
        });

        ClientRole role = new ClientRole();
        role.setId(ROLE_ID);
        when(clientRoleRepository.findByClientIdAndBaseRoleNameAndSystemTrue(CLIENT_ID, "PARTNER_ADMIN"))
                .thenReturn(Optional.of(role));
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

    @Test
    void createsAPartnerAdminLoginForTheCompany() {
        service.createPartnerCompany(withAdmin());

        ArgumentCaptor<CreateUserRequest> req = ArgumentCaptor.forClass(CreateUserRequest.class);
        verify(userService).createUser(req.capture());

        assertThat(req.getValue().email()).isEqualTo("admin@acme.test");
        assertThat(req.getValue().firstName()).isEqualTo("TestP");
        assertThat(req.getValue().lastName()).isEqualTo("Singh");
        assertThat(req.getValue().phone()).isEqualTo("4085556245");
        assertThat(req.getValue().phoneCountryIso2()).isEqualTo("US");
        assertThat(req.getValue().partnerCompanyId()).isEqualTo(COMPANY_ID);
        assertThat(req.getValue().clientRoleId()).isEqualTo(ROLE_ID);
    }

    @Test
    void doesNotProvisionAtCreationAnyMore() {
        service.createPartnerCompany(withAdmin());

        // The admin has not supplied their address yet. Firing CreateBeneficiary now would send a client
        // admin's guess at fields only the admin knows — and the email cannot be corrected afterwards.
        verify(provisioningService, never()).claim(any(), any());
        verify(provisioningService, never()).provision(any(), any());
    }

    @Test
    void createsNoUserWhenThereIsNoAdminIdentity() {
        service.createPartnerCompany(withoutAdmin());

        verify(userService, never()).createUser(any());
    }

    @Test
    void failsTheWholeCreateWhenTheClientHasNoPartnerAdminRole() {
        when(clientRoleRepository.findByClientIdAndBaseRoleNameAndSystemTrue(CLIENT_ID, "PARTNER_ADMIN"))
                .thenReturn(Optional.empty());

        // Better than a company whose admin can never sign in and whose beneficiary can never be created.
        assertThatThrownBy(() -> service.createPartnerCompany(withAdmin()))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("PARTNER_ADMIN");
    }

    @Test
    void passesNoPasswordSoTheOnboardingFlowOwnsIt() {
        service.createPartnerCompany(withAdmin());

        ArgumentCaptor<CreateUserRequest> req = ArgumentCaptor.forClass(CreateUserRequest.class);
        verify(userService).createUser(req.capture());

        // createUser writes a placeholder hash and issues an onboarding token; inventing one here would
        // create a credential nobody asked for and nobody knows.
        assertThat(req.getValue().password()).isNull();
    }
}

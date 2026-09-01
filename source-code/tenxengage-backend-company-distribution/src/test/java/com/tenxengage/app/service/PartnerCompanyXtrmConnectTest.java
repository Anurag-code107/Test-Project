package com.tenxengage.app.service;

import com.tenxengage.app.dto.request.ConnectXtrmAccountRequest;
import com.tenxengage.app.entity.Client;
import com.tenxengage.app.entity.PartnerCompany;
import com.tenxengage.app.entity.PartnerCompanyXtrmAccount;
import com.tenxengage.app.entity.enums.XtrmAccountStatus;
import com.tenxengage.app.exception.BusinessRuleException;
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

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The connect endpoint: provision a legacy company, retry a failure, or finish a row by hand.
 *
 * <p>Which of the three happens is read off the row's state rather than a mode flag, so a caller cannot pick
 * the wrong one. The rule underneath all of them is that {@code CreateBeneficiary} must never be replayed
 * for a company that already has an SPN — that call is not replayable, and a second one would either fail on
 * the duplicate name or mint a second account for a single company.</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PartnerCompanyXtrmConnectTest {

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
        // Mockito builds the bean directly, so there is no Spring proxy and `self` would be null.
        service.setSelf(service);

        when(tenantValidator.getCurrentClientId()).thenReturn(CLIENT_ID);
        Client client = new Client();
        client.setName("Apple");
        when(clientRepository.findById(CLIENT_ID)).thenReturn(Optional.of(client));

        when(partnerCompanyRepository.findByIdAndClientId(COMPANY_ID, CLIENT_ID))
                .thenReturn(Optional.of(bareCompany()));
        when(partnerCompanyRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(xtrmAccountRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private PartnerCompany bareCompany() {
        PartnerCompany pc = PartnerCompany.builder().name("Acme Corp").clientId(CLIENT_ID).build();
        pc.setId(COMPANY_ID);
        return pc;
    }

    private ConnectXtrmAccountRequest fullAdmin() {
        return new ConnectXtrmAccountRequest("TestP", "Singh", "admin@acme.test", "4085556245",
                "San Francisco", "CA", "94105", "US", null);
    }

    private PartnerCompanyXtrmAccount pending() {
        return PartnerCompanyXtrmAccount.builder()
                .clientId(CLIENT_ID).partnerCompanyId(COMPANY_ID)
                .status(XtrmAccountStatus.PENDING).build();
    }

    @Test
    void savesAdminDetailsAndProvisionsALegacyCompany() {
        when(xtrmAccountRepository.findByClientIdAndPartnerCompanyId(CLIENT_ID, COMPANY_ID))
                .thenReturn(Optional.empty());

        service.connectXtrmAccount(COMPANY_ID, fullAdmin());

        verify(provisioningService).claim(CLIENT_ID, COMPANY_ID);
        verify(provisioningService).provision(CLIENT_ID, COMPANY_ID);
    }

    @Test
    void refusesToProvisionALegacyCompanyWithNoAdminDetails() {
        when(xtrmAccountRepository.findByClientIdAndPartnerCompanyId(CLIENT_ID, COMPANY_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.connectXtrmAccount(COMPANY_ID, ConnectXtrmAccountRequest.empty()))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("admin details");

        verify(provisioningService, never()).claim(any(), any());
    }

    @Test
    void retriesAPendingRowWithoutReclaimingIt() {
        PartnerCompanyXtrmAccount row = pending();
        row.setLastError("Could not reach XTRM");
        when(xtrmAccountRepository.findByClientIdAndPartnerCompanyId(CLIENT_ID, COMPANY_ID))
                .thenReturn(Optional.of(row));

        service.connectXtrmAccount(COMPANY_ID, ConnectXtrmAccountRequest.empty());

        // A second claim would violate uq_xtrm_account_per_company; the row is already ours.
        verify(provisioningService, never()).claim(any(), any());
        verify(provisioningService).provision(CLIENT_ID, COMPANY_ID);
    }

    @Test
    void acceptsAManualWalletIdAndConnectsWithoutCallingXtrm() {
        PartnerCompanyXtrmAccount row = pending();
        row.setXtrmAccountNumber("SPN26241004");
        row.setEncryptedCredentials("blob");
        when(xtrmAccountRepository.findByClientIdAndPartnerCompanyId(CLIENT_ID, COMPANY_ID))
                .thenReturn(Optional.of(row));

        service.connectXtrmAccount(COMPANY_ID, new ConnectXtrmAccountRequest(
                null, null, null, null, null, null, null, null, "206415"));

        assertThat(row.getXtrmWalletId()).isEqualTo("206415");
        assertThat(row.getStatus()).isEqualTo(XtrmAccountStatus.CONNECTED);
        assertThat(row.getConnectedAt()).isNotNull();
        assertThat(row.isPayoutReady()).isTrue();
        verify(provisioningService, never()).provision(any(), any());
    }

    @Test
    void ignoresAManualWalletIdWhenThereAreNoCredentialsYet() {
        // Setting a wallet on a row with no credentials would produce a CONNECTED row that cannot pay —
        // and the database CHECK would reject it anyway.
        when(xtrmAccountRepository.findByClientIdAndPartnerCompanyId(CLIENT_ID, COMPANY_ID))
                .thenReturn(Optional.of(pending()));

        service.connectXtrmAccount(COMPANY_ID, new ConnectXtrmAccountRequest(
                null, null, null, null, null, null, null, null, "206415"));

        verify(provisioningService).provision(CLIENT_ID, COMPANY_ID);
    }

    @Test
    void isANoOpWhenAlreadyConnected() {
        PartnerCompanyXtrmAccount connected = pending();
        connected.setStatus(XtrmAccountStatus.CONNECTED);
        connected.setXtrmAccountNumber("SPN26241004");
        connected.setXtrmWalletId("206415");
        connected.setEncryptedCredentials("blob");
        when(xtrmAccountRepository.findByClientIdAndPartnerCompanyId(CLIENT_ID, COMPANY_ID))
                .thenReturn(Optional.of(connected));

        service.connectXtrmAccount(COMPANY_ID, fullAdmin());

        verify(provisioningService, never()).claim(any(), any());
        verify(provisioningService, never()).provision(any(), any());
    }

    @Test
    void returnsTheAccountBlockWithoutAnyCredentials() {
        PartnerCompanyXtrmAccount row = pending();
        row.setXtrmAccountNumber("SPN26241004");
        row.setAccountIdentityLevel("Basic");
        row.setLastError("Wallet lookup failed");
        row.setEncryptedCredentials("super-secret-blob");
        when(xtrmAccountRepository.findByClientIdAndPartnerCompanyId(CLIENT_ID, COMPANY_ID))
                .thenReturn(Optional.of(row));

        var response = service.connectXtrmAccount(COMPANY_ID, ConnectXtrmAccountRequest.empty());

        assertThat(response.xtrmAccount()).isNotNull();
        assertThat(response.xtrmAccount().status()).isEqualTo("PENDING");
        assertThat(response.xtrmAccount().accountNumber()).isEqualTo("SPN26241004");
        assertThat(response.xtrmAccount().identityLevel()).isEqualTo("Basic");
        assertThat(response.xtrmAccount().lastError()).isEqualTo("Wallet lookup failed");
        // This record is serialized straight to a browser.
        assertThat(response.xtrmAccount().toString()).doesNotContain("super-secret-blob");
    }

    @Test
    void persistsAdminDetailsSuppliedOnConnect() {
        PartnerCompany pc = bareCompany();
        when(partnerCompanyRepository.findByIdAndClientId(COMPANY_ID, CLIENT_ID)).thenReturn(Optional.of(pc));
        when(xtrmAccountRepository.findByClientIdAndPartnerCompanyId(CLIENT_ID, COMPANY_ID))
                .thenReturn(Optional.empty());

        service.connectXtrmAccount(COMPANY_ID, fullAdmin());

        assertThat(pc.getAdminEmail()).isEqualTo("admin@acme.test");
        assertThat(pc.getAdminCountryIso2()).isEqualTo("US");
        assertThat(pc.hasCompleteAdminDetails()).isTrue();
    }
}

package com.tenxengage.app.service.xtrm;

import com.tenxengage.app.entity.Client;
import com.tenxengage.app.entity.PartnerCompany;
import com.tenxengage.app.entity.PartnerCompanyXtrmAccount;
import com.tenxengage.app.entity.enums.XtrmAccountStatus;
import com.tenxengage.app.repository.ClientRepository;
import com.tenxengage.app.repository.PartnerCompanyRepository;
import com.tenxengage.app.repository.PartnerCompanyXtrmAccountRepository;
import com.tenxengage.app.service.xtrm.XtrmApiClient.CreateBeneficiaryCommand;
import com.tenxengage.app.service.xtrm.XtrmApiClient.CreateBeneficiaryResult;
import com.tenxengage.app.service.xtrm.XtrmApiClient.GetWalletsResult;
import com.tenxengage.app.service.xtrm.XtrmApiClient.WalletInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Provisioning a partner company's XTRM identity.
 *
 * <p>The property that matters most here is not the happy path. XTRM returns the pseudo credentials exactly
 * once, and {@code CreateBeneficiary} cannot be replayed for the same company because the name is taken on
 * the second attempt. So the credentials must reach the database before anything else is attempted. Held in
 * memory across the token check or the wallet lookup, a single exception loses the company's ability to pay
 * permanently — recoverable only through XTRM support.</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class XtrmCompanyProvisioningServiceTest {

    @Mock private PartnerCompanyXtrmAccountRepository accountRepository;
    @Mock private PartnerCompanyRepository companyRepository;
    @Mock private XtrmApiClient xtrmApiClient;
    @Mock private XtrmCredentialsResolver credentialsResolver;
    @Mock private ClientRepository clientRepository;

    private XtrmCompanyProvisioningService service;

    private static final UUID CLIENT_ID = UUID.randomUUID();
    private static final UUID COMPANY_ID = UUID.randomUUID();

    /**
     * What the row looked like at the moment of each save.
     *
     * <p>An {@code ArgumentCaptor} cannot be used for this. The service mutates one entity instance across
     * the sequence, so the captor would hold N references to the same object and every "state" would read
     * as the final one — which would make the ordering test below pass no matter what order the code ran
     * in. Snapshotting the fields at save time is what makes it a real assertion.</p>
     */
    private record SaveSnapshot(XtrmAccountStatus status, String accountNumber, String walletId,
                                String credentials, String lastError, String beneficiaryName,
                                String identityLevel) {
    }

    private final List<SaveSnapshot> saves = new java.util.ArrayList<>();

    @BeforeEach
    void setUp() {
        service = new XtrmCompanyProvisioningService(
                accountRepository, companyRepository, xtrmApiClient, credentialsResolver, clientRepository);
        // The shipped default. Set deliberately rather than left to the annotation, which Spring is not
        // here to apply — and the value matters: it is how the admin reaches XTRM's portal.
        ReflectionTestUtils.setField(service, "emailNotification", true);
        saves.clear();

        Client tenant = new Client();
        tenant.setName("Apple");
        when(clientRepository.findById(CLIENT_ID)).thenReturn(Optional.of(tenant));

        when(companyRepository.findByIdAndClientId(COMPANY_ID, CLIENT_ID))
                .thenReturn(Optional.of(companyWithAdmin()));
        when(accountRepository.findByClientIdAndPartnerCompanyId(CLIENT_ID, COMPANY_ID))
                .thenReturn(Optional.of(claimRow()));
        when(accountRepository.save(any())).thenAnswer(inv -> {
            PartnerCompanyXtrmAccount a = inv.getArgument(0);
            saves.add(new SaveSnapshot(a.getStatus(), a.getXtrmAccountNumber(), a.getXtrmWalletId(),
                    a.getEncryptedCredentials(), a.getLastError(), a.getXtrmBeneficiaryName(),
                    a.getAccountIdentityLevel()));
            return a;
        });
        when(credentialsResolver.encryptCredentials(any(), any())).thenReturn("encrypted-blob");
    }

    private SaveSnapshot lastSave() {
        assertThat(saves).isNotEmpty();
        return saves.get(saves.size() - 1);
    }

    private PartnerCompany companyWithAdmin() {
        PartnerCompany company = PartnerCompany.builder()
                .name("Acme Corp").clientId(CLIENT_ID).website("https://acme.test")
                .adminFirstName("TestP").adminLastName("Singh").adminEmail("admin@acme.test")
                .adminMobileNumber("4085556245").adminCity("San Francisco").adminRegion("CA")
                .adminPostalCode("94105").adminCountryIso2("US")
                .build();
        company.setId(COMPANY_ID);
        return company;
    }

    private PartnerCompanyXtrmAccount claimRow() {
        return PartnerCompanyXtrmAccount.builder()
                .clientId(CLIENT_ID).partnerCompanyId(COMPANY_ID).status(XtrmAccountStatus.PENDING).build();
    }

    private void vendorSucceeds() {
        when(xtrmApiClient.createBeneficiary(any()))
                .thenReturn(CreateBeneficiaryResult.ok("SPN26241004", "2696718_API_User", "a-secret", "Basic"));
    }

    private void walletFound() {
        when(xtrmApiClient.getBeneficiaryWallets(any()))
                .thenReturn(GetWalletsResult.ok(List.of(new WalletInfo("206415", "Main", "USD", BigDecimal.ZERO))));
    }

    @Test
    void reachesConnectedWhenAllThreeCallsSucceed() {
        vendorSucceeds();
        walletFound();

        service.provision(CLIENT_ID, COMPANY_ID);

        assertThat(lastSave().status()).isEqualTo(XtrmAccountStatus.CONNECTED);
        assertThat(lastSave().accountNumber()).isEqualTo("SPN26241004");
        assertThat(lastSave().walletId()).isEqualTo("206415");
        assertThat(lastSave().credentials()).isEqualTo("encrypted-blob");
        assertThat(lastSave().identityLevel()).isEqualTo("Basic");
    }

    /**
     * The test this whole design exists for. If the credentials are not durable at this point they are
     * gone: the account exists at XTRM, its name is taken, and no endpoint returns the secret again.
     */
    @Test
    void persistsCredentialsEvenWhenWalletDiscoveryFails() {
        vendorSucceeds();
        when(xtrmApiClient.getBeneficiaryWallets(any())).thenThrow(new RuntimeException("XTRM unreachable"));

        service.provision(CLIENT_ID, COMPANY_ID);

        // The ordering assertion, and it has to be about call SEQUENCE rather than saved values. The
        // service mutates one entity, and the error path saves that same object — so a broken
        // implementation that only saved at the end would produce an identical final snapshot. What
        // distinguishes correct from broken is that a save happened *before* the wallet call at all.
        InOrder ordered = inOrder(accountRepository, xtrmApiClient);
        ordered.verify(accountRepository).save(any());
        ordered.verify(xtrmApiClient).getBeneficiaryWallets(any());

        assertThat(saves)
                .as("the pre-wallet save must already carry the credentials")
                .anyMatch(x -> "encrypted-blob".equals(x.credentials())
                        && "SPN26241004".equals(x.accountNumber())
                        && x.walletId() == null);

        assertThat(lastSave().status()).isEqualTo(XtrmAccountStatus.PENDING);
        assertThat(lastSave().credentials()).isEqualTo("encrypted-blob");
        assertThat(lastSave().lastError()).isNotBlank();
    }

    @Test
    void persistsCredentialsEvenWhenTheTokenCheckFails() {
        vendorSucceeds();
        when(credentialsResolver.forCompanyUnchecked(any())).thenThrow(new RuntimeException("bad credentials"));

        service.provision(CLIENT_ID, COMPANY_ID);

        assertThat(saves)
                .as("credentials must be durable before the token check is attempted")
                .anyMatch(x -> "encrypted-blob".equals(x.credentials()) && x.walletId() == null);
        assertThat(lastSave().status()).isEqualTo(XtrmAccountStatus.PENDING);
        assertThat(lastSave().credentials()).isEqualTo("encrypted-blob");
    }

    @Test
    void recordsTheErrorAndStaysPendingWhenCreateBeneficiaryFails() {
        when(xtrmApiClient.createBeneficiary(any()))
                .thenReturn(CreateBeneficiaryResult.failed(List.of("Company name already exists"), false));

        service.provision(CLIENT_ID, COMPANY_ID);

        assertThat(lastSave().status()).isEqualTo(XtrmAccountStatus.PENDING);
        assertThat(lastSave().accountNumber()).isNull();
        assertThat(lastSave().lastError()).contains("Company name already exists");
    }

    @Test
    void recordsAnErrorWhenXtrmReturnsNoWallet() {
        vendorSucceeds();
        when(xtrmApiClient.getBeneficiaryWallets(any())).thenReturn(GetWalletsResult.ok(List.of()));

        service.provision(CLIENT_ID, COMPANY_ID);

        assertThat(lastSave().status()).isEqualTo(XtrmAccountStatus.PENDING);
        assertThat(lastSave().lastError()).isNotBlank();
    }

    /**
     * Provisioning runs after the company is already committed. Throwing here cannot undo that commit; it
     * can only produce an unhandled error on a background thread.
     */
    @Test
    void neverThrows() {
        when(xtrmApiClient.createBeneficiary(any())).thenThrow(new RuntimeException("boom"));

        service.provision(CLIENT_ID, COMPANY_ID);
    }

    @Test
    void doesNothingWhenTheCompanyHasNoAdminDetails() {
        PartnerCompany bare = PartnerCompany.builder().name("Bare").clientId(CLIENT_ID).build();
        bare.setId(COMPANY_ID);
        when(companyRepository.findByIdAndClientId(COMPANY_ID, CLIENT_ID)).thenReturn(Optional.of(bare));

        service.provision(CLIENT_ID, COMPANY_ID);

        verify(xtrmApiClient, never()).createBeneficiary(any());
    }

    @Test
    void doesNothingWhenThereIsNoClaimRow() {
        when(accountRepository.findByClientIdAndPartnerCompanyId(CLIENT_ID, COMPANY_ID))
                .thenReturn(Optional.empty());

        service.provision(CLIENT_ID, COMPANY_ID);

        verify(xtrmApiClient, never()).createBeneficiary(any());
    }

    @Test
    void doesNotReplayCreateBeneficiaryWhenAlreadyConnected() {
        PartnerCompanyXtrmAccount connected = claimRow();
        connected.setStatus(XtrmAccountStatus.CONNECTED);
        connected.setXtrmAccountNumber("SPN26241004");
        connected.setXtrmWalletId("206415");
        connected.setEncryptedCredentials("encrypted-blob");
        when(accountRepository.findByClientIdAndPartnerCompanyId(CLIENT_ID, COMPANY_ID))
                .thenReturn(Optional.of(connected));

        service.provision(CLIENT_ID, COMPANY_ID);

        // The call is not replayable: a second one either fails on the duplicate name or mints a second
        // account for a single company.
        verify(xtrmApiClient, never()).createBeneficiary(any());
    }

    /**
     * Resuming a row that already holds credentials must not call CreateBeneficiary again — it should pick
     * up at wallet discovery.
     */
    @Test
    void resumesFromWalletDiscoveryWhenCredentialsAreAlreadyHeld() {
        PartnerCompanyXtrmAccount partial = claimRow();
        partial.setXtrmAccountNumber("SPN26241004");
        partial.setEncryptedCredentials("encrypted-blob");
        when(accountRepository.findByClientIdAndPartnerCompanyId(CLIENT_ID, COMPANY_ID))
                .thenReturn(Optional.of(partial));
        walletFound();

        service.provision(CLIENT_ID, COMPANY_ID);

        verify(xtrmApiClient, never()).createBeneficiary(any());
        assertThat(lastSave().status()).isEqualTo(XtrmAccountStatus.CONNECTED);
    }

    @Test
    void sendsTheDisambiguatedNameAndTheAdminBlockToXtrm() {
        vendorSucceeds();
        walletFound();

        service.provision(CLIENT_ID, COMPANY_ID);

        ArgumentCaptor<CreateBeneficiaryCommand> cmd = ArgumentCaptor.forClass(CreateBeneficiaryCommand.class);
        verify(xtrmApiClient).createBeneficiary(cmd.capture());

        assertThat(cmd.getValue().companyName()).contains("Acme Corp").contains("Apple");
        assertThat(cmd.getValue().adminEmail()).isEqualTo("admin@acme.test");
        assertThat(cmd.getValue().adminCountryIso2()).isEqualTo("US");
        // On by default: this is the email carrying the admin's XTRM portal credentials, and nothing else
        // in this system gives them out. Off, provisioning reports success while the admin cannot sign in.
        assertThat(cmd.getValue().emailNotification()).isTrue();
    }

    @Test
    void suppressesTheAdminEmailWhenTheEnvironmentAsksItTo() {
        ReflectionTestUtils.setField(service, "emailNotification", false);
        vendorSucceeds();
        walletFound();

        service.provision(CLIENT_ID, COMPANY_ID);

        ArgumentCaptor<CreateBeneficiaryCommand> cmd = ArgumentCaptor.forClass(CreateBeneficiaryCommand.class);
        verify(xtrmApiClient).createBeneficiary(cmd.capture());

        // Both directions, so this reads as pass-through of the setting rather than a fixed value.
        assertThat(cmd.getValue().emailNotification()).isFalse();
    }

    @Test
    void recordsTheNameItActuallySent() {
        vendorSucceeds();
        walletFound();

        service.provision(CLIENT_ID, COMPANY_ID);

        // Without this, nobody can match our row against XTRM's portal.
        assertThat(lastSave().beneficiaryName()).contains("Acme Corp");
    }

    @Test
    void disambiguatesTheBeneficiaryNameByTenant() {
        PartnerCompany company = PartnerCompany.builder().name("Acme Corp").clientId(CLIENT_ID).build();

        String name = service.beneficiaryNameFor(company, "Apple");

        assertThat(name).contains("Acme Corp").contains("Apple");
        assertThat(name.length()).isLessThanOrEqualTo(255);
    }

    @Test
    void truncatesAnOverlongBeneficiaryNameToWhatXtrmAccepts() {
        PartnerCompany company = PartnerCompany.builder().name("A".repeat(300)).clientId(CLIENT_ID).build();

        assertThat(service.beneficiaryNameFor(company, "Apple").length()).isEqualTo(255);
    }
}

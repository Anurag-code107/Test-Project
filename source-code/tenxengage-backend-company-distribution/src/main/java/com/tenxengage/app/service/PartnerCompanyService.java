package com.tenxengage.app.service;

import com.tenxengage.app.dto.request.ConnectXtrmAccountRequest;
import com.tenxengage.app.dto.request.CreatePartnerCompanyRequest;
import com.tenxengage.app.dto.request.CreateUserRequest;
import com.tenxengage.app.dto.request.UpdatePartnerCompanyRequest;
import com.tenxengage.app.dto.response.PartnerCompanyResponse;
import com.tenxengage.app.dto.response.PartnerCompanyXtrmAccountResponse;
import com.tenxengage.app.entity.Client;
import com.tenxengage.app.entity.ClientRole;
import com.tenxengage.app.entity.LocationValue;
import com.tenxengage.app.entity.PartnerCompany;
import com.tenxengage.app.entity.PartnerCompanyLocation;
import com.tenxengage.app.entity.PartnerCompanyXtrmAccount;
import com.tenxengage.app.entity.enums.PartnerCompanyStatus;
import com.tenxengage.app.entity.enums.XtrmAccountStatus;
import com.tenxengage.app.exception.BusinessRuleException;
import com.tenxengage.app.exception.ResourceNotFoundException;
import com.tenxengage.app.repository.ClientRepository;
import com.tenxengage.app.repository.ClientRoleRepository;
import com.tenxengage.app.repository.LocationValueRepository;
import com.tenxengage.app.repository.PartnerCompanyRepository;
import com.tenxengage.app.repository.PartnerCompanyXtrmAccountRepository;
import com.tenxengage.app.repository.UserRepository;
import com.tenxengage.app.security.TenantValidator;
import com.tenxengage.app.service.xtrm.PhoneDialCodes;
import com.tenxengage.app.service.xtrm.XtrmCompanyProvisioningService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class PartnerCompanyService {

    private final PartnerCompanyRepository partnerCompanyRepository;
    private final ClientRepository clientRepository;
    private final UserRepository userRepository;
    private final LocationValueRepository locationValueRepository;
    private final TenantValidator tenantValidator;
    private final XtrmCompanyProvisioningService provisioningService;
    private final PartnerCompanyXtrmAccountRepository xtrmAccountRepository;
    private final UserService userService;
    private final ClientRoleRepository clientRoleRepository;

    /**
     * Self-proxy, so {@code @Transactional} methods called from inside this class are actually proxied.
     *
     * <p>A {@code @Transactional} method invoked directly on {@code this} bypasses the Spring proxy and the
     * annotation silently does nothing. That matters for the connect flow, which deliberately splits a short
     * transactional step from the vendor calls that follow it. Same pattern
     * {@code CompanyDistributionDispatcher} uses.</p>
     */
    private PartnerCompanyService self;

    public PartnerCompanyService(PartnerCompanyRepository partnerCompanyRepository,
                                  ClientRepository clientRepository,
                                  UserRepository userRepository,
                                  LocationValueRepository locationValueRepository,
                                  TenantValidator tenantValidator,
                                  XtrmCompanyProvisioningService provisioningService,
                                  PartnerCompanyXtrmAccountRepository xtrmAccountRepository,
                                  UserService userService,
                                  ClientRoleRepository clientRoleRepository) {
        this.partnerCompanyRepository = partnerCompanyRepository;
        this.clientRepository = clientRepository;
        this.userRepository = userRepository;
        this.locationValueRepository = locationValueRepository;
        this.tenantValidator = tenantValidator;
        this.provisioningService = provisioningService;
        this.xtrmAccountRepository = xtrmAccountRepository;
        this.userService = userService;
        this.clientRoleRepository = clientRoleRepository;
    }

    @Autowired
    public void setSelf(@Lazy PartnerCompanyService self) {
        this.self = self;
    }

    @Transactional(readOnly = true)
    public Page<PartnerCompanyResponse> getPartnerCompanies(Pageable pageable, String search,
                                                             PartnerCompanyStatus status) {
        UUID clientId = tenantValidator.getCurrentClientId();
        Client client = clientRepository.findById(clientId)
            .orElseThrow(() -> new ResourceNotFoundException("Client", "id", clientId));

        // PARTNER_ADMIN and PARTNER_SELLER can only see their own partner company
        UUID partnerCompanyId = tenantValidator.getCurrentPartnerCompanyId();
        if (partnerCompanyId != null) {
            return partnerCompanyRepository.findByIdAndClientId(partnerCompanyId, clientId)
                .<Page<PartnerCompanyResponse>>map(pc -> new org.springframework.data.domain.PageImpl<>(
                    java.util.List.of(PartnerCompanyResponse.from(pc, client.getName())), pageable, 1))
                .orElseGet(() -> Page.empty(pageable));
        }

        Page<PartnerCompany> page = partnerCompanyRepository.searchByClientId(clientId, search, status, pageable);
        Map<UUID, Long> userCounts = getUserCountMap(clientId, page.getContent());
        return page.map(pc -> PartnerCompanyResponse.from(pc, client.getName(),
            userCounts.getOrDefault(pc.getId(), 0L)));
    }

    private Map<UUID, Long> getUserCountMap(UUID clientId, List<PartnerCompany> companies) {
        if (companies.isEmpty()) return Map.of();
        List<UUID> ids = companies.stream().map(PartnerCompany::getId).toList();
        return userRepository.countActiveUsersByPartnerCompanyIds(clientId, ids).stream()
            .collect(Collectors.toMap(
                row -> (UUID) row[0],
                row -> (Long) row[1]
            ));
    }

    @Transactional(readOnly = true)
    public PartnerCompanyResponse getPartnerCompanyById(UUID id) {
        UUID clientId = tenantValidator.getCurrentClientId();

        // PARTNER_ADMIN and PARTNER_SELLER can only access their own partner company
        UUID partnerCompanyId = tenantValidator.getCurrentPartnerCompanyId();
        if (partnerCompanyId != null) {
            tenantValidator.validatePartnerCompanyAccess(id);
        }

        PartnerCompany pc = partnerCompanyRepository.findByIdAndClientId(id, clientId)
            .orElseThrow(() -> new ResourceNotFoundException("PartnerCompany", "id", id));

        Client client = clientRepository.findById(clientId)
            .orElseThrow(() -> new ResourceNotFoundException("Client", "id", clientId));

        // The detail path carries the XTRM block; the list path does not, rather than issuing a query per row.
        return PartnerCompanyResponse.from(pc, client.getName(), 0,
            PartnerCompanyXtrmAccountResponse.from(
                xtrmAccountRepository.findByClientIdAndPartnerCompanyId(clientId, id).orElse(null)));
    }

    /**
     * The identity fields a client admin supplies at company creation — exactly what a login needs.
     *
     * <p>The address XTRM also wants is not here: the admin supplies that themselves once they sign in.</p>
     */
    private static final List<String> ADMIN_FIELD_NAMES = List.of(
            "adminFirstName", "adminLastName", "adminEmail", "adminMobileNumber", "adminCountryIso2");

    /**
     * All five identity fields or none.
     *
     * <p>A partial block cannot produce a usable login, and the email in particular is spent once — XTRM
     * refuses to reuse it — so a half-filled group must be refused at the boundary rather than discovered
     * later. Refusing here names the missing field while the caller can still act on it.</p>
     */
    void validateAdminDetails(CreatePartnerCompanyRequest r) {
        List<String> values = new ArrayList<>(List.of());
        values.add(r.adminFirstName());
        values.add(r.adminLastName());
        values.add(r.adminEmail());
        values.add(r.adminMobileNumber());
        values.add(r.adminCountryIso2());

        List<String> missing = new ArrayList<>();
        for (int i = 0; i < values.size(); i++) {
            String v = values.get(i);
            if (v == null || v.isBlank()) {
                missing.add(ADMIN_FIELD_NAMES.get(i));
            }
        }

        if (missing.size() == ADMIN_FIELD_NAMES.size()) {
            return; // none supplied — legitimate; the company simply has no payout intent yet
        }
        if (!missing.isEmpty()) {
            throw new BusinessRuleException("INVALID_ADMIN_DETAILS",
                    "Company admin details are incomplete. Missing: " + String.join(", ", missing));
        }
        // Checked here rather than at XTRM, because PhoneDialCodes is also what formats the number we send:
        // an unsupported country yields a null MobilePhone, which XTRM rejects with a far less useful message.
        if (!PhoneDialCodes.isSupported(r.adminCountryIso2())) {
            throw new BusinessRuleException("INVALID_ADMIN_DETAILS",
                    "XTRM does not support payouts for country " + r.adminCountryIso2() + ".");
        }
    }

    @Transactional
    public PartnerCompanyResponse createPartnerCompany(CreatePartnerCompanyRequest request) {
        UUID clientId = tenantValidator.getCurrentClientId();

        if (partnerCompanyRepository.existsByClientIdAndName(clientId, request.name())) {
            throw new BusinessRuleException("Partner company name already exists: " + request.name());
        }

        if (request.externalPartnerId() != null
                && partnerCompanyRepository.existsByClientIdAndExternalPartnerId(
                        clientId, request.externalPartnerId())) {
            throw new BusinessRuleException("Partner ID already exists: " + request.externalPartnerId());
        }

        validateAdminDetails(request);

        Client client = clientRepository.findById(clientId)
            .orElseThrow(() -> new ResourceNotFoundException("Client", "id", clientId));

        String metadata = request.metadata() != null ? request.metadata() : "{}";
        metadata = mergeMetadataFields(metadata, request.partnerType(), request.contactEmail());

        PartnerCompany pc = PartnerCompany.builder()
            .name(request.name())
            .externalPartnerId(request.externalPartnerId())
            .clientId(clientId)
            .status(request.status() != null ? request.status() : PartnerCompanyStatus.ACTIVE)
            .website(request.website())
            .contactPhone(request.contactPhone())
            .metadata(metadata)
            .adminFirstName(request.adminFirstName())
            .adminLastName(request.adminLastName())
            .adminEmail(request.adminEmail())
            .adminMobileNumber(request.adminMobileNumber())
            .adminCountryIso2(request.adminCountryIso2())
            .build();

        PartnerCompany saved = partnerCompanyRepository.save(pc);

        // Assign location values
        if (request.locationValueIds() != null) {
            assignLocationValues(saved, request.locationValueIds(), clientId);
        }

        // The admin gets a login now; the XTRM beneficiary waits until they have signed in and supplied
        // their own address. Provisioning here would send a client admin's guess at fields only the admin
        // knows — and the email that reaches XTRM cannot be corrected afterwards, because XTRM refuses to
        // reuse an address.
        if (saved.hasAdminIdentity()) {
            createDefaultAdminUser(clientId, saved);
        }

        return PartnerCompanyResponse.from(saved, client.getName());
    }

    /**
     * Give the company's default admin a login.
     *
     * <p>{@code UserService.createUser} already writes a placeholder password hash and issues an onboarding
     * token, so nothing extra is needed here — the admin sets their own password through that flow.</p>
     */
    private void createDefaultAdminUser(UUID clientId, PartnerCompany company) {
        UUID roleId = clientRoleRepository
                .findByClientIdAndBaseRoleNameAndSystemTrue(clientId, "PARTNER_ADMIN")
                .map(ClientRole::getId)
                .orElseThrow(() -> new BusinessRuleException("PARTNER_ADMIN_ROLE_MISSING",
                        "This client has no PARTNER_ADMIN role, so a company admin cannot be created."));

        userService.createUser(new CreateUserRequest(
                company.getAdminEmail(),
                company.getAdminFirstName(),
                company.getAdminLastName(),
                company.getAdminMobileNumber(),
                company.getAdminCountryIso2(),
                null,               // password: placeholder hash + onboarding token, set by the admin
                company.getId(),
                roleId,
                null));
    }

    @Transactional
    public PartnerCompanyResponse updatePartnerCompany(UUID id, UpdatePartnerCompanyRequest request) {
        UUID clientId = tenantValidator.getCurrentClientId();
        PartnerCompany pc = partnerCompanyRepository.findByIdAndClientId(id, clientId)
            .orElseThrow(() -> new ResourceNotFoundException("PartnerCompany", "id", id));

        Client client = clientRepository.findById(clientId)
            .orElseThrow(() -> new ResourceNotFoundException("Client", "id", clientId));

        if (request.name() != null) {
            if (!request.name().equals(pc.getName())
                    && partnerCompanyRepository.existsByClientIdAndName(clientId, request.name())) {
                throw new BusinessRuleException("Partner company name already exists: " + request.name());
            }
            pc.setName(request.name());
        }
        if (request.status() != null) {
            pc.setStatus(request.status());
        }
        if (request.website() != null) {
            pc.setWebsite(request.website());
        }
        if (request.contactPhone() != null) {
            pc.setContactPhone(request.contactPhone());
        }
        if (request.externalPartnerId() != null) {
            if (!request.externalPartnerId().equals(pc.getExternalPartnerId())
                    && partnerCompanyRepository.existsByClientIdAndExternalPartnerId(
                            clientId, request.externalPartnerId())) {
                throw new BusinessRuleException("Partner ID already exists: " + request.externalPartnerId());
            }
            pc.setExternalPartnerId(request.externalPartnerId());
        }
        if (request.locationValueIds() != null) {
            pc.getLocationAssignments().clear();
            partnerCompanyRepository.flush();
            assignLocationValues(pc, request.locationValueIds(), clientId);
        }
        if (request.metadata() != null) {
            pc.setMetadata(request.metadata());
        }
        // Patch semantics, matching the fields above: null leaves the stored value alone, so an update that
        // touches only the name need not resend the admin block.
        if (request.adminFirstName() != null) {
            pc.setAdminFirstName(request.adminFirstName());
        }
        if (request.adminLastName() != null) {
            pc.setAdminLastName(request.adminLastName());
        }
        if (request.adminEmail() != null) {
            pc.setAdminEmail(request.adminEmail());
        }
        if (request.adminMobileNumber() != null) {
            pc.setAdminMobileNumber(request.adminMobileNumber());
        }
        if (request.adminCity() != null) {
            pc.setAdminCity(request.adminCity());
        }
        if (request.adminRegion() != null) {
            pc.setAdminRegion(request.adminRegion());
        }
        if (request.adminPostalCode() != null) {
            pc.setAdminPostalCode(request.adminPostalCode());
        }
        if (request.adminCountryIso2() != null) {
            if (!PhoneDialCodes.isSupported(request.adminCountryIso2())) {
                throw new BusinessRuleException("INVALID_ADMIN_DETAILS",
                        "XTRM does not support payouts for country " + request.adminCountryIso2() + ".");
            }
            pc.setAdminCountryIso2(request.adminCountryIso2());
        }
        // Merge partnerType and contactEmail into metadata JSONB
        pc.setMetadata(mergeMetadataFields(pc.getMetadata(), request.partnerType(), request.contactEmail()));

        PartnerCompany updated = partnerCompanyRepository.save(pc);
        return PartnerCompanyResponse.from(updated, client.getName());
    }

    /**
     * Provision, retry, or finish a company's XTRM connection.
     *
     * <p>Which of the three happens is read off the row, not off a mode flag the caller has to get right.
     * Crucially it never re-runs {@code CreateBeneficiary} for a company that already has an SPN: that call
     * is not replayable, and a second one would either fail on the duplicate name or mint a second account
     * for a single company.</p>
     *
     * <p><b>Deliberately not {@code @Transactional}.</b> Provisioning makes three HTTP calls to XTRM, and
     * holding a database connection open for the vendor's latency is exactly what the create path goes out
     * of its way to avoid. The database work is {@link #prepareXtrmConnection}, which is transactional and
     * short; the vendor calls run outside it, and the response is built from a fresh read afterwards so it
     * still reports the real status.</p>
     */
    public PartnerCompanyResponse connectXtrmAccount(UUID id, ConnectXtrmAccountRequest request) {
        UUID clientId = tenantValidator.getCurrentClientId();

        // Through `self`, not `this`. A @Transactional method invoked directly on the same bean bypasses the
        // proxy, so the annotation would silently do nothing and the writes below would run unprotected.
        boolean needsProvisioning = self.prepareXtrmConnection(clientId, id, request);

        if (needsProvisioning) {
            provisioningService.provision(clientId, id);
        }

        return self.readCompanyWithXtrmAccount(clientId, id);
    }

    /**
     * The transactional half: persist any supplied admin details, finish the row if a wallet id was given by
     * hand, and claim a slot if there is not one yet.
     *
     * @return true when the caller should run the vendor calls afterwards
     */
    @Transactional
    public boolean prepareXtrmConnection(UUID clientId, UUID id, ConnectXtrmAccountRequest request) {
        PartnerCompany pc = partnerCompanyRepository.findByIdAndClientId(id, clientId)
            .orElseThrow(() -> new ResourceNotFoundException("PartnerCompany", "id", id));

        applyAdminDetails(pc, request);
        partnerCompanyRepository.save(pc);

        Optional<PartnerCompanyXtrmAccount> existing =
                xtrmAccountRepository.findByClientIdAndPartnerCompanyId(clientId, id);

        if (existing.isPresent() && existing.get().isPayoutReady()) {
            return false; // already connected, and CreateBeneficiary is not replayable
        }

        // A wallet id supplied by hand is the one case that finishes the row without calling XTRM — used
        // when wallet discovery could not find one. Only valid once the credentials are already held;
        // otherwise this would produce a CONNECTED row that cannot pay, which the CHECK constraint rejects.
        if (existing.isPresent()
                && request.xtrmWalletId() != null && !request.xtrmWalletId().isBlank()
                && existing.get().getXtrmAccountNumber() != null
                && existing.get().getEncryptedCredentials() != null) {
            PartnerCompanyXtrmAccount account = existing.get();
            account.setXtrmWalletId(request.xtrmWalletId());
            account.setStatus(XtrmAccountStatus.CONNECTED);
            account.setConnectedAt(Instant.now());
            account.setLastError(null);
            xtrmAccountRepository.save(account);
            return false;
        }

        if (existing.isEmpty()) {
            if (!pc.hasCompleteAdminDetails()) {
                throw new BusinessRuleException("INVALID_ADMIN_DETAILS",
                        "Company admin details are required before connecting to XTRM.");
            }
            provisioningService.claim(clientId, id);
        }
        return true;
    }

    /** Reads the company back with its current XTRM state, after provisioning has had its turn. */
    @Transactional(readOnly = true)
    public PartnerCompanyResponse readCompanyWithXtrmAccount(UUID clientId, UUID id) {
        PartnerCompany pc = partnerCompanyRepository.findByIdAndClientId(id, clientId)
            .orElseThrow(() -> new ResourceNotFoundException("PartnerCompany", "id", id));
        Client client = clientRepository.findById(clientId)
            .orElseThrow(() -> new ResourceNotFoundException("Client", "id", clientId));

        return PartnerCompanyResponse.from(pc, client.getName(), 0,
                PartnerCompanyXtrmAccountResponse.from(
                        xtrmAccountRepository.findByClientIdAndPartnerCompanyId(clientId, id).orElse(null)));
    }

    /** Copy any supplied admin field onto the company. Nulls are left alone so a retry need not resend them. */
    private void applyAdminDetails(PartnerCompany pc, ConnectXtrmAccountRequest r) {
        if (r.adminFirstName() != null) {
            pc.setAdminFirstName(r.adminFirstName());
        }
        if (r.adminLastName() != null) {
            pc.setAdminLastName(r.adminLastName());
        }
        if (r.adminEmail() != null) {
            pc.setAdminEmail(r.adminEmail());
        }
        if (r.adminMobileNumber() != null) {
            pc.setAdminMobileNumber(r.adminMobileNumber());
        }
        if (r.adminCity() != null) {
            pc.setAdminCity(r.adminCity());
        }
        if (r.adminRegion() != null) {
            pc.setAdminRegion(r.adminRegion());
        }
        if (r.adminPostalCode() != null) {
            pc.setAdminPostalCode(r.adminPostalCode());
        }
        if (r.adminCountryIso2() != null) {
            if (!PhoneDialCodes.isSupported(r.adminCountryIso2())) {
                throw new BusinessRuleException("INVALID_ADMIN_DETAILS",
                        "XTRM does not support payouts for country " + r.adminCountryIso2() + ".");
            }
            pc.setAdminCountryIso2(r.adminCountryIso2());
        }
    }

    @Transactional
    public void deletePartnerCompany(UUID id) {
        UUID clientId = tenantValidator.getCurrentClientId();
        PartnerCompany pc = partnerCompanyRepository.findByIdAndClientId(id, clientId)
            .orElseThrow(() -> new ResourceNotFoundException("PartnerCompany", "id", id));

        // partner_company_xtrm_accounts has a foreign key to this row, so leaving it makes every
        // provisioned company undeletable — surfacing as a generic DATA_INTEGRITY_VIOLATION whose fixed
        // message names neither the constraint nor the reason. The first report would be "delete is
        // broken", not "this company has an XTRM account".
        //
        // Nothing is deleted at XTRM: we have no endpoint for it, and an abandoned beneficiary company
        // whose credentials we no longer hold can move no money. DISABLED is set first so the row's final
        // state is truthful for anything reading it in the same transaction.
        xtrmAccountRepository.findByClientIdAndPartnerCompanyId(clientId, id)
            .ifPresent(account -> {
                account.setStatus(XtrmAccountStatus.DISABLED);
                xtrmAccountRepository.delete(account);
            });

        partnerCompanyRepository.delete(pc);
    }

    private void assignLocationValues(PartnerCompany pc, List<UUID> locationValueIds, UUID clientId) {
        List<LocationValue> values = locationValueRepository.findByIdIn(locationValueIds);
        for (LocationValue lv : values) {
            if (!lv.getClientId().equals(clientId)) {
                throw new BusinessRuleException("Location value " + lv.getId() + " does not belong to this client");
            }
            PartnerCompanyLocation pcl = PartnerCompanyLocation.builder()
                .clientId(clientId)
                .partnerCompany(pc)
                .locationValue(lv)
                .build();
            pc.getLocationAssignments().add(pcl);
        }
    }

    private String mergeMetadataFields(String existingMetadata, String partnerType, String contactEmail) {
        try {
            var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            var map = mapper.readValue(
                existingMetadata != null && !existingMetadata.isBlank() ? existingMetadata : "{}",
                new com.fasterxml.jackson.core.type.TypeReference<java.util.LinkedHashMap<String, Object>>() {});
            if (partnerType != null) {
                map.put("Partner Type", partnerType);
            }
            if (contactEmail != null) {
                map.put("Contact Email", contactEmail);
            }
            return mapper.writeValueAsString(map);
        } catch (Exception e) {
            return existingMetadata != null ? existingMetadata : "{}";
        }
    }
}

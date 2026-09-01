# Company Admin Self-Service Provisioning — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A company's default admin is created as a real login when the company is created, and **they** complete their own profile — which is what provisions the company's XTRM beneficiary.

**Architecture:** The trigger moves from company creation to profile completion, mirroring how personal redemption already works: a seller's login is created with identity and contact, then they supply the address and that triggers enrollment. Company creation now creates a `PARTNER_ADMIN` user and stops; provisioning happens when that admin fills in the four address fields XTRM needs.

**Tech Stack:** Java 21, Spring Boot, PostgreSQL, JUnit 5 + Mockito + AssertJ. Frontend: React 18, TypeScript, Vitest.

**Spec:** [`2026-08-24-company-beneficiary-provisioning-design.md`](2026-08-24-company-beneficiary-provisioning-design.md) — this plan **reverses D-1** of that design. See "What changes and why" below.

## Global Constraints

- **Worktrees:** `../tenxengage-backend-company-distribution`, `../tenxengage-frontend-company-distribution`, branch `features/company-distribution-store`.
- **Never `git add -A`.** Stage by explicit path.
- **Backend baseline: 1 pre-existing failure** — `IncentiveServiceTest.generateForecastStreaming_...` fails deterministically in isolation and is unrelated to this work (neither it nor `IncentiveService` has changed since April). Everything else passes. Do not chase it.
- Full-suite runs are unreliable on this machine — two were killed mid-run. Run the affected suites per task and treat a full run as a bonus, not a gate.
- **`integrationTest` runs against the LIVE dev database.** Do not run it.

### What changes, and why

| | Before | After |
|---|---|---|
| Company create | client admin types 8 admin fields; provisions immediately | client admin types 5; **creates a PARTNER_ADMIN login**; provisions nothing |
| Admin completes profile | — | supplies 4 address fields → **provisions** |
| Client-admin `connect` | fallback | unchanged, kept |

**Why it is better, in one sentence:** XTRM refuses a duplicate email permanently (`"Email Already Exists"`), so a client admin's typo in the admin email burns that address forever — the person who owns the email is the right person to type it.

### D-1 is reversed

D-1 read: *"The company admin is **contact details on the company**, not a TenXEngage user account. No invite, no role, no password."*

They now log in, so that is no longer true. **D-16 supersedes it:** the default company admin **is** a `PARTNER_ADMIN` user, created with the company. `UserService.createUser` already writes a placeholder password hash and generates an onboarding token — no new mechanism is needed. Email delivery fails silently in local, which is expected; passwords are set directly in the database for testing.

### The field split follows from `CreateUserRequest`

`phone` and `phoneCountryIso2` are `@NotBlank` on `CreateUserRequest`, so mobile cannot wait for profile completion.

| Stage | Fields | Rationale |
|---|---|---|
| Company create | `adminFirstName`, `adminLastName`, `adminEmail`, `adminMobileNumber`, `adminCountryIso2` | exactly what a user record needs |
| Admin completes | `adminCity`, `adminRegion`, `adminPostalCode` | XTRM's `BeneficiaryCompanyAdminDetails` needs no address line |

`adminCountryIso2` is at create-time because it doubles as `phoneCountryIso2` for the user record.

---

## File Structure

**Backend — created**

| File | Responsibility |
|---|---|
| `dto/request/CompleteCompanyAdminProfileRequest.java` | the three address fields the admin supplies |
| `controller/CompanyAdminProfileController.java` | `GET`/`PUT /api/v1/company-admin/profile` — the admin's own view and completion |
| `service/CompanyAdminProfileService.java` | resolves the caller's company, saves the address, triggers provisioning |
| `dto/response/CompanyAdminProfileResponse.java` | what is stored, what is missing, and the XTRM status |

**Backend — modified**

| File | Change |
|---|---|
| `dto/request/CreatePartnerCompanyRequest.java` | 8 admin fields → 5 |
| `service/PartnerCompanyService.java` | create the PARTNER_ADMIN user; stop provisioning at create |
| `entity/PartnerCompany.java` | `hasCompleteAdminDetails()` unchanged; add `hasAdminIdentity()` |

**Frontend — modified**

`pages/client-admin/UserSettingsPage.tsx`, `components/settings/PartnerCompanyAdminFields.tsx` (5 fields), plus a new company-admin profile screen.

---

## Task 1: Split the admin fields at the company boundary

**Files:**
- Modify: `src/main/java/com/tenxengage/app/dto/request/CreatePartnerCompanyRequest.java`
- Modify: `src/main/java/com/tenxengage/app/dto/request/UpdatePartnerCompanyRequest.java`
- Modify: `src/main/java/com/tenxengage/app/entity/PartnerCompany.java`
- Modify: `src/main/java/com/tenxengage/app/service/PartnerCompanyService.java`
- Test: `src/test/java/com/tenxengage/app/service/PartnerCompanyAdminIdentityTest.java`

**Interfaces:**
- Consumes: nothing.
- Produces:
  - `CreatePartnerCompanyRequest` keeps `adminFirstName`, `adminLastName`, `adminEmail`, `adminMobileNumber`, `adminCountryIso2`; **drops** `adminCity`, `adminRegion`, `adminPostalCode`.
  - `PartnerCompany.hasAdminIdentity()` → `boolean`, true when the five create-time fields are present.
  - `PartnerCompany.hasCompleteAdminDetails()` — unchanged, still requires all eight.

- [ ] **Step 1: Write the failing test**

Create `src/test/java/com/tenxengage/app/service/PartnerCompanyAdminIdentityTest.java`:

```java
package com.tenxengage.app.service;

import com.tenxengage.app.entity.PartnerCompany;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Two different questions about the same eight fields.
 *
 * <p>{@code hasAdminIdentity} asks "can this person be given a login?" — answered at company creation.
 * {@code hasCompleteAdminDetails} asks "can XTRM create a beneficiary for them?" — answered only once the
 * admin has filled in their own address. Conflating the two is what made provisioning fire too early.</p>
 */
class PartnerCompanyAdminIdentityTest {

    private PartnerCompany.PartnerCompanyBuilder withIdentity() {
        return PartnerCompany.builder()
                .name("Acme Corp")
                .adminFirstName("TestP")
                .adminLastName("Singh")
                .adminEmail("admin@acme.test")
                .adminMobileNumber("4085556245")
                .adminCountryIso2("US");
    }

    @Test
    void identityIsCompleteWithTheFiveCreateTimeFields() {
        assertThat(withIdentity().build().hasAdminIdentity()).isTrue();
    }

    @Test
    void identityAloneIsNotEnoughForXtrm() {
        // The address is still missing, so provisioning must not fire yet.
        assertThat(withIdentity().build().hasCompleteAdminDetails()).isFalse();
    }

    @Test
    void bothAreCompleteOnceTheAddressArrives() {
        PartnerCompany full = withIdentity()
                .adminCity("San Francisco").adminRegion("CA").adminPostalCode("94105").build();

        assertThat(full.hasAdminIdentity()).isTrue();
        assertThat(full.hasCompleteAdminDetails()).isTrue();
    }

    @Test
    void identityIsIncompleteWithoutAnEmail() {
        // The email is the login and the XTRM identity — it is the one field that cannot be supplied later.
        assertThat(withIdentity().adminEmail(null).build().hasAdminIdentity()).isFalse();
    }

    @Test
    void identityIsIncompleteWithoutAMobile() {
        // CreateUserRequest requires phone + phoneCountryIso2, so a login cannot be made without it.
        assertThat(withIdentity().adminMobileNumber("  ").build().hasAdminIdentity()).isFalse();
    }
}
```

- [ ] **Step 2: Run it and confirm it fails**

Run: `./gradlew test --tests "com.tenxengage.app.service.PartnerCompanyAdminIdentityTest"`
Expected: FAIL — `hasAdminIdentity` does not exist.

- [ ] **Step 3: Add `hasAdminIdentity` to the entity**

In `PartnerCompany`, beside `hasCompleteAdminDetails`:

```java
/**
 * True when the admin can be given a login — the five fields {@code CreateUserRequest} needs.
 *
 * <p>Distinct from {@link #hasCompleteAdminDetails()} on purpose: a company can have an admin who can sign
 * in long before that admin has supplied the address XTRM requires. Provisioning waits for the second.</p>
 */
public boolean hasAdminIdentity() {
    return notBlank(adminFirstName) && notBlank(adminLastName) && notBlank(adminEmail)
            && notBlank(adminMobileNumber) && notBlank(adminCountryIso2);
}
```

- [ ] **Step 4: Narrow the create request to five fields**

In `CreatePartnerCompanyRequest`, delete the `adminCity`, `adminRegion` and `adminPostalCode` components, and replace the group comment:

```java
    // --- Default company admin (D-16) -------------------------------------------------------------
    //
    // Identity only: enough to create this person's login. The address XTRM also needs is supplied by the
    // admin themselves, because they are the ones who know it — and because a mistyped admin email burns
    // that address at XTRM permanently.
    //
    // All five or none; enforced as a group in PartnerCompanyService.validateAdminDetails.
```

Leave `UpdatePartnerCompanyRequest` with all eight: a client admin correcting a company's stored details should still be able to reach every field.

- [ ] **Step 5: Narrow the validator to the same five**

In `PartnerCompanyService`, change `ADMIN_FIELD_NAMES` and `validateAdminDetails` to cover only the five create-time fields. The country check stays — it is still what `PhoneDialCodes` will format the mobile against.

```java
private static final List<String> ADMIN_FIELD_NAMES = List.of(
        "adminFirstName", "adminLastName", "adminEmail", "adminMobileNumber", "adminCountryIso2");
```

Remove the three `values.add(...)` lines for city, region and postal code.

- [ ] **Step 6: Run the affected tests**

Run: `./gradlew test --tests "com.tenxengage.app.service.PartnerCompany*"`
Expected: FAIL in `PartnerCompanyAdminDetailsTest` and any test constructing `CreatePartnerCompanyRequest` — the record has three fewer components. Update each construction site; the cases about the all-or-nothing rule still apply, now over five fields.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/tenxengage/app/dto/request/CreatePartnerCompanyRequest.java \
        src/main/java/com/tenxengage/app/entity/PartnerCompany.java \
        src/main/java/com/tenxengage/app/service/PartnerCompanyService.java \
        src/test/java/com/tenxengage/app/service/PartnerCompanyAdminIdentityTest.java \
        src/test/java/com/tenxengage/app/service/PartnerCompanyAdminDetailsTest.java
git commit -m "feat(partner-company): split admin identity from the address XTRM needs"
```

---

## Task 2: Create the admin's login with the company

**Files:**
- Modify: `src/main/java/com/tenxengage/app/service/PartnerCompanyService.java`
- Test: `src/test/java/com/tenxengage/app/service/PartnerCompanyAdminUserTest.java`

**Interfaces:**
- Consumes: `PartnerCompany.hasAdminIdentity()`; `UserService.createUser(CreateUserRequest)`; `ClientRoleRepository.findByClientIdAndBaseRoleNameAndSystemTrue(UUID, String)`.
- Produces: `PartnerCompanyService` gains `UserService` and `ClientRoleRepository` as constructor parameters (positions 8 and 9). Creating a company with admin identity now creates a `PARTNER_ADMIN` user for it.

- [ ] **Step 1: Write the failing test**

Create `src/test/java/com/tenxengage/app/service/PartnerCompanyAdminUserTest.java`:

```java
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
}
```

- [ ] **Step 2: Run it and confirm it fails**

Run: `./gradlew test --tests "com.tenxengage.app.service.PartnerCompanyAdminUserTest"`
Expected: FAIL — the constructor takes seven arguments, not nine.

- [ ] **Step 3: Add the two collaborators**

Extend `PartnerCompanyService`'s constructor with `UserService userService` and `ClientRoleRepository clientRoleRepository`, assigning both to final fields.

- [ ] **Step 4: Create the login, and stop provisioning at create**

Replace the provisioning block at the end of `createPartnerCompany` with:

```java
        // The admin gets a login now; the XTRM beneficiary waits until they have supplied their own
        // address. UserService.createUser already writes a placeholder password hash and issues an
        // onboarding token, so nothing extra is needed here.
        if (saved.hasAdminIdentity()) {
            createDefaultAdminUser(clientId, saved);
        }

        return PartnerCompanyResponse.from(saved, client.getName());
    }

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
                null,             // password: placeholder hash + onboarding token, set by the admin
                company.getId(),
                roleId,
                null));
    }
```

Delete `registerProvisioningAfterCommit` and its call — nothing triggers provisioning from company creation any more. Leave the method on `XtrmCompanyProvisioningService` untouched; Task 3 calls it.

- [ ] **Step 5: Run the affected tests**

Run: `./gradlew test --tests "com.tenxengage.app.service.PartnerCompany*"`
Expected: FAIL in `PartnerCompanyProvisioningWiringTest` — it asserts that creating a company claims and provisions. That behaviour has moved. Rewrite those cases to assert the login is created and provisioning is **not** triggered; keep the delete cases, which are unchanged.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/tenxengage/app/service/PartnerCompanyService.java \
        src/test/java/com/tenxengage/app/service/PartnerCompanyAdminUserTest.java \
        src/test/java/com/tenxengage/app/service/PartnerCompanyProvisioningWiringTest.java
git commit -m "feat(partner-company): create the default admin's login, stop provisioning at create"
```

---

## Task 3: The admin completes their profile, and that provisions

**Files:**
- Create: `src/main/java/com/tenxengage/app/dto/request/CompleteCompanyAdminProfileRequest.java`
- Create: `src/main/java/com/tenxengage/app/dto/response/CompanyAdminProfileResponse.java`
- Create: `src/main/java/com/tenxengage/app/service/CompanyAdminProfileService.java`
- Create: `src/main/java/com/tenxengage/app/controller/CompanyAdminProfileController.java`
- Test: `src/test/java/com/tenxengage/app/service/CompanyAdminProfileServiceTest.java`

**Interfaces:**
- Consumes: `PartnerCompany.hasCompleteAdminDetails()`, `XtrmCompanyProvisioningService.claim`/`provision`, `TenantValidator.getCurrentClientId()` / `getCurrentPartnerCompanyId()`.
- Produces:
  - `CompleteCompanyAdminProfileRequest(String adminCity, String adminRegion, String adminPostalCode)` — all `@NotBlank`.
  - `CompanyAdminProfileResponse(String companyName, String adminEmail, boolean complete, PartnerCompanyXtrmAccountResponse xtrmAccount)`.
  - `CompanyAdminProfileService.getProfile()` and `.completeProfile(CompleteCompanyAdminProfileRequest)`, both returning `CompanyAdminProfileResponse`.

- [ ] **Step 1: Write the failing test**

Create `src/test/java/com/tenxengage/app/service/CompanyAdminProfileServiceTest.java`:

```java
package com.tenxengage.app.service;

import com.tenxengage.app.dto.request.CompleteCompanyAdminProfileRequest;
import com.tenxengage.app.entity.PartnerCompany;
import com.tenxengage.app.exception.BusinessRuleException;
import com.tenxengage.app.repository.PartnerCompanyRepository;
import com.tenxengage.app.repository.PartnerCompanyXtrmAccountRepository;
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
 * The company admin completing their own profile is what provisions the company's XTRM beneficiary.
 *
 * <p>They supply the address; the identity was set when their login was created. Provisioning fires here
 * rather than at company creation because these are the fields only they know — and because the email that
 * goes to XTRM cannot be corrected later.</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CompanyAdminProfileServiceTest {

    @Mock private PartnerCompanyRepository companyRepository;
    @Mock private PartnerCompanyXtrmAccountRepository xtrmAccountRepository;
    @Mock private TenantValidator tenantValidator;
    @Mock private XtrmCompanyProvisioningService provisioningService;

    private CompanyAdminProfileService service;

    private static final UUID CLIENT_ID = UUID.randomUUID();
    private static final UUID COMPANY_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new CompanyAdminProfileService(companyRepository, xtrmAccountRepository,
                tenantValidator, provisioningService);
        service.setSelf(service);

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
                .adminFirstName("TestP").adminLastName("Singh").adminEmail("admin@acme.test")
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
    void claimsBeforeItProvisions() {
        service.completeProfile(address());

        org.mockito.InOrder ordered = org.mockito.Mockito.inOrder(provisioningService);
        ordered.verify(provisioningService).claim(CLIENT_ID, COMPANY_ID);
        ordered.verify(provisioningService).provision(CLIENT_ID, COMPANY_ID);
    }

    @Test
    void doesNotClaimTwiceWhenAlreadyClaimed() {
        when(xtrmAccountRepository.findByClientIdAndPartnerCompanyId(CLIENT_ID, COMPANY_ID))
                .thenReturn(Optional.of(new com.tenxengage.app.entity.PartnerCompanyXtrmAccount()));

        service.completeProfile(address());

        // uq_xtrm_account_per_company would reject a second claim; resubmitting a profile must retry, not fail.
        verify(provisioningService, never()).claim(any(), any());
        verify(provisioningService).provision(CLIENT_ID, COMPANY_ID);
    }

    @Test
    void refusesACallerWhoBelongsToNoCompany() {
        when(tenantValidator.getCurrentPartnerCompanyId()).thenReturn(null);

        assertThatThrownBy(() -> service.completeProfile(address()))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void refusesWhenTheCompanyHasNoAdminIdentity() {
        PartnerCompany bare = PartnerCompany.builder().name("Bare").clientId(CLIENT_ID).build();
        bare.setId(COMPANY_ID);
        when(companyRepository.findByIdAndClientId(COMPANY_ID, CLIENT_ID)).thenReturn(Optional.of(bare));

        // Nothing to send: the identity fields are set at company creation and cannot be supplied here.
        assertThatThrownBy(() -> service.completeProfile(address()))
                .isInstanceOf(BusinessRuleException.class);

        verify(provisioningService, never()).provision(any(), any());
    }

    @Test
    void reportsWhetherTheProfileIsComplete() {
        assertThat(service.getProfile().complete()).isFalse();
    }
}
```

- [ ] **Step 2: Run it and confirm it fails**

Run: `./gradlew test --tests "com.tenxengage.app.service.CompanyAdminProfileServiceTest"`
Expected: FAIL — none of the new types exist.

- [ ] **Step 3: Create the request and response**

`CompleteCompanyAdminProfileRequest`:

```java
package com.tenxengage.app.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * What a company admin supplies to finish their payout setup.
 *
 * <p>Address only. Their name, email, mobile and country were set when their login was created, and the
 * email in particular cannot be changed here — XTRM refuses to reuse an address, so it is spent once.</p>
 *
 * <p>No address line: XTRM's {@code BeneficiaryCompanyAdminDetails} does not take one.</p>
 */
public record CompleteCompanyAdminProfileRequest(
    @NotBlank(message = "City is required") @Size(max = 100) String adminCity,
    @NotBlank(message = "State/region is required") @Size(max = 100) String adminRegion,
    @NotBlank(message = "Postal code is required") @Size(max = 20) String adminPostalCode
) {}
```

`CompanyAdminProfileResponse`:

```java
package com.tenxengage.app.dto.response;

/**
 * What the company admin sees on their own payout-setup screen.
 *
 * <p>{@code complete} is what the UI gates on: false means the address is still missing and the company
 * cannot be provisioned. Carries no credentials, and no other company's data.</p>
 */
public record CompanyAdminProfileResponse(
    String companyName,
    String adminEmail,
    String adminCity,
    String adminRegion,
    String adminPostalCode,
    boolean complete,
    PartnerCompanyXtrmAccountResponse xtrmAccount
) {}
```

- [ ] **Step 4: Implement the service**

```java
package com.tenxengage.app.service;

import com.tenxengage.app.dto.request.CompleteCompanyAdminProfileRequest;
import com.tenxengage.app.dto.response.CompanyAdminProfileResponse;
import com.tenxengage.app.dto.response.PartnerCompanyXtrmAccountResponse;
import com.tenxengage.app.entity.PartnerCompany;
import com.tenxengage.app.exception.BusinessRuleException;
import com.tenxengage.app.exception.ResourceNotFoundException;
import com.tenxengage.app.repository.PartnerCompanyRepository;
import com.tenxengage.app.repository.PartnerCompanyXtrmAccountRepository;
import com.tenxengage.app.security.TenantValidator;
import com.tenxengage.app.service.xtrm.XtrmCompanyProvisioningService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * A company admin finishing their own payout setup.
 *
 * <p>Mirrors how a seller completes their redemption profile: identity comes from their user record,
 * they supply the address, and that is what triggers enrollment at the vendor. Doing it here rather than at
 * company creation means the person who owns the email is the one who types it — and XTRM refuses to reuse
 * an email, so a typo is permanent.</p>
 */
@Service
public class CompanyAdminProfileService {

    private final PartnerCompanyRepository companyRepository;
    private final PartnerCompanyXtrmAccountRepository xtrmAccountRepository;
    private final TenantValidator tenantValidator;
    private final XtrmCompanyProvisioningService provisioningService;

    /** Self-proxy: the transactional save must be proxied when called from the non-transactional method. */
    private CompanyAdminProfileService self;

    public CompanyAdminProfileService(PartnerCompanyRepository companyRepository,
                                      PartnerCompanyXtrmAccountRepository xtrmAccountRepository,
                                      TenantValidator tenantValidator,
                                      XtrmCompanyProvisioningService provisioningService) {
        this.companyRepository = companyRepository;
        this.xtrmAccountRepository = xtrmAccountRepository;
        this.tenantValidator = tenantValidator;
        this.provisioningService = provisioningService;
    }

    @Autowired
    public void setSelf(@Lazy CompanyAdminProfileService self) {
        this.self = self;
    }

    @Transactional(readOnly = true)
    public CompanyAdminProfileResponse getProfile() {
        return toResponse(loadOwnCompany());
    }

    /**
     * Save the address, then provision.
     *
     * <p>Not {@code @Transactional}: provisioning makes three HTTP calls to XTRM, and holding a database
     * connection open for the vendor's latency is what the create path already avoids.</p>
     */
    public CompanyAdminProfileResponse completeProfile(CompleteCompanyAdminProfileRequest request) {
        UUID clientId = tenantValidator.getCurrentClientId();
        UUID companyId = self.saveAddressAndClaim(request);

        provisioningService.provision(clientId, companyId);

        return toResponse(loadOwnCompany());
    }

    /** The transactional half: persist the address and reserve the provisioning slot. */
    @Transactional
    public UUID saveAddressAndClaim(CompleteCompanyAdminProfileRequest request) {
        UUID clientId = tenantValidator.getCurrentClientId();
        PartnerCompany company = loadOwnCompany();

        if (!company.hasAdminIdentity()) {
            throw new BusinessRuleException("ADMIN_IDENTITY_MISSING",
                    "This company has no admin identity on file. Ask your administrator to set one.");
        }

        company.setAdminCity(request.adminCity());
        company.setAdminRegion(request.adminRegion());
        company.setAdminPostalCode(request.adminPostalCode());
        companyRepository.save(company);

        // Claim only if nobody has: uq_xtrm_account_per_company would reject a second row, and a
        // resubmitted profile must retry rather than fail.
        if (xtrmAccountRepository.findByClientIdAndPartnerCompanyId(clientId, company.getId()).isEmpty()) {
            provisioningService.claim(clientId, company.getId());
        }
        return company.getId();
    }

    /**
     * The caller's own company, and only ever that one.
     *
     * <p>Read from the security context rather than a path variable, so this endpoint cannot be pointed at
     * another company by changing an id.</p>
     */
    private PartnerCompany loadOwnCompany() {
        UUID clientId = tenantValidator.getCurrentClientId();
        UUID companyId = tenantValidator.getCurrentPartnerCompanyId();
        if (companyId == null) {
            throw new BusinessRuleException("NOT_A_COMPANY_ADMIN",
                    "Only a partner company's admin can complete this profile.");
        }
        return companyRepository.findByIdAndClientId(companyId, clientId)
                .orElseThrow(() -> new ResourceNotFoundException("PartnerCompany", "id", companyId));
    }

    private CompanyAdminProfileResponse toResponse(PartnerCompany company) {
        UUID clientId = tenantValidator.getCurrentClientId();
        return new CompanyAdminProfileResponse(
                company.getName(),
                company.getAdminEmail(),
                company.getAdminCity(),
                company.getAdminRegion(),
                company.getAdminPostalCode(),
                company.hasCompleteAdminDetails(),
                PartnerCompanyXtrmAccountResponse.from(
                        xtrmAccountRepository.findByClientIdAndPartnerCompanyId(clientId, company.getId())
                                .orElse(null)));
    }
}
```

- [ ] **Step 5: Add the controller**

```java
package com.tenxengage.app.controller;

import com.tenxengage.app.audit.Audited;
import com.tenxengage.app.dto.request.CompleteCompanyAdminProfileRequest;
import com.tenxengage.app.dto.response.CompanyAdminProfileResponse;
import com.tenxengage.app.security.RequiresPermission;
import com.tenxengage.app.service.CompanyAdminProfileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * A company admin's own payout setup. Scoped to the caller's company by the security context — there is no
 * company id in the path, so it cannot be aimed at anyone else's.
 */
@RestController
@RequestMapping("/api/v1/company-admin/profile")
@Tag(name = "Company Admin Profile", description = "A partner company admin completing their payout setup")
public class CompanyAdminProfileController {

    private final CompanyAdminProfileService service;

    public CompanyAdminProfileController(CompanyAdminProfileService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "My company's payout setup")
    @RequiresPermission("action.redemption.distribute")
    public ResponseEntity<CompanyAdminProfileResponse> getProfile() {
        return ResponseEntity.ok(service.getProfile());
    }

    @PutMapping
    @Operation(summary = "Complete my company's payout setup",
               description = "Saves the admin address and provisions the company's payout account.")
    @RequiresPermission("action.redemption.distribute")
    @Audited(action = "Completed company admin profile", resourceType = "PARTNER_COMPANY",
             resourceName = "#result.body.companyName")
    public ResponseEntity<CompanyAdminProfileResponse> completeProfile(
            @Valid @RequestBody CompleteCompanyAdminProfileRequest request) {
        return ResponseEntity.ok(service.completeProfile(request));
    }
}
```

**On the permission:** `action.redemption.distribute` is used because a partner admin already has it (verified against `partneradmin@techpartners.com`) and it is exactly the population that needs this screen. A dedicated permission would be cleaner but requires seeding **both** `client_role_permissions` and `client_permission_grants`, or Layer-0 strips it and every call 403s. Reusing an existing grant avoids that entirely.

- [ ] **Step 6: Run the tests**

Run: `./gradlew test --tests "com.tenxengage.app.service.CompanyAdminProfileServiceTest"`
Expected: PASS

- [ ] **Step 7: Run the affected suites and commit**

Run: `./gradlew test --tests "com.tenxengage.app.service.PartnerCompany*" --tests "com.tenxengage.app.service.CompanyAdminProfile*" --tests "com.tenxengage.app.service.xtrm.*"`
Expected: PASS

```bash
git add src/main/java/com/tenxengage/app/dto/request/CompleteCompanyAdminProfileRequest.java \
        src/main/java/com/tenxengage/app/dto/response/CompanyAdminProfileResponse.java \
        src/main/java/com/tenxengage/app/service/CompanyAdminProfileService.java \
        src/main/java/com/tenxengage/app/controller/CompanyAdminProfileController.java \
        src/test/java/com/tenxengage/app/service/CompanyAdminProfileServiceTest.java
git commit -m "feat(partner-company): the company admin completes their own profile, and that provisions"
```

---

## Task 4: The two frontend screens

**Files:**
- Modify: `src/components/settings/PartnerCompanyAdminFields.tsx` — 8 fields → 5
- Modify: `src/types/partner-company.types.ts`
- Create: `src/services/company-admin-profile.service.ts`
- Create: `src/hooks/useCompanyAdminProfile.ts`
- Create: `src/pages/distribution/CompanyPayoutSetupPage.tsx`
- Test: `src/components/settings/__tests__/PartnerCompanyAdminFields.test.tsx` (update), `src/pages/distribution/__tests__/CompanyPayoutSetupPage.test.tsx` (new)

**Interfaces:**
- Consumes: Task 3's endpoints.
- Produces: `getCompanyAdminProfile()`, `completeCompanyAdminProfile(data)`, `useCompanyAdminProfile()`, `useCompleteCompanyAdminProfile()`.

- [ ] **Step 1: Narrow the client-admin form to five fields**

In `PartnerCompanyAdminFields.tsx`, reduce `ADMIN_FIELD_KEYS` to the create-time five and update the helper text:

```ts
export const ADMIN_FIELD_KEYS = [
  "adminFirstName",
  "adminLastName",
  "adminEmail",
  "adminMobileNumber",
  "adminCountryIso2",
] as const;
```

> These details create the company admin's login. They&apos;ll sign in and finish the payout setup themselves — so the email must be one they can receive at.

- [ ] **Step 2: Update its tests**

`findMissingAdminFields` now covers five. The existing all-or-nothing cases still apply; drop the city/region/postal assertions and keep everything else.

Run: `npx vitest run src/components/settings`
Expected: PASS

- [ ] **Step 3: Add the service and hook**

Follow the conventions in `partner-company.service.ts` exactly — `api` from `@/lib/axios`, `export async function`, and `response.data.data` because every response is wrapped in `ApiResponse<T>`:

```ts
export async function getCompanyAdminProfile(): Promise<CompanyAdminProfile> {
  const response = await api.get<ApiResponse<CompanyAdminProfile>>("/company-admin/profile");
  return response.data.data;
}

export async function completeCompanyAdminProfile(
  data: CompleteCompanyAdminProfileRequest,
): Promise<CompanyAdminProfile> {
  const response = await api.put<ApiResponse<CompanyAdminProfile>>("/company-admin/profile", data);
  return response.data.data;
}
```

- [ ] **Step 4: Build the setup page**

Create `src/pages/distribution/CompanyPayoutSetupPage.tsx`. Three address inputs, the existing
`XtrmAccountStatus` beneath them, and a submit that re-reads the profile afterwards so the status reflects
what provisioning actually did rather than what was requested.

```tsx
import { useState } from "react";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Loader2, Save } from "lucide-react";
import { XtrmAccountStatus } from "@/components/settings/XtrmAccountStatus";
import {
  useCompanyAdminProfile,
  useCompleteCompanyAdminProfile,
} from "@/hooks/useCompanyAdminProfile";

const FIELDS = [
  { key: "adminCity", label: "City" },
  { key: "adminRegion", label: "State / Region" },
  { key: "adminPostalCode", label: "Postal Code" },
] as const;

type FieldKey = (typeof FIELDS)[number]["key"];

/**
 * A company admin finishing their own payout setup.
 *
 * Address only: name, email and mobile came from the login their client admin created. The email is shown
 * but never editable — it has already been spent at the payment provider, which will not reuse it.
 */
export default function CompanyPayoutSetupPage() {
  const { data: profile, isLoading } = useCompanyAdminProfile();
  const complete = useCompleteCompanyAdminProfile();
  const [values, setValues] = useState<Record<FieldKey, string>>({
    adminCity: "",
    adminRegion: "",
    adminPostalCode: "",
  });

  if (isLoading) {
    return <Loader2 className="h-5 w-5 animate-spin text-muted-foreground" />;
  }

  const valueFor = (k: FieldKey) => values[k] || (profile?.[k] ?? "");
  const missing = FIELDS.filter((f) => !valueFor(f.key).trim());

  const onSubmit = async () => {
    if (missing.length > 0) {
      toast.error(`Still needed: ${missing.map((f) => f.label).join(", ")}`);
      return;
    }
    try {
      await complete.mutateAsync({
        adminCity: valueFor("adminCity"),
        adminRegion: valueFor("adminRegion"),
        adminPostalCode: valueFor("adminPostalCode"),
      });
      toast.success("Payout setup submitted");
    } catch {
      toast.error("Could not complete payout setup");
    }
  };

  return (
    <Card>
      <CardContent className="space-y-6 pt-6">
        <div className="space-y-1">
          <h3 className="text-sm font-medium">Payout setup for {profile?.companyName}</h3>
          <p className="text-xs text-muted-foreground">
            Signed in as {profile?.adminEmail}. Add your address to finish setting up your company&apos;s
            payout account.
          </p>
        </div>

        <div className="grid grid-cols-2 gap-3">
          {FIELDS.map((f) => (
            <div className="space-y-2" key={f.key}>
              <Label htmlFor={f.key}>{f.label}</Label>
              <Input
                id={f.key}
                value={valueFor(f.key)}
                disabled={complete.isPending}
                onChange={(e) =>
                  setValues((prev) => ({ ...prev, [f.key]: e.target.value }))
                }
              />
            </div>
          ))}
        </div>

        <Button onClick={onSubmit} disabled={complete.isPending} className="gap-2">
          {complete.isPending ? (
            <Loader2 className="h-4 w-4 animate-spin" />
          ) : (
            <Save className="h-4 w-4" />
          )}
          Finish setup
        </Button>

        <XtrmAccountStatus
          account={profile?.xtrmAccount}
          isConnecting={complete.isPending}
          onConnect={onSubmit}
        />
      </CardContent>
    </Card>
  );
}
```

- [ ] **Step 5: Write its test**

Create `src/pages/distribution/__tests__/CompanyPayoutSetupPage.test.tsx`:

```tsx
import { describe, expect, it, vi, beforeEach } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import CompanyPayoutSetupPage from "../CompanyPayoutSetupPage";

const mutateAsync = vi.fn().mockResolvedValue({});
let profile: Record<string, unknown> = {};

vi.mock("@/hooks/useCompanyAdminProfile", () => ({
  useCompanyAdminProfile: () => ({ data: profile, isLoading: false }),
  useCompleteCompanyAdminProfile: () => ({ mutateAsync, isPending: false }),
}));
vi.mock("sonner", () => ({ toast: { success: vi.fn(), error: vi.fn() } }));

/**
 * The admin supplies only their address; identity came from the login their client admin created. The
 * email is shown, never editable — it is already spent at the provider, which refuses to reuse it.
 */
describe("CompanyPayoutSetupPage", () => {
  beforeEach(() => {
    mutateAsync.mockClear();
    profile = {
      companyName: "Acme Corp",
      adminEmail: "admin@acme.test",
      adminCity: "",
      adminRegion: "",
      adminPostalCode: "",
      complete: false,
      xtrmAccount: undefined,
    };
  });

  it("submits the three address fields", async () => {
    render(<CompanyPayoutSetupPage />);

    fireEvent.change(screen.getByLabelText(/^city$/i), { target: { value: "San Francisco" } });
    fireEvent.change(screen.getByLabelText(/state \/ region/i), { target: { value: "CA" } });
    fireEvent.change(screen.getByLabelText(/postal code/i), { target: { value: "94105" } });
    fireEvent.click(screen.getByRole("button", { name: /finish setup/i }));

    await waitFor(() => expect(mutateAsync).toHaveBeenCalled());
    expect(mutateAsync.mock.calls[0][0]).toEqual({
      adminCity: "San Francisco",
      adminRegion: "CA",
      adminPostalCode: "94105",
    });
  });

  it("does not submit an incomplete address", async () => {
    render(<CompanyPayoutSetupPage />);

    fireEvent.change(screen.getByLabelText(/^city$/i), { target: { value: "San Francisco" } });
    fireEvent.click(screen.getByRole("button", { name: /finish setup/i }));

    await waitFor(() => expect(mutateAsync).not.toHaveBeenCalled());
  });

  it("shows the admin email without offering to change it", () => {
    render(<CompanyPayoutSetupPage />);

    // Spent once at the provider — showing it is useful, editing it would be a lie.
    expect(screen.getByText(/admin@acme.test/)).toBeInTheDocument();
    expect(screen.queryByLabelText(/admin email/i)).toBeNull();
  });

  it("shows the payout status once connected", () => {
    profile = {
      ...profile,
      complete: true,
      xtrmAccount: { status: "CONNECTED", accountNumber: "SPN26241004" },
    };

    render(<CompanyPayoutSetupPage />);

    expect(screen.getByText(/SPN26241004/)).toBeInTheDocument();
  });

  it("pre-fills an address already on file", () => {
    profile = { ...profile, adminCity: "San Francisco" };

    render(<CompanyPayoutSetupPage />);

    expect(screen.getByLabelText(/^city$/i)).toHaveValue("San Francisco");
  });
});
```

- [ ] **Step 6: Typecheck, test, commit**

Run: `npx tsc --noEmit` then `npx vitest run src/components/settings src/pages/distribution`

```bash
git add src/components/settings/PartnerCompanyAdminFields.tsx \
        src/components/settings/__tests__/PartnerCompanyAdminFields.test.tsx \
        src/types/partner-company.types.ts \
        src/services/company-admin-profile.service.ts \
        src/hooks/useCompanyAdminProfile.ts \
        src/pages/distribution/CompanyPayoutSetupPage.tsx \
        src/pages/distribution/__tests__/CompanyPayoutSetupPage.test.tsx
git commit -m "feat(settings): company admin completes their own payout setup"
```

---

## After the plan

**Update the design doc.** D-1 is reversed by D-16; §4's provisioning sequence now starts at profile completion, not company creation. Leaving the doc claiming the admin is "not a user account" would mislead the next reader more than having no doc at all.

**Update the contract.** `endpoints/partner-companies.yaml` documents eight admin properties on create; it is now five, plus a new `company-admin/profile` endpoint group.

**A company can now sit unprovisioned indefinitely** if its admin never signs in — and its sellers cannot enrol until it does, because enrollment waits for `CONNECTED`. The client-admin `connect` endpoint remains as the fallback for exactly that.

**For testing:** the admin's onboarding email fails silently in local, which is expected. Set the password directly in the database, as agreed.

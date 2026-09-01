# Company Beneficiary Provisioning Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Give every partner company its own XTRM identity and credentials at creation time, and make distribution payouts leave that company's wallet instead of the platform's.

**Architecture:** A `PENDING` claim row is inserted in the same transaction as the company, so a unique constraint — not the vendor — settles concurrent provisioning attempts. After commit, three XTRM calls run: `CreateBeneficiary` (which returns the SPN *and* the pseudo credentials), a token fetch that proves those credentials work, and a wallet lookup. Credentials are persisted the instant they arrive, before anything else is attempted, because XTRM returns them exactly once. Dispatch and reconciliation then resolve the remitter through one shared method so they cannot disagree about who paid.

**Tech Stack:** Java 21, Spring Boot, PostgreSQL, Flyway, JUnit 5 + Mockito + AssertJ. Frontend: React 18, TypeScript, Vite, Vitest, shadcn/ui.

**Spec:** [`2026-08-24-company-beneficiary-provisioning-design.md`](2026-08-24-company-beneficiary-provisioning-design.md) — read it before Task 1. This plan argues from that design and does not restate its reasoning.

## Global Constraints

- **Worktrees.** Backend: `../tenxengage-backend-company-distribution`. Frontend: `../tenxengage-frontend-company-distribution`. Both are on `features/company-distribution-store`. Do not work in `../tenxengage-backend` or `../tenxengage-frontend` — a failed checkout there silently fakes success.
- **Never `git add -A`.** `autocrlf=true` with no `.gitattributes` makes ~1050 files look modified when ~32 are. Stage by explicit path, every time.
- **Secrets never appear in logs, exception messages, API responses, or test fixtures that get printed.** `XtrmCredentials.toString()` already redacts; do not add a getter, a log line, or a DTO field that undoes that.
- **Backend tests:** `./gradlew test --tests "com.tenxengage.app.<...>"`. The `test` task is green at ~1429 and must stay green.
- **`integrationTest` is a different task against the LIVE dev database** (`localhost:5432/tenxengage`, Flyway clean enabled). It has ~14 pre-existing failures. Do not run it as part of these tasks, and never reset that database.
- **Frontend tests:** `npm test` (vitest run) from the frontend worktree.
- **Unit tests build the schema from entities (`ddl-auto=create-drop`), not from migrations.** A constraint added only in SQL is invisible to `./gradlew test`. Task 2 says how to test it anyway.
- **Migration version is V58.** Confirm nothing else has claimed it before writing: `ls src/main/resources/db/migration/ | tail -5`.
- Decisions D-1 through D-13 in §0 of the spec are binding. Where this plan and the spec disagree, the spec wins — stop and report the contradiction.

---

## File Structure

**Backend — created**

| File | Responsibility |
|---|---|
| `db/migration/V58__company_admin_and_xtrm_account_amendments.sql` | Company admin columns; relax three `partner_company_xtrm_accounts` columns; replace the payable constraint |
| `service/xtrm/XtrmCompanyProvisioningService.java` | Owns the claim row and the three-call provisioning sequence. The only class that writes credentials. |
| `service/xtrm/XtrmRemitterResolver.java` | The single answer to "who pays for this redemption?" Used by dispatch **and** reconciliation. |
| `dto/request/ConnectXtrmAccountRequest.java` | Body for the connect endpoint. Every field optional. |
| `dto/response/PartnerCompanyXtrmAccountResponse.java` | The `xtrmAccount` block. Carries no credentials. |

**Backend — modified**

| File | Change |
|---|---|
| `service/ConnectorEncryptionService.java` | Fail startup on a missing key outside local |
| `entity/PartnerCompany.java` | Eight admin columns |
| `entity/PartnerCompanyXtrmAccount.java` | `accountIdentityLevel`, `xtrmBeneficiaryName`; `isPayoutReady` also requires a wallet |
| `dto/request/CreatePartnerCompanyRequest.java`, `UpdatePartnerCompanyRequest.java` | Eight admin fields |
| `dto/response/PartnerCompanyResponse.java` | Nested `xtrmAccount` |
| `service/PartnerCompanyService.java` | Admin mapping; claim row; after-commit provisioning; delete removes the XTRM row |
| `controller/PartnerCompanyController.java` | `POST /{id}/xtrm/connect` |
| `service/xtrm/XtrmApiClient.java` + `XtrmApiClientImpl.java` + `XtrmApiClientStub.java` | `createBeneficiary`; credentials overloads for `getTransactionDetails` and `getBatchStatus` |
| `service/XtrmVendorService.java` | Resolve the remitter instead of defaulting to platform |
| `service/RedemptionReconciliationService.java` | Same resolution as dispatch |
| `service/DistributionRecipientService.java` | One new ineligibility reason |

**Frontend — created**

| File | Responsibility |
|---|---|
| `components/settings/PartnerCompanyFormDialog.tsx` | The add/edit company form, extracted from `UserSettingsPage.tsx` |
| `components/settings/XtrmAccountStatus.tsx` | Status badge + Connect action |

**Frontend — modified**

`pages/client-admin/UserSettingsPage.tsx`, `types/partner-company.types.ts`, `services/partner-company.service.ts`, `hooks/usePartnerCompanyApi.ts`.

**Contracts — modified** (separate repo, `../tenxengage-contracts/`)

`endpoints/partner-companies.yaml`, `models/partner-company.md`. Task 11, once the API is final and before the frontend codes against it.

---

## Task 1: Fail startup on a missing encryption key

Spec §9.2. This is a prerequisite: from Task 5 onward, this key protects the authority to move a partner company's money. Today an unset property silently falls back to a value committed to the repository.

**Files:**
- Modify: `src/main/java/com/tenxengage/app/service/ConnectorEncryptionService.java:31-42`
- Test: `src/test/java/com/tenxengage/app/service/ConnectorEncryptionServiceKeyTest.java`

**Interfaces:**
- Consumes: nothing.
- Produces: `ConnectorEncryptionService(String encryptionKey, Environment environment)` — constructor gains a second parameter. Existing tests that construct it directly must pass a mock `Environment`.

- [ ] **Step 1: Read the current constructor**

Run: `sed -n '25,45p' src/main/java/com/tenxengage/app/service/ConnectorEncryptionService.java`

Note the existing second constructor parameter (there is already one after `encryptionKey`) so you extend rather than replace it.

- [ ] **Step 2: Write the failing test**

Create `src/test/java/com/tenxengage/app/service/ConnectorEncryptionServiceKeyTest.java`:

```java
package com.tenxengage.app.service;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The key protects the authority to move a partner company's money. An unset property used to fall back to
 * a value committed to this repository, which is indistinguishable from no encryption at all for anyone who
 * can read the source.
 */
class ConnectorEncryptionServiceKeyTest {

    private static final String DEV_DEFAULT = "0123456789abcdef0123456789abcdef";
    private static final String REAL_KEY = "abcdefghijklmnopqrstuvwxyz012345";

    @Test
    void refusesToStartOnTheDevDefaultOutsideLocal() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("prod");

        assertThatThrownBy(() -> new ConnectorEncryptionService(DEV_DEFAULT, env))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("app.connector.encryption-key");
    }

    @Test
    void refusesToStartOnABlankKeyOutsideLocal() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("prod");

        assertThatThrownBy(() -> new ConnectorEncryptionService("", env))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void allowsTheDevDefaultInLocal() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("local");

        assertThatCode(() -> new ConnectorEncryptionService(DEV_DEFAULT, env)).doesNotThrowAnyException();
    }

    @Test
    void allowsARealKeyAnywhere() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("prod");

        assertThatCode(() -> new ConnectorEncryptionService(REAL_KEY, env)).doesNotThrowAnyException();
    }
}
```

- [ ] **Step 3: Run it and confirm it fails**

Run: `./gradlew test --tests "com.tenxengage.app.service.ConnectorEncryptionServiceKeyTest"`
Expected: FAIL — compilation error, the constructor does not take an `Environment`.

- [ ] **Step 4: Implement**

In `ConnectorEncryptionService`, add the guard. Keep the existing `@Value` default so `local` and tests keep working, and reject it everywhere else:

```java
private static final String DEV_DEFAULT_KEY = "0123456789abcdef0123456789abcdef";
private static final Set<String> KEYLESS_PROFILES = Set.of("local", "localtest", "test");

public ConnectorEncryptionService(
        @Value("${app.connector.encryption-key:0123456789abcdef0123456789abcdef}") String encryptionKey,
        Environment environment) {

    boolean keylessProfile = Arrays.stream(environment.getActiveProfiles()).anyMatch(KEYLESS_PROFILES::contains);
    if (!keylessProfile && (encryptionKey == null || encryptionKey.isBlank() || DEV_DEFAULT_KEY.equals(encryptionKey))) {
        // Not a warning. A default key is a key everyone with repository access already has.
        throw new IllegalStateException(
                "app.connector.encryption-key must be set to a real 16, 24, or 32 byte key outside local. "
                        + "Refusing to start with the development default.");
    }
    // ... existing key-length handling unchanged ...
}
```

Add imports: `java.util.Arrays`, `java.util.Set`, `org.springframework.core.env.Environment`.

- [ ] **Step 5: Run the test**

Run: `./gradlew test --tests "com.tenxengage.app.service.ConnectorEncryptionServiceKeyTest"`
Expected: PASS

- [ ] **Step 6: Fix every other construction site**

Run: `grep -rn "new ConnectorEncryptionService(" src/ --include=*.java`

Every hit that is not the test above needs a second argument. In tests use `new MockEnvironment()` with no active profiles set — an empty profile array is not in `KEYLESS_PROFILES`, so pass `REAL_KEY` there, or set the `test` profile explicitly. Prefer setting the profile: it documents intent.

- [ ] **Step 7: Run the full suite**

Run: `./gradlew test`
Expected: PASS, ~1429 tests. If the count dropped, something failed to compile — read the output, do not proceed.

- [ ] **Step 8: Commit**

```bash
git add src/main/java/com/tenxengage/app/service/ConnectorEncryptionService.java \
        src/test/java/com/tenxengage/app/service/ConnectorEncryptionServiceKeyTest.java
git commit -m "fix(security): refuse to start on the default connector encryption key"
```

Stage any other test files you touched in Step 6 by explicit path in the same commit.

---

## Task 2: V58 migration and entity fields

Spec §5.1, §5.2. Note the ordering trap: unit tests build the schema from **entities**, so the entity changes are what `./gradlew test` sees. The SQL is what production sees. Both must say the same thing.

**Files:**
- Create: `src/main/resources/db/migration/V58__company_admin_and_xtrm_account_amendments.sql`
- Modify: `src/main/java/com/tenxengage/app/entity/PartnerCompany.java`
- Modify: `src/main/java/com/tenxengage/app/entity/PartnerCompanyXtrmAccount.java`
- Test: `src/test/java/com/tenxengage/app/entity/PartnerCompanyXtrmAccountTest.java`

**Interfaces:**
- Consumes: nothing.
- Produces:
  - `PartnerCompany` getters/setters for `adminFirstName`, `adminLastName`, `adminEmail`, `adminMobileNumber`, `adminCity`, `adminRegion`, `adminPostalCode`, `adminCountryIso2` (all `String`).
  - `PartnerCompanyXtrmAccount` getters/setters for `accountIdentityLevel`, `xtrmBeneficiaryName` (both `String`).
  - `PartnerCompanyXtrmAccount.isPayoutReady()` — unchanged signature, stricter behaviour: now also requires a non-blank `xtrmWalletId` and `xtrmAccountNumber`.

- [ ] **Step 1: Confirm V58 is free**

Run: `ls src/main/resources/db/migration/ | tail -5`
Expected: the highest existing version is V57. If something already claims V58, use the next free number and say so in your commit message.

- [ ] **Step 2: Write the failing test**

Create `src/test/java/com/tenxengage/app/entity/PartnerCompanyXtrmAccountTest.java`:

```java
package com.tenxengage.app.entity;

import com.tenxengage.app.entity.enums.XtrmAccountStatus;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * isPayoutReady is the last gate before a company's money moves, and V58 makes three of the columns it
 * depends on nullable so a PENDING row can record partial progress. Nullable columns plus an unchanged
 * readiness check would mean a CONNECTED row with no wallet reads as payable and fails at dispatch —
 * after funds are reserved.
 */
class PartnerCompanyXtrmAccountTest {

    private PartnerCompanyXtrmAccount.PartnerCompanyXtrmAccountBuilder connected() {
        return PartnerCompanyXtrmAccount.builder()
                .clientId(UUID.randomUUID())
                .partnerCompanyId(UUID.randomUUID())
                .status(XtrmAccountStatus.CONNECTED)
                .xtrmAccountNumber("SPN26241004")
                .xtrmWalletId("206415")
                .encryptedCredentials("blob");
    }

    @Test
    void isPayoutReadyWhenEverythingIsPresent() {
        assertThat(connected().build().isPayoutReady()).isTrue();
    }

    @Test
    void isNotPayoutReadyWithoutAWallet() {
        assertThat(connected().xtrmWalletId(null).build().isPayoutReady()).isFalse();
    }

    @Test
    void isNotPayoutReadyWithoutAnAccountNumber() {
        assertThat(connected().xtrmAccountNumber(null).build().isPayoutReady()).isFalse();
    }

    @Test
    void isNotPayoutReadyWithoutCredentials() {
        assertThat(connected().encryptedCredentials(null).build().isPayoutReady()).isFalse();
    }

    @Test
    void isNotPayoutReadyWhilePending() {
        assertThat(connected().status(XtrmAccountStatus.PENDING).build().isPayoutReady()).isFalse();
    }

    @Test
    void aClaimRowIsValidAndNotPayable() {
        PartnerCompanyXtrmAccount claim = PartnerCompanyXtrmAccount.builder()
                .clientId(UUID.randomUUID())
                .partnerCompanyId(UUID.randomUUID())
                .status(XtrmAccountStatus.PENDING)
                .build();

        assertThat(claim.getXtrmAccountNumber()).isNull();
        assertThat(claim.isPayoutReady()).isFalse();
    }
}
```

- [ ] **Step 3: Run it and confirm it fails**

Run: `./gradlew test --tests "com.tenxengage.app.entity.PartnerCompanyXtrmAccountTest"`
Expected: FAIL — `isNotPayoutReadyWithoutAWallet` and `isNotPayoutReadyWithoutAnAccountNumber` fail, because `isPayoutReady` currently checks only status and credentials. `aClaimRowIsValidAndNotPayable` may fail on a `nullable = false` entity constraint.

- [ ] **Step 4: Update `PartnerCompanyXtrmAccount`**

Relax the two identifier columns and add the two new fields:

```java
/** XTRM's SPN account number for this company. An identifier, not a secret. Null on a claim row. */
@Column(name = "xtrm_account_number", length = 50)
private String xtrmAccountNumber;

/** The company's XTRM wallet that payouts draw from. An identifier, not a secret. Null until discovered. */
@Column(name = "xtrm_wallet_id", length = 50)
private String xtrmWalletId;

/** XTRM's KYC tier for this account, e.g. "Basic". Stored for the D-5 gate and for support. */
@Column(name = "account_identity_level", length = 30)
private String accountIdentityLevel;

/**
 * The name we actually sent as {@code BeneficiaryCompanyName}.
 *
 * <p>Not necessarily the company's name: our names are unique per tenant and XTRM's namespace may be
 * global, so the name is disambiguated before sending. Without this column nobody can match our row
 * against XTRM's portal.</p>
 */
@Column(name = "xtrm_beneficiary_name", length = 255)
private String xtrmBeneficiaryName;
```

Then tighten readiness:

```java
/** True when this company may actually pay from its own wallet. */
public boolean isPayoutReady() {
    return status == XtrmAccountStatus.CONNECTED
            && notBlank(encryptedCredentials)
            && notBlank(xtrmAccountNumber)
            && notBlank(xtrmWalletId);
}

private static boolean notBlank(String s) {
    return s != null && !s.isBlank();
}
```

- [ ] **Step 5: Run the test**

Run: `./gradlew test --tests "com.tenxengage.app.entity.PartnerCompanyXtrmAccountTest"`
Expected: PASS

- [ ] **Step 6: Add the admin columns to `PartnerCompany`**

```java
// --- Default company admin (D-1) ---
// Contact details, not a user account. These are the input to XTRM's BeneficiaryCompanyAdminDetails.
// Columns rather than metadata JSONB on purpose: a typo in admin_country_iso2 fails a payout, and the
// schema should be able to say so — which it cannot for partnerType or contactEmail.

@Column(name = "admin_first_name", length = 100)
private String adminFirstName;

@Column(name = "admin_last_name", length = 100)
private String adminLastName;

@Column(name = "admin_email", length = 255)
private String adminEmail;

@Column(name = "admin_mobile_number", length = 20)
private String adminMobileNumber;

@Column(name = "admin_city", length = 100)
private String adminCity;

@Column(name = "admin_region", length = 100)
private String adminRegion;

@Column(name = "admin_postal_code", length = 20)
private String adminPostalCode;

@Column(name = "admin_country_iso2", length = 2)
private String adminCountryIso2;

/** True when every admin field is present — the all-or-nothing group XTRM needs. */
public boolean hasCompleteAdminDetails() {
    return notBlank(adminFirstName) && notBlank(adminLastName) && notBlank(adminEmail)
            && notBlank(adminMobileNumber) && notBlank(adminCity) && notBlank(adminRegion)
            && notBlank(adminPostalCode) && notBlank(adminCountryIso2);
}

private static boolean notBlank(String s) {
    return s != null && !s.isBlank();
}
```

- [ ] **Step 7: Write the migration**

Create `src/main/resources/db/migration/V58__company_admin_and_xtrm_account_amendments.sql`:

```sql
-- Company admin details + the schema changes that let a PENDING XTRM row hold partial progress.
--
-- WHY THE COLUMNS RELAX
--
-- Beneficiary/CreateBeneficiary returns the account number AND the pseudo credentials, but no wallet id,
-- and it returns the secret exactly once — it cannot be replayed for the same company. So provisioning
-- persists what it has the moment it has it, and enriches afterwards. Three columns therefore have to be
-- nullable: a claim row knows nothing yet, and a post-CreateBeneficiary row knows everything but the wallet.
--
-- The CONNECTED-means-payable invariant is NOT weakened. It moves from the old constraint to a wider one
-- below, because a CONNECTED row missing an account number or a wallet is exactly as unpayable as one
-- missing credentials, and would fail just as late — after money is reserved.

-- 1. Default company admin. Nullable: every existing company has none, and a company can legitimately
--    exist with no payout intent.
ALTER TABLE partner_companies
    ADD COLUMN admin_first_name    VARCHAR(100),
    ADD COLUMN admin_last_name     VARCHAR(100),
    ADD COLUMN admin_email         VARCHAR(255),
    ADD COLUMN admin_mobile_number VARCHAR(20),
    ADD COLUMN admin_city          VARCHAR(100),
    ADD COLUMN admin_region        VARCHAR(100),
    ADD COLUMN admin_postal_code   VARCHAR(20),
    -- VARCHAR(2), not CHAR(2). Every other ISO2 column here is VARCHAR (V34, V35, V38), and Hibernate
    -- generates VARCHAR for @Column(length = 2) — so CHAR would make the unit-test schema and the
    -- production schema differ in type, which is precisely the trap this plan warns about.
    ADD COLUMN admin_country_iso2  VARCHAR(2);

COMMENT ON COLUMN partner_companies.admin_email IS
    'Default company admin. Contact details for XTRM BeneficiaryCompanyAdminDetails, not a platform user.';

-- 2. Let a PENDING row exist before anything is known.
ALTER TABLE partner_company_xtrm_accounts
    ALTER COLUMN xtrm_account_number DROP NOT NULL,
    ALTER COLUMN xtrm_wallet_id      DROP NOT NULL;

-- 3. What CreateBeneficiary tells us, beyond identity and credentials.
ALTER TABLE partner_company_xtrm_accounts
    ADD COLUMN account_identity_level VARCHAR(30),
    ADD COLUMN xtrm_beneficiary_name  VARCHAR(255);

COMMENT ON COLUMN partner_company_xtrm_accounts.xtrm_beneficiary_name IS
    'The name actually sent as BeneficiaryCompanyName — disambiguated per tenant, so not always the '
    'company name. Without it our row cannot be matched to XTRM''s portal.';

-- 4. Replace, do not redefine. The old name no longer describes what is checked, and a schema reader
--    should see that the rule changed rather than find the same name meaning something new.
ALTER TABLE partner_company_xtrm_accounts
    DROP CONSTRAINT chk_xtrm_account_connected_has_credentials;

ALTER TABLE partner_company_xtrm_accounts
    ADD CONSTRAINT chk_xtrm_account_connected_is_payable CHECK (
        status <> 'CONNECTED'
        OR (encrypted_credentials IS NOT NULL
            AND xtrm_account_number IS NOT NULL
            AND xtrm_wallet_id      IS NOT NULL)
    );
```

- [ ] **Step 8: Run the full suite**

Run: `./gradlew test`
Expected: PASS. Unit tests never execute the SQL — they build the schema from the entities you changed in Steps 4 and 6. That is why Step 7's SQL must mirror them exactly; re-read both and confirm the column names and lengths match.

- [ ] **Step 9: Commit**

```bash
git add src/main/resources/db/migration/V58__company_admin_and_xtrm_account_amendments.sql \
        src/main/java/com/tenxengage/app/entity/PartnerCompany.java \
        src/main/java/com/tenxengage/app/entity/PartnerCompanyXtrmAccount.java \
        src/test/java/com/tenxengage/app/entity/PartnerCompanyXtrmAccountTest.java
git commit -m "feat(partner-company): company admin columns, and a PENDING XTRM row that can hold partial progress"
```

---

## Task 3: Company admin on the API

Spec §5.1, §6. Deliverable: a company can be created and read back with admin details, validated as an all-or-nothing group, with an unsupported country refused at the boundary.

**Files:**
- Modify: `src/main/java/com/tenxengage/app/dto/request/CreatePartnerCompanyRequest.java`
- Modify: `src/main/java/com/tenxengage/app/dto/request/UpdatePartnerCompanyRequest.java`
- Modify: `src/main/java/com/tenxengage/app/service/PartnerCompanyService.java:102-140`
- Test: `src/test/java/com/tenxengage/app/service/PartnerCompanyAdminDetailsTest.java`

**Interfaces:**
- Consumes: `PartnerCompany` admin setters and `hasCompleteAdminDetails()` from Task 2.
- Produces:
  - `CreatePartnerCompanyRequest` gains, in order after `contactPhone`: `String adminFirstName, String adminLastName, String adminEmail, String adminMobileNumber, String adminCity, String adminRegion, String adminPostalCode, String adminCountryIso2`. `UpdatePartnerCompanyRequest` gains the same eight.
  - `PartnerCompanyService.validateAdminDetails(CreatePartnerCompanyRequest request)` — package-private, throws `BusinessRuleException("INVALID_ADMIN_DETAILS", ...)`.

- [ ] **Step 1: Write the failing test**

Create `src/test/java/com/tenxengage/app/service/PartnerCompanyAdminDetailsTest.java`:

```java
package com.tenxengage.app.service;

import com.tenxengage.app.dto.request.CreatePartnerCompanyRequest;
import com.tenxengage.app.entity.enums.PartnerCompanyStatus;
import com.tenxengage.app.exception.BusinessRuleException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The admin block is all-or-nothing because a half-filled one is guaranteed to fail at XTRM. Failing it
 * here names the missing field; failing it at the vendor produces an opaque rejection minutes later, on a
 * background thread, where nobody is looking.
 */
class PartnerCompanyAdminDetailsTest {

    private CreatePartnerCompanyRequest request(String firstName, String countryIso2) {
        return new CreatePartnerCompanyRequest(
                "Acme Corp", "EXT-1", List.of(UUID.randomUUID()), "RESELLER",
                PartnerCompanyStatus.ACTIVE, "https://acme.test", "contact@acme.test", "1234567890", "{}",
                firstName, "Singh", "admin@acme.test", "4085556245",
                "San Francisco", "CA", "94105", countryIso2);
    }

    // The constructor is untouched by this task — Task 6 adds the provisioning collaborators, when the
    // classes they refer to exist.
    private final PartnerCompanyService service =
            new PartnerCompanyService(null, null, null, null, null);

    @Test
    void acceptsACompleteAdminBlock() {
        assertThatCode(() -> service.validateAdminDetails(request("TestP", "US")))
                .doesNotThrowAnyException();
    }

    @Test
    void acceptsNoAdminBlockAtAll() {
        CreatePartnerCompanyRequest none = new CreatePartnerCompanyRequest(
                "Acme Corp", "EXT-1", List.of(UUID.randomUUID()), "RESELLER",
                PartnerCompanyStatus.ACTIVE, null, null, null, "{}",
                null, null, null, null, null, null, null, null);

        assertThatCode(() -> service.validateAdminDetails(none)).doesNotThrowAnyException();
    }

    @Test
    void refusesAHalfFilledAdminBlockAndNamesTheMissingField() {
        assertThatThrownBy(() -> service.validateAdminDetails(request(null, "US")))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("adminFirstName");
    }

    @Test
    void refusesACountryXtrmDoesNotSupport() {
        assertThatThrownBy(() -> service.validateAdminDetails(request("TestP", "ZZ")))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("ZZ");
    }
}
```

- [ ] **Step 2: Run it and confirm it fails**

Run: `./gradlew test --tests "com.tenxengage.app.service.PartnerCompanyAdminDetailsTest"`
Expected: FAIL — compilation error; the request record has nine components, not seventeen.

- [ ] **Step 3: Extend the request records**

`CreatePartnerCompanyRequest` — append after `metadata`:

```java
    String metadata,

    // --- Default company admin (D-1). All eight or none; enforced as a group in the service, because
    // bean validation cannot express "all present or all absent" without a custom annotation.
    @Size(max = 100) String adminFirstName,
    @Size(max = 100) String adminLastName,
    @Email(message = "Admin email must be valid") @Size(max = 255) String adminEmail,
    @Size(max = 20) String adminMobileNumber,
    @Size(max = 100) String adminCity,
    @Size(max = 100) String adminRegion,
    @Size(max = 20) String adminPostalCode,
    @Size(min = 2, max = 2, message = "Country must be a 2-letter ISO code") String adminCountryIso2
) {}
```

Apply the identical eight components to `UpdatePartnerCompanyRequest`.

- [ ] **Step 4: Implement validation in `PartnerCompanyService`**

```java
private static final List<String> ADMIN_FIELD_NAMES = List.of(
        "adminFirstName", "adminLastName", "adminEmail", "adminMobileNumber",
        "adminCity", "adminRegion", "adminPostalCode", "adminCountryIso2");

/**
 * All eight or none. A partially-filled admin block cannot produce a beneficiary at XTRM, and the
 * rejection arrives on a background thread long after the request returned 201.
 */
void validateAdminDetails(CreatePartnerCompanyRequest r) {
    List<String> values = List.of(
            String.valueOf(r.adminFirstName()), String.valueOf(r.adminLastName()),
            String.valueOf(r.adminEmail()), String.valueOf(r.adminMobileNumber()),
            String.valueOf(r.adminCity()), String.valueOf(r.adminRegion()),
            String.valueOf(r.adminPostalCode()), String.valueOf(r.adminCountryIso2()));

    List<String> missing = new ArrayList<>();
    for (int i = 0; i < values.size(); i++) {
        String v = values.get(i);
        if (v == null || "null".equals(v) || v.isBlank()) {
            missing.add(ADMIN_FIELD_NAMES.get(i));
        }
    }

    if (missing.size() == ADMIN_FIELD_NAMES.size()) {
        return; // none supplied — legitimate, the company simply has no payout intent yet
    }
    if (!missing.isEmpty()) {
        throw new BusinessRuleException("INVALID_ADMIN_DETAILS",
                "Company admin details are incomplete. Missing: " + String.join(", ", missing));
    }
    if (!PhoneDialCodes.isSupported(r.adminCountryIso2())) {
        throw new BusinessRuleException("INVALID_ADMIN_DETAILS",
                "XTRM does not support payouts for country " + r.adminCountryIso2() + ".");
    }
}
```

Import `com.tenxengage.app.service.xtrm.PhoneDialCodes`.

- [ ] **Step 5: Call it and map the fields in `createPartnerCompany`**

Immediately after the existing `externalPartnerId` uniqueness check, add `validateAdminDetails(request);`. Then extend the builder:

```java
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
    .adminCity(request.adminCity())
    .adminRegion(request.adminRegion())
    .adminPostalCode(request.adminPostalCode())
    .adminCountryIso2(request.adminCountryIso2())
    .build();
```

In `updatePartnerCompany`, set each admin field only when the request value is non-null, matching how `website` and `contactPhone` are already handled.

- [ ] **Step 6: Run the test, then everything**

`PartnerCompanyService`'s constructor is deliberately **not** changed here. It gains its two provisioning collaborators in Task 6, once `XtrmCompanyProvisioningService` exists — adding them now would mean either referencing a class that has not been written or scaffolding an empty `@Service`, and empty placeholder classes ship.

Run: `./gradlew test --tests "com.tenxengage.app.service.PartnerCompanyAdminDetailsTest"`
Expected: PASS

Run: `./gradlew test`
Expected: PASS

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/tenxengage/app/dto/request/CreatePartnerCompanyRequest.java \
        src/main/java/com/tenxengage/app/dto/request/UpdatePartnerCompanyRequest.java \
        src/main/java/com/tenxengage/app/service/PartnerCompanyService.java \
        src/test/java/com/tenxengage/app/service/PartnerCompanyAdminDetailsTest.java
git commit -m "feat(partner-company): capture the default company admin, validated as a group"
```

---

## Task 4: `createBeneficiary` on the XTRM client

Spec §3, §4 step 1. The response shape is confirmed against the sandbox; the request envelope follows the `Request` (capital R) convention the working curl used.

**Files:**
- Modify: `src/main/java/com/tenxengage/app/service/xtrm/XtrmApiClient.java`
- Modify: `src/main/java/com/tenxengage/app/service/xtrm/XtrmApiClientImpl.java`
- Modify: `src/main/java/com/tenxengage/app/service/xtrm/XtrmApiClientStub.java`
- Test: `src/test/java/com/tenxengage/app/service/xtrm/XtrmApiClientImplCreateBeneficiaryTest.java`

**Interfaces:**
- Consumes: `PhoneDialCodes.mobilePhone(String iso2, String nationalNumber)`.
- Produces:

```java
CreateBeneficiaryResult createBeneficiary(CreateBeneficiaryCommand command);

record CreateBeneficiaryCommand(
        String companyName, String webAddress,
        String adminFirstName, String adminLastName, String adminEmail,
        String adminMobileNumber, String adminCountryIso2,
        String adminCity, String adminRegion, String adminPostalCode,
        boolean emailNotification) {}

record CreateBeneficiaryResult(
        boolean success, String beneficiaryAccountNumber,
        String clientId, String clientSecret, String accountIdentityLevel,
        List<String> errors, boolean retryable) {
    static CreateBeneficiaryResult ok(String beneficiaryAccountNumber, String clientId,
                                      String clientSecret, String accountIdentityLevel);
    static CreateBeneficiaryResult failed(List<String> errors, boolean retryable);
}
```

- [ ] **Step 1: Write the failing test**

Create `src/test/java/com/tenxengage/app/service/xtrm/XtrmApiClientImplCreateBeneficiaryTest.java`. Follow the parsing-only style of `XtrmApiClientImplBatchStatusTest` — read it first (`sed -n '1,40p' src/test/java/com/tenxengage/app/service/xtrm/XtrmApiClientImplBatchStatusTest.java`) and mirror how it feeds a canned response map into the parse path without HTTP.

```java
package com.tenxengage.app.service.xtrm;

import com.tenxengage.app.service.xtrm.XtrmApiClient.CreateBeneficiaryResult;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CreateBeneficiary is the only call that ever hands us a company's SecretKey, and it hands it over once.
 * Misparsing it is unrecoverable: the account exists at XTRM, we cannot replay the call for the same
 * company name, and there is no endpoint to read the secret back.
 */
class XtrmApiClientImplCreateBeneficiaryTest {

    private final XtrmApiClientImpl client = new XtrmApiClientImpl();

    private Map<String, Object> sandboxResponse() {
        return Map.of("CreateBeneficiaryResponse", Map.of("CreateBeneficiaryResult", Map.of(
                "BeneficiaryID", "SPN26241004",
                "AccountIdentityLevel", "Basic",
                "ClientID", "2696718_API_User",
                "SecretKey", "a-secret",
                "OperationStatus", Map.of("Success", true, "Errors", List.of()))));
    }

    @Test
    void parsesTheSandboxResponse() {
        CreateBeneficiaryResult result = client.parseCreateBeneficiary(sandboxResponse());

        assertThat(result.success()).isTrue();
        assertThat(result.beneficiaryAccountNumber()).isEqualTo("SPN26241004");
        assertThat(result.clientId()).isEqualTo("2696718_API_User");
        assertThat(result.clientSecret()).isEqualTo("a-secret");
        assertThat(result.accountIdentityLevel()).isEqualTo("Basic");
    }

    @Test
    void failsWhenTheSecretIsMissingRatherThanReportingSuccess() {
        Map<String, Object> noSecret = Map.of("CreateBeneficiaryResponse", Map.of("CreateBeneficiaryResult", Map.of(
                "BeneficiaryID", "SPN26241004",
                "ClientID", "2696718_API_User",
                "OperationStatus", Map.of("Success", true, "Errors", List.of()))));

        CreateBeneficiaryResult result = client.parseCreateBeneficiary(noSecret);

        // An account exists at XTRM that we can never authenticate as. Reporting success would store a
        // CONNECTED-looking row that can never pay, and the only fix would be a support ticket.
        assertThat(result.success()).isFalse();
        assertThat(result.retryable()).isFalse();
    }

    @Test
    void reportsAVendorRejection() {
        Map<String, Object> rejected = Map.of("CreateBeneficiaryResponse", Map.of("CreateBeneficiaryResult", Map.of(
                "OperationStatus", Map.of("Success", false, "Errors", List.of("Company name already exists")))));

        CreateBeneficiaryResult result = client.parseCreateBeneficiary(rejected);

        assertThat(result.success()).isFalse();
        assertThat(result.errors()).contains("Company name already exists");
    }

    @Test
    void reportsAnUnrecognizedResponseAsRetryable() {
        CreateBeneficiaryResult result = client.parseCreateBeneficiary(Map.of("Something", "else"));

        assertThat(result.success()).isFalse();
        assertThat(result.retryable()).isTrue();
    }
}
```

- [ ] **Step 2: Run it and confirm it fails**

Run: `./gradlew test --tests "com.tenxengage.app.service.xtrm.XtrmApiClientImplCreateBeneficiaryTest"`
Expected: FAIL — `parseCreateBeneficiary` does not exist.

- [ ] **Step 3: Add the interface members**

In `XtrmApiClient.java`, next to `createUser`:

```java
/** Create a beneficiary COMPANY ({@code Beneficiary/CreateBeneficiary}); returns its SPN + pseudo credentials. */
CreateBeneficiaryResult createBeneficiary(CreateBeneficiaryCommand command);
```

and the two records exactly as given in **Interfaces** above, with this note on the result:

```java
/**
 * Result of {@code CreateBeneficiary}.
 *
 * <p>{@code clientSecret} is a <b>secret</b> and is returned exactly once by XTRM. Do not log this record,
 * and do not add a {@code toString()} that prints it.</p>
 */
```

- [ ] **Step 4: Implement in `XtrmApiClientImpl`**

Split the call from the parse so the parse is testable without HTTP:

```java
@Override
public CreateBeneficiaryResult createBeneficiary(CreateBeneficiaryCommand cmd) {
    Map<String, Object> admin = new LinkedHashMap<>();
    admin.put("AdminEmail", cmd.adminEmail());
    admin.put("EmailNotification", String.valueOf(cmd.emailNotification()));
    admin.put("AdminFirstName", cmd.adminFirstName());
    admin.put("AdminLastName", cmd.adminLastName());
    // Same formatter CreateUser uses. A second hand-rolled phone format for the same vendor is how the
    // two drift apart.
    admin.put("AdminMobileNumber", PhoneDialCodes.mobilePhone(cmd.adminCountryIso2(), cmd.adminMobileNumber()));
    admin.put("CountryISO2", cmd.adminCountryIso2());
    admin.put("City", cmd.adminCity());
    admin.put("PostalCode", cmd.adminPostalCode());
    admin.put("Region", cmd.adminRegion());

    Map<String, Object> request = new LinkedHashMap<>();
    request.put("IssuerAccountNumber", issuerAccountNumber);
    request.put("BeneficiaryCompanyName", cmd.companyName());
    putIfPresent(request, "WebAddress", cmd.webAddress());
    request.put("BeneficiaryCompanyAdminDetails", admin);

    // Capital-R "Request" here, matching the sandbox-verified curl — not the lowercase envelope() helper.
    Map<String, Object> body = Map.of("CreateBeneficiary", Map.of("Request", request));

    log.info("[step=xtrm_create_beneficiary] calling CreateBeneficiary");
    Map<?, ?> response;
    try {
        response = post("/API/v4/Beneficiary/CreateBeneficiary", body);
    } catch (RuntimeException e) {
        log.warn("[step=xtrm_create_beneficiary_failed] transport error: {}", e.getClass().getSimpleName());
        return CreateBeneficiaryResult.failed(List.of("Could not reach XTRM"), true);
    }
    return parseCreateBeneficiary(response);
}

/** Split out so the parse — the part that can lose a one-shot secret — is testable without HTTP. */
CreateBeneficiaryResult parseCreateBeneficiary(Map<?, ?> response) {
    Map<?, ?> result = unwrap(response, "CreateBeneficiaryResponse", "CreateBeneficiaryResult");
    if (result == null) {
        log.warn("[step=xtrm_create_beneficiary_failed] Unrecognized response; top-level keys={}",
                response == null ? "null" : response.keySet());
        return CreateBeneficiaryResult.failed(List.of("Unrecognized XTRM CreateBeneficiary response"), true);
    }
    if (!isSuccess(result)) {
        return CreateBeneficiaryResult.failed(errors(result), false);
    }

    String spn = firstNonBlank(result, "BeneficiaryID", "BeneficiaryAccountNumber");
    String clientId = firstNonBlank(result, "ClientID", "ClientId");
    String secret = firstNonBlank(result, "SecretKey", "ClientSecret");
    String level = firstNonBlank(result, "AccountIdentityLevel");

    if (isBlank(spn) || isBlank(clientId) || isBlank(secret)) {
        // NOT retryable. XTRM reported success, so the account exists and the name is taken — a retry
        // would fail on the duplicate name, and the secret is gone. Surface it for support instead of
        // looping.
        log.error("[step=xtrm_create_beneficiary_incomplete] success with missing fields; spnPresent={} "
                + "clientIdPresent={} secretPresent={}", !isBlank(spn), !isBlank(clientId), !isBlank(secret));
        return CreateBeneficiaryResult.failed(
                List.of("XTRM reported success but returned incomplete credentials"), false);
    }
    return CreateBeneficiaryResult.ok(spn, clientId, secret, level);
}
```

- [ ] **Step 5: Implement in `XtrmApiClientStub`**

```java
@Override
public CreateBeneficiaryResult createBeneficiary(CreateBeneficiaryCommand cmd) {
    String spn = "SPN-STUB-" + token(cmd.adminEmail());
    log.info("[stub] XTRM CreateBeneficiary name={} -> {}", cmd.companyName(), spn);
    // Deterministic, and obviously fake — nothing here should ever authenticate against a real XTRM.
    return CreateBeneficiaryResult.ok(spn, spn + "_API_User", "stub-secret-" + spn, "Basic");
}
```

- [ ] **Step 6: Run the test, then everything**

Run: `./gradlew test --tests "com.tenxengage.app.service.xtrm.XtrmApiClientImplCreateBeneficiaryTest"`
Expected: PASS

Run: `./gradlew test`
Expected: PASS

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/tenxengage/app/service/xtrm/XtrmApiClient.java \
        src/main/java/com/tenxengage/app/service/xtrm/XtrmApiClientImpl.java \
        src/main/java/com/tenxengage/app/service/xtrm/XtrmApiClientStub.java \
        src/test/java/com/tenxengage/app/service/xtrm/XtrmApiClientImplCreateBeneficiaryTest.java
git commit -m "feat(xtrm): CreateBeneficiary — the call that returns a company's SPN and pseudo credentials"
```

---

## Task 5: `XtrmCompanyProvisioningService`

Spec §4. The most important test in this plan is Step 4's `persistsCredentialsEvenWhenWalletDiscoveryFails`.

**Files:**
- Create: `src/main/java/com/tenxengage/app/service/xtrm/XtrmCompanyProvisioningService.java`
- Test: `src/test/java/com/tenxengage/app/service/xtrm/XtrmCompanyProvisioningServiceTest.java`

**Interfaces:**
- Consumes: `XtrmApiClient.createBeneficiary`, `XtrmApiClient.getBeneficiaryWallets`, `XtrmApiClientImpl.getAccessToken(XtrmCredentials)` (via `XtrmCredentialsResolver`), `ConnectorEncryptionService`, `PartnerCompanyXtrmAccountRepository`, `PartnerCompanyRepository`.
- Produces:
  - `PartnerCompanyXtrmAccount claim(UUID clientId, UUID partnerCompanyId)` — inserts the `PENDING` row. Throws `DataIntegrityViolationException` if one already exists; callers decide what that means.
  - `void provision(UUID clientId, UUID partnerCompanyId)` — the three-call sequence. **Never throws.** Records failure on the row.
  - `String beneficiaryNameFor(PartnerCompany company, String clientName)` — the disambiguated `BeneficiaryCompanyName`.

- [ ] **Step 1: Write the failing test**

Create `src/test/java/com/tenxengage/app/service/xtrm/XtrmCompanyProvisioningServiceTest.java`:

```java
package com.tenxengage.app.service.xtrm;

import com.tenxengage.app.entity.PartnerCompany;
import com.tenxengage.app.entity.PartnerCompanyXtrmAccount;
import com.tenxengage.app.entity.enums.XtrmAccountStatus;
import com.tenxengage.app.repository.PartnerCompanyRepository;
import com.tenxengage.app.repository.PartnerCompanyXtrmAccountRepository;
import com.tenxengage.app.service.ConnectorEncryptionService;
import com.tenxengage.app.service.xtrm.XtrmApiClient.CreateBeneficiaryResult;
import com.tenxengage.app.service.xtrm.XtrmApiClient.GetWalletsResult;
import com.tenxengage.app.service.xtrm.XtrmApiClient.WalletInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Provisioning a company's XTRM identity.
 *
 * <p>The property that matters most here is not the happy path. XTRM returns the pseudo credentials exactly
 * once and CreateBeneficiary cannot be replayed for the same company name, so credentials must reach the
 * database before anything else is attempted. If they are held in memory across the wallet lookup and that
 * lookup throws, the company's ability to pay is gone permanently.</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class XtrmCompanyProvisioningServiceTest {

    @Mock private PartnerCompanyXtrmAccountRepository accountRepository;
    @Mock private PartnerCompanyRepository companyRepository;
    @Mock private XtrmApiClient xtrmApiClient;
    @Mock private XtrmCredentialsResolver credentialsResolver;
    @Mock private ConnectorEncryptionService encryptionService;
    @Mock private ClientRepository clientRepository;

    private XtrmCompanyProvisioningService service;

    private static final UUID CLIENT_ID = UUID.randomUUID();
    private static final UUID COMPANY_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new XtrmCompanyProvisioningService(accountRepository, companyRepository, xtrmApiClient,
                credentialsResolver, encryptionService, clientRepository);

        Client tenant = new Client();
        tenant.setName("Apple");
        when(clientRepository.findById(CLIENT_ID)).thenReturn(Optional.of(tenant));

        PartnerCompany company = PartnerCompany.builder()
                .name("Acme Corp").clientId(CLIENT_ID).website("https://acme.test")
                .adminFirstName("TestP").adminLastName("Singh").adminEmail("admin@acme.test")
                .adminMobileNumber("4085556245").adminCity("San Francisco").adminRegion("CA")
                .adminPostalCode("94105").adminCountryIso2("US")
                .build();
        company.setId(COMPANY_ID);
        when(companyRepository.findByIdAndClientId(COMPANY_ID, CLIENT_ID)).thenReturn(Optional.of(company));

        when(accountRepository.findByClientIdAndPartnerCompanyId(CLIENT_ID, COMPANY_ID))
                .thenReturn(Optional.of(claimRow()));
        when(accountRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(encryptionService.encrypt(any())).thenReturn("encrypted-blob");
        when(credentialsResolver.platform()).thenReturn(
                new XtrmCredentials("platform-id", "platform-secret", "SPN26237883", "203871", "2314"));
    }

    private PartnerCompanyXtrmAccount claimRow() {
        return PartnerCompanyXtrmAccount.builder()
                .clientId(CLIENT_ID).partnerCompanyId(COMPANY_ID).status(XtrmAccountStatus.PENDING).build();
    }

    private void vendorSucceeds() {
        when(xtrmApiClient.createBeneficiary(any()))
                .thenReturn(CreateBeneficiaryResult.ok("SPN26241004", "2696718_API_User", "a-secret", "Basic"));
    }

    @Test
    void reachesConnectedWhenAllThreeCallsSucceed() {
        vendorSucceeds();
        when(xtrmApiClient.getBeneficiaryWallets(any()))
                .thenReturn(GetWalletsResult.ok(List.of(new WalletInfo("206415", "Main", "USD", BigDecimal.ZERO))));

        service.provision(CLIENT_ID, COMPANY_ID);

        ArgumentCaptor<PartnerCompanyXtrmAccount> saved = ArgumentCaptor.forClass(PartnerCompanyXtrmAccount.class);
        verify(accountRepository, atLeastOnce()).save(saved.capture());
        PartnerCompanyXtrmAccount last = saved.getValue();

        assertThat(last.getStatus()).isEqualTo(XtrmAccountStatus.CONNECTED);
        assertThat(last.getXtrmAccountNumber()).isEqualTo("SPN26241004");
        assertThat(last.getXtrmWalletId()).isEqualTo("206415");
        assertThat(last.getEncryptedCredentials()).isEqualTo("encrypted-blob");
        assertThat(last.getAccountIdentityLevel()).isEqualTo("Basic");
        assertThat(last.getConnectedAt()).isNotNull();
    }

    @Test
    void persistsCredentialsEvenWhenWalletDiscoveryFails() {
        vendorSucceeds();
        when(xtrmApiClient.getBeneficiaryWallets(any()))
                .thenThrow(new RuntimeException("XTRM unreachable"));

        service.provision(CLIENT_ID, COMPANY_ID);

        ArgumentCaptor<PartnerCompanyXtrmAccount> saved = ArgumentCaptor.forClass(PartnerCompanyXtrmAccount.class);
        verify(accountRepository, atLeastOnce()).save(saved.capture());

        // The credentials must be in one of the saved states. Losing them is unrecoverable: the account
        // exists at XTRM, the name is taken, and there is no endpoint to read the secret back.
        assertThat(saved.getAllValues())
                .anyMatch(a -> "encrypted-blob".equals(a.getEncryptedCredentials())
                        && "SPN26241004".equals(a.getXtrmAccountNumber()));
        assertThat(saved.getValue().getStatus()).isEqualTo(XtrmAccountStatus.PENDING);
        assertThat(saved.getValue().getLastError()).isNotBlank();
    }

    @Test
    void recordsTheErrorAndStaysPendingWhenCreateBeneficiaryFails() {
        when(xtrmApiClient.createBeneficiary(any()))
                .thenReturn(CreateBeneficiaryResult.failed(List.of("Company name already exists"), false));

        service.provision(CLIENT_ID, COMPANY_ID);

        ArgumentCaptor<PartnerCompanyXtrmAccount> saved = ArgumentCaptor.forClass(PartnerCompanyXtrmAccount.class);
        verify(accountRepository, atLeastOnce()).save(saved.capture());

        assertThat(saved.getValue().getStatus()).isEqualTo(XtrmAccountStatus.PENDING);
        assertThat(saved.getValue().getXtrmAccountNumber()).isNull();
        assertThat(saved.getValue().getLastError()).contains("Company name already exists");
    }

    @Test
    void neverThrows() {
        when(xtrmApiClient.createBeneficiary(any())).thenThrow(new RuntimeException("boom"));

        // Provisioning runs after the company is committed. Throwing here cannot undo that commit; it can
        // only produce an unhandled error on a background thread.
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
    void doesNothingWhenAlreadyConnected() {
        PartnerCompanyXtrmAccount connected = claimRow();
        connected.setStatus(XtrmAccountStatus.CONNECTED);
        connected.setXtrmAccountNumber("SPN26241004");
        connected.setXtrmWalletId("206415");
        connected.setEncryptedCredentials("encrypted-blob");
        when(accountRepository.findByClientIdAndPartnerCompanyId(CLIENT_ID, COMPANY_ID))
                .thenReturn(Optional.of(connected));

        service.provision(CLIENT_ID, COMPANY_ID);

        // CreateBeneficiary is not replayable. Calling it for a company that already has an SPN would
        // either fail on the duplicate name or mint a second account for one company.
        verify(xtrmApiClient, never()).createBeneficiary(any());
    }

    @Test
    void disambiguatesTheBeneficiaryNameByTenant() {
        PartnerCompany company = PartnerCompany.builder().name("Acme Corp").clientId(CLIENT_ID).build();

        String name = service.beneficiaryNameFor(company, "Apple");

        assertThat(name).contains("Acme Corp").contains("Apple");
        assertThat(name.length()).isLessThanOrEqualTo(255);
    }
}
```

- [ ] **Step 2: Run it and confirm it fails**

Run: `./gradlew test --tests "com.tenxengage.app.service.xtrm.XtrmCompanyProvisioningServiceTest"`
Expected: FAIL — the class does not exist.

- [ ] **Step 3: Implement the service**

Create `src/main/java/com/tenxengage/app/service/xtrm/XtrmCompanyProvisioningService.java`:

```java
package com.tenxengage.app.service.xtrm;

import com.tenxengage.app.entity.PartnerCompany;
import com.tenxengage.app.entity.PartnerCompanyXtrmAccount;
import com.tenxengage.app.entity.enums.XtrmAccountStatus;
import com.tenxengage.app.repository.PartnerCompanyRepository;
import com.tenxengage.app.repository.PartnerCompanyXtrmAccountRepository;
import com.tenxengage.app.service.ConnectorEncryptionService;
import com.tenxengage.app.service.xtrm.XtrmApiClient.CreateBeneficiaryCommand;
import com.tenxengage.app.service.xtrm.XtrmApiClient.CreateBeneficiaryResult;
import com.tenxengage.app.service.xtrm.XtrmApiClient.GetWalletsCommand;
import com.tenxengage.app.service.xtrm.XtrmApiClient.GetWalletsResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Gives a partner company an identity at XTRM.
 *
 * <p>Three calls, and the order is the design: {@code CreateBeneficiary} returns the company's SPN
 * <em>and</em> its pseudo credentials, and returns the secret <b>exactly once</b>. The call cannot be
 * replayed for the same company — the name is taken on the second attempt — so the credentials are written
 * to the database before the token check or the wallet lookup is attempted. Losing them costs a support
 * ticket to XTRM; persisting them early costs one write.</p>
 *
 * <p>This is the only class that writes {@code encrypted_credentials}.</p>
 */
@Service
public class XtrmCompanyProvisioningService {

    private static final Logger log = LoggerFactory.getLogger(XtrmCompanyProvisioningService.class);
    private static final int ERROR_MAX = 500;
    private static final int NAME_MAX = 255;

    private final PartnerCompanyXtrmAccountRepository accountRepository;
    private final PartnerCompanyRepository companyRepository;
    private final XtrmApiClient xtrmApiClient;
    private final XtrmCredentialsResolver credentialsResolver;
    private final ConnectorEncryptionService encryptionService;
    private final ClientRepository clientRepository;

    /**
     * Whether XTRM emails the company admin on creation.
     *
     * <p>Off outside prod (D-13). {@code local} runs the real client against the sandbox, so leaving this on
     * would email whatever address a developer typed into the form.</p>
     */
    @Value("${redemption.xtrm.beneficiary-email-notification:false}")
    private boolean emailNotification;

    public XtrmCompanyProvisioningService(PartnerCompanyXtrmAccountRepository accountRepository,
                                          PartnerCompanyRepository companyRepository,
                                          XtrmApiClient xtrmApiClient,
                                          XtrmCredentialsResolver credentialsResolver,
                                          ConnectorEncryptionService encryptionService,
                                          ClientRepository clientRepository) {
        this.accountRepository = accountRepository;
        this.companyRepository = companyRepository;
        this.xtrmApiClient = xtrmApiClient;
        this.credentialsResolver = credentialsResolver;
        this.encryptionService = encryptionService;
        this.clientRepository = clientRepository;
    }

    /**
     * Reserve this company's provisioning slot.
     *
     * <p>Called inside the company-create transaction. {@code uq_xtrm_account_per_company} is what actually
     * serializes concurrent attempts, and it can only do that if the row exists <em>before</em> anyone calls
     * XTRM. Claim first and the loser stops here; claim last and both attempts create a real beneficiary
     * company at XTRM, one of which we then forget about forever.</p>
     */
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public PartnerCompanyXtrmAccount claim(UUID clientId, UUID partnerCompanyId) {
        return accountRepository.save(PartnerCompanyXtrmAccount.builder()
                .clientId(clientId)
                .partnerCompanyId(partnerCompanyId)
                .status(XtrmAccountStatus.PENDING)
                .build());
    }

    /**
     * Run the three-call sequence against an existing claim row. Idempotent, and <b>never throws</b> — it
     * runs after the company is already committed, so an exception here can only surface as an unhandled
     * error on a background thread.
     */
    public void provision(UUID clientId, UUID partnerCompanyId) {
        try {
            doProvision(clientId, partnerCompanyId);
        } catch (RuntimeException e) {
            log.error("[step=xtrm_provision_failed] partnerCompanyId={} reason={}",
                    partnerCompanyId, e.getClass().getSimpleName(), e);
            recordError(clientId, partnerCompanyId, "Provisioning failed: " + e.getClass().getSimpleName());
        }
    }

    private void doProvision(UUID clientId, UUID partnerCompanyId) {
        PartnerCompanyXtrmAccount account = accountRepository
                .findByClientIdAndPartnerCompanyId(clientId, partnerCompanyId)
                .orElse(null);
        if (account == null) {
            log.warn("[step=xtrm_provision_skipped] no claim row; partnerCompanyId={}", partnerCompanyId);
            return;
        }
        if (account.isPayoutReady()) {
            return; // already CONNECTED — CreateBeneficiary is not replayable
        }

        PartnerCompany company = companyRepository.findByIdAndClientId(partnerCompanyId, clientId).orElse(null);
        if (company == null || !company.hasCompleteAdminDetails()) {
            log.info("[step=xtrm_provision_skipped] no admin details; partnerCompanyId={}", partnerCompanyId);
            recordError(account, "Company admin details are required before connecting to XTRM.");
            return;
        }

        // --- 1. CreateBeneficiary, then persist immediately ---------------------------------------
        if (account.getXtrmAccountNumber() == null) {
            String beneficiaryName = beneficiaryNameFor(company, clientNameOf(clientId));
            CreateBeneficiaryResult created = xtrmApiClient.createBeneficiary(new CreateBeneficiaryCommand(
                    beneficiaryName, company.getWebsite(),
                    company.getAdminFirstName(), company.getAdminLastName(), company.getAdminEmail(),
                    company.getAdminMobileNumber(), company.getAdminCountryIso2(),
                    company.getAdminCity(), company.getAdminRegion(), company.getAdminPostalCode(),
                    emailNotification));

            if (!created.success()) {
                recordError(account, String.join("; ", created.errors()));
                return;
            }

            account.setXtrmAccountNumber(created.beneficiaryAccountNumber());
            account.setXtrmBeneficiaryName(beneficiaryName);
            account.setAccountIdentityLevel(created.accountIdentityLevel());
            account.setEncryptedCredentials(
                    credentialsResolver.encryptCredentials(created.clientId(), created.clientSecret()));
            account.setLastError(null);
            // Still PENDING: no wallet yet, and the constraint refuses CONNECTED without one.
            account = accountRepository.save(account);
        }

        // --- 2. Prove the credentials work ---------------------------------------------------------
        // Cheap, and the only way to learn this before money depends on it. It also warms the token cache
        // for the first payout.
        try {
            credentialsResolver.forCompanyUnchecked(account);
        } catch (RuntimeException e) {
            recordError(account, "Credentials could not be used: " + e.getClass().getSimpleName());
            return;
        }

        // --- 3. Discover the wallet ----------------------------------------------------------------
        GetWalletsResult wallets;
        try {
            wallets = xtrmApiClient.getBeneficiaryWallets(new GetWalletsCommand(account.getXtrmAccountNumber()));
        } catch (RuntimeException e) {
            recordError(account, "Wallet lookup failed: " + e.getClass().getSimpleName());
            return;
        }
        if (!wallets.success() || wallets.wallets().isEmpty()) {
            recordError(account, "XTRM returned no wallet for this company.");
            return;
        }

        account.setXtrmWalletId(wallets.wallets().get(0).id());
        account.setStatus(XtrmAccountStatus.CONNECTED);
        account.setConnectedAt(Instant.now());
        account.setLastError(null);
        accountRepository.save(account);

        log.info("[step=xtrm_provision_connected] partnerCompanyId={} account={}",
                partnerCompanyId, account.getXtrmAccountNumber());
    }

    /**
     * The name sent as {@code BeneficiaryCompanyName}.
     *
     * <p>Our company names are unique per tenant; XTRM's namespace appears to be global under the issuer
     * account. Two tenants each with an "Acme Corp" would then collide on the second create, and the failure
     * would read as a vendor outage rather than a name clash. Disambiguating costs nothing if the namespace
     * turns out to be per-issuer after all.</p>
     */
    public String beneficiaryNameFor(PartnerCompany company, String clientName) {
        String composed = company.getName() + " (" + clientName + ")";
        return composed.length() <= NAME_MAX ? composed : composed.substring(0, NAME_MAX);
    }

    /** The tenant's name, used to disambiguate the vendor-visible company name. */
    private String clientNameOf(UUID clientId) {
        return clientRepository.findById(clientId).map(Client::getName).orElse("");
    }

    private void recordError(UUID clientId, UUID partnerCompanyId, String message) {
        accountRepository.findByClientIdAndPartnerCompanyId(clientId, partnerCompanyId)
                .ifPresent(a -> recordError(a, message));
    }

    private void recordError(PartnerCompanyXtrmAccount account, String message) {
        account.setStatus(XtrmAccountStatus.PENDING);
        account.setLastError(message == null ? null : message.substring(0, Math.min(message.length(), ERROR_MAX)));
        accountRepository.save(account);
    }
}
```

- [ ] **Step 4: Add `forCompanyUnchecked` to `XtrmCredentialsResolver`**

`forCompany` refuses a row that is not `CONNECTED`, which is correct for payouts and wrong for provisioning — during step 2 the row is deliberately still `PENDING`. Add a sibling that skips the readiness gate but nothing else:

```java
/**
 * Credentials for an account that is not CONNECTED yet.
 *
 * <p>Only for provisioning, where the row is deliberately still {@code PENDING} while we prove the
 * credentials work. Never use this on a payout path: {@link #forCompany} refuses a not-ready company on
 * purpose, and bypassing that is how a payout silently becomes a platform payout.</p>
 */
public XtrmCredentials forCompanyUnchecked(PartnerCompanyXtrmAccount account) {
    Map<String, String> secrets = decrypt(account);
    return new XtrmCredentials(secrets.get(KEY_CLIENT_ID), secrets.get(KEY_CLIENT_SECRET),
            account.getXtrmAccountNumber(), account.getXtrmWalletId(), programId);
}
```

Note this returns credentials with a null `walletId` at that point, which is fine — the token fetch keys on `clientId` only.

- [ ] **Step 5: Run the test**

Run: `./gradlew test --tests "com.tenxengage.app.service.xtrm.XtrmCompanyProvisioningServiceTest"`
Expected: PASS — all eight tests, and `persistsCredentialsEvenWhenWalletDiscoveryFails` in particular.

- [ ] **Step 6: Add the config property**

In `src/main/resources/application-local.yml` under `redemption.xtrm`, and in the equivalent prod config:

```yaml
    # XTRM emails the company admin on CreateBeneficiary. Off outside prod: `local` runs the REAL client
    # against the sandbox, so a typo in the form would email a stranger.
    beneficiary-email-notification: ${XTRM_BENEFICIARY_EMAIL_NOTIFICATION:false}
```

- [ ] **Step 7: Run everything and commit**

Run: `./gradlew test`
Expected: PASS

```bash
git add src/main/java/com/tenxengage/app/service/xtrm/XtrmCompanyProvisioningService.java \
        src/main/java/com/tenxengage/app/service/xtrm/XtrmCredentialsResolver.java \
        src/main/resources/application-local.yml \
        src/test/java/com/tenxengage/app/service/xtrm/XtrmCompanyProvisioningServiceTest.java
git commit -m "feat(xtrm): provision a company beneficiary, persisting the one-shot credentials first"
```

---

## Task 6: Wire provisioning into create and delete

Spec §4, §9.1. Two behaviours: the claim row goes in the create transaction, provisioning runs after commit, and deleting a company takes its XTRM row with it.

**Files:**
- Modify: `src/main/java/com/tenxengage/app/service/PartnerCompanyService.java:101-145, 189-196`
- Test: `src/test/java/com/tenxengage/app/service/PartnerCompanyProvisioningWiringTest.java`

**Interfaces:**
- Consumes: `XtrmCompanyProvisioningService.claim`, `.provision`; `PartnerCompanyXtrmAccountRepository.findByClientIdAndPartnerCompanyId`.
- Produces: no new public signatures.

- [ ] **Step 1: Write the failing test**

Create `src/test/java/com/tenxengage/app/service/PartnerCompanyProvisioningWiringTest.java`:

```java
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The claim row exists so that a unique constraint, not the vendor, settles concurrent provisioning. That
 * only works if it is written before anybody calls XTRM — which means inside the create transaction, not
 * after it.
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

    private PartnerCompanyService service;

    private static final UUID CLIENT_ID = UUID.randomUUID();
    private static final UUID COMPANY_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new PartnerCompanyService(partnerCompanyRepository, clientRepository, userRepository,
                locationValueRepository, tenantValidator, provisioningService, xtrmAccountRepository);

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
                "TestP", "Singh", "admin@acme.test", "4085556245",
                "San Francisco", "CA", "94105", "US");
    }

    @Test
    void claimsTheXtrmRowWhenAdminDetailsArePresent() {
        service.createPartnerCompany(withAdmin());

        verify(provisioningService).claim(eq(CLIENT_ID), eq(COMPANY_ID));
    }

    @Test
    void doesNotClaimWhenThereAreNoAdminDetails() {
        CreatePartnerCompanyRequest bare = new CreatePartnerCompanyRequest(
                "Bare Corp", "EXT-2", List.of(), "RESELLER", PartnerCompanyStatus.ACTIVE,
                null, null, null, "{}", null, null, null, null, null, null, null, null);

        service.createPartnerCompany(bare);

        verify(provisioningService, org.mockito.Mockito.never()).claim(any(), any());
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

        // Without this the FK from partner_company_xtrm_accounts makes every provisioned company
        // undeletable, and it surfaces as a generic DATA_INTEGRITY_VIOLATION that names nothing.
        verify(xtrmAccountRepository).delete(account);
        verify(partnerCompanyRepository).delete(pc);
    }
}
```

- [ ] **Step 2: Run it and confirm it fails**

Run: `./gradlew test --tests "com.tenxengage.app.service.PartnerCompanyProvisioningWiringTest"`
Expected: FAIL — `claim` is never called; `deletePartnerCompany` never touches the XTRM repository.

- [ ] **Step 3: Add the two collaborators to `PartnerCompanyService`**

Deferred from Task 3 so that nothing referenced a class that did not exist yet. Both are used in this task and the next:

```java
public PartnerCompanyService(PartnerCompanyRepository partnerCompanyRepository,
                              ClientRepository clientRepository,
                              UserRepository userRepository,
                              LocationValueRepository locationValueRepository,
                              TenantValidator tenantValidator,
                              XtrmCompanyProvisioningService provisioningService,
                              PartnerCompanyXtrmAccountRepository xtrmAccountRepository) {
    this.partnerCompanyRepository = partnerCompanyRepository;
    this.clientRepository = clientRepository;
    this.userRepository = userRepository;
    this.locationValueRepository = locationValueRepository;
    this.tenantValidator = tenantValidator;
    this.provisioningService = provisioningService;
    this.xtrmAccountRepository = xtrmAccountRepository;
}
```

Then fix every other construction site:

Run: `grep -rn "new PartnerCompanyService(" src/ --include=*.java`

Pass `null, null` in tests that do not exercise provisioning — including `PartnerCompanyAdminDetailsTest` from Task 3, whose five-argument call now needs two more.

- [ ] **Step 4: Claim inside the create transaction**

At the end of `createPartnerCompany`, after `assignLocationValues`:

```java
        // Claim the provisioning slot INSIDE this transaction. uq_xtrm_account_per_company is what
        // serializes concurrent attempts, and it can only do that if the row lands before anyone calls
        // XTRM. Claiming afterwards leaves a window where two attempts both reach CreateBeneficiary and
        // one real beneficiary company is created and then forgotten.
        if (saved.hasCompleteAdminDetails()) {
            provisioningService.claim(clientId, saved.getId());
            registerProvisioningAfterCommit(clientId, saved.getId());
        }

        return PartnerCompanyResponse.from(saved, client.getName());
```

- [ ] **Step 5: Run provisioning after commit**

```java
/**
 * Run the XTRM calls after this transaction commits.
 *
 * <p>Inside the transaction they would hold a database connection open for the vendor's latency on every
 * company create. After commit they cannot roll the company back either — which is the intent: a vendor
 * outage must not fail a company create (D-2).</p>
 */
private void registerProvisioningAfterCommit(UUID clientId, UUID partnerCompanyId) {
    if (!TransactionSynchronizationManager.isSynchronizationActive()) {
        provisioningService.provision(clientId, partnerCompanyId);
        return;
    }
    TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
        @Override
        public void afterCommit() {
            provisioningService.provision(clientId, partnerCompanyId);
        }
    });
}
```

Import `org.springframework.transaction.support.TransactionSynchronization` and `TransactionSynchronizationManager`.

- [ ] **Step 6: Delete the XTRM row with the company**

```java
@Transactional
public void deletePartnerCompany(UUID id) {
    UUID clientId = tenantValidator.getCurrentClientId();
    PartnerCompany pc = partnerCompanyRepository.findByIdAndClientId(id, clientId)
        .orElseThrow(() -> new ResourceNotFoundException("PartnerCompany", "id", id));

    // partner_company_xtrm_accounts has an FK to this row, so leaving it makes every provisioned company
    // undeletable — behind a generic DATA_INTEGRITY_VIOLATION that names neither the constraint nor the
    // reason. Nothing is deleted at XTRM: we have no endpoint for it, and an abandoned beneficiary whose
    // credentials we no longer hold can move no money.
    xtrmAccountRepository.findByClientIdAndPartnerCompanyId(clientId, id)
        .ifPresent(account -> {
            account.setStatus(XtrmAccountStatus.DISABLED);
            xtrmAccountRepository.delete(account);
        });

    partnerCompanyRepository.delete(pc);
}
```

- [ ] **Step 7: Run the test, then everything**

Run: `./gradlew test --tests "com.tenxengage.app.service.PartnerCompanyProvisioningWiringTest"`
Expected: PASS

Run: `./gradlew test`
Expected: PASS

- [ ] **Step 8: Commit**

```bash
git add src/main/java/com/tenxengage/app/service/PartnerCompanyService.java \
        src/test/java/com/tenxengage/app/service/PartnerCompanyAdminDetailsTest.java \
        src/test/java/com/tenxengage/app/service/PartnerCompanyProvisioningWiringTest.java
git commit -m "feat(partner-company): claim the XTRM slot in-transaction, provision after commit, clean up on delete"
```

---

## Task 7: Connect endpoint and the `xtrmAccount` response block

Spec §6. One endpoint, three jobs, and which job it does is decided by the row's state rather than a mode flag.

**Files:**
- Create: `src/main/java/com/tenxengage/app/dto/request/ConnectXtrmAccountRequest.java`
- Create: `src/main/java/com/tenxengage/app/dto/response/PartnerCompanyXtrmAccountResponse.java`
- Modify: `src/main/java/com/tenxengage/app/dto/response/PartnerCompanyResponse.java`
- Modify: `src/main/java/com/tenxengage/app/service/PartnerCompanyService.java`
- Modify: `src/main/java/com/tenxengage/app/controller/PartnerCompanyController.java`
- Test: `src/test/java/com/tenxengage/app/service/PartnerCompanyXtrmConnectTest.java`

**Interfaces:**
- Consumes: Task 5's `provision`/`claim`, Task 6's repository field.
- Produces:
  - `ConnectXtrmAccountRequest(String adminFirstName, String adminLastName, String adminEmail, String adminMobileNumber, String adminCity, String adminRegion, String adminPostalCode, String adminCountryIso2, String xtrmWalletId)` — every field nullable.
  - `PartnerCompanyXtrmAccountResponse(String status, String accountNumber, String identityLevel, String lastError)` + `static PartnerCompanyXtrmAccountResponse from(PartnerCompanyXtrmAccount)`.
  - `PartnerCompanyResponse` gains a final component `PartnerCompanyXtrmAccountResponse xtrmAccount`.
  - `PartnerCompanyService.connectXtrmAccount(UUID id, ConnectXtrmAccountRequest request)` returning `PartnerCompanyResponse`.

- [ ] **Step 1: Write the failing test**

Create `src/test/java/com/tenxengage/app/service/PartnerCompanyXtrmConnectTest.java`:

```java
package com.tenxengage.app.service;

import com.tenxengage.app.dto.request.ConnectXtrmAccountRequest;
import com.tenxengage.app.entity.Client;
import com.tenxengage.app.entity.PartnerCompany;
import com.tenxengage.app.entity.PartnerCompanyXtrmAccount;
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

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The connect endpoint provisions a legacy company, retries a failure, or supplies a wallet id by hand.
 * Which of the three it does is read off the row's state, so a caller cannot pick the wrong mode — and it
 * must never re-run CreateBeneficiary for a company that already has an SPN, because that call is not
 * replayable and a second one would mint a second account for one company.
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

    private PartnerCompanyService service;

    private static final UUID CLIENT_ID = UUID.randomUUID();
    private static final UUID COMPANY_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new PartnerCompanyService(partnerCompanyRepository, clientRepository, userRepository,
                locationValueRepository, tenantValidator, provisioningService, xtrmAccountRepository);
        // Mockito builds the bean directly, so there is no Spring proxy and `self` would be null.
        service.setSelf(service);

        when(tenantValidator.getCurrentClientId()).thenReturn(CLIENT_ID);
        Client client = new Client();
        client.setName("Apple");
        when(clientRepository.findById(CLIENT_ID)).thenReturn(Optional.of(client));

        PartnerCompany pc = PartnerCompany.builder().name("Acme Corp").clientId(CLIENT_ID).build();
        pc.setId(COMPANY_ID);
        when(partnerCompanyRepository.findByIdAndClientId(COMPANY_ID, CLIENT_ID)).thenReturn(Optional.of(pc));
        when(partnerCompanyRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(xtrmAccountRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private ConnectXtrmAccountRequest fullAdmin() {
        return new ConnectXtrmAccountRequest("TestP", "Singh", "admin@acme.test", "4085556245",
                "San Francisco", "CA", "94105", "US", null);
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
    void retriesAPendingRowWithoutReclaimingIt() {
        PartnerCompanyXtrmAccount pending = PartnerCompanyXtrmAccount.builder()
                .clientId(CLIENT_ID).partnerCompanyId(COMPANY_ID)
                .status(XtrmAccountStatus.PENDING).lastError("Could not reach XTRM").build();
        when(xtrmAccountRepository.findByClientIdAndPartnerCompanyId(CLIENT_ID, COMPANY_ID))
                .thenReturn(Optional.of(pending));

        service.connectXtrmAccount(COMPANY_ID, new ConnectXtrmAccountRequest(
                null, null, null, null, null, null, null, null, null));

        verify(provisioningService, never()).claim(any(), any());
        verify(provisioningService).provision(CLIENT_ID, COMPANY_ID);
    }

    @Test
    void acceptsAManualWalletIdAndConnects() {
        PartnerCompanyXtrmAccount pending = PartnerCompanyXtrmAccount.builder()
                .clientId(CLIENT_ID).partnerCompanyId(COMPANY_ID)
                .status(XtrmAccountStatus.PENDING)
                .xtrmAccountNumber("SPN26241004").encryptedCredentials("blob").build();
        when(xtrmAccountRepository.findByClientIdAndPartnerCompanyId(CLIENT_ID, COMPANY_ID))
                .thenReturn(Optional.of(pending));

        service.connectXtrmAccount(COMPANY_ID, new ConnectXtrmAccountRequest(
                null, null, null, null, null, null, null, null, "206415"));

        assertThat(pending.getXtrmWalletId()).isEqualTo("206415");
        assertThat(pending.getStatus()).isEqualTo(XtrmAccountStatus.CONNECTED);
    }

    @Test
    void isANoOpWhenAlreadyConnected() {
        PartnerCompanyXtrmAccount connected = PartnerCompanyXtrmAccount.builder()
                .clientId(CLIENT_ID).partnerCompanyId(COMPANY_ID)
                .status(XtrmAccountStatus.CONNECTED)
                .xtrmAccountNumber("SPN26241004").xtrmWalletId("206415").encryptedCredentials("blob").build();
        when(xtrmAccountRepository.findByClientIdAndPartnerCompanyId(CLIENT_ID, COMPANY_ID))
                .thenReturn(Optional.of(connected));

        service.connectXtrmAccount(COMPANY_ID, fullAdmin());

        verify(provisioningService, never()).claim(any(), any());
        verify(provisioningService, never()).provision(any(), any());
    }
}
```

- [ ] **Step 2: Run it and confirm it fails**

Run: `./gradlew test --tests "com.tenxengage.app.service.PartnerCompanyXtrmConnectTest"`
Expected: FAIL — `ConnectXtrmAccountRequest` and `connectXtrmAccount` do not exist.

- [ ] **Step 3: Create the DTOs**

`src/main/java/com/tenxengage/app/dto/request/ConnectXtrmAccountRequest.java`:

```java
package com.tenxengage.app.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

/**
 * Body for {@code POST /partner-companies/{id}/xtrm/connect}. Every field is optional: what the call does
 * is decided by the account row's state, not by which fields the caller filled in.
 */
public record ConnectXtrmAccountRequest(
    @Size(max = 100) String adminFirstName,
    @Size(max = 100) String adminLastName,
    @Email @Size(max = 255) String adminEmail,
    @Size(max = 20) String adminMobileNumber,
    @Size(max = 100) String adminCity,
    @Size(max = 100) String adminRegion,
    @Size(max = 20) String adminPostalCode,
    @Size(min = 2, max = 2) String adminCountryIso2,
    @Size(max = 50) String xtrmWalletId
) {}
```

`src/main/java/com/tenxengage/app/dto/response/PartnerCompanyXtrmAccountResponse.java`:

```java
package com.tenxengage.app.dto.response;

import com.tenxengage.app.entity.PartnerCompanyXtrmAccount;

/**
 * The company's XTRM connection, as an admin sees it.
 *
 * <p>Carries identifiers and a failure reason only. It must never gain a credentials field, in any shape or
 * under any name — this record is serialized straight to a browser.</p>
 */
public record PartnerCompanyXtrmAccountResponse(
    String status,
    String accountNumber,
    String identityLevel,
    String lastError
) {
    public static PartnerCompanyXtrmAccountResponse from(PartnerCompanyXtrmAccount a) {
        if (a == null) {
            return null;
        }
        return new PartnerCompanyXtrmAccountResponse(
                a.getStatus() == null ? null : a.getStatus().name(),
                a.getXtrmAccountNumber(),
                a.getAccountIdentityLevel(),
                a.getLastError());
    }
}
```

- [ ] **Step 4: Add `xtrmAccount` to `PartnerCompanyResponse`**

Add `PartnerCompanyXtrmAccountResponse xtrmAccount` as the last record component. Both existing `from(...)` factories pass `null` for it; add a third overload used by the detail path:

```java
public static PartnerCompanyResponse from(PartnerCompany pc, String clientName, long activeUserCount,
                                          PartnerCompanyXtrmAccountResponse xtrmAccount) {
```

Have the two existing factories delegate to it with `null`, so no call site breaks.

- [ ] **Step 5: Implement `connectXtrmAccount`**

```java
/**
 * Provision, retry, or finish a company's XTRM connection.
 *
 * <p>Which of the three happens is read off the row, not off a mode flag the caller has to get right.
 * Crucially it never re-runs {@code CreateBeneficiary} for a company that already has an SPN: that call is
 * not replayable, and a second one would either fail on the duplicate name or mint a second account for a
 * single company.</p>
 *
 * <p><b>Deliberately not {@code @Transactional}.</b> Provisioning makes three HTTP calls to XTRM, and
 * holding a database connection open for the vendor's latency is exactly what the create path goes out of
 * its way to avoid. The database work is split into {@link #prepareXtrmConnection}, which is transactional
 * and short; the vendor calls happen outside it, and the response is built from a fresh read afterwards so
 * it still reports the real status.</p>
 */
public PartnerCompanyResponse connectXtrmAccount(UUID id, ConnectXtrmAccountRequest request) {
    UUID clientId = tenantValidator.getCurrentClientId();

    // Through `self`, not `this`. A @Transactional method invoked directly on the same bean bypasses the
    // proxy, so the annotation would silently do nothing — and `claim`'s Propagation.MANDATORY would then
    // throw, which is at least a loud failure, but the wallet-id branch would quietly run without a
    // transaction. Same self-injection CompanyDistributionDispatcher uses.
    boolean needsProvisioning = self.prepareXtrmConnection(clientId, id, request);

    if (needsProvisioning) {
        provisioningService.provision(clientId, id);
    }

    return self.readCompanyWithXtrmAccount(clientId, id);
}

/**
 * The transactional half: persist any supplied admin details, finish the row if a wallet id was supplied
 * by hand, and claim a slot if there is not one yet.
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
        return false; // already connected — nothing to do, and CreateBeneficiary is not replayable
    }

    // A wallet id supplied by hand is the one case that finishes the row without calling XTRM — used when
    // wallet discovery could not find one.
    if (existing.isPresent() && request.xtrmWalletId() != null && !request.xtrmWalletId().isBlank()
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
        // claim() is Propagation.MANDATORY — it is called here, inside this transaction, and never from
        // connectXtrmAccount directly. Moving it out would make it throw.
        provisioningService.claim(clientId, id);
    }
    return true;
}

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
    if (r.adminFirstName() != null) pc.setAdminFirstName(r.adminFirstName());
    if (r.adminLastName() != null) pc.setAdminLastName(r.adminLastName());
    if (r.adminEmail() != null) pc.setAdminEmail(r.adminEmail());
    if (r.adminMobileNumber() != null) pc.setAdminMobileNumber(r.adminMobileNumber());
    if (r.adminCity() != null) pc.setAdminCity(r.adminCity());
    if (r.adminRegion() != null) pc.setAdminRegion(r.adminRegion());
    if (r.adminPostalCode() != null) pc.setAdminPostalCode(r.adminPostalCode());
    if (r.adminCountryIso2() != null) pc.setAdminCountryIso2(r.adminCountryIso2());
}
```

Add the self-reference, copying `CompanyDistributionDispatcher:73,96`:

```java
/** Self-proxy, so the @Transactional methods below are actually proxied when called from this class. */
private PartnerCompanyService self;

@Autowired
public void setSelf(@Lazy PartnerCompanyService self) {
    this.self = self;
}
```

In `PartnerCompanyXtrmConnectTest`, `self` is null because Mockito constructs the bean directly. Set it in `setUp` so the test exercises the real path:

```java
service.setSelf(service);
```

Also extend `getPartnerCompanyById` to populate the `xtrmAccount` block from `xtrmAccountRepository`.

- [ ] **Step 6: Add the controller endpoint**

```java
@PostMapping("/{id}/xtrm/connect")
@Operation(summary = "Connect a partner company to XTRM",
           description = "Provisions the company's XTRM beneficiary account, retries a failed attempt, or "
                       + "supplies a wallet id by hand. Idempotent: a connected company is returned as-is.")
@RequiresPermission("action.partner_company.edit")
@Audited(action = "Connected to XTRM", resourceType = "PARTNER_COMPANY",
         resourceName = "#result.body.name", resourceId = "#result.body.id.toString()")
public ResponseEntity<PartnerCompanyResponse> connectXtrmAccount(
        @PathVariable UUID id,
        @Valid @RequestBody(required = false) ConnectXtrmAccountRequest request) {
    ConnectXtrmAccountRequest body = request != null ? request
            : new ConnectXtrmAccountRequest(null, null, null, null, null, null, null, null, null);
    return ResponseEntity.ok(partnerCompanyService.connectXtrmAccount(id, body));
}
```

`@Audited` takes **strings and SpEL**, not enums — `AuditResourceType.PARTNER_COMPANY` exists as an enum constant but is not how this annotation is fed. Compare against `updatePartnerCompany` at `PartnerCompanyController:77` if in doubt.

- [ ] **Step 7: Run the test, then everything**

Run: `./gradlew test --tests "com.tenxengage.app.service.PartnerCompanyXtrmConnectTest"`
Expected: PASS

Run: `./gradlew test`
Expected: PASS

- [ ] **Step 8: Commit**

```bash
git add src/main/java/com/tenxengage/app/dto/request/ConnectXtrmAccountRequest.java \
        src/main/java/com/tenxengage/app/dto/response/PartnerCompanyXtrmAccountResponse.java \
        src/main/java/com/tenxengage/app/dto/response/PartnerCompanyResponse.java \
        src/main/java/com/tenxengage/app/service/PartnerCompanyService.java \
        src/main/java/com/tenxengage/app/controller/PartnerCompanyController.java \
        src/test/java/com/tenxengage/app/service/PartnerCompanyXtrmConnectTest.java
git commit -m "feat(partner-company): connect endpoint and the xtrmAccount status block"
```

---

## Task 8: `XtrmRemitterResolver` and the dispatch switch

Spec §7. One method answers "who pays for this redemption?", and Task 9 reuses it. Two implementations that could disagree is the defect this design exists to avoid.

**Files:**
- Create: `src/main/java/com/tenxengage/app/service/xtrm/XtrmRemitterResolver.java`
- Modify: `src/main/java/com/tenxengage/app/service/XtrmVendorService.java:172`
- Test: `src/test/java/com/tenxengage/app/service/xtrm/XtrmRemitterResolverTest.java`

**Interfaces:**
- Consumes: `CompanyDistributionItemRepository.findByRedemptionRequestId(UUID)`, `CompanyDistributionRepository.findById(UUID)`, `XtrmCredentialsResolver.forCompany(UUID, UUID)` / `.platform()`.
- Produces: `XtrmRemitterResolver.forRedemption(UUID redemptionRequestId) -> XtrmCredentials`.

- [ ] **Step 1: Write the failing test**

Create `src/test/java/com/tenxengage/app/service/xtrm/XtrmRemitterResolverTest.java`:

```java
package com.tenxengage.app.service.xtrm;

import com.tenxengage.app.entity.CompanyDistribution;
import com.tenxengage.app.entity.CompanyDistributionItem;
import com.tenxengage.app.repository.CompanyDistributionItemRepository;
import com.tenxengage.app.repository.CompanyDistributionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Who pays for a given redemption.
 *
 * <p>Two things must hold. A distribution leg pays from its company, or XTRM rejects the wallet. A personal
 * redemption keeps paying from the platform, byte for byte, because that path has 27 successful payouts
 * behind it and this feature must not disturb it.</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class XtrmRemitterResolverTest {

    @Mock private CompanyDistributionItemRepository itemRepository;
    @Mock private CompanyDistributionRepository distributionRepository;
    @Mock private XtrmCredentialsResolver credentialsResolver;
    @InjectMocks private XtrmRemitterResolver resolver;

    private static final UUID REDEMPTION_ID = UUID.randomUUID();
    private static final UUID CLIENT_ID = UUID.randomUUID();
    private static final UUID COMPANY_ID = UUID.randomUUID();
    private static final UUID DISTRIBUTION_ID = UUID.randomUUID();

    private final XtrmCredentials platform =
            new XtrmCredentials("platform-id", "s", "SPN26237883", "203871", "2314");
    private final XtrmCredentials company =
            new XtrmCredentials("company-id", "s", "SPN26241004", "206415", "2314");

    @Test
    void aDistributionLegPaysFromItsCompany() {
        CompanyDistributionItem item = new CompanyDistributionItem();
        item.setDistributionId(DISTRIBUTION_ID);
        when(itemRepository.findByRedemptionRequestId(REDEMPTION_ID)).thenReturn(Optional.of(item));

        CompanyDistribution distribution = new CompanyDistribution();
        distribution.setClientId(CLIENT_ID);
        distribution.setPartnerCompanyId(COMPANY_ID);
        when(distributionRepository.findById(DISTRIBUTION_ID)).thenReturn(Optional.of(distribution));
        when(credentialsResolver.forCompany(CLIENT_ID, COMPANY_ID)).thenReturn(company);

        assertThat(resolver.forRedemption(REDEMPTION_ID)).isEqualTo(company);
    }

    @Test
    void aPersonalRedemptionPaysFromThePlatform() {
        when(itemRepository.findByRedemptionRequestId(REDEMPTION_ID)).thenReturn(Optional.empty());
        when(credentialsResolver.platform()).thenReturn(platform);

        assertThat(resolver.forRedemption(REDEMPTION_ID)).isEqualTo(platform);
        verify(credentialsResolver).platform();
    }

    @Test
    void anOrphanedItemFallsBackToThePlatformRatherThanFailing() {
        CompanyDistributionItem item = new CompanyDistributionItem();
        item.setDistributionId(DISTRIBUTION_ID);
        when(itemRepository.findByRedemptionRequestId(REDEMPTION_ID)).thenReturn(Optional.of(item));
        when(distributionRepository.findById(DISTRIBUTION_ID)).thenReturn(Optional.empty());
        when(credentialsResolver.platform()).thenReturn(platform);

        assertThat(resolver.forRedemption(REDEMPTION_ID)).isEqualTo(platform);
    }
}
```

- [ ] **Step 2: Run it and confirm it fails**

Run: `./gradlew test --tests "com.tenxengage.app.service.xtrm.XtrmRemitterResolverTest"`
Expected: FAIL — the class does not exist.

- [ ] **Step 3: Implement the resolver**

```java
package com.tenxengage.app.service.xtrm;

import com.tenxengage.app.repository.CompanyDistributionItemRepository;
import com.tenxengage.app.repository.CompanyDistributionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * The single answer to "which XTRM account pays for this redemption?"
 *
 * <p>Dispatch and reconciliation both ask, and they must get the same answer. Two implementations that
 * merely agree today would drift, and the drift is invisible until it strands money: reconciliation would
 * poll as the wrong account, never find the transaction, and leave the item {@code PROCESSING} with the
 * recipient's share reserved forever.</p>
 */
@Service
public class XtrmRemitterResolver {

    private final CompanyDistributionItemRepository itemRepository;
    private final CompanyDistributionRepository distributionRepository;
    private final XtrmCredentialsResolver credentialsResolver;

    public XtrmRemitterResolver(CompanyDistributionItemRepository itemRepository,
                                CompanyDistributionRepository distributionRepository,
                                XtrmCredentialsResolver credentialsResolver) {
        this.itemRepository = itemRepository;
        this.distributionRepository = distributionRepository;
        this.credentialsResolver = credentialsResolver;
    }

    /**
     * Company credentials for a distribution leg, platform credentials for everything else.
     *
     * <p>No distribution row means a personal redemption, which must keep behaving exactly as it does
     * today. {@code forCompany} throws for a company that is not connected rather than falling back — a
     * fallback would look like success while paying the seller out of the client's money.</p>
     */
    @Transactional(readOnly = true)
    public XtrmCredentials forRedemption(UUID redemptionRequestId) {
        return itemRepository.findByRedemptionRequestId(redemptionRequestId)
                .flatMap(item -> distributionRepository.findById(item.getDistributionId()))
                .map(d -> credentialsResolver.forCompany(d.getClientId(), d.getPartnerCompanyId()))
                .orElseGet(credentialsResolver::platform);
    }
}
```

- [ ] **Step 4: Use it in `XtrmVendorService`**

Add `XtrmRemitterResolver remitterResolver` as a constructor dependency, then change the call at line 172:

```java
        // Who pays. A distribution leg pays from its company's wallet; a personal redemption keeps paying
        // from the platform. Same method reconciliation uses, deliberately.
        XtrmCredentials remitter = remitterResolver.forRedemption(request.getId());

        TransferFundResult result = xtrmApiClient.transferFund(new TransferFundCommand(
                request.getId().toString(), recipientUserId, paymentMethodId, partnerLinkedBankId, cardToken,
                sku, giftCardEmail,
                request.getAmount(), currency, "Reward redemption"), remitter);
```

- [ ] **Step 5: Run the test, fix construction sites, run everything**

Run: `./gradlew test --tests "com.tenxengage.app.service.xtrm.XtrmRemitterResolverTest"`
Expected: PASS

Run: `grep -rn "new XtrmVendorService(" src/ --include=*.java` and add the new argument to each.

Run: `./gradlew test`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/tenxengage/app/service/xtrm/XtrmRemitterResolver.java \
        src/main/java/com/tenxengage/app/service/XtrmVendorService.java \
        src/test/java/com/tenxengage/app/service/xtrm/XtrmRemitterResolverTest.java
git commit -m "feat(distribution): pay a seller from their own company's XTRM wallet"
```

---

## Task 9: Reconciliation follows the remitter

Spec §7. Task 8 alone is a bug: it makes companies the remitter while reconciliation still polls as the platform.

**Files:**
- Modify: `src/main/java/com/tenxengage/app/service/xtrm/XtrmApiClient.java`
- Modify: `src/main/java/com/tenxengage/app/service/xtrm/XtrmApiClientImpl.java`
- Modify: `src/main/java/com/tenxengage/app/service/xtrm/XtrmApiClientStub.java`
- Modify: `src/main/java/com/tenxengage/app/service/RedemptionReconciliationService.java:158,176`
- Test: `src/test/java/com/tenxengage/app/service/RedemptionReconciliationRemitterTest.java`

**Interfaces:**
- Consumes: `XtrmRemitterResolver.forRedemption` from Task 8.
- Produces:
  - `XtrmApiClient.getTransactionDetails(GetTransactionDetailsCommand cmd, XtrmCredentials credentials)`
  - `XtrmApiClient.getBatchStatus(GetBatchStatusCommand cmd, XtrmCredentials credentials)`
  - Both existing no-credentials overloads remain and delegate to platform credentials.

- [ ] **Step 1: Write the failing test**

Create `src/test/java/com/tenxengage/app/service/RedemptionReconciliationRemitterTest.java`:

```java
package com.tenxengage.app.service;

import com.tenxengage.app.service.xtrm.XtrmCredentials;
import com.tenxengage.app.service.xtrm.XtrmRemitterResolver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Reconciliation must ask the same question dispatch asks.
 *
 * <p>CompanyDistributionDispatcher deliberately parks an ambiguous payout in PROCESSING and relies on
 * reconciliation to settle it — "never release on an unknown outcome" is the right call. But a
 * reconciliation that polls as the platform for a transaction the company remitted finds nothing on every
 * run. The item never settles and the recipient's share stays reserved indefinitely, which is worse than
 * either releasing or failing.</p>
 */
@ExtendWith(MockitoExtension.class)
class RedemptionReconciliationRemitterTest {

    @Test
    void reconciliationDependsOnTheSharedRemitterResolver() {
        boolean usesResolver = Arrays.stream(RedemptionReconciliationService.class.getDeclaredFields())
                .anyMatch(f -> f.getType().equals(XtrmRemitterResolver.class));

        assertThat(usesResolver)
                .as("RedemptionReconciliationService must resolve the remitter through XtrmRemitterResolver, "
                        + "the same method XtrmVendorService uses — not its own copy of the logic")
                .isTrue();
    }

    @Test
    void theTransactionStatusApiAcceptsCredentials() throws NoSuchMethodException {
        Method m = com.tenxengage.app.service.xtrm.XtrmApiClient.class.getMethod(
                "getTransactionDetails",
                com.tenxengage.app.service.xtrm.XtrmApiClient.GetTransactionDetailsCommand.class,
                XtrmCredentials.class);

        assertThat(m).isNotNull();
    }

    @Test
    void theBatchStatusApiAcceptsCredentials() throws NoSuchMethodException {
        Method m = com.tenxengage.app.service.xtrm.XtrmApiClient.class.getMethod(
                "getBatchStatus",
                com.tenxengage.app.service.xtrm.XtrmApiClient.GetBatchStatusCommand.class,
                XtrmCredentials.class);

        assertThat(m).isNotNull();
    }
}
```

These are structural assertions rather than behavioural ones on purpose: the behaviour they protect only manifests against a live XTRM, and a structural test that fails the moment someone reintroduces a second copy of the resolution logic is worth more here than a mock-heavy test that proves two mocks agree.

- [ ] **Step 2: Run it and confirm it fails**

Run: `./gradlew test --tests "com.tenxengage.app.service.RedemptionReconciliationRemitterTest"`
Expected: FAIL — no such overloads, no such field.

- [ ] **Step 3: Add the interface overloads**

In `XtrmApiClient.java`:

```java
/** Poll one transaction ({@code GetUserWalletTransactionDetails}) as the platform. */
TransactionStatusResult getTransactionDetails(GetTransactionDetailsCommand command);

/**
 * Poll one transaction as a specific remitter.
 *
 * <p>Required once a partner company can be the remitter: the transaction belongs to whichever account
 * paid, so polling as the platform for a company-remitted payout finds nothing.</p>
 */
TransactionStatusResult getTransactionDetails(GetTransactionDetailsCommand command, XtrmCredentials credentials);

/** Poll a batch ({@code GetBatchStatus}) as the platform. */
BatchStatusResult getBatchStatus(GetBatchStatusCommand command);

/** Poll a batch as a specific remitter. */
BatchStatusResult getBatchStatus(GetBatchStatusCommand command, XtrmCredentials credentials);
```

- [ ] **Step 4: Implement in `XtrmApiClientImpl`**

Mirror the existing `transferFund` pair exactly — the no-arg form delegates, and the credentials form threads `credentials.issuerAccountNumber()` into the body **and** uses the credentials-aware `post(...)` overload:

```java
@Override
public TransactionStatusResult getTransactionDetails(GetTransactionDetailsCommand cmd) {
    return getTransactionDetails(cmd, platformCredentials());
}

@Override
public TransactionStatusResult getTransactionDetails(GetTransactionDetailsCommand cmd, XtrmCredentials credentials) {
    Map<String, Object> request = new LinkedHashMap<>();
    // Both of these must be the remitter's, not the platform's: the account in the body and the account
    // the bearer token belongs to have to agree, or XTRM reports the transaction as not found.
    request.put("IssuerAccountNumber", credentials.issuerAccountNumber());
    request.put("TransactionID", cmd.transactionId());
    request.put("UserID", cmd.recipientUserId());
    Map<String, Object> body = Map.of("GetUserTransactionDetails", Map.of("Request", request));

    log.info("[step=xtrm_txn_status] GetUserWalletTransactionDetails txnId={} issuer={}",
            cmd.transactionId(), credentials.issuerAccountNumber());
    Map<?, ?> response;
    try {
        response = post("/API/v4/Wallet/GetUserWalletTransactionDetails", body, credentials);
    } catch (RuntimeException e) {
        log.warn("[step=xtrm_txn_status_failed] transport error txnId={}: {}",
                cmd.transactionId(), e.getClass().getSimpleName());
        return TransactionStatusResult.error(true);
    }
    // ... existing parse body unchanged ...
}
```

Do the same for `getBatchStatus`. Confirm the credentials-aware `post(path, body, credentials)` overload exists (it does — `XtrmApiClientImpl:810`); if the signature differs, match it rather than adding another.

- [ ] **Step 5: Implement the stub overloads**

```java
@Override
public TransactionStatusResult getTransactionDetails(GetTransactionDetailsCommand cmd, XtrmCredentials credentials) {
    log.info("[stub] XTRM GetUserWalletTransactionDetails as issuerAccount={}", credentials.issuerAccountNumber());
    return getTransactionDetails(cmd);
}

@Override
public BatchStatusResult getBatchStatus(GetBatchStatusCommand cmd, XtrmCredentials credentials) {
    log.info("[stub] XTRM GetBatchStatus as issuerAccount={}", credentials.issuerAccountNumber());
    return getBatchStatus(cmd);
}
```

- [ ] **Step 6: Wire the reconciliation service**

Add `XtrmRemitterResolver remitterResolver` as a constructor dependency. In `reconcileSingle`:

```java
    TransactionStatusResult res = xtrmApiClient.getTransactionDetails(
            new GetTransactionDetailsCommand(pat, beneficiaryTxn),
            remitterResolver.forRedemption(r.getId()));
```

In `reconcileBatch`, resolve once from the first item in the batch — every item in a batch shares a remitter — and pass it to `getBatchStatus`:

```java
    // Every item in a batch was sent by the same account, so one resolution covers the page loop.
    XtrmCredentials remitter = remitterResolver.forRedemption(items.get(0).getId());
    ...
    BatchStatusResult res = xtrmApiClient.getBatchStatus(
            new GetBatchStatusCommand(customerBatchId, skip, batchPageSize), remitter);
```

Guard the empty-list case: return `0` immediately if `items.isEmpty()`.

- [ ] **Step 7: Run the test, fix construction sites, run everything**

Run: `./gradlew test --tests "com.tenxengage.app.service.RedemptionReconciliationRemitterTest"`
Expected: PASS

Run: `grep -rn "new RedemptionReconciliationService(" src/ --include=*.java` and add the argument.

Run: `./gradlew test`
Expected: PASS

- [ ] **Step 8: Commit**

```bash
git add src/main/java/com/tenxengage/app/service/xtrm/XtrmApiClient.java \
        src/main/java/com/tenxengage/app/service/xtrm/XtrmApiClientImpl.java \
        src/main/java/com/tenxengage/app/service/xtrm/XtrmApiClientStub.java \
        src/main/java/com/tenxengage/app/service/RedemptionReconciliationService.java \
        src/test/java/com/tenxengage/app/service/RedemptionReconciliationRemitterTest.java
git commit -m "fix(reconciliation): poll as the account that actually paid, or items strand in PROCESSING"
```

---

## Task 10: The company-not-connected eligibility reason

Spec §7. Reported on listing, not just submit, so an admin sees why a rail is closed before building a distribution.

**Files:**
- Modify: `src/main/java/com/tenxengage/app/service/DistributionRecipientService.java`
- Test: `src/test/java/com/tenxengage/app/service/DistributionRecipientCompanyConnectionTest.java`

**Interfaces:**
- Consumes: `XtrmCredentialsResolver.canPayFromOwnWallet(UUID clientId, UUID partnerCompanyId)`.
- Produces: no new public signatures; `listRecipients` and `assertAllEligible` behaviour changes.

- [ ] **Step 1: Write the failing test**

Create `src/test/java/com/tenxengage/app/service/DistributionRecipientCompanyConnectionTest.java`:

```java
package com.tenxengage.app.service;

import com.tenxengage.app.dto.response.DistributionRecipientResponse;
import com.tenxengage.app.entity.User;
import com.tenxengage.app.entity.enums.DistributionRail;
import com.tenxengage.app.repository.UserRepository;
import com.tenxengage.app.repository.xtrm.PartnerLinkedBankRepository;
import com.tenxengage.app.repository.xtrm.PartnerRedemptionRepository;
import com.tenxengage.app.service.xtrm.XtrmCredentialsResolver;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * A company that has not finished XTRM setup cannot pay anyone, and that is a property of the company, not
 * of any individual seller. Reporting it per-seller on the listing is what lets an admin see the real
 * blocker before they build a distribution and hit a refusal at submit.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DistributionRecipientCompanyConnectionTest {

    @Mock private UserRepository userRepository;
    @Mock private PartnerRedemptionRepository profileRepository;
    @Mock private PartnerLinkedBankRepository linkedBankRepository;
    @Mock private XtrmCredentialsResolver credentialsResolver;

    private DistributionRecipientService service;

    private static final UUID CLIENT_ID = UUID.randomUUID();
    private static final UUID COMPANY_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new DistributionRecipientService(userRepository, profileRepository, linkedBankRepository,
                credentialsResolver, true);

        User seller = new User();
        seller.setId(UUID.randomUUID());
        seller.setEmail("seller@acme.test");
        when(userRepository.findActiveSellersOfCompany(CLIENT_ID, COMPANY_ID)).thenReturn(List.of(seller));
        // profilesByUser loops per seller calling findByUserIdAndClientId — PartnerRedemptionRepository has
        // no bulk finder. Empty means "no payout profile", which is irrelevant here: the company-level
        // blocker must fire regardless of any individual seller's readiness.
        when(profileRepository.findByUserIdAndClientId(any(), any())).thenReturn(Optional.empty());
    }

    @Test
    void reportsTheCompanyBlockerOnTheListing() {
        when(credentialsResolver.canPayFromOwnWallet(CLIENT_ID, COMPANY_ID)).thenReturn(false);

        List<DistributionRecipientResponse> out =
                service.listRecipients(CLIENT_ID, COMPANY_ID, DistributionRail.GIFT_CARD);

        assertThat(out).hasSize(1);
        assertThat(out.get(0).eligible()).isFalse();
        assertThat(out.get(0).reason()).containsIgnoringCase("not connected");
    }

    @Test
    void doesNotApplyTheCompanyBlockerToWalletCredit() {
        when(credentialsResolver.canPayFromOwnWallet(CLIENT_ID, COMPANY_ID)).thenReturn(false);

        List<DistributionRecipientResponse> out =
                service.listRecipients(CLIENT_ID, COMPANY_ID, DistributionRail.WALLET_CREDIT);

        // WALLET_CREDIT moves money inside our own ledger and calls no vendor, so an unconnected company
        // is irrelevant to it. This is the rail that keeps the store usable while XTRM setup is pending.
        assertThat(out.get(0).reason()).doesNotContainIgnoringCase("not connected");
    }
}
```

**This task breaks an existing test, and that is expected.** `DistributionRecipientServiceRailSwitchTest:74` constructs the service with four arguments; Step 3 makes it five. Add `credentialsResolver` there as a `@Mock` and stub `canPayFromOwnWallet` to return `true`, so that test keeps asserting what it was written to assert — the rail switch — rather than accidentally failing on the new company gate.

- [ ] **Step 2: Run it and confirm it fails**

Run: `./gradlew test --tests "com.tenxengage.app.service.DistributionRecipientCompanyConnectionTest"`
Expected: FAIL — the constructor takes four arguments, not five.

- [ ] **Step 3: Implement**

Add `XtrmCredentialsResolver` as a constructor dependency, before the `@Value` boolean. Then, at the top of `evaluate(...)`, for vendor rails only:

```java
    // A company that has not finished XTRM onboarding cannot pay anyone from its own wallet. This is a
    // property of the company rather than the seller, but it is reported per-seller so it shows up on the
    // listing, where an admin can act on it — not only at submit, after they have built the whole thing.
    if (rail.isVendorPayout() && !credentialsResolver.canPayFromOwnWallet(clientId, companyId)) {
        return Eligibility.no("Your company isn't connected to XTRM yet.");
    }
```

`evaluate` currently takes `(clientId, user, profile, rail)`. Thread `companyId` through both call sites in `listRecipients` and `assertAllEligible`.

- [ ] **Step 4: Add the identity-level gate (D-5)**

Spec §9 requires `AccountIdentityLevel` to be gated by a config property, **permissive by default**. Task 2 stores the level; this is the lever that reads it. It ships doing nothing, and the spec says so plainly — with an empty list every level passes, because we have observed exactly one value (`Basic`) and a rule inferred from one observation would block real companies on a guess.

Add to `XtrmCredentialsResolver`:

```java
/**
 * Identity levels XTRM will actually pay out from, e.g. {@code Verified,Advanced}.
 *
 * <p><b>Empty means no opinion, and empty is the default.</b> This is a configured lever, not a
 * protection — anything depending on identity level being enforced is depending on someone setting this
 * property. Tighten it when XTRM says which level clears payouts; no code change needed.</p>
 */
@Value("${redemption.xtrm.acceptable-identity-levels:}")
private String acceptableIdentityLevels;

/** True when this account's KYC tier is one we are willing to pay from. */
public boolean hasAcceptableIdentityLevel(PartnerCompanyXtrmAccount account) {
    if (acceptableIdentityLevels == null || acceptableIdentityLevels.isBlank()) {
        return true;
    }
    String level = account.getAccountIdentityLevel();
    return level != null && Arrays.stream(acceptableIdentityLevels.split(","))
            .map(String::trim)
            .anyMatch(allowed -> allowed.equalsIgnoreCase(level));
}
```

Fold it into `canPayFromOwnWallet` so there is one question, not two:

```java
@Transactional(readOnly = true)
public boolean canPayFromOwnWallet(UUID clientId, UUID partnerCompanyId) {
    return accountRepository.findByClientIdAndPartnerCompanyId(clientId, partnerCompanyId)
            .filter(PartnerCompanyXtrmAccount::isPayoutReady)
            .filter(this::hasAcceptableIdentityLevel)
            .isPresent();
}
```

Add to `application-local.yml` under `redemption.xtrm`:

```yaml
    # Empty = accept any KYC tier. Set once XTRM confirms which level clears payouts.
    acceptable-identity-levels: ${XTRM_ACCEPTABLE_IDENTITY_LEVELS:}
```

Add these cases to `XtrmCredentialsResolverTest`:

```java
@Test
void acceptsAnyIdentityLevelWhenTheGateIsUnset() {
    ReflectionTestUtils.setField(resolver, "acceptableIdentityLevels", "");
    PartnerCompanyXtrmAccount account = account(XtrmAccountStatus.CONNECTED, "blob");
    account.setAccountIdentityLevel("Basic");

    assertThat(resolver.hasAcceptableIdentityLevel(account)).isTrue();
}

@Test
void refusesALevelOutsideTheConfiguredSet() {
    ReflectionTestUtils.setField(resolver, "acceptableIdentityLevels", "Verified,Advanced");
    PartnerCompanyXtrmAccount account = account(XtrmAccountStatus.CONNECTED, "blob");
    account.setAccountIdentityLevel("Basic");

    assertThat(resolver.hasAcceptableIdentityLevel(account)).isFalse();
}

@Test
void acceptsAConfiguredLevelIgnoringCaseAndSpacing() {
    ReflectionTestUtils.setField(resolver, "acceptableIdentityLevels", "Verified, Advanced");
    PartnerCompanyXtrmAccount account = account(XtrmAccountStatus.CONNECTED, "blob");
    account.setAccountIdentityLevel("advanced");

    assertThat(resolver.hasAcceptableIdentityLevel(account)).isTrue();
}
```

Set `acceptableIdentityLevels` to `""` in that test's existing `setUp`, so every other case in the file keeps its current meaning.

- [ ] **Step 5: Run the test, then everything**

Run: `./gradlew test --tests "com.tenxengage.app.service.DistributionRecipientCompanyConnectionTest"`
Expected: PASS

Run: `./gradlew test`
Expected: PASS. `DistributionRecipientServiceRailSwitchTest` exists and pins the rail-switch promise — if it fails, you have changed behaviour it guards. Read it before assuming your change is right.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/tenxengage/app/service/DistributionRecipientService.java \
        src/main/java/com/tenxengage/app/service/xtrm/XtrmCredentialsResolver.java \
        src/main/resources/application-local.yml \
        src/test/java/com/tenxengage/app/service/DistributionRecipientCompanyConnectionTest.java \
        src/test/java/com/tenxengage/app/service/xtrm/XtrmCredentialsResolverTest.java
git commit -m "feat(distribution): tell an admin their company isn't connected, on the listing"
```

---

## Task 11: Update the shared API contract

The backend API is final as of Task 10. `../tenxengage-contracts/` is the shared source of truth for both teams, and it already describes this endpoint group — leaving it stale means the frontend tasks that follow are coding against a contract that no longer matches the server.

**Files (in the `tenxengage-contracts` repo, not the backend):**
- Modify: `endpoints/partner-companies.yaml` — `CreatePartnerCompanyRequest` (line ~134), `PartnerCompanyResponse` (line ~178), and the paths block (line ~8)
- Modify: `models/partner-company.md`

**Interfaces:**
- Consumes: the final shapes from Tasks 3 and 7.
- Produces: the contract the frontend tasks read.

- [ ] **Step 1: Read what is already there**

```bash
cd ../tenxengage-contracts
sed -n '130,210p' endpoints/partner-companies.yaml
```

Match the existing style — property ordering, `description` phrasing, how `nullable` and `maxLength` are expressed. Do not restructure the file.

- [ ] **Step 2: Add the eight admin properties to `CreatePartnerCompanyRequest`**

Mirror the record from Task 3 exactly: `adminFirstName`, `adminLastName`, `adminEmail`, `adminMobileNumber`, `adminCity`, `adminRegion`, `adminPostalCode`, `adminCountryIso2`. All optional, with `maxLength` matching the column widths in Task 2 (100/100/255/20/100/100/20/2).

Add a note on the group, because a reader cannot infer it from per-field optionality:

```yaml
# All eight admin properties are supplied together or not at all. A partial group is rejected with
# 422 INVALID_ADMIN_DETAILS — it cannot produce a beneficiary at the payment provider.
```

Apply the same eight to `UpdatePartnerCompanyRequest` if that schema exists in the file; if the file reuses one schema for both, leave it as one.

- [ ] **Step 3: Add `xtrmAccount` to `PartnerCompanyResponse`**

```yaml
xtrmAccount:
  type: object
  nullable: true
  description: >
    The company's connection to the payout provider. Null when the company has never been
    connected. Never contains credentials.
  properties:
    status:
      type: string
      enum: [PENDING, CONNECTED, DISABLED]
    accountNumber:
      type: string
      nullable: true
    identityLevel:
      type: string
      nullable: true
    lastError:
      type: string
      nullable: true
      description: Why the last connection attempt failed. Present only while PENDING.
```

- [ ] **Step 4: Add the connect endpoint to the paths block**

`POST /api/v1/partner-companies/{id}/xtrm/connect`, permission `action.partner_company.edit`, request body `ConnectXtrmAccountRequest` (the eight admin properties plus `xtrmWalletId`, every one optional), response `PartnerCompanyResponse`. Document that it is idempotent and that an already-connected company is returned unchanged.

- [ ] **Step 5: Update `models/partner-company.md`**

Add the admin fields and the XTRM account concept, in the style already used by that file.

- [ ] **Step 6: Commit — in the contracts repo**

```bash
cd ../tenxengage-contracts
git add endpoints/partner-companies.yaml models/partner-company.md
git commit -m "contract(partner-companies): company admin details and the XTRM account block"
```

Note this is a **separate repository** with its own branch state. Check what branch you are on before committing.

---

## Task 12: Extract the company form from `UserSettingsPage.tsx`

Spec §10, D-9. Mechanical, and it lands before the new fields so the diff for Task 13 is readable.

**Files:**
- Create: `src/components/settings/PartnerCompanyFormDialog.tsx`
- Modify: `src/pages/client-admin/UserSettingsPage.tsx`
- Test: `src/components/settings/__tests__/PartnerCompanyFormDialog.test.tsx`

**Interfaces:**
- Consumes: `CreatePartnerCompanyRequest`, `UpdatePartnerCompanyRequest`, `PartnerCompany` from `@/types/partner-company.types`.
- Produces:

```ts
export interface PartnerCompanyFormDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  mode: "create" | "edit";
  company?: PartnerCompany;
  onSubmit: (values: PartnerCompanyFormValues) => Promise<void>;
  isSubmitting: boolean;
}

export interface PartnerCompanyFormValues {
  name: string;
  externalPartnerId: string;
  locationValueIds: string[];
  partnerType: string;
  status: PartnerCompanyStatus;
  website: string;
  contactEmail: string;
  contactPhone: string;
}
```

- [ ] **Step 1: Read what you are extracting**

Run from the frontend worktree:
```bash
sed -n '1440,1530p' src/pages/client-admin/UserSettingsPage.tsx
grep -n "addPartnerCompanyOpen\|editPartnerCompanyOpen\|handleCreatePartnerCompany\|handleEditPartnerCompanySave\|openEditPartnerCompany" src/pages/client-admin/UserSettingsPage.tsx
```

Everything those hits touch moves. Nothing else does. This task must not change behaviour.

- [ ] **Step 2: Write the characterization test first**

Create `src/components/settings/__tests__/PartnerCompanyFormDialog.test.tsx`:

```tsx
import { describe, expect, it, vi } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { PartnerCompanyFormDialog } from "../PartnerCompanyFormDialog";

/**
 * Extraction must not change behaviour. These assertions describe what the form already does inside
 * UserSettingsPage, so a regression during the move fails here rather than in someone's manual testing.
 */
describe("PartnerCompanyFormDialog", () => {
  const baseProps = {
    open: true,
    onOpenChange: vi.fn(),
    mode: "create" as const,
    onSubmit: vi.fn().mockResolvedValue(undefined),
    isSubmitting: false,
  };

  it("renders the required company fields", () => {
    render(<PartnerCompanyFormDialog {...baseProps} />);

    expect(screen.getByLabelText(/name/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/partner id/i)).toBeInTheDocument();
  });

  it("submits the entered values", async () => {
    const onSubmit = vi.fn().mockResolvedValue(undefined);
    render(<PartnerCompanyFormDialog {...baseProps} onSubmit={onSubmit} />);

    fireEvent.change(screen.getByLabelText(/name/i), { target: { value: "Acme Corp" } });
    fireEvent.change(screen.getByLabelText(/partner id/i), { target: { value: "EXT-1" } });
    fireEvent.click(screen.getByRole("button", { name: /save|create|add/i }));

    await waitFor(() => expect(onSubmit).toHaveBeenCalled());
    expect(onSubmit.mock.calls[0][0]).toMatchObject({ name: "Acme Corp", externalPartnerId: "EXT-1" });
  });

  it("pre-fills in edit mode", () => {
    render(
      <PartnerCompanyFormDialog
        {...baseProps}
        mode="edit"
        company={{
          id: "1", name: "Acme Corp", externalPartnerId: "EXT-1", partnerType: "RESELLER",
          clientId: "c1", clientName: "Apple", status: "ACTIVE", activeUserCount: 0,
          locations: [], metadata: "{}", createdAt: "", updatedAt: "",
        }}
      />,
    );

    expect(screen.getByLabelText(/name/i)).toHaveValue("Acme Corp");
  });

  it("disables submit while a save is in flight", () => {
    render(<PartnerCompanyFormDialog {...baseProps} isSubmitting />);

    expect(screen.getByRole("button", { name: /save|create|add/i })).toBeDisabled();
  });
});
```

- [ ] **Step 3: Run it and confirm it fails**

Run: `npm test -- PartnerCompanyFormDialog`
Expected: FAIL — module not found.

- [ ] **Step 4: Move the form**

Create `src/components/settings/PartnerCompanyFormDialog.tsx`. Move — do not rewrite — the dialog JSX, its local form state, and the `toCreateRequest` mapper currently at `UserSettingsPage.tsx:1456`. Match the label text your test queries; if the existing labels differ, change the **test** to match the existing UI, not the UI to match the test. This task preserves behaviour.

- [ ] **Step 5: Use it from `UserSettingsPage.tsx`**

Replace both inline dialogs with two instances of the new component, keeping `handleCreatePartnerCompany` and `handleEditPartnerCompanySave` in the page as the `onSubmit` handlers. Delete the now-unused form state and the extracted mapper.

- [ ] **Step 6: Run the tests**

Run: `npm test -- PartnerCompanyFormDialog`
Expected: PASS

Run: `npm test`
Expected: PASS, no new failures. `UserSettingsPage` has existing tests; if any fail, the extraction changed behaviour — fix the extraction.

- [ ] **Step 7: Commit**

```bash
git add src/components/settings/PartnerCompanyFormDialog.tsx \
        src/components/settings/__tests__/PartnerCompanyFormDialog.test.tsx \
        src/pages/client-admin/UserSettingsPage.tsx
git commit -m "refactor(settings): extract the partner company form from UserSettingsPage"
```

---

## Task 13: Company admin fields on the form

Spec §10. Depends on Task 12 landing first.

**Files:**
- Modify: `src/types/partner-company.types.ts`
- Modify: `src/components/settings/PartnerCompanyFormDialog.tsx`
- Test: `src/components/settings/__tests__/PartnerCompanyFormDialog.admin.test.tsx`

**Interfaces:**
- Consumes: `PartnerCompanyFormValues` from Task 12.
- Produces: `PartnerCompanyFormValues` and both request types gain `adminFirstName`, `adminLastName`, `adminEmail`, `adminMobileNumber`, `adminCity`, `adminRegion`, `adminPostalCode`, `adminCountryIso2` — all `string`. `PartnerCompany` gains the same eight as optional, plus `xtrmAccount?: XtrmAccountSummary`.

- [ ] **Step 1: Extend the types**

In `src/types/partner-company.types.ts`:

```ts
export type XtrmAccountStatus = "PENDING" | "CONNECTED" | "DISABLED";

export interface XtrmAccountSummary {
  status: XtrmAccountStatus;
  accountNumber?: string;
  identityLevel?: string;
  lastError?: string;
}

export interface CompanyAdminDetails {
  adminFirstName?: string;
  adminLastName?: string;
  adminEmail?: string;
  adminMobileNumber?: string;
  adminCity?: string;
  adminRegion?: string;
  adminPostalCode?: string;
  adminCountryIso2?: string;
}
```

Add `CompanyAdminDetails` to `PartnerCompany` (plus `xtrmAccount?: XtrmAccountSummary`), `CreatePartnerCompanyRequest`, and `UpdatePartnerCompanyRequest` by extending each interface.

- [ ] **Step 2: Write the failing test**

Create `src/components/settings/__tests__/PartnerCompanyFormDialog.admin.test.tsx`:

```tsx
import { describe, expect, it, vi } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { PartnerCompanyFormDialog } from "../PartnerCompanyFormDialog";

/**
 * The admin block is all-or-nothing, and the server enforces the same rule. Catching it here means the
 * user is told which field is missing while they are still looking at the form, instead of a 422 landing
 * after they submit.
 */
describe("PartnerCompanyFormDialog — company admin", () => {
  const baseProps = {
    open: true,
    onOpenChange: vi.fn(),
    mode: "create" as const,
    onSubmit: vi.fn().mockResolvedValue(undefined),
    isSubmitting: false,
  };

  const fillRequired = () => {
    fireEvent.change(screen.getByLabelText(/^name/i), { target: { value: "Acme Corp" } });
    fireEvent.change(screen.getByLabelText(/partner id/i), { target: { value: "EXT-1" } });
  };

  it("submits with no admin details at all", async () => {
    const onSubmit = vi.fn().mockResolvedValue(undefined);
    render(<PartnerCompanyFormDialog {...baseProps} onSubmit={onSubmit} />);

    fillRequired();
    fireEvent.click(screen.getByRole("button", { name: /save|create|add/i }));

    await waitFor(() => expect(onSubmit).toHaveBeenCalled());
  });

  it("blocks submit when the admin block is half filled", async () => {
    const onSubmit = vi.fn().mockResolvedValue(undefined);
    render(<PartnerCompanyFormDialog {...baseProps} onSubmit={onSubmit} />);

    fillRequired();
    fireEvent.change(screen.getByLabelText(/admin email/i), { target: { value: "admin@acme.test" } });
    fireEvent.click(screen.getByRole("button", { name: /save|create|add/i }));

    await waitFor(() => expect(screen.getByText(/all company admin fields/i)).toBeInTheDocument());
    expect(onSubmit).not.toHaveBeenCalled();
  });

  it("submits a complete admin block", async () => {
    const onSubmit = vi.fn().mockResolvedValue(undefined);
    render(<PartnerCompanyFormDialog {...baseProps} onSubmit={onSubmit} />);

    fillRequired();
    fireEvent.change(screen.getByLabelText(/admin first name/i), { target: { value: "TestP" } });
    fireEvent.change(screen.getByLabelText(/admin last name/i), { target: { value: "Singh" } });
    fireEvent.change(screen.getByLabelText(/admin email/i), { target: { value: "admin@acme.test" } });
    fireEvent.change(screen.getByLabelText(/admin mobile/i), { target: { value: "4085556245" } });
    fireEvent.change(screen.getByLabelText(/city/i), { target: { value: "San Francisco" } });
    fireEvent.change(screen.getByLabelText(/region|state/i), { target: { value: "CA" } });
    fireEvent.change(screen.getByLabelText(/postal/i), { target: { value: "94105" } });
    fireEvent.change(screen.getByLabelText(/country/i), { target: { value: "US" } });
    fireEvent.click(screen.getByRole("button", { name: /save|create|add/i }));

    await waitFor(() => expect(onSubmit).toHaveBeenCalled());
    expect(onSubmit.mock.calls[0][0]).toMatchObject({ adminEmail: "admin@acme.test", adminCountryIso2: "US" });
  });
});
```

- [ ] **Step 3: Run it and confirm it fails**

Run: `npm test -- PartnerCompanyFormDialog.admin`
Expected: FAIL — no admin inputs exist.

- [ ] **Step 4: Add the section**

Add a **Company Admin** section to the dialog with the eight inputs, and this helper text under the heading:

> These details create the company's payout account with our payment provider. Leave them blank if this company won't be sending rewards.

Add the group validation before calling `onSubmit`:

```ts
const ADMIN_FIELDS = [
  "adminFirstName", "adminLastName", "adminEmail", "adminMobileNumber",
  "adminCity", "adminRegion", "adminPostalCode", "adminCountryIso2",
] as const;

const filled = ADMIN_FIELDS.filter((f) => (values[f] ?? "").trim().length > 0);

// All eight or none. A partial block is guaranteed to fail at the provider, and that failure arrives
// long after this dialog has closed.
if (filled.length > 0 && filled.length < ADMIN_FIELDS.length) {
  setAdminError("All company admin fields are required to connect this company for payouts.");
  return;
}
```

- [ ] **Step 5: Run the tests**

Run: `npm test -- PartnerCompanyFormDialog`
Expected: PASS, both files.

Run: `npm test`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add src/types/partner-company.types.ts \
        src/components/settings/PartnerCompanyFormDialog.tsx \
        src/components/settings/__tests__/PartnerCompanyFormDialog.admin.test.tsx
git commit -m "feat(settings): capture company admin details on the company form"
```

---

## Task 14: XTRM status and the Connect action

Spec §10.

**Files:**
- Create: `src/components/settings/XtrmAccountStatus.tsx`
- Modify: `src/services/partner-company.service.ts`
- Modify: `src/hooks/usePartnerCompanyApi.ts`
- Modify: `src/pages/client-admin/UserSettingsPage.tsx`
- Test: `src/components/settings/__tests__/XtrmAccountStatus.test.tsx`

**Interfaces:**
- Consumes: `XtrmAccountSummary` from Task 13.
- Produces:
  - `connectPartnerCompanyXtrm(id: string, body?: ConnectXtrmAccountRequest): Promise<PartnerCompany>` in the service.
  - `useConnectPartnerCompanyXtrm()` mutation hook.
  - `<XtrmAccountStatus account={...} onConnect={...} isConnecting={...} />`.

- [ ] **Step 1: Write the failing test**

Create `src/components/settings/__tests__/XtrmAccountStatus.test.tsx`:

```tsx
import { describe, expect, it, vi } from "vitest";
import { render, screen, fireEvent } from "@testing-library/react";
import { XtrmAccountStatus } from "../XtrmAccountStatus";

/**
 * The status row is the only place an admin can see why a company cannot send rewards, and the only way
 * to retry without a support ticket.
 */
describe("XtrmAccountStatus", () => {
  it("offers Connect when the company has no account", () => {
    const onConnect = vi.fn();
    render(<XtrmAccountStatus account={undefined} onConnect={onConnect} isConnecting={false} />);

    fireEvent.click(screen.getByRole("button", { name: /connect/i }));
    expect(onConnect).toHaveBeenCalled();
  });

  it("shows the failure reason and offers a retry when pending", () => {
    render(
      <XtrmAccountStatus
        account={{ status: "PENDING", lastError: "Could not reach XTRM" }}
        onConnect={vi.fn()}
        isConnecting={false}
      />,
    );

    expect(screen.getByText(/could not reach xtrm/i)).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /connect|retry/i })).toBeInTheDocument();
  });

  it("shows the account number and no action when connected", () => {
    render(
      <XtrmAccountStatus
        account={{ status: "CONNECTED", accountNumber: "SPN26241004", identityLevel: "Basic" }}
        onConnect={vi.fn()}
        isConnecting={false}
      />,
    );

    expect(screen.getByText(/SPN26241004/)).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /connect|retry/i })).not.toBeInTheDocument();
  });

  it("disables the action while connecting", () => {
    render(<XtrmAccountStatus account={{ status: "PENDING" }} onConnect={vi.fn()} isConnecting />);

    expect(screen.getByRole("button", { name: /connect|retry/i })).toBeDisabled();
  });
});
```

- [ ] **Step 2: Run it and confirm it fails**

Run: `npm test -- XtrmAccountStatus`
Expected: FAIL — module not found.

- [ ] **Step 3: Build the component**

Create `src/components/settings/XtrmAccountStatus.tsx` using the existing badge component the codebase already uses for statuses — find it with `grep -rn "status-and-badges\|<Badge" src/components/settings src/pages/client-admin | head`. Map `CONNECTED` to the success variant, `PENDING` to warning, `DISABLED` to muted. Render `lastError` only when present.

- [ ] **Step 4: Add the service call and hook**

In `src/services/partner-company.service.ts`, mirror the existing `updatePartnerCompany` call:

Match the file's existing conventions exactly: it imports `api` from `@/lib/axios` (not `apiClient`), uses `export async function`, and every response is wrapped in `ApiResponse<T>` — so the payload is `response.data.data`, one level deeper than it looks.

```ts
export async function connectPartnerCompanyXtrm(
  id: string,
  data: ConnectXtrmAccountRequest = {},
): Promise<PartnerCompany> {
  const response = await api.post<ApiResponse<PartnerCompany>>(
    `/partner-companies/${id}/xtrm/connect`,
    data,
  );
  return response.data.data;
}
```

Add `ConnectXtrmAccountRequest` to the type imports at the top of the file, and define it in `partner-company.types.ts` as `CompanyAdminDetails & { xtrmWalletId?: string }`.

In `src/hooks/usePartnerCompanyApi.ts`, add `useConnectPartnerCompanyXtrm` following the shape of `useUpdatePartnerCompany` — `useMutation` with `onSuccess: () => qc.invalidateQueries({ queryKey: ["partner-companies"] })`.

- [ ] **Step 5: Render it in the page**

Add the status row to the partner company detail area of `UserSettingsPage.tsx`, wired to the new hook.

- [ ] **Step 6: Run the tests**

Run: `npm test -- XtrmAccountStatus`
Expected: PASS

Run: `npm test`
Expected: PASS

- [ ] **Step 7: Commit**

```bash
git add src/components/settings/XtrmAccountStatus.tsx \
        src/components/settings/__tests__/XtrmAccountStatus.test.tsx \
        src/services/partner-company.service.ts \
        src/hooks/usePartnerCompanyApi.ts \
        src/pages/client-admin/UserSettingsPage.tsx
git commit -m "feat(settings): show a company's XTRM connection and let an admin connect it"
```

---

## After the plan

**Not included, deliberately.** Both are named in the spec as separate phases:

- **D-8 — funding must move money at XTRM.** `POST /wallets/company/{companyId}/fund` still credits the internal ledger only. Until `TransferFundToCompany` is wired into it, a company's XTRM wallet is empty however healthy our balance reads, and every company-remitted payout fails for insufficient funds. **Do not enable the XTRM rails in production before this lands.**
- **§8.1 — company→seller vendor authorization.** Still unresolved, and the deciding probe is unrun: `OTP/GetConnectedStatus` authenticated *as* a freshly created beneficiary company, against an existing seller PAT. Run it before scheduling any OTP work — the answer decides whether that work exists at all.

**Verification once the plan is complete**, in the `local` profile against the XTRM sandbox:

1. Create a partner company with full admin details. Confirm a `partner_company_xtrm_accounts` row reaches `CONNECTED` with an SPN, a wallet id, and a non-null `encrypted_credentials`.
2. Confirm the admin was **not** emailed (D-13).
3. `grep -ri "secretkey\|clientsecret" logs/tenxengage.log` — expect no hits.
4. Create a second company with the same name under a different tenant. Confirm §5.4's disambiguation holds.
5. Delete a provisioned company. Confirm it succeeds.

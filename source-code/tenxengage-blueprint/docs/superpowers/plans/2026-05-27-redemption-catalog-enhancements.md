# Redemption Catalog Enhancements Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Merge Global Catalog and Redemption Catalog into a Platform Settings tab, and enhance the catalog item form with image upload, currency dropdown from DB, and geographic scope multiselect from DB.

**Architecture:** Phase 1 is FE-only (navigation restructure). Phase 2 adds a `image_url` column to `redemption_catalog_items`, a new upload endpoint following the existing `BrandingController`/`FileStorageService` pattern, and replaces three static form fields with API-driven components.

**Tech Stack:** Java 21 / Spring Boot / JPA / Flyway (BE), React 18 / TypeScript / React Hook Form / Zod / React Query / shadcn/ui (FE), OpenAPI contracts (Contracts repo), Markdown specs (Blueprint repo)

---

## Pre-requisite — Sync branches in all 4 repos

Run this in each repo before starting any task:

```bash
git checkout features/redemption-catalog
git merge roadmaps/redemption-store
git push origin features/redemption-catalog
```

Repos: `tenxengage-blueprint`, `tenxengage-backend`, `tenxengage-frontend`, `tenxengage-contracts`

---

## PHASE 1 — Blueprint Amendments

### Task 1: Amend spec.md

**Repo:** `tenxengage-blueprint`
**Sub-branch:** `work/redemption-catalog-spec-amendments` off `features/redemption-catalog`

**Files:**
- Modify: `features/redemption-catalog/spec.md`

- [ ] **Step 1: Open spec.md and locate the `RedemptionCatalogItem` entity section**

Find the table or list that documents the entity fields.

- [ ] **Step 2: Add `image_url` field to the entity fields table**

Add this row to the `RedemptionCatalogItem` entity fields table (after `description`):

```markdown
| `image_url` | VARCHAR(2000) | nullable | URL or object key of the uploaded catalog item image (optional) |
```

- [ ] **Step 3: Add the image upload endpoint to the API Endpoints section**

In the Platform Admin endpoints table, add after the existing `PUT /{id}` row:

```markdown
| `POST /api/v1/admin/redemption-catalog/{id}/image` | Upload catalog item image | `action.redemption.catalog.manage` | `multipart/form-data` → `RedemptionCatalogItemResponse` |
```

- [ ] **Step 4: Update the create/update DTO section**

In `CreateRedemptionCatalogItemRequest` and `UpdateRedemptionCatalogItemRequest` DTO tables, add:

```markdown
| `imageUrl` | `String` | optional/nullable | Object key of uploaded image; null removes existing image |
```

- [ ] **Step 5: Add a note under Geographic Scope**

In the spec section describing `geographicScope`, add:

```markdown
> **FE note:** Geographic scope options are loaded from `GET /api/v1/location-levels` (returns tenant location hierarchy). Existing Xoxoday-synced items may have ISO codes not present in the tenant hierarchy — these are shown as read-only "From sync" chips in the form (removable but not re-selectable via picker).
```

- [ ] **Step 6: Add Amendments section at the top of spec.md**

Add immediately after the spec's front-matter / status block (before the first content section):

```markdown
## Amendments

| Date | Author | Description | Design doc |
|---|---|---|---|
| 2026-05-27 | Robert (review) | Navigation restructure: merged Global Catalog + Redemption Catalog into a single Platform Settings tab. Form enhancements: image upload, currency dropdown from DB, geographic scope multiselect from DB. | `docs/superpowers/specs/2026-05-27-redemption-catalog-enhancements-design.md` |
```

> This section is the permanent record linking spec changes back to the review that requested them. Future readers can open the design doc for full context and reasoning.

- [ ] **Step 7: Add navigation restructure note**

Add a new section or note at the top of the spec (after the Amendments section):

```markdown
### Navigation (FE)
"Global Catalog" and "Redemption Catalog" sidebar links are removed. Both are merged into a "Redemption Catalog" tab in Platform Settings (between "Manage Business Rules" and "Builder Config"). The tab has two sub-tabs: **Catalog Items** (global catalog management) and **Tenant Config** (tenant-level configuration).
```

- [ ] **Step 8: Commit**

```bash
git add features/redemption-catalog/spec.md
git commit -m "docs(spec): add amendments section, image_url field, upload endpoint, nav restructure note"
```

---

### Task 2: Amend technical.md

**Files:**
- Modify: `features/redemption-catalog/technical.md`

- [ ] **Step 1: Update the migration version table**

Find the migrations table or section. Add a new row for V18:

```markdown
| V18 | `ALTER TABLE redemption_catalog_items ADD COLUMN image_url VARCHAR(2000) NULL` | Adds optional image storage key to catalog items |
```

> **Note:** If approval-queue F2 has already used V18, use V19 instead. Check with `ls src/main/resources/db/migration/` after merging.

- [ ] **Step 2: Update the `RedemptionCatalogItem` entity fields table**

Add `imageUrl String` (nullable) to the entity fields list.

- [ ] **Step 3: Document the image upload service method**

Add under the service layer section:

```markdown
#### `uploadCatalogItemImage(UUID id, MultipartFile file)`
- Validates size ≤ 5 MB and MIME type (png/jpeg/webp)
- Deletes old image from storage if `imageUrl` is set
- Generates key: `catalog/{id}/image-{uuid}.{ext}`
- Calls `FileStorageService.upload(key, stream, size, contentType)`
- Stores returned object key in `imageUrl`, saves entity
```

- [ ] **Step 4: Commit**

```bash
git add features/redemption-catalog/technical.md
git commit -m "docs(technical): add V18 migration, imageUrl entity field, upload service method"
```

---

### Task 3: Amend US-01 story

**Files:**
- Modify: `features/redemption-catalog/stories/US-01-manage-global-catalog-items.md`

- [ ] **Step 1: Add navigation restructure FE tasks**

In the FE tasks section, add:

```markdown
#### FE-0: Navigation restructure (prerequisite, do before form tasks)
- Remove "Global Catalog" primary nav item from `sidebarConfigs.ts`
- Remove "Redemption Catalog" item from Settings section in `sidebarConfigs.ts`
- Add "Redemption Catalog" tab to `PlatformSettingsPage.tsx` (between business-rules and builder-config)
- Create `RedemptionCatalogTab.tsx` with "Catalog Items" and "Tenant Config" sub-tabs
- Update `App.tsx` routing — redirect `/admin/redemption-catalog` and `/settings/redemption/catalog` to `/settings/platform?tab=redemption-catalog`
```

- [ ] **Step 2: Add form enhancement FE tasks**

```markdown
#### FE-4: Currency dropdown
Replace static text `Input` for `currencyId` in `GlobalCatalogItemForm.tsx` with a shadcn `Select` loaded from `GET /api/v1/currencies`. Display: `{name} ({type})`. Save: `currency.code`.

#### FE-5: Geographic scope multiselect
Replace static `COUNTRY_OPTIONS` array with options loaded from `GET /api/v1/location-levels`. Regions shown as group labels; countries as selectable items. Unmatched codes (from Xoxoday sync) shown as read-only "From sync" chips. Save: array of `location_value.code` values.

#### FE-6: Image upload component
New component `CatalogImageUpload.tsx`. Optional file picker (png/jpeg/webp, ≤ 5 MB). In edit mode: calls `POST /api/v1/admin/redemption-catalog/{id}/image` immediately on file select. In create mode: upload triggered after `createCatalogItem` succeeds. Shows preview, progress state, Remove button.
```

- [ ] **Step 3: Commit**

```bash
git add features/redemption-catalog/stories/US-01-manage-global-catalog-items.md
git commit -m "docs(US-01): add nav restructure and form enhancement tasks"
```

- [ ] **Step 4: Squash-merge back to features/redemption-catalog, push**

```bash
git checkout features/redemption-catalog
git merge --squash work/redemption-catalog-spec-amendments
git commit -m "docs: amend redemption-catalog spec, technical, US-01 for Robert's review changes"
git push origin features/redemption-catalog
```

---

## PHASE 2 — Backend Changes

**Repo:** `tenxengage-backend`
**Sub-branch:** `work/redemption-catalog-form-be` off `features/redemption-catalog`

```bash
git checkout features/redemption-catalog
git checkout -b work/redemption-catalog-form-be
```

---

### Task 4: Flyway Migration

**Files:**
- Create: `src/main/resources/db/migration/V18__add_image_url_to_catalog_items.sql`

- [ ] **Step 1: Check current highest migration version**

```bash
ls src/main/resources/db/migration/ | sort
```

If V18 already exists (from approval-queue), use V19 for this file.

- [ ] **Step 2: Create the migration file**

```sql
-- V18__add_image_url_to_catalog_items.sql
ALTER TABLE redemption_catalog_items
    ADD COLUMN image_url VARCHAR(2000) NULL;
```

- [ ] **Step 3: Run migrations to verify**

```bash
./gradlew flywayMigrate -Pspring.profiles.active=local
```

Expected: `Successfully applied 1 migration to schema "public"` (no errors)

- [ ] **Step 4: Commit**

```bash
git add src/main/resources/db/migration/V18__add_image_url_to_catalog_items.sql
git commit -m "db: add image_url column to redemption_catalog_items (V18)"
```

---

### Task 5: Entity and DTO Updates

**Files:**
- Modify: `src/main/java/com/tenxengage/app/entity/RedemptionCatalogItem.java`
- Modify: `src/main/java/com/tenxengage/app/dto/response/RedemptionCatalogItemResponse.java`
- Modify: `src/main/java/com/tenxengage/app/dto/request/CreateRedemptionCatalogItemRequest.java`
- Modify: `src/main/java/com/tenxengage/app/dto/request/UpdateRedemptionCatalogItemRequest.java`

- [ ] **Step 1: Add `imageUrl` to `RedemptionCatalogItem` entity**

In `RedemptionCatalogItem.java`, add after the `syncMetadata` field:

```java
@Column(name = "image_url", length = 2000)
private String imageUrl;
```

- [ ] **Step 2: Add `imageUrl` to `RedemptionCatalogItemResponse`**

Replace the current record definition:

```java
public record RedemptionCatalogItemResponse(
        UUID id,
        String name,
        String description,
        RedemptionCategory category,
        String currencyId,
        BigDecimal defaultMinRedemptionAmount,
        RedemptionProcessingMode defaultProcessingMode,
        String[] geographicScope,
        String providerItemId,
        boolean isReturnable,
        int defaultReturnWindowDays,
        boolean isActive,
        String imageUrl,        // ← add this
        Instant createdAt,
        Instant updatedAt
) {
    public static RedemptionCatalogItemResponse from(RedemptionCatalogItem item) {
        return new RedemptionCatalogItemResponse(
                item.getId(),
                item.getName(),
                item.getDescription(),
                item.getCategory(),
                item.getCurrencyId(),
                item.getDefaultMinRedemptionAmount(),
                item.getDefaultProcessingMode(),
                item.getGeographicScope(),
                item.getProviderItemId(),
                item.isReturnable(),
                item.getDefaultReturnWindowDays(),
                item.isActive(),
                item.getImageUrl(),        // ← add this
                item.getCreatedAt(),
                item.getUpdatedAt()
        );
    }
}
```

- [ ] **Step 3: Add `imageUrl` to `CreateRedemptionCatalogItemRequest`**

Add at the end of the record's parameter list:

```java
@Size(max = 2000) String imageUrl
```

- [ ] **Step 4: Add `imageUrl` to `UpdateRedemptionCatalogItemRequest`**

Add at the end of the record's parameter list:

```java
@JsonInclude(JsonInclude.Include.ALWAYS)
@Size(max = 2000) String imageUrl
```

> `@JsonInclude(ALWAYS)` ensures `null` is serialised — distinguishes "remove image" from "field not sent". Add `import com.fasterxml.jackson.annotation.JsonInclude;` to imports.

- [ ] **Step 5: Update `createCatalogItem` service method to map `imageUrl`**

In `RedemptionCatalogAdminService.createCatalogItem()`, in the `RedemptionCatalogItem.builder()` block, add:

```java
.imageUrl(request.imageUrl())
```

- [ ] **Step 6: Inject `FileStorageService` into `RedemptionCatalogAdminService`**

The next step uses `fileStorageService` — inject it now so the code compiles.

Add field and constructor parameter to `RedemptionCatalogAdminService.java`:

```java
private final FileStorageService fileStorageService;

// Update existing constructor to add the new parameter:
public RedemptionCatalogAdminService(RedemptionCatalogItemRepository catalogItemRepository,
                                      ClientCatalogRegionConfigRepository regionConfigRepository,
                                      XoxodaySyncJobService syncJobService,
                                      FileStorageService fileStorageService) {
    this.catalogItemRepository = catalogItemRepository;
    this.regionConfigRepository = regionConfigRepository;
    this.syncJobService = syncJobService;
    this.fileStorageService = fileStorageService;
}
```

> If the service already uses `@RequiredArgsConstructor` (Lombok), just add the field — Lombok generates the constructor automatically.

- [ ] **Step 7: Update `updateCatalogItem` service method to map `imageUrl`**

In `RedemptionCatalogAdminService.updateCatalogItem()`, find where the entity fields are updated from the request, and add:

```java
// imageUrl is always applied from request: null = remove image, non-null = set/replace
if (request.imageUrl() == null && item.getImageUrl() != null) {
    fileStorageService.delete(item.getImageUrl());
}
item.setImageUrl(request.imageUrl());
```

- [ ] **Step 8: Fix any existing tests that construct `RedemptionCatalogItemResponse` directly**

`RedemptionCatalogItemResponse` is a Java record — adding `imageUrl` changes the constructor arity. Any existing test that calls `new RedemptionCatalogItemResponse(id, name, ...)` with positional args will fail to compile. Search for these and add `null` as the 13th argument (imageUrl, before `createdAt`):

```bash
grep -r "new RedemptionCatalogItemResponse(" src/test/
```

For each match, insert `null,` before the `createdAt` argument.

Then run:

```bash
./gradlew test --tests "com.tenxengage.app.controller.RedemptionCatalogAdminControllerTest"
./gradlew test --tests "com.tenxengage.app.service.RedemptionCatalogAdminServiceTest"
```

Expected: All tests compile and pass.

- [ ] **Step 9: Commit**

```bash
git add src/main/java/com/tenxengage/app/entity/RedemptionCatalogItem.java \
        src/main/java/com/tenxengage/app/dto/response/RedemptionCatalogItemResponse.java \
        src/main/java/com/tenxengage/app/dto/request/CreateRedemptionCatalogItemRequest.java \
        src/main/java/com/tenxengage/app/dto/request/UpdateRedemptionCatalogItemRequest.java \
        src/main/java/com/tenxengage/app/service/RedemptionCatalogAdminService.java
git commit -m "feat: add imageUrl field to RedemptionCatalogItem entity and DTOs"
```

---

### Task 6: Image Upload Service Method

**Files:**
- Modify: `src/main/java/com/tenxengage/app/service/RedemptionCatalogAdminService.java`
- Test: `src/test/java/com/tenxengage/app/service/RedemptionCatalogAdminServiceTest.java`

- [ ] **Step 1: Write failing tests**

In `RedemptionCatalogAdminServiceTest.java`, add:

```java
@Test
void uploadCatalogItemImage_validFile_savesKeyAndReturnsResponse() throws Exception {
    UUID id = UUID.randomUUID();
    RedemptionCatalogItem item = RedemptionCatalogItem.builder()
            .name("Test Item").category(RedemptionCategory.NON_CASH)
            .currencyId("points").defaultMinRedemptionAmount(BigDecimal.TEN)
            .defaultProcessingMode(RedemptionProcessingMode.INSTANT)
            .geographicScope(new String[0]).isActive(true).build();
    item.setId(id);

    MockMultipartFile file = new MockMultipartFile(
            "file", "image.png", "image/png", new byte[1024]);

    // Note: item.setId(id) requires a public setId() on BaseEntity.
    // If BaseEntity uses @Getter only (no @Setter), replace with:
    //   ReflectionTestUtils.setField(item, "id", id);
    when(catalogItemRepository.findById(id)).thenReturn(Optional.of(item));
    when(catalogItemRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    RedemptionCatalogItemResponse response = adminService.uploadCatalogItemImage(id, file);

    assertThat(response.imageUrl()).startsWith("catalog/" + id + "/image-");
    assertThat(response.imageUrl()).endsWith(".png");
    verify(fileStorageService).upload(startsWith("catalog/" + id + "/image-"),
            any(), eq(1024L), eq("image/png"));
}

@Test
void uploadCatalogItemImage_replaceExisting_deletesOldKey() throws Exception {
    UUID id = UUID.randomUUID();
    RedemptionCatalogItem item = RedemptionCatalogItem.builder()
            .name("Test").category(RedemptionCategory.NON_CASH)
            .currencyId("points").defaultMinRedemptionAmount(BigDecimal.TEN)
            .defaultProcessingMode(RedemptionProcessingMode.INSTANT)
            .geographicScope(new String[0]).isActive(true).build();
    item.setId(id);
    item.setImageUrl("catalog/" + id + "/image-old.jpg");

    MockMultipartFile file = new MockMultipartFile(
            "file", "new.png", "image/png", new byte[512]);

    when(catalogItemRepository.findById(id)).thenReturn(Optional.of(item));
    when(catalogItemRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    adminService.uploadCatalogItemImage(id, file);

    verify(fileStorageService).delete("catalog/" + id + "/image-old.jpg");
}

@Test
void uploadCatalogItemImage_oversizedFile_throws400() {
    UUID id = UUID.randomUUID();
    byte[] bigFile = new byte[6 * 1024 * 1024]; // 6 MB
    MockMultipartFile file = new MockMultipartFile("file", "big.png", "image/png", bigFile);
    when(catalogItemRepository.findById(id)).thenReturn(Optional.of(
            RedemptionCatalogItem.builder().name("x").category(RedemptionCategory.NON_CASH)
                    .currencyId("p").defaultMinRedemptionAmount(BigDecimal.ONE)
                    .defaultProcessingMode(RedemptionProcessingMode.INSTANT)
                    .geographicScope(new String[0]).isActive(true).build()));

    assertThatThrownBy(() -> adminService.uploadCatalogItemImage(id, file))
            .isInstanceOf(ResponseStatusException.class)
            .extracting(e -> ((ResponseStatusException) e).getStatusCode())
            .isEqualTo(HttpStatus.BAD_REQUEST);
}

@Test
void uploadCatalogItemImage_unsupportedMimeType_throws400() {
    UUID id = UUID.randomUUID();
    MockMultipartFile file = new MockMultipartFile("file", "doc.pdf", "application/pdf", new byte[100]);
    when(catalogItemRepository.findById(id)).thenReturn(Optional.of(
            RedemptionCatalogItem.builder().name("x").category(RedemptionCategory.NON_CASH)
                    .currencyId("p").defaultMinRedemptionAmount(BigDecimal.ONE)
                    .defaultProcessingMode(RedemptionProcessingMode.INSTANT)
                    .geographicScope(new String[0]).isActive(true).build()));

    assertThatThrownBy(() -> adminService.uploadCatalogItemImage(id, file))
            .isInstanceOf(ResponseStatusException.class)
            .extracting(e -> ((ResponseStatusException) e).getStatusCode())
            .isEqualTo(HttpStatus.BAD_REQUEST);
}
```

- [ ] **Step 2: Run tests to confirm they fail**

```bash
./gradlew test --tests "com.tenxengage.app.service.RedemptionCatalogAdminServiceTest.uploadCatalogItemImage*"
```

Expected: Compilation error or test failures — method does not exist yet.

- [ ] **Step 3: Implement `uploadCatalogItemImage`**

Add to `RedemptionCatalogAdminService.java`:

```java
private static final long MAX_IMAGE_BYTES = 5L * 1024 * 1024;
private static final List<String> ALLOWED_TYPES = List.of("image/png", "image/jpeg", "image/webp");

@Transactional
public RedemptionCatalogItemResponse uploadCatalogItemImage(UUID id, MultipartFile file) {
    validateImageFile(file);

    RedemptionCatalogItem item = catalogItemRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Catalog item not found: " + id));

    if (item.getImageUrl() != null) {
        fileStorageService.delete(item.getImageUrl());
    }

    String ext = resolveExtension(Objects.requireNonNull(file.getContentType()));
    String key = "catalog/" + id + "/image-" + UUID.randomUUID() + "." + ext;

    try (InputStream stream = file.getInputStream()) {
        fileStorageService.upload(key, stream, file.getSize(), file.getContentType());
    } catch (IOException e) {
        throw new StorageException("Failed to upload catalog image", e);
    }

    item.setImageUrl(key);
    return RedemptionCatalogItemResponse.from(catalogItemRepository.save(item));
}

private void validateImageFile(MultipartFile file) {
    if (file.isEmpty()) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "File must not be empty");
    }
    if (file.getSize() > MAX_IMAGE_BYTES) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "File exceeds 5 MB limit");
    }
    if (!ALLOWED_TYPES.contains(file.getContentType())) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Unsupported type. Allowed: image/png, image/jpeg, image/webp");
    }
}

private String resolveExtension(String contentType) {
    return switch (contentType) {
        case "image/png" -> "png";
        case "image/jpeg" -> "jpg";
        case "image/webp" -> "webp";
        default -> throw new IllegalArgumentException("Unsupported: " + contentType);
    };
}
```

Add imports: `java.io.IOException`, `java.io.InputStream`, `java.util.List`, `java.util.Objects`, `com.tenxengage.app.service.FileStorageService`, `org.springframework.web.multipart.MultipartFile`.

> **Verify:** Check if `StorageException` exists at `com.tenxengage.app.exception.StorageException`. If it does, add the import. If not, replace `throw new StorageException(...)` with `throw new RuntimeException("Failed to upload catalog image", e)` or use whatever exception the `BrandingController` uses for storage failures.

- [ ] **Step 4: Run tests to confirm they pass**

```bash
./gradlew test --tests "com.tenxengage.app.service.RedemptionCatalogAdminServiceTest.uploadCatalogItemImage*"
```

Expected: 4 tests pass.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/tenxengage/app/service/RedemptionCatalogAdminService.java \
        src/test/java/com/tenxengage/app/service/RedemptionCatalogAdminServiceTest.java
git commit -m "feat: add uploadCatalogItemImage service method with validation"
```

---

### Task 7: Controller Endpoint

**Files:**
- Modify: `src/main/java/com/tenxengage/app/controller/RedemptionCatalogAdminController.java`
- Test: `src/test/java/com/tenxengage/app/controller/RedemptionCatalogAdminControllerTest.java`

- [ ] **Step 1: Write failing @WebMvcTest**

In `RedemptionCatalogAdminControllerTest.java`, add:

```java
@Test
void uploadCatalogItemImage_validFile_returns200() throws Exception {
    UUID id = UUID.randomUUID();
    RedemptionCatalogItemResponse mockResponse = new RedemptionCatalogItemResponse(
            id, "Test", null, RedemptionCategory.NON_CASH, "points",
            BigDecimal.TEN, RedemptionProcessingMode.INSTANT,
            new String[0], null, false, 0, true,
            "catalog/" + id + "/image-abc.png",
            Instant.now(), Instant.now());

    when(adminService.uploadCatalogItemImage(eq(id), any(MultipartFile.class)))
            .thenReturn(mockResponse);

    MockMultipartFile file = new MockMultipartFile("file", "img.png", "image/png", new byte[100]);

    mockMvc.perform(multipart("/api/v1/admin/redemption-catalog/" + id + "/image")
                    .file(file)
                    .with(jwt().authorities(new SimpleGrantedAuthority("action.redemption.catalog.manage"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.imageUrl").value("catalog/" + id + "/image-abc.png"));
}

@Test
void uploadCatalogItemImage_missingPermission_returns403() throws Exception {
    MockMultipartFile file = new MockMultipartFile("file", "img.png", "image/png", new byte[100]);
    mockMvc.perform(multipart("/api/v1/admin/redemption-catalog/" + UUID.randomUUID() + "/image")
                    .file(file)
                    .with(jwt()))
            .andExpect(status().isForbidden());
}
```

- [ ] **Step 2: Run tests to confirm they fail**

```bash
./gradlew test --tests "com.tenxengage.app.controller.RedemptionCatalogAdminControllerTest.uploadCatalogItemImage*"
```

Expected: FAIL — endpoint does not exist.

- [ ] **Step 3: Add import for MultipartFile and MediaType**

In `RedemptionCatalogAdminController.java`, ensure these imports exist:

```java
import org.springframework.http.MediaType;
import org.springframework.web.multipart.MultipartFile;
```

- [ ] **Step 4: Add the endpoint method**

In `RedemptionCatalogAdminController.java`, add after the `updateCatalogItem` method:

```java
@PostMapping(value = "/{id}/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
@Operation(summary = "Upload image for a catalog item")
@RequiresPermission("action.redemption.catalog.manage")
@Audited(action = "UPLOADED", resourceType = "REDEMPTION_CATALOG_ITEM",
        resourceId = "#id.toString()", description = "Uploaded catalog item image")
public ResponseEntity<RedemptionCatalogItemResponse> uploadCatalogItemImage(
        @PathVariable UUID id,
        @RequestParam("file") MultipartFile file) {
    return ResponseEntity.ok(adminService.uploadCatalogItemImage(id, file));
}
```

> **Verify `@Audited` types:** Other controllers use `AuditAction.APPROVED` and `AuditResourceType.REDEMPTION_REQUEST` (enum constants). Check if `AuditAction.UPLOADED` and `AuditResourceType.REDEMPTION_CATALOG_ITEM` exist. If the enums don't have these values, either add them or use the closest existing constant and note it.

> **Verify response wrapper:** The test asserts `jsonPath("$.data.imageUrl")`. This assumes the app wraps all responses in `{ data: ... }` via a `ResponseBodyAdvice`. Check existing controller tests to confirm the path — if the response is returned directly, use `jsonPath("$.imageUrl")` instead.

- [ ] **Step 5: Run tests to confirm they pass**

```bash
./gradlew test --tests "com.tenxengage.app.controller.RedemptionCatalogAdminControllerTest.uploadCatalogItemImage*"
```

Expected: 2 tests pass.

- [ ] **Step 6: Run full test suite**

```bash
./gradlew test
```

Expected: All existing tests pass.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/tenxengage/app/controller/RedemptionCatalogAdminController.java \
        src/test/java/com/tenxengage/app/controller/RedemptionCatalogAdminControllerTest.java
git commit -m "feat: POST /admin/redemption-catalog/{id}/image — catalog item image upload endpoint"
```

- [ ] **Step 8: Squash-merge back to features/redemption-catalog, push**

```bash
git checkout features/redemption-catalog
git merge --squash work/redemption-catalog-form-be
git commit -m "feat(BE): add image_url to catalog items — migration V18, entity, DTOs, upload endpoint"
git push origin features/redemption-catalog
```

---

## PHASE 3 — Contracts

**Repo:** `tenxengage-contracts`
**Branch:** `features/redemption-catalog` (already synced in pre-requisite)

- [ ] **Step 1: Regenerate contracts**

```bash
cd ../tenxengage-contracts
# ensure on features/redemption-catalog
git branch --show-current
```

Run the generate command from the blueprint repo:

```bash
cd ../tenxengage-blueprint
/generate-contracts redemption-catalog
```

- [ ] **Step 2: Verify the generated diff**

```bash
cd ../tenxengage-contracts
git diff
```

Expected diff includes:
- `imageUrl` added to `RedemptionCatalogItemResponse` schema (nullable string)
- `imageUrl` added to `CreateRedemptionCatalogItemRequest` (optional string)
- `imageUrl` added to `UpdateRedemptionCatalogItemRequest` (optional string)
- `POST /api/v1/admin/redemption-catalog/{id}/image` endpoint added with `multipart/form-data` request body

- [ ] **Step 3: Commit and push**

```bash
git add .
git commit -m "contracts: add imageUrl field and image upload endpoint for redemption-catalog"
git push origin features/redemption-catalog
```

---

## PHASE 4 — FE Navigation Restructure

**Repo:** `tenxengage-frontend`
**Sub-branch:** `work/redemption-catalog-nav-restructure` off `features/redemption-catalog`

```bash
git checkout features/redemption-catalog
git checkout -b work/redemption-catalog-nav-restructure
```

---

### Task 8: Remove Sidebar Links

**Files:**
- Modify: `src/components/layout/sidebars/sidebarConfigs.ts`

- [ ] **Step 1: Remove "Global Catalog" from primaryItems**

In `sidebarConfigs.ts`, delete this block from `primaryItems`:

```ts
{
  to: "/admin/redemption-catalog",
  icon: Package,
  label: "Global Catalog",
  permissionKey: "action.redemption.catalog.manage",
},
```

- [ ] **Step 2: Remove "Redemption Catalog" from the Settings section**

In the `sections` array under `Configuration > Settings > items`, delete:

```ts
{
  to: "/settings/redemption/catalog",
  icon: Tag,
  label: "Redemption Catalog",
  permissionKey: "action.redemption.configure",
},
```

- [ ] **Step 3: Remove unused icon imports if no longer needed**

Check if `Package` and `Tag` are still used elsewhere in the file. If not, remove from the lucide-react import at the top.

- [ ] **Step 4: Run the app and verify sidebar**

```bash
npm run dev
```

Navigate to the client admin portal. Confirm "Global Catalog" and "Redemption Catalog" no longer appear in the sidebar.

- [ ] **Step 5: Commit**

```bash
git add src/components/layout/sidebars/sidebarConfigs.ts
git commit -m "feat: remove Global Catalog and Redemption Catalog sidebar links"
```

---

### Task 9: Add Redemption Catalog Tab to Platform Settings

**Files:**
- Create: `src/components/settings/RedemptionCatalogTab.tsx`
- Modify: `src/pages/client-admin/PlatformSettingsPage.tsx`

- [ ] **Step 1: Create `RedemptionCatalogTab.tsx`**

```tsx
// src/components/settings/RedemptionCatalogTab.tsx
import { Package, Tag } from "lucide-react";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import GlobalCatalogAdminPage from "@/pages/tenx-admin/GlobalCatalogAdminPage";
import CatalogConfigPage from "@/pages/client-admin/CatalogConfigPage";

export function RedemptionCatalogTab() {
  return (
    <Card className="border-dashed">
      <CardHeader className="pb-3">
        <div className="flex items-center gap-2">
          <Package className="h-5 w-5 text-muted-foreground" />
          <CardTitle className="text-foreground">Redemption Catalog</CardTitle>
        </div>
        <CardDescription>
          Manage global catalog items and configure tenant-level availability.
        </CardDescription>
      </CardHeader>
      <CardContent>
        <Tabs defaultValue="catalog-items" className="space-y-4">
          <TabsList>
            <TabsTrigger value="catalog-items" className="gap-2 text-xs">
              <Package className="h-3.5 w-3.5" />
              Catalog Items
            </TabsTrigger>
            <TabsTrigger value="tenant-config" className="gap-2 text-xs">
              <Tag className="h-3.5 w-3.5" />
              Tenant Config
            </TabsTrigger>
          </TabsList>
          <TabsContent value="catalog-items">
            <GlobalCatalogAdminPage />
          </TabsContent>
          <TabsContent value="tenant-config">
            <CatalogConfigPage />
          </TabsContent>
        </Tabs>
      </CardContent>
    </Card>
  );
}
```

- [ ] **Step 2: Add the tab to `PlatformSettingsPage.tsx`**

Add the import at the top:

```tsx
import { RedemptionCatalogTab } from "@/components/settings/RedemptionCatalogTab";
import { ShoppingBag } from "lucide-react";
```

In `TabsList`, add after the `business-rules` trigger and before `builder-config`:

```tsx
<TabsTrigger value="redemption-catalog" className="gap-2">
  <ShoppingBag className="h-4 w-4" />
  Redemption Catalog
</TabsTrigger>
```

Add the `TabsContent` after the `business-rules` content block:

```tsx
<TabsContent value="redemption-catalog" className="mt-4">
  <RedemptionCatalogTab />
</TabsContent>
```

- [ ] **Step 3: Run the app and verify**

```bash
npm run dev
```

Navigate to Platform Settings. Confirm the "Redemption Catalog" tab appears between "Manage Business Rules" and "Builder Config". Click it — "Catalog Items" and "Tenant Config" sub-tabs should render their existing pages.

- [ ] **Step 4: Commit**

```bash
git add src/components/settings/RedemptionCatalogTab.tsx \
        src/pages/client-admin/PlatformSettingsPage.tsx
git commit -m "feat: add Redemption Catalog tab to Platform Settings with Catalog Items and Tenant Config sub-tabs"
```

---

### Task 10: Update Routing

**Files:**
- Modify: `src/App.tsx`

- [ ] **Step 1: Add redirects for old routes**

In `App.tsx`, find where `/admin/redemption-catalog` and `/settings/redemption/catalog` routes are defined. Replace (or add alongside) with redirects:

```tsx
import { Navigate } from "react-router-dom";

// Replace the old route definitions with:
{ path: "/admin/redemption-catalog", element: <Navigate to="/settings/platform?tab=redemption-catalog" replace /> },
{ path: "/settings/redemption/catalog", element: <Navigate to="/settings/platform?tab=redemption-catalog" replace /> },
```

> **Verify:** Check `PlatformSettingsPage.tsx` — if the shadcn `<Tabs>` component reads `?tab=` from `useSearchParams()`, the redirect will land directly on the Redemption Catalog tab. If it uses a fixed `defaultValue` and doesn't read the query param, add that query-param support in Task 9 (read `useSearchParams().get("tab")` and pass as `value` to `<Tabs>`). Either way the `?tab=redemption-catalog` in the URL is the right target.

If the old routes rendered page components directly, the page components still exist (they're now rendered inside `RedemptionCatalogTab`) — don't delete them.

- [ ] **Step 2: Run the app and test redirects**

```bash
npm run dev
```

Navigate directly to `/admin/redemption-catalog` — should redirect to `/settings/platform`. Same for `/settings/redemption/catalog`.

- [ ] **Step 3: Run existing tests**

```bash
npm run test
```

Expected: All existing tests pass.

- [ ] **Step 4: Commit**

```bash
git add src/App.tsx
git commit -m "feat: redirect /admin/redemption-catalog and /settings/redemption/catalog to /settings/platform"
```

- [ ] **Step 5: Squash-merge back to features/redemption-catalog, push**

```bash
git checkout features/redemption-catalog
git merge --squash work/redemption-catalog-nav-restructure
git commit -m "feat(FE): navigation restructure — merge Global Catalog and Redemption Catalog into Platform Settings tab"
git push origin features/redemption-catalog
```

---

## PHASE 5 — FE Form Enhancements

**Sub-branch:** `work/redemption-catalog-form-fe` off `features/redemption-catalog`

```bash
git checkout features/redemption-catalog
git checkout -b work/redemption-catalog-form-fe
```

---

### Task 11: Update FE Types and Service

**Files:**
- Modify: `src/types/redemption-catalog.types.ts`
- Modify: `src/services/redemption-catalog-admin.service.ts`

- [ ] **Step 1: Add `imageUrl` to FE types**

In `redemption-catalog.types.ts`:

Add to `RedemptionCatalogItemResponse`:
```ts
imageUrl?: string;
```

Add to `CreateRedemptionCatalogItemRequest`:
```ts
imageUrl?: string;
```

Add to `UpdateRedemptionCatalogItemRequest`:
```ts
imageUrl?: string | null;  // null = explicitly remove image
```

- [ ] **Step 2: Add `uploadCatalogItemImage` to service**

In `redemption-catalog-admin.service.ts`, add:

```ts
export async function uploadCatalogItemImage(
  id: string,
  file: File,
): Promise<RedemptionCatalogItemResponse> {
  const formData = new FormData();
  formData.append("file", file);
  // Do NOT set Content-Type manually — axios detects FormData and sets
  // multipart/form-data with the correct boundary automatically
  const response = await api.post<ApiResponse<RedemptionCatalogItemResponse>>(
    `${BASE}/${id}/image`,
    formData,
  );
  return response.data.data;
}
```

- [ ] **Step 3: Commit**

```bash
git add src/types/redemption-catalog.types.ts \
        src/services/redemption-catalog-admin.service.ts
git commit -m "feat: add imageUrl to catalog types and uploadCatalogItemImage service function"
```

---

### Task 12: Currency Dropdown

**Files:**
- Modify: `src/components/redemption-catalog/GlobalCatalogItemForm.tsx`
- Test: `src/components/redemption-catalog/__tests__/GlobalCatalogItemForm.test.tsx`

- [ ] **Step 1: Write failing test**

In `GlobalCatalogItemForm.test.tsx`, add:

```tsx
it("renders currency options from API instead of static input", async () => {
  const currencies = [
    { id: "1", code: "USD", name: "US Dollar", type: "MONETARY" },
    { id: "2", code: "POINTS", name: "Points", type: "NON_MONETARY" },
  ];
  server.use(
    http.get("/api/v1/currencies", () => HttpResponse.json({ data: currencies })),
  );

  render(<GlobalCatalogItemForm onSave={vi.fn()} />);

  await screen.findByRole("combobox", { name: /currency/i });
  await userEvent.click(screen.getByRole("combobox", { name: /currency/i }));

  expect(screen.getByText("US Dollar (MONETARY)")).toBeInTheDocument();
  expect(screen.getByText("Points (NON_MONETARY)")).toBeInTheDocument();
  // Static text input should not be present
  expect(screen.queryByPlaceholderText("e.g. cash, points")).not.toBeInTheDocument();
});
```

- [ ] **Step 2: Run test to confirm it fails**

```bash
npm run test -- GlobalCatalogItemForm
```

Expected: FAIL — still renders the text input.

- [ ] **Step 3: Add currency query to `GlobalCatalogItemForm.tsx`**

Add import at top:
```tsx
import { useQuery } from "@tanstack/react-query";
import api from "@/lib/axios";
```

Add inside component body (before return):
```tsx
const { data: currenciesData } = useQuery({
  queryKey: ["currencies"],
  queryFn: async () => {
    const res = await api.get<{ data: Array<{ id: string; code: string; name: string; type: string }> }>(
      "/api/v1/currencies"
    );
    return res.data.data;
  },
});
const currencyOptions = currenciesData ?? [];
```

- [ ] **Step 4: Replace the `currencyId` Input with a Select**

Remove:
```tsx
<Label htmlFor="currencyId">Currency *</Label>
<Input id="currencyId" {...register("currencyId")} placeholder="e.g. cash, points" />
{errors.currencyId && <p className="text-sm text-destructive">{errors.currencyId.message}</p>}
```

Replace with:
```tsx
<Label htmlFor="currencyId">Currency *</Label>
<Select
  value={watch("currencyId")}
  onValueChange={(v) => setValue("currencyId", v)}
>
  <SelectTrigger id="currencyId">
    <SelectValue placeholder="Select currency" />
  </SelectTrigger>
  <SelectContent>
    {currencyOptions.map((c) => (
      <SelectItem key={c.id} value={c.code}>
        {c.name} ({c.type})
      </SelectItem>
    ))}
  </SelectContent>
</Select>
{errors.currencyId && <p className="text-sm text-destructive">{errors.currencyId.message}</p>}
```

- [ ] **Step 5: Run test to confirm it passes**

```bash
npm run test -- GlobalCatalogItemForm
```

Expected: currency dropdown test passes.

- [ ] **Step 6: Commit**

```bash
git add src/components/redemption-catalog/GlobalCatalogItemForm.tsx \
        src/components/redemption-catalog/__tests__/GlobalCatalogItemForm.test.tsx
git commit -m "feat: currency dropdown from API in GlobalCatalogItemForm"
```

---

### Task 13: Geographic Scope Multiselect

**Files:**
- Modify: `src/components/redemption-catalog/GlobalCatalogItemForm.tsx`
- Test: `src/components/redemption-catalog/__tests__/GlobalCatalogItemForm.test.tsx`

- [ ] **Step 1: Write failing tests**

```tsx
it("loads geographic scope options from location hierarchy API", async () => {
  const hierarchy = {
    data: {
      levels: [
        { id: "l1", name: "Region", depth: 1 },
        { id: "l2", name: "Country", depth: 2 },
      ],
      tree: [
        {
          id: "v1", name: "AMERICAS", code: "AMERICAS", levelId: "l1",
          levelName: "Region", parentId: null,
          children: [
            { id: "v2", name: "United States", code: "US", levelId: "l2",
              levelName: "Country", parentId: "v1", children: [] },
          ],
        },
      ],
    },
  };
  server.use(
    http.get("/api/v1/location-levels", () => HttpResponse.json(hierarchy)),
  );

  render(<GlobalCatalogItemForm onSave={vi.fn()} />);
  await screen.findByText("AMERICAS");
  expect(screen.getByText("United States")).toBeInTheDocument();
  // Static COUNTRY_OPTIONS should not appear
  expect(screen.queryByText("United Kingdom")).not.toBeInTheDocument();
});

it("shows unmatched existing codes as From sync chips", async () => {
  server.use(
    http.get("/api/v1/location-levels", () =>
      HttpResponse.json({ data: { levels: [], tree: [] } })),
  );
  // Define mockCatalogItem at the top of the describe block (or import from fixtures):
  const mockCatalogItem = {
    id: "item-1", name: "Test Item", description: null,
    category: "NON_CASH", currencyId: "points",
    defaultMinRedemptionAmount: 10, defaultProcessingMode: "INSTANT",
    geographicScope: [], providerItemId: null, isReturnable: false,
    defaultReturnWindowDays: 0, isActive: true, imageUrl: null,
    createdAt: new Date().toISOString(), updatedAt: new Date().toISOString(),
  };
  const item = { ...mockCatalogItem, geographicScope: ["XOXODAY_CODE"] };

  render(<GlobalCatalogItemForm item={item} onSave={vi.fn()} />);
  await screen.findByText("XOXODAY_CODE");
  expect(screen.getByText("From sync")).toBeInTheDocument();
});
```

- [ ] **Step 2: Run tests to confirm they fail**

```bash
npm run test -- GlobalCatalogItemForm
```

Expected: FAIL — still uses `COUNTRY_OPTIONS`.

- [ ] **Step 3: Add location hierarchy query to component**

Add inside component body:
```tsx
const { data: locationData } = useQuery({
  queryKey: ["location-levels"],
  queryFn: async () => {
    const res = await api.get<{
      data: {
        levels: Array<{ id: string; name: string; depth: number }>;
        tree: Array<{
          id: string; name: string; code: string | null; levelName: string;
          levelId: string; parentId: string | null;
          children: Array<{ id: string; name: string; code: string | null;
            levelName: string; levelId: string; parentId: string | null; children: never[] }>;
        }>;
      };
    }>("/api/v1/location-levels");
    return res.data.data;
  },
});

const locationOptions = (locationData?.tree ?? []).flatMap((region) => [
  ...(region.code ? [{ value: region.code, label: region.name, isRegion: true }] : []),
  ...(region.children ?? [])
    .filter((c) => c.code != null)
    .map((c) => ({ value: c.code!, label: c.name, isRegion: false })),
]);
```

- [ ] **Step 4: Compute unmatched codes from existing item**

Add inside component body:
```tsx
const knownCodes = new Set(locationOptions.map((o) => o.value));
const unmatchedCodes = (watch("geographicScope") ?? []).filter(
  (code) => !knownCodes.has(code)
);
```

- [ ] **Step 5: Remove `COUNTRY_OPTIONS` constant and replace the geographic scope field**

Delete the `COUNTRY_OPTIONS` array at the top of the file.

Replace the existing geographic scope `MultiSelect` with:
```tsx
<div className="space-y-1">
  <Label>Geographic Scope</Label>
  <MultiSelect
    options={locationOptions.map((o) => ({
      value: o.value,
      label: o.isRegion ? `📍 ${o.label}` : o.label,
    }))}
    value={(watch("geographicScope") ?? []).filter((c) => knownCodes.has(c))}
    onValueChange={(values) => {
      setValue("geographicScope", [...values, ...unmatchedCodes]);
    }}
    placeholder="Select regions or countries"
  />
  {unmatchedCodes.length > 0 && (
    <div className="flex flex-wrap gap-1 mt-1">
      {unmatchedCodes.map((code) => (
        <span
          key={code}
          className="inline-flex items-center gap-1 px-2 py-0.5 text-xs rounded-full bg-muted text-muted-foreground"
        >
          {code}
          <span className="text-xs opacity-60">From sync</span>
          <button
            type="button"
            onClick={() =>
              setValue(
                "geographicScope",
                (watch("geographicScope") ?? []).filter((c) => c !== code)
              )
            }
            className="ml-1 hover:text-destructive"
            aria-label={`Remove ${code}`}
          >
            ×
          </button>
        </span>
      ))}
    </div>
  )}
</div>
```

- [ ] **Step 6: Run tests to confirm they pass**

```bash
npm run test -- GlobalCatalogItemForm
```

Expected: All tests pass including the two new geographic scope tests.

- [ ] **Step 7: Commit**

```bash
git add src/components/redemption-catalog/GlobalCatalogItemForm.tsx \
        src/components/redemption-catalog/__tests__/GlobalCatalogItemForm.test.tsx
git commit -m "feat: geographic scope multiselect from location hierarchy API with Xoxoday sync chip handling"
```

---

### Task 14: Image Upload Component

**Files:**
- Create: `src/components/redemption-catalog/CatalogImageUpload.tsx`
- Test: `src/components/redemption-catalog/__tests__/CatalogImageUpload.test.tsx`

- [ ] **Step 1: Write failing tests**

```tsx
// src/components/redemption-catalog/__tests__/CatalogImageUpload.test.tsx
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { vi } from "vitest";
import { CatalogImageUpload } from "../CatalogImageUpload";

const mockUpload = vi.fn();
vi.mock("@/services/redemption-catalog-admin.service", () => ({
  uploadCatalogItemImage: (...args: unknown[]) => mockUpload(...args),
}));

describe("CatalogImageUpload", () => {
  it("renders file input and no preview when no existing image", () => {
    render(<CatalogImageUpload itemId={null} currentImageUrl={null} onUploaded={vi.fn()} />);
    expect(screen.getByLabelText(/upload image/i)).toBeInTheDocument();
    expect(screen.queryByRole("img")).not.toBeInTheDocument();
  });

  it("shows preview when existing imageUrl provided", () => {
    render(
      <CatalogImageUpload
        itemId="abc"
        currentImageUrl="catalog/abc/image-123.png"
        onUploaded={vi.fn()}
      />
    );
    expect(screen.getByRole("img")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /remove/i })).toBeInTheDocument();
  });

  it("calls uploadCatalogItemImage on file select when itemId is set", async () => {
    mockUpload.mockResolvedValueOnce({ id: "abc", imageUrl: "catalog/abc/image-new.png" });
    const onUploaded = vi.fn();
    render(
      <CatalogImageUpload itemId="abc" currentImageUrl={null} onUploaded={onUploaded} />
    );
    const file = new File(["img"], "photo.png", { type: "image/png" });
    await userEvent.upload(screen.getByLabelText(/upload image/i), file);
    expect(mockUpload).toHaveBeenCalledWith("abc", file);
    expect(onUploaded).toHaveBeenCalledWith("catalog/abc/image-new.png");
  });

  it("shows error toast when file exceeds 5 MB", async () => {
    render(<CatalogImageUpload itemId="abc" currentImageUrl={null} onUploaded={vi.fn()} />);
    const bigFile = new File([new ArrayBuffer(6 * 1024 * 1024)], "big.png", { type: "image/png" });
    await userEvent.upload(screen.getByLabelText(/upload image/i), bigFile);
    expect(screen.getByText(/5 MB/i)).toBeInTheDocument();
    expect(mockUpload).not.toHaveBeenCalled();
  });

  it("calls onFilePicked with File object in create mode without calling upload endpoint", async () => {
    const onFilePicked = vi.fn();
    render(
      <CatalogImageUpload
        itemId={null}
        currentImageUrl={null}
        onUploaded={vi.fn()}
        onFilePicked={onFilePicked}
      />
    );
    const file = new File(["img"], "photo.png", { type: "image/png" });
    await userEvent.upload(screen.getByLabelText(/upload image/i), file);
    expect(onFilePicked).toHaveBeenCalledWith(file);
    expect(mockUpload).not.toHaveBeenCalled();
  });
});
```

- [ ] **Step 2: Run tests to confirm they fail**

```bash
npm run test -- CatalogImageUpload
```

Expected: FAIL — component doesn't exist.

- [ ] **Step 3: Create `CatalogImageUpload.tsx`**

```tsx
// src/components/redemption-catalog/CatalogImageUpload.tsx
import { useRef, useState } from "react";
import { Button } from "@/components/ui/button";
import { Label } from "@/components/ui/label";
import { uploadCatalogItemImage } from "@/services/redemption-catalog-admin.service";

const MAX_BYTES = 5 * 1024 * 1024;
const ALLOWED = ["image/png", "image/jpeg", "image/webp"];

interface Props {
  itemId: string | null;
  currentImageUrl: string | null;
  onUploaded: (url: string) => void;
  onFilePicked?: (file: File) => void; // create mode: parent stores File and uploads after item creation
  onRemove?: () => void;
}

export function CatalogImageUpload({ itemId, currentImageUrl, onUploaded, onFilePicked, onRemove }: Props) {
  const inputRef = useRef<HTMLInputElement>(null);
  const [preview, setPreview] = useState<string | null>(currentImageUrl);
  const [uploading, setUploading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function handleFileChange(e: React.ChangeEvent<HTMLInputElement>) {
    const file = e.target.files?.[0];
    if (!file) return;
    setError(null);

    if (!ALLOWED.includes(file.type)) {
      setError("Allowed formats: PNG, JPEG, WebP");
      return;
    }
    if (file.size > MAX_BYTES) {
      setError("File exceeds 5 MB limit");
      return;
    }

    if (!itemId) {
      // Create mode: show preview locally; hand File to parent for post-create upload
      setPreview(URL.createObjectURL(file));
      onFilePicked?.(file);
      return;
    }

    setUploading(true);
    try {
      const response = await uploadCatalogItemImage(itemId, file);
      setPreview(response.imageUrl ?? null);
      if (response.imageUrl) onUploaded(response.imageUrl);
    } finally {
      setUploading(false);
      if (inputRef.current) inputRef.current.value = "";
    }
  }

  function handleRemove() {
    setPreview(null);
    onRemove?.();
  }

  return (
    <div className="space-y-2">
      <Label htmlFor="catalog-image-upload">
        Image <span className="text-muted-foreground text-xs">(optional — PNG/JPEG/WebP, max 5 MB)</span>
      </Label>
      {preview ? (
        <div className="flex items-start gap-3">
          <img
            src={preview}
            alt="Catalog item preview"
            className="h-20 w-20 rounded border object-cover"
          />
          <div className="flex flex-col gap-1">
            <Button
              type="button"
              variant="outline"
              size="sm"
              onClick={() => inputRef.current?.click()}
              disabled={uploading}
            >
              {uploading ? "Uploading…" : "Change"}
            </Button>
            <Button
              type="button"
              variant="ghost"
              size="sm"
              onClick={handleRemove}
              disabled={uploading}
            >
              Remove
            </Button>
          </div>
        </div>
      ) : (
        <Button
          type="button"
          variant="outline"
          size="sm"
          onClick={() => inputRef.current?.click()}
          disabled={uploading}
        >
          {uploading ? "Uploading…" : "Upload image"}
        </Button>
      )}
      <input
        ref={inputRef}
        id="catalog-image-upload"
        aria-label="Upload image"
        type="file"
        accept="image/png,image/jpeg,image/webp"
        className="hidden"
        onChange={handleFileChange}
      />
      {error && <p className="text-sm text-destructive">{error}</p>}
    </div>
  );
}
```

- [ ] **Step 4: Run tests to confirm they pass**

```bash
npm run test -- CatalogImageUpload
```

Expected: 5 tests pass.

- [ ] **Step 5: Commit**

```bash
git add src/components/redemption-catalog/CatalogImageUpload.tsx \
        src/components/redemption-catalog/__tests__/CatalogImageUpload.test.tsx
git commit -m "feat: CatalogImageUpload component with preview, validation, and upload on file select"
```

---

### Task 15: Wire into GlobalCatalogItemForm

**Files:**
- Modify: `src/components/redemption-catalog/GlobalCatalogItemForm.tsx`

- [ ] **Step 1: Import CatalogImageUpload**

```tsx
import { CatalogImageUpload } from "./CatalogImageUpload";
```

- [ ] **Step 2: Add `pendingImageFile` state for create mode**

```tsx
const [pendingImageFile, setPendingImageFile] = useState<File | null>(null);
```

- [ ] **Step 3: Handle post-create image upload in `onSubmit`**

Modify the `onSubmit` function to upload the image after creating the item:

```tsx
async function onSubmit(values: FormValues) {
  if (isEdit && item) {
    await updateMutation.mutateAsync({ id: item.id, request: values });
    onSave();
  } else {
    const created = await createMutation.mutateAsync(values);
    if (pendingImageFile) {
      await uploadCatalogItemImage(created.id, pendingImageFile);
    }
    onSave();
  }
}
```

Add the import at the top:
```tsx
import { uploadCatalogItemImage } from "@/services/redemption-catalog-admin.service";
```

- [ ] **Step 4: Add `CatalogImageUpload` to the form JSX**

Add after the `description` field and before the `category/currencyId` grid:

```tsx
<CatalogImageUpload
  itemId={item?.id ?? null}
  currentImageUrl={item?.imageUrl ?? null}
  onUploaded={() => {
    // edit mode: upload already done inside the component; nothing extra needed
  }}
  onFilePicked={(file) => setPendingImageFile(file)}
  onRemove={() => {
    if (isEdit && item) {
      updateMutation.mutate({ id: item.id, request: { imageUrl: null } });
    }
  }}
/>
```

- [ ] **Step 5: Run full test suite**

```bash
npm run test
```

Expected: All tests pass.

- [ ] **Step 6: Run the app and manually test the form**

```bash
npm run dev
```

Navigate to Platform Settings → Redemption Catalog → Catalog Items. Open the create form and verify:
- Currency shows dropdown with tenant currencies
- Geographic scope shows location hierarchy tree, "From sync" chips for unmatched codes
- Image shows file picker, preview after upload, Remove button

- [ ] **Step 7: Commit**

```bash
git add src/components/redemption-catalog/GlobalCatalogItemForm.tsx
git commit -m "feat: wire CatalogImageUpload into GlobalCatalogItemForm"
```

- [ ] **Step 8: Squash-merge back to features/redemption-catalog, push**

```bash
git checkout features/redemption-catalog
git merge --squash work/redemption-catalog-form-fe
git commit -m "feat(FE): form enhancements — image upload, currency dropdown, geographic scope multiselect"
git push origin features/redemption-catalog
```

---

## Done When

- [ ] Blueprint: `spec.md`, `technical.md`, `US-01` all updated and committed on `features/redemption-catalog`
- [ ] BE: `./gradlew test` passes — all new service + controller tests green
- [ ] Contracts: diff shows `imageUrl` in 3 DTOs + new upload endpoint
- [ ] FE Phase 1: sidebar links removed, Redemption Catalog tab renders in Platform Settings with both sub-tabs working, old routes redirect
- [ ] FE Phase 2: `npm run test` passes — currency dropdown, geo scope, image upload all have tests; form submits correctly
- [ ] GitLab MR !2 (FE) and !7 (BE) updated with new commits for Robert to re-review

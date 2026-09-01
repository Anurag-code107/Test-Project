# Pattern: webhook-security

## When this applies

Feature introduces an inbound webhook endpoint (no JWT) secured by HMAC-SHA256 signature validation — typically from a third-party vendor (Xoxoday, Stripe, etc.) posting confirmation events.

## Spec authoring guidance

- **Header name:** agree on a standard, vendor-agnostic header name. Prefer `X-Webhook-Signature` (generic) over vendor-prefixed names like `X-Xoxoday-Hmac-Sha256`. The OpenAPI contract is the source of truth — drift between the contract and implementation is a high-severity finding.
- **Vendor allowlist:** spec an explicit list of valid vendor path variable values. Unknown values must return 404 (not 401/403) to avoid information leakage.
- **HMAC algorithm:** spec `HmacSHA256` with lowercase hex output. Never spec base64 unless the vendor explicitly requires it.
- **Response semantics:** webhook endpoints return 200 for all processed events, including duplicates and idempotent no-ops. Error cases (bad HMAC, unknown vendor, parse error) return the appropriate error status, but the endpoint should return 200 even for parse errors to prevent the vendor from retrying on our parse bug.
- **Idempotency:** spec that idempotency is enforced at the service layer, not the controller. The controller always delegates to the service and returns 200.

## Implementation guidance

### Controller structure

```java
@PostMapping("/{vendor}")
public ResponseEntity<Void> handleWebhook(
        @PathVariable String vendor,
        @RequestHeader(value = "X-Webhook-Signature", required = false) String signature,
        @RequestBody String rawBody) {

    // Step 1: vendor allowlist FIRST — 404 for unknowns, no HMAC logic leaked
    if (!SUPPORTED_VENDORS.contains(vendor.toLowerCase(Locale.ROOT))) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
    }

    // Step 2: HMAC validation
    if (!validateHmac(vendor, signature, rawBody, resolveSecret(vendor))) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
    }

    // Step 3: parse + delegate
    ...
    return ResponseEntity.ok().build();
}
```

### HMAC comparison

Always use `MessageDigest.isEqual()` — never `equals()` or `equalsIgnoreCase()`. These are subject to timing side-channel attacks.

```java
return MessageDigest.isEqual(
    expected.getBytes(StandardCharsets.UTF_8),
    signature.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8));
```

### @Value injection

Use constructor injection (not field injection) for signing secrets so the field can be `final`:

```java
public ReturnWebhookController(ReturnService returnService,
                               @Value("${my.webhook.secret:}") String signingSecret) {
    this.signingSecret = signingSecret;
}
```

### Startup guard

Validate the signing secret at startup via `@PostConstruct` (or a `@Configuration` bean that fails eagerly):

```java
@PostConstruct
void validateConfig() {
    if (signingSecret.isBlank()) {
        throw new IllegalStateException("my.webhook.secret must be configured");
    }
}
```

Note: `@Profile` is silently ignored on `@PostConstruct` methods inside a `@Service` class — it only works on `@Bean` and `@Configuration` class declarations. Use a programmatic profile check if you need to skip validation in test profiles:

```java
@Autowired private Environment env;

@PostConstruct
void validateConfig() {
    if (env.acceptsProfiles(Profiles.of("!test", "!localtest")) && signingSecret.isBlank()) {
        throw new IllegalStateException("my.webhook.secret must be configured");
    }
}
```

### Log injection prevention

Always sanitize vendor-supplied strings before logging to prevent log injection:

```java
String safeRef = vendorRef != null
    ? vendorRef.replaceAll("[\r\n\t]", "_")
    : "(null)";
log.info("step=webhook_received vendorRef={}", safeRef);
```

Never log the expected HMAC value, even at DEBUG level.

### SecurityConfig

Add the webhook path to `PUBLIC_PATHS` in `SecurityConfig` (no JWT on webhook endpoints):

```java
private static final List<String> PUBLIC_PATHS = List.of(
    "/api/v1/webhooks/**",
    ...
);
```

## Examples in codebase

- `ReturnWebhookController` — `src/main/java/com/tenxengage/app/controller/ReturnWebhookController.java`
- `RedemptionWebhookController` — same package

## Common gotchas

- **Header name drift with OpenAPI contract.** If the contract says `X-Webhook-Signature` and the implementation uses `X-Xoxoday-Hmac-Sha256`, every webhook call fails with 403. The contract is the source of truth. Found during redemption-returns US-03 (2026-06-13).
- **`@Profile` silently ignored on `@PostConstruct`.** Annotating a `@PostConstruct` method with `@Profile("!test")` does nothing — Spring only applies `@Profile` at `@Bean` and `@Configuration` class level. The method runs in all profiles. Use a programmatic `Environment.acceptsProfiles()` check instead. Found during redemption-returns US-03 (2026-06-13).
- **`catch (Exception e)` swallows non-parse RuntimeExceptions.** Narrow the catch to `JsonProcessingException` so unexpected runtime errors are not silently swallowed and return 200.
- **Vendor allowlist checked after HMAC.** Checking HMAC before the vendor allowlist leaks information (attacker learns the vendor is invalid vs HMAC is wrong). Always check the allowlist first, return 404 for unknowns.
- **`@Audited` hardcoded on webhook handler.** If the webhook can result in both CONFIRM and REJECT outcomes, a single `@Audited(action = "COMPLETED")` annotation fires for both paths and produces incorrect audit entries. Emit programmatic audit events inside the service layer, branching on the outcome.
- **Dead null check for resolveSecret return.** If `resolveSecret()` always returns a non-null string (e.g., returns `""` for unrecognised vendors), a `secret == null` guard is dead code. Keep only `secret.isBlank()`.

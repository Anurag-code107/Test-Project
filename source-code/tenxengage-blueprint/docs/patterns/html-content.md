# Pattern: html-content

## When this applies

Feature stores user-generated HTML or rich-text content (blog body, course description with formatting, message body with embedded styling, etc.).

## Spec authoring guidance

- **Sanitization is mandatory.** The spec's Security Design section MUST specify the chosen sanitization mechanism: (a) service-layer sanitization using **Jsoup** before persistence (preferred), or (b) a custom `@Constraint` validator that runs Jsoup. Document the choice in the Input Validation table.
- **Do NOT reference `@SafeHtml`.** That annotation was removed in Hibernate Validator 7+ and does not exist in Spring Boot 3.x projects. The spec must NOT cite it.
- **Allowlist policy is explicit.** Spec the Jsoup `Safelist` policy used (e.g., `Safelist.basic()`, `Safelist.relaxed()`, or a custom built one). Different allowlists are appropriate for different content types — pick consciously and document the choice.
- **PII implications.** If HTML content can contain PII (e.g., user-pasted email addresses in a forum post body), the field must appear in the Data Retention & Compliance PII Fields table.

## Implementation guidance

- Implement sanitization in the service layer **before** persistence — never trust the controller to have done it.
- Use Jsoup's `Safelist` API. Define the allowlist as a `static final` constant or `@Bean` to avoid per-request construction overhead.
- For rendering: HTML is stored already-sanitized, so the frontend can render via `dangerouslySetInnerHTML` (React) or equivalent. Document this trust boundary in code comments at the rendering site.
- Validation order: bean validation → Jsoup sanitization → DB constraints. Bean validation alone does NOT prevent XSS; Jsoup is the line of defense.

## Examples in codebase

- Existing Jsoup usage (if any): `grep -rn "Jsoup" tenxengage-backend/src/main/java/`
- If Jsoup is not yet introduced: this is the first feature to add it. Spec must include the Maven dependency in `technical.md` and call out the new dependency in the spec's Dependencies section.

## Common gotchas

- **Don't sanitize at the controller using `@Valid` alone.** Bean validation cannot detect XSS.
- **Don't sanitize at render time.** Sanitize once on write; trust on read. Sanitize-on-read is a perpetual security debt.
- **Don't use `Safelist.relaxed()` without consideration** — it permits `<a>` and `<img>` which can carry javascript URIs in some configurations. Pick the most restrictive allowlist that satisfies the feature's needs.
- **CSP headers are complementary, not a replacement.** Even with strict CSP, sanitize on write.
- **Don't forget plain-text fields.** `title`, `name`, and similar short-string fields also need sanitization if they are later rendered in a browser context — use `Jsoup.clean(value, Safelist.simpleText())`. `@NotBlank` and `@Size` do not prevent stored XSS. The pattern "only description gets Jsoup, title is fine" is a common miss.
- **Post-sanitization blank check is mandatory on required fields.** `Jsoup.clean()` strips all markup, so a value like `<script>alert(1)</script>` becomes an empty string after sanitization. `@NotBlank` on the raw input does NOT catch this because bean validation runs before service-layer sanitization. After `Jsoup.clean()`, call `.strip()` and check `isEmpty()` — throw `BusinessRuleException` (422) if empty: `if (sanitizedValue.isEmpty()) throw new BusinessRuleException("Field name must not be empty after sanitization");`

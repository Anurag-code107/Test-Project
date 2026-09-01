# F-NN: {Feature Name}

> **Slug**: `{slug}` · **Roadmap**: `{roadmap-slug}` · **Phase**: {1|2|3} · **Recommended order**: {Nth}
> **Roadmap**: [../roadmap.md](../roadmap.md) · **Digest**: [../digest.md](../digest.md)
> **BRD anchors**: "{heading text}", "{heading text}"

## Business outcome
{1–2 sentences. What shipping this enables for the business and users. Plain language; no implementation terms.}

## Primary persona of record
**{Persona name}** — {one-line role description; recipient of action / journey owner; not the escalation target, not the configurator unless this is a config feature}.

## Secondary personas
- **{Persona}** — {how they interact with this slice}
- **{Persona}** — {how they interact}

## User journey (sketch — not a flow diagram)
{Entry point → core action → exit, in 2–3 sentences. Business language. No screen names yet, no API names.}

## Functional requirements (business intent)

> Numbered, business-language capability statements. Not UI elements. Not API methods. Not tables. **5–12 typical per feature.**

1. **FR-NN.1** — {Capability statement, business language, testable in plain English.}
2. **FR-NN.2** — {Capability statement.}
3. **FR-NN.3** — {Capability statement.}
4. **FR-NN.4** — ...

## Business rules
- {Rule — must hold true at all times. Different from FR which is "what the system does".}
- {Rule}

## Constraints / validations
- {Constraint — what's not allowed; what triggers rejection in business terms.}

## Relevant non-functional requirements
> Only NFRs that materially shape this feature. Skip platform-givens (tenant isolation, audit, RBAC enforcement — those are inherited).
- {NFR — e.g., "Recommendations must surface in the deal room within 2 minutes of room provisioning"}

## Edge cases / open questions
- {Edge case the spec author needs to handle}
- {Open question — flag for `/create-spec` to resolve from codebase or with PM}

## Dependencies
- **Features**: {F-NN, F-NN, or "—"}
- **ADRs**: {ADR-NN — must be resolved before spec freeze, or "—"}
- **External counterparties**: {cross-quadrant or third-party readiness, or "—"}

## Riskiest unknown
{The one thing that, if wrong, hurts this slice most. One paragraph.}

## Candidate domain concepts (business nouns)

> **Not entities. Not schemas. Not field names.** Business-language nouns the feature operates on. Final entity naming and shapes are decided in `/create-spec` from the actual codebase, not from this list.
>
> If the BRD's CamelCase entity guesses are useful, they live in [../digest-annex.md](../digest-annex.md) as advisory hints.

- **{concept}**: {business definition in one line}
- **{concept}**: {business definition}

## Cross-feature / cross-quadrant signals (business intent)

> Business-language flow. NOT event names. NOT API ops. The contract artifacts (event taxonomy, payloads) are decided in `/create-spec`.

| Direction | Counterparty | Business intent |
|---|---|---|
| Sends to | {Feature/Quadrant} | {what business fact is communicated} |
| Receives from | {Feature/Quadrant} | {what business fact triggers this slice} |

---

## Suggested story seeds

> **Planning-level only.** Title + 1-line business outcome (+ optional type, optional dependency). **Not** acceptance criteria. **Not** Gherkin. **Not** API methods. **Not** DB tasks.
>
> The PM uses these as a backlog skeleton. `/create-stories` will refine into execution-ready stories with full acceptance criteria after `/create-spec` produces the technical spec.
>
> **3–8 seeds typical per feature.** Phrased as **business slices**, not technical slices.

| # | Title | Business outcome | Type | Depends on |
|---|---|---|---|---|
| S-01 | {3–7 word title} | {1-line intent in business language} | `UI` / `workflow` / `rules` / `integration` / `reporting` / `admin` / `agent` / `data` | — |
| S-02 | {title} | {intent} | {type} | S-01 |
| S-03 | {title} | {intent} | {type} | — |

### What good story seeds look like (worked example for F-04 Certification Lifecycle)

| # | Title | Business outcome | Type | Depends on |
|---|---|---|---|---|
| S-01 | Define certification programs | Admins create certification programs and link prerequisites. | admin | — |
| S-02 | Earn certifications | Partners receive digital certificates and badges on completion. | workflow | S-01 |
| S-03 | Show certification status | Partners and partner admins see active / expiring / expired / revoked certifications on profile. | UI | S-02 |
| S-04 | Verify certificates externally | Third parties validate a certificate via verification token. | integration | S-02 |
| S-05 | Detect approaching expiry | System surfaces certifications approaching expiry per tier-based lead time. | rules | S-02 |
| S-06 | Publish lapse signal | When a certificate lapses, downstream eligibility consumers are notified. | integration | S-02 |
| S-07 | Recertify on version invalidation | When a product version invalidates a certification, the partner is auto-assigned the recertification path. | rules | S-01, S-02 |

### What story seeds must NOT be (anti-examples for the same feature)

| ❌ Bad seed | Why it's bad | ✅ Better seed |
|---|---|---|
| Create `CertificationProgram` entity and repository | Technical / implementation task | "Define certification programs" |
| `POST /api/v1/certifications` endpoint | API-level detail | "Earn certifications" |
| Add `expiry_date` column to `certifications` table | DB schema task | "Track certification validity periods" (folded into "Define certification programs") |
| Issue `certification_lapsed` Kafka event | Event taxonomy | "Publish lapse signal" |
| Backend Flyway migration V001 | Engineering task | (n/a — not a planning seed) |
| Wire RBAC for certification.create | Permissions task | (n/a — inherited from platform; not a planning seed) |

---

## `/create-spec` invocation

```
/create-spec {roadmap-slug} F-NN
```

---
domain: incentive
status: active
anchored-on: own-builder-stack
authored: 2026-05-12
---

# Incentive Domain

## Slot fillers

| Slot | Filler | Location |
| --- | --- | --- |
| Core aggregate | `Incentive` | `entity/Incentive.java` |
| Audience-rule entity | `IncentiveAudienceRule` | `entity/IncentiveAudienceRule.java` |
| Eligibility engine contract | `ParticipantEligibilityChecker.matchesUserEligibility(Incentive, …)` | `service/ParticipantEligibilityChecker.java:91` |
| Completion/participation entity | `UserIncentiveCompletion` | `entity/UserIncentiveCompletion.java` |
| Budget model | `IncentiveBudget` | `entity/IncentiveBudget.java` |
| Approval workflow entity | `IncentiveApprover` | `entity/IncentiveApprover.java` |
| Builder discriminator | `incentive_type` column | on `BuilderSectionConfig` |
| Section/field storage entity | `BuilderSectionConfig` / `BuilderFieldConfig` | `entity/Builder*Config.java` |

> **Shared infra note (2026-05-25):** `BuilderSectionConfig` / `BuilderFieldConfig` are now also used by the course/enablement domain. Rows are distinguished by `incentive_type IS NOT NULL` (incentive) vs `builder_type IS NOT NULL AND builder_domain IS NOT NULL` (enablement/future). The incentive admin endpoints and `BuilderFieldEditor.tsx` are unchanged. See [../builder-config.md](../builder-config.md) for the unified schema.

## Section keys per `incentive_type`

| `incentive_type` | `section_keys` (ordered) | lock summary |
| --- | --- | --- |
| SALES | `basics, schedule, audience, budget, criteria, approval` | all locked except `audience` |
| TRAINING | `basics, schedule, audience, budget, criteria (Training Courses), approval` | locked except `audience` and `criteria` |
| ACTIVITY | `basics, schedule, audience, budget, criteria (Activity Setup), approval` | locked except `audience` and `criteria` |
| JOURNEY | `basics, schedule, audience, budget, criteria (Journey Stages), approval` | all locked except `audience` |

## Domain conventions (prose, not slot-filled)

- Eligibility is lazy / company-mediated. No `incentive_participant` join table.
  Computed at query time from the user's current `PartnerCompany.locationAssignments`
  and `ClientRole`.
- No auto-tagging on user onboarding. No company-switch handler. **KNOWN GAPS.**
- Completion event topic: `completion-events` (Kafka).

## Notes on adjacent classes

- `EligibilityRule.java` and `EligibilityRuleGroup.java` are unprefixed but
  sit alongside `IncentiveAudienceRule` in the incentive package. They are
  considered incentive-internal pending confirmation. If a later audit
  determines they are domain-neutral, they may seed platform primitives.

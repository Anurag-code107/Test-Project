# Contract Alignment Patterns

Rules for keeping TypeScript types aligned with API contracts.

## Rules

- TypeScript interfaces for API responses must be derived from `contracts/endpoints/{resource}.yaml` schemas — not from entity field names in `contracts/models/`.
- Response field names often differ from entity field names. Example: the entity has `estimatedDurationMinutes`, which matches the API response field — but an implementer assuming the entity uses a shorter alias would write `durationMinutes`.
- Comment references should point to the endpoint schema (`contracts/endpoints/resource.yaml`), not just the model doc, because the model doc describes the DB entity while the endpoint YAML defines the exact API shape.
- Request interfaces must include all required fields from the contract schema. `CreateCourseRequest` requires `creationMode`; `UpdateCourseRequest` requires `name` and `version`. Omitting required fields will cause 400 errors at runtime even though TypeScript won't catch it (because the interface is too permissive).

## Pitfalls


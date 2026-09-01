# Consent Model

Represents a user's consent preference for optional data processing activities.

## ConsentPreferenceResponse

| Field | Type | Required | Constraints | Notes |
|-------|------|----------|-------------|-------|
| consentType | ConsentType | yes | enum | See enums.md |
| label | string | yes | max 100 | Display label (e.g. "AI Recommendations") |
| description | string | yes | max 500 | Explanation of what the consent covers |
| required | boolean | yes | — | Whether this consent is mandatory (always true = cannot opt out) |
| defaultValue | boolean | yes | — | Default consent state if user does not explicitly choose |

## Notes

- Consent preferences are returned during onboarding and on the user settings page
- Consent records are stored per-user with timestamps for audit purposes
- Changing consent preferences creates a new audit record (old records are not deleted)

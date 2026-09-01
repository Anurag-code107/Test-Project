# Onboarding Models

## OnboardingStatusResponse

Tracks the progress of a user through the onboarding flow.

| Field | Type | Required | Constraints | Notes |
|-------|------|----------|-------------|-------|
| userId | UUID | yes | foreign key | References User |
| email | string | yes | format: email | User's email address |
| passwordSet | boolean | yes | — | Whether the user has set a password |
| profileCompleted | boolean | yes | — | Whether profile fields are filled |
| policiesAccepted | boolean | yes | — | Whether all required policies are accepted |
| consentRecorded | boolean | yes | — | Whether consent preferences have been recorded |
| onboardingComplete | boolean | yes | — | Whether the entire onboarding flow is complete |
| currentStep | string | yes | — | Next step to complete (e.g. "SET_PASSWORD", "COMPLETE_PROFILE", "ACCEPT_POLICIES", "SET_CONSENT", "DONE") |

## SetPasswordRequest

| Field | Type | Required | Constraints | Notes |
|-------|------|----------|-------------|-------|
| token | string | yes | — | Onboarding invitation token |
| password | string | yes | min 8 | Must meet password policy |
| confirmPassword | string | yes | min 8 | Must match password |

## CompleteProfileRequest

| Field | Type | Required | Constraints | Notes |
|-------|------|----------|-------------|-------|
| token | string | yes | — | Onboarding invitation token |
| firstName | string | yes | max 100 | |
| lastName | string | yes | max 100 | |
| phone | string | no | max 20 | |

## AcceptPoliciesRequest

| Field | Type | Required | Constraints | Notes |
|-------|------|----------|-------------|-------|
| token | string | yes | — | Onboarding invitation token |
| acceptedPolicyIds | UUID[] | yes | non-empty | IDs of the policies the user is accepting |

## SetConsentRequest

| Field | Type | Required | Constraints | Notes |
|-------|------|----------|-------------|-------|
| token | string | yes | — | Onboarding invitation token |
| consents | ConsentEntry[] | yes | non-empty | Array of consent preferences |

### ConsentEntry (inline object)

| Field | Type | Required | Constraints | Notes |
|-------|------|----------|-------------|-------|
| consentType | ConsentType | yes | enum | See enums.md |
| granted | boolean | yes | — | Whether consent is granted |

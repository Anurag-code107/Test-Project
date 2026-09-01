# Step 4 — setup-world

### Inputs
- `TEST_WORLD_SPEC` from Step 3
- `BE_URL` from Step 2
- `SLUG` from Step 1

### Actions

Dispatch a subagent to the FE repo to create the test world via real API calls. This uses the same `apiLogin` + `apiPost` helpers established in `e2e/<slug>/helpers.ts`.

```javascript
Agent({
  description: "QA Explore — test world setup",
  subagent_type: "general-purpose",
  prompt: `
    Create the qa-explore test world for feature: ${SLUG}.
    Working directory: c:/Users/TenXengage/Development/TenXengage-New/source-code/tenxengage-frontend

    Use Node.js inline script (not Playwright) to call the real API.
    The backend is running at: ${BE_URL}

    Execute these steps in order using fetch() with the credentials cookie pattern:

    1. Login as INSTRUCTOR_A:
       POST ${BE_URL}/api/v1/auth/login
       Body: { "email": "instructor-a@acme.com", "password": "TestPass1!" }
       Capture the Set-Cookie header (session cookie) for subsequent calls.

    2. Create entities in this order (follow ${TEST_WORLD_SPEC} exactly):
       ${JSON.stringify(TEST_WORLD_SPEC, null, 2)}

       For each entity:
       - Use the endpoint and payload from the spec
       - Capture the returned id from response.data.id
       - Name entities clearly (e.g., "QA-Explore DRAFT Assessment", "QA-Explore Bank")

    3. If any creation fails with an unexpected status (not the expected 200/201):
       Print: WORLD_SETUP_STATUS=failure
       Print: WORLD_SETUP_ERROR=<HTTP method> <path> returned <status>: <body>
       Stop.

    4. On success, print exactly:
       WORLD_SETUP_STATUS=success
       WORLD_IDS_JSON=<single-line JSON object with all entity IDs>

       Example format:
       WORLD_IDS_JSON={"assessmentDraftId":"uuid","assessmentPublishedId":"uuid","questionBankId":"uuid","questionIds":["uuid","uuid","uuid"]}
  `
})
```

Parse subagent output:
- Extract line starting with `WORLD_SETUP_STATUS=` → `setupStatus`
- Extract line starting with `WORLD_IDS_JSON=` → parse as JSON → `WORLD_IDS`

If `setupStatus = "failure"`:
- Print: `"Test world setup failed. Verify the BE database is seeded with instructor-a@acme.com and learner-a@acme.com accounts."`
- Print the `WORLD_SETUP_ERROR` from subagent output
- STOP

Save `WORLD_IDS` for Steps 5 and 6.

### Output of Step 4
- `WORLD_IDS` dict with all created entity IDs (assessments, questions, banks, etc.)
- Test data exists in the real BE database, ready for browser interaction

---

## Boundary

Outputs of this step:
- `WORLD_IDS` (map of entity IDs created)

Route to step 05: read `steps/step-05-primary-pass.md`.

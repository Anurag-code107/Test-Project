# Findings classification rubric

Loaded by `steps/step-07-classify.md` to assign severity and attribute root cause
to each finding from steps 05 and 06.

---

## Severity

| Severity | Criteria |
|---|---|
| `CRITICAL` | Blank page on navigation. Unhandled exception that halts the flow. App crashes. |
| `HIGH` | Error toast fires on a successful API call. Missing required UI element (exists in spec microcopy but absent). Broken navigation (button navigates to wrong route). Data not persisting after successful POST/PUT. |
| `MEDIUM` | Inconsistent state (API returned success but UI still shows stale data after invalidation). Filter produces wrong results. Partial failure with no user feedback. |
| `LOW` | Console warning outside noise baseline. Minor UX inconsistency. Advisory toast shown when it should not be. |

Assign `CRITICAL` for any test named `baseline: page loads without blank screen or console errors` that failed — that is definitionally a blank page or crash.

Assign `HIGH` for:
- Any test named `dom-inventory: all interactive elements respond without crash` that failed on a specific element — record which element caused it.
- Any AC test that failed.

Assign `MEDIUM` or `LOW` for secondary-pass findings based on criteria above.

## Root-cause attribution

For each finding, determine root cause by examining the failure message, console errors, and network errors:

1. **No network errors, console error contains a React stack trace pointing to a component file** → `FE`

2. **Network log shows a 4xx/5xx on an API call:**
   - Check the story's AC items: is this status code expected for this endpoint under this condition?
   - If expected (e.g., 404 when navigating to a non-existent entity) but UI shows incorrect result (e.g., error toast instead of "not found" page) → `FE`
   - If unexpected (no AC item covers this status code from this endpoint, and it is not a user-data error) → `BE`. Document: exact HTTP method, URL, request body, actual status, actual response body, expected behavior per spec.

3. **Blank page on navigation with no network errors** → Check if the route is registered in the FE router:
   - If route is in the router config → `FE` (component crashes on mount)
   - If route is NOT in the router → `Config`

4. **Error toast fires on a 201/200 success response** → `FE` (mutation hook mishandles success response)

5. **API returned a field with wrong name or missing field** → `BE`

Set `rootCause = "FE" | "BE" | "Config"` on each finding.

**For BE findings:** Record exactly:
- HTTP method and full URL
- Request body (if POST/PUT)
- Actual response status and body
- Expected behavior per the story's AC item

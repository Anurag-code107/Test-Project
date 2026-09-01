# Payout mobile number + XTRM profile sync

**Status:** in progress (2026-07-16)
**Branch:** `features/redemption-xtrm-payout-enhancement` (off `roadmaps/redemption-store`)
**Enhancement to:** F-03 redemption payout (card + wallet-withdrawal work).

## Why

`UserWithdrawFund` fails with **"Mobile # is not available in the user profile"** unless the payee's
XTRM user profile carries a mobile number. Root cause: our `CreateUser` never sent one — the phone flowed
from `user.getPhone()` into `CreateUserCommand.phone` but `createUser()` dropped it. Fixed the mapping; now
we must guarantee the phone is **present** (required) and let users **change** it (synced to XTRM).

Confirmed with sandbox curls:
- **CreateUser** takes `MobilePhone` (single field, dial code inline, e.g. `"14085551284"`).
- **UpdateUser** (`POST /API/v4/Register/UpdateUser`) is **OTP-gated (2 calls)** and splits the mobile into
  `MobileCountryISO2` (e.g. `"IN"`) + `MobileNumber` (national, e.g. `"8377906689"`). Field is `UserId`
  (lowercase d). Call 1 (no OTP) → OTP sent **to the new number provided** (no chicken-and-egg for users
  with no mobile). Call 2 (with OTP) → applied. Envelope `{"UpdateUser":{"request":{…}}}`.

## Decisions

- Phone is **mandatory at user creation** (admin create + self-register) and **editable** in Profile
  Information. Collected as **country (ISO2) + national number** — feeds both XTRM shapes cleanly.
- Storage: `users.phone` = national number (digits), new `users.phone_country_iso2` = ISO2. A
  `PhoneDialCodes` util maps ISO2 → dial code so `CreateUser` can build `MobilePhone` = dialcode+national.
- On a **profile phone change for an already-enrolled user**, run the 2-step OTP `UpdateUser`; persist our
  phone only after XTRM confirms (kept in sync). Not-yet-enrolled users just save; the phone flows at
  enrollment.
- Withdrawal OTP flags reverted to both email+SMS (mobile now present) — matches the proven curl.

## Phases

1. **BE data + CreateUser** — V38 `phone_country_iso2`; `User` field; `PhoneDialCodes`; `CreateUserCommand`
   +iso2; `createUser()` builds `MobilePhone`; enrollment passes iso2. *(done in this pass)*
2. **BE UpdateUser client** — `XtrmApiClient.updateUser` 2-step (Impl + Stub). *(done in this pass)*
3. **BE required + sync** — require phone+country on create/self-profile; self-profile update routes an
   enrolled user's phone change through the OTP flow; controller endpoints.
4. **Contracts + JUnit.**
5. **FE** — country+number inputs on registration/admin-create; Profile Information editable phone with a
   2-step OTP modal for enrolled users.

## Done when

- A newly-created user always has a mobile → enrolls with it → withdrawal runs (no "mobile not available").
- An enrolled user can change their phone via Profile Information (OTP-confirmed), synced to XTRM + our DB.
- Contracts + BE unit tests + FE vitest green.

# Plan — Payout Tab Redesign (F-03 Redemption Payout enhancement)

**Status:** REVIEWED (independent code-review pass folded in — see §18)
**Type:** Enhancement (UI reorganization of an existing feature — plan doc + enhancement branch, not /create-spec)
**Scope:** Frontend only. No backend or contract changes — every hook/endpoint/UI primitive already exists (verified).
**Date:** 2026-07-17

---

## 1. Goal

Reorganize the user's **Payout** area from a long vertical stack (plus a separate **Withdraw** tab) into a single, tabbed Payout tab:

- Method **sub-tabs**: **Bank Accounts · Cards · Digital Wallet**, each managing its own linked accounts and a unified "Set as default".
- **Withdraw** becomes a **modal** launched from the Digital Wallet tab; the standalone Withdraw top-level tab is removed.
- **Recent withdrawals** moves to the **bottom** of the Payout tab.
- The **address/enrollment** stays as a single **"Payout profile"** section at the top (enroll-once + update), collapsible after enrollment.

## 2. Locked decisions

1. **Withdraw** → modal off the Digital Wallet tab; **remove** the separate `Withdraw` top-level tab. ✅
2. **Default model** → **unify**: "Set as default" inside a method tab makes that account the single payout destination (composes existing endpoints); **drop** the standalone payout-method radio. ✅
3. **Address** → keep as the top "Payout profile" section (not inside a method tab); collapse to a summary after enrollment.

## 2a. Scope constraint — don't change functionality OUTSIDE this redesign

The intended redesign changes below proceed as planned (including the unified default, §5). Everything *outside* the redesign's scope stays exactly as it is — this is scope containment, not a freeze on the redesign itself.

**Untouched (outside scope):**
- **Backend + API contracts:** zero changes — no controllers, services, endpoints, or contracts.
- **The underlying logic of every payout flow:** enrollment (enroll-once + address update), link/remove bank, link/remove card, set default, withdraw (initiate → OTP → confirm), wallet balance read — same requests, responses, validation, OTP UX, and error handling. (The withdraw UI is *relocated* into a modal; its flow is byte-for-byte the same.)
- **Other areas of the app:** the other profile tabs (Profile, Notifications, Privacy, Support) and any unrelated features are not touched.

**Intended (in-scope) changes:** sub-tabs (Bank/Card/Digital Wallet), withdraw-as-modal, recent-withdrawals moved to bottom, **unified default** (removes the method radio; same capability via the same endpoints), collapsible address summary, and the "Digital Wallet" label sweep.

**Optional niceties (in-scope, your call):**
- Address **prefill** into the bank/card forms (§8) — pure convenience; off by default.
- Wallet-balance **refresh after withdraw** (§6) — **recommended** now that balance + withdraw are co-located, so the balance isn't briefly stale; small and in-scope.

## 3. Current state (as mapped)

Host: `src/pages/client-admin/MyProfilePage.tsx` — top-level `Tabs`: Profile · Notifications · **Payout** · **Withdraw** · Privacy · Support. Payout/Withdraw gated by `canPayout`. URL-driven via `?tab=payout` / `?tab=withdraw`.

- `PayoutTab.tsx` (vertical stack): enrollment `Alert` → `ProfileAddressSection` → payout-method `RadioGroup` (`useSetPayoutMethod`) → Banks card (`RadioGroup` default via `useSetDefaultBank`, remove via `useRemoveBankAccount`, `LinkBankForm`) → Cards card (`useSetDefaultCard`, `useRemoveCard`, `AddCardForm`) → `DigitalWalletsPanel` (view-only).
- `WithdrawTab.tsx`: "Send to" `RadioGroup` (banks+cards merged) → amount → step `form` (`useInitiateWithdrawal`, sends OTP) → step `otp` (`useConfirmWithdrawal`) → **Recent withdrawals** list (`useWithdrawals`).
- `ProfileAddressSection.tsx`: single address form doing enroll-once + update (`useSaveAddress` → `PUT /redemption/profile/address`); button flips "Save & enroll" ↔ "Update address".
- `DigitalWalletsPanel.tsx`: view-only wallet balances (`useDigitalWallets` → `GET /redemption/profile/wallets`); `formatFiat`; "Receives your payouts" badge on the USD wallet.
- Hooks: `hooks/redemption-payout/useRedemptionProfile.ts` (queries), `useRedemptionProfileMutations.ts` (mutations + `xtrmErrorCode`/`friendlyXtrmError`). Service: `services/redemption-payout/redemption-payout.service.ts` (base `/redemption/profile`). Types: `types/redemption-payout/redemption-payout.types.ts`. Tests under `components/redemption-payout/__tests__/`.

## 4. Target layout

```
Payout tab (single)
├─ Enrollment banner (Alert)                         (unchanged)
├─ Payout profile  ── collapsed summary after enroll ── [Edit]   (ProfileAddressSection)
├─ "Payouts go to: <label> (<method>)"  summary line              (new, derived)
├─ Sub-tabs:  [ Bank Accounts ] [ Cards ] [ Digital Wallet ]     (new shadcn Tabs)
│     Bank Accounts → list + Set default + remove + LinkBankForm
│     Cards         → list + Set default + remove + AddCardForm
│     Digital Wallet→ wallet card: name + balance + [Withdraw] + [Set as default]
└─ Recent withdrawals                                (moved from WithdrawTab, bottom)
```

## 5. Unified default model (no backend change)

Today: payout **method** (`useSetPayoutMethod`: ANYPAY/BANK/CARD) is separate from per-rail default (`useSetDefaultBank`/`useSetDefaultCard`). New behavior — "Set as default" in a tab sets the **single payout destination** by composing the existing calls:

| Tab action | Calls |
|---|---|
| Bank → Set as default | `setDefaultBank(id)` then `setPayoutMethod("BANK")` |
| Card → Set as default | `setDefaultCard(id)` then `setPayoutMethod("CARD")` |
| Digital Wallet → Set as default | `setPayoutMethod("ANYPAY")` |

- Compose as a small helper `useSetDefaultDestination()` that sequences the two mutations. **Partial-failure mechanism (required):** the set-default/set-method mutations use `setQueryData(["redemption-profile"], …)`, NOT `invalidateQueries` — so if `setPayoutMethod` fails after `setDefaultBank` succeeded, the cache holds the new default bank but the *old* `payoutMethod` and won't self-correct. The helper MUST `invalidateQueries({queryKey:["redemption-profile", userId]})` on failure (and surface the inline error).
- **Do NOT copy the existing "already default" early-return** (`if (bankId === defaultBankId) return`). In the unified model, an account can already be the rail default while `payoutMethod` is a *different* rail — clicking "Set as default" must still fire `setPayoutMethod` to switch the destination.
- **"Payouts go to" line** derives from `profile.payoutMethod` + the profile's own `linkedBankLabel` / `linkedCardLabel` (always in-sync, simpler than cross-referencing the lists) / "Digital Wallet".
- **"Default" badge = the single global destination, not per-rail.** `setDefaultBank`/`setDefaultCard` set a *persistent per-rail* `isDefault`, so after switching rails an old rail's `isDefault` lingers. Render the badge on exactly one account: `account.isDefault && profile.payoutMethod === <thatRail>`. Every other account — including a *different* rail's own `isDefault` — shows "Set as default". This keeps the badge in lockstep with "Payouts go to". (Wallet's badge = `profile.payoutMethod === "ANYPAY"`.)
- The standalone method `RadioGroup` is **removed** (its `PayoutTab.test` radio assertions must be rewritten in the *same* commit or the suite goes red mid-refactor).

## 6. Withdraw modal

- New `WithdrawDialog.tsx` — a shadcn `Dialog` (`src/components/ui/dialog.tsx`, Radix) wrapping the **extracted** two-step flow from `WithdrawTab` (destination `RadioGroup` from banks+cards, amount, `useInitiateWithdrawal` → OTP step → `useConfirmWithdrawal`), including the existing inline `xtrmErrorCode`/`friendlyXtrmError` handling and success `Alert`. `WithdrawTab` takes no props and has zero routing/tab coupling, so the form/OTP logic moves intact.
- **a11y (required):** wrap in `DialogHeader > DialogTitle` (e.g. "Withdraw from your wallet") — Radix `DialogContent` warns/violates without a title.
- **State reset for free:** put the step/amount/otp `useState` *inside* `DialogContent` — Radix unmounts content on close, so state resets on close automatically (no manual reset).
- **Success behavior:** on confirm, show the success `Alert` in-dialog (net/fee/destination) and let the user close (don't auto-close) — matches today's UX.
- **Balance invalidation [RECOMMENDED — see §2a]:** add `invalidateQueries(["digital-wallets", userId])` **inside `useConfirmWithdrawal`** (not the dialog) so both the balance and the `["withdrawals", userId]` history refresh; today the hook invalidates history only. Without it, the now co-located balance may briefly show stale after a withdrawal (refreshes on next refetch / 60s staleTime).
- Fix the now-self-referential copy inside the extracted flow: "…on the Payout tab" strings (`WithdrawTab` lines 87, 169) — the flow now *lives* on the Payout tab.
- Recent-withdrawals block is **removed** from the withdraw flow (relocated — §7).

## 7. Recent withdrawals

- New `RecentWithdrawals.tsx` — the list extracted from `WithdrawTab` (`useWithdrawals`, `formatFiat`, status badge, empty state "No withdrawals yet"). Rendered at the **bottom** of `PayoutTab`, gated on enrollment.
- **Query placement (required):** keep `useLinkedBanks` / `useLinkedCards` / `useWithdrawals` mounted at the **`PayoutTab` parent** level, NOT inside the sub-tab panels — Radix `Tabs` unmounts inactive panels, so the withdraw modal (opened from the Digital Wallet sub-tab) would otherwise have no banks/cards data.

## 8. Payout profile (address)

- Keep `ProfileAddressSection` behavior (enroll-once + update). Add a **collapsed summary** state once `enrollmentStatus === "ENROLLED"`: one line (`📍 line1, city, COUNTRY · Edit`) that expands to the existing form.
- **[OPTIONAL — default off under §2a]** **Prefill** the address block inside `LinkBankForm` / `AddCardForm` from the saved profile address (reduce re-entry). This is **not just "defaults"**: both forms hard-init `useState(EMPTY)` and accept only `onLinked`/`onCancel` — so it needs (a) a new `defaultAddress` prop and (b) changing the `useState` initializer. **Keep the raw card fields empty** (`AddCardForm` sets `autoComplete="off"` as a PCI measure — prefill only the address block, never PAN/CVV/expiry).

## 9. URL / back-compat

- Remove the `Withdraw` `TabsTrigger`/`TabsContent` + import from `MyProfilePage` (lines ~40, 256, 522–526) and drop `"withdraw"` from the `canPayout`-conditional `VALID_TABS` (line 52).
- **`?tab=withdraw` redirect is REQUIRED, not optional.** `activeTab` falls back to `"profile"` (not payout) for any tab not in `VALID_TABS` — so simply removing `"withdraw"` lands legacy links on **Profile**. Add an explicit effect: `if (tabParam === "withdraw") setSearchParams({tab:"payout"})`, and only for `canPayout` users (a non-payout user should keep falling back to Profile). **Do the removal + redirect in the same commit** to avoid an interim wrong-tab state.
- Optional: auto-open `WithdrawDialog` from the legacy link (behind `?tab=payout&withdraw=1`).

## 10. Enrollment gating

- **Correction from review:** only `WithdrawTab` and `DigitalWalletsPanel` gate on enrollment today — `PayoutTab`'s bank/card lists and `LinkBankForm`/`AddCardForm` are **ungated** (the server 422s `XTRM_NOT_ENROLLED` and the FE surfaces it inline). So disabling bank/card *linking* pre-enrollment would be a **new behavior change**, not a "mirror".
- **Decision (default): keep current behavior** — leave bank/card linking ungated (server-guarded), and keep the existing enrollment gates on **withdraw** and **wallet balance**. This avoids scope creep. (If you'd rather hard-gate linking behind enrollment, call it out — it's a deliberate change, not free.)

## 11. Labels

- "AnyPay wallet" → **"Digital Wallet"** everywhere in the FE (matches the BE enum `displayName`). Section header "Payout profile" (not "Address"). Tabs: "Bank Accounts" / "Cards" / "Digital Wallet".

## 12. Tests (update / add under `components/redemption-payout/__tests__/`)

- `PayoutTab.test` (rewrite) — renders sub-tabs; "Payouts go to" reflects default; "Set as default" per tab calls the composed mutation; **method radio assertions removed in the same commit as the radio** (current test asserts `radio` roles by name + "link a card below…" copy — those go red otherwise); Recent Withdrawals at bottom. Must still `vi.mock` `useLinkBankAccount`/`useAddCard` (the current test does).
- `WithdrawDialog.test` (new, ported from `WithdrawTab.test`) — open from wallet, initiate → OTP → confirm, error mapping, balance+history invalidation.
- `DigitalWalletsPanel.test` — Withdraw + Set-default buttons; opens dialog; wallet "default" badge from `payoutMethod === "ANYPAY"`.
- `RecentWithdrawals.test` (**new**) — list + empty state.
- `ProfileAddressSection.test` (**new — does not exist today**) — collapsed summary after enroll, expand-to-edit.
- Delete `WithdrawTab.test` (component removed).
- `MyProfilePage.test` is unaffected (it runs with no payout permission, so it never renders the Payout/Withdraw tabs).

## 13. Multi-wallet readiness

- Digital Wallet tab already renders a list; when multi-wallet lands, each wallet card carries its own [Withdraw]/[Set as default] and the withdraw modal takes a `walletId`. No structural change needed now.

## 14. Out of scope

- No backend/contract changes. No new payout methods. No changes to enrollment/OTP/withdrawal *logic* (only relocation into a modal). Multi-wallet creation/selection deferred.

## 15. Build order (small, reviewable PRs or commits)

1. Extract `RecentWithdrawals` + `WithdrawDialog` from `WithdrawTab` (behavior-preserving); wire dialog open from a temporary button — verify parity.
2. Restructure `PayoutTab` into sub-tabs; add "Payouts go to" summary; unified `useSetDefaultDestination`; remove method radio.
3. Add [Withdraw]/[Set default] to `DigitalWalletsPanel`; mount `RecentWithdrawals` at the bottom of `PayoutTab`.
4. Remove `Withdraw` top-level tab + `?tab=withdraw` redirect.
5. Collapsible Payout profile + prefill instrument addresses.
6. Label sweep ("Digital Wallet"). Tests throughout.

## 16. Risks / watch-outs

- **Partial default update**: two sequenced mutations — handle failure between them (refetch, surface error) so the UI never shows a false default.
- **Withdraw extraction**: `WithdrawTab` holds local `step`/`otp` state + XTRM error mapping — must move intact into the dialog; don't regress the OTP UX or inline error codes.
- **Balance staleness after withdraw**: invalidate `useDigitalWallets` on confirm (new — today's WithdrawTab didn't need to).
- **Back-compat**: existing links/bookmarks to `?tab=withdraw` must not 404 — redirect.
- **Enrollment gating** must stay consistent across the new sub-tabs and the modal.

## 17. Open questions

- Auto-open the withdraw modal from the legacy `?tab=withdraw` link, or just land on Payout? (default: just land on Payout)
- Show the "Payouts go to" summary even before any account is linked (prompt to add one), or hide until a default exists? (default: show a prompt)

## 18. Review outcome (independent code-review, 2026-07-17)

**Verdict: fundamentally feasible** — every hook, type, endpoint, and UI primitive already exists; the Withdraw flow has zero prop/routing coupling so it extracts cleanly. Corrections folded into the plan above:

- **`?tab=withdraw` trap (§9):** removing the tab lands legacy links on *Profile*, not Payout — explicit redirect effect now required, same commit.
- **Partial-failure invalidate (§5):** mutations use `setQueryData`, not `invalidate`; the composed helper must `invalidateQueries(["redemption-profile", userId])` on failure.
- **"Already default" guard (§5):** must not early-return, or the destination won't switch when the account is the rail default but the method differs.
- **Enrollment gating (§10):** bank/card linking is *ungated* today — hard-gating it would be a new behavior change; default is to keep current (server-guarded).
- **Modal a11y (§6):** `DialogTitle` required.
- **Balance staleness (§6):** invalidate `["digital-wallets", userId]` inside `useConfirmWithdrawal`.
- **Query placement (§7):** keep `useLinkedBanks/useLinkedCards/useWithdrawals` at the `PayoutTab` parent — Radix Tabs unmount inactive panels, else the modal has no data.
- **Prefill (§8):** needs a `defaultAddress` prop + `useState` init change; keep raw card fields empty (PCI).
- **Tests (§12):** `ProfileAddressSection.test` and `RecentWithdrawals.test` are new; `PayoutTab.test` radio assertions must be rewritten in the radio-removal commit; `MyProfilePage.test` unaffected.
- **Self-referential copy (§6):** "…on the Payout tab" strings inside the withdraw flow must be fixed on extraction.
- **Summary source (§5):** use the profile's own `linkedBankLabel`/`linkedCardLabel` (always in-sync) for "Payouts go to".

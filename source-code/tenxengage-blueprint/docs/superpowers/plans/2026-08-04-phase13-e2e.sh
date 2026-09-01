#!/usr/bin/env bash
# Phase 13 E2E — §0.2 (partner seller), §0.3 (partner admin), and all three distribution rails.
# Usage: DEV_PASSWORD='...' bash phase13-e2e.sh
#
# Auth is an HTTP-only cookie (LoginResponse carries no token), so every call uses a cookie jar.
# Never aborts on failure: each check reports PASS/FAIL and the script exits non-zero if any failed.

set -uo pipefail
BASE=http://localhost:8080
TMP="${CLAUDE_JOB_DIR:-/tmp}/tmp"; mkdir -p "$TMP"
PSQL="/c/Program Files/PostgreSQL/17/bin/psql.exe"
export PGPASSWORD=localdev
PASS=0; FAIL=0
ADMIN_JAR="$TMP/admin.cookies"; SELLER_JAR="$TMP/seller.cookies"; CADMIN_JAR="$TMP/cadmin.cookies"

q() { "$PSQL" -h localhost -U tenxengage -d tenxengage -tAF'|' -c "$1" 2>&1; }

ok()   { PASS=$((PASS+1)); printf '  \033[32mPASS\033[0m  %s\n' "$1"; }
bad()  { FAIL=$((FAIL+1)); printf '  \033[31mFAIL\033[0m  %s\n' "$1"; [ -n "${2:-}" ] && printf '        %s\n' "$2"; }
head1() { printf '\n\033[1m%s\033[0m\n' "$1"; }

# req <jar> <METHOD> <path> [json]  -> writes body to $TMP/resp.json, echoes status
req() {
  local jar=$1 verb=$2 path=$3 data=${4:-}
  local out="$TMP/resp.json"
  if [ -n "$data" ]; then
    code=$(curl -s -o "$out" -w '%{http_code}' -m 30 -b "$jar" -c "$jar" \
      -X "$verb" "$BASE$path" -H 'Content-Type: application/json' -d "$data")
  else
    code=$(curl -s -o "$out" -w '%{http_code}' -m 30 -b "$jar" -c "$jar" -X "$verb" "$BASE$path")
  fi
  echo "$code"
}
# The body lives in a file precisely because req() is invoked inside $( ) — a subshell — so any
# variable it assigns is discarded the moment that subshell exits.
body() { cat "$TMP/resp.json" 2>/dev/null; }
jget() { python3 -c "
import json,sys
try: d=json.loads(sys.argv[1])
except Exception: print(''); sys.exit()
for k in sys.argv[2].split('.'):
    if d is None: break
    if k.isdigit() and isinstance(d,list): d=d[int(k)] if int(k)<len(d) else None
    elif isinstance(d,dict): d=d.get(k)
    else: d=None
print('' if d is None else d)" "$1" "$2" 2>/dev/null; }

check() { # check <label> <actual> <expected...>
  local label=$1 actual=$2; shift 2
  for e in "$@"; do [ "$actual" = "$e" ] && { ok "$label ($actual)"; return; }; done
  bad "$label — got $actual, wanted $*" "$(body | head -c 220)"
}

# ─────────────────────────────────────────────────────────── login
head1 "AUTH"
rm -f "$ADMIN_JAR" "$SELLER_JAR"
c=$(req "$ADMIN_JAR" POST /api/v1/auth/login "{\"email\":\"partneradmin@techpartners.com\",\"password\":\"$DEV_PASSWORD\"}")
check "partner admin login" "$c" 200
ADMIN_UID=$(jget "$(body)" user.id); ADMIN_ROLE=$(jget "$(body)" user.clientRoleName)
ADMIN_COMPANY=$(jget "$(body)" user.partnerCompanyId)
echo "        admin=$ADMIN_UID role=$ADMIN_ROLE company=$ADMIN_COMPANY"

c=$(req "$SELLER_JAR" POST /api/v1/auth/login "{\"email\":\"seller@techpartners.com\",\"password\":\"$DEV_PASSWORD\"}")
check "partner seller login" "$c" 200
SELLER_UID=$(jget "$(body)" user.id); SELLER_ROLE=$(jget "$(body)" user.clientRoleName)
echo "        seller=$SELLER_UID role=$SELLER_ROLE"

c=$(req "$CADMIN_JAR" POST /api/v1/auth/login "{\"email\":\"clientadmin@acme.com\",\"password\":\"$DEV_PASSWORD\"}")
check "client admin login (holds action.wallet.fund_company)" "$c" 200

[ -z "$ADMIN_UID" ] && { echo "cannot continue without an admin session"; exit 1; }

# ─────────────────────────────────────────────── §0.3 row 1: THE FIX
head1 "§0.3 #1 — partner admin redeems from their OWN wallet (was 403 before V52)"
c=$(req "$ADMIN_JAR" GET /api/v1/wallets/me)
check "admin GET /wallets/me" "$c" 200
echo "        admin wallets: $(body | python3 -c "import json,sys;d=json.load(sys.stdin).get('data') or [];print(', '.join(f\"{w['currencyId']}={w['availableBalance']}\" for w in d) or 'NONE')" 2>/dev/null)"
# The regression contract's row 1 is a PERMISSION fix. 403 = the old bug (no `redeem` grant);
# 4xx-but-not-403 = permission resolved and only a business rule (no wallet / balance) blocks.
c=$(req "$ADMIN_JAR" POST /api/v1/redemption/requests '{"catalogItemId":"00000000-0000-0000-0000-000000000000","amount":"10.00","currencyId":"cash"}')
if [ "$c" = "403" ]; then bad "§0.3 #1 admin personal redeem still 403 — V52 grant not effective" "$(body | head -c 200)"
else ok "§0.3 #1 admin personal redeem is NOT 403 (got $c — permission resolved)"; fi
c=$(req "$ADMIN_JAR" GET "/api/v1/redemption/catalog?page=0&pageSize=5")
check "admin can browse the store (nav/visibility, §0.3 #4)" "$c" 200

# ─────────────────────────────────────────────── §0.3 #2 payout profile
head1 "§0.3 #2 — payout profile survives redeem_company deletion (via redeem)"
for path in /api/v1/redemption/profile /api/v1/redemption/profile/banks /api/v1/redemption/profile/cards /api/v1/redemption/profile/wallets; do
  c=$(req "$ADMIN_JAR" GET "$path")
  check "admin $path" "$c" 200 404 422
done
for path in /api/v1/redemption/profile /api/v1/redemption/profile/banks; do
  c=$(req "$SELLER_JAR" GET "$path")
  check "seller $path" "$c" 200 404 422
done

# ─────────────────────────────────────────────── §0.3 #3 export PERSONAL
head1 "§0.3 #3 / §0.2 #6 — CSV export at PERSONAL scope; COMPANY scope rejected"
c=$(req "$ADMIN_JAR" POST /api/v1/redemption/requests/export '{"format":"CSV","scope":"PERSONAL"}')
check "admin export PERSONAL" "$c" 200 201 422
c=$(req "$ADMIN_JAR" POST /api/v1/redemption/requests/export '{"format":"CSV","scope":"COMPANY"}')
check "admin export COMPANY is now REJECTED" "$c" 400 422
c=$(req "$SELLER_JAR" POST /api/v1/redemption/requests/export '{"format":"CSV","scope":"PERSONAL"}')
check "seller export PERSONAL" "$c" 200 201 422

# ─────────────────────────────────────────────── retired endpoints
head1 "Phase 9 — retired endpoints are GONE for an authenticated caller"
c=$(req "$ADMIN_JAR" POST /api/v1/redemption/requests/company '{"catalogItemId":"00000000-0000-0000-0000-000000000000","amount":"1.00","currencyId":"cash","companyId":"00000000-0000-0000-0000-000000000000"}')
check "POST /requests/company gone (405: no such mapping)" "$c" 404 405
c=$(req "$ADMIN_JAR" GET /api/v1/redemption/requests/company)
check "GET /requests/company gone (400: \"company\" now parsed as {id})" "$c" 400 404 405

# ─────────────────────────────────────────────── §0.2 seller personal flow
head1 "§0.2 — partner seller personal flow"
c=$(req "$SELLER_JAR" GET "/api/v1/redemption/catalog?page=0&pageSize=10")
check "§0.2 #1 browse store" "$c" 200
CATALOG_ID=$(jget "$(body)" data.data.0.id); CATALOG_CUR=$(jget "$(body)" data.data.0.currencyId)
CATALOG_MIN=$(jget "$(body)" data.data.0.effectiveMinTransactionAmount)
echo "        first item: $CATALOG_ID cur=$CATALOG_CUR min=$CATALOG_MIN"
c=$(req "$SELLER_JAR" GET "/api/v1/redemption/requests?page=0&pageSize=10")
check "§0.2 #5 history list" "$c" 200
FIRST_REQ=$(jget "$(body)" data.data.0.id)
if [ -n "$FIRST_REQ" ]; then
  c=$(req "$SELLER_JAR" GET "/api/v1/redemption/requests/$FIRST_REQ")
  check "§0.2 #5 history detail" "$c" 200
fi
c=$(req "$SELLER_JAR" GET /api/v1/wallets/me)
check "§0.2 #9 wallet balance widget source" "$c" 200

# ─────────────────────────────────────────────── T-13.3 distribution
head1 "T-13.3 — fund the company wallet (Phase 8 API replaces manual DB insert)"
BEFORE_FUND=$(q "SELECT COALESCE(available_balance::numeric(12,2)::text,'none') FROM reward_wallets WHERE partner_company_id='$ADMIN_COMPANY' AND wallet_type='COMPANY' AND currency_id='cash';")
echo "        company wallet before: ${BEFORE_FUND:-none}"
REF="e2e-$(q "SELECT to_char(now(),'YYYYMMDDHH24MISS');")"
# Funding requires action.wallet.fund_company, held only by CLIENT_ADMIN. A partner admin must NOT be able
# to create balance for their own company — assert that boundary first, then fund as the correct role.
c=$(req "$ADMIN_JAR" POST "/api/v1/wallets/company/$ADMIN_COMPANY/fund" \
   "{\"currencyId\":\"cash\",\"amount\":\"1000.00\",\"reference\":\"$REF-denied\",\"note\":\"must be denied\"}")
check "partner admin CANNOT fund their own company wallet" "$c" 403
c=$(req "$CADMIN_JAR" POST "/api/v1/wallets/company/$ADMIN_COMPANY/fund" \
   "{\"currencyId\":\"cash\",\"amount\":\"1000.00\",\"reference\":\"$REF\",\"note\":\"phase 13 e2e\"}")
check "client admin funds company wallet 1000.00" "$c" 200 201
SRC_WALLET=$(jget "$(body)" data.id)
[ -z "$SRC_WALLET" ] && SRC_WALLET=$(q "SELECT id FROM reward_wallets WHERE partner_company_id='$ADMIN_COMPANY' AND wallet_type='COMPANY' AND currency_id='cash';")
AFTER_FUND=$(q "SELECT available_balance::numeric(12,2)::text FROM reward_wallets WHERE id='$SRC_WALLET';")
echo "        company wallet after: $AFTER_FUND (wallet=$SRC_WALLET)"

# idempotency: same reference must NOT double-credit
c=$(req "$CADMIN_JAR" POST "/api/v1/wallets/company/$ADMIN_COMPANY/fund" \
   "{\"currencyId\":\"cash\",\"amount\":\"1000.00\",\"reference\":\"$REF\",\"note\":\"replay\"}")
AFTER_REPLAY=$(q "SELECT available_balance::numeric(12,2)::text FROM reward_wallets WHERE id='$SRC_WALLET';")
if [ "$AFTER_REPLAY" = "$AFTER_FUND" ]; then ok "funding is idempotent on reference (balance still $AFTER_REPLAY)"
else bad "funding replayed! $AFTER_FUND -> $AFTER_REPLAY (status $c)"; fi

head1 "T-13.3 — recipients and distributable catalog"
c=$(req "$ADMIN_JAR" GET "/api/v1/redemption/distribution/recipients?rail=WALLET_CREDIT")
check "GET /distribution/recipients" "$c" 200
R0=$(jget "$(body)" data.0.userId); R1=$(jget "$(body)" data.1.userId)
RCOUNT=$(python3 -c "import json,sys;d=json.loads(sys.argv[1]);print(len(d.get('data') or []))" "$(body)" 2>/dev/null)
echo "        $RCOUNT eligible recipient(s); first=$R0 second=$R1"
if [ -z "$R0" ]; then bad "recipient list empty — cannot check OQ-7"
elif [ "$R0" != "$ADMIN_UID" ]; then ok "OQ-7: admin is not their own recipient"
else bad "admin appears in their own recipient list"; fi
c=$(req "$ADMIN_JAR" GET /api/v1/redemption/distribution/catalog)
check "GET /distribution/catalog" "$c" 200
GC_ITEM=$(jget "$(body)" data.0.catalogItemId); [ -z "$GC_ITEM" ] && GC_ITEM=$(jget "$(body)" data.0.id)
echo "        gift card SKU: $GC_ITEM"

# ---- rail 1: WALLET_CREDIT
# First recipient the SERVER reports as eligible for that rail. Bank and gift card require an XTRM payout
# profile, so picking an arbitrary seller only ever proves the 422 — never that the rail works.
eligible_for() {
  req "$ADMIN_JAR" GET "/api/v1/redemption/distribution/recipients?rail=$1" >/dev/null
  body | python3 -c "
import json,sys
for r in (json.load(sys.stdin).get('data') or []):
    if r.get('eligible'): print(r['userId']); break"
}

head1 "T-13.3 rail 1/3 — WALLET_CREDIT (recipient's own cash wallet)"
if [ -n "$R0" ]; then
  RB=$(q "SELECT COALESCE(available_balance::numeric(12,2)::text,'0') FROM reward_wallets WHERE user_id='$R0' AND wallet_type='INDIVIDUAL' AND currency_id='cash';")
  CB=$(q "SELECT available_balance::numeric(12,2)::text FROM reward_wallets WHERE id='$SRC_WALLET';")
  c=$(req "$ADMIN_JAR" POST /api/v1/redemption/distribution \
    "{\"rail\":\"WALLET_CREDIT\",\"sourceWalletId\":\"$SRC_WALLET\",\"amount\":\"25.00\",\"userIds\":[\"$R0\"],\"note\":\"e2e wallet credit\"}")
  check "POST distribution WALLET_CREDIT" "$c" 200 201 202
  DIST_WC=$(jget "$(body)" data.id)
  sleep 6
  RA=$(q "SELECT available_balance::numeric(12,2)::text FROM reward_wallets WHERE user_id='$R0' AND wallet_type='INDIVIDUAL' AND currency_id='cash';")
  CA=$(q "SELECT available_balance::numeric(12,2)::text FROM reward_wallets WHERE id='$SRC_WALLET';")
  echo "        recipient $RB -> $RA | company $CB -> $CA"
  python3 - "$RB" "$RA" "$CB" "$CA" <<'PY'
import sys
rb,ra,cb,ca=[float(x or 0) for x in sys.argv[1:5]]
print("  PASS  recipient credited +25.00" if abs(ra-rb-25)<0.001 else f"  FAIL  recipient delta {ra-rb} != 25")
print("  PASS  company debited -25.00" if abs(cb-ca-25)<0.001 else f"  FAIL  company delta {cb-ca} != 25")
PY
  LEDG=$(q "SELECT entry_type||':'||amount::numeric(12,2) FROM ledger_entries WHERE reward_wallet_id IN (SELECT id FROM reward_wallets WHERE user_id='$R0' AND wallet_type='INDIVIDUAL' AND currency_id='cash') ORDER BY created_at DESC LIMIT 2;")
  echo "        recipient ledger (latest): $(echo "$LEDG" | tr '\n' ' ')"
  echo "$LEDG" | grep -q "CREDIT" && ok "recipient CREDIT ledger entry written" || bad "no CREDIT ledger entry for recipient"
  NOLEG=$(q "SELECT count(*) FROM company_distribution_items i WHERE i.distribution_id='$DIST_WC' AND i.redemption_request_id IS NOT NULL;")
  [ "$NOLEG" = "0" ] && ok "WALLET_CREDIT created NO redemption row (by design)" || bad "WALLET_CREDIT wrote $NOLEG redemption leg(s)"
else bad "no eligible recipient — cannot exercise the rails"; fi

# ---- rail 2: BANK_TRANSFER
head1 "T-13.3 rail 2/3 — BANK_TRANSFER"
BT_R=$(eligible_for BANK_TRANSFER)
if [ -z "$BT_R" ]; then
  bad "no BANK_TRANSFER-eligible recipient (needs an XTRM payout profile) — rail NOT exercised"
else
  echo "        eligible recipient: $BT_R"
  c=$(req "$ADMIN_JAR" POST /api/v1/redemption/distribution \
    "{\"rail\":\"BANK_TRANSFER\",\"sourceWalletId\":\"$SRC_WALLET\",\"amount\":\"15.00\",\"userIds\":[\"$BT_R\"],\"note\":\"e2e bank\"}")
  check "POST distribution BANK_TRANSFER" "$c" 200 201 202
  DIST_BT=$(jget "$(body)" data.id)
  if [ -n "$DIST_BT" ]; then
    sleep 6
    ST=$(q "SELECT DISTINCT COALESCE(r.status::text, i.status::text) FROM company_distribution_items i LEFT JOIN redemption_requests r ON r.id=i.redemption_request_id WHERE i.distribution_id='$DIST_BT';")
    echo "        item status: $(echo "$ST" | tr '\n' ' ')"
    LEG=$(q "SELECT count(*) FROM company_distribution_items WHERE distribution_id='$DIST_BT' AND redemption_request_id IS NOT NULL;")
    [ "$LEG" -ge 1 ] && ok "BANK_TRANSFER created a redemption payout leg ($LEG)" || bad "no payout leg for BANK_TRANSFER"
    ORIG=$(q "SELECT DISTINCT r.origin FROM company_distribution_items i JOIN redemption_requests r ON r.id=i.redemption_request_id WHERE i.distribution_id='$DIST_BT';")
    [ "$ORIG" = "COMPANY_DISTRIBUTION" ] && ok "leg tagged origin=COMPANY_DISTRIBUTION" || bad "leg origin='$ORIG'"
  fi
fi

# ---- rail 3: GIFT_CARD
head1 "T-13.3 rail 3/3 — GIFT_CARD"
GC_R=$(eligible_for GIFT_CARD)
if [ -z "$GC_R" ] || [ -z "$GC_ITEM" ]; then
  bad "no GIFT_CARD-eligible recipient or no SKU — rail NOT exercised"
else
  echo "        eligible recipient: $GC_R  SKU: $GC_ITEM"
  c=$(req "$ADMIN_JAR" POST /api/v1/redemption/distribution \
    "{\"rail\":\"GIFT_CARD\",\"sourceWalletId\":\"$SRC_WALLET\",\"catalogItemId\":\"$GC_ITEM\",\"amount\":\"10.00\",\"userIds\":[\"$GC_R\"],\"note\":\"e2e gift card\"}")
  check "POST distribution GIFT_CARD" "$c" 200 201 202
  DIST_GC=$(jget "$(body)" data.id)
  if [ -n "$DIST_GC" ]; then
    sleep 6
    GST=$(q "SELECT DISTINCT COALESCE(r.status::text,i.status::text) FROM company_distribution_items i LEFT JOIN redemption_requests r ON r.id=i.redemption_request_id WHERE i.distribution_id='$DIST_GC';")
    echo "        gift-card item status: $(echo "$GST" | tr '\n' ' ')"
    GLEG=$(q "SELECT count(*) FROM company_distribution_items WHERE distribution_id='$DIST_GC' AND redemption_request_id IS NOT NULL;")
    [ "${GLEG:-0}" -ge 1 ] && ok "GIFT_CARD created a redemption payout leg ($GLEG)" || bad "no payout leg for GIFT_CARD"
    GORIG=$(q "SELECT DISTINCT r.origin FROM company_distribution_items i JOIN redemption_requests r ON r.id=i.redemption_request_id WHERE i.distribution_id='$DIST_GC';")
    [ "$GORIG" = "COMPANY_DISTRIBUTION" ] && ok "gift-card leg tagged origin=COMPANY_DISTRIBUTION" || bad "gift-card leg origin='$GORIG'"
  fi
fi

head1 "T-13.3 — an ineligible recipient is REPORTED with a reason, not silently dropped"
req "$ADMIN_JAR" GET "/api/v1/redemption/distribution/recipients?rail=BANK_TRANSFER" >/dev/null
INELIG=$(body | python3 -c "
import json,sys
d=json.load(sys.stdin).get('data') or []
print(len([r for r in d if not r.get('eligible') and r.get('ineligibleReason')]))")
if [ "${INELIG:-0}" -ge 1 ]; then ok "ineligible recipient carries an explanatory reason ($INELIG)"
else bad "no ineligible recipient carried a reason — the UI would silently omit them"; fi

# ---- validation guards
head1 "T-13.3 — request validation guards"
c=$(req "$ADMIN_JAR" POST /api/v1/redemption/distribution \
  "{\"rail\":\"WALLET_CREDIT\",\"sourceWalletId\":\"$SRC_WALLET\",\"catalogItemId\":\"$GC_ITEM\",\"amount\":\"5.00\",\"userIds\":[\"$R0\"]}")
check "catalogItemId on a non-GIFT_CARD rail is rejected" "$c" 400 422
c=$(req "$ADMIN_JAR" POST /api/v1/redemption/distribution \
  "{\"rail\":\"GIFT_CARD\",\"sourceWalletId\":\"$SRC_WALLET\",\"amount\":\"5.00\",\"userIds\":[\"$R0\"]}")
check "GIFT_CARD without catalogItemId is rejected" "$c" 400 422
c=$(req "$ADMIN_JAR" POST /api/v1/redemption/distribution \
  "{\"rail\":\"WALLET_CREDIT\",\"sourceWalletId\":\"$SRC_WALLET\",\"amount\":\"5.00\",\"userIds\":[\"$ADMIN_UID\"]}")
check "admin distributing to SELF is rejected (OQ-7)" "$c" 400 422
c=$(req "$ADMIN_JAR" POST /api/v1/redemption/distribution \
  "{\"rail\":\"WALLET_CREDIT\",\"sourceWalletId\":\"$SRC_WALLET\",\"amount\":\"9999999.00\",\"userIds\":[\"$R0\"]}")
check "insufficient company balance is rejected" "$c" 400 422
c=$(req "$SELLER_JAR" POST /api/v1/redemption/distribution \
  "{\"rail\":\"WALLET_CREDIT\",\"sourceWalletId\":\"$SRC_WALLET\",\"amount\":\"5.00\",\"userIds\":[\"$R0\"]}")
check "a SELLER cannot distribute (403)" "$c" 403

# ---- history + awards
head1 "T-13.3 — Distribution History (admin) and Company Awards (seller)"
c=$(req "$ADMIN_JAR" GET "/api/v1/redemption/distribution?page=0&pageSize=10")
check "GET /distribution (history list)" "$c" 200
echo "        rows: $(python3 -c "import json,sys;d=json.loads(sys.argv[1]);x=d.get('data') or {};print(x.get('totalElements', len(x.get('data') or [])))" "$(body)" 2>/dev/null)"
if [ -n "${DIST_WC:-}" ]; then
  c=$(req "$ADMIN_JAR" GET "/api/v1/redemption/distribution/$DIST_WC")
  check "GET /distribution/{id} (detail — the Map.of NPE path)" "$c" 200
fi
c=$(req "$SELLER_JAR" GET "/api/v1/redemption/distribution/awards?page=0&pageSize=10")
check "seller GET /distribution/awards" "$c" 200
AWARD=$(jget "$(body)" data.data.0.id)
if [ -n "$AWARD" ]; then
  c=$(req "$SELLER_JAR" GET "/api/v1/redemption/distribution/awards/$AWARD")
  check "seller award detail (all-wallet-transfer NPE path)" "$c" 200
fi
c=$(req "$ADMIN_JAR" GET "/api/v1/redemption/distribution/awards?page=0&pageSize=5")
echo "        admin on /awards -> $c (informational)"

# ---- OQ-3: analytics must not move
head1 "OQ-3 — distributions must NOT appear in redemption analytics"
LEAK=$(q "SELECT count(*) FROM mv_item_redemption_breakdown m
          WHERE EXISTS (SELECT 1 FROM redemption_requests r
                        WHERE r.origin='COMPANY_DISTRIBUTION' AND r.catalog_item_id=m.catalog_item_id
                          AND r.submitted_at::date = m.period_date AND r.client_id=m.client_id);")
DISTROWS=$(q "SELECT count(*) FROM redemption_requests WHERE origin='COMPANY_DISTRIBUTION';")
echo "        distribution legs in redemption_requests: $DISTROWS"
# PLAIN refresh, matching AnalyticsMvRefreshScheduler. CONCURRENTLY cannot work here: the uq_mv_*
# indexes are expression-based (COALESCE over nullable region/role), which Postgres disqualifies.
q "REFRESH MATERIALIZED VIEW mv_item_redemption_breakdown;" >/dev/null 2>&1 && ok "MV refresh works after the V56 rebuild" || bad "MV refresh failed after rebuild"
SELFONLY=$(q "SELECT count(*) FROM redemption_requests r WHERE r.origin='COMPANY_DISTRIBUTION'
              AND EXISTS (SELECT 1 FROM mv_redemption_rate_trend t
                          WHERE t.client_id=r.client_id AND t.period_date=r.submitted_at::date
                            AND t.currency_type=r.currency_id AND t.redeemed_count > 0
                            AND NOT EXISTS (SELECT 1 FROM redemption_requests s WHERE s.origin='SELF'
                                            AND s.client_id=r.client_id AND s.submitted_at::date=r.submitted_at::date));")
[ "$SELFONLY" = "0" ] && ok "no analytics row exists solely because of a distribution" || bad "$SELFONLY distribution-only analytics row(s) leaked"

printf '\n\033[1m════ RESULT: %d passed, %d failed ════\033[0m\n' "$PASS" "$FAIL"
[ "$FAIL" -eq 0 ] || exit 1

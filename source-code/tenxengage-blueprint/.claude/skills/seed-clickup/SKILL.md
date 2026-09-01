---
name: seed-clickup
description: Seed ClickUp with Epic → Milestone → UserStory hierarchy from a roadmap's backlog-seeds.csv. Reads roadmaps/{slug}/backlog-seeds.csv, creates ClickUp tasks with parent-child nesting, caches IDs for idempotent re-runs.
type: skill
---

# Seed ClickUp Backlog

Creates an **Epic** (Roadmap) → **Milestone** (Feature) → **UserStory** (Story) hierarchy in ClickUp from `roadmaps/{slug}/backlog-seeds.csv`.

**When to use:** After `/decompose-brd` generates a roadmap, to create the ClickUp project hierarchy automatically. Can also be run standalone at any time.

---

## Phase 1: Resolve roadmap and configuration

### Step 1: Check CLICKUP_API_TOKEN

```bash
if [ -z "$CLICKUP_API_TOKEN" ]; then
  echo "ERROR: CLICKUP_API_TOKEN is not set."
  echo "Add it to ~/.claude/settings.json under the 'env' key:"
  echo '  "env": { "CLICKUP_API_TOKEN": "pk_xxx" }'
  exit 1
fi
```

If `CLICKUP_API_TOKEN` is empty → abort with the message above. Do not proceed.

### Step 2: List available roadmaps and prompt user

```bash
ls -d roadmaps/*/  2>/dev/null | sed 's|roadmaps/||;s|/$||' | sort
```

Present the output as a numbered list and ask the user which roadmap to seed:

```
Available roadmaps:
  1. partner-revenue-readiness
  2. partner-onboarding

Which roadmap? Enter number or slug:
```

Save the chosen slug as `SLUG`. If no roadmaps exist under `roadmaps/`, abort:
```
No roadmaps found under roadmaps/. Run /decompose-brd first.
```

### Step 3: Read roadmap Epic title

```bash
grep "^# " roadmaps/$SLUG/roadmap.md | head -1 | sed 's/^# //'
```

Save as `EPIC_TITLE`. This will be the name of the top-level Epic task in ClickUp.

If `roadmaps/$SLUG/roadmap.md` does not exist:
```
ERROR: roadmaps/$SLUG/roadmap.md not found. Has /decompose-brd been run for this slug?
```
Abort.

### Step 4: Resolve ClickUp List ID

Check in this order:

**4a.** `$CLICKUP_BACKLOG_LIST_ID` env var — use if set and non-empty.

**4b.** `roadmaps/$SLUG/.clickup-ids.json` `.listId` field — use if file exists and field is non-empty:
```bash
python3 -c "
import json
try:
    with open('roadmaps/$SLUG/.clickup-ids.json') as f:
        c = json.load(f)
    print(c.get('listId',''))
except:
    print('')
"
```

**4c.** If both are empty, prompt the user:
```
CLICKUP_BACKLOG_LIST_ID is not set.
Enter the ClickUp List ID to seed into (the numeric ID from the list URL):
```
After the user enters the ID, ask:
```
Save this List ID to .claude/settings.json as CLICKUP_BACKLOG_LIST_ID? (yes/no)
```
If yes:
```bash
python3 -c "
import json, os
settings_path = os.path.expanduser('~/.claude/settings.json')
try:
    with open(settings_path, 'r') as f:
        s = json.load(f)
except:
    s = {}
s.setdefault('env', {})['CLICKUP_BACKLOG_LIST_ID'] = '$LIST_ID'
with open(settings_path, 'w') as f:
    json.dump(s, f, indent=2)
print(f'Saved to {settings_path}')
"
```

Save the resolved value as `LIST_ID`.

---

## Phase 2: Load cache and parse CSV

### Step 5: Load idempotency cache

```bash
python3 -c "
import json
try:
    with open('roadmaps/$SLUG/.clickup-ids.json') as f:
        print(json.dumps(json.load(f)))
except:
    print('{\"listId\":\"\",\"epic\":{},\"features\":{},\"stories\":{}}')
"
```

Store the result as `CACHE` (JSON string in memory).

### Step 6: Parse CSV into features and stories

```bash
python3 - "roadmaps/$SLUG/backlog-seeds.csv" <<'PYEOF'
import csv, json, sys

csv_path = sys.argv[1]
rows = []
try:
    with open(csv_path) as f:
        for row in csv.DictReader(f):
            rows.append({
                'feature_id':       row['feature_id'].strip(),
                'feature_name':     row['feature_name'].strip(),
                'seed_id':          row['seed_id'].strip(),
                'title':            row['title'].strip(),
                'business_outcome': row['business_outcome'].strip(),
            })
except Exception as e:
    print(json.dumps({'error': str(e)}))
    sys.exit(1)

# Deduplicated feature list preserving first-occurrence order
seen = set()
features = []
for r in rows:
    k = r['feature_id']
    if k not in seen:
        seen.add(k)
        features.append({'id': k, 'name': r['feature_name']})

print(json.dumps({'rows': rows, 'features': features}))
PYEOF
```

If the output contains `"error"`, abort with the parse error message.

Store `features` list and `rows` list in memory for Phase 3.

---

## Phase 3: Create ClickUp hierarchy

Initialize counters: `CREATED=0`, `SKIPPED=0`, `FAILED=0`.

**Idempotency rule for every item:**
- Key exists in `CACHE` → verify: `curl -s -o /dev/null -w "%{http_code}" -H "Authorization: $CLICKUP_API_TOKEN" "https://api.clickup.com/api/v2/task/$CACHED_ID"`
  - HTTP 200 → skip (increment `SKIPPED`)
  - HTTP 404 → re-create (clear the cached ID, proceed to create)
- Key absent from `CACHE` → create

**Rate limit handling for every POST:** If response body contains `"err":"Rate limit reached"`, sleep 2 seconds and retry once. If still failing, warn and increment `FAILED`.

**List ID validation:** If any POST returns HTTP 404 with a body containing `"List not found"` → abort immediately:
```
ERROR: List not found. Check your CLICKUP_BACKLOG_LIST_ID value: $LIST_ID
```

### Step 7a: Create/verify Epic

```bash
# Extract cached Epic ID
EPIC_ID=$(echo "$CACHE" | python3 -c "import sys,json; c=json.load(sys.stdin); print(c.get('epic',{}).get('id',''))")

# Verify if cached
if [ -n "$EPIC_ID" ]; then
  HTTP=$(curl -s -o /dev/null -w "%{http_code}" \
    -H "Authorization: $CLICKUP_API_TOKEN" \
    "https://api.clickup.com/api/v2/task/$EPIC_ID")
  if [ "$HTTP" = "200" ]; then
    echo "Epic [$EPIC_TITLE]: skip"
    SKIPPED=$((SKIPPED+1))
  else
    EPIC_ID=""   # will create below
  fi
fi

# Create if needed
if [ -z "$EPIC_ID" ]; then
  SAFE_TITLE=$(printf '%s' "$EPIC_TITLE" | python3 -c "import sys,json; print(json.dumps(sys.stdin.read()))")
  RESP=$(curl -s -X POST "https://api.clickup.com/api/v2/list/$LIST_ID/task" \
    -H "Authorization: $CLICKUP_API_TOKEN" \
    -H "Content-Type: application/json" \
    -d "{\"name\": $SAFE_TITLE, \"custom_item_id\": 1001}")

  if echo "$RESP" | grep -q '"err".*"Rate limit reached"'; then
    sleep 2
    RESP=$(curl -s -X POST "https://api.clickup.com/api/v2/list/$LIST_ID/task" \
      -H "Authorization: $CLICKUP_API_TOKEN" \
      -H "Content-Type: application/json" \
      -d "{\"name\": $SAFE_TITLE, \"custom_item_id\": 1001}")
  fi

  # Check for List not found
  if echo "$RESP" | grep -q '"List not found"'; then
    echo "ERROR: List not found. Check CLICKUP_BACKLOG_LIST_ID: $LIST_ID"
    exit 1
  fi

  EPIC_ID=$(echo "$RESP" | python3 -c "import sys,json; print(json.load(sys.stdin).get('id',''))" 2>/dev/null)

  if [ -z "$EPIC_ID" ]; then
    echo "ERROR: Epic creation failed. Response: $RESP"
    exit 1
  fi

  echo "Epic [$EPIC_TITLE]: created ($EPIC_ID)"
  CREATED=$((CREATED+1))

  # Update cache
  CACHE=$(echo "$CACHE" | python3 -c "
import sys,json
c=json.load(sys.stdin)
c['listId']='$LIST_ID'
c.setdefault('epic',{})['id']='$EPIC_ID'
print(json.dumps(c))")
fi
```

If Epic creation fails with an empty ID (after retry) → abort. Do not attempt features/stories without an Epic.

### Step 7b: Create/verify Features (Milestones)

For each feature in `features` array (iterate in order):

```bash
# Variables: FEAT_KEY = feature_id (e.g. "F-01"), FEAT_NAME = feature_name
FEATURE_DISPLAY="$FEAT_KEY · $FEAT_NAME"

# Check cache
MILESTONE_ID=$(echo "$CACHE" | python3 -c "import sys,json; c=json.load(sys.stdin); print(c.get('features',{}).get('$FEAT_KEY',{}).get('id',''))")

if [ -n "$MILESTONE_ID" ]; then
  HTTP=$(curl -s -o /dev/null -w "%{http_code}" \
    -H "Authorization: $CLICKUP_API_TOKEN" \
    "https://api.clickup.com/api/v2/task/$MILESTONE_ID")
  if [ "$HTTP" = "200" ]; then
    echo "  Feature $FEAT_KEY: skip"
    SKIPPED=$((SKIPPED+1))
    # Keep MILESTONE_ID — used for stories below
  else
    MILESTONE_ID=""
  fi
fi

if [ -z "$MILESTONE_ID" ]; then
  SAFE_NAME=$(printf '%s' "$FEATURE_DISPLAY" | python3 -c "import sys,json; print(json.dumps(sys.stdin.read()))")
  RESP=$(curl -s -X POST "https://api.clickup.com/api/v2/list/$LIST_ID/task" \
    -H "Authorization: $CLICKUP_API_TOKEN" \
    -H "Content-Type: application/json" \
    -d "{\"name\": $SAFE_NAME, \"parent\": \"$EPIC_ID\", \"custom_item_id\": 1}")

  if echo "$RESP" | grep -q '"err".*"Rate limit reached"'; then
    sleep 2
    RESP=$(curl -s -X POST "https://api.clickup.com/api/v2/list/$LIST_ID/task" \
      -H "Authorization: $CLICKUP_API_TOKEN" \
      -H "Content-Type: application/json" \
      -d "{\"name\": $SAFE_NAME, \"parent\": \"$EPIC_ID\", \"custom_item_id\": 1}")
  fi

  if echo "$RESP" | grep -q '"List not found"'; then
    echo "ERROR: List not found. Check CLICKUP_BACKLOG_LIST_ID: $LIST_ID"
    exit 1
  fi

  MILESTONE_ID=$(echo "$RESP" | python3 -c "import sys,json; print(json.load(sys.stdin).get('id',''))" 2>/dev/null)

  if [ -z "$MILESTONE_ID" ]; then
    echo "  WARN: Feature $FEAT_KEY creation failed: $RESP"
    FAILED=$((FAILED+1))
    # Skip stories for this feature — no parent ID available
    continue
  fi

  echo "  Feature $FEAT_KEY [$FEAT_NAME]: created ($MILESTONE_ID)"
  CREATED=$((CREATED+1))

  CACHE=$(echo "$CACHE" | python3 -c "
import sys,json
c=json.load(sys.stdin)
c.setdefault('features',{})['$FEAT_KEY']={'id':'$MILESTONE_ID'}
print(json.dumps(c))")
fi
```

If a Feature fails to create → warn, increment `FAILED`, skip its stories. Continue to the next feature.

### Step 7c: Create/verify Stories (Tasks)

For each story row belonging to the current feature (iterate in CSV order):

```bash
# Variables: STORY_TITLE = title, STORY_DESC = business_outcome, SEED_ID = seed_id
STORY_KEY="$FEAT_KEY.$SEED_ID"   # e.g. "F-01.S-01"

# Check cache
TASK_ID=$(echo "$CACHE" | python3 -c "import sys,json; c=json.load(sys.stdin); print(c.get('stories',{}).get('$STORY_KEY',{}).get('id',''))")

if [ -n "$TASK_ID" ]; then
  HTTP=$(curl -s -o /dev/null -w "%{http_code}" \
    -H "Authorization: $CLICKUP_API_TOKEN" \
    "https://api.clickup.com/api/v2/task/$TASK_ID")
  if [ "$HTTP" = "200" ]; then
    echo "    Story $STORY_KEY: skip"
    SKIPPED=$((SKIPPED+1))
    continue
  fi
  TASK_ID=""
fi

SAFE_TITLE=$(printf '%s' "$STORY_TITLE" | python3 -c "import sys,json; print(json.dumps(sys.stdin.read()))")
SAFE_DESC=$(printf '%s' "$STORY_DESC" | python3 -c "import sys,json; print(json.dumps(sys.stdin.read()))")

RESP=$(curl -s -X POST "https://api.clickup.com/api/v2/list/$LIST_ID/task" \
  -H "Authorization: $CLICKUP_API_TOKEN" \
  -H "Content-Type: application/json" \
  -d "{\"name\": $SAFE_TITLE, \"description\": $SAFE_DESC, \"parent\": \"$MILESTONE_ID\", \"custom_item_id\": 1002}")

if echo "$RESP" | grep -q '"err".*"Rate limit reached"'; then
  sleep 2
  RESP=$(curl -s -X POST "https://api.clickup.com/api/v2/list/$LIST_ID/task" \
    -H "Authorization: $CLICKUP_API_TOKEN" \
    -H "Content-Type: application/json" \
    -d "{\"name\": $SAFE_TITLE, \"description\": $SAFE_DESC, \"parent\": \"$MILESTONE_ID\", \"custom_item_id\": 1002}")
fi

if echo "$RESP" | grep -q '"List not found"'; then
  echo "ERROR: List not found. Check CLICKUP_BACKLOG_LIST_ID: $LIST_ID"
  exit 1
fi

TASK_ID=$(echo "$RESP" | python3 -c "import sys,json; print(json.load(sys.stdin).get('id',''))" 2>/dev/null)

if [ -z "$TASK_ID" ]; then
  echo "    WARN: Story $STORY_KEY creation failed: $RESP"
  FAILED=$((FAILED+1))
else
  echo "    Story $STORY_KEY [$STORY_TITLE]: created ($TASK_ID)"
  CREATED=$((CREATED+1))

  CACHE=$(echo "$CACHE" | python3 -c "
import sys,json
c=json.load(sys.stdin)
c.setdefault('stories',{})['$STORY_KEY']={'id':'$TASK_ID'}
print(json.dumps(c))")
fi
```

If a Story fails to create → warn, increment `FAILED`. Continue to the next story.

---

## Phase 4: Persist and summarize

### Step 8: Save idempotency cache

```bash
echo "$CACHE" | python3 -c "
import sys, json
c = json.load(sys.stdin)
with open('roadmaps/$SLUG/.clickup-ids.json', 'w') as f:
    json.dump(c, f, indent=2)
print('Cache saved to roadmaps/$SLUG/.clickup-ids.json')
" || echo "WARN: Could not write .clickup-ids.json — tasks were created but idempotency cache was not updated."
```

### Step 9: Print summary

```
ClickUp seeding complete
  Roadmap:  $SLUG
  Epic:     $EPIC_TITLE
  List ID:  $LIST_ID
  Epic URL: https://app.clickup.com/t/$EPIC_ID

  Created:  $CREATED
  Skipped:  $SKIPPED
  Failed:   $FAILED
```

If `FAILED > 0`, append:
```
  ⚠ Some items failed. Re-run /seed-clickup to retry.
```
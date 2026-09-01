---
name: clickup-client
description: Reusable ClickUp API call patterns for bug-reporter and bug-fixer: fetch, comment, status update, attachment download
type: reference
---

# ClickUp API Client Patterns

Common API patterns shared by `bug-reporter` and `bug-fixer`. All calls use `$CLICKUP_API_TOKEN` from the environment.

---

## Prerequisites check

```bash
if [ -z "$CLICKUP_API_TOKEN" ] || [ -z "$CLICKUP_BUGS_LIST_ID" ]; then
  echo "ERROR: CLICKUP_API_TOKEN and CLICKUP_BUGS_LIST_ID must be set."
  echo "Add to your shell profile: export CLICKUP_API_TOKEN=pk_xxx && export CLICKUP_BUGS_LIST_ID=xxxxxxxxx"
  exit 1
fi
```

---

## Fetch tasks from the list

**Oldest unresolved (default):**
```bash
curl -s "https://api.clickup.com/api/v2/list/$CLICKUP_BUGS_LIST_ID/task?order_by=created&reverse=false&page=0&include_closed=false" \
  -H "Authorization: $CLICKUP_API_TOKEN" \
  -H "Content-Type: application/json"
```

**Newest unresolved:**
```bash
curl -s "https://api.clickup.com/api/v2/list/$CLICKUP_BUGS_LIST_ID/task?order_by=created&reverse=true&page=0&include_closed=false" \
  -H "Authorization: $CLICKUP_API_TOKEN" \
  -H "Content-Type: application/json"
```

Pick `response.tasks[0]` for both cases.

---

## Fetch full task details

```bash
curl -s "https://api.clickup.com/api/v2/task/$TASK_ID?include_subtasks=false" \
  -H "Authorization: $CLICKUP_API_TOKEN"
```

---

## Fetch task comments

```bash
curl -s "https://api.clickup.com/api/v2/task/$TASK_ID/comment" \
  -H "Authorization: $CLICKUP_API_TOKEN"
```

---

## Search tasks by keyword

```bash
curl -s "https://api.clickup.com/api/v2/list/$CLICKUP_BUGS_LIST_ID/task?search=$KEYWORD&include_closed=true" \
  -H "Authorization: $CLICKUP_API_TOKEN"
```

Use for duplicate detection. URL-encode `$KEYWORD`.

---

## Download image attachments

For each entry in `task.attachments` where `mimetype` starts with `image/`:

```bash
curl -s -L "$ATTACHMENT_URL" \
  -H "Authorization: $CLICKUP_API_TOKEN" \
  -o "/tmp/clickup-attach-$TASK_ID-$i.png"
```

Then use the Read tool to view each downloaded image. Skip video attachments (mimetype `video/*`).

---

## Post a comment

```bash
curl -s -X POST "https://api.clickup.com/api/v2/task/$TASK_ID/comment" \
  -H "Authorization: $CLICKUP_API_TOKEN" \
  -H "Content-Type: application/json" \
  -d "{\"comment_text\": \"$COMMENT_TEXT\", \"notify_all\": false}"
```

---

## Update task status

Status names must come from `clickup-lifecycle.md` (single source of truth). Do NOT hardcode status strings here.

```bash
curl -s -X PUT "https://api.clickup.com/api/v2/task/$TASK_ID" \
  -H "Authorization: $CLICKUP_API_TOKEN" \
  -H "Content-Type: application/json" \
  -d "{\"status\": \"$STATUS_NAME\"}"
```

---

## Discover custom field IDs (run once per session, cache results)

```bash
# Returns JSON with .fields[].id and .fields[].name for every custom field on the list
FIELDS_JSON=$(curl -s "https://api.clickup.com/api/v2/list/$CLICKUP_BUGS_LIST_ID/field" \
  -H "Authorization: $CLICKUP_API_TOKEN")

# Extract IDs for the standard bug fields
FIELD_ID_AFFECTED_REPOS=$(echo "$FIELDS_JSON" | python3 -c "import sys,json; f=[x for x in json.load(sys.stdin)['fields'] if x['name']=='affected_repos']; print(f[0]['id'] if f else '')")
FIELD_ID_FIX_BRANCH=$(echo "$FIELDS_JSON"     | python3 -c "import sys,json; f=[x for x in json.load(sys.stdin)['fields'] if x['name']=='fix_branch']; print(f[0]['id'] if f else '')")
FIELD_ID_BASE_BRANCH=$(echo "$FIELDS_JSON"    | python3 -c "import sys,json; f=[x for x in json.load(sys.stdin)['fields'] if x['name']=='base_branch']; print(f[0]['id'] if f else '')")
FIELD_ID_FIX_MRS=$(echo "$FIELDS_JSON"        | python3 -c "import sys,json; f=[x for x in json.load(sys.stdin)['fields'] if x['name']=='fix_mrs']; print(f[0]['id'] if f else '')")
FIELD_ID_LINKED_DUPS=$(echo "$FIELDS_JSON"    | python3 -c "import sys,json; f=[x for x in json.load(sys.stdin)['fields'] if x['name']=='linked_duplicates']; print(f[0]['id'] if f else '')")
```

If a field ID is empty, that custom field doesn't exist — skip the update for that field silently.

---

## Set a text custom field

```bash
curl -s -X POST "https://api.clickup.com/api/v2/task/$TASK_ID/field/$FIELD_ID" \
  -H "Authorization: $CLICKUP_API_TOKEN" \
  -H "Content-Type: application/json" \
  -d "{\"value\": \"$VALUE\"}"
```

---

## Set a Labels (multi-select) custom field (e.g. `affected_repos`)

Labels fields require option IDs, not strings. First discover the option IDs from the field definition:

```bash
# Extract option IDs matching your repo names
REPO_OPTION_IDS=$(echo "$FIELDS_JSON" | python3 -c "
import sys, json
fields = json.load(sys.stdin)['fields']
target_repos = ['frontend', 'backend']   # ← replace with actual affected repos
f = next((x for x in fields if x['name'] == 'affected_repos'), None)
if f:
    opts = [o['id'] for o in f.get('type_config', {}).get('options', []) if o.get('label', '').lower() in target_repos]
    print(json.dumps(opts))
else:
    print('[]')
")

# Set the field (pass option IDs as JSON array)
curl -s -X POST "https://api.clickup.com/api/v2/task/$TASK_ID/field/$FIELD_ID_AFFECTED_REPOS" \
  -H "Authorization: $CLICKUP_API_TOKEN" \
  -H "Content-Type: application/json" \
  -d "{\"value\": $REPO_OPTION_IDS}"
```

If `REPO_OPTION_IDS` is `[]` (options not found in the workspace config), skip the update.

---

## Create a new task

```bash
curl -s -X POST "https://api.clickup.com/api/v2/list/$CLICKUP_BUGS_LIST_ID/task" \
  -H "Authorization: $CLICKUP_API_TOKEN" \
  -H "Content-Type: application/json" \
  -d "$(cat <<EOF
{
  "name": "$TASK_TITLE",
  "description": "$TASK_DESCRIPTION",
  "status": "pending",
  "custom_fields": []
}
EOF
)"
```

The response contains the new task's `id`. Save this for subsequent calls.

---

## Error handling

| HTTP status | Action |
|---|---|
| 401 | Stop. "Check your CLICKUP_API_TOKEN." |
| 404 | Stop. "List or task not found. Check CLICKUP_BUGS_LIST_ID or task ID." |
| 429 | Retry after 2 seconds (rate limit). Max 3 retries. |
| 5xx | Stop. "ClickUp API error. Check https://status.clickup.com." |
| Empty `tasks` array | "No open bugs found in the list. All caught up!" |
# Orchestrator Return Format

<!-- SYNC: duplicated in tenxengage-backend and tenxengage-frontend. Update both repos together. -->

Emit when `$GATE != every` OR `$PHASE == implement`. One line per key, prefixed `ORCHESTRATOR_RETURN `.

```
ORCHESTRATOR_RETURN status=<success|failure|awaiting-approval>
ORCHESTRATOR_RETURN unit_id=<US-NN-BE>
ORCHESTRATOR_RETURN branch=work/<slug>-<US-NN>-be
ORCHESTRATOR_RETURN ready_check=<green|red|advisory-only|not-run>
ORCHESTRATOR_RETURN antipattern_pass=<clean | one-line list of unresolved anti-pattern items>
ORCHESTRATOR_RETURN advisory_findings_count=<integer>
ORCHESTRATOR_RETURN advisory_findings_path=<.ready-check/.../advisory.json or "none">
ORCHESTRATOR_RETURN diff_stat=<single-line `git diff --stat features/{feature-id}..HEAD` output, semicolon-joined if multi-line>
ORCHESTRATOR_RETURN summary=<2-3 sentence summary>
```

**Additional fields ONLY when `status=failure`:**

```
ORCHESTRATOR_RETURN failed_stage=<stage>
ORCHESTRATOR_RETURN failed_reason=<one-line>
ORCHESTRATOR_RETURN findings_path=<abs file path>
ORCHESTRATOR_RETURN repo_abs_path=<abs repo path>
```

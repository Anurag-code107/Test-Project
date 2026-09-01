# Contracts Ritual

<!-- SYNC: duplicated in tenxengage-backend and tenxengage-frontend. Update both repos together. -->

When any review (ready-check, code, security, adversarial) requires a contracts change:

1. Edit the relevant section of `../tenxengage-blueprint/features/{feature-id}/spec.md`
2. Re-run `cd ../tenxengage-contracts && /generate-contracts {feature-id}` (idempotent)
3. Contracts repo: `git add`, commit to `features/$FEATURE_SLUG`, `git push`
4. Backend repo: `git submodule update --remote contracts`
5. `git add contracts && git commit -m "$STORY_ID BE: update contracts submodule"`
6. Re-run `./gradlew test`

After completing the ritual, re-run the check that triggered it.

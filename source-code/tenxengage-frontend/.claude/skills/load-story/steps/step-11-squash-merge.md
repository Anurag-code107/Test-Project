### 11. Local squash-merge into the feature branch

```
git checkout features/{feature-id}
# only if remote branch exists: git pull --rebase origin features/{feature-id}
git merge --squash work/{feature-slug}-{US-NN}-fe
git commit -m "{US-NN} FE: {title}"
```

Capture SHA: `git rev-parse HEAD`.

## Next step

Read `steps/step-12-cleanup.md`.

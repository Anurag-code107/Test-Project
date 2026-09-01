### 9. Local squash-merge into the feature branch

```
git checkout features/{feature-id}
# only if remote branch exists: git pull --rebase origin features/{feature-id}
git merge --squash work/{feature-slug}-{US-NN}-be
git commit -m "{US-NN} BE: {title}"
```

Capture SHA: `git rev-parse HEAD`.

## Next step

Read `steps/step-10-cleanup.md`.

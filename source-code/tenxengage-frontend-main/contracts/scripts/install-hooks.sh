#!/bin/bash
# Install shared git hooks from scripts/hooks/ into .git/hooks/.
# Run once after cloning or pulling new hook files.

HOOKS_DIR="$(git rev-parse --show-toplevel)/scripts/hooks"
GIT_HOOKS_DIR="$(git rev-parse --show-toplevel)/.git/hooks"

for hook in "$HOOKS_DIR"/*; do
  name=$(basename "$hook")
  target="$GIT_HOOKS_DIR/$name"
  cp "$hook" "$target"
  chmod +x "$target"
  echo "Installed: .git/hooks/$name"
done

echo "Done."

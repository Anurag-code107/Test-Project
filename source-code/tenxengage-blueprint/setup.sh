#!/bin/bash
# TenXEngage Blueprint Setup
# Run this script from the tenxengage-blueprint/ directory to verify
# the workspace is configured correctly.

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PARENT_DIR="$(dirname "$SCRIPT_DIR")"

echo "=== TenXEngage Blueprint Setup ==="
echo ""
echo "Blueprint dir: $SCRIPT_DIR"
echo "Parent dir:    $PARENT_DIR"
echo ""

ALL_OK=true

check_skill() {
  local REPO_DIR="$1"
  local SKILL="$2"
  if [ -d "$REPO_DIR/.claude/skills/$SKILL" ]; then
    echo "       - $SKILL skill: installed"
  else
    echo "       - $SKILL skill: MISSING"
    ALL_OK=false
  fi
}

check_file() {
  local REPO_DIR="$1"
  local FILE="$2"
  if [ -f "$REPO_DIR/$FILE" ]; then
    echo "       - $FILE: found"
  else
    echo "       - $FILE: MISSING"
    ALL_OK=false
  fi
}

# ── Blueprint repo self-checks ──────────────────────────────────────────────
echo "  [BLUEPRINT] tenxengage-blueprint/"
check_file "$SCRIPT_DIR" "PROJECT-CONTEXT.md"
echo ""

# ── tenxengage-contracts ────────────────────────────────────────────────────
CONTRACTS_DIR="$PARENT_DIR/tenxengage-contracts"
if [ -d "$CONTRACTS_DIR/.git" ]; then
  echo "  [OK] tenxengage-contracts/ found"
  check_skill "$CONTRACTS_DIR" "generate-contracts"
  # Install git hooks (idempotent — safe to re-run)
  if [ -f "$CONTRACTS_DIR/scripts/install-hooks.sh" ]; then
    bash "$CONTRACTS_DIR/scripts/install-hooks.sh" > /dev/null 2>&1
    echo "       - git hooks: installed"
  else
    echo "       - git hooks: install script missing (scripts/install-hooks.sh)"
    ALL_OK=false
  fi
else
  echo "  [MISSING] tenxengage-contracts/ not found at $CONTRACTS_DIR"
  ALL_OK=false
fi
echo ""

# ── tenxengage-backend ──────────────────────────────────────────────────────
BACKEND_DIR="$PARENT_DIR/tenxengage-backend"
if [ -d "$BACKEND_DIR/.git" ]; then
  echo "  [OK] tenxengage-backend/ found"
  check_file "$BACKEND_DIR" "PROJECT-CONTEXT.md"
  check_skill "$BACKEND_DIR" "ready-check"
  check_skill "$BACKEND_DIR" "create-pr"
  check_skill "$BACKEND_DIR" "load-spec"
  check_skill "$BACKEND_DIR" "load-story"
  check_skill "$BACKEND_DIR" "execute-foundation"
  check_skill "$BACKEND_DIR" "run-tests"
  # Check contracts submodule is initialised
  if [ -f "$BACKEND_DIR/contracts/.git" ] || [ -d "$BACKEND_DIR/contracts/.git" ] || [ -f "$BACKEND_DIR/contracts/conventions.md" ]; then
    echo "       - contracts/ submodule: initialised"
  else
    echo "       - contracts/ submodule: NOT INITIALISED (run: git submodule update --init)"
    ALL_OK=false
  fi
else
  echo "  [MISSING] tenxengage-backend/ not found at $BACKEND_DIR"
  ALL_OK=false
fi
echo ""

# ── tenxengage-frontend ─────────────────────────────────────────────────────
FRONTEND_DIR="$PARENT_DIR/tenxengage-frontend"
if [ -d "$FRONTEND_DIR/.git" ]; then
  echo "  [OK] tenxengage-frontend/ found"
  check_file "$FRONTEND_DIR" "PROJECT-CONTEXT.md"
  check_skill "$FRONTEND_DIR" "ready-check"
  check_skill "$FRONTEND_DIR" "create-pr"
  check_skill "$FRONTEND_DIR" "load-spec"
  check_skill "$FRONTEND_DIR" "load-story"
  check_skill "$FRONTEND_DIR" "run-tests"
  check_skill "$FRONTEND_DIR" "ui-ux-review"
else
  echo "  [MISSING] tenxengage-frontend/ not found at $FRONTEND_DIR"
  ALL_OK=false
fi
echo ""

# ── Result ───────────────────────────────────────────────────────────────────
if [ "$ALL_OK" = true ]; then
  echo "=== Setup Verified ==="
else
  echo "=== Some items missing — see above ==="
fi

echo ""
echo "Workflow:"
echo "  1. cd tenxengage-blueprint && /create-spec 'your feature'"
echo "  2. cd tenxengage-blueprint && /create-stories <slug>"
echo "  3. cd tenxengage-contracts && /generate-contracts <slug>"
echo "  4. cd tenxengage-backend  && /load-spec <slug>   (then /load-story per US)"
echo "  5. cd tenxengage-frontend && /load-spec <slug>   (then /load-story per US)"
echo "  6. /ready-check before raising a PR"
echo "  7. /create-pr to validate report and create PR"

#!/usr/bin/env bash
# Stop hook: when core/ has uncommitted changes, run the core tests before the
# agent finishes its turn. A failure (exit 2) sends the output back to the agent
# so it fixes the tests instead of stopping.
input="$(cat)"

# Already continuing because of this hook: don't loop.
if printf '%s' "$input" | grep -q '"stop_hook_active"[[:space:]]*:[[:space:]]*true'; then
  exit 0
fi

cd "$CLAUDE_PROJECT_DIR" || exit 0

if [ -z "$(git status --porcelain -- core/)" ]; then
  exit 0
fi

if ! output="$(./gradlew :core:test -q 2>&1)"; then
  echo "core tests failed after your changes to core/. Fix them before finishing:" >&2
  printf '%s\n' "$output" | tail -n 60 >&2
  exit 2
fi
exit 0

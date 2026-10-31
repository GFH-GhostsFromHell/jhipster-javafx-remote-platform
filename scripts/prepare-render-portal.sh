#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
PORTAL="$ROOT/portal"
JDL="$ROOT/jhipster/remote-portal-render.jdl"

if ! command -v jhipster >/dev/null 2>&1; then
  echo "JHipster CLI was not found. Install it with:"
  echo "  npm install -g generator-jhipster@9.4.0"
  exit 1
fi

if [ -d "$PORTAL" ] && [ -n "$(ls -A "$PORTAL" 2>/dev/null || true)" ]; then
  echo "portal/ already exists; refusing to overwrite it."
  exit 1
fi

mkdir -p "$PORTAL"
cd "$PORTAL"
jhipster import-jdl "$JDL" --force

echo
cat <<'MSG'
Generated portal/ using the Render/JWT JDL.

Next:
  1. Merge the custom Java classes from portal-custom/src/main/java into portal/src/main/java.
  2. Merge the Angular feature-access component from portal-custom/angular into the generated admin UI.
  3. Commit the complete repository to GitHub.
  4. Create a Neon PostgreSQL database and add the three SPRING_DATASOURCE_* values in Render.
  5. Create a Render Blueprint from render.yaml.
MSG

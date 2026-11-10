#!/bin/sh
set -eu

# Render Blueprint binds database host, port, username, and password separately.
# Never log the connection string or secrets.
if [ -z "${STUDIO_DB_URL:-}" ]; then
  : "${STUDIO_DB_HOST:?Set STUDIO_DB_HOST or STUDIO_DB_URL}"
  : "${STUDIO_DB_NAME:?Set STUDIO_DB_NAME or STUDIO_DB_URL}"
  STUDIO_DB_URL="jdbc:postgresql://${STUDIO_DB_HOST}:${STUDIO_DB_PORT:-5432}/${STUDIO_DB_NAME}"
  export STUDIO_DB_URL
fi

: "${STUDIO_DB_USER:?Database username is required}"
: "${STUDIO_DB_PASSWORD:?Database password is required}"
: "${STUDIO_ADMIN_PASSWORD:?Admin password is required}"
: "${STUDIO_VIEWER_PASSWORD:?Viewer password is required}"

if [ -z "${STUDIO_CLIENT_BASE_URL:-}" ]; then
  : "${RENDER_EXTERNAL_HOSTNAME:?Set STUDIO_CLIENT_BASE_URL when not on Render}"
  STUDIO_CLIENT_BASE_URL="https://${RENDER_EXTERNAL_HOSTNAME}"
  export STUDIO_CLIENT_BASE_URL
fi

exec java -jar /app/studio.jar

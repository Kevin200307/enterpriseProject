#!/bin/sh
# Writes /config.js from container env so ONE image works in Compose and Kubernetes.
set -eu

API_BASE_URL="${API_BASE_URL:-http://localhost:8081}"
WS_URL="${WS_URL:-${API_BASE_URL%/}/ws}"
case "${ASSISTANT_ENABLED:-true}" in
  false|FALSE|0) ASSISTANT=false ;;
  *) ASSISTANT=true ;;
esac

# Reject anything that could break out of the JS string literals.
for v in "$API_BASE_URL" "$WS_URL"; do
  case "$v" in
    *\'*|*\"*|*\\*|*\<*|*\>*|*\;*) echo "runtime-config: refusing unsafe value: $v" >&2; exit 1 ;;
  esac
done

cat > /usr/share/nginx/html/config.js <<CFG
window.__SWIFTDROP__ = {
  apiBaseUrl: '${API_BASE_URL}',
  wsUrl: '${WS_URL}',
  assistantEnabled: ${ASSISTANT},
}
CFG
echo "runtime-config: api=${API_BASE_URL} ws=${WS_URL} assistant=${ASSISTANT}"

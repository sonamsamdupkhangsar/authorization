#!/usr/bin/env bash
set -euo pipefail

require_cmd() {
  command -v "$1" >/dev/null 2>&1 || {
    echo "Missing required command: $1" >&2
    exit 1
  }
}

require_var() {
  local name="$1"
  if [[ -z "${!name:-}" ]]; then
    echo "Missing required env var: $name" >&2
    exit 1
  fi
}

require_cmd curl
require_cmd jq

require_var BASE1
require_var BASE2
require_var CLIENT_ID
require_var SECRET1
require_var SECRET2

check_discovery() {
  local base="$1"
  curl -fsS "$base/.well-known/openid-configuration" |
    jq -e --arg iss "$base" '
      .issuer == $iss and
      .token_endpoint == ($iss + "/oauth2/token") and
      .jwks_uri == ($iss + "/oauth2/jwks")
    ' >/dev/null
}

token_request() {
  local base="$1"
  local client_id="$2"
  local secret="$3"

  curl -sS -u "$client_id:$secret" \
    -X POST "$base/oauth2/token" \
    -H 'Content-Type: application/x-www-form-urlencoded' \
    -d 'grant_type=client_credentials'
}

token_request_with_status() {
  local base="$1"
  local client_id="$2"
  local secret="$3"

  curl -sS -u "$client_id:$secret" \
    -X POST "$base/oauth2/token" \
    -H 'Content-Type: application/x-www-form-urlencoded' \
    -d 'grant_type=client_credentials' \
    -w '\n%{http_code}'
}

echo "Checking discovery documents..."
check_discovery "$BASE1"
check_discovery "$BASE2"

echo "Checking tenant 1 token issuance..."
token_request "$BASE1" "$CLIENT_ID" "$SECRET1" |
  jq -e '.access_token and .token_type == "Bearer"' >/dev/null

echo "Checking tenant 2 token issuance..."
token_request "$BASE2" "$CLIENT_ID" "$SECRET2" |
  jq -e '.access_token and .token_type == "Bearer"' >/dev/null

echo "Checking cross-tenant rejection..."
cross_response="$(token_request_with_status "$BASE2" "$CLIENT_ID" "$SECRET1")"
cross_body="$(printf '%s' "$cross_response" | sed '$d')"
cross_status="$(printf '%s' "$cross_response" | tail -n1)"

[[ "$cross_status" == "401" ]]
printf '%s' "$cross_body" | jq -e '.error == "invalid_client"' >/dev/null

echo "Smoke test passed."

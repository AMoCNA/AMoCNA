#!/bin/bash

# Configuration
RABBIT_HOST=${RABBIT_HOST:-localhost}
RABBIT_PORT=${RABBIT_PORT:-15672}
RABBIT_USER=${RABBIT_USER:-guest}
RABBIT_PASS=${RABBIT_PASS:-guest}

PROTOCOL=${1:-REST}
EXPECTED_STATUS=${2:-200}
AUTH_MECHANISM=${3:-NONE}

# Generate a random ID
ACTION_ID=$(cat /proc/sys/kernel/random/uuid 2>/dev/null || echo "test-action-$(date +%s)")

echo "---------------------------------------------------"
echo "Simulating Action for Themis"
echo "Action ID: $ACTION_ID"
echo "Protocol:  $PROTOCOL"
echo "Expected:  $EXPECTED_STATUS"
echo "Auth:      $AUTH_MECHANISM"
echo "---------------------------------------------------"

if [ "$PROTOCOL" == "SHELL" ]; then
  # Shell Example
  # Using jq --arg to safely build JSON without shell escaping hell
  ACTION_PAYLOAD=$(jq -n \
    --arg id "$ACTION_ID" \
    --arg proto "SHELL" \
    --arg inst "echo \"Hello Themis - ActionID: $ACTION_ID\"; exit $EXPECTED_STATUS" \
    --arg auth "$AUTH_MECHANISM" \
    --argjson timeout 10 \
    --argjson idempotent true \
    --argjson retries 3 \
    --argjson status "$EXPECTED_STATUS" \
    '{
      actionId: $id,
      protocol: $proto,
      instruction: $inst,
      method: null,
      payload: null,
      authMechanism: $auth,
      timeoutSeconds: $timeout,
      isIdempotent: $idempotent,
      maxRetries: $retries,
      expectedStatusCode: $status
    }')
else
  # REST Example
  ACTION_PAYLOAD=$(jq -n \
    --arg id "$ACTION_ID" \
    --arg proto "REST" \
    --arg inst "http://localhost:8080/actuator/health" \
    --arg auth "$AUTH_MECHANISM" \
    --argjson timeout 10 \
    --argjson idempotent true \
    --argjson retries 3 \
    --argjson status "$EXPECTED_STATUS" \
    '{
      actionId: $id,
      protocol: $proto,
      instruction: $inst,
      method: "GET",
      payload: null,
      authMechanism: $auth,
      timeoutSeconds: $timeout,
      isIdempotent: $idempotent,
      maxRetries: $retries,
      expectedStatusCode: $status
    }')
fi

# Build the final RabbitMQ Management API wrapper JSON
# This ensures the internal JSON is string-escaped correctly
RABBIT_WRAPPER=$(jq -n \
  --arg p "$ACTION_PAYLOAD" \
  '{
    properties: {content_type: "application/json"},
    routing_key: "action",
    payload: $p,
    payload_encoding: "string"
  }')

# Publish to RabbitMQ using Management API
# Using --data-binary @- to send JSON from stdin safely
RESPONSE=$(echo "$RABBIT_WRAPPER" | curl -s -u "$RABBIT_USER:$RABBIT_PASS" \
  -X POST "http://$RABBIT_HOST:$RABBIT_PORT/api/exchanges/%2f/amocna.direct.exchange/publish" \
  -H "content-type:application/json" \
  --data-binary @-)

if echo "$RESPONSE" | grep -q "routed\":true"; then
  echo "SUCCESS: Action published to amocna.action.queue"
  echo "Payload: $ACTION_PAYLOAD"
else
  echo "ERROR: Failed to publish action."
  echo "Response: $RESPONSE"
fi

#!/bin/bash

RABBIT_HOST=${RABBIT_HOST:-localhost}
RABBIT_PORT=${RABBIT_PORT:-15672}
RABBIT_USER=${RABBIT_USER:-guest}
RABBIT_PASS=${RABBIT_PASS:-guest}

echo "---------------------------------------------------"
echo "Observing Status Updates from Themis"
echo "Queue: amocna.status.queue"
echo "Press [CTRL+C] to stop"
echo "---------------------------------------------------"

while true; do
  RESPONSE=$(curl -s -u $RABBIT_USER:$RABBIT_PASS -X POST http://$RABBIT_HOST:$RABBIT_PORT/api/queues/%2f/amocna.status.queue/get \
    -H "content-type:application/json" \
    -d '{"count":1, "ackmode":"ack_requeue_false", "encoding":"auto"}')

  if [[ "$RESPONSE" != "[]" ]]; then
    echo "$(date '+%Y-%m-%d %H:%M:%S') - Received Status Update:"

    PAYLOAD=$(echo $RESPONSE | sed -e 's/.*"payload":"//' -e 's/","payload_encoding".*//' -e 's/\\"/"/g')

    echo "$PAYLOAD"
    echo "---------------------------------------------------"
  fi

  sleep 1
done

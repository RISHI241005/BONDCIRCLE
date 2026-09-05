#!/bin/sh
set -eu

/opt/java/openjdk/bin/java -cp /app/portgate PortGate &
gate_pid=$!

/opt/java/openjdk/bin/java \
  -Duser.timezone=UTC \
  -jar /app/chat-service.jar \
  --server.port=8081 &
java_pid=$!

stop_java() {
  kill -TERM "$java_pid" 2>/dev/null || true
  kill -TERM "$gate_pid" 2>/dev/null || true
  wait "$java_pid" 2>/dev/null || true
  wait "$gate_pid" 2>/dev/null || true
}

trap stop_java EXIT TERM INT
wait "$java_pid"

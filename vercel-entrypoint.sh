#!/bin/sh
set -eu

/opt/java/openjdk/bin/java \
  -Duser.timezone=UTC \
  -jar /app/chat-service.jar \
  --server.port=8081 &
java_pid=$!

stop_java() {
  kill -TERM "$java_pid" 2>/dev/null || true
  wait "$java_pid" 2>/dev/null || true
}

trap stop_java EXIT TERM INT
nginx -c /app/vercel-nginx.conf -g 'daemon off;'

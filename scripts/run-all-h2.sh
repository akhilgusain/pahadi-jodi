#!/usr/bin/env bash
# Starts every service + gateway in zero-infrastructure mode (H2, no Kafka/Redis). Logs: .run/logs, pids: .run/pids
# Usage: scripts/run-all-h2.sh start|stop|status   (build first: mvn -q install -DskipTests)
set -euo pipefail
cd "$(dirname "$0")/.."
RUN=.run; mkdir -p $RUN/logs
# name:port  (identity first: everyone else fetches its JWKS lazily on first request)
SERVICES="identity-service:8081 profile-service:8082 user-service:8083 matchmaking-service:8084 search-service:8085
chat-service:8086 notification-service:8087 subscription-service:8088 payment-service:8089 ai-recommendation-service:8090
media-service:8091 moderation-service:8092 horoscope-service:8093 analytics-service:8094 admin-service:8095
fraud-detection-service:8096 api-gateway:8080"
HEAP="${PJ_HEAP:--Xmx192m -Xss512k}"
case "${1:-start}" in
  start)
    for e in $SERVICES; do n=${e%%:*}; p=${e##*:}
      jar=services/$n/target/$n-0.1.0-SNAPSHOT.jar
      [ -f "$jar" ] || { echo "missing $jar (run: mvn -q install -DskipTests)"; exit 1; }
      PJ_ADMIN_EMAILS="${PJ_ADMIN_EMAILS:-admin@example.in}" SPRING_PROFILES_ACTIVE=h2 \
        nohup java $HEAP -jar "$jar" > $RUN/logs/$n.log 2>&1 & echo $! > $RUN/$n.pid
    done
    echo "started; waiting for health..."
    for e in $SERVICES; do p=${e##*:}; for i in $(seq 1 90); do curl -sf -m 1 localhost:$p/actuator/health >/dev/null && break; sleep 1; done; done
    "$0" status ;;
  stop) for f in $RUN/*.pid; do [ -f "$f" ] && kill "$(cat "$f")" 2>/dev/null || true; rm -f "$f"; done; echo stopped ;;
  status) for e in $SERVICES; do n=${e%%:*}; p=${e##*:}
      curl -sf -m 1 localhost:$p/actuator/health >/dev/null && echo "UP    $n :$p" || echo "DOWN  $n :$p"; done ;;
esac

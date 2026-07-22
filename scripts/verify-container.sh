#!/usr/bin/env sh
set -eu

image_name=${1:-system-data-service:verify}
service_name="system-data-verify-service-$$"

cleanup() { docker rm --force "$service_name" >/dev/null 2>&1 || true; }
trap cleanup EXIT INT TERM

mvn -B clean verify
test -f target/system-data-service-1.0.0.jar
docker build --tag "$image_name" .
test "$(docker image inspect --format '{{.Config.User}}' "$image_name")" = "10001:10001"
test "$(docker image inspect --format '{{json .Config.Healthcheck.Test}}' "$image_name")" != "null"

docker run --detach --name "$service_name" \
    --read-only --tmpfs /tmp:rw,noexec,nosuid,size=16m \
    "$image_name" >/dev/null

attempt=0
while [ "$attempt" -lt 60 ]; do
    state=$(docker inspect --format '{{.State.Status}} {{if .State.Health}}{{.State.Health.Status}}{{else}}missing{{end}}' "$service_name")
    if [ "$state" = "running healthy" ]; then break; fi
    if [ "${state%% *}" != "running" ]; then docker logs "$service_name"; exit 1; fi
    attempt=$((attempt + 1))
    sleep 1
done
test "$(docker inspect --format '{{.State.Health.Status}}' "$service_name")" = "healthy"
docker exec "$service_name" sh -c 'test "$(id -u)" = 10001 && test "$(id -g)" = 10001'

if docker exec "$service_name" wget --quiet --timeout=3 --tries=1 --spider \
        http://127.0.0.1:8103/internal/fixtures/status; then
    echo "fixture API unexpectedly enabled by default" >&2
    exit 1
fi

if docker exec "$service_name" wget --quiet --timeout=3 --tries=1 --spider \
        http://127.0.0.1:8103/internal/environments/status; then
    echo "environment-management API unexpectedly accepted an unauthenticated default request" >&2
    exit 1
fi

docker stop --time 25 "$service_name" >/dev/null
test "$(docker inspect --format '{{.State.ExitCode}}' "$service_name")" = "143"

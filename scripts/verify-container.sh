#!/usr/bin/env sh
set -eu

image_name=${1:-system-data-service:verify}
service_name="system-data-verify-service-$$"
fixture_service_name="system-data-fixture-verify-service-$$"
inbound_test_key=local-container-test-key-32-characters
downstream_test_token=local-downstream-environment-token-32-characters
test "$inbound_test_key" != "$downstream_test_token"

cleanup() {
    docker rm --force "$service_name" >/dev/null 2>&1 || true
    docker rm --force "$fixture_service_name" >/dev/null 2>&1 || true
}
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

docker run --detach --name "$fixture_service_name" \
    --read-only --tmpfs /tmp:rw,noexec,nosuid,size=16m \
    --env SPRING_PROFILES_ACTIVE=local \
    --env SYSTEM_DATA_FIXTURES_ENABLED=true \
    --env SYSTEM_DATA_ENVIRONMENT_MANAGEMENT_ENABLED=true \
    --env SYSTEM_DATA_INTERNAL_CALLER_KEY="$inbound_test_key" \
    --env SYSTEM_DATA_DOWNSTREAM_ENVIRONMENT_DATA_TOKEN="$downstream_test_token" \
    "$image_name" >/dev/null

attempt=0
while [ "$attempt" -lt 60 ]; do
    state=$(docker inspect --format '{{.State.Status}} {{if .State.Health}}{{.State.Health.Status}}{{else}}missing{{end}}' "$fixture_service_name")
    if [ "$state" = "running healthy" ]; then break; fi
    if [ "${state%% *}" != "running" ]; then docker logs "$fixture_service_name"; exit 1; fi
    attempt=$((attempt + 1))
    sleep 1
done
test "$(docker inspect --format '{{.State.Health.Status}}' "$fixture_service_name")" = "healthy"

fixture_response=$(docker exec "$fixture_service_name" wget --quiet --timeout=3 --tries=1 -O - \
    'http://127.0.0.1:8103/internal/fixtures/jobs/search?pageSize=20')
printf '%s' "$fixture_response" | grep -F '"totalResults":9' >/dev/null
printf '%s' "$fixture_response" | grep -F 'SYNTH-JOB-001' >/dev/null
if printf '%s' "$fixture_response" | grep -F 'LIVE_CAPTURED_FIXTURE' >/dev/null; then
    echo "fixture API returned a captured-provider marker" >&2
    exit 1
fi

states_response=$(docker exec "$fixture_service_name" wget --quiet --timeout=3 --tries=1 -O - \
    --header "X-System-Data-Key: $inbound_test_key" \
    'http://127.0.0.1:8103/internal/environments/states')
printf '%s' "$states_response" | grep -F '"scenario":"REGISTRATION_CLEAN"' >/dev/null
printf '%s' "$states_response" | grep -F '"scenario":"CROSS_USER_SECURITY"' >/dev/null
printf '%s' "$states_response" | grep -F '"scenario":"PROVIDER_FAILURE"' >/dev/null
if printf '%s' "$states_response" | grep -E '(local-container-test-key|https?://localhost|accessToken|refreshToken)' >/dev/null; then
    echo "named-state response exposed a credential or target URL" >&2
    exit 1
fi

if docker exec "$fixture_service_name" wget --quiet --timeout=3 --tries=1 --spider \
        'http://127.0.0.1:8103/internal/fixtures/jobs/search?scenario=PROVIDER_FAILURE'; then
    echo "provider-failure state unexpectedly returned fixture jobs" >&2
    exit 1
fi

docker stop --time 25 "$fixture_service_name" >/dev/null
test "$(docker inspect --format '{{.State.ExitCode}}' "$fixture_service_name")" = "143"

#!/bin/sh
set -eu

command -v docker >/dev/null 2>&1 || {
    echo "Docker Compose v2 is required for the Bedrock runtime smoke." >&2
    exit 2
}
docker compose version >/dev/null 2>&1 || {
    echo "Docker Compose v2 is required for the Bedrock runtime smoke." >&2
    exit 2
}
command -v python3 >/dev/null 2>&1 || {
    echo "Python 3 is required for the Bedrock runtime smoke." >&2
    exit 2
}

project="${SCENARIOCRAFT_BEDROCK_SMOKE_PROJECT:-scenariocraft-bedrock-smoke-$$}"
bedrock_port="${SCENARIOCRAFT_BEDROCK_PORT:-19132}"
container_bedrock_port=19132
timeout_seconds="${SCENARIOCRAFT_BEDROCK_SMOKE_TIMEOUT_SECONDS:-300}"

case "${project}" in
    ''|[!a-z0-9]*|*[!a-z0-9_-]*)
        echo "SCENARIOCRAFT_BEDROCK_SMOKE_PROJECT must use lowercase letters, digits, underscores, or hyphens." >&2
        exit 2
        ;;
esac
case "${bedrock_port}" in
    ''|*[!0-9]*) echo "SCENARIOCRAFT_BEDROCK_PORT must be an integer from 1 to 65535." >&2; exit 2 ;;
esac
if [ "${bedrock_port}" -lt 1 ] || [ "${bedrock_port}" -gt 65535 ]; then
    echo "SCENARIOCRAFT_BEDROCK_PORT must be an integer from 1 to 65535." >&2
    exit 2
fi
case "${timeout_seconds}" in
    ''|*[!0-9]*) echo "SCENARIOCRAFT_BEDROCK_SMOKE_TIMEOUT_SECONDS must be a positive integer." >&2; exit 2 ;;
esac
if [ "${timeout_seconds}" -lt 1 ]; then
    echo "SCENARIOCRAFT_BEDROCK_SMOKE_TIMEOUT_SECONDS must be a positive integer." >&2
    exit 2
fi

compose() {
    OPENAI_API_KEY=bedrock-runtime-smoke-only \
        docker compose -p "${project}" \
        -f docker-compose.yml -f docker-compose.bedrock.yml \
        -f docker-compose.smoke.yml "$@"
}
cleanup() {
    compose down --volumes --remove-orphans >/dev/null 2>&1 || true
}
trap cleanup EXIT INT TERM

compose up --build --detach paper

test "$(compose port paper 25565 --protocol tcp)" = "127.0.0.1:25565"
test "$(compose port paper "${container_bedrock_port}" --protocol udp)" = \
    "127.0.0.1:${bedrock_port}"

deadline=$(( $(date +%s) + timeout_seconds ))
while :; do
    paper_logs="$(compose logs --no-color paper 2>&1 || true)"
    if printf '%s\n' "${paper_logs}" | \
        grep -Fq "Started Geyser on UDP port ${container_bedrock_port}"; then
        break
    fi
    if compose ps --status exited --services | grep -Fqx paper; then
        printf '%s\n' "${paper_logs}" | tail -n 100 >&2
        echo "Paper exited before Geyser opened container UDP ${container_bedrock_port}." >&2
        exit 1
    fi
    if [ "$(date +%s)" -ge "${deadline}" ]; then
        printf '%s\n' "${paper_logs}" | tail -n 100 >&2
        echo "Timed out waiting for Geyser to open container UDP ${container_bedrock_port}." >&2
        exit 1
    fi
    sleep 5
done

compose ps --status running --services | grep -Fqx paper
compose exec -T paper sh -ceu \
    "grep -Eq '^[[:space:]]+auth-type: floodgate$' \
       plugins/Geyser-Spigot/config.yml; \
     test -f plugins/floodgate/key.pem"
./demo/check-bedrock.sh 127.0.0.1 "${bedrock_port}"
echo "SCENARIOCRAFT_BEDROCK_RUNTIME_SMOKE_OK"

#!/bin/sh
set -eu

if [ -z "${OPENAI_API_KEY:-}" ] && [ "${SCENARIOCRAFT_DEMO_DRY_RUN:-false}" != "true" ]; then
    echo "OPENAI_API_KEY is required. Export it before running make demo." >&2
    exit 2
fi

if docker compose version >/dev/null 2>&1; then
    compose='docker compose'
elif command -v docker-compose >/dev/null 2>&1; then
    compose='docker-compose'
else
    echo "Docker Compose is required (docker compose or docker-compose)." >&2
    exit 2
fi

if [ "${SCENARIOCRAFT_DEMO_DRY_RUN:-false}" = "true" ] && [ -z "${OPENAI_API_KEY:-}" ]; then
    OPENAI_API_KEY=dry-run-not-used
    export OPENAI_API_KEY
fi

project="${SCENARIOCRAFT_DEMO_PROJECT:-scenariocraft-headless}"
timeout_seconds="${SCENARIOCRAFT_DEMO_TIMEOUT_SECONDS:-600}"
case "${timeout_seconds}" in
    ''|*[!0-9]*) echo "SCENARIOCRAFT_DEMO_TIMEOUT_SECONDS must be a positive integer." >&2; exit 2 ;;
esac
if [ "${timeout_seconds}" -eq 0 ]; then
    echo "SCENARIOCRAFT_DEMO_TIMEOUT_SECONDS must be a positive integer." >&2
    exit 2
fi

cleanup() {
    if [ "${SCENARIOCRAFT_DEMO_KEEP:-false}" != "true" ]; then
        ${compose} --project-name "${project}" down --volumes --remove-orphans >/dev/null 2>&1 || true
    fi
}
trap cleanup EXIT INT TERM

started="$(date +%s)"
started_iso="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
deadline=$((started + timeout_seconds))
${compose} --project-name "${project}" up --build --detach &
compose_up_pid=$!
while kill -0 "${compose_up_pid}" 2>/dev/null; do
    if [ "$(date +%s)" -ge "${deadline}" ]; then
        echo "Timed out starting the demo services." >&2
        kill "${compose_up_pid}" 2>/dev/null || true
        wait "${compose_up_pid}" || true
        exit 1
    fi
    sleep 1
done
wait "${compose_up_pid}"

while ! ${compose} --project-name "${project}" logs --since "${started_iso}" paper 2>&1 \
        | grep -q 'SCENARIOCRAFT_DEMO_ARENA_READY'; do
    if [ "$(date +%s)" -ge "${deadline}" ]; then
        echo "Timed out waiting for the demo arena." >&2
        ${compose} --project-name "${project}" logs paper judge >&2
        exit 1
    fi
    sleep 2
done

${compose} --project-name "${project}" exec -T paper rcon-cli 'battle start'

while true; do
    logs="$(${compose} --project-name "${project}" logs --since "${started_iso}" paper judge 2>&1)"
    if printf '%s\n' "${logs}" | grep -q 'SCENARIOCRAFT_DEMO_JUDGED' \
            && printf '%s\n' "${logs}" | grep -q 'SCENARIOCRAFT_RESULTS_ANNOUNCED'; then
        break
    fi
    if printf '%s\n' "${logs}" | grep -q 'SCENARIOCRAFT_DEMO_JUDGE_FAILURE .*attempt=3/3'; then
        printf '%s\n' "${logs}" >&2
        exit 1
    fi
    if [ "$(date +%s)" -ge "${deadline}" ]; then
        echo "Timed out waiting for the judged result announcement." >&2
        printf '%s\n' "${logs}" >&2
        exit 1
    fi
    sleep 2
done

elapsed=$(($(date +%s) - started))
echo "SCENARIOCRAFT_DEMO_SUCCESS elapsed_seconds=${elapsed}"
${compose} --project-name "${project}" exec -T paper rcon-cli 'battle results'

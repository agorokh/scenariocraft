#!/bin/sh
set -eu

export SCENARIOCRAFT_CONFIG_FILE=./demo/plugin-config.yml

if [ -z "${OPENAI_API_KEY:-}" ]; then
    echo "OPENAI_API_KEY is required. Export it before running make proof-round." >&2
    exit 2
fi

if [ "${SCENARIOCRAFT_DEMO_DRY_RUN:-false}" != "false" ]; then
    echo "make proof-round requires the live judge; unset SCENARIOCRAFT_DEMO_DRY_RUN." >&2
    exit 2
fi
SCENARIOCRAFT_DEMO_DRY_RUN=false
export SCENARIOCRAFT_DEMO_DRY_RUN

timeout_seconds="${SCENARIOCRAFT_PROOF_TIMEOUT_SECONDS:-1200}"
case "${timeout_seconds}" in
    ''|*[!0-9]*) echo "SCENARIOCRAFT_PROOF_TIMEOUT_SECONDS must be a positive integer." >&2; exit 2 ;;
esac
if [ "${timeout_seconds}" -eq 0 ]; then
    echo "SCENARIOCRAFT_PROOF_TIMEOUT_SECONDS must be a positive integer." >&2
    exit 2
fi

started="$(date +%s)"
deadline=$((started + timeout_seconds))

run_with_deadline() {
    description="$1"
    shift
    if command -v setsid >/dev/null 2>&1; then
        setsid "$@" &
    elif command -v perl >/dev/null 2>&1; then
        perl -MPOSIX -e '
            POSIX::setsid() >= 0 or die "setsid failed\n";
            exec { $ARGV[0] } @ARGV or die "exec failed\n";
        ' -- "$@" &
    else
        echo "setsid or perl is required to enforce the proof-round deadline." >&2
        return 127
    fi
    command_pid=$!
    while kill -0 "${command_pid}" 2>/dev/null; do
        if [ "$(date +%s)" -ge "${deadline}" ]; then
            echo "Timed out ${description}." >&2
            # The command is a session/process-group leader, so a single group
            # signal also closes pipes inherited by grandchildren.
            kill -KILL "-${command_pid}" 2>/dev/null \
                || kill -KILL "${command_pid}" 2>/dev/null \
                || true
            wait "${command_pid}" 2>/dev/null || true
            return 124
        fi
        sleep 1
    done
    if wait "${command_pid}"; then
        return 0
    else
        command_status=$?
        return "${command_status}"
    fi
}

if run_with_deadline "detecting Docker Compose" docker compose version >/dev/null 2>&1; then
    compose_frontend=plugin
elif command -v docker-compose >/dev/null 2>&1 \
        && run_with_deadline "detecting Docker Compose" docker-compose version >/dev/null 2>&1; then
    compose_frontend=standalone
else
    echo "Docker Compose is required (docker compose or docker-compose)." >&2
    exit 2
fi

run_compose_with_deadline() {
    description="$1"
    shift
    if [ "${compose_frontend}" = plugin ]; then
        run_with_deadline "${description}" docker compose "$@"
    else
        run_with_deadline "${description}" docker-compose "$@"
    fi
}

# Native rootful Linux bind mounts preserve numeric ownership, so publish as the
# invoking user. Docker Desktop and rootless Docker present bind mounts as
# container-root-owned while mapping root's writes back to the invoking host user.
if docker_security_options="$(run_with_deadline "reading Docker security options" \
        docker info --format '{{json .SecurityOptions}}')"; then
    :
else
    docker_status=$?
    if [ "${docker_status}" -eq 124 ]; then
        exit 124
    fi
    docker_security_options=""
fi
if [ "$(uname -s)" = "Darwin" ] \
        || printf '%s' "${docker_security_options}" | grep -q 'name=rootless'; then
    SCENARIOCRAFT_PROOF_UID=0
    SCENARIOCRAFT_PROOF_GID=0
else
    SCENARIOCRAFT_PROOF_UID="$(id -u)"
    SCENARIOCRAFT_PROOF_GID="$(id -g)"
fi
export SCENARIOCRAFT_PROOF_UID SCENARIOCRAFT_PROOF_GID
mkdir -p build/proof-round

proof_temp="$(mktemp -d "${TMPDIR:-/tmp}/scenariocraft-proof.XXXXXX")"
cleanup() {
    rm -rf -- "${proof_temp}"
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

run_compose_with_deadline "starting the proof services" \
    --profile proof up --build --detach --force-recreate paper judge
run_compose_with_deadline "building the proof driver" \
    --profile proof build round-driver

while true; do
    run_compose_with_deadline "reading the Paper logs" logs paper \
        >"${proof_temp}/paper.log" 2>&1
    if grep -q 'SCENARIOCRAFT_DEMO_ARENA_READY' "${proof_temp}/paper.log"; then
        break
    fi
    if [ "$(date +%s)" -ge "${deadline}" ]; then
        echo "Timed out waiting for the proof-round arena." >&2
        cat "${proof_temp}/paper.log" >&2
        exit 1
    fi
    sleep 2
done

run_compose_with_deadline "driving the played round" --profile proof run --rm \
    -e "SCENARIOCRAFT_PROOF_TIMEOUT_SECONDS=${timeout_seconds}" \
    round-driver
run_compose_with_deadline "publishing the proof bundle" --profile proof run --rm --no-deps --entrypoint node round-driver \
    /opt/scenariocraft/e2e/assemble.mjs publish --proof /proof --site /site
run_compose_with_deadline "generating the proof page" --profile proof run --rm --no-deps --entrypoint node round-driver \
    /site/build-round-page.mjs --write /site
run_compose_with_deadline "validating the proof bundle" --profile proof run --rm --no-deps --entrypoint node round-driver \
    /opt/scenariocraft/e2e/assemble.mjs check --site /site
run_compose_with_deadline "validating the proof page" --profile proof run --rm --no-deps --entrypoint node round-driver \
    /site/build-round-page.mjs --check /site

elapsed=$(($(date +%s) - started))
echo "SCENARIOCRAFT_PROOF_ROUND_SUCCESS elapsed_seconds=${elapsed}"

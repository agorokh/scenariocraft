#!/bin/sh
set -eu

root=$(cd "$(dirname "$0")/.." && pwd)
test_root=$(mktemp -d)
cleanup() {
    rm -rf "$test_root"
}
trap cleanup EXIT INT TERM

mkdir -p "$test_root/repo/demo" "$test_root/bin" "$test_root/home"
cp "$root/demo/family-server.sh" "$test_root/repo/demo/family-server.sh"
touch "$test_root/repo/docker-compose.yml" "$test_root/repo/docker-compose.bedrock.yml"

cat >"$test_root/bin/uname" <<'SH'
#!/bin/sh
echo "${FAKE_UNAME:-Linux}"
SH

cat >"$test_root/bin/launchctl" <<'SH'
#!/bin/sh
set -eu
case "${1:-}" in
    print)
        if [ -f "${FAKE_LAUNCH_STATE_FILE:?}" ]; then
            printf '{\n\tpid = 4242\n}\n'
            exit 0
        fi
        exit 1
        ;;
    bootout)
        rm -f "${FAKE_LAUNCH_STATE_FILE:?}"
        ;;
    bootstrap)
        touch "${FAKE_LAUNCH_STATE_FILE:?}"
        ;;
esac
SH

cat >"$test_root/bin/lsof" <<'SH'
#!/bin/sh
if [ "${FAKE_LSOF_BUSY:-false}" = true ]; then
    exit 0
fi
case " $* " in
    *" -a -p 4242 -iUDP:19132 "*)
        test -f "${FAKE_LAUNCH_STATE_FILE:?}"
        exit $?
        ;;
esac
exit 1
SH

cat >"$test_root/bin/java" <<'SH'
#!/bin/sh
if [ "${1:-}" = -version ]; then
    echo 'openjdk version "21.0.11"' >&2
fi
exit 0
SH

cat >"$test_root/bin/curl" <<'SH'
#!/bin/sh
set -eu
while [ "$#" -gt 0 ]; do
    if [ "$1" = -o ]; then
        shift
        printf 'mock geyser jar\n' >"$1"
        exit 0
    fi
    shift
done
exit 1
SH

cat >"$test_root/bin/shasum" <<'SH'
#!/bin/sh
echo '036475e5a1dfea07bd0d2974d117e67fb477df1c47db7c95b90de0638c019d22  mock'
SH

cat >"$test_root/bin/plutil" <<'SH'
#!/bin/sh
if [ "${1:-}" = -create ]; then
    : >"${3:?}"
fi
exit 0
SH

cat >"$test_root/bin/sleep" <<'SH'
#!/bin/sh
exit 0
SH

cat >"$test_root/bin/docker" <<'SH'
#!/bin/sh
set -eu
printf '%s|%s|%s\n' \
    "${SCENARIOCRAFT_CONFIG_FILE:-}" \
    "${SCENARIOCRAFT_BEDROCK_PORT:-}" \
    "$*" >>"${FAKE_DOCKER_LOG}"
if [ "$*" = "compose version" ]; then
    exit 0
fi
if [ "${FAKE_DOCKER_UP_FAIL:-false}" = true ]; then
    case " $* " in
        *" up -d --build "*) exit 1 ;;
    esac
fi
case " $* " in
    *" cp paper-container:/data/plugins/floodgate/key.pem "*)
        for argument in "$@"; do
            destination=$argument
        done
        printf 'mock floodgate key\n' >"$destination"
        ;;
    *" exec paper-container test -s /data/plugins/floodgate/key.pem "*)
        ;;
    *" exec paper-container sha256sum /data/plugins/floodgate/key.pem "*)
        echo '036475e5a1dfea07bd0d2974d117e67fb477df1c47db7c95b90de0638c019d22  key.pem'
        ;;
    *" inspect --format "*)
        echo healthy
        ;;
    *" ps -q paper "*)
        echo paper-container
        ;;
esac
SH

cat >"$test_root/repo/demo/check-bedrock.sh" <<'SH'
#!/bin/sh
if [ "${FAKE_PROBE_FAIL:-false}" = true ]; then
    exit 1
fi
if [ "${FAKE_PROBE_FAILURES:-0}" -gt 0 ]; then
    attempts_file=${FAKE_PROBE_ATTEMPTS_FILE:?}
    attempts=0
    if [ -f "$attempts_file" ]; then
        attempts=$(cat "$attempts_file")
    fi
    attempts=$((attempts + 1))
    printf '%s\n' "$attempts" >"$attempts_file"
    if [ "$attempts" -le "$FAKE_PROBE_FAILURES" ]; then
        exit 1
    fi
fi
echo "SCENARIOCRAFT_BEDROCK_OK host=$1 port=$2 motd=Speed Build"
SH

chmod +x "$test_root/bin/curl" "$test_root/bin/docker" "$test_root/bin/java" \
    "$test_root/bin/launchctl" "$test_root/bin/lsof" "$test_root/bin/plutil" \
    "$test_root/bin/shasum" "$test_root/bin/sleep" "$test_root/bin/uname" \
    "$test_root/repo/demo/check-bedrock.sh" "$test_root/repo/demo/family-server.sh"

export PATH="$test_root/bin:$PATH"
export HOME="$test_root/home"
export FAKE_DOCKER_LOG="$test_root/docker.log"
export FAKE_LAUNCH_STATE_FILE="$test_root/launch-state"
export FAKE_PROBE_ATTEMPTS_FILE="$test_root/probe-attempts"
export SCENARIOCRAFT_CONFIG_FILE=./demo/plugin-config.yml
export SCENARIOCRAFT_BEDROCK_PORT=19133

"$test_root/repo/demo/family-server.sh" up >"$test_root/up.out"
grep -Fq 'ScenarioCraft is ready: Java TCP 25565; Bedrock UDP 19132.' "$test_root/up.out"
grep -Eq '^./demo/family-config.yml\|19132\|compose .* up -d --build$' "$FAKE_DOCKER_LOG"

rm -f "$FAKE_PROBE_ATTEMPTS_FILE"
FAKE_PROBE_FAILURES=2 "$test_root/repo/demo/family-server.sh" up >"$test_root/retried-up.out"
test "$(cat "$FAKE_PROBE_ATTEMPTS_FILE")" -eq 3
grep -Fq 'ScenarioCraft is ready: Java TCP 25565; Bedrock UDP 19132.' "$test_root/retried-up.out"

"$test_root/repo/demo/family-server.sh" status >"$test_root/status.out"
grep -Fq 'Bedrock UDP 19132 answered a RakNet discovery probe.' "$test_root/status.out"

if FAKE_PROBE_FAIL=true "$test_root/repo/demo/family-server.sh" status >/dev/null 2>&1; then
    echo "family status succeeded despite a failed RakNet probe" >&2
    exit 1
fi

"$test_root/repo/demo/family-server.sh" down
grep -Eq '^./demo/family-config.yml\|19132\|compose .* down$' "$FAKE_DOCKER_LOG"

FAKE_UNAME=Darwin "$test_root/repo/demo/family-server.sh" up >"$test_root/mac-up.out"
grep -Fq 'ScenarioCraft is ready: Java TCP 25565; Bedrock UDP 19132.' "$test_root/mac-up.out"
test -f "$FAKE_LAUNCH_STATE_FILE"
test -s "$test_root/repo/.local/geyser/key.pem"
grep -Fq 'auth-type: floodgate' "$test_root/repo/.local/geyser/config.yml"
FAKE_UNAME=Darwin "$test_root/repo/demo/family-server.sh" status >"$test_root/mac-status.out"
grep -Fq 'Bedrock UDP 19132 answered a RakNet discovery probe.' "$test_root/mac-status.out"
FAKE_UNAME=Darwin "$test_root/repo/demo/family-server.sh" down
test ! -f "$FAKE_LAUNCH_STATE_FILE"

before_mac_down=$(grep -Ec '\|compose .* down$' "$FAKE_DOCKER_LOG")
if FAKE_UNAME=Darwin FAKE_LSOF_BUSY=true \
        "$test_root/repo/demo/family-server.sh" down >/dev/null 2>&1; then
    echo "macOS family down ignored an occupied Bedrock port" >&2
    exit 1
fi
after_mac_down=$(grep -Ec '\|compose .* down$' "$FAKE_DOCKER_LOG")
test "$after_mac_down" -eq $((before_mac_down + 1))

before_failed_mac_up=$(grep -Ec '\|compose .* down$' "$FAKE_DOCKER_LOG")
if FAKE_UNAME=Darwin FAKE_DOCKER_UP_FAIL=true \
        "$test_root/repo/demo/family-server.sh" up >/dev/null 2>&1; then
    echo "failed macOS family up unexpectedly succeeded" >&2
    exit 1
fi
after_failed_mac_up=$(grep -Ec '\|compose .* down$' "$FAKE_DOCKER_LOG")
test "$after_failed_mac_up" -eq $((before_failed_mac_up + 1))

echo SCENARIOCRAFT_FAMILY_SERVER_LIFECYCLE_OK

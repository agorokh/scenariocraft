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
exit 1
SH

cat >"$test_root/bin/lsof" <<'SH'
#!/bin/sh
if [ "${FAKE_LSOF_BUSY:-false}" = true ]; then
    exit 0
fi
exit 1
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
echo "SCENARIOCRAFT_BEDROCK_OK host=$1 port=$2 motd=Speed Build"
SH

chmod +x "$test_root/bin/docker" "$test_root/bin/launchctl" \
    "$test_root/bin/lsof" "$test_root/bin/sleep" "$test_root/bin/uname" \
    "$test_root/repo/demo/check-bedrock.sh" "$test_root/repo/demo/family-server.sh"

export PATH="$test_root/bin:$PATH"
export HOME="$test_root/home"
export FAKE_DOCKER_LOG="$test_root/docker.log"
export SCENARIOCRAFT_CONFIG_FILE=./demo/plugin-config.yml
export SCENARIOCRAFT_BEDROCK_PORT=19133

"$test_root/repo/demo/family-server.sh" up >"$test_root/up.out"
grep -Fq 'ScenarioCraft is ready: Java TCP 25565; Bedrock UDP 19132.' "$test_root/up.out"
grep -Eq '^./demo/family-config.yml\|19132\|compose .* up -d --build$' "$FAKE_DOCKER_LOG"

"$test_root/repo/demo/family-server.sh" status >"$test_root/status.out"
grep -Fq 'Bedrock UDP 19132 answered a RakNet discovery probe.' "$test_root/status.out"

if FAKE_PROBE_FAIL=true "$test_root/repo/demo/family-server.sh" status >/dev/null 2>&1; then
    echo "family status succeeded despite a failed RakNet probe" >&2
    exit 1
fi

"$test_root/repo/demo/family-server.sh" down
grep -Eq '^./demo/family-config.yml\|19132\|compose .* down$' "$FAKE_DOCKER_LOG"

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

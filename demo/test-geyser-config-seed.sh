#!/bin/sh
set -eu

fixture_dir="$(mktemp -d)"
cleanup() {
    rm -rf "${fixture_dir}"
}
trap cleanup EXIT INT TERM

config="${fixture_dir}/config.yml"
printf '%s\n' \
    'bedrock:' \
    '  port: 19132' \
    'java:' \
    '  address: auto' \
    '  # auth-type: online' \
    '  auth-type : online' \
    '  port: 25565' \
    'remote:' \
    '  use-proxy-protocol: false' \
    'java:' \
    '  auth-type: online' >"${config}"

./demo/seed-geyser-config.sh "${config}"

test "$(grep -Ec '^java[[:space:]]*:' "${config}")" -eq 1
test "$(grep -Ec '^[[:space:]]+auth-type[[:space:]]*:[[:space:]]*floodgate$' "${config}")" -eq 1
grep -Fq '  address: auto' "${config}"
grep -Fq '  port: 25565' "${config}"
grep -Fq 'remote:' "${config}"

printf '%s\n' 'bedrock:' '  port: 19132' >"${config}"
./demo/seed-geyser-config.sh "${config}"
test "$(grep -Ec '^java[[:space:]]*:' "${config}")" -eq 1
grep -Eq '^[[:space:]]+auth-type:[[:space:]]*floodgate$' "${config}"

rm -f "${config}"
./demo/seed-geyser-config.sh "${config}"
grep -Fxq 'java:' "${config}"
grep -Fxq '  auth-type: floodgate' "${config}"

echo "SCENARIOCRAFT_GEYSER_CONFIG_SEED_OK"

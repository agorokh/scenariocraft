#!/bin/sh
set -eu

if ! docker compose version >/dev/null 2>&1; then
    echo "Docker Compose v2 is required to verify the Bedrock overlay." >&2
    exit 2
fi

command -v jq >/dev/null 2>&1 || {
    echo "jq is required to verify the Bedrock overlay." >&2
    exit 2
}

contract_dir="$(mktemp -d)"
cleanup() {
    rm -rf "${contract_dir}"
}
trap cleanup EXIT INT TERM

env -u SCENARIOCRAFT_BEDROCK_PORT \
    OPENAI_API_KEY=bedrock-compose-contract-only \
    docker compose -f docker-compose.yml -f docker-compose.bedrock.yml \
    config --format json >"${contract_dir}/compose.json"

SCENARIOCRAFT_BEDROCK_PORT=19133 \
OPENAI_API_KEY=bedrock-compose-contract-only \
    docker compose -f docker-compose.yml -f docker-compose.bedrock.yml \
    -f docker-compose.smoke.yml \
    config --format json >"${contract_dir}/smoke-compose.json"

jq -e '
    def floodgate_url:
      "https://download.geysermc.org/v2/projects/floodgate/versions/2.2.5/builds/138/downloads/spigot";
    def floodgate_sha256:
      "44bdb908e2fb4ff1b974d5313d048a625a21555a9844cfb86256a98e8e1c6bd1";
    .services.paper.environment.MODRINTH_PROJECTS ==
      "geyser:U1DOZeks,viaversion:CjleI5xo" and
    .services.paper.environment.MODRINTH_ALLOWED_VERSION_TYPE == "beta" and
    .services.paper.environment.MODRINTH_DEFAULT_VERSION_TYPE == "beta" and
    .services.paper.environment.MOTD == "ScenarioCraft Speed Build demo" and
    (.services.paper.environment.PLUGINS == null) and
    .services["bedrock-plugins"].image == "alpine:3.23" and
    (.services["bedrock-plugins"].command | tostring | contains(floodgate_url)) and
    (.services["bedrock-plugins"].command | tostring | contains(floodgate_sha256)) and
    (.services["bedrock-plugins"].command | tostring | contains("wget -q -T 30 -t 3")) and
    (.services["bedrock-plugins"].command | tostring |
      contains("/demo/seed-geyser-config.sh")) and
    (.services["bedrock-plugins"].command | tostring |
      contains("/plugins/Geyser-Spigot")) and
    any(
      .services["bedrock-plugins"].volumes[];
      .target == "/demo/seed-geyser-config.sh" and .read_only == true
    ) and
    .services.paper.depends_on["bedrock-plugins"].condition ==
      "service_completed_successfully" and
    any(
      .services.paper.ports[];
      .target == 19132 and .published == "19132" and .protocol == "udp"
    )
' "${contract_dir}/compose.json" >/dev/null

jq -e '
    (.services.paper.ports | length) == 2 and
    any(
      .services.paper.ports[];
      .target == 25565 and .published == "25565" and .protocol == "tcp" and
      .host_ip == "127.0.0.1"
    ) and
    any(
      .services.paper.ports[];
      .target == 19132 and .published == "19133" and .protocol == "udp" and
      .host_ip == "127.0.0.1"
    )
' "${contract_dir}/smoke-compose.json" >/dev/null

echo "SCENARIOCRAFT_BEDROCK_COMPOSE_OK"

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

OPENAI_API_KEY=bedrock-compose-contract-only \
SCENARIOCRAFT_BEDROCK_PORT=19132 \
    docker compose -f docker-compose.yml -f docker-compose.bedrock.yml \
    config --format json >"${contract_dir}/compose.json"

jq -e '
    def floodgate_url:
      "https://download.geysermc.org/v2/projects/floodgate/versions/2.2.5/builds/138/downloads/spigot";
    def floodgate_sha256:
      "44bdb908e2fb4ff1b974d5313d048a625a21555a9844cfb86256a98e8e1c6bd1";
    .services.paper.environment.MODRINTH_PROJECTS == "geyser,viaversion" and
    .services.paper.environment.MODRINTH_ALLOWED_VERSION_TYPE == "beta" and
    .services.paper.environment.MODRINTH_DEFAULT_VERSION_TYPE == "beta" and
    .services.paper.environment.MOTD == "ScenarioCraft Speed Build demo" and
    (.services.paper.environment.PLUGINS == null) and
    .services["bedrock-plugins"].image == "alpine:3.23" and
    (.services["bedrock-plugins"].command | tostring | contains(floodgate_url)) and
    (.services["bedrock-plugins"].command | tostring | contains(floodgate_sha256)) and
    .services.paper.depends_on["bedrock-plugins"].condition ==
      "service_completed_successfully" and
    any(
      .services.paper.ports[];
      .target == 19132 and .published == "19132" and .protocol == "udp"
    )
' "${contract_dir}/compose.json" >/dev/null

echo "SCENARIOCRAFT_BEDROCK_COMPOSE_OK"

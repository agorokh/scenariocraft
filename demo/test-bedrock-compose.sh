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
    docker compose -f docker-compose.yml -f docker-compose.bedrock.yml \
    config --format json >"${contract_dir}/compose.json"

jq -e '
    .services.paper.environment.MODRINTH_PROJECTS == "geyser,viaversion" and
    .services.paper.environment.MODRINTH_ALLOWED_VERSION_TYPE == "beta" and
    .services.paper.environment.MODRINTH_DEFAULT_VERSION_TYPE == "beta" and
    .services.paper.environment.MOTD == "ScenarioCraft Speed Build demo" and
    .services.paper.environment.PLUGINS ==
      "https://download.geysermc.org/v2/projects/floodgate/versions/latest/builds/latest/downloads/spigot" and
    any(
      .services.paper.ports[];
      .target == 19132 and .published == "19132" and .protocol == "udp"
    )
' "${contract_dir}/compose.json" >/dev/null

echo "SCENARIOCRAFT_BEDROCK_COMPOSE_OK"

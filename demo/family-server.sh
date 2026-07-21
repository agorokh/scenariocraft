#!/usr/bin/env bash
set -euo pipefail

repo_dir=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
project_name=scenariocraft
geyser_label=com.scenariocraft.geyser
runtime_dir="$repo_dir/.local/geyser"
geyser_jar="$runtime_dir/Geyser-Standalone.jar"
geyser_log="$runtime_dir/geyser.log"

for dependency in curl make python3; do
    if ! command -v "$dependency" >/dev/null 2>&1; then
        echo "$dependency is required to run the ScenarioCraft family server." >&2
        exit 1
    fi
done

if docker compose version >/dev/null 2>&1; then
    compose=(docker compose --project-name "$project_name" -f "$repo_dir/docker-compose.yml" -f "$repo_dir/docker-compose.bedrock.yml")
elif command -v docker-compose >/dev/null 2>&1; then
    compose=(docker-compose --project-name "$project_name" -f "$repo_dir/docker-compose.yml" -f "$repo_dir/docker-compose.bedrock.yml")
else
    echo "Docker Compose is required (docker compose or docker-compose)." >&2
    exit 1
fi

if [[ -n "${SCENARIOCRAFT_ENV_FILE:-}" ]]; then
    compose+=(--env-file "$SCENARIOCRAFT_ENV_FILE")
fi

compose_cmd() {
    (cd "$repo_dir" && "${compose[@]}" "$@")
}

raknet_probe() {
    SCENARIOCRAFT_BEDROCK_TIMEOUT_SECONDS=2 \
        "$repo_dir/demo/check-bedrock.sh" "$1" 19132
}

wait_for_paper() {
    local container_id status
    for _ in $(seq 1 90); do
        container_id=$(compose_cmd ps -q paper)
        if [[ -n "$container_id" ]]; then
            status=$(docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' "$container_id")
            if [[ "$status" == healthy ]]; then
                return
            fi
        fi
        sleep 2
    done
    echo "Paper did not become healthy within 3 minutes." >&2
    compose_cmd logs --tail=100 paper >&2
    exit 1
}

wait_for_floodgate_key() {
    local container_id
    container_id=$(compose_cmd ps -q paper)
    for _ in $(seq 1 30); do
        if docker exec "$container_id" test -f /data/plugins/floodgate/key.pem 2>/dev/null; then
            return
        fi
        sleep 2
    done
    echo "Floodgate did not generate its key within 60 seconds." >&2
    compose_cmd logs --tail=100 paper >&2
    exit 1
}

find_java_21() {
    local java_home
    if java_home=$(/usr/libexec/java_home -v 21 2>/dev/null); then
        printf '%s/bin/java\n' "$java_home"
    elif [[ -x /opt/homebrew/opt/openjdk@21/bin/java ]]; then
        printf '%s\n' /opt/homebrew/opt/openjdk@21/bin/java
    elif command -v java >/dev/null 2>&1 && java -version 2>&1 | head -n 1 | grep -Eq 'version "21([.]|\")'; then
        command -v java
    else
        echo "Java 21 is required for host-native Geyser (for example: brew install openjdk@21)." >&2
        exit 1
    fi
}

install_geyser() {
    local metadata expected actual
    mkdir -p "$runtime_dir"
    metadata=$(curl -fsSL https://download.geysermc.org/v2/projects/geyser/versions/latest/builds/latest)
    expected=$(python3 -c 'import json,sys; print(json.load(sys.stdin)["downloads"]["standalone"]["sha256"])' <<<"$metadata")
    if [[ -f "$geyser_jar" ]]; then
        actual=$(shasum -a 256 "$geyser_jar" | awk '{print $1}')
    else
        actual=
    fi
    if [[ "$actual" != "$expected" ]]; then
        curl -fsSL -o "$geyser_jar.tmp" https://download.geysermc.org/v2/projects/geyser/versions/latest/builds/latest/downloads/standalone
        actual=$(shasum -a 256 "$geyser_jar.tmp" | awk '{print $1}')
        if [[ "$actual" != "$expected" ]]; then
            rm -f "$geyser_jar.tmp"
            echo "Downloaded Geyser checksum did not match its release metadata." >&2
            exit 1
        fi
        mv "$geyser_jar.tmp" "$geyser_jar"
    fi
}

write_geyser_config() {
    cat >"$runtime_dir/config.yml" <<'YAML'
bedrock:
  address: 0.0.0.0
  port: 19132
java:
  address: 127.0.0.1
  port: 25565
  auth-type: floodgate
motd:
  primary-motd: Speed Build
  secondary-motd: ScenarioCraft family demo
  passthrough-motd: true
  passthrough-player-counts: true
  ping-passthrough-interval: 3
gameplay:
  server-name: ScenarioCraft family demo
  command-suggestions: true
  show-coordinates: true
  force-resource-packs: true
  enable-integrated-pack: true
advanced:
  floodgate-key-file: key.pem
  bedrock:
    broadcast-port: 0
    compression-level: 6
    mtu: 1400
    validate-bedrock-login: true
debug-mode: false
config-version: 7
YAML
}

start_macos_geyser() {
    local container_id java_bin plist service_target
    java_bin=$(find_java_21)
    install_geyser
    write_geyser_config
    wait_for_floodgate_key
    container_id=$(compose_cmd ps -q paper)
    docker cp "$container_id:/data/plugins/floodgate/key.pem" "$runtime_dir/key.pem"
    chmod 600 "$runtime_dir/key.pem"

    mkdir -p "$HOME/Library/LaunchAgents"
    plist="$HOME/Library/LaunchAgents/$geyser_label.plist"
    plutil -create xml1 "$plist"
    plutil -insert Label -string "$geyser_label" "$plist"
    plutil -insert ProgramArguments -json "[\"$java_bin\",\"-jar\",\"$geyser_jar\",\"--nogui\"]" "$plist"
    plutil -insert WorkingDirectory -string "$runtime_dir" "$plist"
    plutil -insert RunAtLoad -bool true "$plist"
    plutil -insert KeepAlive -bool true "$plist"
    plutil -insert StandardOutPath -string "$geyser_log" "$plist"
    plutil -insert StandardErrorPath -string "$geyser_log" "$plist"

    service_target="gui/$(id -u)/$geyser_label"
    launchctl bootout "$service_target" 2>/dev/null || true
    for _ in $(seq 1 20); do
        if launchctl bootstrap "gui/$(id -u)" "$plist" 2>/dev/null; then
            break
        fi
        sleep 0.25
    done
    if ! launchctl print "$service_target" >/dev/null 2>&1; then
        echo "Could not register the macOS Geyser LaunchAgent at $plist." >&2
        exit 1
    fi

    for _ in $(seq 1 30); do
        if raknet_probe 127.0.0.1 2>/dev/null; then
            return
        fi
        sleep 2
    done
    echo "Host-native Geyser did not answer UDP 19132." >&2
    tail -n 100 "$geyser_log" >&2 || true
    exit 1
}

case "${1:-up}" in
    up)
        if [[ $(uname -s) == Darwin ]]; then
            # Colima and some Docker Desktop configurations do not expose UDP
            # to LAN discovery. Keep container Geyser on a non-public fallback
            # port and let the host LaunchAgent own the canonical Bedrock port.
            SCENARIOCRAFT_BEDROCK_PORT=19133 compose_cmd up -d --build
            wait_for_paper
            start_macos_geyser
        else
            compose_cmd up -d --build
            wait_for_paper
            raknet_probe 127.0.0.1
        fi
        echo "ScenarioCraft is ready: Java TCP 25565; Bedrock UDP 19132."
        ;;
    status)
        compose_cmd ps
        if raknet_probe 127.0.0.1; then
            echo "Bedrock UDP 19132 answered a RakNet discovery probe."
        fi
        ;;
    down)
        if [[ $(uname -s) == Darwin ]]; then
            launchctl bootout "gui/$(id -u)/$geyser_label" 2>/dev/null || true
        fi
        compose_cmd down
        ;;
    *)
        echo "Usage: $0 {up|status|down}" >&2
        exit 2
        ;;
esac

#!/usr/bin/env bash
set -euo pipefail

repo_dir=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
project_name=scenariocraft
geyser_label=com.scenariocraft.geyser
runtime_dir="$repo_dir/.local/geyser"
geyser_jar="$runtime_dir/Geyser-Standalone.jar"
geyser_log="$runtime_dir/geyser.log"
# Bump the version, build, URL, and independently recorded digest together.
geyser_version=2.11.0
geyser_build=1201
geyser_sha256=036475e5a1dfea07bd0d2974d117e67fb477df1c47db7c95b90de0638c019d22
geyser_url="https://download.geysermc.org/v2/projects/geyser/versions/$geyser_version/builds/$geyser_build/downloads/standalone"

# A family launch is intentionally immune to timing/port overrides left by
# proof and smoke commands in the parent shell.
export SCENARIOCRAFT_CONFIG_FILE=./demo/family-config.yml
export SCENARIOCRAFT_BEDROCK_PORT=19132

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

wait_for_bedrock() {
    for _ in $(seq 1 30); do
        if raknet_probe 127.0.0.1; then
            return
        fi
        sleep 2
    done
    echo "Geyser did not answer UDP 19132 within 60 seconds." >&2
    compose_cmd logs --tail=100 paper >&2 || true
    return 1
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
        if docker exec "$container_id" test -s /data/plugins/floodgate/key.pem 2>/dev/null; then
            return
        fi
        sleep 2
    done
    echo "Floodgate did not generate its key within 60 seconds." >&2
    compose_cmd logs --tail=100 paper >&2
    exit 1
}

verify_floodgate_health() {
    local container_digest container_id local_digest
    container_id=$(compose_cmd ps -q paper)
    compose_cmd exec -T paper sh -ceu \
        "test -s /data/plugins/floodgate/key.pem; \
         grep -Eq '^[[:space:]]+auth-type: floodgate$' \
           /data/plugins/Geyser-Spigot/config.yml"
    if [[ $(uname -s) == Darwin ]]; then
        if [[ ! -s "$runtime_dir/key.pem" ]]; then
            echo "Host Geyser has no Floodgate key." >&2
            return 1
        fi
        container_digest=$(docker exec "$container_id" sha256sum /data/plugins/floodgate/key.pem | awk '{print $1}')
        local_digest=$(shasum -a 256 "$runtime_dir/key.pem" | awk '{print $1}')
        if [[ "$container_digest" != "$local_digest" ]]; then
            echo "Host Geyser and Paper have different Floodgate keys; rerun make family-up." >&2
            return 1
        fi
    fi
}

find_java_21() {
    local candidate java_home
    if java_home=$(/usr/libexec/java_home -v 21 2>/dev/null); then
        candidate="$java_home/bin/java"
        if [[ -x "$candidate" ]] && "$candidate" -version 2>&1 | head -n 1 | grep -Eq 'version "21([.]|\")'; then
            printf '%s\n' "$candidate"
            return
        fi
    fi
    for candidate in /opt/homebrew/opt/openjdk@21/bin/java "$(command -v java 2>/dev/null || true)"; do
        if [[ -n "$candidate" && -x "$candidate" ]] \
                && "$candidate" -version 2>&1 | head -n 1 | grep -Eq 'version "21([.]|\")'; then
            printf '%s\n' "$candidate"
            return
        fi
    done
    echo "Java 21 is required for host-native Geyser (for example: brew install openjdk@21)." >&2
    exit 1
}

unload_macos_geyser() {
    local remove_plist="$1" plist service_target
    plist="$HOME/Library/LaunchAgents/$geyser_label.plist"
    service_target="gui/$(id -u)/$geyser_label"
    for _ in $(seq 1 20); do
        if ! launchctl print "$service_target" >/dev/null 2>&1; then
            break
        fi
        launchctl bootout "$service_target" 2>/dev/null || true
        sleep 0.25
    done
    if launchctl print "$service_target" >/dev/null 2>&1; then
        echo "Could not stop the managed macOS Geyser LaunchAgent." >&2
        return 1
    fi
    if [[ "$remove_plist" == true ]]; then
        rm -f "$plist"
    fi
    for _ in $(seq 1 20); do
        if ! lsof -nP -iUDP:19132 >/dev/null 2>&1; then
            return 0
        fi
        sleep 0.25
    done
    if lsof -nP -iUDP:19132 >/dev/null 2>&1; then
        echo "UDP 19132 did not become available after stopping the managed macOS Geyser service." >&2
        return 1
    fi
}

cleanup_failed_macos_up() {
    local failure_status=$?
    trap - EXIT
    if [[ "$failure_status" -ne 0 ]]; then
        echo "Family startup failed; removing partial macOS Geyser and Compose services." >&2
        unload_macos_geyser true || true
        compose_cmd down || true
    fi
    exit "$failure_status"
}

install_geyser() {
    local actual
    mkdir -p "$runtime_dir"
    if [[ -f "$geyser_jar" ]]; then
        actual=$(shasum -a 256 "$geyser_jar" | awk '{print $1}')
    else
        actual=
    fi
    if [[ "$actual" != "$geyser_sha256" ]]; then
        curl -fsSL -o "$geyser_jar.tmp" "$geyser_url"
        actual=$(shasum -a 256 "$geyser_jar.tmp" | awk '{print $1}')
        if [[ "$actual" != "$geyser_sha256" ]]; then
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
    local container_id java_bin listener_pid plist probe_output service_target service_state
    command -v lsof >/dev/null 2>&1 || {
        echo "lsof is required to verify the macOS Geyser listener." >&2
        exit 1
    }
    java_bin=$(find_java_21)
    install_geyser
    write_geyser_config
    wait_for_floodgate_key
    container_id=$(compose_cmd ps -q paper)
    unload_macos_geyser false
    docker cp "$container_id:/data/plugins/floodgate/key.pem" "$runtime_dir/key.pem"
    chmod 600 "$runtime_dir/key.pem"
    if [[ ! -s "$runtime_dir/key.pem" ]]; then
        echo "Floodgate key copy was empty; refusing to start Geyser." >&2
        exit 1
    fi

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
        service_state=$(launchctl print "$service_target" 2>/dev/null || true)
        listener_pid=$(printf '%s\n' "$service_state" | awk '/^[[:space:]]*pid = [0-9]+$/ {print $3; exit}')
        if [[ -n "$listener_pid" ]] \
                && lsof -nP -a -p "$listener_pid" -iUDP:19132 >/dev/null 2>&1 \
                && probe_output=$(raknet_probe 127.0.0.1 2>/dev/null); then
            printf '%s\n' "$probe_output"
            return 0
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
            trap cleanup_failed_macos_up EXIT
            SCENARIOCRAFT_BEDROCK_PORT=19133 compose_cmd up -d --build
            wait_for_paper
            start_macos_geyser
            verify_floodgate_health
            trap - EXIT
        else
            SCENARIOCRAFT_BEDROCK_PORT=19132 compose_cmd up -d --build
            wait_for_paper
            wait_for_bedrock
            verify_floodgate_health
        fi
        echo "ScenarioCraft is ready: Java TCP 25565; Bedrock UDP 19132."
        ;;
    status)
        compose_cmd ps
        verify_floodgate_health
        if raknet_probe 127.0.0.1; then
            echo "Bedrock UDP 19132 answered a RakNet discovery probe."
        else
            echo "Bedrock UDP 19132 did not answer a valid RakNet discovery probe." >&2
            exit 1
        fi
        ;;
    down)
        down_status=0
        if [[ $(uname -s) == Darwin ]]; then
            unload_macos_geyser true || down_status=$?
        fi
        compose_cmd down || down_status=$?
        exit "$down_status"
        ;;
    *)
        echo "Usage: $0 {up|status|down}" >&2
        exit 2
        ;;
esac

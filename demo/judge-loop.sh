#!/bin/sh
set -eu

rounds=/plugins/ScenarioCraft/rounds
secret=/run/scenariocraft/rcon-password

while [ ! -s "${secret}" ]; do
    sleep 1
done
SCENARIOCRAFT_RCON_PASSWORD="$(tr -d '\r\n' < "${secret}")"
export SCENARIOCRAFT_RCON_PASSWORD

echo "ScenarioCraft judge is watching ${rounds} for completed rounds."
while true; do
    if [ -d "${rounds}" ]; then
        for manifest in "${rounds}"/round-*/manifest.json; do
            [ -f "${manifest}" ] || continue
            round="${manifest%/manifest.json}"
            [ ! -s "${round}/results.txt" ] || continue
            attempts_file="${round}/.judge-attempts"
            attempts=0
            if [ -s "${attempts_file}" ]; then
                attempts="$(tr -d '\r\n' < "${attempts_file}")"
            fi
            case "${attempts}" in
                0|1|2) ;;
                *) continue ;;
            esac
            attempts=$((attempts + 1))
            printf '%s\n' "${attempts}" > "${attempts_file}"
            if [ "${SCENARIOCRAFT_DEMO_DRY_RUN:-false}" = "true" ]; then
                /opt/scenariocraft/judge/bin/judge --round "${round}" --dry-run || true
            else
                /opt/scenariocraft/judge/bin/judge --round "${round}" || true
            fi
            if [ -s "${round}/results.txt" ]; then
                echo "SCENARIOCRAFT_DEMO_JUDGED $(basename "${round}")"
            else
                echo "SCENARIOCRAFT_DEMO_JUDGE_FAILURE $(basename "${round}") attempt=${attempts}/3" >&2
                sleep 10
            fi
        done
    fi
    sleep 2
done

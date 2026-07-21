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
            [ ! -e "${round}/.judge-attempted" ] || continue
            : > "${round}/.judge-attempted"
            if [ "${SCENARIOCRAFT_DEMO_DRY_RUN:-false}" = "true" ]; then
                /opt/scenariocraft/judge/bin/judge --round "${round}" --dry-run || true
            else
                /opt/scenariocraft/judge/bin/judge --round "${round}" || true
            fi
            if [ -s "${round}/results.txt" ]; then
                echo "SCENARIOCRAFT_DEMO_JUDGED $(basename "${round}")"
            else
                echo "SCENARIOCRAFT_DEMO_JUDGE_FAILURE $(basename "${round}")" >&2
            fi
        done
    fi
    sleep 2
done

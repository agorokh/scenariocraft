#!/bin/sh
set -eu

install -d -o 1000 -g 1000 -m 0755 /plugins /plugins/ScenarioCraft
install -o 1000 -g 1000 -m 0644 \
    /opt/scenariocraft/ScenarioCraft.jar /plugins/ScenarioCraft.jar
install -o 1000 -g 1000 -m 0644 \
    /demo/plugin-config.yml /plugins/ScenarioCraft/config.yml

#!/bin/sh
set -eu

secret=/run/scenariocraft/rcon-password
rounds=/rounds
umask 077
mkdir -p /run/scenariocraft
mkdir -p "${rounds}"
chown 1000:1000 "${rounds}"
chmod 0755 "${rounds}"
if [ ! -s "${secret}" ]; then
    od -An -N32 -tx1 /dev/urandom | tr -d ' \n' > "${secret}"
fi
chown 1000:1000 "${secret}"
chmod 0400 "${secret}"

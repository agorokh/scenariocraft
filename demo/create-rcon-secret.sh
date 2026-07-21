#!/bin/sh
set -eu

secret=/run/scenariocraft/rcon-password
umask 077
mkdir -p /run/scenariocraft
if [ ! -s "${secret}" ]; then
    od -An -N32 -tx1 /dev/urandom | tr -d ' \n' > "${secret}"
fi
chown 1000:1000 "${secret}"
chmod 0400 "${secret}"

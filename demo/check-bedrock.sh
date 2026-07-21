#!/bin/sh
set -eu

host="${1:-127.0.0.1}"
port="${2:-19132}"
timeout_seconds="${SCENARIOCRAFT_BEDROCK_TIMEOUT_SECONDS:-3}"

case "${port}" in
    ''|*[!0-9]*) echo "Bedrock port must be an integer from 1 to 65535." >&2; exit 2 ;;
esac
if [ "${port}" -lt 1 ] || [ "${port}" -gt 65535 ]; then
    echo "Bedrock port must be an integer from 1 to 65535." >&2
    exit 2
fi
case "${timeout_seconds}" in
    ''|*[!0-9]*) echo "SCENARIOCRAFT_BEDROCK_TIMEOUT_SECONDS must be a positive integer." >&2; exit 2 ;;
esac
if [ "${timeout_seconds}" -lt 1 ]; then
    echo "SCENARIOCRAFT_BEDROCK_TIMEOUT_SECONDS must be a positive integer." >&2
    exit 2
fi

command -v python3 >/dev/null 2>&1 || {
    echo "Python 3 is required for the RakNet probe." >&2
    exit 2
}

exec python3 - "${host}" "${port}" "${timeout_seconds}" <<'PY'
import secrets
import socket
import struct
import sys
import time

MAGIC = bytes.fromhex("00ffff00fefefefefdfdfdfd12345678")
EXPECTED_MOTD = "Speed Build"


def fail(message: str) -> "NoReturn":
    print(message, file=sys.stderr)
    raise SystemExit(1)


host = sys.argv[1]
port = int(sys.argv[2])
timeout_seconds = int(sys.argv[3])
timestamp = int(time.time() * 1000)
ping = b"\x01" + struct.pack(">Q", timestamp) + MAGIC + struct.pack(">Q", secrets.randbits(64))
last_error = "no response"

for family, socktype, proto, _, address in socket.getaddrinfo(
    host, port, type=socket.SOCK_DGRAM
):
    with socket.socket(family, socktype, proto) as probe:
        probe.settimeout(timeout_seconds)
        try:
            probe.connect(address)
            probe.send(ping)
            response = probe.recv(65535)
        except OSError as error:
            last_error = str(error)
            continue

    minimum_length = 1 + 8 + 8 + len(MAGIC) + 2
    if len(response) < minimum_length:
        fail(f"RakNet pong was too short: {len(response)} bytes")
    if response[0] != 0x1C:
        fail(f"Expected RakNet unconnected pong 0x1c, received 0x{response[0]:02x}")
    if response[17:33] != MAGIC:
        fail("RakNet pong contained an invalid offline-message identifier")

    motd_length = struct.unpack(">H", response[33:35])[0]
    motd_bytes = response[35:]
    if len(motd_bytes) != motd_length:
        fail(
            "RakNet pong MOTD length did not match payload: "
            f"declared {motd_length}, received {len(motd_bytes)}"
        )
    try:
        motd = motd_bytes.decode("utf-8")
    except UnicodeDecodeError as error:
        fail(f"RakNet pong MOTD was not UTF-8: {error}")
    if EXPECTED_MOTD not in motd:
        fail(f"RakNet pong did not contain {EXPECTED_MOTD!r}: {motd}")

    print(f"SCENARIOCRAFT_BEDROCK_OK host={host} port={port} motd={motd}")
    raise SystemExit(0)

fail(f"No RakNet pong from {host}:{port}: {last_error}")
PY

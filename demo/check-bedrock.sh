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
deadline = time.monotonic() + timeout_seconds

try:
    addresses = socket.getaddrinfo(host, port, type=socket.SOCK_DGRAM)
except socket.gaierror as error:
    fail(f"Could not resolve Bedrock host {host!r}: {error}")

for family, socktype, proto, _, address in addresses:
    remaining = deadline - time.monotonic()
    if remaining <= 0:
        last_error = f"timed out after {timeout_seconds} seconds"
        break
    with socket.socket(family, socktype, proto) as probe:
        probe.settimeout(remaining)
        try:
            probe.connect(address)
            probe.send(ping)
            response = probe.recv(65535)
        except OSError as error:
            last_error = str(error)
            continue

    minimum_length = 1 + 8 + 8 + len(MAGIC) + 2
    if len(response) < minimum_length:
        last_error = f"RakNet pong was too short: {len(response)} bytes"
        continue
    if response[0] != 0x1C:
        last_error = (
            f"expected RakNet unconnected pong 0x1c, received 0x{response[0]:02x}"
        )
        continue
    if response[1:9] != ping[1:9]:
        last_error = "RakNet pong timestamp did not match this probe"
        continue
    if response[17:33] != MAGIC:
        last_error = "RakNet pong contained an invalid offline-message identifier"
        continue

    motd_length = struct.unpack(">H", response[33:35])[0]
    motd_bytes = response[35:]
    if len(motd_bytes) != motd_length:
        last_error = (
            "RakNet pong MOTD length did not match payload: "
            f"declared {motd_length}, received {len(motd_bytes)}"
        )
        continue
    try:
        motd = motd_bytes.decode("utf-8")
    except UnicodeDecodeError as error:
        last_error = f"RakNet pong MOTD was not UTF-8: {error}"
        continue
    if EXPECTED_MOTD not in motd:
        last_error = f"RakNet pong did not contain {EXPECTED_MOTD!r}: {motd}"
        continue

    print(f"SCENARIOCRAFT_BEDROCK_OK host={host} port={port} motd={motd}")
    raise SystemExit(0)

fail(f"No valid RakNet pong from {host}:{port}: {last_error}")
PY

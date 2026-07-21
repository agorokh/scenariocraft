#!/usr/bin/env python3
import os
import socket
import struct
import subprocess
import threading

MAGIC = bytes.fromhex("00ffff00fefefefefdfdfdfd12345678")
MOTD = b"MCPE;ScenarioCraft Speed Build demo;999;1.21.0;0;20"


def run_fixture(stale_timestamp: bool) -> subprocess.CompletedProcess[str]:
    ready = threading.Event()
    port_holder: list[int] = []

    def serve() -> None:
        with socket.socket(socket.AF_INET, socket.SOCK_DGRAM) as server:
            server.bind(("127.0.0.1", 0))
            port_holder.append(server.getsockname()[1])
            ready.set()
            request, address = server.recvfrom(65535)
            timestamp = request[1:9]
            if stale_timestamp:
                timestamp = struct.pack(">Q", struct.unpack(">Q", timestamp)[0] - 1)
            pong = b"\x1c" + timestamp + struct.pack(">Q", 7) + MAGIC
            pong += struct.pack(">H", len(MOTD)) + MOTD
            server.sendto(pong, address)

    thread = threading.Thread(target=serve, daemon=True)
    thread.start()
    if not ready.wait(timeout=2):
        raise AssertionError("UDP fixture did not bind")
    environment = os.environ.copy()
    environment["SCENARIOCRAFT_BEDROCK_TIMEOUT_SECONDS"] = "2"
    result = subprocess.run(
        ["./demo/check-bedrock.sh", "127.0.0.1", str(port_holder[0])],
        check=False,
        capture_output=True,
        text=True,
        env=environment,
        timeout=5,
    )
    thread.join(timeout=2)
    return result


valid = run_fixture(stale_timestamp=False)
assert valid.returncode == 0, valid.stderr
assert "SCENARIOCRAFT_BEDROCK_OK" in valid.stdout, valid.stdout

stale = run_fixture(stale_timestamp=True)
assert stale.returncode == 1, stale.stdout
assert "timestamp did not match" in stale.stderr, stale.stderr

print("SCENARIOCRAFT_BEDROCK_PROBE_TEST_OK")

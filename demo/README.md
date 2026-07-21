# ScenarioCraft Docker demo

Run these exact steps from a clean machine with Docker Compose and Minecraft Java 1.21.x:

1. `git clone https://github.com/agorokh/scenariocraft.git && cd scenariocraft`
2. `export OPENAI_API_KEY='<your OpenAI API key>'`
3. `docker compose up --build`
4. Join `localhost:25565` in Minecraft.
5. Run `/speedbuild start` in chat.

The local demo intentionally uses Paper 1.21.11 in offline mode so a submission judge can
join without account-server setup. Do not expose it to an untrusted network. RCON is enabled
only on the private Compose network; a random password is generated into a private Docker
volume and is never stored in the repository or exposed on a host port.

Solo mode is enabled in the demo configuration. With one human player, ScenarioCraft builds
and exports a bundled rocket in the second plot, so the normal two-entry judging and chat
verdict path still runs. Wait for the `SCENARIOCRAFT_DEMO_ARENA_READY` log before starting a
round; this normally appears before a client finishes joining.

For a no-client proof of the same server, export, judge, RCON, and announcement pipeline, run
`make demo`. It starts a round over RCON and reports `SCENARIOCRAFT_DEMO_SUCCESS` when the
verdict has been announced. `SCENARIOCRAFT_DEMO_KEEP=true make demo` leaves the containers
and volumes running for inspection. Maintainers without an API key may exercise packaging
with `make demo-dry-run`; that is test evidence only, not acceptance evidence for the live
judge path.

## One household, every device

Different kids can bring an iPad, Windows Bedrock client, or Java PC to one kid-safe server
that still feels like a real online game. The same secret prompt, timers, and warm AI panel
judge every build the same way, whatever device its builder uses. Consoles need a separate
LAN discovery or redirect setup, as documented below.

## Bedrock on Linux

The opt-in `docker-compose.bedrock.yml` overlay adds Geyser, Floodgate, ViaVersion, and the
Bedrock UDP port without changing the Java-only base demo. On a Linux Docker host:

1. Export `OPENAI_API_KEY` as in the base quickstart.
2. Start the stack:

   ```sh
   docker compose -f docker-compose.yml -f docker-compose.bedrock.yml up --build -d
   ```

3. Wait for Geyser to generate its writable configuration, switch it to Floodgate
   authentication, and restart Paper:

   ```sh
   docker compose -f docker-compose.yml -f docker-compose.bedrock.yml exec paper \
     sh -c "until test -f plugins/Geyser-Spigot/config.yml; do sleep 1; done; \
     sed -i 's/auth-type: online/auth-type: floodgate/' plugins/Geyser-Spigot/config.yml; \
     grep -q 'auth-type: floodgate' plugins/Geyser-Spigot/config.yml"
   docker compose -f docker-compose.yml -f docker-compose.bedrock.yml restart paper
   ```

   This edits the generated file inside the named plugin volume. Do not bind-mount a file or
   directory into `/data/plugins`: Docker can create it as root, which prevents Floodgate
   from writing its private `key.pem` and makes Bedrock authentication fail closed.

4. From the Linux host, verify the live RakNet endpoint and Speed Build MOTD:

   ```sh
   ./demo/check-bedrock.sh 127.0.0.1 19132
   ```

   A healthy endpoint prints `SCENARIOCRAFT_BEDROCK_OK`. To probe another machine, replace
   `127.0.0.1` with that host's LAN address. Keep UDP 19132 open only to the trusted LAN.

The beta version settings are intentional: Geyser currently publishes beta-type Modrinth
builds. The overlay pins Geyser 2.11.0 build 1200 (`U1DOZeks`) and ViaVersion 5.11.1
snapshot build 1042 (`CjleI5xo`), the exact Modrinth artifacts exercised by this delivery.
The `bedrock-plugins` setup service downloads Floodgate 2.2.5 build 138 from GeyserMC's
versioned endpoint with three 30-second attempts and verifies its committed SHA-256 checksum
before placing the jar in the shared plugin volume; a stalled or changed artifact stops
startup. Floodgate's Modrinth files do not match the Paper and Minecraft version tags used
by this demo. ViaVersion bridges the newer Java protocol expected by Geyser to Paper
1.21.11.

### How players join

- A Java PC connects to the Docker host on port `25565`.
- iPad, Android, and Windows Bedrock players add the Docker host as a server on UDP port
  `19132`.
- Xbox and PlayStation do not expose the same direct custom-server entry flow. Docker bridge
  networking also does not relay Geyser's LAN broadcast to the physical LAN, so this overlay
  does **not** make the server appear under **Friends > LAN Games**. Console play requires a
  separately supported LAN broadcast relay, DNS redirect, or host-networked Geyser setup;
  none is bundled or claimed by this demo.

After a Bedrock client joins, run `/speedbuild start` and complete a round. A RakNet pong
proves discovery and the MOTD, not gameplay; the client round is separate acceptance
evidence.

## Docker Desktop on macOS

The Compose overlay alone is not a working Bedrock path on Docker Desktop on macOS. The
container can answer RakNet inside the Compose network while UDP forwarded through host
loopback still times out. Use Geyser Standalone on the Mac host instead of claiming the
overlay is sufficient:

1. Start the Bedrock overlay with its ineffective container UDP publication moved aside so
   host port 19132 remains available:

   ```sh
   SCENARIOCRAFT_BEDROCK_PORT=19133 \
     docker compose -f docker-compose.yml -f docker-compose.bedrock.yml up --build -d
   ```

2. Let Floodgate generate `/data/plugins/floodgate/key.pem`, then copy it out without
   printing it and restrict the local file to your account:

   ```sh
   install -d -m 700 .local/geyser
   docker compose -f docker-compose.yml -f docker-compose.bedrock.yml \
     cp paper:/data/plugins/floodgate/key.pem .local/geyser/key.pem
   chmod 600 .local/geyser/key.pem
   ```

   `.local/` is ignored by this repository. Never commit, log, or share `key.pem`.

3. Download the current Geyser Standalone jar from GeyserMC into `.local/geyser/`, run it
   once to generate its config, then set `java.address` to `127.0.0.1`, the Java port to
   `25565`, the Bedrock port to `19132`, and `auth-type` to `floodgate`. Keep `key.pem` beside
   the standalone config and restart Geyser Standalone.
4. Run `./demo/check-bedrock.sh 127.0.0.1 19132`, then join from a Bedrock device on the same
   Wi-Fi using the Mac's LAN address.

The Floodgate key is intentionally copied for this split plugin/standalone topology. If it
is exposed, stop the stack, delete the Floodgate plugin directory so a new key is generated,
replace the standalone copy, and restart both sides.

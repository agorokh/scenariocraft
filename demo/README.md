# ScenarioCraft Docker demo

Run these exact steps from a clean machine with Docker Compose, GNU Make, `curl`, and
Python 3.10 or newer. macOS also requires Java 21 (for example,
`brew install openjdk@21`) for the LAN-facing Geyser service:

1. `git clone https://github.com/agorokh/scenariocraft.git && cd scenariocraft`
2. `export OPENAI_API_KEY='<your OpenAI API key>'`
3. `make family-up`
4. Join `localhost:25565` from Java, or join the Docker host's LAN IP on UDP port `19132`
   from Bedrock. With a macOS host, Xbox players on the same LAN can select
   **ScenarioCraft family demo** under **Friends > LAN Games**.
5. Run `/speedbuild start` in chat.

The local demo intentionally uses Paper 1.21.11 in offline mode so a submission judge can
join without account-server setup. Geyser, Floodgate, and ViaVersion are installed by Compose.
On Linux the Geyser plugin owns UDP `19132`. On macOS the start command installs a host
LaunchAgent because Colima and some Docker Desktop configurations do not expose container UDP
to LAN discovery; it verifies pinned Geyser Standalone 2.11.0 build 1201, copies and compares
Floodgate's key with the live Paper
container, and refuses to report success until a real RakNet probe answers. This is automatic,
not a manual standalone-Geyser procedure. The default family configuration uses the complete
prompt deck and a 10-minute build phase. Do not expose it to an untrusted network. RCON is enabled
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
judge path. These automated proof commands explicitly select `demo/plugin-config.yml` for
short CI timings; ordinary `make family-up` uses `demo/family-config.yml`.

Use `make family-status` to verify the containers and Bedrock UDP response. Use
`make family-down` to stop the normal family server without deleting its worlds. The proof
commands continue to manage their own short-lived Compose environment.

On macOS, approve Java's incoming-network firewall prompt. Xbox LAN discovery also requires
the Mac and console to be on the same non-guest network without wireless client isolation.
If `make family-status` does not report a successful RakNet probe, inspect
`.local/geyser/geyser.log` and run
`docker compose --project-name scenariocraft -f docker-compose.yml -f docker-compose.bedrock.yml logs paper judge`.
Rerunning `make family-up`
resynchronizes Floodgate after a volume reset; never copy its key manually or start another
Geyser listener on UDP `19132`.

## One household, every device

Different kids can bring an iPad, Windows Bedrock client, Java PC, or Xbox to one kid-safe
server that still feels like a real online game. The same secret prompt, timers, and warm AI
panel judge every build the same way, whatever device its builder uses. `make family-up`
selects the supported bridge for the host platform.

## Bedrock on Linux

The opt-in `docker-compose.bedrock.yml` overlay adds Geyser, Floodgate, ViaVersion, and the
Bedrock UDP port without changing the Java-only base demo. On a Linux Docker host:

1. Export `OPENAI_API_KEY` as in the base quickstart.
2. Start the stack:

   ```sh
   docker compose -f docker-compose.yml -f docker-compose.bedrock.yml up --build -d
   ```

3. The one-shot `bedrock-plugins` service creates or updates the writable
   `plugins/Geyser-Spigot/config.yml` in the named plugin volume with
   `java.auth-type: floodgate` before Paper starts. It preserves the rest of an existing
   config, owns the directory as the server user, and removes the manual edit-and-restart
   race. Do not bind-mount a file or directory into `/data/plugins`: Docker can create it as
   root, which prevents Floodgate from writing its private `key.pem` and makes Bedrock
   authentication fail closed.

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

Hosted CI runs `make bedrock-compose-smoke` on Linux. It builds the plugin, starts the real
Paper overlay, verifies the pinned downloads, nested Floodgate auth, and generated key,
waits for Geyser UDP 19132, and sends the same RakNet probe before tearing down all smoke volumes.
That is runtime wiring evidence, not a substitute for a real Bedrock player completing a
round.

CI also runs `make family-server-check`, a mocked Linux lifecycle that exercises the
documented `up`, successful and failed `status`, and `down` paths while proving inherited
proof timing and alternate-port variables cannot change the family contract.

### How players join

- A Java PC connects to the Docker host on port `25565`.
- iPad, Android, and Windows Bedrock players add the Docker host as a server on UDP port
  `19132`.
- Xbox and PlayStation do not expose the same direct custom-server entry flow. On Linux,
  console discovery can still depend on the host network and router; on macOS,
  `make family-up` supplies the host-networked Geyser service that was verified with Xbox.

After a Bedrock client joins, run `/speedbuild start` and complete a round. A RakNet pong
proves discovery and the MOTD, not gameplay; the client round is separate acceptance
evidence.

## Docker Desktop on macOS

The Compose overlay alone is not a working Bedrock path on Docker Desktop or Colima on
macOS: container Geyser can be healthy while forwarded UDP still times out. `make family-up`
therefore moves container Geyser to fallback host port `19133`, downloads pinned,
checksum-verified Geyser Standalone 2.11.0 build 1201 under ignored `.local/geyser/`, generates its Floodgate configuration,
copies the live key without printing it, installs a persistent user LaunchAgent on UDP
`19132`, and refuses success unless `demo/check-bedrock.sh` receives a valid Speed Build
RakNet pong. This is the supported path; no manual config edit, key copy, or second command
is required.

`make family-down` unloads the LaunchAgent and stops Compose without deleting the named
world volumes. If Floodgate data is regenerated, rerun `make family-up` to resynchronize it.
Never commit, print, or share `.local/geyser/key.pem`.

## Played-for-real documentation proof

From a clean clone with `OPENAI_API_KEY` exported, run `make proof-round`.
The target joins Blocky, Crafty, and Pixel through Mineflayer, drives the Secret Chest and a
complete real round, waits for live judge results in game, and publishes the round's frozen data
and renderer images under `site/`. Every step has a timeout and exits non-zero on failure. The
services remain available for inspection; finish with `docker compose down --volumes`.

Public CI never starts this path and never receives a judge key. It regenerates every renderer
view from the committed voxel exports with `make proof-check`, compares the PNGs byte-for-byte,
and confirms that `site/index.html` regenerates byte-for-byte from the bundle.

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

## Played-for-real documentation proof

From a clean clone with `OPENAI_API_KEY` exported, run `make proof-round`.
The target joins Blocky, Crafty, and Pixel through Mineflayer, drives the Secret Chest and a
complete real round, waits for live judge results in game, and publishes the round's frozen data
and renderer images under `site/`. Every step has a timeout and exits non-zero on failure. The
services remain available for inspection; finish with `docker compose down --volumes`.

Public CI never starts this path and never receives a judge key. It regenerates every renderer
view from the committed voxel exports with `make proof-check`, compares the PNGs byte-for-byte,
and confirms that `site/index.html` regenerates byte-for-byte from the bundle.

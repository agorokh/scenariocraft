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

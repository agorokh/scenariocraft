# ScenarioCraft

ScenarioCraft turns our kids' game ideas into playable family rituals: they invent a scenario,
we shape it into a spec together, an AI coding agent builds it, and an AI panel referees the
match while we play. Build Battle is reference scenario #1, not the product. The product is
the repeatable, kid-legible, parent-guidable, agent-buildable, warmly judged loop that can
welcome whatever scenario comes up in the car next week.

> **NOT AN OFFICIAL MINECRAFT PRODUCT. NOT APPROVED BY OR ASSOCIATED WITH MOJANG OR
> MICROSOFT.**

## The co-creators

- A 7-year-old game brainstormer and judge auditor.
- A 10-year-old idea author, gaem designer, logo designer and UX lead, judge auditor.
- A 17-year-old contributing engineer working in his own Codex sessions, tester.
- Their dad, facilitating the build and playing every round.

Their design feedback is product input. Relevant choices are recorded as “Decisions by the
design council” without publishing any child's name.

## First scenario: Build Battle

Contestants receive a secret build prompt, create in private plots, tour the finished builds,
and hear a warm three-persona AI panel score every entry against one shared rubric.

Gameplay arrives through the Build Week issue sequence. This scaffold intentionally contains
no game logic yet.

## Quickstart

Quickstart instructions will be added with the runnable demo instance in BB-12. Contributors
can build the scaffold now with Java 21:

```sh
./gradlew build
```

The plugin jar is written to `build/libs/`.

## Build Battle operator notes

During an active Speed Build, ScenarioCraft protects the entire configured
`battle_world`: it contains explosions, pistons, dispensers, fire, fluid flow, block fading
and leaf decay, decorative-entity interactions, and entity-driven/block-form changes until
the controller returns to `IDLE`.
During plot entry and BUILDING, contestant teleports are accepted only inside their assigned
boundary, and non-contestant teleports into private plots are rejected
(controller-owned phase moves are tracked explicitly). The plugin logs one
activation message when each round starts. Keep unrelated builds and minigames in a
different world.

`wall-height` must leave one additional block above the concrete wall for the
anti-peek roof: `floor Y + wall-height + 1` must be below the battle world's
exclusive maximum height. If an older configuration is now too tall, startup
reports the configured value, calculated roof Y, world maximum, and minimum
reduction.

Controller-owned moves use explicit-world console teleports and verify the result on the
server, including bounded confirmation after 1, 5, and 20 total ticks for chunk-loading or
lifecycle delays. Startup and round start check that the exact `minecraft:execute` and
`minecraft:tp` registrations remain available without teleporting players as a health probe;
every relocation checks again before production dispatch, and CI executes the full command
path on real Paper. Keep both exact namespaced commands available in server configuration.
A failed
move logs `SCENARIOCRAFT_TELEPORT_FAILURE` and alerts every online operator. Run
`/battle stop`, move the named player safely if needed, and have them reconnect. Rejoin
retries a confirmed hub return; a successful recovery is logged and clears temporary
containment only after the hub arrival and player data are saved. An atomic plugin-owned
registry under the ScenarioCraft data folder retains pending player UUIDs even when a
player-data save fails, so enable and rejoin rediscover the recovery after a restart. Verify
that the player is at the hub with the default world border and can drop/pick up items before
starting the next round. Operators who join while recovery is pending receive another alert.
To inspect the durable queue, read
`plugins/ScenarioCraft/pending-teleport-recovery.txt`; it contains one pending player UUID per
line. Do not manually remove an entry: reconnect that player and let ScenarioCraft clear the
entry only after it confirms the hub arrival and saves the player data. If the entry remains,
keep the player contained and use the persistence alert's exception in the server log to fix
the underlying storage problem before reconnecting them again. The registry requires a data
folder filesystem that supports same-directory atomic moves; unsupported storage fails closed
at enable. Runtime registry writes emit `SCENARIOCRAFT_RECOVERY_REGISTRY_FAILURE` with the
registry path, distinct from player-data `SCENARIOCRAFT_RECOVERY_PERSISTENCE_FAILURE`, rather
than weakening the atomic-write guarantee. An operator command may move a pending player only
to the configured hub; the controller supervises that arrival and performs the same saved,
atomic clear sequence.
If enable fails with `Could not load teleport recovery registry`, stop the server and back up
the registry before editing it. Correct any malformed line to a UUID; move the file aside only
after verifying that no listed player is still mid-recovery, then restart and confirm every
affected player is safely at the hub.

Alerts are also sent to the server console and to online players with the
`scenariocraft.alerts` permission, which defaults to operators.

A rejected console dispatch is retried once before it is treated as a failure. If saving a
player-data recovery marker or the plugin-owned registry fails, the console and online
operators receive a separate persistence alert; keep the player contained and have them
reconnect so the hub return and durable clear are retried. The server console is the durable
on-call signal when no privileged player is online: monitor for
`SCENARIOCRAFT_TELEPORT_FAILURE`, `SCENARIOCRAFT_RECOVERY_PERSISTENCE_FAILURE`, and
`SCENARIOCRAFT_RECOVERY_REGISTRY_FAILURE`, then follow the recovery steps above before starting
another round. Startup logs `SCENARIOCRAFT_PENDING_RECOVERY_COUNT` with the registry path
whenever persisted recoveries are waiting; each one resumes when that player rejoins.

## How this was built

ScenarioCraft is being built in the open for **OpenAI Build Week (July 2026)**. Every change
starts from a GitHub issue and is implemented by Codex.

The engineering discipline used here was not invented for Build Week. It is a public port of
a privately evolved, multi-model agentic engineering system: pitfall ledgers, decision logs,
eval gates, and session handoffs. ScenarioCraft is the first public project built with that
method and Codex as its sole software builder.

## License

ScenarioCraft is available under the [MIT License](LICENSE).

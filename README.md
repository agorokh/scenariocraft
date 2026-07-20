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

During an active Build Battle, ScenarioCraft protects the entire configured
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
lifecycle delays. Startup fails fast if the vanilla `minecraft:execute` or `minecraft:tp`
console command is missing; the exact namespaced registrations used for dispatch are
required. Keep both commands available in server command configuration. A failed
move logs `SCENARIOCRAFT_TELEPORT_FAILURE` and alerts every online operator. Run
`/battle stop`, move the named player safely if needed, and have them reconnect. Rejoin
retries a confirmed hub return; a successful recovery is logged and clears temporary
containment. Once its player-data save succeeds, the recovery marker survives disable/reload
until the exit is confirmed. Verify that the player is at the hub with the default world border and can
drop/pick up items before starting the next round. Operators who join while recovery is
pending receive another alert.

Alerts are also sent to the server console and to online players with the
`scenariocraft.alerts` permission, which defaults to operators.

A rejected console dispatch is retried once before it is treated as a failure. If saving a
recovery marker fails, online operators receive a separate persistence alert; keep the
server running and have the named player reconnect so the hub return and player-data save
are retried. Each failed recovery move retries the marker save. Until a save succeeds, the
in-memory containment does not survive a restart; manually contain the named player before
any unavoidable restart.

## How this was built

ScenarioCraft is being built in the open for **OpenAI Build Week (July 2026)**. Every change
starts from a GitHub issue and is implemented by Codex.

The engineering discipline used here was not invented for Build Week. It is a public port of
a privately evolved, multi-model agentic engineering system: pitfall ledgers, decision logs,
eval gates, and session handoffs. ScenarioCraft is the first public project built with that
method and Codex as its sole software builder.

## License

ScenarioCraft is available under the [MIT License](LICENSE).

# ScenarioCraft Agent Guide

ScenarioCraft turns kid-invented Minecraft game ideas into Paper plugins through a
parent-guided, agent-built, warmly judged loop. Build Battle is the first scenario, not a
framework boundary.

## Repository layout

- `src/main/java/` — Paper plugin code.
- `src/main/resources/` — `plugin.yml` and future runtime configuration.
- `src/test/java/` — unit tests.
- `docs/plans/` — ExecPlans for multi-hour work.
- `evals/` — judge regression cases added by BB-11.

Keep scenario-specific code in a scenario-named package when it arrives. Do not build a
general scenario framework before BB-14.

## Build and test

Use the checked-in Gradle wrapper and Java 21:

```sh
make ci-fast
./gradlew build
./gradlew test
./gradlew test --tests 'io.github.agorokh.scenariocraft.PluginDescriptorTest'
```

`make ci-fast` is the local CI-parity gate. The build jar is written to `build/libs/`.

## Conventions

- Batch arena block work behind a per-tick budget; never perform an unbounded block mutation
  loop on the server main thread.
- Use chat, titles, and bossbars for controls and feedback. Do not add inventory GUIs; Bedrock
  players through Geyser are first-class.
- Keep every player-facing string kid-appropriate. Judge output must name at least one genuine
  strength in every build.
- Keep timings and task decks configurable rather than hard-coded.
- Read `code_review.md` before review. Treat its P1 conditions as merge blockers.
- Read the current issue and its acceptance criteria before implementation. Do not expand scope
  silently.

## Pull requests

- One issue → one Codex session → one pull request.
- Include the issue number and Codex session ID in the pull request description.
- Include tests and the issue's requested acceptance evidence.
- Run `/review` and fix every P1 before merge.
- Never merge with failing CI.

## Corrections

Entries in this section must be dated and trace to a repeated operator correction.

- 2026-07-19 — Do not create or reference infrastructure that does not exist in this
  repository. A session added `docs/01_Vault/` handoff notes describing a memory bridge, a
  Tier-3 workspace, a code-edit gate, a self-hosted reviewer, and a resolve-gate — none of
  which exist here. Durable memory in this repository is: the issue backlog, `docs/plans/`
  ExecPlans, and this Corrections section. Nothing else.

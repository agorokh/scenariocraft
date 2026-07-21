# ExecPlan: BB-09 in-game announcer

Issue: #11
Owner: Codex
Status: Complete

## Purpose

Close the Build Battle judging loop so a completed judge run reaches players without an
operator copying text: the standalone judge requests an in-server announcement over RCON,
the plugin notices newly written results during REVEAL, and `/battle results` can replay the
latest kid-friendly summary from disk. This needs an ExecPlan because it crosses a TCP
protocol boundary, untrusted file parsing, Paper scheduling and command handling, round
lifecycle state, and real-server acceptance evidence.

## Progress

- [x] Define the smallest end-to-end slice.
- [x] Implement with tests.
- [x] Capture the issue's acceptance evidence.
- [x] Complete `/review` and resolve P1 findings.
- [x] Resolve current-head review findings and rerun local CI.
- [x] Record the retrospective.

Update this list as work proceeds. Add timestamps when a checkpoint is useful to the next
session.

## Decision Log

| Date | Decision | Why |
| --- | --- | --- |
| 2026-07-20 | Have the judge atomically publish `results.json` and `results.txt` before making a best-effort RCON request. | A network or authentication failure must never erase or suppress the durable result artifacts. |
| 2026-07-20 | Make one plugin-side results service own bounded `results.txt` parsing, replay formatting, title/chat broadcast, winner particles, and duplicate suppression. | `/battle results`, the REVEAL poll, and the RCON-triggered command must display the same Bedrock-safe text without racing into duplicate announcements or exposing raw JSON. |
| 2026-07-20 | Send the plugin's existing `battle results` command over Source RCON instead of assembling player-facing Minecraft commands in the judge. | Paper stays authoritative for current phase, plot coordinates, command output, and deduplication, while the judge still initiates the complete in-game announcement through RCON. |
| 2026-07-20 | Read RCON settings from `SCENARIOCRAFT_RCON_HOST`, `SCENARIOCRAFT_RCON_PORT`, and `SCENARIOCRAFT_RCON_PASSWORD`, falling back to optional `judge.yml` fields. | Deployments can keep credentials out of the repository while retaining a file-based configuration option requested by the issue. |
| 2026-07-21 | Keep result-file reads for automatic polling on an async scheduler task and return only the announcement to the server thread. | Even bounded filesystem work can stall a tick on slow storage; a single-flight handoff keeps Bukkit calls on the main thread without overlapping reads. |
| 2026-07-21 | Treat a missing `results-poll-ticks` as the packaged default, while still rejecting an explicitly invalid value. | Existing generated configs do not gain newly added keys during upgrade, so a mandatory lookup would prevent the plugin from enabling. |
| 2026-07-21 | Read player-requested result replays asynchronously too, and replace machine-oriented no-winner reasons with a fixed warm message. | Players may issue the replay command repeatedly against shared storage, and operational quorum diagnostics are not appropriate judge feedback for children. |
| 2026-07-21 | Skip `judge.yml` entirely when the environment supplies the complete RCON trio. | Environment overrides must remain usable when an optional local configuration file is stale, malformed, or inaccessible. |
| 2026-07-21 | Use the async read/main-thread delivery boundary for RCON-triggered announcements as well as polling and player replay. | Every path reads the same potentially shared results directory; the transport that initiates a read does not make synchronous filesystem work safe on Paper's main thread. |
| 2026-07-21 | Bind REVEAL polling and RCON announcements to the round ID returned when the active export starts. | After restart, the newest completed directory may belong to the prior battle while the current snapshot is still being written; global-newest selection can celebrate a stale winner in the new arena. |

## Surprises & Discoveries

- `results.txt` is already atomically published alongside `results.json`, and its human-readable
  shape contains the winner plus contestant/persona lines needed for Bedrock-safe replay. The
  plugin can consume that contract without importing judge implementation classes or parsing
  the raw JSON artifact.
- The round exporter writes immutable timestamped directories under the server's `rounds/`
  directory. Polling only while the controller is in REVEAL keeps the happy path simple and
  avoids a filesystem watcher thread crossing Paper lifecycle boundaries.
- The plugin's production round directory is under its data folder,
  `plugins/ScenarioCraft/rounds/`, rather than at the server root. Automatic polling must
  inspect only the newest round directory so a server restart cannot announce an older verdict;
  the explicit replay command may still find the latest completed result.
- Paper 1.21.11's RCON reader expects one socket read to contain a whole request packet. Writing
  a valid packet through several stream writes caused Paper to close the connection after a
  partial read. Buffering each packet and issuing one socket write fixed the real server path;
  a regression test pins that behavior.
- `Server.broadcastMessage` includes command senders in its permission-aware broadcast. While a
  new Paper RCON sender was initializing, that path dereferenced an unset permission delegate.
  Sending chat lines directly to online players is both safer and a closer match for the issue.
- Current-head review caught three boundary cases that the first pass missed: legacy plugin
  configs omit newly added keys, command blocks are non-player command senders, and SnakeYAML
  reports malformed input with its own runtime exception type. Regression tests now pin the
  backward-compatible default, console/RCON-only command path, and best-effort malformed-YAML
  behavior.
- The replacement-head review found two more cross-boundary cases: player-triggered replay reads
  need the same async handoff as polling, and a complete environment override must not parse an
  irrelevant stale `judge.yml`. It also caught that machine-oriented quorum diagnostics could
  reach children; the formatter now keeps those details on disk and displays a warm neutral
  no-winner message in game.
- The next current-head review completed the boundary audit: RCON-triggered reads now use the
  same async handoff, unchecked reader failures are converted into completed read failures so
  the single-flight poll gate always reopens, and legacy Minecraft section-sign formatting is
  stripped before any judge text reaches chat or titles.
- A later review found the restart/export window where global-newest polling could announce a
  prior battle. The exporter now returns the active round ID, the controller exposes it to the
  announcement service, and async completion revalidates both phase and round ID before any
  player-facing effect.
- The first active-round implementation correctly made the old synthetic future-dated smoke
  fixture ineligible, exposing that the smoke no longer modeled the production contract. The
  smoke now waits for the real active export, overlays deterministic judge inputs into that
  exact directory, and verifies RCON, polling deduplication, and replay against its dynamic ID.

## Acceptance evidence

- `make ci-fast` passed on Java 21 with 138 plugin tests, 78 judge tests, and 14 renderer tests
  (230 total), zero failures after the final boundary-hardening review fixes.
- `BattleResultsReaderTest`, `ResultAnnouncementFormatterTest`, and
  `ResultAnnouncementServiceTest` cover the friendly no-results path, strict bounded text
  parsing, raw-JSON rejection/cleaning, 120-code-point chat lines, 64-code-point titles,
  per-persona score/comment lines, one-shot deduplication, title delivery, and winner particles.
- `RconConfigTest`, `RconClientTest`, `RconResultAnnouncerTest`, and
  `JudgeApplicationTest.rconFailureAfterJudgingKeepsBothResultArtifactsAndSuccessStatus` cover
  env/`judge.yml` precedence, bounded configuration, one-write Source-RCON packets, real command
  selection, redacted diagnostics, and post-publication failure semantics.
- The exact YAML-extracted CI smoke program passed locally against checksum-pinned Paper
  1.21.11 build 132 on RCON port 25585. The shortened Build Battle reached
  `BUILDING -> REVEAL`; the installed judge wrote fixture results and authenticated over RCON;
  Paper logged `SCENARIOCRAFT_RESULTS_ANNOUNCED round-20991231-235959 chat_lines=8` exactly once;
  `battle results` logged `Winner: Alex!`; and Paper shut down with all dimensions saved.
- The same live smoke reran the judge with an intentionally wrong RCON password. The judge
  remained successful, both result artifacts remained non-empty, and diagnostics reported
  `RCON authentication failed` without printing either password.
- The modified workflow parsed as YAML and its complete embedded Paper smoke program passed
  `bash -n` after GitHub-expression substitution.
- `/review` against `code_review.md` found no P1: judge and announcement logic have focused and
  real-server coverage, player-facing output is bounded and kid-appropriate, no raw JSON,
  inventory GUI, or block mutation was introduced, timings remain configurable, and no real
  credential is present.

## Retrospective

BB-09 closes the visible judging loop without coupling the standalone judge to Bukkit. The
judge owns durable publication and a best-effort authenticated transport; the plugin owns
phase awareness, file replay, Bedrock-safe presentation, deduplication, and plot effects. The
most useful failure was the real RCON smoke: it exposed both Paper's packet-read assumption and
its permission-aware broadcast edge case, neither of which the in-memory protocol and service
tests could reveal alone. Keeping the disk artifact authoritative makes both failures graceful
and preserves `/battle results` as the operator/player recovery path.

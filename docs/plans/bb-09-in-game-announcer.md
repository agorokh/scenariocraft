# ExecPlan: BB-09 in-game announcer

Issue: #11
Owner: Codex
Status: In progress

## Purpose

Close the Build Battle judging loop so a completed judge run reaches players without an
operator copying text: the standalone judge requests an in-server announcement over RCON,
the plugin notices newly written results during REVEAL, and `/battle results` can replay the
latest kid-friendly summary from disk. This needs an ExecPlan because it crosses a TCP
protocol boundary, untrusted file parsing, Paper scheduling and command handling, round
lifecycle state, and real-server acceptance evidence.

## Progress

- [x] Define the smallest end-to-end slice.
- [ ] Implement with tests.
- [ ] Capture the issue's acceptance evidence.
- [ ] Complete `/review` and resolve P1 findings.
- [ ] Record the retrospective.

Update this list as work proceeds. Add timestamps when a checkpoint is useful to the next
session.

## Decision Log

| Date | Decision | Why |
| --- | --- | --- |
| 2026-07-20 | Have the judge atomically publish `results.json` and `results.txt` before making a best-effort RCON request. | A network or authentication failure must never erase or suppress the durable result artifacts. |
| 2026-07-20 | Make one plugin-side results service own bounded `results.txt` parsing, replay formatting, title/chat broadcast, winner particles, and duplicate suppression. | `/battle results`, the REVEAL poll, and the RCON-triggered command must display the same Bedrock-safe text without racing into duplicate announcements or exposing raw JSON. |
| 2026-07-20 | Send the plugin's existing `battle results` command over Source RCON instead of assembling player-facing Minecraft commands in the judge. | Paper stays authoritative for current phase, plot coordinates, command output, and deduplication, while the judge still initiates the complete in-game announcement through RCON. |
| 2026-07-20 | Read RCON settings from `SCENARIOCRAFT_RCON_HOST`, `SCENARIOCRAFT_RCON_PORT`, and `SCENARIOCRAFT_RCON_PASSWORD`, falling back to optional `judge.yml` fields. | Deployments can keep credentials out of the repository while retaining a file-based configuration option requested by the issue. |

## Surprises & Discoveries

- `results.txt` is already atomically published alongside `results.json`, and its human-readable
  shape contains the winner plus contestant/persona lines needed for Bedrock-safe replay. The
  plugin can consume that contract without importing judge implementation classes or parsing
  the raw JSON artifact.
- The round exporter writes immutable timestamped directories under the server's `rounds/`
  directory. Polling only while the controller is in REVEAL keeps the happy path simple and
  avoids a filesystem watcher thread crossing Paper lifecycle boundaries.

## Acceptance evidence

To be completed with focused unit tests, `make ci-fast`, and a pinned local Paper/RCON smoke
that proves automatic chat output, command replay, winner effect dispatch, and disk fallback
after an intentional RCON failure.

## Retrospective

To be completed after implementation, review, and acceptance verification.

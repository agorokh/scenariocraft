# ExecPlan: BB-09 in-game announcer

Issue: #11
Owner: Codex session 019f8312-8590-7c30-ac1b-121082f7df69
Status: In progress

## Purpose

Close the Build Battle judging loop so a completed judge run reaches players without an
operator copying output: the judge writes the durable result artifacts, asks the running
server to announce over RCON, and the plugin independently notices the same `results.txt`
during REVEAL. Players can also reprint the latest result with `/battle results` after the
round. This needs an ExecPlan because it crosses the standalone judge, RCON protocol, Paper
scheduling, round lifecycle, filesystem publication, kid-safe formatting, and live-server
acceptance boundaries.

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
| 2026-07-20 | Keep `results.txt` as the durable plugin-facing contract and make the plugin the single owner of chat, title, and particle presentation. | The plugin must reprint results even when judging ran elsewhere, and a single formatter prevents RCON and polling from drifting or leaking raw JSON. |
| 2026-07-20 | Have the judge write both result files before attempting a narrow authenticated RCON `battle announce <round-id>` command. | Disk publication must survive network/authentication failure, while the command lets Bukkit resolve the active arena world and winner plot safely. |
| 2026-07-20 | Poll bounded result files off the server main thread, then marshal only Bukkit presentation work back to the main thread and deduplicate by round/result identity. | Filesystem I/O should not stall ticks, and RCON plus polling may observe the same result concurrently. |
| 2026-07-20 | Load RCON host, port, password, and timeouts from environment overrides or an optional external `judge.yml`, with no repository credential. | The issue requires both configuration surfaces and `code_review.md` forbids credentials in source, fixtures, logs, or history. |

## Surprises & Discoveries

- `RoundExportService` chooses the timestamped round ID internally and writes asynchronously;
  `RoundController` currently has no completion callback or result-watch seam. The announcer
  therefore needs a bounded rounds-directory discovery contract instead of assuming the
  controller already knows the final path.
- `results.txt` already contains contestant-to-plot identity, persona comments, means, and
  the winner, so the plugin can remain independent of `results.json`. Winner coordinates are
  still an active-round concern and must come from the controller's plot map rather than raw
  result JSON.

## Acceptance evidence

- Pending: local Java 21 `make ci-fast`, focused judge RCON/config tests, plugin parser,
  formatting, no-results, polling/deduplication, command, and celebration tests.
- Pending: real Paper + RCON run showing round export, judge result publication, automatic
  REVEAL announcement, winner title/persona chat, winner-plot effect, and `/battle results`.
- Pending: forced RCON authentication/connection failure showing `results.txt` and
  `results.json` remain available and `/battle results` still prints the verdict.

## Retrospective

Pending implementation, live acceptance evidence, and review.

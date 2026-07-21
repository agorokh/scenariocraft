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
- [x] Implement with tests (2026-07-21).
- [x] Capture local acceptance evidence (2026-07-21); remote Paper smoke remains a merge gate.
- [x] Complete `/review` and repair the plan-only P1 finding (2026-07-21).
- [x] Record the retrospective (2026-07-21).

Update this list as work proceeds. Add timestamps when a checkpoint is useful to the next
session.

## Decision Log

| Date | Decision | Why |
| --- | --- | --- |
| 2026-07-20 | Keep `results.txt` as the durable plugin-facing contract and make the plugin the single owner of chat, title, and particle presentation. | The plugin must reprint results even when judging ran elsewhere, and a single formatter prevents RCON and polling from drifting or leaking raw JSON. |
| 2026-07-20 | Have the judge write both result files before attempting a narrow authenticated RCON `battle announce <round-id>` command. | Disk publication must survive network/authentication failure, while the command lets Bukkit resolve the active arena world and winner plot safely. |
| 2026-07-20 | Poll bounded result files off the server main thread, then marshal only Bukkit presentation work back to the main thread and deduplicate by round/result identity. | Filesystem I/O should not stall ticks, and RCON plus polling may observe the same result concurrently. |
| 2026-07-20 | Load RCON host, port, password, and timeouts from environment overrides or an optional external `judge.yml`, with no repository credential. | The issue requires both configuration surfaces and `code_review.md` forbids credentials in source, fixtures, logs, or history. |
| 2026-07-21 | Make the only RCON payload `battle announce <round-id>` and accept it only from console/RCON senders. | The judge should not own Bukkit formatting or world effects, and a narrow validated round identifier keeps the authenticated command surface small. |
| 2026-07-21 | Parse `results.txt` with fixed headers, bounded bytes/lines/fields, then format only parsed fields with JSON punctuation removed and per-line clamps. | Copied result files are an external input; strict parsing prevents raw JSON, failure details, control characters, or an unbounded line from reaching players. |
| 2026-07-21 | Carry the exact exporter-allocated round ID into the controller and require polling to load that directory, never the globally latest result. | A later REVEAL normally begins before its judge result exists; global latest discovery would replay the previous round before announcing the current one. |
| 2026-07-21 | Treat absent result-announcement keys as versioned defaults while validating any explicitly configured value. | `saveDefaultConfig()` must not make an existing server config unable to boot after an upgrade adds new optional settings. |
| 2026-07-21 | Run a one-tick phase observer that reads immediately when an active export ID appears, then honors the configured disk-read interval; require that interval not exceed REVEAL. | A repeating task scheduled only at the poll interval can miss an entire short phase depending on timer alignment. The per-tick work is an enum/ID check only; filesystem reads remain asynchronous and rate-limited. |
| 2026-07-21 | Keep only the newest 256 round paths in a bounded priority queue while scanning history, and require every parsed header to match its directory name. | Historical accumulation must not permanently disable replay, while copied/misplaced results must never impersonate the active round. |

## Surprises & Discoveries

- `RoundExportService` chooses the timestamped round ID internally and writes asynchronously;
  `RoundController` currently has no completion callback or result-watch seam. The announcer
  therefore needs a bounded rounds-directory discovery contract instead of assuming the
  controller already knows the final path.
- `results.txt` already contains contestant-to-plot identity, persona comments, means, and
  the winner, so the plugin can remain independent of `results.json`. Winner coordinates are
  still an active-round concern and must come from the controller's plot map rather than raw
  result JSON.
- Marking the plan-only head ready for review immediately produced a valid P1: `Closes #11`
  would have closed the issue without any behavior. The resolver therefore had to complete
  the implementation before it could resolve that thread.
- Paper exposes remote-console and local-console sender types separately. The hidden
  `battle announce` path accepts only those two types; players and command blocks cannot use
  the judge's announcement path.
- The first implementation review found two lifecycle edges that unit happy paths missed:
  “latest result” is not the same as “active round result,” and packaged config defaults do
  not guarantee upgrade safety for an already-written server config. Both became focused
  regression tests before the replacement head was pushed.
- The next current-head review found that “configured interval” and “bounded history” each
  needed lifecycle semantics, not just numeric caps: poll scheduling must align to phase
  entry, and the history cap must bound memory without turning normal retention into a
  permanent command outage.

## Acceptance evidence

- `JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home make ci-fast`
  passed on 2026-07-21 after merging current `main`: plugin 246 tests, judge 73 tests,
  renderer 15 tests, zero failures.
- `RconClientTest` exercises a real loopback TCP exchange: authentication packet, narrow
  `battle announce round-20260721-193000` command, and response framing.
- `BattleResultServiceTest` proves REVEAL polling announces a copied `results.txt` once and
  that the no-result command path gives a friendly player message. Parser/formatter tests
  enforce byte/line/field bounds, chat-line limits, kid-safe no-winner text, and no raw JSON
  punctuation. `BattleResultRepositoryTest` uses a round containing only copied
  `results.txt`, with no local manifest or JSON dependency.
- `BattleResultServiceTest.revealPollingDoesNotReplayThePreviousRoundsLatestResult` proves
  the poller stays silent while only the previous round has results, then announces exactly
  once when the exporter-selected active round appears. `ArenaConfigLoaderTest` removes all
  three new keys and verifies legacy configurations receive safe defaults.
- Repository regressions cover 257 retained round directories and reject a result whose
  `Round:` header does not match the requested directory. Poll/config regressions cover the
  immediate active-round check and reject an interval longer than REVEAL.
- `JudgeApplicationTest.rconFailureLeavesPublishedResultsAvailable` forces an announcement
  connection failure after judging and verifies both result files remain published while
  the judge returns success.
- The new-head GitHub Paper smoke job remains mandatory before merge and supplies the real
  plugin enable/boot evidence. A complete timed Build Battle with live OpenAI judging is an
  operator acceptance exercise because it requires players, an API credential, and server
  RCON configuration that are intentionally absent from the repository.

## Retrospective

The smallest safe boundary was not “send judge text over RCON”; it was “publish durable
results, send one authenticated round identifier, and let Bukkit own presentation.” That
kept disk polling and protocol work off the server thread, preserved Bedrock-safe chat/title
controls, and made RCON failure independent from judging success. The premature ready-for-
review transition was useful evidence that an ExecPlan is not delivery: review must compare
the actual diff with the issue before trusting checklist prose.

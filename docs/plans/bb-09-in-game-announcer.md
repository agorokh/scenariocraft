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
| 2026-07-21 | Apply the judge's cruel-language denylist again at the plugin file boundary and require RCON round IDs to match the active REVEAL export. | Copied files are untrusted even when structurally valid, and a delayed authenticated command for an older round must not announce or celebrate inside a newer round. |
| 2026-07-21 | Broaden copied-text rejection to self-harm/threat vocabulary, ignore `judge.yml` everywhere, and treat invalid optional RCON configuration as announcement failure rather than judging failure. | Player safety, credential containment, and durable publication must each fail independently at their own boundary. |
| 2026-07-21 | Reject common profanity, sexual-assault language, and Minecraft formatting markers at the copied-result boundary, and describe missing verdicts truthfully instead of inventing praise. | Untrusted judge artifacts must not gain presentation capabilities or expose children to abusive text, while a provider failure cannot be represented as feedback the judge never supplied. |
| 2026-07-21 | Bound completed-result candidates rather than round directories, recover the async read gate from unchecked filesystem failures, and enforce the judge's eight-plot/eight-persona presentation limits in the plugin parser. | Delayed judging and filesystem stream failures must not permanently disable replay, while copied artifacts must not amplify into an unbounded main-thread chat broadcast. |
| 2026-07-21 | Require copied feedback to name a concrete build feature and positive effect, render only that allowlisted feature in a fixed safe strength sentence, reject common identity slurs, isolate automatic polling from manual reads, and expose an export ID only after its directory is published successfully. | Arbitrary copied prose cannot be made safe by an ever-growing denylist; the last presentation boundary must preserve the genuine-strength signal without broadcasting the untrusted tail, while a slow command or timestamp collision must never suppress or misdirect the active round announcement. |
| 2026-07-21 | Gate published-ID discovery on the current controller export having started, and require exactly one terminal result record after all contestants. | REVEAL begins before wall removal/export, so the exporter can still expose the prior round during that gap; copied files also must not override contradictory winner state through record ordering. |
| 2026-07-21 | Accept the judge contract's full 512-character task length in copied results and clamp only at presentation. | A task that exports and judges successfully must not become unreadable solely because the plugin used a narrower trust-boundary length. |
| 2026-07-21 | Structurally validate raw copied comments, reduce them to the fixed allowlisted strength sentence, then language-check only the rendered sentence; treat player names as identifiers, skip corrupt latest-result candidates, and deduplicate announcements by parsed content rather than round ID. | Safe rewriting makes the raw tail non-presented data, legal player identifiers are not prose, one corrupt new artifact must not disable replay, and a corrected file for the active round must be able to replace an early version. |

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
- The plugin cannot inherit safety guarantees from the standalone judge: cross-machine
  `results.txt` and delayed network commands each cross a trust/lifecycle boundary and need
  independent validation immediately before presentation.
- “Optional announcement” applies to malformed configuration as well as network failure. A
  typo in an RCON port must not prevent the fallback artifact from being produced, and a
  credential-bearing file must be ignored mechanically rather than only by documentation.
- A general “cruel language” filter did not cover ordinary profanity, sexual-assault
  language, or Minecraft's section-sign formatting codes. These need explicit validation
  at the last player-facing trust boundary, and an all-failed verdict set needs an honest
  fallback rather than synthetic encouragement.
- A directory-count cap is not a completed-result cap: 256 newer unjudged exports can hide
  an older usable result. Also, `Files.list` can surface iteration failures as unchecked
  exceptions, so the off-thread read gate must recover from both checked and runtime
  filesystem paths.
- One shared asynchronous gate made a harmless manual replay capable of consuming a short
  REVEAL's only polling window. Timestamp-derived IDs also cannot be treated as published
  until the atomic directory write succeeds, because clocks can move backwards into an
  existing round name.
- Structural and word-level safety are not enough for copied feedback: a bland or purely
  corrective sentence can still violate the project's genuine-strength rule. The plugin
  therefore applies the same concrete-feature plus positive-effect semantic floor before
  any copied comment reaches chat.
- A positive prefix does not make the rest of copied prose safe. The player-facing form now
  extracts only the matched allowlisted build feature and emits a fixed strength sentence;
  this removes arbitrary trailing prose instead of trying to enumerate every abusive word.
- “Published ID” still needs a current-round generation boundary: early REVEAL polling runs
  before export begins and can otherwise observe the previous successful publication. The
  controller now refuses exporter IDs until its own current export call has succeeded.
- Line-level result parsing needs ordering semantics as well as syntax. A winner or no-winner
  line is accepted exactly once, after contestants, and no nonblank content may follow it.
- Input safety limits must align across producer and consumer. The judge permits 512-character
  tasks, so the plugin parser accepts the same bound while its chat/title formatter remains
  responsible for the shorter display limits.
- Applying a prose denylist before discarding copied prose can reject judge-valid results
  without improving player safety. Safety checks now follow data flow: structural checks on
  raw input, allowlisted reduction, then language validation on the sentence actually shown.
  Player names follow the structural identifier path instead of prose policy.
- Replay resilience is per candidate, not only per directory scan: a malformed newest result
  is skipped so an older valid result remains available. Announcement idempotence is based on
  the parsed result value, allowing a corrected same-round artifact to announce once more.

## Acceptance evidence

- `JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home make ci-fast`
  passed on 2026-07-21 after merging current `main`: plugin 262 tests, judge 74 tests,
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
- Parser and service regressions reject cruel copied feedback and prevent a delayed RCON
  command for an older round from broadcasting or targeting the current winner plot.
- Additional regressions reject self-harm language and verify malformed optional RCON
  settings still reach the durable judging attempt. Root `.gitignore` rejects `judge.yml`
  regardless of the configured content-directory depth.
- Copied-result regressions also reject common profanity, sexual-assault language, and
  section-sign formatting codes; when every persona verdict fails, the formatter reports
  that feedback could not be completed and uses a neutral no-winner status instead of
  fabricating praise.
- Replay regressions keep an older completed result visible behind 257 unjudged exports,
  async-read regression proves an unchecked filesystem failure releases the next request,
  and the parser rejects feedback beyond the eight-persona judging limit.
- Boundary regressions reject slurs and feedback without a concrete positive build effect,
  reject the reported abusive-tail case, and prove unknown copied tail prose is not retained
  in the player-facing summary. An intentionally queued manual read no longer prevents an
  active-round poll. Export tests expose no current round ID before publication and keep it
  empty after a directory collision fails.
- Controller regression hides a retained prior ID during early REVEAL and discovers only the
  new publication after export starts; parser regressions reject pre-contestant,
  contradictory, repeated, or nonterminal outcome records.
- Task-bound regression accepts 512 characters, rejects 513, and verifies the accepted task
  is still clamped to the chat presentation limit.
- Current-head configured-reviewer regressions prove denylisted raw tails are discarded before
  rendering, legal player identifiers remain readable, a malformed newest result falls back
  to an older valid result, and corrected same-round content announces after the early result.
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

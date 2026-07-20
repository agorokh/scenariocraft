# ExecPlan: BB-03 round phase machine and countdown

Issue: #5
Owner: Codex
Status: Complete

## Purpose

Deliver a complete timed Build Battle round around the arena primitives from BB-02:
authorized start and stop commands, explicit legal phase transitions, contestant plot
assignment, phase-aware reconnects, chat/title/bossbar feedback, and batched reset and
reveal work. This needs an ExecPlan because it coordinates Paper lifecycle, online-player
state, scheduled timers, cancellable arena work, and real-server acceptance evidence.

## Progress

- [x] Define the smallest end-to-end slice.
- [x] Implement with tests.
- [x] Capture the issue's acceptance evidence.
- [x] Complete `/review` and resolve P1 findings.
- [x] Record the retrospective.

Update this list as work proceeds. Add timestamps when a checkpoint is useful to the next
session.

## Decision Log

| Date | Decision | Why |
| --- | --- | --- |
| 2026-07-19 | Keep the phase graph and countdown arithmetic in Paper-independent types, with the runtime controller translating their decisions into Bukkit effects. | The issue explicitly requires transition-table and timer-arithmetic unit tests, and pure logic makes every legal and illegal edge deterministic. |
| 2026-07-19 | Treat `PREPARING` as asynchronous arena reset completion rather than a wall-clock phase, then drive all timed phases from one scheduler tick. | Arena edits remain behind BB-02's block budget, while the controller has one cancellable source of phase and warning events. |
| 2026-07-19 | Snapshot each non-exempt online player's pre-round game mode, assign plots in stable case-insensitive player-name order, and retain assignments for reconnect handling. | The issue requires participants to return to the hub, while deterministic assignment and retained state make stop and reconnect behavior predictable without inventing a broader persistence system. |
| 2026-07-19 | Reuse the arena editor for reveal by adding a pure, batched wall-removal plan rather than directly clearing walls. | The standing main-thread rule applies to wall dissolution just as it does to arena reset. |
| 2026-07-19 | Use the first configured task as the temporary hardcoded round task. | BB-03 requires a hardcoded task string and explicitly excludes the task chest/deck interaction owned by BB-04. |
| 2026-07-19 | Restore contestants during `PlayerQuitEvent`, while retaining their assignment until the round ends. | Paper can persist the safe hub/game-mode state before a disconnect or shutdown completes, and a player who rejoins during the same round can still receive the current phase state. |
| 2026-07-19 | Snapshot non-contestant spectators when reveal moves them to the tour point, then restore their original location and game mode. | BB-03 requires all players to join the reveal tour, but unrelated exempt helpers and late joiners must not be stranded by the round lifecycle. |
| 2026-07-19 | Limit periodic short-countdown chat to `GATHERING` and `NOTE_PICK`. | The configurable reveal linger can be much longer, so ten-second announcements there would create avoidable chat spam. |
| 2026-07-19 | Close open views, snapshot contestant inventory and ender-chest contents, vacate those items for the round, clear Creative-sourced items before reveal, and restore the snapshot only when the contestant leaves the round. | Keeping the snapshot separate for the entire round prevents both Creative-item leakage and drop-then-restore duplication. |
| 2026-07-19 | Persist the protected inventory snapshot in the player's Paper persistent-data container before vacating items, and replay pending state during controller initialization or join. | An in-memory-only snapshot would be lost on JVM/server failure after an empty round inventory had already been saved to player data. |
| 2026-07-19 | Keep failed/cross-version snapshot bytes for human recovery, stage decoded contents before replacing live state, and continue active-phase lockdown when a reconnect save fails. | Recovery must never discard the only protected copy or leave a still-enrolled contestant holding their restored normal-play inventory. |

## Surprises & Discoveries

- The delivery worktree began detached at the already-current `origin/main`; after fetching
  and verifying the SHA, the issue branch could be created directly without carrying
  unrelated worktree state.
- GitHub's HTTPS credential reported repository admin access through the API but rejected
  Git pushes and PR creation with HTTP 403. The already-authorized SSH key published the
  branch, and the connected GitHub app created the exact verified draft PR.
- The desktop shell did not expose its installed Java runtime through `JAVA_HOME`; local
  Gradle gates use the repository-required Homebrew Java 21 installation explicitly.
- `/review` found no P1 but caught that "debug fill" was developer language in a
  player-facing message and that a reconnect during `NOTE_PICK` did not replay the current
  task title. Both paths now use kid-friendly feedback and have reconnect regression
  coverage.
- External current-head review found that offline contestants could retain a round game
  mode, non-contestant reveal spectators were not restored, and the long reveal linger
  reused short-phase countdown announcements. Resolution adds lifecycle and countdown
  regression coverage. A separate suggestion to let configured non-operators stop a round
  was rejected because issue #5 explicitly requires `/battle stop` to be admin-only.
- Follow-up current-head review caught that restored reveal spectators still kept an
  interactive game mode during the tour. Spectators now use Adventure mode until their
  snapshotted state is restored. The same pass exposed stale plan wording about contestant
  locations and assignment order; the Decision Log now matches the issue and implementation.
- Final current-head review caught that restoring only game mode could preserve
  Creative-sourced items and that a non-player starter would learn about arena failure only
  through the server log. Contestant inventory state is now restored with game mode, and
  console/RCON starters receive the same kid-appropriate failure message as players.
- Inventory follow-up review found that snapshot-and-later-restore was insufficient while
  the original items remained usable during early phases, and that Creative items survived
  into reveal. Contestants are now moved to the hub and vacated immediately after the
  snapshot; cursor, open-view, inventory, and ender-chest state are cleared at every
  round-controlled boundary and the snapshot is restored only on round exit.
- Crash-recovery review found that vacating a live inventory requires the protected copy to
  survive the controller and JVM. The snapshot now persists before any clear, replays on
  enable/join, includes cursor state, blocks contestant drops during the active round, and
  is removed only after a successful restore and player-data save.
- Recovery-edge review found that a reconnect save failure could bypass lockdown and that
  pickups needed the same guard as drops. Reconnects now re-enter the phase from the
  in-memory protected copy even when the redundant save fails, active contestants cannot
  pick up items, restore stages cloned contents before applying them, and synchronous arena
  enqueue exceptions have a focused abort-and-restore regression.

## Acceptance evidence

- `make ci-fast` completed successfully with 45 tests. The focused coverage includes every
  legal and illegal phase pair, countdown arithmetic and warning boundaries, command
  authorization, one-contestant debug-fill cycling, reconnect state restoration, clean
  stop from every active phase, batched wall removal, and editor cancellation.
- Pinned Paper `1.21.11` build `132` enabled the final implementation with Java 21. A real
  offline-mode protocol client joined as `BuilderKid`, ran `/battle start`, and completed
  the two-plot round as one connected player plus one debug plot.
- The client received the hardcoded `A dragon treehouse` task, gathering and note
  countdown chat, seven bossbar protocol packets during `BUILDING`, reveal chat, and
  `Build Battle complete — amazing creating, everyone!`.
- Paper logged the complete transition chain:
  `IDLE -> PREPARING -> GATHERING -> NOTE_PICK -> BUILDING -> REVEAL -> IDLE`.
- Paper queued and completed `1288` reset mutations and `320` reveal mutations at the
  configured `4000` blocks per tick.
- Separate real-server commands logged clean returns to `IDLE` when stopped from
  `PREPARING`, `GATHERING`, `NOTE_PICK`, `BUILDING`, and `REVEAL`.
- The acceptance log contained no watchdog, arena mutation failure, plugin enable failure,
  or plugin disable failure signature and ended with `All dimensions are saved`.
- GitHub's build and pinned real-Paper smoke jobs passed on the pushed implementation head.

## Retrospective

BB-03 now turns the BB-02 arena primitives into a playable, timer-driven round without
pulling later chest, anti-peek, or judging work forward. Separating the phase graph and
timer arithmetic from Paper effects made the required transition table precise, while one
runtime controller kept reconnect, command, bossbar, teleport, and restoration behavior
coherent. Reusing the existing editor for reveal work also preserved the main-thread block
budget instead of introducing a second mutation path. The real protocol client was useful
beyond a boot smoke: it proved the one-player-plus-practice-plot path and observed actual
bossbar packets, chat countdowns, task feedback, and completion.

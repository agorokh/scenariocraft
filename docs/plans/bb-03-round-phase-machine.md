# ExecPlan: BB-03 round phase machine and countdown

Issue: #5
Owner: Codex
Status: In progress

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
- [ ] Record the retrospective.

Update this list as work proceeds. Add timestamps when a checkpoint is useful to the next
session.

## Decision Log

| Date | Decision | Why |
| --- | --- | --- |
| 2026-07-19 | Keep the phase graph and countdown arithmetic in Paper-independent types, with the runtime controller translating their decisions into Bukkit effects. | The issue explicitly requires transition-table and timer-arithmetic unit tests, and pure logic makes every legal and illegal edge deterministic. |
| 2026-07-19 | Treat `PREPARING` as asynchronous arena reset completion rather than a wall-clock phase, then drive all timed phases from one scheduler tick. | Arena edits remain behind BB-02's block budget, while the controller has one cancellable source of phase and warning events. |
| 2026-07-19 | Snapshot each non-exempt online player's pre-round location and game mode, assign plots in stable join order, and retain assignments for reconnect handling. | Stop can restore participants cleanly, and reconnect behavior can be selected from the current phase without inventing a broader persistence system. |
| 2026-07-19 | Reuse the arena editor for reveal by adding a pure, batched wall-removal plan rather than directly clearing walls. | The standing main-thread rule applies to wall dissolution just as it does to arena reset. |
| 2026-07-19 | Use the first configured task as the temporary hardcoded round task. | BB-03 requires a hardcoded task string and explicitly excludes the task chest/deck interaction owned by BB-04. |

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

## Acceptance evidence

- `make ci-fast` completed successfully with 37 tests. The focused coverage includes every
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

## Retrospective

Pending completion.

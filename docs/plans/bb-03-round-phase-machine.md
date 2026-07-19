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
- [ ] Implement with tests.
- [ ] Capture the issue's acceptance evidence.
- [ ] Complete `/review` and resolve P1 findings.
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

## Acceptance evidence

Pending implementation, focused tests, local CI, and a real Paper server cycle.

## Retrospective

Pending completion.

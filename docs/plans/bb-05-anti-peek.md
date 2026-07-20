# ExecPlan: BB-05 anti-peek and protected plots

Issue: #7
Owner: Codex
Status: In progress

## Purpose

Keep every contestant inside their own hidden build space during BUILDING, prevent block
edits outside the contestant's plot in every phase, and make every round teleport explicit
about its destination world. This needs an ExecPlan because it coordinates per-player
world-border lifecycle, vertical build limits, Paper block events, phase transitions,
reconnect and stop cleanup, teleport command dispatch, pure geometry and permission logic,
and real-server acceptance evidence.

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
| 2026-07-20 | Model border geometry and edit permission as small Paper-independent values before wiring listeners into `RoundController`. | The issue requires deterministic unit coverage for each plot and phase, while Paper integration should only translate player, block, and phase state into those pure decisions. |
| 2026-07-20 | Apply a personal border only during BUILDING and restore the arena world's default border before entering REVEAL, on stop, disconnect, close, and failed-round cleanup. | The border is a temporary anti-peek control; centralized cleanup prevents a contestant from retaining it after any exit path. |
| 2026-07-20 | Enforce the Y-cap through block placement permission and the per-player border through Paper's client-enforced world-border API, without a `PlayerMoveEvent`. | Paper world borders constrain horizontal visibility and reach, while cancelling edits above the wall is server-authoritative and avoids Bedrock-hostile movement rubberbanding. |
| 2026-07-20 | Route controller-owned teleports through console-dispatched `execute in <dimension> run tp` commands. | This matches the issue contract and avoids player-context command aliases or implicit-world ambiguity. |

## Surprises & Discoveries

- The delivery worktree began detached at the current `origin/main`. Local `main` was one
  merge behind, so it was fast-forwarded before creating the issue branch.

## Acceptance evidence

- Pending: `make ci-fast` output and focused unit-test results.
- Pending: source scan proving no `PlayerMoveEvent` handler exists.
- Pending: Paper-server evidence for BUILDING border/Y-cap behavior and restoration on
  REVEAL and `/battle stop`.

## Retrospective

Pending implementation and verification.

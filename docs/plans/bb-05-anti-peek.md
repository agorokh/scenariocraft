# ExecPlan: BB-05 anti-peek and protected plots

Issue: #7
Owner: Codex
Status: Complete

## Purpose

Keep every contestant inside their own hidden build space during BUILDING, prevent block
edits outside the contestant's plot in every phase, and make every round teleport explicit
about its destination world. This needs an ExecPlan because it coordinates per-player
world-border lifecycle, vertical build limits, Paper block events, phase transitions,
reconnect and stop cleanup, teleport command dispatch, pure geometry and permission logic,
and real-server acceptance evidence.

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
| 2026-07-20 | Model border geometry and edit permission as small Paper-independent values before wiring listeners into `RoundController`. | The issue requires deterministic unit coverage for each plot and phase, while Paper integration should only translate player, block, and phase state into those pure decisions. |
| 2026-07-20 | Apply a personal border only during BUILDING and restore the arena world's default border before entering REVEAL, on stop, disconnect, close, and failed-round cleanup. | The border is a temporary anti-peek control; centralized cleanup prevents a contestant from retaining it after any exit path. |
| 2026-07-20 | Enforce the Y-cap through block placement permission and the per-player border through Paper's client-enforced world-border API, without a `PlayerMoveEvent`. | Paper world borders constrain horizontal visibility and reach, while cancelling edits above the wall is server-authoritative and avoids Bedrock-hostile movement rubberbanding. |
| 2026-07-20 | Route controller-owned teleports through console-dispatched `execute in <dimension> run tp` commands. | This matches the issue contract and avoids player-context command aliases or implicit-world ambiguity. |
| 2026-07-20 | Build the invisible barrier cap and remove it through the existing per-tick arena queue. | The cap must be a real vertical anti-peek boundary, but it must preserve the repository rule that arena block work is always budgeted. |
| 2026-07-20 | Cancel active-arena explosions and pistons, validate every replaced block in multi-place events, and contain bucket/fluid changes within one editable plot. | `/review` exposed indirect mutations as a P1 bypass; covering the related event surfaces keeps the plot policy server-authoritative instead of relying on only ordinary place/break events. |
| 2026-07-20 | Target console teleports by player UUID and roll back the personal border when dispatch fails. | UUIDs are safe Brigadier entity arguments, and a failed plot move must not strand a player outside a plot-sized client border. |
| 2026-07-20 | Cancel dispensers throughout an active arena and apply the plot/phase policy to hanging and entity placements. | Dispenser effects and entity-backed decorations otherwise bypass ordinary block place events and can cross the assigned volume. |

## Surprises & Discoveries

- The delivery worktree began detached at the current `origin/main`. Local `main` was one
  merge behind, so it was fast-forwarded before creating the issue branch.
- Initial unit failures were stale mutation-count expectations: the barrier cap intentionally
  adds one plane to arena setup and reveal. The expected totals were updated only after
  checking the new fill geometry.
- A cancelled client break can temporarily look successful to the protocol client. The
  real-server smoke test therefore verified the authoritative server block with
  `execute if block`, proving the outside wall remained intact.
- `/review` found that explosions and pistons could bypass the direct place/break policy.
  Those paths, multi-place events, buckets, and cross-boundary fluid flow now have regression
  coverage.
- External review correctly identified console target hardening, failed-teleport border
  rollback, and entity-backed placement gaps. Its direct `Player.teleport` fallback conflicted
  with the issue's explicit console-only contract, so the failure remains visible and safe
  instead of silently changing transport mechanisms.
- The reported vertical look-over gap was a coordinate misunderstanding: a wall block at
  `maxBuildY` occupies space through `maxBuildY + 1`, exactly where the barrier roof begins.
  The existing local-server evidence therefore remains valid without changing build height.

## Acceptance evidence

- `make ci-fast` passed on Java 21 with 71 tests.
- Source scans found no `PlayerMoveEvent` and no direct `Player.teleport` call in production
  code; controller teleports are explicit `execute in minecraft:battle_world run tp ...`
  console commands.
- A real Paper 1.21.11 build 132 server booted the final jar with two protocol clients.
  BuilderOne and BuilderTwo each received a diameter-9 personal border centered on their
  separate plots during BUILDING. Both clients observed the border packets.
- The smoke test verified a barrier cap at Y=-55, allowed place/break within the assigned
  plot, rejected an outside wall break using server-authoritative block state, and removed
  the cap during REVEAL.
- Both clients observed the world-default diameter 59,999,968 border after `/battle stop`
  and again after REVEAL. The server then stopped cleanly with no ScenarioCraft errors.
- Arena setup and reveal remained budgeted: 2,015 setup mutations and 562 reveal mutations
  at 1,000 blocks per tick in the smoke configuration.

## Retrospective

The smallest useful design was a pure `PlotBoundary` plus `PlotEditPolicy`, leaving Paper
listeners responsible only for translating events into deterministic policy inputs. The
same boundary now drives personal border geometry, vertical edit permission, the barrier
cap, and fluid containment, which keeps those controls aligned. Real protocol clients were
necessary evidence here: unit tests proved the calculations and cleanup calls, while the
server run proved Paper actually sent separate borders, retained cancelled blocks, removed
the cap, and restored defaults across both exit paths.

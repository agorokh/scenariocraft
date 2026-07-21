# ExecPlan: contestant relocation and recovery lifecycle

Issue: #24
Owner: Codex
Status: In progress

## Purpose

Keep contestants safely contained through death, reconnect, command-registry changes,
teleport confirmation, controller shutdown, and player-data persistence failures. This needs
an ExecPlan because the change coordinates Paper lifecycle events, exact console-command
transport, durable plugin-owned recovery state, scheduled task ownership, personal borders,
operator alerts, and real-server smoke evidence.

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
| 2026-07-20 | Put exact namespaced-command readiness and dispatch behind one small teleport transport boundary. | The production command shape and the health check must not drift, and availability must be checked before round start and each relocation rather than only at plugin enable. |
| 2026-07-20 | Add a plugin-owned UUID recovery store that atomically replaces its on-disk registry and is injectable in tests. | A `Player.saveData()` failure can make the player-data marker volatile; restart recovery therefore needs independent durable state without adding unrelated orchestration state. |
| 2026-07-20 | Model each teleport attempt as owned state with its scheduled retry and confirmation tasks. | Superseded and closing attempts need deterministic cancellation and exactly-once completion semantics instead of leaving anonymous delayed tasks to observe stale maps. |
| 2026-07-20 | Treat BUILDING respawn as a guarded plot re-entry and apply Creative mode, bossbar, and a plot border only after authoritative arrival. | A remote plot border must never be applied while the player is elsewhere, but a successful respawn must restore all phase controls and containment. |
| 2026-07-20 | Preserve the current plot border during plot-to-safe-destination confirmation and keep pending recovery protected in IDLE. | Containment must cover the full dispatch window and must not disappear merely because round state was cleared before hub arrival was confirmed and persisted. |
| 2026-07-20 | Exercise the production `minecraft:execute ... run minecraft:tp` path in the pinned real-Paper smoke job. | Plugin enable and unit command-map checks do not prove that the exact console dispatch path parses and executes on the supported Paper build. |

## Surprises & Discoveries

- BB-05 already added delayed 1, 5, and 20 tick confirmation, a player-data recovery marker,
  console and permission alerts, and in-memory attempt IDs. The missing layer is lifecycle
  ownership and durability: scheduled tasks are anonymous, command readiness is enable-only,
  and a failed `saveData()` can leave no restart-discoverable recovery record.
- The existing success path clears the player-data recovery marker on every confirmed
  teleport, including plot entry, rather than only after authoritative hub recovery. The
  lifecycle boundary must distinguish safe hub extraction from ordinary controller movement.

## Acceptance evidence

- Pending: focused unit tests for BUILDING respawn success and unusable-plot containment.
- Pending: recovery-store restart tests with simulated player-data save and clear failures.
- Pending: runtime loss tests for the exact namespaced command path at round start,
  relocation, and retry.
- Pending: supervised-task tests for supersede, close, and exactly-once terminal callbacks.
- Pending: containment tests for border timing and pending-recovery teleport restrictions in
  IDLE.
- Pending: `make ci-fast`, `./gradlew build`, and `./gradlew test` on Java 21.
- Pending: real-Paper smoke assertion for the production namespaced teleport command path.
- Pending: `/review` against `code_review.md` and complete PR feedback audit.

## Retrospective

Pending implementation, verification, external review, and merge.

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
- [x] Implement with tests.
- [x] Capture the issue's acceptance evidence.
- [x] Complete `/review` and resolve P1 findings.
- [x] Record the retrospective.

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
| 2026-07-20 | Prioritize pending durable recovery before active-round re-entry on join. | A contestant who disconnected during a failed relocation must reach the hub before the controller may reapply the current phase. |
| 2026-07-20 | Settle an already-arrived teleport during close without running its success callback. | A move can arrive after dispatch but before its delayed confirmation, but callback side effects such as entering BUILDING are no longer valid once shutdown begins; normal round restoration owns the remaining hub cleanup. |
| 2026-07-20 | Isolate the real-Paper teleport probe from generated terrain and document inspection without a force-clear control. | The smoke should test the command path rather than block geometry, while manual registry deletion would violate the requirement to clear only after authoritative hub arrival and player-data persistence. |
| 2026-07-20 | Keep round-start transport checks side-effect free and supervise an externally requested hub rescue as an owned delayed attempt. | Command registration is enough to fail a round before mutation, while every real relocation validates and dispatches the exact command; a pending player may reach only the hub and must still pass the normal confirmation and persistence boundary. |
| 2026-07-20 | Resume an active contestant's phase only after recovery reaches the hub and durably clears. | Phase timers may continue while a player is pending; every phase move must route through hub recovery first, then apply the current phase so a BUILDING rejoin is not left uncontained at the hub. |
| 2026-07-20 | During close, run only an already-arrived hub recovery's owned success callback; otherwise settle without phase side effects. | Durable recovery cleanup and pending inventory restoration are safe and required at the hub, while a plot-arrival callback could incorrectly enter BUILDING during shutdown. Delayed dispatch also fails before command execution when its player is offline. |

## Surprises & Discoveries

- BB-05 already added delayed 1, 5, and 20 tick confirmation, a player-data recovery marker,
  console and permission alerts, and in-memory attempt IDs. The missing layer is lifecycle
  ownership and durability: scheduled tasks are anonymous, command readiness is enable-only,
  and a failed `saveData()` can leave no restart-discoverable recovery record.
- The existing success path clears the player-data recovery marker on every confirmed
  teleport, including plot entry, rather than only after authoritative hub recovery. The
  lifecycle boundary must distinguish safe hub extraction from ordinary controller movement.
- The first local gate could not locate Java through the shell. The repository-required Java
  21 runtime was already installed at
  `/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home`; binding `JAVA_HOME` to
  that installation made the baseline and final gates reproducible without changing the
  repository.
- Independent review found that active-contestant join handling originally ran before pending
  recovery, unusable-respawn hub success did not clear the old plot border, and close could
  misclassify an arrival that settled before its confirmation task. All three paths now have
  focused regression tests.
- The first pushed smoke extension used single-quoted command literals inside the workflow's
  existing single-quoted `bash -c` program. Bash therefore ended the inner program early and
  treated `minecraft:battle_world` as its working-directory argument. Double-quoted command
  literals preserve the wrapper boundary; the extracted workflow program passes `bash -n` and
  the complete local Paper smoke.
- Current-head external review found that the smoke marker could depend on generated terrain
  at Y=100. The probe now clears a bounded air pocket and gives the marker no gravity before
  dispatch; the operator runbook also names the durable registry and makes its safe automatic
  clear boundary explicit.
- A second current-head review correctly identified shutdown callback and preflight side
  effects, plus the no-attempt hub-rescue gap. It also reported three inapplicable findings:
  the workflow opens fd 3 before the wait loop, the constructor already retries online
  persisted players, and a non-atomic move fallback would contradict the issue's durability
  requirement. The applicable paths now have focused regression tests; the inapplicable ones
  are documented and receive visible evidence replies.
- Subsequent current-head review found two more ordering gaps: an already-arrived delayed
  attempt was checked against command availability before settlement, and active phase moves
  could race ahead of durable hub recovery. Arrival now settles first, phase moves recover at
  the hub before re-entry, and synchronous recovery no longer forces a redundant second start.
  Enable-time malformed-registry recovery is also explicit in the operator runbook.
- A current-head review tightened the last asynchronous boundaries. The Paper probe now polls
  and retries its complete marker sequence for up to 30 seconds before starting the battle;
  controller close completes an already-arrived hub recovery without running unsafe plot
  callbacks; offline delayed dispatch fails before sending a command; and enable-time registry
  failure logs the durable recovery marker plus the runbook path before failing closed.

## Acceptance evidence

- `RoundControllerTest` covers BUILDING respawn success, unusable-plot hub fallback,
  active-contestant recovery-first rejoin, border timing, runtime command loss, IDLE movement
  restriction, supersede cancellation, close failure settlement, and close-after-arrival
  success settlement.
- `TeleportRecoveryStoreTest` covers atomic add/remove across fresh store instances, malformed
  input fail-closed behavior, and failed replacement without in-memory corruption. The
  controller integration test combines a simulated `Player.saveData()` failure with an
  on-disk store reopen and successful hub retry.
- `TeleportTransportTest` pins the exact namespaced, explicit-world, UUID-targeted production
  command including coordinates and rotation.
- `make ci-fast` passed on Java 21 with 122 plugin tests and 13 renderer tests, all green.
- A local Paper 1.21.11 build 132 smoke run enabled ScenarioCraft, executed
  `minecraft:execute in minecraft:battle_world run minecraft:tp` against a marker entity,
  confirmed its authoritative destination, and shut down with all dimensions saved. The run
  used the exact YAML-extracted smoke program with only the port changed because another local
  Paper server already owned the workflow default of 25565.
- Production source scans found no direct `Player.teleport`, `teleportAsync`, or
  `PlayerMoveEvent`; console dispatch is isolated in `TeleportTransport`.
- Independent `/review` against `code_review.md` found no P1 and no remaining actionable
  findings after three lifecycle fixes were added and retested.

## Retrospective

The relocation code is now split at two narrow ownership boundaries: command construction and
dispatch live in `TeleportTransport`, while durable pending UUIDs live in
`TeleportRecoveryStore`. `RoundController` retains phase decisions and supervises every retry
and confirmation task. The most important design correction was to make hub arrival a distinct
terminal event: generic teleport success no longer clears recovery, and borders, Adventure
mode, player data, and the atomic registry transition only in the order justified by an
authoritative location. Local verification and internal review are complete; current-head
external review, CI, and merge remain delivery steps.

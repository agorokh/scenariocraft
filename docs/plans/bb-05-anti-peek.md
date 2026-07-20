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
| 2026-07-20 | Deny non-contestant edits and cancel fire spread, burn, and ignition throughout an active arena. | Bystanders and indirect fire events do not pass through ordinary contestant place/break policy, but they can still damage walls, caps, and builds. |
| 2026-07-20 | Namespace vanilla `execute`/`tp`, emit plain decimal arguments, and verify the player's resulting location after every dispatch. | Command acceptance alone does not prove that an entity matched or moved; every caller now receives a real success/failure result without falling back to the forbidden direct teleport API. |
| 2026-07-20 | Attempt hub extraction after inventory restoration regardless of decode, write, or save failure. | Physical round cleanup is a separate invariant from item recovery and must not be skipped by an inventory exception. |
| 2026-07-20 | Keep the personal border cleared after a failed round-exit hub teleport, while retaining it for a failed in-round REVEAL move. | Round teardown removes the privacy requirement and must not leave a player permanently constrained after contestant state is cleared. |
| 2026-07-20 | Cancel active-arena entity block changes and null-player hanging/entity placements. | Automated or entity-driven mutations do not have a contestant identity to evaluate, so they must be denied throughout an active arena. |
| 2026-07-20 | Extend each barrier roof across the outer wall footprint and remove that full footprint during REVEAL. | Covering the wall ring closes the last overhead line around the plot perimeter without changing the contestant's editable volume. |
| 2026-07-20 | Track contestants whose round-exit teleport fails and continue denying their arena edits without restoring a personal border. | An IDLE phase must not turn a failed extraction into permission to edit the arena, while teardown must still release the client-side privacy constraint. |
| 2026-07-20 | Apply plot permission to non-secret right-click block interactions and notify every online operator when a controller teleport fails. | Tool transformations can mutate blocks without a place event, and an operator-visible alert makes manual recovery actionable immediately. |
| 2026-07-20 | Deny use of a protected clicked block while allowing the held item to act on an editable face-relative target. | First-block placement, buckets, redstone, and entity attachments commonly click the protected plot floor or wall; Bukkit's split interaction result blocks tool transformations without suppressing their dedicated placement events. |
| 2026-07-20 | Confirm handled-but-not-yet-observed console teleports after 1, 5, and 20 total ticks before applying failure containment. | Cross-chunk and player lifecycle edges can defer the authoritative location update; callers now receive a bounded success/failure result without misclassifying ordinary slow loads. |
| 2026-07-20 | Freeze each contestant's `PlotBoundary` at round preparation and clear failed-exit containment on confirmed controller moves, close, and new participation. | Logical edit and border geometry must stay aligned with the already-built walls, and recovery state must not leak into another world or round. An unconfirmed disconnect retains containment until rejoin retries the hub return. |
| 2026-07-20 | Mark round exits contained before dispatch, and keep plot entrants in Adventure without a personal border until the destination is confirmed. | Deferred verification must not create an IDLE edit window or apply a distant plot-centered border to a player who remains at the hub. Failed plot entry aborts the round safely. |
| 2026-07-20 | Apply the plot policy to fertilization and structure growth. | Bone meal and trees can change multiple blocks beyond the interacted block, so every resulting block must stay inside the assigned editable volume. |
| 2026-07-20 | Persist an unconfirmed-exit recovery marker in player data until a hub arrival is authoritative, and guard physical block interactions. | Reload must not erase containment after inventory restoration, and spectator farmland trampling must follow the same active-arena policy as other mutations. |
| 2026-07-20 | Run mutation guards at `HIGHEST` while ignoring already-cancelled events, require vanilla teleport commands at startup, and keep adjacent-item use separate from clicked-block use. | ScenarioCraft should be final among normal protection listeners without reviving their denials, fail before a live round if its transport commands are missing, and allow buckets/redstone/attachments into editable space without permitting tool transforms on the protected floor or wall. |
| 2026-07-20 | Keep NOTE_PICK as the holding phase until every online contestant's plot arrival is confirmed; allow player ignition and gravity settlement only inside an editable plot. | Build time and Creative controls must not begin on dispatch alone, while valid fire, portal, sand, gravel, concrete-powder, and anvil builds should work without opening out-of-plot mutation paths. |
| 2026-07-20 | Retry a rejected console dispatch once and retry saving an existing recovery marker on every recovery attempt. | A transient command-registry edge should not abort immediately, and a player-data save failure must remain operator-visible and get another durability attempt on rejoin/recovery. |
| 2026-07-20 | Remove disconnecting players from the plot-entry wait set and route NOTE_PICK rejoins back to their plot while confirmations are pending. | A superseded attempt must not leave a stale UUID that softlocks BUILDING, and a returning contestant must not be confirmed at the hub. |
| 2026-07-20 | Allow only same-plot `FallingBlock` settlement, reject contestant teleports outside their boundary during BUILDING, and cancel active-arena block formation. | Gravity builds need their source and target validated together; chorus fruit and similar server teleports can cross solid walls; Frost Walker, weather, and freezing must not bypass the mutation policy. |
| 2026-07-20 | Extend teleport containment through the NOTE_PICK plot-entry wait; allow plot-local passive block formation and contestant-owned entity formation; document failed recovery-marker saves as volatile. | A confirmed early arrival must remain contained while peers are still moving, concrete powder should remain usable, and operators must know that a failed player-data save cannot survive restart. |
| 2026-07-20 | Cancel leaf decay throughout the active arena. | Vanilla leaf decay is a distinct mutation event and must not erase contestant builds or bypass arena containment. |
| 2026-07-20 | Cancel block fading throughout the active arena. | Melting ice or snow and drying farmland use a distinct fade event and must not erase contestant builds during a round. |
| 2026-07-20 | Require the exact namespaced transport commands, contain failed BUILDING rejoins without aborting the round, preserve the plot border until reveal arrival, and apply stranded-player edit guards in IDLE. | The startup probe must match dispatch, one reconnect must not stop every builder, deferred reveal must not open a peek window, and containment must outlive the round phase. |
| 2026-07-20 | Return immediately after starting pending-inventory recovery on join, and protect hanging/decorative entity interactions with the plot policy. | A REVEAL join must not supersede its recovery teleport, and item frames, paintings, and armor stands are part of a build even though they are not blocks. |
| 2026-07-20 | Reject non-contestant teleports into occupied private plots and apply the plot policy to armor-stand damage, including player-fired projectiles. | Edit denial alone does not prevent peeking, and left-click damage is distinct from decorative-entity interaction. |
| 2026-07-20 | Treat null entity-placement faces as the event block, delay Creative until every plot arrival confirms, persist every stranded path and recovery failure, and send alerts to console plus `scenariocraft.alerts`. | Nullable Paper event fields must not bypass policy, partial arrival must not grant early controls, restart recovery needs a marker from every containment path, and an alert must have a durable/operator-visible recipient. |
| 2026-07-20 | Keep close-created restore attempts live through their callbacks, finish plot-entry state before restoring a quitter, and restore spectator gamemode before dispatch. | Disable must leave durable recovery rather than orphan attempts, a quitting player must not retain Creative with restored items, and a failed spectator move must not lose their original mode. |

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
- A later current-head review found that a true `dispatchCommand` result only proves command
  handling, not that the UUID matched and moved. Teleports now verify world and coordinates,
  while failed reveal/restore moves retain Adventure mode and a contestant's personal border.
- The reported missing cap-height budget was already covered by startup validation:
  `capY` must be below the arena world's exclusive maximum block height.
- A claimed fire-based teardown path does not exist in this repository. Arena cleanup and
  reset use the batched fill plan, so active-round fire cancellation does not interfere with
  `/battle stop`.
- Vanilla command dispatch is synchronous, but the player's authoritative location may still
  settle later around chunk or lifecycle edges. Immediate successes are accepted; an
  unobserved handled command receives bounded checks after 1, 5, and 20 total ticks before
  any caller applies failure containment.
- Teleport verification failures emit the greppable
  `SCENARIOCRAFT_TELEPORT_FAILURE` marker. Operators should run `/battle stop`, then use a
  manual explicit-world teleport for any player named by the marker if the console command
  subsystem remains unavailable.
- Indirect-mutation cancellation is always on across `battle_world` while a round is active;
  no operator toggle is required. The README documents the dedicated-world expectation and
  recovery runbook, and round start logs the protection scope. Once the controller returns
  to IDLE, normal arena-world behavior resumes except for a contestant whose failed exit is
  still being contained.

## Acceptance evidence

- `make ci-fast` passed on Java 21 with 88 tests.
- Source scans found no `PlayerMoveEvent` and no direct `Player.teleport` call in production
  code; controller teleports are explicit
  `minecraft:execute in minecraft:battle_world run minecraft:tp ...` console commands.
- A real Paper 1.21.11 build 132 server booted the final jar with two protocol clients.
  BuilderOne and BuilderTwo each received a diameter-9 personal border centered on their
  separate plots during BUILDING. Both clients observed the border packets.
- The smoke test verified a barrier cap at Y=-55, allowed place/break within the assigned
  plot, rejected an outside wall break using server-authoritative block state, and removed
  the cap during REVEAL.
- Both clients observed the world-default diameter 59,999,968 border after `/battle stop`
  and again after REVEAL. The server then stopped cleanly with no ScenarioCraft errors.
- The reviewed outer-roof geometry budgets 2,095 setup mutations and 642 reveal mutations
  at 1,000 blocks per tick in the smoke configuration. The real-server smoke asserts the
  queued setup count is completed exactly.

## Retrospective

The smallest useful design was a pure `PlotBoundary` plus `PlotEditPolicy`, leaving Paper
listeners responsible only for translating events into deterministic policy inputs. The
same boundary now drives personal border geometry, vertical edit permission, the barrier
cap, and fluid containment, which keeps those controls aligned. Real protocol clients were
necessary evidence here: unit tests proved the calculations and cleanup calls, while the
server run proved Paper actually sent separate borders, retained cancelled blocks, removed
the cap, and restored defaults across both exit paths.

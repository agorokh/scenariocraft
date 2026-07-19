# ExecPlan: BB-02 arena world, config, and batched geometry

Issue: #4
Owner: Codex
Status: Complete

## Purpose

Deliver the first playable Build Battle arena slice: a predictable superflat world,
configuration-backed arena settings, deterministic non-overlapping plots, and a temporary
`/battle start` command whose clear-and-wall block edits are bounded by the configured
per-tick budget. This needs an ExecPlan because it crosses Paper world lifecycle, command
handling, configuration validation, geometry, scheduling, and acceptance evidence from a
real server boot.

## Progress

- [x] Define the smallest end-to-end slice.
- [x] Implement with tests.
- [x] Capture the issue's acceptance evidence.
- [x] Complete `/review` and resolve P1 findings.
- [x] Resolve external review findings about pre-existing worlds, chunk preparation, and
      smoke fast-failure.
- [x] Keep asynchronous preparation failures on the Paper main thread and align smoke
      failure detection with the runtime log contract.
- [x] Prevent false queued announcements after synchronous failure and recover cleanly from
      mutation-tick exceptions.
- [x] Bound asynchronous chunk preparation, clear every failed build, and make smoke
      correlate its queued and completed mutation totals.
- [x] Scope smoke command detection to log bytes written after `battle start`.
- [x] Let completed chunk loads win a same-deadline race with their main-thread handoff.
- [x] Record the retrospective.

## Decision Log

| Date | Decision | Why |
| --- | --- | --- |
| 2026-07-19 | Keep all arena implementation under a `buildbattle` package and avoid a general scenario abstraction. | BB-02 is scenario-specific, and the repository contract reserves a platform seam for BB-14. |
| 2026-07-19 | Represent clears and walls as lazy cuboid fill operations consumed by one bounded queue. | The command can enqueue a constant number of operations while every tick performs at most `blocks-per-tick` block mutations. |
| 2026-07-19 | Place plots on deterministic concentric square rings using `plot-spacing` as the grid pitch. | This makes bounds, spacing, and non-overlap directly testable while reserving the hub for gathering and later chest work. |
| 2026-07-19 | Treat packaged `config.yml` as the source of numeric and timing defaults, then validate all values while loading. | Defaults remain operator-visible and no gameplay timing, deck, plot, or batch values are hidden in code. |
| 2026-07-19 | Asynchronously prepare every chunk touched by the fill plan and hold plugin chunk tickets until mutation completes. | Synchronous chunk generation inside a scheduled mutation tick can violate the apparent block budget even when the number of `setType` calls is bounded. |
| 2026-07-19 | Reject a pre-existing `battle_world` unless Paper reports `WorldType.FLAT`. | Loading an arbitrary same-named world would make the single sampled floor height unsafe for plots away from the hub. |
| 2026-07-19 | Hand scheduler-rejection failures back to the editor tick through an atomic slot. | The async completion thread must never mutate editor state or call a command sender when Paper rejects the normal main-thread handoff. |
| 2026-07-19 | Treat synchronous preparation and mutation-tick exceptions as failed builds, not queued or permanently busy builds. | Operators and CI must not see a false success line, and a transient world mutation failure must release tickets and permit a later retry. |
| 2026-07-19 | Time out chunk preparation after 30 seconds and invalidate late callbacks by generation. | A lost Paper chunk future must not leave `/battle start` permanently busy, and an old callback must never populate a newer build. |

## Surprises & Discoveries

- The delivery worktree began detached at `origin/main`; it was clean, so the base branch
  could be fast-forwarded and the issue branch created without carrying unrelated changes.
- The first `/review` found that a centered 3×3 grid put the fifth plot directly on the
  hub at the default eight-plot capacity. The geometry now skips the center by construction,
  and the eight-plot regression test asserts all expected ring positions.
- A nested review sandbox could compile the final sources but could not bind a Paper server
  port. The delivery session therefore reran the pinned Paper server directly and captured
  the command evidence below.
- Final review caught that the first edit of an outlying plot could synchronously generate
  its chunk during a mutation tick. Chunk coordinates are now derived from the pure fill
  plan, loaded asynchronously, and ticketed for the lifetime of the edit.
- Kimi's HIGH claim that `/battle` was absent from `plugin.yml` was false: the packaged
  descriptor already declared the command, and the real-server smoke had executed it.
- A current-head review found that CI's preparation-failure grep did not match the runtime
  message and that scheduler rejection could take an off-thread callback path. The log
  contract now matches exactly, and the editor tick owns that failure transition.
- The next current-head review exposed two recovery holes: synchronous preparation could
  emit both failure and success, while a `setType` exception left queued work permanently
  busy. The command now checks editor state before announcing, and the tick failure path
  clears work, releases tickets, and invokes the contained failure callback.
- The final review pass identified a hung-future recovery gap and a partial-enqueue path
  that could retain orphaned mutations. Preparation now has a cancellable deadline with
  generation guards, and every build failure clears the shared queue.
- A post-cooldown review caught that smoke fast-failure signatures were searched across
  the whole log. The harness now snapshots a byte cursor before `battle start`, checks only
  later output, and reports command, preparation, server-exit, and timeout failures distinctly.
- The next Kimi pass noted that `sed` could return an unmatched log line as a supposed
  mutation count. Extraction is now anchored and produces no value on mismatch, followed
  by an explicit digits-only assertion and a separate queued-versus-completed comparison.
- A later cursor pass found that the timeout task could run just before an already-queued
  completion handoff. The deadline now yields when every chunk future is done, and a
  regression test drives that exact scheduler ordering through successful completion.

## Acceptance evidence

- `make ci-fast` completed successfully with 23 tests covering packaged configuration,
  plot bounds, ring spacing, hub reservation, non-overlap, clear-and-wall fill bounds,
  batching limits and arithmetic, asynchronous chunk preparation and ticket cleanup,
  existing-world validation, command authorization settings, and protection-plugin detection.
- Pinned Paper `1.21.11` build `132` enabled the final plugin jar and logged:
  `Loaded or created superflat world battle_world.`
- The same boot logged:
  `Loaded battle_world spawn chunk before reading arena floor at hub x=0, z=0, Y=-61.`
- Running `battle start` from the Paper console logged:
  `Arena build queued: 2 plots in battle_world, 81660 block mutations at 4000 per tick.`
- One second later the server logged:
  `Arena build complete: 2 plots in battle_world (81660 block mutations).`
- The final log contained no `A single server tick took`, `The server has stopped
  responding`, or `Watchdog Thread` signature and completed a clean shutdown with
  `All dimensions are saved`.
- A second `/review` after the hub fix reported no functional regression.

## Retrospective

BB-02 now supplies the configuration contract needed by later phase work, creates and
stabilizes the arena world, reserves a real hub while laying out arbitrary plot counts,
and turns every plot clear or wall build into lazy operations behind a strict tick budget.
The server smoke path now exercises the temporary two-plot command instead of proving only
that the plugin enables. Keeping the fill plan pure made both exact wall geometry and the
runtime mutation count testable without mocking Paper.

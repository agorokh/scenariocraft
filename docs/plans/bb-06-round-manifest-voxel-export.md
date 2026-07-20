# ExecPlan: BB-06 round manifest and voxel export

Issue: #8
Owner: Codex
Status: Complete

## Purpose

Publish each completed Build Battle as the frozen schema-v1 `manifest.json` and one
`p<N>.voxels.json` per plot so the renderer and judge can consume rounds without knowing
Paper internals. This needs an ExecPlan because it crosses the phase controller, bounded
main-thread world reads, off-thread serialization, durable file publication, and a public
contract that cannot change after merge without a schema bump.

## Progress

- [x] Define the smallest end-to-end slice.
- [x] Implement with tests.
- [x] Capture the issue's acceptance evidence.
- [x] Complete `/review` and resolve P1 findings.
- [x] Resolve external lifecycle review by canceling mutable snapshots before abort reset and
      blocking new rounds during immutable writes (2026-07-20).
- [x] Resolve final-head failure-path review by canceling partial chunk loads and requiring
      atomic final-directory publication (2026-07-20).
- [x] Record the retrospective.

Update this list as work proceeds. Add timestamps when a checkpoint is useful to the next
session.

## Decision Log

| Date | Decision | Why |
| --- | --- | --- |
| 2026-07-20 | Snapshot configured plot volumes through a dedicated per-tick read budget when BUILDING enters REVEAL, then encode and write only immutable snapshot data asynchronously. | Bukkit world access stays bounded on the server thread while palette construction, JSON generation, and disk I/O cannot stall a tick. |
| 2026-07-20 | Keep schema-v1 records and JSON encoding in Paper-independent types, with air fixed at palette index zero and x-to-z-to-y flattening made explicit in one tested encoder. | The renderer/judge contract needs exact, deterministic output and unit tests should not require a running server. |
| 2026-07-20 | Publish a round directory only after every temporary file has been written successfully. | Consumers must not observe a manifest that names voxel files which are missing or only partially written. |
| 2026-07-20 | Implement the normative amendment literally: `blocks` is an integer array, x varies fastest under `x + sizeX * (z + sizeZ * y)`, block-state data is omitted, and an empty build publishes height zero plus an empty array. | Issue #8 now freezes these choices for both BB-07 and BB-08; later representation changes require a new schema version. |
| 2026-07-20 | Load every source chunk asynchronously and hold plugin chunk tickets while the per-tick snapshot is read. Start export after reveal-wall removal completes so the editor and exporter never compete for the same plugin-owned ticket. | A bounded `getBlockAt` loop is not watchdog-safe if its first access synchronously loads an unloaded chunk, and Bukkit plugin tickets are not reference-counted between two services using the same plugin. |
| 2026-07-20 | Export contestant plots, not the controller's extra debug/practice plots. | The manifest's `player` field identifies builds that the renderer and judge should process; an unowned debug plot is arena capacity, not a contestant submission. |
| 2026-07-20 | Cancel mutable snapshot work when a round aborts, preserve arena protection while a normal completion still has mutable reads, and keep a new round IDLE while an immutable export write is finishing. | Arena mutations must never race snapshot reads, while an already-captured immutable snapshot can finish safely without dropping the next round's export. |
| 2026-07-20 | Track each chunk future immediately and fail closed when the filesystem cannot atomically rename the completed temporary round directory. | Partial preparation failure must not orphan work, and publishing under the consumer-visible round ID is safe only when the visibility transition is atomic. |

## Surprises & Discoveries

- The delivery worktree began on the BB-04 merge, while remote `main` had advanced to the
  BB-05 merge. Fetching and creating the issue branch from `origin/main` supplied the latest
  plot-containment behavior without adding an unrelated branch dependency.
- The issue's sample uses a 40-block plot height, while current runtime configuration uses a
  configurable 30-block wall height. The exporter therefore treats configured height as
  data; the required 33×40×33 watchdog case is covered independently in tests.
- The original schema sketch made the `blocks` field a string without an encoding. Delivery
  paused until the owner amended issue #8 with the integer-array representation, exact
  flattening formula, all-air behavior, and a normative worked fixture.
- The normative 2×2×2 fixture intentionally retains its declared upper air layer, while live
  export must trim air above the highest non-air block. Keeping exact schema encoding separate
  from runtime trimming allows that fixture to round-trip unchanged without weakening the
  live trimming rule.
- `Material.isAir()` in Paper's API requires live registry access and cannot run in a plain
  unit test. Matching the three air materials explicitly preserves the runtime semantics and
  makes the async-boundary integration test independent of a booted server.
- Review found that `/battle stop` could return the controller to IDLE while the exporter was
  still reading mutable plot blocks, and normal completion could similarly remove event
  protection before snapshotting finished. The exporter now exposes mutable-read state for
  protection, supports reusable cancellation, and keeps round start waiting through any
  already-immutable write.
- Final-head review caught two failure paths that the happy-path tests did not exercise:
  partially started chunk loads were registered too late for cleanup, and the atomic move
  fallback weakened the all-or-nothing publication guarantee. Both paths now fail closed.

## Acceptance evidence

- `make ci-fast` passed on Java 21 with 99 tests, zero failures, and the plugin jar built at
  `build/libs/ScenarioCraft-0.1.0-SNAPSHOT.jar`.
- `RoundExportWriterTest.normativeWorkedExampleRoundTripsExactly` loads the committed
  `fixtures/schema-v1/p1.voxels.json`, programmatically constructs the amendment's single
  oak-plank volume, and proves the exact palette, indices, dimensions, and decoded blocks.
- `RoundExportWriterTest.completeRoundFilesExistAndMatchFrozenJsonSchemas` atomically publishes
  `manifest.json` plus `p1.voxels.json`, validates both against the committed tiny schema
  fixtures, and decodes the resulting voxel file back to the known structure.
- `RoundExportWriterTest.entirelyAirPlotIsStillWrittenAndListedWithZeroHeight` proves the empty
  file and manifest entry survive trimming with `size[1] = 0`, palette index zero as air, and
  `blocks: []`.
- `BatchedRoundSnapshotTest.fullPlotSnapshotHonorsEveryTickBudgetWithoutAWatchdogStall` reads a
  complete 33×40×33 volume in 11 batches of at most 4,000 reads, under a one-second unit-test
  timeout, then verifies all 43,560 indices.
- `RoundExportServiceTest.snapshotReadsFinishBeforeEncodingAndFilesAreWrittenOffThread` proves
  cancellation invalidates a mutable snapshot without scheduling a write, then proves a
  subsequent export's chunk preparation and bounded snapshotting finish before asynchronous
  encoding and no round directory is visible until the off-thread writer publishes it.
- `RoundControllerTest.fullRoundQueuesOnlyContestantPlotsForExportAtReveal` proves the phase
  controller supplies the frozen task, world, player, origin, and configured volume after the
  reveal walls finish their bounded removal.
- `RoundControllerTest.stoppingRevealCancelsTheMutableSnapshotBeforeAnotherArenaReset` and
  `newRoundWaitsForAnImmutableExportWriteToFinish` pin both sides of the export lifecycle gate;
  `idleArenaProtectionLastsUntilMutableSnapshotReadsFinish` keeps external mutation protection
  active only through the mutable-read portion.
- `RoundExportServiceTest.partialChunkPreparationFailureCancelsLoadsThatAlreadyStarted` proves
  a later synchronous load failure cancels earlier futures, while
  `RoundExportWriterTest.unsupportedAtomicMoveFailsWithoutExposingTheFinalRoundDirectory`
  proves unsupported atomic publication leaves no consumer-visible or temporary directory.
- Review against `code_review.md` found no P1: export logic has focused tests, adds no block
  mutation, adds no player-facing output or inventory UI, and contains no credential material.

## Retrospective

BB-06 now turns the end of BUILDING into a durable renderer/judge handoff without putting
chunk loading, unbounded world reads, JSON construction, or disk I/O onto a server tick. The
contract layer remains Paper-independent and is pinned by the owner's worked example plus
schema-shape fixtures. Separating exact encoding from live air trimming kept the amended
fixture authoritative while preserving small output files, and explicit chunk-ticket
ownership closed the remaining watchdog risk before review.

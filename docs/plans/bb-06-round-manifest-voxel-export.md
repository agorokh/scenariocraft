# ExecPlan: BB-06 round manifest and voxel export

Issue: #8
Owner: Codex
Status: In progress

## Purpose

Publish each completed Build Battle as the frozen schema-v1 `manifest.json` and one
`p<N>.voxels.json` per plot so the renderer and judge can consume rounds without knowing
Paper internals. This needs an ExecPlan because it crosses the phase controller, bounded
main-thread world reads, off-thread serialization, durable file publication, and a public
contract that cannot change after merge without a schema bump.

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
| 2026-07-20 | Snapshot configured plot volumes through a dedicated per-tick read budget when BUILDING enters REVEAL, then encode and write only immutable snapshot data asynchronously. | Bukkit world access stays bounded on the server thread while palette construction, JSON generation, and disk I/O cannot stall a tick. |
| 2026-07-20 | Keep schema-v1 records and JSON encoding in Paper-independent types, with air fixed at palette index zero and x-to-z-to-y flattening made explicit in one tested encoder. | The renderer/judge contract needs exact, deterministic output and unit tests should not require a running server. |
| 2026-07-20 | Publish a round directory only after every temporary file has been written successfully. | Consumers must not observe a manifest that names voxel files which are missing or only partially written. |

## Surprises & Discoveries

- The delivery worktree began on the BB-04 merge, while remote `main` had advanced to the
  BB-05 merge. Fetching and creating the issue branch from `origin/main` supplied the latest
  plot-containment behavior without adding an unrelated branch dependency.
- The issue's sample uses a 40-block plot height, while current runtime configuration uses a
  configurable 30-block wall height. The exporter therefore treats configured height as
  data; the required 33×40×33 watchdog case is covered independently in tests.

## Acceptance evidence

To be completed with exact test names, local CI output, Paper smoke output, and generated
schema-v1 fixture paths.

## Retrospective

To be completed after implementation and review.

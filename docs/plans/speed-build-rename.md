# ExecPlan: Speed Build rename

Issue: #31
Owner: Codex
Status: Draft

## Purpose

Make the design council's chosen name, Speed Build, the primary player and reader-facing
identity while preserving existing `/battle` and `/bb` commands for family servers.
This is a cross-cutting rename, so the plan tracks the frozen-schema boundary and every
public surface rather than risking an incomplete text replacement.

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
| 2026-07-21 | Keep the `buildbattle` Java package and `battle_world` identifier unchanged. | Issue #31 explicitly excludes code-identifier churn and frozen contract changes. |
| 2026-07-21 | Register `speedbuild` as the command key and retain `battle`, `buildbattle`, and `bb` as aliases. | Paper advertises the primary command while preserving established family-server commands. |

## Surprises & Discoveries

Issue #28 had to merge first. It is merged at `dcd57322c30c1ff87c91f46e5a798bd4dac0d18f`,
so this branch begins from the current `main` after that dependency.

## Acceptance evidence

- Descriptor and command tests show `/speedbuild` is primary and `/battle` plus `/bb` remain
  registered aliases.
- Repository text scan excludes historical plans and confirms no remaining reader/player-facing
  `Build Battle` text.
- Hash comparison against `main` confirms frozen `manifest.json` and `voxels.json` are unchanged.
- `make ci-fast` and the Paper smoke boot pass.

## Retrospective

Pending implementation.

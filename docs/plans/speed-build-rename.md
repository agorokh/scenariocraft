# ExecPlan: Speed Build rename

Issue: #31
Owner: Codex
Status: Complete

## Purpose

Make the design council's chosen name, Speed Build, the primary player and reader-facing
identity while preserving existing `/battle` and `/bb` commands for family servers.
This is a cross-cutting rename, so the plan tracks the frozen-schema boundary and every
public surface rather than risking an incomplete text replacement.

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
| 2026-07-21 | Keep the `buildbattle` Java package and `battle_world` identifier unchanged. | Issue #31 explicitly excludes code-identifier churn and frozen contract changes. |
| 2026-07-21 | Register `speedbuild` as the command key and retain `battle`, `buildbattle`, and `bb` as aliases. | Paper advertises the primary command while preserving established family-server commands. |

## Surprises & Discoveries

Issue #28 had to merge first. It is merged at `dcd57322c30c1ff87c91f46e5a798bd4dac0d18f`,
so this branch begins from the current `main` after that dependency.

The local macOS shell has no Java runtime. The project was therefore built and tested with
Java 21 in the pinned Temurin container; the source tree's generated test report records
255 tests, zero failures, and zero skips.

## Acceptance evidence

- `PluginDescriptorTest` verifies `speedbuild` is primary and `battle`, `buildbattle`, and `bb`
  are registered aliases; `BattleCommandTest` verifies command behavior remains label-agnostic.
- A repository text scan finds no `Build Battle` text in live player/resource/source surfaces;
  the README's single occurrence is the required historical rename note.
- `git diff --exit-code origin/main -- judge/src/test/resources/fixtures/runtime/rounds/round-20260721-193000/manifest.json`
  confirms the tracked frozen schema is byte-identical. There is no tracked `voxels.json`.
- The `make ci-fast` equivalent completed under Java 21: site checks, Gradle wrapper checksum,
  build, and all 255 tests passed. GitHub CI will additionally run the Paper smoke boot.

## Retrospective

The rename stayed on the issue's safe surfaces: command registration, live player text,
documentation, metadata, and tests. Java package/class identifiers and the frozen manifest
were intentionally unchanged. Future scenario work should keep the public identity separate
from these internal compatibility identifiers.

# ExecPlan: BB-07 voxel renderer

Issue: #9
Owner: Codex session 019f814e-00c3-7061-b809-dfa183f9ca0e
Status: In progress

## Purpose

Deliver a standalone Java 21 renderer that consumes only the frozen schema-v1 voxel JSON
contract and emits the seven deterministic PNG views used by the judge. The work needs an
ExecPlan because it combines JSON validation, projection and visibility logic, palette
handling, a CLI, deterministic image encoding, fixtures, golden evidence, and a performance
constraint.

## Progress

- [x] Define the smallest end-to-end slice.
- [ ] Implement with tests.
- [ ] Capture the issue's acceptance evidence.
- [ ] Complete `/review` and resolve P1 findings.
- [ ] Record the retrospective.

## Decision Log

| Date | Decision | Why |
| --- | --- | --- |
| 2026-07-20 | Add an isolated `renderer/` Gradle subproject using Java2D and a JSON-only model. | Issue #9 requires standalone execution with no Bukkit/Paper dependency. |
| 2026-07-20 | Treat issue #8's newest amendment as the complete schema-v1 blocks contract. | It normatively freezes integer arrays, x-fastest flattening, and zero-height empty plots. |
| 2026-07-20 | Commit the amendment's exact 2x2x2 example and a hand-authored small house fixture. | BB-06 is not landed, and the operator explicitly requires independent fixtures. |
| 2026-07-20 | Generate all views through fixed integer-coordinate drawing and deterministic PNG writing. | Same input must produce byte-identical output across repeated renders. |

## Surprises & Discoveries

- A prerequisite shell check initially used `path` as a zsh loop variable. Because zsh ties
  that special array to `PATH`, the command temporarily hid `make`; rerunning with a neutral
  variable confirmed `/usr/bin/make` and the repository gate are available.

## Acceptance evidence

Pending implementation and verification.

## Retrospective

Pending completion.

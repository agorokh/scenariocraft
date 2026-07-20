# ExecPlan: BB-07 voxel renderer

Issue: #9
Owner: Codex session 019f814e-00c3-7061-b809-dfa183f9ca0e
Status: Complete

## Purpose

Deliver a standalone Java 21 renderer that consumes only the frozen schema-v1 voxel JSON
contract and emits the seven deterministic PNG views used by the judge. The work needs an
ExecPlan because it combines JSON validation, projection and visibility logic, palette
handling, a CLI, deterministic image encoding, fixtures, golden evidence, and a performance
constraint.

## Progress

- [x] Define the smallest end-to-end slice.
- [x] Implement with tests.
- [x] Capture the issue's acceptance evidence.
- [x] Complete `/review` and resolve P1 findings.
- [x] Resolve the full post-commit review inventory and add regression coverage.
- [x] Record the retrospective.

## Decision Log

| Date | Decision | Why |
| --- | --- | --- |
| 2026-07-20 | Add an isolated `renderer/` Gradle subproject using Java2D and a JSON-only model. | Issue #9 requires standalone execution with no Bukkit/Paper dependency. |
| 2026-07-20 | Treat issue #8's newest amendment as the complete schema-v1 blocks contract. | It normatively freezes integer arrays, x-fastest flattening, and zero-height empty plots. |
| 2026-07-20 | Commit the amendment's exact 2x2x2 example and a hand-authored small house fixture. | BB-06 is not landed, and the operator explicitly requires independent fixtures. |
| 2026-07-20 | Generate all views through fixed integer-coordinate drawing and deterministic PNG writing. | Same input must produce byte-identical output across repeated renders. |
| 2026-07-20 | Parse integer fields from the JSON token tree before model construction. | Gson's typed adapters coerce strings and decimal tokens, which violates the frozen schema-v1 integer contract. |

## Surprises & Discoveries

- A prerequisite shell check initially used `path` as a zsh loop variable. Because zsh ties
  that special array to `PATH`, the command temporarily hid `make`; rerunning with a neutral
  variable confirmed `/usr/bin/make` and the repository gate are available.
- The first golden regeneration succeeded in writing the image but Gradle rejected its
  `doLast` closure as configuration-cache incompatible. Splitting rendering and copying into
  typed tasks made regeneration cache-safe.
- `/review` found no P1. It identified a P2 where an unexpected stale destination file could
  break the exactly-seven-output guarantee; rendering now rejects such a directory without
  deleting user data, and a regression test covers the case.
- The post-fix review found that the camera directions were assigned to filenames without
  accounting for Minecraft's positive-Z-is-south convention. The output mapping and NE golden
  were corrected to name all four compass views accurately.
- The external review found three validation edges: Gson token coercion, long overflow in the
  size product, and null palette entries. Strict token parsing, checked multiplication, and
  explicit palette validation now reject all three with regression tests.

## Acceptance evidence

- `make ci-fast` passed on Java 21 with the root plugin tests and 13 renderer tests.
- The installed standalone CLI rendered the exact 2x2x2 amendment fixture and emitted only
  `iso-ne.png`, `iso-se.png`, `iso-sw.png`, `iso-nw.png`, `plan.png`, `cut-x.png`, and
  `cut-z.png`, all at 1024×1024.
- Repeated small-house renders were byte-identical for all seven PNGs; the committed NE golden
  is tracked with the fixture and verified byte-for-byte by the test suite.
- The 33×40×33 solid-plot performance test rendered all seven views in 0.294 seconds locally,
  below the 5-second acceptance bound.
- The renderer runtime dependency report contained no Paper or Bukkit artifact, and an
  isolated-classpath regression confirms `org.bukkit.Bukkit` is absent.
- Visual inspection of the golden `iso-ne.png` shows a wooden house with a stepped dark roof,
  windows, and a brick chimney on a transparent background.

## Retrospective

BB-07 ships as an isolated Java 21 application that validates the frozen voxel schema and
renders the seven judge views deterministically. Integer-coordinate rasterization plus a
fixed palette/fallback keeps output stable, while the independent worked example, empty plot,
small house, golden image, and full-size generated plot cover the contract without waiting
for BB-06.

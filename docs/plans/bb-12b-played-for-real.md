# ExecPlan: BB-12b Played-for-real proof round

Issue: #38
Owner: Codex session 019f85c0-096d-75e2-899a-9998c4167f05
Status: In progress

## Purpose

Turn the How to Play walkthrough into evidence from one reproducible Speed Build round. Open-
source Mineflayer contestants must drive the real Paper plugin through its complete round,
exercise the Secret Chest rejection path, place blocks only in their assigned plots, and leave a
committed bundle whose voxel exports, renderer images, transcript, and unedited judge verdicts
generate the public page deterministically. This needs an ExecPlan because it crosses the Paper
runtime, Docker Compose, a new Node driver, the renderer and judge CLIs, frozen BB-06 artifacts,
and the static site/CI contract.

## Progress

- [x] Define the smallest end-to-end slice.
- [ ] Implement the Mineflayer round driver and proof-round orchestration with timeout failures.
- [ ] Freeze and validate the round artifact bundle without changing BB-06 schemas.
- [ ] Regenerate the How to Play page from the committed real-round bundle.
- [ ] Capture the issue's acceptance evidence, including a clean proof-round timing.
- [ ] Complete `/review` and resolve P1 findings.
- [ ] Record the retrospective.

Update this list as work proceeds. Add timestamps when a checkpoint is useful to the next
session.

## Decision Log

| Date | Decision | Why |
| --- | --- | --- |
| 2026-07-21 | Add a repository-owned Node + Mineflayer driver under `e2e/round-driver/` and run it beside the existing BB-12 Compose server. | This exercises the real network protocol and player-visible controls while keeping the driver reproducible from the clone. |
| 2026-07-21 | Keep the frozen BB-06 `manifest.json` and `p<N>.voxels.json` contracts byte-compatible; assemble the page proof manifest as a separate site bundle from copied exports plus driver observations. | Issue #2 forbids silently changing frozen schemas, while issue #38 needs transcript, phase, and block-count evidence that the game export does not currently contain. |
| 2026-07-21 | Make `make proof-round` the operator-only live path, but make `site-check` validate and regenerate a temporary page from the committed bundle. | Public CI must prove deterministic documentation without starting Docker, bots, or a key-bearing judge. |
| 2026-07-21 | Use only the invented contestants Blocky, Crafty, and Pixel in the public proof data and presentation. | The issue explicitly forbids child names in every artifact and change surface. |

## Surprises & Discoveries

- The merged BB-12 demo starts a headless, one-player/sample round through RCON and keeps the
  judge running as a watcher. BB-12b needs a separate multi-player proof path rather than
  weakening that quick demo contract.
- Issue #31 has not landed on the base branch. The proof driver will prefer `/speedbuild` only
  if that command exists when implementation reaches the command surface; compatibility with
  the current `/battle` command must remain until the rename lands.

## Acceptance evidence

Pending implementation and the clean proof-round run.

## Retrospective

Pending completion.

# ExecPlan: BB-12b Played-for-real proof round

Issue: #38
Owner: Codex session 019f85c0-096d-75e2-899a-9998c4167f05
Status: Complete

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
- [x] Implement the Mineflayer round driver and proof-round orchestration with timeout failures.
- [x] Freeze and validate the round artifact bundle without changing BB-06 schemas.
- [x] Regenerate the How to Play page from the committed real-round bundle.
- [x] Capture the issue's acceptance evidence, including a clean proof-round timing.
- [x] Complete the final `/review` and resolve its findings.
- [x] Record the final retrospective.
- [x] Repair the stale GitHub conflict state after confirming the current base is already merged.

Update this list as work proceeds. Add timestamps when a checkpoint is useful to the next
session.

## Decision Log

| Date | Decision | Why |
| --- | --- | --- |
| 2026-07-21 | Add a repository-owned Node + Mineflayer driver under `e2e/round-driver/` and run it beside the existing BB-12 Compose server. | This exercises the real network protocol and player-visible controls while keeping the driver reproducible from the clone. |
| 2026-07-21 | Keep the frozen BB-06 `manifest.json` and `p<N>.voxels.json` contracts byte-compatible; assemble the page proof manifest as a separate site bundle from copied exports plus driver observations. | Issue #2 forbids silently changing frozen schemas, while issue #38 needs transcript, phase, and block-count evidence that the game export does not currently contain. |
| 2026-07-21 | Make `make proof-round` the operator-only live path, but make `make proof-check` regenerate the committed renderer views and page from the frozen bundle. | Public CI must prove deterministic documentation without starting Docker, bots, or a key-bearing judge. |
| 2026-07-21 | Use only the invented contestants Blocky, Crafty, and Pixel in the public proof data and presentation. | The issue explicitly forbids child names in every artifact and change surface. |
| 2026-07-21 | Drive `/speedbuild start` and synchronize the delivery branch with the merged rename and page design before freezing acceptance evidence. | Issues #31 and #41 landed while this delivery was active; the proof must describe the current public product rather than the pre-rename branch point. |
| 2026-07-21 | Force-recreate Paper and the judge at the start of every proof run. | Rebuilding the one-shot plugin installer does not make an already-running Paper process reload the jar, so repeat runs otherwise reach stale code. |
| 2026-07-21 | Derive picker, task, and every phase timestamp from the full transcript during public validation, and byte-compare regenerated renderer output. | Independently replaying the evidence prevents self-asserted metadata or an unrelated valid PNG from passing as proof. |
| 2026-07-21 | Run the proof publisher as the invoking UID/GID on rootful Linux, and as container root under Docker Desktop or rootless Docker. | Each engine maps those container identities back to writable, maintainable host artifacts differently. |
| 2026-07-21 | Record the judge renderer palette per proof manifest and give all three proof materials explicit palette entries. | The live judge and public page must use the same disclosed colors instead of relying on a renderer fallback. |
| 2026-07-21 | Give the three bots distinct 6-, 11-, and 18-block structures. | Identical six-block structures repeatedly produced tied panels; different truthful builds make the winner path testable without manufacturing a result. |
| 2026-07-21 | State the kid-comment lexical contract in both the live judge prompt and its strict response schema. | Live responses must reliably name a visible strength first and end with one achievable next step, matching the validator rather than discovering its contract only after a rejected response. |
| 2026-07-21 | Push a normal follow-up commit instead of rebasing or force-pushing after GitHub reported the synchronized head as conflicting. | The current base commit is already a direct parent of the PR head, so there is no content conflict to choose between; advancing the branch safely forces GitHub to recompute mergeability while preserving review history. |

## Surprises & Discoveries

- The merged BB-12 demo starts a headless, one-player/sample round through RCON and keeps the
  judge running as a watcher. BB-12b needs a separate multi-player proof path rather than
  weakening that quick demo contract.
- The proof-runner image initially omitted the renderer install distribution. Building
  `:renderer:installDist` in the shared Docker build stage made the real CLI available without
  duplicating renderer logic in Node.
- Paper's same-IP connection throttle requires a five-second delay between the three Mineflayer
  joins. Sequential delayed joins remain well inside the proof timeout.
- Real bot placement exposed a Paper interaction edge case: marking the clicked structural floor
  `DENY` makes Paper treat the whole placement as cancelled before `BlockPlaceEvent` can validate
  the adjacent target. Leaving the clicked block result `DEFAULT` while allowing the item lets
  blocks, buckets, redstone, and attachments reach the existing target policy without permitting
  floor transformations; the regression test covers both block and non-block items.
- A repeat proof run showed that Compose rebuilt the plugin installer but kept an already-running
  Paper container. `--force-recreate` is required for idempotent current-code evidence.
- A real judge run exposed that the prompt did not tell the model the validator's exact two-sentence
  kid-comment contract. Adding that contract to the prompt and JSON schema produced complete
  nine-verdict panels on subsequent live rounds without weakening validation.
- The first successful live result exposed a proof-fixture error: Gson omits null winner-alternative
  fields, while the validator fixture expected explicit nulls. Aligning the strict validator with
  production serialization let the exact one-command run publish and verify its bundle.
- GitHub reported the PR as `dirty` even though the current base is a direct parent of the PR head
  and `git merge-base --is-ancestor` confirms the branch already contains it. The conflict repair
  therefore needs no content choice; a normal follow-up commit is sufficient to refresh the stale
  mergeability calculation without rewriting history.

## Acceptance evidence

- `OPENAI_API_KEY=<operator key> make proof-round` completed with
  `SCENARIOCRAFT_PROOF_ROUND_SUCCESS elapsed_seconds=263`.
- Played round: `round-20260721-214219`; driver duration 161 seconds; phase timeline contains
  PREPARING, GATHERING, NOTE_PICK, BUILDING, REVEAL, and RESULTS in order.
- Blocky exercised the non-picker gate and received the exact rejection for picker Pixel.
- Blocky, Crafty, and Pixel exported 6, 11, and 18 non-air blocks respectively from their own
  assigned plots.
- The live result contains three verdicts from each of Professor Brickworth, Captain Sparkle,
  and Granny Redstone; Pixel won with mean 2.1666666666666665 and Paper logged the in-game
  result announcement.
- `make proof-check` reports one valid committed bundle after regenerating all nine renderer
  views byte-for-byte, replaying the transcript, and confirming a byte-current generated page.
- A substituted valid PNG fails with `Render does not match p1.voxels.json`; missing-key and
  inherited-dry-run tests both exit 2 before Docker.
- The proof-runner image builds successfully, and the selected Docker user can write both proof
  bind mounts on macOS; Compose selects the invoking numeric user on native Linux.
- Browser QA at the default desktop viewport and 390-pixel mobile viewport found no broken
  images or horizontal document overflow, and confirmed readable real-build and verdict cards.

## Retrospective

- The live panel evaluated the exact committed voxel geometry using explicit faithful palette
  entries for all three proof materials. The same material colors are regenerated for the public
  page and declared in the proof manifest.

The strongest part of this delivery is that the public claim is independently recomputable: CI
does not trust the proof manifest's phase fields or the committed PNGs, but derives and regenerates
them from lower-level frozen artifacts. The live runs also paid for four operational discoveries—
the same-IP join throttle, Compose container reuse, Paper's split interaction semantics, and the
judge comment contract—that are now encoded in code and regression tests.

Review materially tightened the result. It closed a dry-run leakage path, bound every Compose
operation to the global deadline, preserved the complete Paper interaction contract, averaged the
displayed judge panel, made two-plot page generation valid, synchronized accessibility labels, and
prevented root-owned proof output on native Linux. The final pass also kills the complete external
process group at the deadline, rejects extra phase fields, and limits the protected-floor exception
to items that actually target the editable adjacent block. The remaining work belongs to the normal
PR resolution loop: remote CI, review-thread resolution, and merge.

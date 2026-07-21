# ExecPlan: Speed Build How to Play page

Issue: #32
Owner: Codex
Status: In progress

## Purpose

Publish a self-contained, phone-readable GitHub Pages story that explains a complete Speed
Build round in seven visual steps. The page must use the children's original logo as its visual
source, draw gameplay imagery from repository-owned voxel fixtures and renderer output, and
give Build Week judges a one-minute path to understanding the game without installing it.

## Progress

- [x] Define the smallest end-to-end slice.
- [x] Inspect and preserve the supplied logo assets under `assets/branding/`.
- [x] Produce traceable renderer imagery and implement all seven story steps.
- [x] Add the Pages deployment workflow and README header link.
- [ ] Verify zero external requests, mobile readability, source traceability, and local CI.
- [x] Enable workflow-based GitHub Pages; live verification follows merge-time deployment.
- [ ] Complete `/review` against `code_review.md` and resolve P1 findings.
- [ ] Record the retrospective.

## Decision Log

| Date | Decision | Why |
| --- | --- | --- |
| 2026-07-20 | Build one semantic HTML page with local CSS, local images, and no JavaScript dependency. | This is the smallest robust surface that meets the zero-external-request and phone-readability requirements. |
| 2026-07-20 | Use committed schema-v1 voxel fixtures rendered by the repository CLI as primary scene imagery, with inline decorative CSS/SVG only where a renderer cannot express the game phase. | Every visual remains original and traceable to repository artifacts while the seven-step story stays legible. |
| 2026-07-20 | Keep all rename implementation surfaces out of this branch; only the page copy and README link use the adopted target name and command. | Issue #31 belongs to `pgorokh`, and #32 explicitly permits advertising the target state without changing plugin behavior. |
| 2026-07-20 | Deploy the literal `site/` directory with the official Pages artifact and deploy actions. | It preserves the issue's requested source layout and avoids a generated-site toolchain or external dependencies. |
| 2026-07-20 | Derive the final page system from the supplied 1535x1024 logo: acid grass green, warm dirt and gold, purple-black deepslate, italic angular type, beveled depth, asymmetric horizontal trails, and a white field. | These are the actual recurring cues in the design council's image; using them directly keeps the page in the same visual language rather than merely using generic pixel styling. |

## Surprises & Discoveries

- The requested `~/Downloads/speed-build-logo.png` was absent during initial discovery and
  blocked the first review. The operator then restored the original file. It is the only
  matching variant and is preserved byte-for-byte under `assets/branding/`.
- The operator's original checkout contains unrelated work on another branch, so #32 uses a
  separate clean worktree based on `origin/main` rather than disturbing those files.
- The desktop shell does not select its installed Java automatically. Setting
  `JAVA_HOME=/opt/homebrew/opt/openjdk@21` exposes Java 21.0.11 for renderer and CI runs.
- The in-app browser was unavailable, so responsive screenshot evidence must be captured from
  the public Pages URL after deployment.
- The first `/review` found one P1 and no other findings: the missing canonical logo made
  `site-check`, the Pages staging copy, the README image, and the page hero fail. Restoring and
  committing the exact supplied asset resolves that finding; a second review will verify it.

## Acceptance evidence

- Pages API: `POST /repos/agorokh/scenariocraft/pages` succeeded with
  `build_type: workflow`, `public: true`, and HTTPS enabled.
- Canonical logo SHA-256:
  `0e4a76e9c3b7c676e723f85fba885b4b6a7d954c6386af791bfabb15647b9a42`.
- Renderer regression:
  `./gradlew :renderer:test --tests 'io.github.agorokh.scenariocraft.renderer.VoxelRendererTest'`
  passed under Java 21.
- Demo fixture SHA-256:
  `e1fbe884fb463a0ec8e8986b169fa12ad9d8114e498e84ac10c950ffbbe0c557`.
- Page render SHA-256 values: isometric
  `afb8d7f7103c17ed2c29cd88cafa2e5caaaf1266beab0eb7d13dab72a9ac8676`, cutaway
  `576ecf174d97a88ab4ad298c1d1e271f3844b95085619549edd36204ab5c9720`, and plan
  `e557947a5768e6e5244860fbfa387a47a8465c30699ad40570d94a21ae6b782a`.
- Static scans find exactly seven step articles and no remote `src`, `href`, CSS `@import`,
  or remote CSS `url()` resource.
- Full `make ci-fast`, GitHub CI, Pages deployment, responsive screenshots, and live URL
  verification remain pending on the missing logo.

## Retrospective

To be completed after deployment and review.

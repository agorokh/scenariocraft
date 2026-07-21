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
- [ ] Inspect and preserve the supplied logo assets under `assets/branding/`.
- [ ] Produce traceable renderer imagery and implement all seven story steps.
- [ ] Add the Pages deployment workflow and README header link.
- [ ] Verify zero external requests, mobile readability, source traceability, and local CI.
- [ ] Enable workflow-based GitHub Pages and verify the live deployment.
- [ ] Complete `/review` against `code_review.md` and resolve P1 findings.
- [ ] Record the retrospective.

## Decision Log

| Date | Decision | Why |
| --- | --- | --- |
| 2026-07-20 | Build one semantic HTML page with local CSS, local images, and no JavaScript dependency. | This is the smallest robust surface that meets the zero-external-request and phone-readability requirements. |
| 2026-07-20 | Use committed schema-v1 voxel fixtures rendered by the repository CLI as primary scene imagery, with inline decorative CSS/SVG only where a renderer cannot express the game phase. | Every visual remains original and traceable to repository artifacts while the seven-step story stays legible. |
| 2026-07-20 | Keep all rename implementation surfaces out of this branch; only the page copy and README link use the adopted target name and command. | Issue #31 belongs to `pgorokh`, and #32 explicitly permits advertising the target state without changing plugin behavior. |
| 2026-07-20 | Deploy the literal `site/` directory with the official Pages artifact and deploy actions. | It preserves the issue's requested source layout and avoids a generated-site toolchain or external dependencies. |

## Surprises & Discoveries

- The requested `~/Downloads/speed-build-logo.png` glob did not resolve during initial
  discovery. A broader filename search is in progress before any branding implementation.
- The operator's original checkout contains unrelated work on another branch, so #32 uses a
  separate clean worktree based on `origin/main` rather than disturbing those files.

## Acceptance evidence

To be completed with renderer commands and hashes, self-containment checks, responsive browser
screenshots, local CI output, GitHub Actions status, Pages API state, and the verified live URL.

## Retrospective

To be completed after deployment and review.

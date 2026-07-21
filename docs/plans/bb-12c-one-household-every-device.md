# ExecPlan: BB-12c One household, every device

Issue: #43
Owner: Codex session 019f8642-a30e-7e11-89e0-6e80df9773ca
Status: In progress

## Purpose

Make the repository's standing Bedrock-first promise runnable and honest. The delivery adds
an opt-in Geyser, Floodgate, and ViaVersion Compose overlay for supported Linux Docker hosts,
a regression check for that deployment contract, an operator-run RakNet probe, and clear
macOS standalone guidance. It also carries the household story into the repository README
and How to Play page without weakening the existing safety or unofficial-product labels.

This crosses Compose configuration, operator automation, documentation, the public page, and
CI, so an ExecPlan keeps the platform-specific claims and acceptance evidence aligned.

## Progress

- [x] Define the smallest end-to-end slice.
- [ ] Implement with tests.
- [ ] Capture the issue's acceptance evidence.
- [ ] Complete `/review` and resolve P1 findings.
- [ ] Record the retrospective.

## Decision Log

| Date | Decision | Why |
| --- | --- | --- |
| 2026-07-21 | Keep Bedrock support in `docker-compose.bedrock.yml` and leave the base demo unchanged. | The issue explicitly makes the bridge opt-in, and Java-only smoke coverage must remain stable. |
| 2026-07-21 | Add a Compose-render regression check to `make ci-fast` and keep the live RakNet probe operator-run. | CI can prove the overlay parses and retains the required plugin and UDP contract without pretending that a client joined a real server. |
| 2026-07-21 | Document Docker Desktop on macOS as unsupported for the overlay's UDP host path and provide a host-run Geyser Standalone fallback. | The issue requires the platform limitation to remain visible rather than overstating support. |

## Surprises & Discoveries

- The delivery worktree began detached because the primary checkout owns `main`; it was
  clean and exactly matched `origin/main`, so the issue branch was created directly from the
  verified remote base without disturbing the primary checkout.

## Acceptance evidence

To be completed with exact commands and outputs after implementation and live-capable checks.

## Retrospective

To be completed after review and CI verification.

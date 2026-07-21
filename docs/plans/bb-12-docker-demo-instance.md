# ExecPlan: BB-12 Docker demo instance

Issue: #14
Owner: Codex session 019f833e-e103-75d0-b2b6-4abfd1270517
Status: In progress

## Purpose

Deliver a clean-machine Docker path that lets a human judge start ScenarioCraft, join a
ready arena, run a complete Build Battle alone in under ten minutes, and see the verdict in
Minecraft chat. This needs an ExecPlan because it crosses the Paper server, plugin runtime,
renderer/judge process, Docker packaging, bootstrap data, and operator documentation.

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
| 2026-07-20 | Package the Paper server and judge from this repository and orchestrate them with Docker Compose. | The issue requires one reproducible project-owned path and forbids assuming host tools beyond Docker. |
| 2026-07-20 | Make the OpenAI key a required Compose interpolation and keep it environment-only. | Startup must fail clearly when the key is absent, without copying credentials into files or images. |
| 2026-07-20 | Build solo mode around one real contestant plus a bundled sample second plot. | A single Devpost judge must exercise the normal export, judging, and announcement path instead of a reduced mock-only flow. |
| 2026-07-20 | Treat `main` as the delivery base while BB-09 PR #34 remains an external dependency. | Issue #14 does not name a non-default base; the delivery procedure requires the repository default in that case. Integration evidence will be captured only after the announcer dependency is available. |

## Surprises & Discoveries

- BB-09 is implemented in PR #34 with green CI but was not merged when this plan started.
  Issue #14 can build its isolated Docker and solo-mode slices on `main`, but final verdict
  announcement evidence depends on that PR reaching the base branch.

## Acceptance evidence

Pending. Record the exact clean-machine-equivalent quickstart, elapsed time, solo-player
proof, missing-key failure, headless demo output, and GitHub/local CI results here.

## Retrospective

Pending completion.

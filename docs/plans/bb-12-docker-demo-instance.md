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
- [x] Implement with tests.
- [ ] Capture the issue's acceptance evidence. Dry-run and live-key judge evidence are
      complete; integrated chat-announcement and one-human evidence remain.
- [x] Complete `/review` and resolve P1 findings.
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
- The first real Compose smoke mounted server data and plugin data as separate named volumes.
  Paper correctly wrote rounds under the plugin volume, but the judge initially watched the
  shadowed `server-data/plugins` path. Mounting the plugin volume directly into the judge made
  the ownership boundary explicit and produced a two-plot verdict on the next watcher start.
- The host has Docker but no Java runtime and exposes Compose as `docker-compose`, not the
  newer subcommand. Local CI therefore ran in the pinned Java 21 Gradle image; the documented
  `docker compose` form was separately copy-paste validated through the installed Compose
  plugin binary.
- The first live OpenAI pass returned schema-invalid output twice for one persona. The judge's
  bounded retry path recovered, completed the round, and wrote a valid result without exposing
  the API key. This is useful acceptance evidence for the real model boundary rather than the
  deterministic dry-run path.
- BB-09 PR #34 reached green CI with all threads resolved and a clean current-head Codex review
  comment, but the review app did not emit the formal review object required by `resolve-pr`
  after three configured cycles. The dependency resolver recorded escalation comment
  `#5031442899`; BB-12 cannot capture integrated chat evidence through the normal base path
  until that gate is restored and BB-09 merges.

## Acceptance evidence

- `env -u OPENAI_API_KEY docker-compose config` exited 1 with
  `OPENAI_API_KEY is required; export it before starting ScenarioCraft`.
- A fresh Docker-volume smoke booted Paper 1.21.11, enabled ScenarioCraft, and logged
  `SCENARIOCRAFT_DEMO_ARENA_READY` after 27,189 mutations at the configured 6,000-block
  per-tick budget.
- Headless RCON `/battle start` entered PREPARING at 06:18:48 UTC, exported two bundled
  sample plots at 06:20:22 UTC, and the dry-run judge wrote `results.txt` with a winner.
- A fresh-volume live-key Compose probe loaded `OPENAI_API_KEY` from the ignored main-checkout
  `.env`, reached `SCENARIOCRAFT_DEMO_ARENA_READY` in 97 seconds, started a headless round,
  and wrote a real OpenAI-backed verdict for `round-20260721-072451` in 272 seconds total
  (175 seconds after arena readiness). The probe removed its containers and named volumes.
- The one-player controller regression exports `BuilderKid` as p1 and
  `ScenarioCraft Sample 2` as p2 through the normal voxel contract.
- `make ci-fast` passed in the Java 21 Gradle container; draft PR #37's build and real-Paper
  smoke checks passed for implementation commit `112ce73`.
- `/review` against `code_review.md` found no P1: the sample build is placed through the
  configured batched editor, export changes have regressions, player text is kid-appropriate,
  and the Compose/images contain no committed credential.
- Pending after BB-09 integration: `make demo` must also observe
  `SCENARIOCRAFT_RESULTS_ANNOUNCED`, followed by the literal one-human-player chat check.

## Retrospective

Pending completion.

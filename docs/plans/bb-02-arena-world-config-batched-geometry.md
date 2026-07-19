# ExecPlan: BB-02 arena world, config, and batched geometry

Issue: #4
Owner: Codex
Status: In progress

## Purpose

Deliver the first playable Build Battle arena slice: a predictable superflat world,
configuration-backed arena settings, deterministic non-overlapping plots, and a temporary
`/battle start` command whose clear-and-wall block edits are bounded by the configured
per-tick budget. This needs an ExecPlan because it crosses Paper world lifecycle, command
handling, configuration validation, geometry, scheduling, and acceptance evidence from a
real server boot.

## Progress

- [x] Define the smallest end-to-end slice.
- [ ] Implement with tests.
- [ ] Capture the issue's acceptance evidence.
- [ ] Complete `/review` and resolve P1 findings.
- [ ] Record the retrospective.

## Decision Log

| Date | Decision | Why |
| --- | --- | --- |
| 2026-07-19 | Keep all arena implementation under a `buildbattle` package and avoid a general scenario abstraction. | BB-02 is scenario-specific, and the repository contract reserves a platform seam for BB-14. |
| 2026-07-19 | Represent clears and walls as lazy cuboid fill operations consumed by one bounded queue. | The command can enqueue a constant number of operations while every tick performs at most `blocks-per-tick` block mutations. |
| 2026-07-19 | Place plots on a deterministic centered grid using center-to-center `plot-spacing`. | This makes bounds, spacing, and non-overlap directly testable for every configured plot count. |
| 2026-07-19 | Treat packaged `config.yml` as the source of numeric and timing defaults, then validate all values while loading. | Defaults remain operator-visible and no gameplay timing, deck, plot, or batch values are hidden in code. |

## Surprises & Discoveries

- The delivery worktree began detached at `origin/main`; it was clean, so the base branch
  could be fast-forwarded and the issue branch created without carrying unrelated changes.

## Acceptance evidence

Planned evidence:

- `make ci-fast` for compilation and unit tests covering plot bounds, spacing, overlap,
  batching limits, and packaged configuration.
- Paper smoke-test output showing `battle_world` creation and successful plugin enable.
- A real server command run showing `/battle start` enqueues two plots and completes the
  batched mutations at `blocks-per-tick: 4000` without a watchdog stall.

## Retrospective

To be completed after implementation, local review, and acceptance verification.

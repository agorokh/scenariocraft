# ExecPlan: Active arena mutation policy

Issue: #25
Owner: Codex
Status: In progress

## Purpose

Protect active Build Battle plots from environmental, automated, and non-owner edits without
breaking legitimate contestant building. This work needs an ExecPlan because it audits a broad
Paper event surface, extracts existing protection out of round orchestration, adds missing event
families, and establishes a checked-in regression matrix as the durable review contract.

## Progress

- [x] 2026-07-20: Define the smallest end-to-end slice: extract an active-arena policy and
  listener while preserving the PR #23 protections.
- [ ] Implement the policy/listener and checked-in event-family matrix with parameterized tests.
- [ ] Capture the issue's acceptance evidence.
- [ ] Complete `/review` and resolve P1 findings.
- [ ] Record the retrospective.

## Decision Log

| Date | Decision | Why |
| --- | --- | --- |
| 2026-07-20 | Keep the mutation policy and listener in the Build Battle package and inject round state through narrow queries. | Issue #25 requires a dedicated seam independent of relocation hardening and explicitly forbids a premature general scenario framework. |
| 2026-07-20 | Treat the checked-in matrix and parameterized policy tests as two views of the same event-family contract. | Reviewers need a readable audit while tests must prevent deny families and boundary rules from drifting. |
| 2026-07-20 | Permit ordinary in-plot physics and cancel only documented unsafe mutation families or boundary crossings. | Wholesale physics cancellation would break normal builds and violates the acceptance criteria. |

## Surprises & Discoveries

- The delivery worktree began on an older detached `main`; `origin/main` had advanced through
  PR #33 and already contains the PR #23 handlers that issue #25 asks this work to extract.

## Acceptance evidence

Pending implementation, targeted tests, `make ci-fast`, Paper smoke, and GitHub Actions results.

## Retrospective

Pending completion.

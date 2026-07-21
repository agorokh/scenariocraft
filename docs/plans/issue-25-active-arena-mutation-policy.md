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
- [x] 2026-07-20: Implement the policy/listener and checked-in event-family matrix with
  parameterized tests.
- [x] 2026-07-20: Capture local acceptance evidence; pushed CI and Paper smoke remain.
- [x] 2026-07-20: Complete `/review` against `code_review.md`; no P1 findings remained.
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
- Homebrew Java 21 was installed but not linked into the desktop shell. Setting `JAVA_HOME` to
  `$(brew --prefix openjdk@21)/libexec/openjdk.jdk/Contents/Home` restored the required toolchain.
- A root `./gradlew test --tests ...` filter also reached the `renderer` subproject and failed
  because that subproject has no matching Build Battle tests. The correct targeted form is
  `./gradlew :test --tests ...`.
- The first policy fixture accidentally constructed `PlotBounds(0, 0, 4, 4)` instead of
  `PlotBounds(0, 4, 0, 4)`, making every intended in-plot point out of bounds. The failing allow
  matrix exposed the coordinate-order mistake before implementation was pushed.

## Acceptance evidence

- `./gradlew :test --tests 'io.github.agorokh.scenariocraft.buildbattle.ActiveArenaMutationPolicyTest' --tests 'io.github.agorokh.scenariocraft.buildbattle.RoundControllerTest'`
  passes, covering every deny-capable family, boundary crossings, non-owners, representative
  allows, newly audited Paper events, and the explicit block-physics allow.
- `make ci-fast` passes on Java 21 after the extraction and regression additions.
- Source review confirms the only loop introduced walks Paper's bounded piston moved-block list;
  no handler scans or mutates arena blocks.
- GitHub Actions build and Paper boot smoke: pending pushed implementation.

## Retrospective

Pending completion.

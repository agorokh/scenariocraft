# ExecPlan: Active arena mutation policy

Issue: #25
Owner: Codex
Status: Complete

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
- [x] 2026-07-20: Record the retrospective after GitHub's real Paper smoke passed.
- [x] 2026-07-20: Address current-head review feedback by including the piston head in
  boundary checks and adding a build-ceiling regression.
- [x] 2026-07-20: Correct retraction containment to follow pulled blocks toward the piston
  and add a legitimate inward-retraction regression.

## Decision Log

| Date | Decision | Why |
| --- | --- | --- |
| 2026-07-20 | Keep the mutation policy and listener in the Build Battle package and inject round state through narrow queries. | Issue #25 requires a dedicated seam independent of relocation hardening and explicitly forbids a premature general scenario framework. |
| 2026-07-20 | Treat the checked-in matrix and parameterized policy tests as two views of the same event-family contract. | Reviewers need a readable audit while tests must prevent deny families and boundary rules from drifting. |
| 2026-07-20 | Permit ordinary in-plot physics and cancel only documented unsafe mutation families or boundary crossings. | Wholesale physics cancellation would break normal builds and violates the acceptance criteria. |
| 2026-07-20 | Check the piston head in the facing direction, but check moved blocks in their actual movement direction; retraction uses the opposite face. | Paper's moved-block list omits a newly extended head when the piston pushes only air, while retract events report the facing direction even though pulled blocks move toward the piston. |

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
- Current-head review found that Paper's piston moved-block list does not include the new head
  when extending into air. The listener now checks that bounded extra destination explicitly.
- The follow-up current-head pass caught that `BlockPistonRetractEvent#getDirection()` reports
  the facing direction rather than the pulled blocks' movement direction.

## Acceptance evidence

- `./gradlew :test --tests 'io.github.agorokh.scenariocraft.buildbattle.ActiveArenaMutationPolicyTest' --tests 'io.github.agorokh.scenariocraft.buildbattle.RoundControllerTest'`
  passes, covering every deny-capable family, boundary crossings, non-owners, representative
  allows, newly audited Paper events, and the explicit block-physics allow.
- `make ci-fast` passes on Java 21 after the extraction and regression additions.
- `RoundControllerTest.activeArenaCancelsPistonHeadExtensionAboveBuildHeight` proves an empty
  moved-block list cannot extend a piston head beyond `maxBuildY`.
- `RoundControllerTest.activeArenaAllowsPistonRetractionTowardThePiston` proves a sticky piston
  can pull an edge block inward without a false boundary denial.
- Source review confirms the only loop introduced walks Paper's bounded piston moved-block list;
  no handler scans or mutates arena blocks.
- GitHub Actions `build` and real Paper 1.21.11 `smoke` both passed for the pushed
  implementation head; the final ExecPlan-only head reruns the same required checks.

## Retrospective

The active-arena protections now have a dedicated Build Battle policy/listener, a readable event
contract, and parameterized regression coverage instead of continuing to grow round orchestration.
The implementation preserved direct owner edits, added narrow same-plot rules for bounded
environmental lists, explicitly denied unsafe indirect families, and left block physics alone.
Future event-surface discoveries should add one matrix row, one policy family, one listener
mapping, and one deny/allow regression rather than reopening `RoundController`.

# ExecPlan: BB-11 judge eval suite

Issue: #13
Owner: Codex session 019f85ba-261c-74e3-8ec9-1074d0ec72b1
Status: In progress

## Purpose

Deliver a repeatable regression suite for the LLM judge so maintainers can detect scoring,
tone, and response-structure regressions without making network calls in CI, while retaining
an explicit live mode for checking the production judge. This needs an ExecPlan because the
work spans fixture provenance, an assertion format, live and recorded execution, anonymous
design-council validation data, CI integration, and a real prompt or rubric correction proven
by a failing eval.

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
| 2026-07-21 | Keep eval assets under `evals/`, with one self-contained directory per case and a Java runner owned by the standalone `judge` module. | The issue freezes the fixture layout, while reusing judge-domain parsing and validation avoids a second implementation of verdict semantics. |
| 2026-07-21 | Make recorded verdicts the deterministic CI input and reserve the production judge invocation for explicit live mode. | CI must be green without credentials or network access, but the same assertion engine must validate live GPT-5.6 output. |
| 2026-07-21 | Treat the two family-round fixtures as anonymized repository data with provenance labels rather than participant names. | Acceptance requires real-round coverage and prohibits names in ground-truth records. |
| 2026-07-21 | First encode the intended assertion suite against pre-fix recorded output, then make the smallest production prompt or rubric correction needed to turn that failure green. | The required real fix must be traceable to observed eval evidence rather than invented after implementation. |

## Surprises & Discoveries

- The issue calls for at least six seed shapes but separately requires at least two cases mined
  from real family rounds. The suite therefore needs at least seven cases unless the named
  "real round" seed is expanded to two distinct family-round cases.

## Acceptance evidence

Pending implementation. Evidence will include the recorded-mode command and pass table, the
failing-before/fixed-after assertion, fixture provenance counts, anonymous ground-truth
validation, focused unit tests, and the repository `make ci-fast` gate.

## Retrospective

Pending completion.

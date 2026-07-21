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
- [x] Implement the recorded/live runner, synthetic seed cases, and focused judge fix with tests.
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
| 2026-07-21 | Use the interior-only case to inspect the pre-fix multimodal request contract, then make the smallest production prompt correction needed to make cutaway evidence identifiable. | The required real fix must be traceable to an observed eval boundary failure rather than invented after implementation. |
| 2026-07-21 | Label all seven canonical views in the GPT request and explicitly identify the plan and center cross-sections as interior evidence. | The interior-only seed exposed that filenames were retained locally but discarded at the model boundary, so the judge could not distinguish a cutaway from an exterior view. |
| 2026-07-21 | Keep synthetic responses explicitly marked as hand-authored goldens; require family cases and council records to bind round, plot, commit, voxel, and response hashes. | Synthetic fixtures exercise the assertion engine but must never be presented as live family evidence. |

## Surprises & Discoveries

- The issue calls for at least six seed shapes but separately requires at least two cases mined
  from real family rounds. The suite therefore needs at least seven cases unless the named
  "real round" seed is expanded to two distinct family-round cases.
- The first interior-only eval design failed before live scoring: at pre-fix head `b2fb44d`,
  `OpenAiPersonaJudgeTest.requestUsesSevenImagesSharedRubricAndStrictReasonThenScoresSchema`
  proved the request contained one task block plus seven bare images. Nothing told GPT-5.6 that
  `cut-x.png` and `cut-z.png` were center cross-sections, so an interior-detail assertion could
  not distinguish model failure from missing input semantics. Commit `7683426` fixed the
  production request; the focused test now requires seven label/image pairs in canonical order,
  explicit interior-evidence labels, and the unchanged seven image payloads.
- Co-review found that the first hand-authored interior fixture was a solid 3x3x3 cube while its
  golden verdict described a furnished room. The case now contains a hollow 5x3x5 stone shell
  with air space, oak furnishing, and a red-wool centerpiece intersecting the center cutaways.
- Co-review also caught two successful recorded verdicts with no failure entry despite the
  configured three-persona panel. Every golden now contains the exact configured panel, unique
  personas, recomputed contestant/winner means, and explicit hand-authored provenance.

## Acceptance evidence

- `./evals/run.sh --dry-run` passes all six synthetic seed cases and prints the required table;
  score-band, tone, schema, and cross-case ordering assertions are active.
- `python3 -m unittest discover -s evals/tests -p 'test_*.py'` passes seven focused runner tests,
  including duplicate-key rejection, aggregate verification, ordering failure, and anonymous
  checksum-bound council-record validation.
- `OpenAiPersonaJudgeTest.requestUsesSevenImagesSharedRubricAndStrictReasonThenScoresSchema`
  proves the production request labels all seven views and identifies both center cross-sections
  as interior evidence.
- `make ci-fast` passed after each pushed implementation slice on Java 21. Family-round and
  adult-supervised council evidence remains the final acceptance item and is intentionally not
  inferred from synthetic or robot-generated artifacts.

## Retrospective

Pending completion.

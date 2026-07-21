# ExecPlan: BB-11 judge eval suite

Issue: #13
Owner: Codex session 019f85ba-261c-74e3-8ec9-1074d0ec72b1
Status: Ready for review under operator-approved deadline exception

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
- [x] Complete parallel review and resolve all actionable code and fixture findings.
- [x] Record the deadline scope exception for unavailable family-round evidence.
- [ ] Capture the deferred family-round acceptance evidence after review.
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
| 2026-07-21 | Bind family provenance to repository paths whose content is read and hashed from the cited commit, and fetch full history in CI. | Merely proving that a SHA names some commit does not prove the reviewed round artifacts existed in it. |
| 2026-07-21 | Move PR #40 to ready without the unavailable family-round exports and child-auditor records. | With one hour remaining, the operator explicitly accepted this scope exception so external review and CI could proceed; synthetic fixtures remain labeled honestly and are not presented as family evidence. |
| 2026-07-21 | Make the default/release runner fail closed and expose the deadline exception only as `--allow-synthetic-only`. | The reviewed synthetic slice can merge without encoding missing family evidence as a silent permanent bypass; PR #40 no longer closes #13. |

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
- The first recorded run through the production-Java validator failed on `single-block` with
  `Recorded response failed production validation`. The valid comment named its literal strength
  as a "stone block," but `JudgeVerdict.BUILD_FEATURE` omitted `block`, so the kid-safety gate
  rejected honest praise for the required single-block edge case. Adding that concrete feature
  and `JudgeVerdictTest.acceptsOneBlockAsTheConcreteStrengthInAnEdgeCase` turned the same eval
  green without weakening sentence, cruelty, or constructive-guidance checks.
- Final co-review rejected the original synthetic calibration for three cases: a sparse sealed
  cabin was praised as richly furnished, a six-block treehouse received above-anchor creativity,
  and a compact ship was described as layered and complete. The score bands, ordering assertions,
  verdicts, and aggregate means now describe the actual committed geometry.
- Provenance co-review found that resolving `artifact_commit` alone accepted an unrelated commit.
  Family metadata now names the voxel and response paths, verifies both hashes from the cited
  commit, rejects unexpected ground-truth files, and uses full Git history in the CI build job.
- Judge-parity co-review found that duplicating persona-YAML, sentence, and tone parsing in Python
  repeatedly lagged the production Java contract. The runner now delegates those rules to the
  production validator and keeps Python focused on eval-specific bands, bans, ordering, provenance,
  aggregates, and field order.
- Review also caught that the empty fixture used a zero-height volume, which could make a live
  renderer fail before judging. It now uses a positive-height all-air volume while remaining an
  actually empty build.

## Acceptance evidence

- `./evals/run.sh --dry-run --allow-synthetic-only` passes all six synthetic seed cases and prints the required table;
  score-band, tone, schema, and cross-case ordering assertions are active.
- `python3 -m unittest discover -s evals/tests -p 'test_*.py'` passes eleven focused runner tests,
  including duplicate-key rejection, aggregate verification, ordering failure, and anonymous
  checksum-bound council-record validation.
- `OpenAiPersonaJudgeTest.requestUsesSevenImagesSharedRubricAndStrictReasonThenScoresSchema`
  proves the production request labels all seven views and identifies both center cross-sections
  as interior evidence.
- The pre-fix production-parity command `./evals/run.sh --dry-run` stopped at `single-block`;
  after the focused `block` vocabulary fix, the same committed response passes both Java
  `JudgeVerdict` validation and the Python score/tone/order assertions.
- `make ci-fast` passed after each pushed implementation slice on Java 21. Family-round and
  adult-supervised council evidence remains the final acceptance item and is intentionally not
  inferred from synthetic or robot-generated artifacts.

## Retrospective

Pending completion.

# ExecPlan: Polish the Speed Build showcase

Issue: #52
Owner: Codex session `019f86de-56f3-7391-a6c6-700ac1efe89f`
Status: In progress

## Purpose

Make the How to Play page submission-ready without weakening its evidence. The walkthrough
will teach one visually coherent rainbow-volcano story using deterministic renderer output,
while the real robot round remains separately visible and truthfully labeled. Repository
counts and the Saturday-to-Tuesday timeline will be auditable from their linked sources.

## Progress

- [x] Define the smallest end-to-end slice.
- [ ] Implement with tests.
- [ ] Capture the issue's acceptance evidence.
- [ ] Complete `/review` and resolve P1 findings.
- [ ] Record the retrospective.

## Decision Log

| Date | Decision | Why |
| --- | --- | --- |
| 2026-07-21 | Use one dense volcano fixture and one walls-up fixture, selecting their existing renderer views for the four-step story. | The renderer already emits seven deterministic camera views per schema-v1 fixture; two fixtures express the state change without altering the frozen schema or renderer. |
| 2026-07-21 | Keep the proof builder responsible for the compact real-round gallery, but make showcase markup static. | A newly captured proof round must still refresh its task, transcript, verdicts, block counts, and three real renders without overwriting the teaching visuals. |
| 2026-07-21 | Recount exact issue and merged-PR queries at the final commit. | An open delivery PR does not satisfy `is:pr is:merged`, so the displayed merged count must follow query truth rather than anticipated merge state. |

## Surprises & Discoveries

- The generic issue-creation skill's vault and pitfall-hub files do not exist in this
  repository, as ScenarioCraft's agent correction already documents. Issue #52 uses the
  required manual-review fallback rather than introducing that infrastructure.

## Acceptance evidence

Pending implementation. Record renderer hashes, `make ci-fast`, proof regeneration, stale-copy
searches, live GitHub counts, and 375 px / 1280 px screenshots here before completion.

## Retrospective

Pending completion.

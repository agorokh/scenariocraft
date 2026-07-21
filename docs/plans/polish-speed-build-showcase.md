# ExecPlan: Polish the Speed Build showcase

Issue: #52
Owner: Codex session `019f86de-56f3-7391-a6c6-700ac1efe89f`
Status: Ready for review

## Purpose

Make the How to Play page submission-ready without weakening its evidence. The walkthrough
will teach one visually coherent rainbow-volcano story using deterministic renderer output,
while the real robot round remains separately visible and truthfully labeled. Repository
counts and the Saturday-to-Tuesday timeline will be auditable from their linked sources.

## Progress

- [x] Define the smallest end-to-end slice.
- [x] Implement with tests.
- [x] Capture the issue's acceptance evidence.
- [x] Complete `/review` and resolve P1 findings.
- [x] Record the retrospective.

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
- The default desktop shell still cannot discover Java. The installed Java 21 runtime at
  `/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home` runs every required gate.
- The browser's stitched full-page capture repeated the hero on this unusually tall page.
  Viewport-sized screenshots at each anchored showcase beat produced reliable, convention-sized
  evidence instead; the temporary stitched files were overwritten.

## Acceptance evidence

- `make ci-fast`: green, including `site-check`, `proof-check`, 23 round-driver tests,
  renderer golden tests, the Gradle build, Bedrock probe checks, and 6/6 dry-run eval cases.
- Two fresh renders of both fixtures: all 14 emitted views were byte-identical. The four
  committed selected-view SHA-256 values matched the fresh output:
  `b7e032d491a9` (isometric), `909d27a27fd3` (walls), `bf949ce14707`
  (cutaway), and `3d2c40f47c1` (gallery plan).
- Fixture contract: both scenes are schema v1 and `33x22x33`; the open build contains 6,031
  non-air blocks and the walled state contains 12,060.
- Browser inspection at 1280×900 and 375×812: document width equaled viewport width, all eight
  page images loaded, and steps 3–7 remained inside the viewport with no horizontal overflow.
- Screenshots: `docs/screenshots/polish-showcase-desktop-1280*.png` and
  `docs/screenshots/polish-showcase-mobile-375*.png` cover steps 3–6 at both widths.
- Final July 21 query audit before commit: 27 public issues and 25 merged pull requests. The open
  delivery PR is intentionally not counted by `is:pr is:merged`. Git records the first commit at
  `2026-07-18T15:16:20-07:00`.
- `grep -rn "23 public\|21 merged\|three calendar" README.md SUBMISSION.md site/`: no matches.
- Stylesheet digest: `9ccb804b63ed`, matching the `styles.css?v=` cache-buster.
- `/review` against `code_review.md`: no P1. The change adds no judge/export behavior, gameplay
  mutation, runtime player copy, inventory GUI, timing/deck change, or credential material.

## Retrospective

The walkthrough now uses four deliberate camera beats to explain one rainbow-volcano round, while
the proof builder still owns and refreshes the separate robot evidence. Two schema-v1 fixtures are
enough: the renderer's isometric, plan, and cut-Z outputs express the challenge, privacy, hidden
interior, and reveal without changing the frozen format. Keeping the sparse real renders as a
compact three-card gallery protects the strongest provenance claim without asking them to carry
the page's visual storytelling.

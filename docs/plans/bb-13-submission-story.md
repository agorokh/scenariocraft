# ExecPlan: BB-13 submission story

Issue: #15
Owner: Codex
Status: In progress

## Purpose

Turn the How to Play page and README into one verifiable Build Week submission story: a
judge can understand who specified, built, and referees Speed Build, then follow the canonical
quickstart to run it at home. The work is deliberately limited to submission copy, original
page art, accessibility, and claim verification; it does not add gameplay or dependencies.

## Progress

- [x] Define the smallest end-to-end slice.
- [ ] Replace the page's closing headline with the crafting-recipe story and preserve the
      existing provenance trail as small print.
- [ ] Add the README-derived home quickstart panel and reciprocal README page link.
- [ ] Complete the README and submission-requirement claim audit without duplicating the
      in-flight real-round, judge-eval, or household/device work.
- [ ] Verify links, 1280 px and 375 px layouts, contrast, accessibility, and untouched content.
- [ ] Run local CI, complete `/review`, and capture acceptance evidence.
- [ ] Record the retrospective.

## Decision Log

| Date | Decision | Why |
| --- | --- | --- |
| 2026-07-21 | Deliver this brief under the existing BB-13 issue #15. | Its submission-pack scope and README acceptance criteria own the requested story; a second issue or PR would fragment the same deliverable. |
| 2026-07-21 | Keep the judge runtime on its single configured `gpt-5.6` model and state that truthfully. | The current CLI has one static model for all personas and the persona schema accepts only `name` and `voice`; adding per-persona tiers would exceed this surgical page/submission pass. |
| 2026-07-21 | Hand-verify repository-history numbers immediately before the final push and date the claim. | A static GitHub Pages artifact cannot query GitHub without violating the no-external-request rule, and concurrent PRs can change the merged count during this session. |
| 2026-07-21 | Avoid copying proposed artifacts from PRs #40, #42, and #44. | The brief explicitly requires coordination with the judge-eval, real-round, and household/device lanes; this branch will link only to artifacts present on its actual base. |

## Surprises & Discoveries

- The design-system change is ScenarioCraft PR #41 and is already on `main`; the supplied URL
  used the issue route, but GitHub resolves the shared issue/PR number to the pull request.
- The repository currently has 22 public issues and 19 merged pull requests. Those counts are
  provisional because three related pull requests remain open while this work proceeds.
- The judge config fails closed on unknown persona keys and `OpenAiPersonaJudge` uses one static
  `gpt-5.6` model, so Sol/Terra/Luna cannot be assigned truthfully without a runtime feature.

## Acceptance evidence

To be filled with final repository queries, link checks, local CI output, responsive screenshots,
and Lighthouse accessibility results.

## Retrospective

To be completed after review and verification.

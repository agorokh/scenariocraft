# ExecPlan: BB-13 submission story

Issue: #15
Owner: Codex
Status: Ready for review

## Purpose

Turn the How to Play page and README into one verifiable Build Week submission story: a
judge can understand who specified, built, and referees Speed Build, then follow the canonical
quickstart to run it at home. The work is deliberately limited to submission copy, original
page art, accessibility, and claim verification; it does not add gameplay or dependencies.

## Progress

- [x] Define the smallest end-to-end slice.
- [x] Replace the page's closing headline with the crafting-recipe story and preserve the
      existing provenance trail as small print.
- [x] Add the README-derived home quickstart panel and reciprocal README page link.
- [x] Complete the README and submission-requirement claim audit without duplicating the
      in-flight real-round, judge-eval, or household/device work.
- [x] Verify links, 1280 px and 375 px layouts, contrast, accessibility, and untouched content.
- [x] Run local CI, complete `/review`, and capture acceptance evidence.
- [x] Record the retrospective.

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
- The default desktop shell has no discoverable Java runtime. The required Homebrew Java 21 at
  `/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home` runs the full gate cleanly.
- The first Lighthouse pass scored 96 and caught 4.04:1 contrast on two blue recipe icons.
  Reusing the design system's deepslate surface raised the final accessibility score to 100
  with no failed contrast audit.
- No public YouTube demo URL exists in the repository or its issue/PR history. The submission
  file and shot list flag that operator-owned recording/upload step instead of inventing a link.
- Current-head review found that the optional live-link path could attempt a protocol-relative
  URL, follow a GitHub redirect to a non-allowlisted host, or accept an existing local resource
  outside `site/`. Explicit scheme/host validation, a redirect guard, containment checks, and
  three standard-library regression tests now close those holes.

## Acceptance evidence

- `python3 scripts/site_check.py --check-external`: `SITE_CHECK_OK` and
  `SITE_EXTERNAL_LINKS_OK`; all local resources, anchors, and allowlisted GitHub links resolve.
- `python3 -m unittest discover -s scripts -p 'test_*.py'`: four safety regressions pass for
  explicit HTTPS GitHub links, `site/` path containment, redirect allowlisting, and remote
  `<base href>` rejection.
- Browser metrics at 1280 px and 375 px: document scroll width exactly equals viewport width;
  hero, all seven steps, judge stage, home panel, and recipe stay inside the mobile viewport;
  all four images load at their natural sizes with non-empty alt text.
- Recipe interaction: all nine ingredients are present; the Codex card opens by click/tap and
  its tooltip is visible at both widths without horizontal overflow.
- Screenshots: `docs/screenshots/bb-13-desktop-1280.png`,
  `docs/screenshots/bb-13-desktop-recipe-1280.png`, and
  `docs/screenshots/bb-13-mobile-375.png`.
- Lighthouse 13.4.1 accessibility: **100/100**, with no failed accessibility audits.
- `JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home make ci-fast`:
  `BUILD SUCCESSFUL` across 23 actionable tasks.
- `git diff --check`: clean. The canonical logo has no diff, the full footer block is
  byte-for-byte unchanged, and every honesty label, disclaimer, skip link, and reduced-motion
  rule remains present.
- Public-history query on July 21, 2026: 22 issues and 19 merged pull requests; first commit
  July 18, three calendar days before verification.
- `/review` against `code_review.md`: no P1. The patch changes no judge/export logic, block
  mutation, player-facing runtime output, timing/deck behavior, GUI, or credential handling.

## Retrospective

The page now tells one coherent story from play through installation to authorship, while the
README supplies the longer claim trail and submission details. Native `details` elements keep
the ingredient cards usable by tap and keyboard without JavaScript, while CSS hover labels make
the desktop recipe quick to scan. The main scope decision held: this branch links to the
in-flight eval work but does not copy it, does not claim Mineflayer before its PR lands, and does
not fabricate tier routing or a video URL. The only remaining submission action is external to
the repository: record/upload the public YouTube demo and place its URL in the three named
locations before 5:00 PM PDT.

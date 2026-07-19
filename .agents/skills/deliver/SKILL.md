---
name: deliver
description: Take one ScenarioCraft Build Week issue from specification to a pull request ready for external review. Use when asked to deliver an issue from the build-week milestone, such as "deliver 4 (BB-02)" or "deliver 8 (BB-06)."
---

# Deliver a ScenarioCraft issue

**Job:** Take one issue from spec to a PR ready for external review.

**Inputs:** An issue number on the build-week milestone.

**Outputs:** One ready-for-review PR satisfying that issue's acceptance criteria, with its
ExecPlan updated and the session ID recorded.

## Steps

1. Read, in order: issue [#2](https://github.com/agorokh/scenariocraft/issues/2)
   (working agreement), `AGENTS.md`, `code_review.md`, then the target issue. Treat the
   issue's acceptance criteria as the definition of done.
2. If the issue is multi-hour, create or extend `docs/plans/<feature>.md` from
   `docs/plans/TEMPLATE.md` before writing code. Record the intended approach in the
   Decision Log.
3. Create branch `codex/<issue>-<slug>`. Make and push the first scoped commit — the
   ExecPlan for multi-hour work, or the smallest implementation slice otherwise — before
   opening a draft PR so GitHub has a branch difference to review. Keep the PR in draft
   while implementation and verification continue.
4. Implement to the acceptance criteria only. If a spec is wrong or ambiguous, comment on
   the issue and stop; do not silently expand scope.
5. Write tests alongside the change. Run `make ci-fast` locally before pushing.
6. Update the ExecPlan: check off Progress and record anything that failed or surprised you
   under Surprises & Discoveries. Keep the scar tissue; it is the point.
7. Put the issue number, the acceptance evidence each criterion asks for, and the Codex
   session ID in the PR description. Confirm local CI and the latest pushed head's GitHub
   checks are green; if they are not, push fixes while the PR remains a draft. Then mark the
   PR ready for review.
   Reviewers do not run on drafts, so a PR left in draft will sit with no findings and that
   is not the same as a clean review.
   Marking it ready starts external review. Hand off to the resolve-pr skill to drive the PR
   to merged; do not run the CI-repair, review-resolution, or merge loop here.
8. If the same correction has now been needed twice, append a dated rule to
    `AGENTS.md` → Corrections in this PR.

## Do not

- Add gameplay outside the issue's scope.
- Add inventory GUIs.
- Mutate blocks unbatched on the main thread.
- Reference infrastructure that does not exist in this repository: memory bridges,
  workspaces, code-edit gates, self-hosted reviewers, or resolve-gates.
- Commit secrets.

## Examples

- `deliver 4 (BB-02)`
- `deliver 8 (BB-06)`

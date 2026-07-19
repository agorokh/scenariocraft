---
name: resolve-pr
description: Drive one open ScenarioCraft pull request through CI repair, review-thread resolution, final review, and squash merge. Use after opening a PR or when asked to fix CI or review feedback and continue until the PR is green, clean, and merged.
---

# Resolve a ScenarioCraft pull request

**Job:** Drive one open pull request to green-and-clean, then merge it.

**Inputs:** A PR number.

**Outputs:** A merged PR, or an escalation comment explaining what a human must decide.

## Prerequisites

`gh auth status` must succeed, with repository access and pull-request write scope.

## Steps

1. Inspect the PR:

   ```sh
   gh pr view <P> --repo agorokh/scenariocraft \
     --json number,state,isDraft,headRefOid,statusCheckRollup,mergeable,reviewDecision,comments,reviews
   ```

2. If `isDraft` is true, mark the PR ready because reviewers do not run on drafts:

   ```sh
   gh pr ready <P> --repo agorokh/scenariocraft
   ```

3. If CI is red, read the failing job's logs, fix the cause rather than the symptom,
   commit, and push.

4. Check review threads with GraphQL because REST does not report resolution state:

   ```sh
   gh api graphql -f query='query($o:String!,$n:String!,$p:Int!,$after:String){repository(owner:$o,name:$n){pullRequest(number:$p){reviewThreads(first:50,after:$after){pageInfo{hasNextPage endCursor} nodes{id isResolved isOutdated path line comments(first:100){pageInfo{hasNextPage endCursor} nodes{author{login} body}}}}}}}' -f o=agorokh -f n=scenariocraft -F p=<P>
   ```

   Use `-F` for the PR number. `-f` sends a string and fails the GraphQL integer
   validation. If `reviewThreads.pageInfo.hasNextPage` is true, repeat the query with
   `-f after=<END_CURSOR>` and continue until it is false.

   Page every thread's comments before classifying it. When
   `comments.pageInfo.hasNextPage` is true, use the thread `id` and comment cursor:

   ```sh
   gh api graphql -f query='query($id:ID!,$after:String!){node(id:$id){... on PullRequestReviewThread{comments(first:100,after:$after){pageInfo{hasNextPage endCursor} nodes{author{login} body}}}}}' -f id=<THREAD_ID> -f after=<END_CURSOR>
   ```

   Repeat until `comments.pageInfo.hasNextPage` is false so every comment author is
   considered.

5. Classify feedback as gating or advisory:

   - Unresolved threads from humans and from the repository's configured review app gate
     merge.
   - Comments from `gemini-code-assist[bot]`, `qodo-code-review[bot]`, and Copilot are
     advisory. Fix an advisory finding when it is obviously correct; otherwise reply once
     and move on. Advisory feedback never blocks merge.
   - If a human replies inside an advisory thread, treat that thread as gating.

6. Address every gating thread with either a fix and push or a factual reply explaining why
   the finding does not apply. Never silently resolve a thread.

7. Run `/review` against `code_review.md` and fix every P1 introduced by this PR.

8. After any push, record the new `headRefOid`. A review is pending until the configured
   reviewer has posted its review result for that exact head SHA. Wait for the reviewer's
   end-to-end pass and then return to step 1:

   ```sh
   sleep 600
   ```

   The wait may be taken by ending the turn and resuming after 600 seconds instead of
   blocking inside the session. Wait at most three cycles for the same head SHA. If the
   review is still pending after the third cycle, post an escalation comment naming the
   head SHA and the reviewer or review result that is stuck, then stop.

9. Squash-merge only when required checks are green on the head SHA, no gating thread is
   unresolved, every P1 is fixed, and the configured reviewer has posted a completed
   review for that head SHA. A green check with no posted review for the current head SHA
   is unfinished, not clean, and must not be merged:

   ```sh
   gh pr merge <P> --repo agorokh/scenariocraft --squash
   ```

## Escalate

Comment on the PR with the evidence and the decision a human must make, then stop, when:

- The same root cause fails CI three times.
- A security or dependency tradeoff needs a human decision.
- Branch protection or a force-push would be required.
- `gh` cannot resolve PR state after retries.

## Guardrails

- Name the repository explicitly on every `gh` call. Pass
  `--repo agorokh/scenariocraft` to commands that support it; for `gh api graphql`, bind the
  `agorokh/scenariocraft` owner and repository variables explicitly as shown above.
- Never force-push.
- Never commit secrets.
- Keep changes scoped to the PR's issue. Open a follow-up issue for anything else.
- Do not reference infrastructure that does not exist in this repository.

---
name: resolve-pr
description: Drive one open ScenarioCraft pull request through CI repair, review-thread resolution, final review, and squash merge. Use after opening a PR or when asked to fix CI or review feedback and continue until the PR is green, clean, and merged.
---

# Resolve a ScenarioCraft pull request

**Job:** Drive one open pull request to green-and-clean, then merge it.

**Inputs:** A PR number.

**Outputs:** A merged PR, or an escalation comment explaining what a human must decide.

## Prerequisites

`gh auth status` must succeed, with repository access, pull-request write scope, and
Actions/checks read access.

## Steps

1. Confirm that the worktree has no unrelated changes, check out the requested PR head, and
   verify that local `HEAD` matches it before editing or pushing:

   ```sh
   git status --short
   gh pr checkout <P> --repo agorokh/scenariocraft
   test "$(git rev-parse HEAD)" = \
     "$(gh pr view <P> --repo agorokh/scenariocraft --json headRefOid --jq .headRefOid)"
   ```

2. Inspect the PR:

   ```sh
   gh pr view <P> --repo agorokh/scenariocraft \
     --json number,state,isDraft,headRefOid,statusCheckRollup,mergeable,reviewDecision,comments,reviews
   ```

   If `isDraft` is true, stop and hand it back to its author or the `deliver` skill.
   `deliver` owns the draft-to-ready transition after CI is green; `resolve-pr` starts once
   external review has begun.

3. If CI is pending, wait 60 seconds and reinspect it, for at most three cycles; if it is
   still pending after the third cycle, escalate instead of guessing. If CI is red, read
   the failing job's logs, fix the cause rather than the symptom, commit, and push.

4. Fetch the complete feedback inventory. Page top-level conversation comments, formal
   reviews, and inline comments:

   ```sh
   gh api --paginate 'repos/agorokh/scenariocraft/issues/<P>/comments?per_page=100'
   gh api --paginate 'repos/agorokh/scenariocraft/pulls/<P>/reviews?per_page=100'
   gh api --paginate 'repos/agorokh/scenariocraft/pulls/<P>/comments?per_page=100'
   ```

   Check review threads with GraphQL because REST does not report resolution state:

   ```sh
   gh api graphql -f query='query($o:String!,$n:String!,$p:Int!,$after:String){repository(owner:$o,name:$n){pullRequest(number:$p){reviewThreads(first:50,after:$after){pageInfo{hasNextPage endCursor} nodes{id isResolved isOutdated path line comments(first:100){pageInfo{hasNextPage endCursor} nodes{author{login} body}}}}}}}' -f o=agorokh -f n=scenariocraft -F p=<P>
   ```

   Use `-F` for the PR number. `-f` sends a string and fails the GraphQL integer
   validation. Omit the `after` flag entirely on the first call; never pass a literal
   `null`. If `reviewThreads.pageInfo.hasNextPage` is true, repeat the query with
   `-f after=<END_CURSOR>` using the returned cursor string and continue until it is false.

   Page every thread's comments before classifying it. When
   `comments.pageInfo.hasNextPage` is true, use the thread `id` and comment cursor:

   ```sh
   gh api graphql -f query='query($id:ID!,$after:String!){node(id:$id){... on PullRequestReviewThread{comments(first:100,after:$after){pageInfo{hasNextPage endCursor} nodes{author{login} body}}}}}' -f id=<THREAD_ID> -f after=<END_CURSOR>
   ```

   Repeat until `comments.pageInfo.hasNextPage` is false so every comment author is
   considered.

5. Classify every feedback item:

   - Unresolved threads from humans and from the repository's configured review app gate
     merge.
   - A top-level `CHANGES_REQUESTED` review gates merge even when it has no inline thread.
     Address it and confirm that it has been dismissed or superseded before merging.
   - Fix every actionable finding regardless of author or severity. For non-actionable or
     inapplicable feedback, post one factual reply before marking it resolved.
   - If a human replies inside an advisory thread, treat that thread as gating.

6. Address every thread with either a fix and push or a factual reply explaining why the
   finding does not apply. After the fix or reply is visible, resolve the thread explicitly:

   ```sh
   gh api graphql -f query='mutation($id:ID!){resolveReviewThread(input:{threadId:$id}){thread{id isResolved}}}' -f id=<THREAD_ID>
   ```

   "Never silently resolve" means never resolve without a visible fix or factual reply; it
   does not mean leaving an addressed thread unresolved. Verify `isResolved` after the
   mutation. If it remains false after three attempts, post an escalation comment naming
   the thread and stop.

7. Run `/review` against `code_review.md` and fix every P1 introduced by this PR.

8. After any push, record the new `headRefOid`. This repository's external reviewer is
   `ws-ops-cursor-reviewer[bot]`; its authoritative signal is a formal review whose
   `commit_id` equals that SHA. Fetch every review page and filter it explicitly:

   ```sh
   REVIEWED_SHA="$(gh pr view <P> --repo agorokh/scenariocraft \
     --json headRefOid --jq .headRefOid)"
   gh api --paginate --slurp \
     'repos/agorokh/scenariocraft/pulls/<P>/reviews?per_page=100' |
     jq --arg sha "${REVIEWED_SHA}" \
       'add | map(select(.user.login == "ws-ops-cursor-reviewer[bot]" and .commit_id == $sha))'
   ```

   An empty result means the review is pending. Before each wait, report the head SHA and
   cycle number so the operator can see progress and cancel the sleep with an interrupt:

   ```sh
   RESOLVE_PR_POLL_INTERVAL_SECONDS="${RESOLVE_PR_POLL_INTERVAL_SECONDS:-600}"
   printf 'Waiting for review of %s (cycle %s/3; interrupt to cancel)\n' \
     "${REVIEWED_SHA}" "<CYCLE>"
   sleep "${RESOLVE_PR_POLL_INTERVAL_SECONDS}"
   ```

   The default interval is the observed 600-second reviewer latency. The operator may
   override it with a positive integer. Wait at most three cycles for the same head SHA, so
   the default total timeout is 1,800 seconds. End the turn and resume later only when the
   host provides a scheduled-resume mechanism; otherwise keep a long-lived session and run
   the sleep. If the reviewer is known to be unavailable, or the review is still pending
   after the third cycle, post an escalation comment naming the head SHA and the reviewer or
   review result that is stuck, then stop.

9. Squash-merge only when required checks are green on the head SHA, no gating thread is
   unresolved, every P1 is fixed, the external reviewer has posted a completed review for
   that head SHA, and `reviewDecision` is not `CHANGES_REQUESTED`. Confirm every change
   request on that SHA was dismissed or superseded by a later approving review. A green
   check with no posted review for the current head SHA is unfinished, not clean, and must
   not be merged. Immediately before merging, verify all reported checks, re-read the head
   and review decision, and pin the merge to the reviewed SHA:

   ```sh
   gh pr checks <P> --repo agorokh/scenariocraft
   test "$(gh pr view <P> --repo agorokh/scenariocraft \
     --json headRefOid --jq .headRefOid)" = "${REVIEWED_SHA}"
   test "$(gh pr view <P> --repo agorokh/scenariocraft \
     --json reviewDecision --jq .reviewDecision)" != "CHANGES_REQUESTED"
   gh pr merge <P> --repo agorokh/scenariocraft --squash \
     --match-head-commit "${REVIEWED_SHA}"
   ```

## Escalate

Comment on the PR with the evidence and the decision a human must make, then stop, when:

- The same root cause fails CI three times.
- An addressed thread remains unresolved after three resolution attempts.
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

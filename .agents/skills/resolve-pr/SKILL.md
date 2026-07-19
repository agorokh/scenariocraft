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
Actions/checks read access. The installed `gh` must expose `--match-head-commit`; verify it
with `gh pr merge --help`.

## Steps

1. Authenticate, resolve the repository target, reject a dirty worktree, check out the
   requested PR head, and verify that local `HEAD` matches it before editing or pushing.
   `SCENARIOCRAFT_REPO` supports an intentional fork or renamed remote while retaining the
   canonical repository as the fallback:

   ```sh
   gh auth status
   gh pr merge --help | grep -q -- '--match-head-commit'
   REPO="${SCENARIOCRAFT_REPO:-agorokh/scenariocraft}"
   OWNER="${REPO%%/*}"
   NAME="${REPO#*/}"
   gh repo view "${REPO}" --json nameWithOwner
   test -z "$(git status --porcelain)"
   gh pr checkout <P> --repo "${REPO}"
   test "$(git rev-parse HEAD)" = \
     "$(gh pr view <P> --repo "${REPO}" --json headRefOid --jq .headRefOid)"
   ```

2. Inspect the PR:

   ```sh
   gh pr view <P> --repo "${REPO}" \
     --json number,state,isDraft,headRefOid,statusCheckRollup,mergeable,reviewDecision,reviewRequests,comments,reviews
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
   gh api --paginate "repos/${REPO}/issues/<P>/comments?per_page=100"
   gh api --paginate "repos/${REPO}/pulls/<P>/reviews?per_page=100"
   gh api --paginate "repos/${REPO}/pulls/<P>/comments?per_page=100"
   ```

   Check review threads with GraphQL because REST does not report resolution state:

   ```sh
   gh api graphql -f query='query($o:String!,$n:String!,$p:Int!,$after:String){repository(owner:$o,name:$n){pullRequest(number:$p){reviewThreads(first:50,after:$after){pageInfo{hasNextPage endCursor} nodes{id isResolved isOutdated path line comments(first:100){pageInfo{hasNextPage endCursor} nodes{author{login} body}}}}}}}' -f o="${OWNER}" -f n="${NAME}" -F p=<P>
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

8. On entry and after any push, record the current `headRefOid`. Fetch every formal-review
   page and filter it to that SHA:

   ```sh
   REVIEWED_SHA="$(gh pr view <P> --repo "${REPO}" \
     --json headRefOid --jq .headRefOid)"
   gh api --paginate --slurp \
     "repos/${REPO}/pulls/<P>/reviews?per_page=100" |
     jq --arg sha "${REVIEWED_SHA}" \
       'add | map(select(.commit_id == $sha)) | map({user: .user.login, state, body})'
   ```

   Reject `DISMISSED` and `CHANGES_REQUESTED` as completion signals. `APPROVED` or
   `COMMENTED` means the pass posted, but every finding in its body still must be addressed.
   A nonempty `reviewRequests` list means review is pending. When the repository has no
   required or requested reviewer, wait one full cycle for asynchronous findings; after
   that, absence of a formal review is non-blocking and only posted findings, unresolved
   threads, review decisions, and checks gate merge.

   Before each wait, report the head SHA and cycle number so the operator can see progress
   and cancel the sleep with an interrupt:

   ```sh
   RESOLVE_PR_POLL_INTERVAL_SECONDS="${RESOLVE_PR_POLL_INTERVAL_SECONDS:-600}"
   printf 'Waiting for review of %s (cycle %s/3; interrupt to cancel)\n' \
     "${REVIEWED_SHA}" "<CYCLE>"
   sleep "${RESOLVE_PR_POLL_INTERVAL_SECONDS}"
   ```

   The default interval is the observed 600-second reviewer latency. The operator may
   override it with a positive integer. This repository has no scheduler or automatic
   session-resume mechanism, so keep a long-lived terminal session for the sleep. Wait at
   most three cycles for the same head SHA, for a default total timeout of 1,800 seconds. If
   a required or requested reviewer is known to be unavailable, or remains pending after
   the third cycle, post an escalation comment naming the head SHA and reviewer, then stop.

9. Squash-merge only when required checks are green on the head SHA, no gating thread is
   unresolved, every P1 is fixed, no current-SHA review is `CHANGES_REQUESTED`, no review
   request is pending, and `reviewDecision` is not `CHANGES_REQUESTED`. A `DISMISSED` review
   does not count as a completed pass but does not itself gate merge. Confirm every change
   request on that SHA was dismissed or superseded by a later approving review. Immediately
   before merging, verify all reported checks, re-read the head and review decision, and pin
   the merge to the reviewed SHA:

   ```sh
   gh pr checks <P> --repo "${REPO}"
   test "$(gh pr view <P> --repo "${REPO}" \
     --json headRefOid --jq .headRefOid)" = "${REVIEWED_SHA}"
   test "$(gh pr view <P> --repo "${REPO}" \
     --json reviewDecision --jq .reviewDecision)" != "CHANGES_REQUESTED"
   gh pr merge <P> --repo "${REPO}" --squash \
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

- Name the resolved `REPO` explicitly on every `gh` call. For `gh api graphql`, bind its
  `OWNER` and `NAME` variables explicitly as shown above.
- Never force-push.
- Never commit secrets.
- Keep changes scoped to the PR's issue. Open a follow-up issue for anything else.
- Do not reference infrastructure that does not exist in this repository.

# ExecPlan: BB-08 GPT-5.6 judge CLI

Issue: #10
Owner: Codex session 019f819a-59d3-7673-bb87-779bfa73c915
Status: Complete

## Purpose

Deliver a standalone Java 21 judge that consumes a frozen schema-v1 round plus seven rendered
views per contestant, asks a three-voice GPT-5.6 council to apply one shared rubric, and
publishes kid-readable results without ever declaring a winner from an undersized council.
This needs an ExecPlan because it crosses the renderer boundary, strict YAML and JSON
contracts, multimodal API requests, retry and quorum failure behavior, deterministic offline
CI, and live acceptance evidence.

## Progress

- [x] Define the smallest end-to-end slice.
- [x] Implement with tests.
- [x] Capture the issue's acceptance evidence.
- [x] Complete `/review` and resolve P1 findings.
- [x] Record the retrospective.
- [x] 2026-07-21 resolve external review findings against the integrated BB-10 content.

Update this list as work proceeds. Add timestamps when a checkpoint is useful to the next
session.

## Decision Log

| Date | Decision | Why |
| --- | --- | --- |
| 2026-07-20 | Add an isolated `judge/` Gradle application that depends on the standalone renderer but never on Paper/Bukkit. | The CLI must run outside the server while reusing BB-07's exact seven-view implementation when a round has voxel JSON but no complete PNG set. |
| 2026-07-20 | Use the Responses API directly through Java 21 `HttpClient`, model alias `gpt-5.6`, base64 `input_image` items, and strict `text.format` JSON Schema; never serialize `temperature`. | Current official OpenAI docs confirm the alias, image-input shape, and Responses structured-output shape, while a narrow HTTP boundary makes request construction and malformed-response handling fully testable. |
| 2026-07-20 | Treat the newest issue #10 comment as the frozen BB-08/BB-10 contract: runtime reads `judge/personas.yml` and `judge/rubric.md`, while this PR commits only fixture versions plus shape/drift guards. | BB-10 owns the human-authored personas and rubric; changing or duplicating that content here would break the parallel-work boundary. |
| 2026-07-20 | Compute each persona score as the arithmetic mean of the four integer criteria and rank contestants by the mean across successful personas. | This follows the frozen verdict contract and avoids trusting a redundant model-supplied aggregate. |
| 2026-07-20 | Write partial verdicts but set `no_winner: true` and return non-zero whenever any contestant has fewer than configured `min_judges` valid verdicts after one retry per persona. | The round must fail closed rather than quietly ranking builds from unequal or one-person councils. |
| 2026-07-21 | Treat model output, response lifecycle state, and round image paths as untrusted inputs: reject cruel or strength-free comments, non-completed responses, symbolic links, and plot-ID mismatches. | Judge output is player-facing and image payloads leave the machine, so validation must fail closed before either publishing text or uploading bytes. |
| 2026-07-21 | Make the persona/rubric directory configurable with `SCENARIOCRAFT_JUDGE_CONFIG_DIR`, while preserving `judge/` as the repository-root default. | Operators can launch the installed CLI outside the repository root without weakening the frozen filenames or duplicating BB-10 content. |
| 2026-07-21 | Reject terminal-control and Unicode-format characters in manifest display fields, and cap uploaded PNGs at 10 MiB and 4096 pixels per dimension after validating their headers. | Manifest text is printed to a terminal and image bytes are fully encoded for every judge attempt; explicit bounds prevent terminal injection and unbounded heap use. |
| 2026-07-21 | Require equal successful-verdict counts before ranking, reject multi-link image inodes, and run the CI fixture from `RUNNER_TEMP` with the production content directory explicit. | A quorum floor alone does not make unequal means comparable; hard links bypass symlink-only provenance checks; fixture smoke output must not dirty the checkout. |
| 2026-07-21 | Snapshot each validated PNG into immutable judge memory before either dry-run or live judging, compare successful persona identities rather than only counts, and validate every schema-v1 manifest field. | A stable byte snapshot closes the path-validation/upload race and gives dry-run the same PNG gate; equal-sized but different panels are still incomparable; partial manifests must never reach winner publication. |
| 2026-07-21 | Replace the brittle positive-word requirement with structural two-sentence guidance plus strict hostile/negative-language rejection, classify retryable HTTP statuses, and configure connect timeout separately. | Concrete praise has unbounded valid wording, while kid safety still needs a conservative fail-closed shape; deterministic 4xx responses should not be retried; every operational timeout must be configurable. |
| 2026-07-21 | Require every live comment to pass `omni-moderation-latest` after local structural validation and before publication. | The issue still permits exactly one GPT-5.6 vision request per persona, while the official moderation endpoint provides a semantic, fail-closed safety boundary that a finite word list cannot. |
| 2026-07-21 | Document Unix `nlink` support as a runtime requirement instead of weakening the hard-link provenance gate on unsupported filesystems. | Silently skipping an unavailable ownership check would reopen the input-substitution path; operators need an explicit recovery path that preserves the fail-closed boundary. |
| 2026-07-21 | Snapshot voxel JSON behind a 16 MiB cap, cap OpenAI response bodies at 1 MiB, and remove prior result artifacts before reading a new round. | Every heap-facing input needs a bound, and a hard failure must not leave a previous winner looking current. |
| 2026-07-21 | Validate the full bounded PNG chunk stream, including CRCs, non-empty IDAT, and terminal IEND, rather than treating a valid signature/IHDR prefix as a complete image. | A truncated or corrupted file must fail before it becomes an OpenAI image payload in either live or dry-run mode. |
| 2026-07-21 | Cap each seven-image set at 16 MiB total and bound manifest bytes, text fields, and plot count; reserve the local cruelty denylist for abusive terms and leave contextual neutral wording to moderation. | Per-file limits do not bound aggregate Base64/Gson request memory, untrusted metadata must not inflate heap or prompts, and neutral phrases must not cause avoidable quorum failures. |
| 2026-07-21 | Cap retained round images at 32 MiB, decode PNG rasters, require concrete strength vocabulary plus a build feature, cap verdict text, and require fallback voxel origin/size and structural limits. | Final review showed that individually bounded inputs can still aggregate, structurally valid PNG chunks can contain unusable raster data, moderation does not prove praise, and stale voxel geometry can misattribute a build. |
| 2026-07-21 | Canonicalize and reject a symlinked round root before deleting old results, and apply local plus moderation safety checks to serialized reasoning as well as comments. | Fail-closed artifact invalidation must not become redirected deletion, and every model-generated field written to results is untrusted player-adjacent output. |
| 2026-07-21 | Preserve a primary renderer failure when temp cleanup also fails, keep the documented eight-plot limit aligned with runtime, and translate a missing voxel fallback into the operator diagnostic. | Cleanup must not erase the actionable cause, and published contracts/errors must match actual behavior. |
| 2026-07-21 | Apply `nlink` ownership only to untrusted round inputs, not process-generated temp PNGs, and inspect the complete Responses output before accepting exactly one verdict. | Voxel fallback must not depend on the system temp filesystem exposing Unix attributes, and a later refusal or duplicate output must not be hidden by an earlier valid-looking verdict. |
| 2026-07-21 | Enforce the same eight-plot ceiling in arena config and judge manifests, fail both result artifacts as a unit, cap decoded pixels, validate persona-name controls, and recalculate aggregate size from stable snapshots. | Cross-component contracts must agree, partial publication must not expose a winner after failure, and preflight metadata cannot substitute for bounds on decoded or immutable data. |
| 2026-07-21 | Use code-point comment validation, reject top-level Responses refusals, and stream-count voxel palette/block arrays before tree parsing. | UTF-16 code units miss astral format controls, refusal can appear outside content, and post-materialization limits do not prevent heap amplification. |

## Surprises & Discoveries

- The issue's initially returned comment list was empty, but the paginated comments endpoint
  exposed a newer normative BB-08/BB-10 file contract. It supersedes assumptions about who
  commits production persona and rubric content and freezes the exact verdict shape.
- `OPENAI_API_KEY` is not present in the delivery environment. Offline implementation and
  dry-run evidence can proceed, but the required live fixture output will need a key supplied
  through that environment variable before the PR can be marked ready.
- The desktop shell did not expose a default Java runtime even though Homebrew Java 21 was
  installed. Setting task-scoped `JAVA_HOME` to that existing JDK made the repository gates
  run without changing project configuration.
- The repository-wide `out/` ignore rule also matches the judge's normative `out/p<N>/`
  fixture contract. The 14 intentional PNG fixtures therefore have to be force-added while
  generated `results.json` and `results.txt` remain uncommitted run output.
- BB-10 merged while this PR was under review. Integrating current `main` made its production
  `judge/personas.yml` and `judge/rubric.md` available, so the shape and drift guard now runs
  against the real content as well as the isolated fixture.
- A reviewer-guide comment posted after the first green repair head identified two additional
  input-boundary risks: terminal control characters in manifest display text and unbounded
  image reads. Both required a second repair head and a reset of the current-head review gate.
- The first hard-link containment test exposed macOS's `/var` to `/private/var` canonical-path
  alias: resolving the round root but not the image's parent made safe renderer output appear
  outside the round. Canonicalizing both paths fixed the false rejection while retaining the
  inode link-count gate.
- The cycle-1 configured review landed just after the checkpoint and exposed that equal verdict
  counts can still hide different persona panels, dry-run had not opened PNG bytes, and image
  validation and upload were separated by a path re-open. The repair now snapshots validated
  bytes once and passes those snapshots through the council.
- The first snapshot-era hard-link test accidentally built only one of seven PNG paths, so the
  intended fallback path correctly ignored that unused hard link and reported a missing voxel
  source. Completing the seven-file set made the test exercise the actual upload-input gate.

## Acceptance evidence

- `make ci-fast` passed on Java 21 with 99 plugin tests, 13 renderer tests, and 18 judge tests
  (130 total), zero failures.
- The installed command `judge/build/install/judge/bin/judge --round
  rounds/round-20260721-193000 --dry-run` ran from a copy of the committed fixture runtime,
  produced two verdicts per contestant, means `8.75` and `6.75`, and named Alex/p1 the winner.
- `JudgeApplicationTest.twoPersonaFailuresWriteNoWinnerAndReturnNonZero` simulates both
  configured personas failing both attempts, verifies exit status 1, `no_winner: true`, no
  winner field, a clear quorum reason, and the second-attempt diagnostic.
- `JudgeApplicationTest.dryRunJudgesCommittedFixtureEndToEndWithoutNetwork` asserts the losing
  fixture comment names a genuine detail and contains no cruel fixture terms.
- `ScoresTest` pins four-criterion averaging; `JudgeCouncilTest` pins cross-persona means,
  one retry, and fail-closed quorum; `OpenAiPersonaJudgeTest` pins malformed-response
  rejection, seven image inputs, verbatim shared rubric injection, no `temperature`, and the
  reason-before-scores strict schema.
- Review against `code_review.md` found no P1: judge logic is covered by focused and
  end-to-end tests, output is kid-appropriate, no block mutation or inventory UI exists, and
  the API key is accepted only from `OPENAI_API_KEY` and never serialized or logged. Review
  also moved the request timeout behind `SCENARIOCRAFT_JUDGE_TIMEOUT_SECONDS` to preserve the
  repository's configuration-driven timing rule.
- A live run used the exact three-persona and shared-rubric files from BB-10 draft PR #29 at
  head `96e188898fc8e1c0494cbd6b7748368b30fc3e0c`. GPT-5.6 returned three valid verdicts for
  each fixture contestant with no retries or failures. Alex/p1 scored `3.8333333333333335`,
  Sam/p2 scored `1.0833333333333333`, and `results.json` named Alex/p1 the mean-based winner.
  The corresponding human output rounded the means to `3.83` and `1.08`; all six comments
  named a concrete strength and offered one kind next step.
- Review-repair focused tests passed for kid-safe comment validation, deterministic sentence
  boundaries, non-completed Responses payload rejection, sanitized HTTP diagnostics,
  cancellation without retry, symbolic-link rejection, voxel ownership, configurable content
  paths, and the merged production persona/rubric contract.
- The post-repair `make ci-fast` gate and the workflow-equivalent installed judge dry run both
  passed on Java 21; the dry run retained the expected `8.75` / `6.75` means and Alex winner.
- Additional focused tests pass for ANSI/Unicode terminal-control rejection and for oversized
  or over-dimension PNG rejection before OpenAI request construction.
- The production-content, temp-copy workflow-equivalent dry run passed with three fixture
  verdicts per contestant, `7.92` / `5.92` means, and Alex as winner; `make ci-fast` remained
  green after equal-council, hard-link, and CI workspace-isolation repairs.
- The snapshot/full-manifest repair also passed `make ci-fast` and the same production-content
  smoke. Focused coverage now includes different-persona equal-sized panels, corrupt dry-run
  PNGs, hard-linked voxel sources, stable image snapshots, Unicode model controls, configurable
  connect timeout, HTTP retry classification, and missing-config guidance.
- Official OpenAI API documentation confirmed that `POST /v1/moderations` accepts text with
  `omni-moderation-latest` and returns a boolean `results[].flagged` decision. Malformed,
  failed, or flagged moderation now consumes the same bounded persona retry as other errors.

## Retrospective

BB-08 now closes the round loop from a frozen manifest and seven PNG views to strict,
machine-readable GPT-5.6 verdicts and a kid-readable winner announcement. The renderer
fallback keeps the directory contract usable before PNGs exist, while verbatim rubric
injection, exact persona shapes, locally computed means, one retry, and per-contestant quorum
make the judging boundary testable and fail-closed. Developing against fixture content while
live-verifying the exact BB-10 draft files preserved parallel ownership without duplicating
the design council's prose. The only environment-specific friction was locating Java 21 and
loading the explicitly identified API-key env file; neither required a repository workaround.

# ExecPlan: BB-08 GPT-5.6 judge CLI

Issue: #10
Owner: Codex session 019f819a-59d3-7673-bb87-779bfa73c915
Status: In progress

## Purpose

Deliver a standalone Java 21 judge that consumes a frozen schema-v1 round plus seven rendered
views per contestant, asks a three-voice GPT-5.6 council to apply one shared rubric, and
publishes kid-readable results without ever declaring a winner from an undersized council.
This needs an ExecPlan because it crosses the renderer boundary, strict YAML and JSON
contracts, multimodal API requests, retry and quorum failure behavior, deterministic offline
CI, and live acceptance evidence.

## Progress

- [x] Define the smallest end-to-end slice.
- [ ] Implement with tests.
- [ ] Capture the issue's acceptance evidence.
- [ ] Complete `/review` and resolve P1 findings.
- [ ] Record the retrospective.

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

## Surprises & Discoveries

- The issue's initially returned comment list was empty, but the paginated comments endpoint
  exposed a newer normative BB-08/BB-10 file contract. It supersedes assumptions about who
  commits production persona and rubric content and freezes the exact verdict shape.
- `OPENAI_API_KEY` is not present in the delivery environment. Offline implementation and
  dry-run evidence can proceed, but the required live fixture output will need a key supplied
  through that environment variable before the PR can be marked ready.

## Acceptance evidence

To be filled with the exact dry-run command/output, live `results.json`, quorum failure
output and exit status, focused unit-test names, local CI result, and review result.

## Retrospective

To be completed after implementation, live verification, and review.

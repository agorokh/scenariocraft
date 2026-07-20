# ExecPlan: BB-04 secret chest and task reveal

Issue: #6
Owner: Codex
Status: In progress

## Purpose

Turn the existing NOTE_PICK phase into a playable, Bedrock-safe task reveal: choose one
eligible contestant, gate a hub chest to that picker, draw a configured task without
repeating the deck, reveal it to everyone through title and chat, and continue
automatically when the picker is away. This needs an ExecPlan because it coordinates
randomized round state, a world interaction listener, batched arena setup, Paper item
metadata, reconnect behavior, and the phase timer.

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
| 2026-07-19 | Keep picker selection, no-repeat task draws, and note-pick timeout decisions in small Paper-independent types with injectable bounded randomness. | The issue explicitly requires unit coverage for uniform picker selection, exempt filtering, deck behavior, and the AFK timer; pure logic makes those contracts deterministic. |
| 2026-07-19 | Store the selected picker and task as active-round state in `RoundController`, while keeping the task deck alive across completed rounds. | Reconnect and chest interaction need one authoritative selection during a round, and no-repeat behavior must span successive rounds until every configured task has been used. |
| 2026-07-19 | Add the hub chest to the existing arena reset plan and its per-tick mutation budget. | The repository treats block mutation outside the configured batch budget as P1, including the single chest block needed by this issue. |
| 2026-07-19 | Treat the written book as cosmetic and use chat plus title for the actual task reveal to every online player. | Book rendering is unreliable through Geyser, and the issue names chat plus title as the authoritative cross-platform path. |

## Surprises & Discoveries

- The delivery worktree started detached on the BB-02 merge. BB-03, the issue's dependency,
  had already merged as PR #21; fetching and fast-forwarding `main` supplied the required
  phase controller before the issue branch was created.

## Acceptance evidence

To be recorded after implementation and verification.

## Retrospective

To be completed after review and acceptance verification.

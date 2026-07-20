# ExecPlan: BB-04 secret chest and task reveal

Issue: #6
Owner: Codex
Status: Complete

## Purpose

Turn the existing NOTE_PICK phase into a playable, Bedrock-safe task reveal: choose one
eligible contestant, gate a hub chest to that picker, draw a configured task without
repeating the deck, reveal it to everyone through title and chat, and continue
automatically when the picker is away. This needs an ExecPlan because it coordinates
randomized round state, a world interaction listener, batched arena setup, Paper item
metadata, reconnect behavior, and the phase timer.

## Progress

- [x] Define the smallest end-to-end slice.
- [x] Implement with tests.
- [x] Capture the issue's acceptance evidence.
- [x] Complete `/review` and resolve P1 findings.
- [x] Resolve external review feedback with focused regressions.
- [x] Record the retrospective.

Update this list as work proceeds. Add timestamps when a checkpoint is useful to the next
session.

## Decision Log

| Date | Decision | Why |
| --- | --- | --- |
| 2026-07-19 | Keep picker selection, no-repeat task draws, and note-pick timeout decisions in small Paper-independent types with injectable bounded randomness. | The issue explicitly requires unit coverage for uniform picker selection, exempt filtering, deck behavior, and the AFK timer; pure logic makes those contracts deterministic. |
| 2026-07-19 | Store the selected picker and task as active-round state in `RoundController`, while keeping the task deck alive across completed rounds. | Reconnect and chest interaction need one authoritative selection during a round, and no-repeat behavior must span successive rounds until every configured task has been used. |
| 2026-07-19 | Add the hub chest to the existing arena reset plan and its per-tick mutation budget. | The repository treats block mutation outside the configured batch budget as P1, including the single chest block needed by this issue. |
| 2026-07-19 | Treat the written book as cosmetic and use chat plus title for the actual task reveal to every online player. | Book rendering is unreliable through Geyser, and the issue names chat plus title as the authoritative cross-platform path. |
| 2026-07-19 | Normalize Paper's empty `ItemStack` values to `null` before cloning or serializing inventory snapshots. | A real client exposed that Paper may return a non-null empty stack which cannot be serialized; snapshot persistence must treat it exactly like an empty slot. |
| 2026-07-19 | Cancel off-hand secret-chest events without processing them, and continue the round when cosmetic book placement fails. | Paper can emit an interaction for each hand, while the issue makes title and chat authoritative; neither an off-hand duplicate nor a prop failure should alter the reveal flow. |
| 2026-07-19 | Keep the configured task out of book NBT entirely; the written book contains only generic cosmetic text. | A neighboring double chest or item transport could expose the book inventory without interacting with the gated block. Controller state plus title/chat remains the only authoritative task channel. |

## Surprises & Discoveries

- The delivery worktree started detached on the BB-02 merge. BB-03, the issue's dependency,
  had already merged as PR #21; fetching and fast-forwarding `main` supplied the required
  phase controller before the issue branch was created.
- The first real-player smoke reached inventory capture and exposed Paper's non-null empty
  `ItemStack` representation. Normalizing empty stacks fixed the abort and gained a focused
  regression test.
- The nested `/review` command completed its substantive diff inspection but then followed
  optional global context into a non-terminating tool tail. Direct policy inspection found
  no P1 issue; the resulting hardening added secret-chest break protection and a safe
  immediate transition if scheduler submission fails.
- External review described `RoundTimer` as a scheduled task, but it is immutable countdown
  data consumed by one controller-wide repeating task. Replacing it with the BUILDING
  countdown cannot leak a NOTE_PICK task; a focused regression now proves the ownership
  handoff. The same review proposed scanning for a chest location, but `battle_world` is
  required to be superflat and the stable hub-relative chest is intentionally created by
  the bounded arena plan.
- A later review found that storing the real task in the cosmetic book made the prompt
  readable through alternate inventory access such as a neighboring double chest. The
  finalized book page is generic; the configured task never enters book NBT.

## Acceptance evidence

- `make ci-fast` passed with 55 tests and a successful plugin build.
- Deterministic unit tests cover uniform bounded picker selection, exemption filtering,
  non-picker cancellation and friendly messaging, picker reveal to all online players,
  off-hand event suppression, cosmetic-book failure fallback, build-countdown ownership,
  the no-repeat task deck, AFK auto-reveal, scheduler-rejection fallback, protected chest
  breaking, and Paper empty-stack snapshot normalization.
- Paper 1.21.11 build 132 loaded and enabled the built plugin. With a deliberately small
  arena, setup completed as 35 mutations through the configured 1,000-block tick budget,
  including the hub chest.
- Live block inspection verified the `minecraft:written_book` placement in chest slot 13;
  review hardening then removed the configured task from book NBT so only title and chat
  carry the secret prompt.
- An unattended real client received the picker announcement, waited through the
  10-second note timer, received the task through both chat and title, and observed the
  `NOTE_PICK -> BUILDING` transition.
- A two-client real-server run showed the selected picker opening the chest, both clients
  receiving the same task through chat and title, and both entering build time.
- The final Paper log contained no exception, severe error, watchdog warning, or arena
  mutation failure, and the server shut down cleanly.

## Retrospective

BB-04 now has one authoritative task-reveal path: configuration feeds a stateful random
deck, round state owns the picker and task, and chat plus title serves every client while
the book remains a fun prop. The chest is created through the existing bounded arena
pipeline and cannot be opened by the wrong player or removed. Real-server verification
was especially valuable because it found an integration-only inventory representation
that mocks did not initially reproduce; that behavior is now locked down by a regression
test.

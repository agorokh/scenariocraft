# ScenarioCraft code review

Run `/review` on every pull request. Review against the owning issue and classify any of the
following as P1 (merge-blocking):

- Judge or voxel-export logic is added or changed without tests.
- Block mutation runs on the server main thread outside the configured batched per-tick budget.
- Player-facing or judge-generated output is not kid-appropriate.
- A secret or credential is present in source, configuration, logs, fixtures, or history.

Also confirm:

- The pull request stays within one issue and meets every acceptance criterion.
- Bedrock-compatible chat, title, and bossbar interactions are preserved; no inventory GUI was
  introduced.
- Timings and decks remain configuration-driven.
- The pull request description contains its Codex session receipt and acceptance evidence.

P1 findings must be fixed in the same pull request before merge.

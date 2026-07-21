# ExecPlan: BB-12c One household, every device

Issue: #43
Owner: Codex session 019f8642-a30e-7e11-89e0-6e80df9773ca
Status: Ready for external review; Linux client evidence remains operator-run

## Purpose

Make the repository's standing Bedrock-first promise runnable and honest. The delivery adds
an opt-in Geyser, Floodgate, and ViaVersion Compose overlay for supported Linux Docker hosts,
a regression check for that deployment contract, an operator-run RakNet probe, and clear
macOS standalone guidance. It also carries the household story into the repository README
and How to Play page without weakening the existing safety or unofficial-product labels.

This crosses Compose configuration, operator automation, documentation, the public page, and
CI, so an ExecPlan keeps the platform-specific claims and acceptance evidence aligned.

## Progress

- [x] Define the smallest end-to-end slice.
- [x] Implement with tests.
- [ ] Capture the Linux-host and real Bedrock-client acceptance evidence. The macOS and
      container-network evidence available in this delivery environment is recorded below.
- [x] Complete `/review` and resolve P1 findings.
- [x] Record the retrospective.

## Decision Log

| Date | Decision | Why |
| --- | --- | --- |
| 2026-07-21 | Keep Bedrock support in `docker-compose.bedrock.yml` and leave the base demo unchanged. | The issue explicitly makes the bridge opt-in, and Java-only smoke coverage must remain stable. |
| 2026-07-21 | Add a Compose-render regression check to `make ci-fast` and keep the live RakNet probe operator-run. | CI can prove the overlay parses and retains the required plugin and UDP contract without pretending that a client joined a real server. |
| 2026-07-21 | Document Docker Desktop on macOS as unsupported for the overlay's UDP host path and provide a host-run Geyser Standalone fallback. | The issue requires the platform limitation to remain visible rather than overstating support. |

## Surprises & Discoveries

- The delivery worktree began detached because the primary checkout owns `main`; it was
  clean and exactly matched `origin/main`, so the issue branch was created directly from the
  verified remote base without disturbing the primary checkout.
- The first `make ci-fast` invocation could not find macOS's Homebrew-managed Java runtime.
  Re-running with `JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home`
  used the required Java 21 and passed the full gate.
- On Docker Desktop for macOS, Geyser started on container UDP 19132 and answered the real
  RakNet probe from the Compose network, while the same probe against published host UDP
  19134 returned connection refused. This reproduces the issue's platform caveat rather than
  treating a healthy container as proof of macOS host forwarding.
- Floodgate generated `key.pem` in the named plugin volume as UID/GID 1000. The runbook keeps
  that writable volume intact and restricts the standalone copy to mode 600 without exposing
  the key's contents.

## Acceptance evidence

- `JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home make ci-fast`
  passed: `SCENARIOCRAFT_BEDROCK_COMPOSE_OK`, Gradle build, plugin tests, judge tests, renderer
  tests, jar assembly, and the existing page checks all completed successfully.
- `demo/test-bedrock-compose.sh` rendered the combined Compose model as JSON and asserted the
  default published `19132/udp`, `geyser,viaversion`, both beta version settings, the exact
  Floodgate download URL, and `ScenarioCraft Speed Build demo` MOTD.
- `demo/check-bedrock.sh` parsed a protocol-valid local RakNet pong fixture and printed
  `SCENARIOCRAFT_BEDROCK_OK` with the fixture's Speed Build MOTD.
- A fresh isolated Compose-volume smoke downloaded Geyser 2.11.0-b1200, ViaVersion
  5.11.1-SNAPSHOT, and Floodgate 2.2.5-SNAPSHOT; generated the Geyser config and Floodgate
  key; applied `auth-type: floodgate`; restarted Paper; and logged
  `Started Geyser on UDP port 19132` twice.
- From a Python container on that live Compose network, `demo/check-bedrock.sh paper 19132`
  printed `SCENARIOCRAFT_BEDROCK_OK` with `ScenarioCraft Speed Build demo` in Geyser's MOTD.
  The same probe through Docker Desktop's published host port failed, preserving the required
  macOS disclosure.
- Review against `code_review.md`, `git diff --check`, a credential-pattern scan, page-label
  checks, and private-hostname checks found no P1 or acceptance-policy violation.
- Pending operator evidence before merge: a clean Linux host pong through published UDP 19132
  and a real Bedrock client joining and completing one round.

## Retrospective

The delivery keeps Java-only startup untouched and concentrates every Bedrock-specific change
in one overlay and runbook. CI now freezes the declarative plugin and port contract, while the
operator probe tests the actual UDP protocol and refuses a non-Speed-Build endpoint. The
macOS smoke confirmed why the standalone fallback must stay prominent. Final merge readiness
still depends on the explicitly operator-run Linux and real-client acceptance evidence; the
repository does not pretend a Compose render or synthetic pong is a player session.

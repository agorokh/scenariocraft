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
| 2026-07-21 | Keep the Compose-render regression as an explicit CI step and separate `make bedrock-compose-check`; leave `make ci-fast` usable without Docker. | The repository's fast local gate must work in a Java-only checkout, while hosted CI can still freeze the overlay contract. |
| 2026-07-21 | Document Docker Desktop on macOS as unsupported for the overlay's UDP host path and provide a host-run Geyser Standalone fallback. | The issue requires the platform limitation to remain visible rather than overstating support. |
| 2026-07-21 | Install Floodgate 2.2.5 build 138 through a one-shot Compose service and verify SHA-256 before publishing it to the plugin volume. | A floating `latest` plugin URL executes mutable remote code at every start; a versioned URL plus committed digest fails closed. |
| 2026-07-21 | State that the Docker bridge overlay does not provide console LAN discovery. | Published UDP accepts direct Bedrock connections but does not relay Geyser's broadcast from the container bridge to the physical LAN. |
| 2026-07-21 | Pin Geyser and ViaVersion by exact Modrinth version ID, bound Floodgate download retries, and split repository-document assertions into `docs-check`. | Current-head review showed that floating betas, an unbounded setup download, and cross-domain `site-check` assertions could hide runtime drift or erode target contracts. |

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
- Exact-head review found that an inherited `SCENARIOCRAFT_BEDROCK_PORT` could pollute the
  default-port contract, Docker had accidentally become mandatory for `make ci-fast`, the
  mutable Floodgate URL lacked integrity verification, and console discovery was overstated.
  The review fix pins the render environment, restores the Java-only fast gate, verifies the
  Floodgate digest, and narrows the console claim.
- The next current-head review caught that forcing port 19132 tested an override rather than
  the Compose fallback, and that the remaining plugin downloads and network wait were still
  open-ended. The contract now explicitly unsets the caller variable, Geyser and ViaVersion
  use exact Modrinth version IDs, Floodgate has three 30-second attempts, and `site-check`
  again owns only `site/` assertions.

## Acceptance evidence

- `JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home make ci-fast`
  passed: Gradle build, plugin tests, judge tests, renderer tests, jar assembly, and the
  existing page checks all completed successfully.
- `demo/test-bedrock-compose.sh` rendered the combined Compose model as JSON and asserted the
  default published `19132/udp` with the caller variable explicitly unset, exact Geyser and
  ViaVersion Modrinth version IDs, both beta version settings, the bounded versioned
  Floodgate URL and SHA-256, the Paper dependency on the verified installer, and
  `ScenarioCraft Speed Build demo` MOTD.
- A `SETUP_ONLY` run of the pinned `itzg/minecraft-server:2026.4.0-java21` image resolved
  `geyser:U1DOZeks` and `viaversion:CjleI5xo` into `Geyser-Spigot.jar` and
  `ViaVersion-5.11.1-SNAPSHOT.jar` in a fresh data volume.
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

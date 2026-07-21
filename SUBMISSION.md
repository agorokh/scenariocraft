# ScenarioCraft — OpenAI Build Week submission

## Submission fields

- **Track:** Apps for Your Life
- **Repository:** https://github.com/agorokh/scenariocraft
- **How to Play:** https://agorokh.github.io/scenariocraft/
- **Primary Codex Session ID:** `019f7c98-df79-77c2-a996-6e93667720b1`
- **Public YouTube demo:** Pending operator recording and upload before the submission deadline.

The primary session above delivered the first complete Speed Build round lifecycle in
[PR #21](https://github.com/agorokh/scenariocraft/pull/21): start and stop commands, the timed
phase machine, plot assignment, chat/title/bossbar feedback, reconnect handling, and a real
Paper-server round. Later feature PRs carry their own Codex session receipts in the same public
history.

## What we built

ScenarioCraft turns kid-invented game ideas into playable family rituals. Speed Build is its
first scenario: players receive a secret prompt, build behind private walls, tour the results,
and hear three warm AI personas score every build against one shared rubric.

Codex built the public repository from issue specifications. GPT-5.6 is part of the running
product: the judge CLI sends each committed persona's view of each build to `gpt-5.6`, requires
a quorum, and fails closed rather than naming a winner from an incomplete panel.

## Documentation as evidence

The documentation is a passing test: three open-source Mineflayer robot players join the same
Docker Compose server a family runs, complete a real Speed Build round through the public player
controls, exercise the Secret Chest rejection, place every exported block, and wait for the live
AI verdict in game. The repository then freezes that transcript, the BB-06 voxel exports, unedited
judge output, and original renderer images into the How to Play page, while public CI revalidates
the bundle and regenerates the page without a server or API key.

## Judge test path

The canonical quickstart is in the [README](README.md#quickstart):

1. Clone the public repository.
2. Export an `OPENAI_API_KEY` and run `docker compose up --build`.
3. Join `localhost:25565` from Minecraft Java 1.21.x and run `/speedbuild start`.

The demo targets Paper 1.21.11 and Java 21. Docker Compose supplies the server, renderer, and
judge services. The server runs in offline mode and is for a trusted local network only; no
test account is required. The repository's CI also boots a real Paper server and exercises the
plugin instead of treating compilation as runtime proof.

## Prior work and third-party foundation

Before Build Week, the family played a private prototype of this game. This public repository
is a clean-room rebuild created during the event: no prototype code was copied. Only design
lessons were carried over as written GitHub issue specifications.

The project builds on Paper API and Gradle. Their versions are declared in
[`build.gradle.kts`](build.gradle.kts) and the checked-in Gradle wrapper. The repository-owned
voxel renderer and GPT-5.6 judge CLI live in [`renderer/`](renderer/) and [`judge/`](judge/).
ScenarioCraft's code is released under the [MIT License](LICENSE); the self-hosted Bungee font
is distributed under its included [SIL Open Font License](site/assets/fonts/OFL.txt).

## Video requirement

Record the public YouTube video from [the shot list](docs/video-shot-list.md). It must remain
under three minutes, show the working project, include audio, and explain both how Codex built
the repository and how GPT-5.6 referees a round. Add the final public URL to this file, the
README, and the Devpost submission form before submitting.

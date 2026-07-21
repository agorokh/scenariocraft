# ScenarioCraft judge

The judge is a standalone Java 21 CLI. From the repository root, build its installed command:

```sh
./gradlew :judge:installDist
```

Run a round offline with deterministic fixture verdicts:

```sh
judge/build/install/judge/bin/judge --round <round-directory> --dry-run
```

For a live round, omit `--dry-run` and set `OPENAI_API_KEY` in the process environment. The
key is never accepted as an argument or written to output. Live calls default to a 90-second
per-attempt timeout; set `SCENARIOCRAFT_JUDGE_TIMEOUT_SECONDS` to a positive integer to
override it. HTTP connections default to 10 seconds; set
`SCENARIOCRAFT_JUDGE_CONNECT_TIMEOUT_SECONDS` to override that timeout separately.
Both timeout values accept 1 through 600 seconds.
The request timeout remains active through bounded response-body consumption.
Every live kid-facing comment must also pass OpenAI moderation before it can enter a result.
Transient moderation errors retry once against the same generated verdict; a flagged comment
fails closed immediately without regenerating or rechecking unsafe text.

The CLI requires a Unix-like filesystem that reports the `unix:nlink` file attribute for
round inputs. This is an intentional fail-closed provenance boundary: filesystems that cannot
prove an image or voxel source has a single owning path are not supported. Copy the round to a
local APFS, ext4, or other Unix-attribute filesystem before judging it.
Voxel fallback files are capped at 16 MiB, OpenAI response bodies at 1 MiB, and prior result
artifacts are removed before each attempt so a hard failure cannot expose a stale winner.
PNG inputs must contain a complete, CRC-valid chunk stream with image data and a terminal IEND.
Each seven-image set is capped at 16 MiB total. Manifests are capped at 1 MiB, 8 plots,
512 task characters, 128 world characters, and 64 player-name characters.
Persona and rubric files are each capped at 64 KiB; persona count and prompt-field lengths are
also bounded before any OpenAI request is constructed.
Round snapshots are capped at 32 MiB total. Fallback voxels must match the manifest origin and
size and stay within renderer palette, block-count, dimension, and volume limits. PNG raster
data is decoded before acceptance. Verdict reasoning is capped at 4,000 characters and the
kid-facing comment at 500 characters.
Round roots themselves may not be symbolic links. Both serialized reasoning and kid-facing
comments must pass local control/cruelty validation and live OpenAI moderation.
The `unix:nlink` provenance requirement applies to untrusted round inputs; renderer-generated
temporary PNGs retain the same bounded/stable/raster checks without depending on temp filesystem
link-count support. Live Responses envelopes must contain exactly one verdict and no refusal.
Arena configuration and judge manifests share the same eight-plot maximum. Decoded PNGs are capped
at 4,194,304 pixels, persona names reject terminal/format controls, and result JSON/text publication
removes both artifacts if either write fails.
Voxel palette/block entry limits are enforced by a streaming preflight before Gson builds a tree.
Supplementary-plane format controls are rejected, and top-level as well as nested Responses
refusals fail the persona attempt closed.
Fallback palette entries must be namespace-form block IDs no longer than 256 characters, and PNG
validation rejects images with more than 1,024 chunks before raster decoding.

The command reads `judge/personas.yml` and `judge/rubric.md` relative to its working directory
by default. To run it from another directory, set `SCENARIOCRAFT_JUDGE_CONFIG_DIR` to the
directory containing those two files. BB-10 owns the production content. Each round must
contain `manifest.json` and either all seven PNGs under `out/<plot_id>/` or
`<plot_id>.voxels.json` for the renderer fallback. The command writes `results.json` and
`results.txt` into the round directory.

## In-game announcement over RCON

After both result files are published, the judge can ask the running Paper server to announce
them. Enable RCON in `server.properties`, then provide all three settings through the process
environment:

```sh
export SCENARIOCRAFT_RCON_HOST=127.0.0.1
export SCENARIOCRAFT_RCON_PORT=25575
export SCENARIOCRAFT_RCON_PASSWORD='<server rcon password>'
```

The same values can be placed in an optional `judge.yml` beside `personas.yml` and
`rubric.md`:

```yaml
rcon:
  host: 127.0.0.1
  port: 25575
  password: replace-with-the-server-rcon-password
  timeout-seconds: 5
```

Environment values override `judge.yml`. Keep a credential-bearing `judge.yml` outside source
control with owner-only permissions. `SCENARIOCRAFT_RCON_TIMEOUT_SECONDS` can override the
connection and response timeout with a value from 1 through 60 seconds. RCON is optional; a
missing configuration skips the network request. A connection, authentication, or command
failure is reported without the password and does not change the judge status or remove
`results.json`/`results.txt`. If the files are shared with the server, ScenarioCraft's REVEAL
poll still announces them; `/speedbuild results` replays the latest readable result at any time.

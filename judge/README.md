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
Every live kid-facing comment must also pass OpenAI moderation before it can enter a result.
A moderation error or flagged comment fails closed and uses the persona's single retry.

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

The command reads `judge/personas.yml` and `judge/rubric.md` relative to its working directory
by default. To run it from another directory, set `SCENARIOCRAFT_JUDGE_CONFIG_DIR` to the
directory containing those two files. BB-10 owns the production content. Each round must
contain `manifest.json` and either all seven PNGs under `out/<plot_id>/` or
`<plot_id>.voxels.json` for the renderer fallback. The command writes `results.json` and
`results.txt` into the round directory.

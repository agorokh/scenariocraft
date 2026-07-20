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
override it.

The command reads `judge/personas.yml` and `judge/rubric.md` relative to its working directory.
BB-10 owns those production content files. Each round must contain `manifest.json` and either
all seven PNGs under `out/<plot_id>/` or `<plot_id>.voxels.json` for the renderer fallback.
The command writes `results.json` and `results.txt` into the round directory.

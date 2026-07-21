# Asset sources

Every visual loaded by the Speed Build How to Play page is original to this repository.

- `assets/branding/speed-build-logo.png` is the children's original pixel-art identity,
  copied from the design council's supplied source file.
- `assets/scenes/candy-volcano-*.png` are deterministic outputs from the repository's Java
  voxel renderer. Their source is
  `src/test/resources/fixtures/speed-build-candy-volcano.voxels.json` (schema v1).
- The remaining story illustrations and judge portraits are inline SVG or CSS authored for
  this page. They do not copy Mojang assets or game-client UI.

Regenerate the three voxel images from the repository root with Java 21:

```sh
./gradlew :renderer:installDist
renderer/build/install/renderer/bin/renderer \
  --in src/test/resources/fixtures/speed-build-candy-volcano.voxels.json \
  --out renderer/build/site-candy-volcano
```

Then copy `iso-ne.png`, `cut-z.png`, and `plan.png` to the three corresponding files under
`site/assets/scenes/`. The SHA-256 values are recorded in the issue ExecPlan.

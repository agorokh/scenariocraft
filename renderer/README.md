# ScenarioCraft voxel renderer

This standalone Java 21 command reads a frozen schema-v1 `p<N>.voxels.json` file and writes
exactly seven 1024×1024 transparent PNG views. It has no dependency on Bukkit or Paper.

Run it from the repository root:

```sh
./gradlew :renderer:installDist
renderer/build/install/renderer/bin/renderer \
  --in src/test/resources/fixtures/small-house.voxels.json \
  --out renderer/build/example
```

Regenerate the committed golden image only after intentionally reviewing a rendering change:

```sh
./gradlew :renderer:regenerateGolden
```

The golden test renders the same fixture and compares the complete PNG byte stream with
`src/test/resources/golden/small-house-iso-ne.png`.

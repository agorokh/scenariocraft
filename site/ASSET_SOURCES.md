# Asset sources

Every visual loaded by the Speed Build How to Play page is original to this repository.

- `assets/branding/speed-build-logo.png` is the children's original pixel-art identity,
  copied from the design council's supplied source file.
- `assets/rounds/<round-id>/*.png` are deterministic outputs from the repository's Java
  voxel renderer. Each image is generated from the corresponding frozen
  `data/rounds/<round-id>/p<N>.voxels.json` export (schema v1) during `make proof-round`.
- `assets/scenes/rainbow-volcano-*.png` are deterministic outputs from the same renderer.
  They are a scripted showcase—not a played round—and come from the checked-in schema-v1
  `speed-build-rainbow-volcano*.voxels.json` fixtures under `src/test/resources/fixtures/`.
- `assets/fonts/bungee-latin.woff2` is the Latin subset of Bungee Regular, self-hosted
  from Google Fonts and licensed under the SIL Open Font License 1.1. The license is
  preserved at `assets/fonts/OFL.txt`.
- The remaining story illustrations and judge portraits are inline SVG or CSS authored for
  this page. They do not copy Mojang assets or game-client UI.

Regenerate and validate the current played-round images from the repository root with Docker
Compose and a live judge key:

```sh
OPENAI_API_KEY='<your OpenAI API key>' make proof-round
```

The proof target runs the real Paper round, copies its frozen voxel exports, invokes the renderer,
and rebuilds `site/index.html`. Public CI uses `make proof-check` to regenerate the renderer views,
compare them byte-for-byte with the committed PNGs, validate the bundle, and regenerate the page
without Docker, bots, or a judge key.

Regenerate the scripted showcase fixtures and four selected views without Docker, bots, or a
judge key:

```sh
make showcase-scenes
```

Renderer tests render both showcase fixtures twice, require byte-identical results, and compare
the selected views with the committed page assets. The legacy candy-volcano fixture images remain
separate renderer regression assets and are not presented as played-round proof.

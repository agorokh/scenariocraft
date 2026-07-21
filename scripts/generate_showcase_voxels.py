#!/usr/bin/env python3
"""Generate the checked-in rainbow-volcano showcase voxel fixtures."""

from __future__ import annotations

import argparse
import json
from pathlib import Path


REPOSITORY_ROOT = Path(__file__).resolve().parent.parent
FIXTURE_ROOT = REPOSITORY_ROOT / "src/test/resources/fixtures"
SIZE = (33, 22, 33)
CENTER = 16
PALETTE = (
    "minecraft:air",
    "minecraft:grass_block",
    "minecraft:deepslate",
    "minecraft:dirt",
    "minecraft:coarse_dirt",
    "minecraft:terracotta",
    "minecraft:black_wool",
    "minecraft:red_wool",
    "minecraft:orange_wool",
    "minecraft:yellow_wool",
    "minecraft:lime_wool",
    "minecraft:cyan_wool",
    "minecraft:blue_wool",
    "minecraft:purple_wool",
    "minecraft:gold_block",
    "minecraft:quartz_block",
    "minecraft:white_wool",
    "minecraft:light_gray_wool",
)
INDEX = {block_id: index for index, block_id in enumerate(PALETTE)}
RAINBOW = (
    "minecraft:red_wool",
    "minecraft:orange_wool",
    "minecraft:yellow_wool",
    "minecraft:lime_wool",
    "minecraft:cyan_wool",
    "minecraft:blue_wool",
    "minecraft:purple_wool",
)
FIXTURES = {
    "speed-build-rainbow-volcano.voxels.json": False,
    "speed-build-rainbow-volcano-walled.voxels.json": True,
}


def offset(x: int, y: int, z: int) -> int:
    size_x, _, size_z = SIZE
    return x + size_x * (z + size_z * y)


def set_block(blocks: list[int], x: int, y: int, z: int, block_id: str) -> None:
    size_x, size_y, size_z = SIZE
    if 0 <= x < size_x and 0 <= y < size_y and 0 <= z < size_z:
        blocks[offset(x, y, z)] = INDEX[block_id]


def volcano_radius(y: int) -> int:
    return max(3, 14 - ((y - 1) * 11 // 16))


def rock_for(y: int, x: int, z: int) -> str:
    bands = (
        "minecraft:deepslate",
        "minecraft:dirt",
        "minecraft:coarse_dirt",
        "minecraft:terracotta",
    )
    band = (y // 3 + (x + 2 * z) // 7) % len(bands)
    return bands[band]


def add_ground(blocks: list[int]) -> None:
    for z in range(SIZE[2]):
        for x in range(SIZE[0]):
            block_id = "minecraft:grass_block"
            if (x + z) % 11 == 0:
                block_id = "minecraft:coarse_dirt"
            set_block(blocks, x, 0, z, block_id)


def add_volcano(blocks: list[int]) -> None:
    for y in range(1, 18):
        radius = volcano_radius(y)
        radius_squared = radius * radius
        for z in range(CENTER - radius, CENTER + radius + 1):
            for x in range(CENTER - radius, CENTER + radius + 1):
                distance_squared = (x - CENTER) ** 2 + (z - CENTER) ** 2
                if distance_squared > radius_squared:
                    continue
                block_id = rock_for(y, x, z)
                if distance_squared <= 3:
                    block_id = (
                        "minecraft:gold_block" if y % 3 == 0 else "minecraft:orange_wool"
                    )
                set_block(blocks, x, y, z, block_id)

    # A bright crater pool and lip make the volcano readable from above and in isometric views.
    for z in range(CENTER - 3, CENTER + 4):
        for x in range(CENTER - 3, CENTER + 4):
            distance_squared = (x - CENTER) ** 2 + (z - CENTER) ** 2
            if distance_squared <= 4:
                set_block(blocks, x, 17, z, "minecraft:orange_wool")
                set_block(blocks, x, 18, z, "minecraft:gold_block")
            elif distance_squared <= 10:
                set_block(blocks, x, 17, z, "minecraft:black_wool")

    # A tiny erupting spark lets the no-peek view show glow without exposing the build.
    set_block(blocks, CENTER, 19, CENTER, "minecraft:orange_wool")
    for x, z in (
        (CENTER, CENTER),
        (CENTER - 1, CENTER),
        (CENTER + 1, CENTER),
        (CENTER, CENTER - 1),
        (CENTER, CENTER + 1),
    ):
        set_block(blocks, x, 20, z, "minecraft:gold_block")
    set_block(blocks, CENTER, 21, CENTER, "minecraft:gold_block")

    # Seven flows fan out from the crater and track the stepped north face of the cone.
    for y in range(2, 17):
        radius = volcano_radius(y)
        spread = max(1, radius - 3)
        for color_index, block_id in enumerate(RAINBOW):
            lane = color_index - 3
            x = CENTER + round(lane * spread / 6)
            z = CENTER - radius
            set_block(blocks, x, y, z, block_id)
            set_block(blocks, x, y, z + 1, block_id)

    # Continue the rainbow onto the grass as a short, unmistakable lava fan.
    for color_index, block_id in enumerate(RAINBOW):
        lane = color_index - 3
        for step in range(4):
            set_block(blocks, CENTER + lane, 1, 1 + step, block_id)


def add_walls(blocks: list[int]) -> None:
    for y in range(1, SIZE[1]):
        for coordinate in range(SIZE[0]):
            block_id = (
                "minecraft:quartz_block"
                if (coordinate // 4 + y // 4) % 2 == 0
                else "minecraft:white_wool"
            )
            for inset in (0, 1, SIZE[0] - 2, SIZE[0] - 1):
                set_block(blocks, inset, y, coordinate, block_id)
                set_block(blocks, coordinate, y, inset, block_id)

    # A pale cap emphasizes the wall height without borrowing any game-client texture.
    for coordinate in range(SIZE[0]):
        for inset in (0, 1, SIZE[0] - 2, SIZE[0] - 1):
            set_block(blocks, inset, SIZE[1] - 1, coordinate, "minecraft:light_gray_wool")
            set_block(blocks, coordinate, SIZE[1] - 1, inset, "minecraft:light_gray_wool")

    # The real arena has an anti-peek cap. Leave only a tiny camera-readable glow opening;
    # the outside view must explain privacy rather than reveal the showcase build.
    roof_y = SIZE[1] - 1
    for z in range(2, SIZE[2] - 2):
        for x in range(2, SIZE[0] - 2):
            if (x - CENTER) ** 2 + (z - CENTER) ** 2 <= 4:
                continue
            block_id = (
                "minecraft:white_wool"
                if (x // 4 + z // 4) % 2 == 0
                else "minecraft:quartz_block"
            )
            set_block(blocks, x, roof_y, z, block_id)


def build_document(walled: bool) -> dict[str, object]:
    blocks = [0] * (SIZE[0] * SIZE[1] * SIZE[2])
    add_ground(blocks)
    add_volcano(blocks)
    if walled:
        add_walls(blocks)
    return {
        "schema": 1,
        "plot_id": (
            "speed-build-rainbow-volcano-walled"
            if walled
            else "speed-build-rainbow-volcano"
        ),
        "origin": [0, 64, 0],
        "size": list(SIZE),
        "palette": list(PALETTE),
        "blocks": blocks,
    }


def serialized_fixture(walled: bool) -> str:
    return json.dumps(build_document(walled), indent=2) + "\n"


def main() -> int:
    parser = argparse.ArgumentParser()
    mode = parser.add_mutually_exclusive_group(required=True)
    mode.add_argument("--write", action="store_true")
    mode.add_argument("--check", action="store_true")
    args = parser.parse_args()

    stale: list[str] = []
    for filename, walled in FIXTURES.items():
        path = FIXTURE_ROOT / filename
        expected = serialized_fixture(walled)
        if args.write:
            path.write_text(expected, encoding="utf-8")
        elif not path.is_file() or path.read_text(encoding="utf-8") != expected:
            stale.append(filename)

    if stale:
        parser.error("stale showcase fixtures: " + ", ".join(stale))
    print("SCENARIOCRAFT_SHOWCASE_FIXTURES_WRITTEN" if args.write else "SCENARIOCRAFT_SHOWCASE_FIXTURES_CURRENT")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

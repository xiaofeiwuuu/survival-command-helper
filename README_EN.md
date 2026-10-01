# Survival Command Helper

![icon](docs/icon.png)

A **client-side** utility mod for **Minecraft Forge 1.20.1**: build and run common commands from a GUI instead of
memorising syntax. Items, blocks and mobs are read from the live registries (vanilla plus every loaded mod), and search
works by name, **pinyin**, ID or mod name.

> 中文：[README.md](README.md) | License: [MIT](LICENSE) | [CHANGELOG](CHANGELOG.md)

## Install

1. Minecraft **1.20.1** with **Forge 47.x**.
2. Put `survival-command-helper-forge-1.20.1-<version>.jar` in `mods/` (delete any older copy first).
3. In a world, press **K** to open the menu (rebindable under Controls).

Install it on the **client only**; nothing is needed on the server. It depends on no other mod.

## Features

- **/give, /kill, /tp, /fill, /setblock, /summon, /weather, /time, /gamemode, /difficulty, /locate, /forceload** from a GUI,
  with a live command preview line (with a plain-language explanation) and problem hints on every screen.
- **Fill** and **Clone** previews show the **real blocks as a translucent ghost** (press **V** for coloured boxes). Fill's
  hollow/outline modes show their true shape.
- **Clone**: live check of source area, sizes (difference + 1), destination and resulting area; move by east/south/west/
  north/up/down in blocks or in multiples of the source size; clone history; one-click clearing of the pasted area (the
  overlap with the source is kept).
- **Building blueprints**: scan a building, save it as a blueprint (merged into the fewest `/fill` boxes), place it in
  another world where the crosshair points, with rotate / mirror / raise / lower. **Blocks from mods that aren't installed
  are skipped.** Ghost preview, throttled execution, and an automatic check-and-patch afterwards. Optional "fill air too".
- **One-click "clear hostile mobs"**: scans around you first and only kills the types that are actually there. The
  list of hostile types is editable.
- **Force-loaded chunks**: list them, teleport to one, or un-force it.
- **/locate**: structures and points of interest get readable names (you can name modded ones); one click teleports you there.
- **Pinyin search**, coordinate **paste boxes** (`10 64 -5`, `10，64，-5`, `X: 10 Y: 64 Z: -5`…), tooltips on every input,
  history and favourites.

## Limitations

- **Forge 1.20.1 only.** Not for Fabric or other versions.
- **Running commands needs cheats/OP.**
- **Blueprints store blocks and their states only** — *not* chest contents, entities, sign text or banner patterns. Scanning
  needs the area to be loaded on the client (within render distance), up to about 1,000,000 blocks at a time. The
  after-placement check compares block *kinds*, not every property.
- "Clear hostile mobs" only sees entities loaded on the client, and judges by type.
- "Clear pasted area" fills with air; it does **not** restore what was there before.
- Force-loaded chunk listing shows the current dimension only and needs OP.
- Teleporting to a structure lands on the surface (the server gives no height); underground structures need digging.
- Chests, beds, signs and fluids appear as coloured boxes in ghost previews. With shader/render mods, if the ghost looks
  wrong, press **V**.
- The mod only sends commands and reads the client's loaded world; it does not change game logic.

## Build

JDK 17: `./gradlew build` (jar in `build/libs/`), `./gradlew runClient`.

## Credits & license

[MIT](LICENSE) © 2026 xiaofeiwu. The pinyin table is derived from
[mozillazg/pinyin-data](https://github.com/mozillazg/pinyin-data) (MIT) — see [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).

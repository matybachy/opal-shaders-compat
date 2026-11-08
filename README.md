# Opal Compat

Runs OptiFine and Iris format shader packs on [VulkanMod Reforged](https://github.com/TrulyRin/VulkanMod-Reforged) on Minecraft 1.21.1 with NeoForge, and makes Opal Shaders, Vectors, Distant Horizons, Create and Mekanism work with it.

Client-side only. **Alpha:** tested on one GPU (RTX 5060 Ti) and a handful of shader packs, so expect bugs. Not made by the authors of Opal, Vectors or VulkanMod Reforged.

## What it does

**With VulkanMod Reforged**

- Draws shader packs on Vulkan: G-buffers, shadow maps, deferred and composite passes, compute programs and custom textures.
- Switches packs in the game without a restart. If a pack fails, the game falls back to plain VulkanMod Reforged and shows the error on the Shader Packs screen.
- Puts the settings on one screen: VulkanMod Reforged's video settings get a **Shaders** group and pages for Vectors.
- Keeps Opal's screens and keys working. Opal's own OpenGL renderer stands down, and the packs it asks for are drawn here.
- Draws Distant Horizons 3.3.3 LODs through a pack's `dh_*` programs.
- Draws Create and Mekanism with the pack. Flywheel falls back to `flywheel:off`, so Create's moving parts go through block entity renderers.
- Works around crashes in other mods: Steam 'n' Rails uploading textures on VulkanMod Reforged during the load, a random failure in Every Compat at startup, and Distant Horizons with Chunky when a world loads.

**Without VulkanMod Reforged** (Opal on OpenGL)

- Opal takes packs with more than seven custom images.
- Flywheel gets out of the pack's way, so Create's moving parts get the pack's lighting.

Since the merge into one mod this OpenGL part has only been checked statically, and the Flywheel change has never run in the game.

## Requirements

| | Version |
|---|---|
| Minecraft | 1.21.1 |
| NeoForge | 21.1.x (21.1.259 or newer with Opal) |
| Java | 21 |
| VulkanMod Reforged | exactly `0.3.5-ALPHA+1.21.1` for the Vulkan features; the game refuses other versions |

Optional:

| | Version | Notes |
|---|---|---|
| Opal Shaders | 1.0.0-alpha.8 or newer | needs Vectors |
| Vectors | 1.5.0 or newer | needs LibLibLib |
| Distant Horizons | exactly 3.3.3 | with other versions the Vulkan bridge stays off, which is untested |

## Installation

There are no prebuilt releases yet, so [build the jar](#building) first.

1. Put `opalcompat-<version>.jar` and VulkanMod Reforged in `mods`, with any of the optional mods. Remove old `vitrailvk` and `vitrailbridge` jars, the game refuses them.
2. Put shader packs in `shaderpacks`. At least one should be there when the game starts, otherwise turning a pack on needs a restart. With a pack installed, terrain takes twice the memory, even with shaders turned off.
3. In the game open the video settings and the **Shaders** group on the left. Choose a pack, turn **Shaders** on and press **Apply**.

A pack's settings are saved in `shaderpacks/<pack>.txt`, the same file Iris uses.

### Large mod sets

A set with millions of block states needs FerriteCore. A 356-mod set with 3.3 million block states ran out of 15 GB without it, and even with it about 14 GB was live just after joining a new world. Prefer G1 over ZGC and give the game more memory if you can. See [BLOCKERS.md](BLOCKERS.md), which also lists mods that can't be made to work, such as `create-dyn-light`.

## Tested

On Vulkan with NeoForge 21.1.265. The full list and the evidence are in [PORTING.md](PORTING.md).

| | Result |
|---|---|
| Complementary Unbound + Euphoria Patches: clouds, hand, entities, block entities, sunset, night, rain, caves, underwater | works |
| BSL, Complementary Reimagined, Bliss | run, log clean; only the daytime image was inspected |
| Turning packs off, on and switching them without a restart, from both VulkanMod Reforged and Opal | works |
| Create 6.0.10: machines, belts, a piston contraption | drawn with the pack and shadows |
| Mekanism 10.7.19: machines, tanks, cables, generators | drawn with the pack |
| Distant Horizons 3.3.3: LODs by day and night | works |
| A 356-mod set with FerriteCore, G1 and 15 GB, without a shader pack | starts, creates a world and joins it |
| A 184-mod set with Complementary Unbound + Euphoria Patches and DH, over deep water: near and far water meet | no wall or gap at the edge of the render distance |

## Known limitations

- Without a shader pack, Distant Horizons LODs are not drawn, nor are its clouds and beacons.
- Normal and specular maps are neutral: PBR atlases are not loaded.
- Packed, BGR, integer and rectangle raw textures read as black.
- Only sections compiled by the camera's graph go into the shadow map.
- What a mod draws with direct OpenGL outside the game's render paths is missing from the image.
- VulkanMod Reforged does not support Fabulous graphics.
- The first time a pack or its settings are used, the game stops for up to a second whenever a new program is needed, for example at the first mob. The result is kept in `config/opalcompat_pipeline_cache.bin`, so later runs do not stop.

More in [PORTING.md](PORTING.md#known-problems-and-limitations).

## Building

Needs JDK 21.

```bash
./gradlew build
```

The jar is `build/libs/opalcompat-<version>.jar`. The first build downloads VulkanMod Reforged and Distant Horizons to compile against and checks their SHA-256. Neither is put in the jar. Opal and Vectors are not needed: `src/stubs` only names what this mod calls, and is left out of the jar too.

`./gradlew checkPacks` translates and compiles every program of the packs in `run/shaderpacks` without starting the game. `scripts/instance.py` sets up and runs clean test instances, driven by the scenes in `scripts/scenes`.

## Documentation

- [PORTING.md](PORTING.md): status, test results with evidence, decisions, mixins and what is not verified.
- [BLOCKERS.md](BLOCKERS.md): what takes a change in another mod, and the bugs this mod works around.
- [ANALYSIS.md](ANALYSIS.md): the initial analysis.

## Name

Up to 0.3.0 this was Vitrail VK and Vitrail Bridge. Since 0.4.0 it is one mod, Opal Compat. The code under `dev.vitrail` is taken from [Vitrail](https://github.com/avpbynf/Vitrail-Shaders) and keeps its name, which is why you may see a `vitrail` folder in the game directory and `[Vitrail]` in the log.

## License

[LGPL-3.0-only](LICENSE), with the [GPL-3.0](COPYING) it refers to. Third-party material and its terms are listed in [NOTICE](NOTICE) and [NOTICE-vitrail](NOTICE-vitrail). VulkanMod Reforged, Distant Horizons, Opal, Vectors and Flywheel are not redistributed.

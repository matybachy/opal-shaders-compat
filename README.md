# Opal Compat

Runs OptiFine and Iris format shader packs on [VulkanMod Reforged](https://github.com/TrulyRin/VulkanMod-Reforged) on Minecraft 1.21.1 with NeoForge, and makes Opal Shaders, Vectors, Distant Horizons, Create and Mekanism work with it.

Client-side only. **Alpha:** tested on one GPU (RTX 5060 Ti) and a handful of shader packs, so expect bugs. Not made by the authors of Opal, Vectors or VulkanMod Reforged.

## What it does

**With VulkanMod Reforged**

- Draws shader packs on Vulkan: G-buffers, shadow maps, deferred and composite passes, compute programs and custom textures.
- Switches packs in the game without a restart. If a pack fails, the game falls back to plain VulkanMod Reforged and shows the error on the Shader Packs screen.
- Puts the settings on one screen: VulkanMod Reforged's video settings get a **Shaders** group and pages for Vectors.
- Keeps Opal's screens and keys working. Opal's own OpenGL renderer stands down, and the packs it asks for are drawn here.
- Has pack screens of its own behind the Shaders group: a list of packs that filters by name, and a pack's settings with a search over all of its pages.
- Uploads finished chunk sections as fast as VulkanMod Reforged's own frame budget allows while they wait, whatever its "Chunk Upload Ceiling" says, and builds and uploads the sections near the player and those they change first.
- Animates a texture only once something has drawn with it, which spares a large mod set hundreds of uploads twenty times a second. A switch in the Shaders group turns it off.
- Draws Distant Horizons 3.3.3 LODs through a pack's `dh_*` programs, and with no pack on through DH's own terrain program on Vulkan, with its fog and far fade.
- Draws Create and Mekanism with the pack. Flywheel falls back to `flywheel:off`, so Create's moving parts go through block entity renderers.
- Works around crashes in other mods: Steam 'n' Rails uploading textures on VulkanMod Reforged during the load, a random failure in Every Compat at startup, and Distant Horizons with Chunky when a world loads. Distant Horizons also loads its far terrain faster than its own limits would allow here: its threads copy it to the GPU themselves instead of waiting for a frame.

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

Download the jar from [Releases](https://github.com/matybachy/opal-shaders-compat/releases), or [build it](#building).

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
| The same set, moving at render distance 5, measured frame by frame (`scripts/scenes/blink.txt`): standing, drifting, flying and turning | no shadow jumps, no chunks or far sections missing; water and light hold still while walking |

## Known limitations

- Distant Horizons' clouds and beacons are not drawn. Without a shader pack its LODs have no ambient occlusion or anti-aliasing and no fade from the near chunks, and that route has been built but not yet run.
- Normal and specular maps are neutral: PBR atlases are not loaded.
- Packed, BGR, integer and rectangle raw textures read as black.
- Only sections compiled by the camera's graph go into the shadow map.
- What a mod draws with direct OpenGL outside the game's render paths is missing from the image.
- VulkanMod Reforged does not support Fabulous graphics.
- The first time a pack or its settings are used, the game stops for up to a second whenever a new program is needed, for example at the first mob. The result is kept in `config/opalcompat_pipeline_cache.bin`, so later runs do not stop.

More in [PORTING.md](PORTING.md#known-problems-and-limitations).

## Reporting low frame rates

While a shader pack draws a world, `logs/latest.log` says every ten seconds how the frames went (`[pack] ... frames in 10 s`) and every thirty seconds where the render thread spent them (`[where]`): which part of the frame, which mods, which methods, for all frames and apart for the slow ones. Send that log with a report. The sampling costs a fraction of a percent; `frameSampler=false` in `config/opalcompat.properties` turns it off.

## Building

Needs JDK 21.

```bash
./gradlew build
```

The jar is `build/libs/opalcompat-<version>.jar`. The first build downloads VulkanMod Reforged and Distant Horizons to compile against and checks their SHA-256. Neither is put in the jar. Opal and Vectors are not needed: `src/stubs` only names what this mod calls, and is left out of the jar too.

`./gradlew checkPacks` translates and compiles every program of the packs in `run/shaderpacks` without starting the game. `scripts/instance.py` sets up and runs clean test instances, driven by the scenes in `scripts/scenes`.

`./gradlew patchDistantHorizons` writes a modified copy of Distant Horizons 3.3.3 to `build/libs/distanthorizons`, under the same file name, that selects Blaze3D in its code instead of its config and calls no OpenGL of its own. It only works with this mod on VulkanMod Reforged. The unmodified jar works as well; see [PORTING.md](PORTING.md).

## Documentation

- [PORTING.md](PORTING.md): status, test results with evidence, decisions, mixins and what is not verified.
- [BLOCKERS.md](BLOCKERS.md): what takes a change in another mod, and the bugs this mod works around.
- [ANALYSIS.md](ANALYSIS.md): the initial analysis.

## Name

Up to 0.3.0 this was Vitrail VK and Vitrail Bridge. Since 0.4.0 it is one mod, Opal Compat. The code under `dev.vitrail` is taken from [Vitrail](https://github.com/avpbynf/Vitrail-Shaders) and keeps its name, which is why you may see a `vitrail` folder in the game directory and `[Vitrail]` in the log.

## License

[LGPL-3.0-only](LICENSE), with the [GPL-3.0](COPYING) it refers to. Third-party material and its terms are listed in [NOTICE](NOTICE) and [NOTICE-vitrail](NOTICE-vitrail). VulkanMod Reforged, Distant Horizons, Opal, Vectors and Flywheel are not redistributed.

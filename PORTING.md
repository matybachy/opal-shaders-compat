# PORTING.md

Porting log: status, decisions, known problems and the origin of taken code. The initial analysis is
in [ANALYSIS.md](ANALYSIS.md).

Up to version 0.3.0 the mod was called Vitrail VK, with Vitrail Bridge next to it. Since 0.4.0 it is one mod, `opalcompat`, at the user's request.
Older commits, tags and jars in `release/` carry the old names. The code taken from Vitrail stays in `dev/vitrail` and its origin is in `NOTICE`.

## Status

Note: "done in code" means compiled and statically checked against the sources, not working. What really ran in the game is
in Phase 2 results and Results on Vulkan.

| Item | Status |
|---|---|
| 1 References and build | done, `./gradlew build` passes |
| 2 Mixins and runtime | done, ran in the game (`test_final`, `test_composite`, `test_terrain`) |
| 3 Options, noisetex, mipmaps, blends | done in code |
| 4 Shadows: terrain, entities, block entities, `shadowcomp` (graphics), mipmaps | done in code |
| 5 Sky, entities, hand, particles, weather, clouds | done in code |
| 6 Custom textures, image.*, SSBO, compute | done in code |
| 7 GUI for choosing a pack and its settings | done in code |
| A Flywheel, Create | fallback done in code: Flywheel off, Create through BERs; no backend of our own |
| B Other mods including Mekanism | done in code: mapping by Iris's table, bytecode checked |
| C Distant Horizons including `dh_*` | done, ran in the game with LODs, see Results on Vulkan |
| D Opal Compat: Opal and Vectors on VMR, shared GUI | done, ran in the game, see below |

**Restoration, 2026-11-02:** the original `vulkan-shaders` folder, including its local git history (M1-M5), disappeared
from the disk (it was not in the Recycle Bin either). The project was restored from the conversation and the same revisions of the
references, and the history starts over. `ANALYSIS.md` is restored in a shortened form.

## How to build and run

- Java 21: the system `JAVA_HOME` on this PC points to JDK 8. Set it to `C:\Program Files\Zulu\zulu-21`.
- `./gradlew build`: `build/libs/opalcompat-<version>.jar`. The first build downloads VMR into `libs/` and checks its SHA-256
  (`gradle.properties`).
- `scripts/release.py "text"`: after a commit and a build, checks the jar's metadata, compares its classes with the jar the tests ran on, and copies
  it to `release/`, recording it in `release/LATEST.txt`.
- `./gradlew checkPacks [-Ppacks=...] [-PdumpAll=true]`: without the game, translates and compiles all programs of the packs
  in `run/shaderpacks`, including mesh families the way the renderer loads them (entities, sky, particles, `shadow_entities`),
  graphics `shadowcomp` and compute. It also checks that every vertex input has a source. It used to skip the graphics half
  of a path with both `.csh` and `.fsh`.
- `scripts/autotest.sh [pack] [-Pautotest.options=A=b,C=d] [-Pautotest.camera=x,z,yaw,pitch,height]
  [-Pautotest.time=6000]`: build, client in autotest mode, summary. The output is in `run/autotest/`.
- `scripts/instance.py create|run NAME`: a clean instance with NeoForge in `test-instance/NAME` (outside git, jars into `mods/`),
  no dev run. `run` takes `--pack`, `--options`, `--script`, `--camera`, `--time`. The output is in `test-instance/NAME/autotest`.
- `scripts/instance.py install NAME [--renderer gl] [--extra DIR]`: the fresh jar, optionally without VMR, and the jars
  from `test-instance/jars/DIR`. A run on OpenGL is driven by `compat/GlAutoTest` with the same properties and scripts.
- Autotest script (`--script`, lines: `cmd`, `wait`, `settle`, `shot`, `dump`, `fps`, `gui`, `slot`, `graphics`, `resize`,
  `minimize`, `restore`, `pack`, `reload`, `screen`, `click`, `press`, `emptyupload`, `error`, `note`, `quit`), see `dev/AutoTest`. Scenes are
  in `scripts/scenes/`. The test window is a normal window: the autotest closes a screen the script did not open, but moving the mouse
  still affects the result.
  `dump` saves all targets of the current pack as PNG (`render/TargetDump`).
- `scripts/glscan.py jar...`: direct GL calls in a mod's bytecode, including nested jars.
- `./gradlew checkPacks -PdhVariant=true`: everything with `DISTANT_HORIZONS` defined; the `dh_*` programs are always checked this way.
- `scripts/pixel.py file.png x,y ...`: pixel values, no dependencies.
- `scripts/take_from_vitrail.py`: takes group a from Vitrail into `src/main/java/dev/vitrail`. **Careful:** it would overwrite
  the changes in `PackProgram`, `ShaderProperties` and `TargetPlan` (marked `OpalCompat:`), add them back by hand afterwards.
- `-Pvalidation=false` turns off validation layers (`tools/vvl`, Khronos nightly, outside git).

A pack is chosen in the game: VMR's video settings, group **Shaders** on the left (saved to
`config/opalcompat.properties`, key `shaderPack`). A pack's settings are in `shaderpacks/<pack>.txt`, the same file
as with Iris. The system properties `opalcompat.pack`, `opalcompat.profile` and `opalcompat.options` (autotest) take precedence
until a pack is chosen in the game.

## Opal, Vectors and VMR (package `compat`, since 2026-11-03)

Everything is one client mod, with VMR, Opal and Vectors all optional. The Vulkan part starts from `VulkanSide` only with VMR in the game and its
mixins with it (`OpalCompatMixinPlugin`); without it only the part for Opal on OpenGL runs. Nothing is copied from Opal or Vectors: `src/stubs` only
names the members called, for compiling, and does not go into the jar. The old jars `vitrailvk` and `vitrailbridge` are incompatible in `neoforge.mods.toml`,
and the game says to remove them.

**Installation on Vulkan:** VMR, `opalcompat`, Opal, Vectors, LibLibLib, NeoForge at least 21.1.259 (Opal requires it). Not `create-dyn-light`,
see `BLOCKERS.md`.

**Why Opal does not draw on VMR.** First log: Opal started through VMR's GL emulation, `glTexImage2D` of a depth texture failed
(`Invalid texture source byte size`) and a minute later `VK_ERROR_DEVICE_LOST` followed. In `GlFunctionRegistry` the functions `glDrawBuffers`,
`glBindImageTexture`, `glDispatchCompute` and `glTexImage3D` are empty, so its pipeline can't stand there. `OpalOnVulkanMixin` cancels
`Opal.rebuild`, the pipeline never comes to exist and all its hooks are empty. Its screens and keys stay: `OpalLink` keeps the selection of both mods the
same and `Opal.requestReload` loads the pack in Vitrail after a change of the selection or the settings file. Without Vitrail
`OpalClient.conflict` reports and there are no shaders.

**"Too many custom images".** Second log, Opal on OpenGL: it gives every image a fixed unit and takes at most 7, the user's Complementary
Unbound + Euphoria has 8 (`Opal custom images` in the log). `OpalImagesMixin` removes the limit and `CustomImageUnits` assigns units per
program at each bind, like Iris. Verified: Opal + Vectors on OpenGL, that pack, `opal_active=true`.

**GUI.** The basis is the registry `ModSettingsRegistry`, which VMR has for this. `ShaderPages`: on/off, pack, settings, list,
folder, state; takes effect after Apply. `VectorsPages`: Workload and Tuning pages, words and ranges from the Vectors screen, without the
OpenGL terrain renderer options. With Opal the buttons lead to its screens, which have the Vectors look. Vectors got a VulkanMod page
and its two extra buttons disappeared from the Options screen. `VectorsOnVulkanMixin`: Vectors counts Opal as a shader mod and would turn off
limits that make sense here.

**Terrain.** `TerrainFormat` decided before the mod was constructed, so outside tests (where the pack comes as `-D`) it ended up off
and a pack would want a restart. Now it asks the `shaderpacks` folder: with a pack in it the mesh is extended and turning one on at run time takes nothing.

## Results on Vulkan (2026-11-03)

NeoForge 21.1.265, RTX 5060 Ti, validation layers, 854x480 window, fresh jars. The pack is Complementary Unbound + Euphoria Patches
with the settings from the user's instance unless stated otherwise.

| Test | Result |
|---|---|
| VMR, `opalcompat`, Opal, Vectors: startup, world | works, the first log does not repeat |
| Only VMR and `opalcompat`, without Opal and Vectors | works (0.4.0) |
| Clouds | work after the fix below |
| Hand, enchanted sword, torch, chest, sign, banner, campfire, fire, mobs | work after the fix below |
| Sunset, night, rain, cave, underwater, block outline | work, validation clean |
| Window resize, minimizing | work, validation clean |
| BSL, Complementary Reimagined, Bliss: scene `world3` | run, log clean; of the images, day was inspected |
| Turning off, turning on, another pack from VMR and from Opal, no restart | works |
| Pack settings from Opal: saving, reloading in Vitrail | works |
| Vectors switch on the VMR page, Apply | saved to `vectors-1.21.1.json` |
| Create 6.0.10 + addons: motor, shafts, gears, belt, mixer, press, water wheel, tank | drawn with the pack and with shadows, `flywheel:off` from the log |
| Create: mechanical piston as a contraption | moves, both plank and pole slide |
| Mekanism 10.7.19 + addons: machines, cube, tanks, cables, generators, Atomic Disassembler in hand | drawn with the pack |
| DH 3.3.3: LODs by day and night, through `dh_*` | work |

**Clouds, the proof.** Debug modes in a copy of the pack (output from `deferred1` into the unused `colortex11`): noise is fine (`noisetex` and
`GetCloudNoise` give structure), `z0` = 1, `cameraPosition.y` = 78, `cloudAlt1i` = 200. The direction was wrong: `viewPos.z` > 0 on the sky
and `nPlayerPos.y` = -0.91 when looking up. Cause: VMR gives `getDepthFar` = infinity and JOML builds such a matrix with an epsilon of
1e-6, so `w` at depth 1 is negative and the sky lies behind the camera. `GetVolumetricClouds` then returns 0 right at `maxPlaneDistance < 0`.
An attempt with an injection into `getDepthFar` did not work: the mixin export showed VMR's handler first and returning, so `GameRendererDepthFarMixin`
redirects the call in `getProjectionMatrix`. After the fix `w` = 1/510, `nPlayerPos.y` = +0.5 in the center when looking 30 degrees up, `clouds.a` > 0.

**Shape of the matrices.** The same measurement gave `P[3][2]` = -0.2 instead of -0.1. The mixin export has nothing under `org/joml`, so VMR's `Matrix4fM`
is not applied and the matrices are OpenGL [-1,1]. `forwardToReversed` made 2z-w of them: near 0.1 and entity shadows with a different depth than terrain.
`glToReversed` publishes the matrix unchanged, the hand multiplies the z row by 0.125 like Iris, DH has its volume in the same form.

**Purple sky with an enchanted sword.** A plain sword changes nothing, an enchanted one changes `colortex0` on the sky from (51,91,227) to (5,1,15), and
back once put away. A hard red glint in a copy of the pack coloured the whole sky, so the glint program was drawing it. Cause: vanilla calls
`setShader(position)` (`LevelRenderer:962`), right after it `renderSky` (`:963`), and that takes `RenderSystem.getShader()` (`:1618`). The shadow pass
runs in between, the player holds the sword in it and the last type is `glint`; `Families.resolve` tests `glint` before `Phase.SKY`. `LevelRendererMixin`
restores the shader after the pass.

**"Not building!".** An enchanted item in hand took the pack down at once (the fallback to plain VMR worked): the hand and shadows had a `MultiBufferSource`
with one shared buffer and the glint ended the item being written. `PassBuffers` is a set like vanilla's.

## Crashes in the Stellarity mod set (2026-11-04)

A copy of the 356 jars and configs of the user's instance in `test-instance/stellarity`, memory as there (`--jvm=-Xmx15360m`), the user's folders unchanged.

| Crash | Cause and proof | Solution |
|---|---|---|
| `Texture is null at index: 0` (report 22.24.34, in a test 18.18.39) | Railways uploads a sprite from the tick of the block atlas (`PhantomSpriteManager.renderTick`), until the load is over the atlas has an id but no image, and `VTextureSelector.uploadSubTexture` throws an NPE. OpenGL and VMR's `GlTexture.texSubImage2D` skip it. | `VTextureSelectorMixin` |
| `Cannot get config value before config is loaded` (22.17.11, in a test 18.20.19, there without `opalcompat`) | `ConcurrentModificationException` in `EveryCompat.forAllModules`: `addModule` is `synchronized`, walking is not, and VanillaBackport, No Man's Land and Gems Realm add modules from their constructors. NeoForge then loads no configs and the first tick crashes. | `EveryCompatModulesMixin` |
| `OutOfMemoryError` / hang on a full heap | The set has no FerriteCore, see `BLOCKERS.md`. | Put FerriteCore back in `mods` |
| `Chunky is not loaded` when creating a world (19.18.02) | DH 3.3.3 binds to Chunky on the first chunk, Chunky comes to exist only in `ServerStartingEvent`, after the first chunks have loaded. | `DhChunkyMixin` |

**Result.** Final jar, FerriteCore, G1, 15 GB: atlas 16384x8192, new world, joined, `status=OK`, no crash. Regression: pack, Create,
Mekanism, DH, Railways `status=OK`, log without errors from the mod.

**Timing of `Texture is null`.** VMR lets an upload through only in the last tick of a frame (`SpriteUtil.setDoUpload(j == n)`, `MSpriteContents.checkUpload`), otherwise it
drops it. So the crash depends on how long the frame is at that moment: of the six runs with the mod that got that far it happened in two (both with the old jar), in the single
run without the mod it did not. The mod's code is not on the stack and it does not call `SpriteUtil`. The exact test is the command `emptyupload` (scene `emptyupload.txt`), which works without Railways: without the mixin
`NullPointerException: Texture is null at index: 0`, with it `empty upload passed`, log without errors.

**Every Compat.** Without the fix it happened in 2 of 6 starts, with it in 0 of 4. That alone says little; the proof is the bytecode export: `forAllModules` walks
`redirect$...$opalcompat$copy`, `private static synchronized`, so a copy made under the lock of the class, the same one as `addModule`.

## Phase 2 results (interrupted by a limit, 2026-11-03)

A run in a clean instance (`scripts/instance.py`, fresh jar, NeoForge 21.1.228, RTX 5060 Ti, validation layers on):

| Test | Result |
|---|---|
| Startup without a pack | works, validation only VMR's baseline |
| `test_final`, `test_composite`, `test_terrain` | work, the only new message is `Undefined-Value-ShaderOutputNotConsumed` |
| `test_compute` | works: setup, begin without a pass, prepare without a pass, composite, `final` + `final_a` |
| BSL, default settings, one scene, day | runs with no errors in the log, see `run/baseline/bsl_basic.png`; image quality still has to be judged |

Fixed by testing: `PackImage.upload` reached for `GraphicsQueue.INSTANCE`, which VMR never sets (`DeviceManager.getGraphicsQueue()`).
It also confirmed the fallback: the pack error ended in plain VMR and a text in `lastError`.

Compile verified: `checkPacks` and `-PdhVariant=true` clean on BSL, Complementary and Bliss. What ran afterwards is in Results on Vulkan.

## Decisions

- **Mod id `opalcompat`, package `dev.opalcompat`**; the taken code stays in `dev.vitrail.*` with a header
  about its origin, changes are marked `OpalCompat:`.
- **VMR as a release jar** (`libs/`, SHA-256 from release `mc-1.21.1-0618965`), no fork; a dependency on the
  exact version, because the mixins reach into VMR's internal classes.
- **Validation layers through the Vulkan loader** (`VK_ADD_LAYER_PATH`, `VK_INSTANCE_LAYERS`), because
  `Vulkan.ENABLE_VALIDATION_LAYERS` is a constant inlined into VMR's code.
- **Compilation:** shaderc from LWJGL 3.3.3 with options of our own, optimization `zero`, because `performance`
  removes `OpName` and stages are matched by name. The source goes off-heap because of its size (`MemoryStack`).
- **SPIR-V renumbering** (`compile.SpirvModule`): descriptors by name across stages into set 0,
  stage inputs onto the outputs of the previous stage by name. Vitrail's translator stays unchanged.
- **Runtime:** our own render passes, framebuffers and pipelines with MRT, because VMR has only 1 color
  attachment. We share VMR's device, `VulkanImage` and command buffer. The pipelines also declare VMR's dynamic states,
  otherwise binding them invalidates VMR's state.
- **Orientation:** targets have row 0 at the bottom like GL, no viewport flip; `final` goes to the swapchain
  with a flip, `frontFace = CLOCKWISE` because of Vulkan's framebuffer coordinates.
- **Depth:** the game's matrices are in OpenGL form and are published to the pack unchanged (`LevelCapture.glToReversed` is the conversion for
  `ViewMatrices`), the target has depth 0..1 through `ClipSpace.FORWARD`, and while a pack runs far is finite, as with Iris.
- **Terrain vertex format:** `DrawBuffers.VERTEX_SIZE`/`POS_OFFSET` are `static final` and `QuadSorter`
  compares the format by identity, so `CustomVertexFormat.COMPRESSED_TERRAIN` itself is extended at startup (stride 40 B),
  if there is a pack in the `shaderpacks` folder. `dev/vitrail/glsl/SodiumVertex.java`
  is a replacement of our own with the same API.
- **Frame order:** `beginLevel` → `beforeSky` (clear, begin, shadow: terrain, entities, copy of `shadowtex1`, translucents,
  `shadowcomp`, prepare) → sky → opaque terrain → entities and BERs → before the first translucent layer a copy of
  `depthtex2`, opaque hand, a copy of `depthtex1` and deferred → translucents → clouds, weather → at the end of `renderLevel`
  the translucent hand → `afterLevel` (composite, `final`, only then screen effects). VMR is interrupted through
  `temporaryRenderTarget()`.
- **Meshes outside terrain** (`MeshDraw`, `MeshPrograms`, `Families`): captured in `BufferUploader._drawWithShader` and `draw`
  and in `VBO.drawWithShader`. The program is decided by the shader's name, the phase and the vertex's owner. State (blend, depth, cull, polygon
  offset) is read from `VRenderSystem`, as the game set it. Bindings: 0 mesh, 1 one record of constants per draw for what the
  format lacks (instance rate), 2 a derived per-vertex stream (`EntityIds`, `MidTexCoord`, `Tangent`), only when the
  program really reads it (`SpirvModule` reports `read`) and the vertices are at hand.
- **What a pack does not handle, VMR draws into its own target and `final` overwrites it.** That is not an error, it is just missing from the image.
  Exception: a pack with no terrain program (e.g. only a color grade) gets VMR's image in its target (`seedFromGame`).
- **Vertex identity** (`DrawIds`, `BufferBuilderMixin`, `MeshDataMixin`): a batch mixes owners and is drawn later,
  so the ID is written with each vertex; mixed batches are split into runs in the original order (`MixedMesh`).
- **GUI** (`screen/ShaderPackScreen`, `screen/PackSettingsScreen`, `VOptionScreenMixin`): screens of our own on vanilla
  widgets, Vitrail's `screen/` is written against 26.x. The page model is the taken `pack/menu` (`PackMenu`, `MenuValues`)
  and `settings/PackSession`, the file is `SettingsFile`. The entry is a mixin into `VOptionScreen.addButtonsWithSearchBar`:
  `ModSettingsRegistry` can only do option pages, not a button and a pack's nested pages. Left click is the next value, right the
  previous, shift the default; Cancel leaves, Apply writes and reloads, Done both, as with Iris.
- **Pack failure:** an exception while loading or drawing leads to `report()`: the pack turns off, plain VMR draws, a toast, the full error
  text including shaderc diagnostics on the Shader Packs screen and a Copy Error button. After a change `PackRenderer.reload()`
  also rebuilds the mesh (`allChanged`), because `block.properties` is in it, as in `Iris.reload`.
- **Restart:** only when the game started with an empty `shaderpacks` folder. Everything else at run time: VMR reads the first 20 B of the extended
  vertex. The cost is double the terrain memory, even for a player with the pack turned off, if one is installed.
- **Device features** (`DeviceManagerMixin`): `independentBlend`, `geometryShader`,
  `fragmentStoresAndAtomics`, storage image formats and more, where the GPU has them.
- **Mipmaps:** `colortexNMipmapEnabled` are generated with a blit before the pass that wants them, on the side it
  reads (like Iris). Normals/specular without a resource pack get a neutral 1×1 texture.
- **Barriers** (`ImageLayouts`, `VulkanImageMixin`): transitions from/to `SHADER_READ_ONLY` and `GENERAL` go through
  `ALL_COMMANDS`, because VMR counts only on reads in the fragment stage and does not know `GENERAL`, and packs also read in the vertex stage. This also holds
  for VMR's images; the layout VMR tracks stays true. A global barrier only for a program with storage.
- **Compute** (`PackComputes`, `PackRenderer.dispatch`): the moments follow Iris. `setup` at each new target size
  (Iris: after construction and on `changed`, `dispatch(1, 1)`), `shadow` after clearing the map's depth over the map's resolution,
  `shadowcompN` before its pass over the screen size, `begin/prepare/deferred/composite/final` before their pass;
  a program with no pass (`TargetPlan.passing()`) at its moment, even if nothing draws after it in that phase. Like Iris:
  a barrier before each dispatch and after the whole group, `colorimg`/`colortex` on the halves of the given pass
  (`TargetSchedule.step`/`passing`), `shadowcolorimg`, `indirect.<stem>`. Zero groups means a pass that is off. A compute
  that does not compile or fails drops out on its own with a log, and the pack runs on (Iris would refuse the whole pack; a missing effect is
  less harm than a pack turned off). The shared barrier is `ALL_COMMANDS`: simple and correct, not the fastest.
- **Memory and descriptors** (`FrameArena`, `allocateSet`): grow by pages instead of failing, recycled after waiting
  on that slot's fence, not twice per frame (a mod may draw the level more than once).

## Mods

**Flywheel, Create.** VMR 0618965 contains `compat/sable/flywheel`, but `VulkanFlywheelEngine.render` draws nothing (it only
writes instances to memory) and the backend is behind `vulkanmod.compat.sable.flywheel.experimental`. Flywheel's default backends
want `GL 3.3`, VMR reports an emulated `3.2` (`GlFunctionRegistry.REPORTED_GL_VERSION`), so `GlCompat.SUPPORTS_*` is
false and Flywheel falls back to `flywheel:off` by itself; Create then draws through vanilla BERs and entities (`SuperByteBuffer` into
`MultiBufferSource`). That is the fallback and it needs no mixin. The render type of blocks (`rendertype_solid`, ...) under
an entity or BER is by Iris `MOVING_BLOCK`, so `gbuffers_block`, in the shadow `shadow_cutout`/`shadow_water`, a BER
in the shadow `shadow_block`. **A backend of our own** (Colorwheel) is not being done: Flywheel generates shaders from the mods' GLSL materials (over
1000 lines per program), Colorwheel wants further programs from the pack and stands on `glsl-transformer` (AGPL). The cost of the fallback is
CPU in large scenes, not a missing image. To check: `flywheel:off` in the log.

**Other mods.** Everything that goes through `RenderType` and `BufferUploader` is mapped by the shader's name (`Families`) without a
namespace, an unknown shader as an entity or BER depending on blending. Direct GL is looked for in the bytecode, `scripts/glscan.py <jar>`
(nested jars too). Mekanism 10.7.19: no GL, `GlStateManager` only in `MekanismRenderType`, `GuiGraph`, `GuiInsetElement`
(state, not drawing), its own shaders `rendertype_flame`, `rendertype_mekasuit`, `rendertype_sps` got programs as
in Iris (`ShaderAccess`). Create 6.0.10: GL only in Flywheel (backend, off) and in one Ponder class. **No Iris API shim:**
Create, Mekanism and DH call nothing from the Iris API without the mod id `iris`, Flywheel and DH look for it through `ModList`.
DH gets its own accessor (`IIrisAccessor`), other mods nothing. What a mod draws with direct GL outside this path ends up
in VMR's target, which `final` overwrites: missing from the image, no crash.

**Distant Horizons.** DH 3.3.3 has its rendering behind `AbstractDhRenderApiDefinition` (`IDhTerrainRenderer`, `IVertexBufferWrapper`
and more). `DhDependencySetupMixin` gives it `VkDhDefinition` instead of OpenGL: buffers are VMR's `VertexBuffer` (GPU, VMA), the terrain
is drawn by `PackRenderer.distantHalf` with the programs `dh_terrain`, `dh_water`, `dh_shadow`, the other passes (fog, SSAO, fade, TAA,
applying to MC) are empty, because the pack takes care of those. `DhMainMixin` adds `IIrisAccessor`, thanks to which DH
defers water, builds the shadow list without the frustum camera and draws differently without a pack; the id `iris` appears nowhere. Depth:
own `dhDepthTex0` and a copy `dhDepthTex1` before water, forward like everything else, the projection row from DH's planes.
Vertex of 16 B (`DistantVertex`), section corner through a dynamic offset of one UBO, one set per half, one index buffer.
`DISTANT_HORIZONS` is defined only with the bridge active. No fork and no run-time dependency: `libs/` is `compileOnly` only,
the mixins are applied by `OpalCompatMixinPlugin` only for the exact version 3.3.3 and without `-Dopalcompat.dh=false`. Without a pack LODs
are not drawn (there is no `dh_` program), nor are DH's clouds and beacons (`dh_generic`).

## Codex's work (branch `codex-work`, revision `39fd05e`)

The verbatim import is commit `bed96bd`, the fixes are separate. The zip had no `.git`, but it stood directly on `b65080c`, so the difference of the
trees is the whole work. The jar `opalcompat-0.1.0-alpha.jar` is byte-identical in all classes with a build of these
sources, only the line endings in `opalcompat.mixins.json` differ; that says nothing about whether it works.

**Accepted:** the `:jar` fix in `build.gradle`, `HandPass` (two hand phases by Iris), `MixedMesh` and `DrawIds`
(vertex identity), `ShadowFeatures`, graphics `shadowcomp` with its own flips (agreement with Iris verified),
`ShadowMaps`, `TextureImages`, `PackStorage`, `PackImage`, `ImageLayouts`, `StorageBuffer`, `applyViewport`,
deferred cleanup after an error (`fail`/`frameEnded`).

**Removed:** `VertexBufferMixin`. Injectors at HEAD run in the order of application: lower `order`, then lower priority.
A comment in it counted on the opposite, so it ran only after VMR's `VertexBufferM`, which always cancels. All dead code: neither sky nor
clouds ever reached the pack. It also kept a copy of every static VBO on the heap. Replaced by `VboMixin` on `VBO`.

**Fixed** (all statically, none of it had run):

- unset `renderStage` and `of_PassColour` (sun, moon, sky color), the directives `sun`, `moon`, `stars`, `sky`,
  `clouds`, `weather` were ignored;
- the entity overlay was bound to a white 1×1 and `texelFetch` read outside the image (black entities were a risk);
- hand depth in 0..0.125 instead of the 0.4375..0.5625 packs ask about (`depth < 0.56`);
- `depthtex2` was copied only after the hand; the substitute `UV0`, `UV1`, `UV2` were zeros (red overlay, darkness), now as in Iris;
- the block outline, cracks, world border and programs outside the plan (`gbuffers_beaconbeam`, `gbuffers_lightning`) ended up
  in VMR, so they vanished; polygon offset was ignored;
- the vanilla blob under an entity also went into the shadow map (now turned off like in Iris when a pack draws shadows);
- screen effects (fire, water) went through the hand program instead of after `final`;
- a global `ALL_COMMANDS` barrier at each descriptor write, so a GPU stall per draw;
- 112 B of constants per vertex with byte-wise zeroing (now one record per draw);
- `vkResetDescriptorPool` and rewriting the UBO on a second `renderLevel` in one frame;
- reading depth attached as an attachment (wrong layout), a 2D descriptor for `sampler3D`/`sampler1D`;
- `render type setup` inside an interrupted pass: under Fabulous it would start a VMR pass in the middle of ours (also in the original
  terrain code); all passes on one `MemoryStack` (64 kB, `OutOfMemoryError` with packs of dozens of passes);
- targets with `clear=false` read undefined data in the first frame (Iris does a full clear);
- `VK_IMAGE_USAGE_STORAGE_BIT` even for a format that can't do it; a missing texture or unsupported raw took down the whole pack
  (now a warning and black); `reload()` closed resources a frame in progress was still using; protection against
  `skipRendering` (minimizing would otherwise turn the pack off for good).

## Mixins

All verified against the bytecode (`javap` over `neoforge-21.1.228-merged.jar` and the VMR jar), including handler descriptors,
staticness, and that a name without a descriptor points to a single method. No mixin has `require = 0`; `defaultRequire: 1`
applies, so a wrong target brings the game down at startup.

| Mixin | Target | Remap |
|---|---|---|
| `LevelRendererMixin` | `renderLevel` (HEAD, before `renderSky`, before `RenderType.translucent()` in 2 branches, RETURN), `renderSky`, `renderClouds`, `renderSnowAndRain` | yes |
| `GameRendererMixin` | `render`, `renderItemInHand` (wrapper, `renderHandsWithItems`, `renderScreenEffect`) | yes |
| `GameRendererDepthFarMixin` | the call to `getDepthFar` in `getProjectionMatrix` | yes |
| `BufferUploaderMixin` | `_drawWithShader`, `draw` (order 1000, before VMR's `order = 2000`) | yes |
| `BufferBuilderMixin`, `MeshDataMixin` | `beginVertex`, `build`, a new field | yes |
| `EntityRenderDispatcherMixin` | `render`, `renderFlame`, static `renderShadow` | yes |
| `BlockEntityRenderDispatcherMixin`, `ItemRendererMixin`, `ItemInHandRendererMixin`, `ParticleEngineMixin` | `render`, `render`, `renderArmWithItem`, `render` (5 arguments) | yes |
| `GameRendererAccessor`, `LevelRendererAccessor`, `TextureAtlasAccessor`, `BiomesMixin` | `getFov`, `bobHurt`, `bobView`, `renderHand`, `renderEntity`, static `Biomes.register` | yes |
| `VboMixin`, `VboAccessor` | `VBO.drawWithShader(..., ShaderInstance)`, `VBO` fields | no |
| `VulkanImageMixin` | static `VulkanImage.transitionImageLayout(MemoryStack, VkCommandBuffer, VulkanImage, int)` | no |
| `VTextureSelectorMixin` | static `VTextureSelector.uploadSubTexture` (HEAD) | no |
| `RendererMixin`, `RendererAccessor` | `Renderer.endFrame`, `appliedDepthBiasUnits/Factor` | no |
| `WorldRendererMixin`, `WorldRendererAccessor`, `DrawBuffersAccessor`, `DrawParametersAccessor` | VMR's terrain | no |
| `CustomVertexFormatMixin`, `TerrainBufferBuilderMixin`, `DeviceManagerMixin` | format, builder, `createLogicalDevice` | no |

`@WrapMethod` and `@WrapOperation` need MixinExtras 0.4+, NeoForge 21.1.228 carries 0.5.3 and VMR uses them too.

The package `compat` has its own config with `required: false` and `@Pseudo`, a change in another mod must not bring it down: `OpalOnVulkanMixin` and
`VectorsOnVulkanMixin` only with VMR, `OpalImagesMixin`, `OpalImageMixin` and `FlywheelShaderPackMixin` only without it, `EveryCompatModulesMixin` and `DhChunkyMixin`
whenever their target is in the game.

## Not verified at run time

What ran is in Results on Vulkan. Consciously left out or not run:

- Opal on OpenGL with Create, Mekanism and DH: ran only with Opal and Vectors, one scene. `FlywheelShaderPackMixin` did not run in the game
  and Opal has no `dh_*` programs. The user's decision: Vulkan is enough.
- A run without VMR after merging into one mod: it last ran as two jars. Now it is verified only statically (`scripts/release.py` makes sure
  that the entry classes and the OpenGL part do not name VMR's classes). The user's decision: no tests on OpenGL.
- FPS measurements and comparison with Iris: the user's decision. The one number in passing: pack and all mods with DH, 854x480, 80 FPS, 1% low 36.
- The VMR jar from the user's instance differs from the one on GitHub (5e4b09e8, 20,537,547 B against 61a1c215, 20,537,626 B), same version. The tests ran
  on the one from GitHub.
- The Stellarity set: ran without a shader pack, with G1 and the VMR jar from GitHub (it differs from the user's jar in a single GUI class,
  `DiagnosticsPageContent$DiagnosticsList`). A pack on this set, ZGC with FerriteCore and longer play are not verified. Nor is whether DH really gets Chunky's
  events after startup: only joining a world without a crash is verified.
- A large scene in Create, a formed multiblock in Mekanism (the dynamic tank stands, forming not verified), world border, cracks when mining,
  the fire screen effect, glow.

There are no `TODO-VERIFY` marks in the code. Hand and entity shadows are verified against `refs/Iris` (`pathways/HandRenderer`,
`shadows/ShadowRenderer`): Iris does the second drawing of entities the same way, so a mod that minds it has the same problem under Iris.
Fixed during that check: entity shadows sort by type (fewer batches), take tick delta like vanilla (`isEntityFrozen`, BER
`false`), `shadowPlayer` also takes the vehicle and passengers; the hand asks about the sleep of the camera entity, not the player.

## Known problems and limitations

- VMR 0.3.5's validation baseline, not caused by us: `VUID-vkQueueSubmit-pSignalSemaphores-00067`,
  `VUID-vkDestroyDevice-device-05137`. Further things from VMR and mods are in `BLOCKERS.md`.
- The warning `Undefined-Value-ShaderOutputNotConsumed`: Vitrail's `ofCoverage` output has no attachment.
  `Undefined-Value-OutputNotWritten` (1 to 2 with BSL and Bliss) is not investigated, it does not show in the image.
- At night with DH part of the sky has a purple haze with a sharper edge. It moves with the world, not with the screen, so it is not from overlapping targets;
  whether it belongs to the pack is not verified. BSL has a white horizon, the other packs do not.
- Only sections compiled by the camera's graph go into the shadow, VMR has no others.
- `centerDepth` reads depth directly, without smoothing. `normals`/`specular` are neutral for now, PBR atlases are not loaded.
- Raw textures: packed, BGR, integer and rectangle read black for now. VBOs with `transientGeometry` are drawn by VMR.
- A shadow program reading the same map it writes gets `shadowtex1`.
- `Vk.storageUsage`: a pack that binds as `colorimg` a target in a format without storage support fails when writing the descriptor.

## What is left

1. What is listed in "Not verified at run time".
2. Possibly: LODs without a pack, `dh_generic`, a Flywheel backend of our own.

## Reference: Iris-Vulkan-Port

`refs/Iris-Vulkan-Port`, commit `47babb4`. **LGPL-3.0**, but it depends on `glsl-transformer` (**AGPL-3.0**),
so nothing of the transformation part is taken. It targets Fabric and its own modified VulkanMod 0.5.9 with MRT.
- **Depth/stencil:** `depthtex0` is the depth of the main target shared with vanilla; we have our own D32,
  because VMR draws with a flip and our targets do not.
- **Framebuffers:** their VulkanMod can do MRT and draws terrain itself; we have our own passes and draw loop with no fork.
- **SPIR-V and uniforms:** they assign bindings in the text and have a manual UBO; we rewrite decorations by name.
- **Terrain attributes:** with them constants and a normal from `dFdx/dFdy`; with us real attributes. Consciously not taken.

## Taken code

- `src/main/java/dev/vitrail/**`: group a from Vitrail (commit `7a3cdcb`) + boundary `PackValues`,
  `ViewMatrices`, `CapturedProjection`, `ShadowAmortisation`, `ShadowCullPlan`, `BlockStateIds`,
  `PackNameIds`, `BiomeClassifier`. See `scripts/take_from_vitrail.py`. Java 21 (`_` → `unusedN`), API 1.21.1.
- Replacements of our own for Vitrail's files: `glsl/GameTransforms`, `glsl/SodiumVertex`, `render/FrameState`
  (port to 1.21.1), `FaceShading`, `HandDraw`, `ShadowTargets`, `PackImages`.
- Changes in Vitrail after taking it: `PackProgram.loadShadowComposites`, `ShaderProperties.viewportScales`, a note
  in `TargetPlan` (Codex, marked `OpalCompat:`).
- After the behaviour of Iris 1.21.1 (LGPL-3.0), with no copied code, see [NOTICE](NOTICE): `HandPass`, `ShadowFeatures`, `DrawIds`,
  `ItemInHandRendererMixin`, `TextureImages`, `Families`, the flips in `ShadowMaps`.
- License LGPL-3.0-only, `NOTICE-vitrail` is unchanged.

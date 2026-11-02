# PORTING.md

Deník portu: stav, rozhodnutí, známé problémy a původ převzatého kódu. Výchozí analýza je
v [ANALYSIS.md](ANALYSIS.md).

## Stav

| Položka | Stav |
|---|---|
| 1 Reference a build | hotovo |
| 2 Mixiny a runtime | hotovo |
| 3 Optiony, noisetex, mipmapy, blendy | hotovo, vizuálně neověřeno |
| 4 Stíny | – |
| 5 Obloha, entity, ruka, částice, počasí, mraky | – |
| 6 Custom textury a compute | – |
| 7 GUI výběru packu a nastavení | – |

**Obnova 2. 11. 2026:** původní složka `vulkan-shaders` i s lokální git historií (M1–M5) zmizela
z disku (nebyla ani v Koši). Projekt byl obnoven z konverzace a stejných revizí referencí, historie
začíná znovu. `ANALYSIS.md` je obnovená ve zkrácené podobě.

## Jak spustit

- Java 21: systémový `JAVA_HOME` na tomhle PC ukazuje na JDK 8; `scripts/autotest.sh` si nastaví
  `C:\Program Files\Zulu\zulu-21`. Při ručním spuštění Gradlu ho nastav taky.
- `scripts/autotest.sh [pack] [-Pautotest.options=A=b,C=d] [-Pautotest.camera=x,z,yaw,pitch,výška]
  [-Pautotest.time=6000]`: build, klient v autotest režimu, souhrn. Výstupy jsou v `run/autotest/`.
- `./gradlew checkPacks [-Ppacks=...] [-PdumpAll=true]`: mimoherní překlad a kompilace všech programů.
- `scripts/pixel.py soubor.png x,y ...`: hodnoty pixelů bez závislostí.
- `scripts/take_from_vitrail.py`: převzetí skupiny a z Vitrailu do `src/main/java/dev/vitrail`.
- `-Pvalidation=false` vypne validation layers (`tools/vvl`, Khronos nightly, mimo git).

## Rozhodnutí

- **Mod id `vitrailvk`, balík `dev.vitrailvk`**; převzatý kód zůstává v `dev.vitrail.*` s hlavičkou
  o původu, úpravy jsou označené `VitrailVK:`.
- **VMR jako release jar** (`libs/`, SHA-256 z releasu `mc-1.21.1-0618965`), žádný fork; závislost na
  přesné verzi, protože mixiny sahají do interních tříd VMR.
- **Validation layers přes Vulkan loader** (`VK_ADD_LAYER_PATH`, `VK_INSTANCE_LAYERS`), protože
  `Vulkan.ENABLE_VALIDATION_LAYERS` je ve VMR konstanta vložená do kódu.
- **Kompilace:** shaderc z LWJGL 3.3.3 s vlastními options, optimalizace `zero`, protože `performance`
  maže `OpName` a stage se párují podle jmen. Zdroják jde off-heap kvůli velikosti (`MemoryStack`).
- **Přečíslování SPIR-V** (`compile.SpirvModule`): deskriptory podle jména napříč stagemi do setu 0,
  vstupy stage na výstupy předchozí stage podle jména. Vitrailův překladač zůstává beze změny.
- **Runtime:** vlastní render passy, framebuffery a pipeline s MRT, protože VMR má jen 1 color
  attachment. Sdílíme device, `VulkanImage` a command buffer VMR. Pipeline deklarují i dynamické stavy
  VMR, jinak jejich bind zneplatní stav VMR.
- **Orientace:** targety mají řádek 0 dole jako GL, bez flipu viewportu; `final` jde do swapchainu
  s flipem, `frontFace = CLOCKWISE` kvůli framebufferovým souřadnicím Vulkanu.
- **Hloubka:** VMR kreslí forward 0..1. `LevelCapture.forwardToReversed` převádí projekci pro
  `ViewMatrices`, konvence targetů je `ClipSpace.FORWARD`.
- **Vertex formát terénu:** `DrawBuffers.VERTEX_SIZE`/`POS_OFFSET` jsou `static final` a `QuadSorter`
  porovnává formát identitou, proto se při startu rozšiřuje přímo
  `CustomVertexFormat.COMPRESSED_TERRAIN` (stride 40 B). **Zapnutí/vypnutí packu vyžaduje restart**,
  přepnutí mezi packy ne. `dev/vitrail/glsl/SodiumVertex.java` je vlastní náhrada se stejným API.
- **Snímek:** clear a begin/prepare při prvním kreslení; terén vlastní smyčkou nad `ChunkArea`/
  `DrawBuffers` (accessory do VMR); před první průsvitnou vrstvou kopie `depthtex1` a deferred; po
  levelu kopie `depthtex2`, zbytek řetězce a `final`. VMR se přerušuje přes `temporaryRenderTarget()`.
- **Optiony:** `config/vitrailvk.properties` (`shaderPack`, `profile`, `option.NAME`), přednost mají
  systémové properties `vitrailvk.pack` a `vitrailvk.options`.
- **Device features** (`DeviceManagerMixin`): `independentBlend`, `geometryShader`,
  `fragmentStoresAndAtomics`, storage image formáty a další, kde je GPU má.
- **Mipmapy:** `colortexNMipmapEnabled` se generují blitem před passem, který je žádá, na straně, kterou
  čte (jako Iris). Normály/specular bez resource packu dostávají neutrální 1×1 texturu.

## Reference: Iris-Vulkan-Port

`refs/Iris-Vulkan-Port`, commit `47babb4`. **LGPL-3.0**, ale závisí na `glsl-transformer` (**AGPL-3.0**),
takže z transformační části nic nepřebíráme. Cílí na Fabric a vlastní upravený VulkanMod 0.5.9 s MRT.
- **Depth/stencil:** `depthtex0` je depth hlavního targetu sdílený s vanillou; my máme vlastní D32,
  protože VMR kreslí s flipem a naše targety ne.
- **Framebuffery:** jejich VulkanMod umí MRT a terén kreslí sám; my máme vlastní passy a draw loop bez forku.
- **SPIR-V a uniformy:** bindingy přidělují v textu a mají ruční UBO; my přepisujeme dekorace podle jmen.
- **Terénní atributy:** u nich konstanty a normála z `dFdx/dFdy`; u nás skutečné atributy. Vědomě nepřevzato.

## Převzatý kód

- `src/main/java/dev/vitrail/**`: skupina a z Vitrailu (commit `7a3cdcb`) + hraniční `PackValues`,
  `ViewMatrices`, `CapturedProjection`, `ShadowAmortisation`, `ShadowCullPlan`, `BlockStateIds`,
  `PackNameIds`, `BiomeClassifier`. Viz `scripts/take_from_vitrail.py`. Java 21 (`_` → `unusedN`), API 1.21.1.
- Vlastní náhrady Vitrailových souborů: `glsl/GameTransforms`, `glsl/SodiumVertex`, `render/FrameState`
  (port na 1.21.1), `FaceShading`, `HandDraw`, `ShadowTargets`, `PackImages`.
- Licence LGPL-3.0-only, `NOTICE-vitrail` je beze změny.

## Známé problémy

- Baseline validace VMR 0.3.5 bez našeho vlivu: `VUID-vkQueueSubmit-pSignalSemaphores-00067`,
  `VUID-vkDestroyDevice-device-05137`.
- Varování `Undefined-Value-ShaderOutputNotConsumed`: výstup `ofCoverage` z Vitrailu nemá attachment.

## Vizuálně neověřeno (projít v závěrečném testování)

- Noisetex, mipmapy, blend direktivy, optiony z configu; `MC_VERSION`/vendor/biomy v `Machine`.
- BSL a Complementary end-to-end.

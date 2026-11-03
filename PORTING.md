# PORTING.md

Deník portu: stav, rozhodnutí, známé problémy a původ převzatého kódu. Výchozí analýza je
v [ANALYSIS.md](ANALYSIS.md).

## Stav

Pozor: od položky 3 dál nic neběželo ve hře (pokyn „netestuj“). „Hotovo v kódu“ znamená přeloženo a staticky
ověřeno proti zdrojům, ne funkční. Poslední skutečný běh je `test_terrain` před commitem `f79800c`.

| Položka | Stav |
|---|---|
| 1 Reference a build | hotovo, `./gradlew build` prochází |
| 2 Mixiny a runtime | hotovo, běželo ve hře (`test_final`, `test_composite`, `test_terrain`) |
| 3 Optiony, noisetex, mipmapy, blendy | hotovo v kódu |
| 4 Stíny: terén, entity, block entity, `shadowcomp` (grafické), mipmapy | hotovo v kódu |
| 5 Obloha, entity, ruka, částice, počasí, mraky | hotovo v kódu |
| 6 Custom textury, image.*, SSBO, compute | hotovo v kódu |
| 7 GUI výběru packu a nastavení | nezačato |
| A Flywheel, Create, Colorwheel | nezačato |
| B Ostatní mody včetně Mekanismu | obecná cesta entit a BER existuje, mapování nezačato |
| C Distant Horizons včetně `dh_*` | nezačato (`dh/DhDepth`, `glsl/DistantVertex` jsou převzaté a nezapojené) |

**Obnova 2. 11. 2026:** původní složka `vulkan-shaders` i s lokální git historií (M1–M5) zmizela
z disku (nebyla ani v Koši). Projekt byl obnoven z konverzace a stejných revizí referencí, historie
začíná znovu. `ANALYSIS.md` je obnovená ve zkrácené podobě.

## Jak buildnout a spustit

- Java 21: systémový `JAVA_HOME` na tomhle PC ukazuje na JDK 8. Nastav `C:\Program Files\Zulu\zulu-21`.
- `./gradlew build`: `build/libs/vitrailvk-<verze>.jar`. VMR se při prvním buildu stáhne do `libs/` a ověří se
  jeho SHA-256 (`gradle.properties`).
- `./gradlew checkPacks [-Ppacks=...] [-PdumpAll=true]`: bez hry přeloží a zkompiluje všechny programy packů
  v `run/shaderpacks`, včetně rodin meshů tak, jak je načítá renderer (entity, obloha, částice, `shadow_entities`),
  grafických `shadowcomp` a compute. Kontroluje i to, že každý vstup vertexu má zdroj. Dřív u cesty s `.csh` i `.fsh`
  přeskakoval grafickou půlku.
- `scripts/autotest.sh [pack] [-Pautotest.options=A=b,C=d] [-Pautotest.camera=x,z,yaw,pitch,výška]
  [-Pautotest.time=6000]`: build, klient v autotest režimu, souhrn. Výstupy jsou v `run/autotest/`.
- `scripts/pixel.py soubor.png x,y ...`: hodnoty pixelů bez závislostí.
- `scripts/take_from_vitrail.py`: převzetí skupiny a z Vitrailu do `src/main/java/dev/vitrail`. **Pozor:** přepsal by
  úpravy v `PackProgram`, `ShaderProperties` a `TargetPlan` (značené `VitrailVK:`), znovu je pak dopiš ručně.
- `-Pvalidation=false` vypne validation layers (`tools/vvl`, Khronos nightly, mimo git).

Pack se vybírá v `config/vitrailvk.properties` (`shaderPack`, `profile`, `option.NAME`) nebo přes systémové
vlastnosti `vitrailvk.pack` a `vitrailvk.options`. GUI zatím není.

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
- **Pořadí snímku:** `beginLevel` → `beforeSky` (clear, begin, stín: terén, entity, kopie `shadowtex1`, průsvitné,
  `shadowcomp`, prepare) → obloha → neprůhledný terén → entity a BER → před první průsvitnou vrstvou kopie
  `depthtex2`, neprůhledná ruka, kopie `depthtex1` a deferred → průsvitné → mraky, počasí → na konci `renderLevel`
  průsvitná ruka → `afterLevel` (composite, `final`, teprve pak efekty přes obraz). VMR se přerušuje přes
  `temporaryRenderTarget()`.
- **Meshe mimo terén** (`MeshDraw`, `MeshPrograms`, `Families`): záchyt v `BufferUploader._drawWithShader` a `draw`
  a v `VBO.drawWithShader`. Program určí jméno shaderu, fáze a vlastník vrcholu. Stav (blend, depth, cull, polygon
  offset) se čte z `VRenderSystem`, jak ho hra nastavila. Vázání: 0 mesh, 1 jeden záznam konstant na draw pro to, co
  formát nemá (instance rate), 2 odvozený proud na vrchol (`EntityIds`, `MidTexCoord`, `Tangent`), jen když ho
  program opravdu čte (`SpirvModule` hlásí `read`) a vrcholy jsou po ruce.
- **Co pack neobslouží, kreslí VMR do svého targetu a `final` to přepíše.** Není to chyba, jen to chybí v obraze.
  Výjimka: pack bez programu pro terén (např. jen color grade) dostane obraz VMR do targetu (`seedFromGame`).
- **Identita vrcholu** (`DrawIds`, `BufferBuilderMixin`, `MeshDataMixin`): dávka míchá vlastníky a kreslí se později,
  proto se ID zapisuje u každého vrcholu; smíšené dávky se dělí na běhy v původním pořadí (`MixedMesh`).
- **Optiony:** `config/vitrailvk.properties`, přednost mají systémové properties.
- **Device features** (`DeviceManagerMixin`): `independentBlend`, `geometryShader`,
  `fragmentStoresAndAtomics`, storage image formáty a další, kde je GPU má.
- **Mipmapy:** `colortexNMipmapEnabled` se generují blitem před passem, který je žádá, na straně, kterou
  čte (jako Iris). Normály/specular bez resource packu dostávají neutrální 1×1 texturu.
- **Barriéry** (`ImageLayouts`, `VulkanImageMixin`): přechody z/do `SHADER_READ_ONLY` a `GENERAL` jdou přes
  `ALL_COMMANDS`, protože VMR počítá jen se čtením ve fragmentu a `GENERAL` neumí, a packy čtou i ve vertexu. Platí
  i pro obrázky VMR; layout, který VMR sleduje, zůstává pravdivý. Globální bariéra jen pro program se storage.
- **Compute** (`PackComputes`, `PackRenderer.dispatch`): okamžiky podle Iris. `setup` při každé nové velikosti targetů
  (Iris: po konstrukci a při `changed`, `dispatch(1, 1)`), `shadow` po clearu hloubky mapy přes rozlišení mapy,
  `shadowcompN` před svým průchodem přes rozměr obrazovky, `begin/prepare/deferred/composite/final` před svým průchodem;
  program bez průchodu (`TargetPlan.passing()`) ve svém okamžiku, i když po něm v dané fázi nic nekreslí. Jako Iris:
  bariéra před každým dispatchem a za celou skupinou, `colorimg`/`colortex` na půlkách daného průchodu
  (`TargetSchedule.step`/`passing`), `shadowcolorimg`, `indirect.<stem>`. Nula skupin znamená vypnutý průchod. Compute,
  který se nepřeloží nebo selže, vypadne sám a s logem, pack běží dál (Iris by odmítl celý pack; chybějící efekt je
  menší škoda než vypnutý pack). Sdílená bariéra je `ALL_COMMANDS`: jednoduché a správné, ne nejrychlejší.
- **Paměť a deskriptory** (`FrameArena`, `allocateSet`): rostou po stránkách místo selhání, recyklují se po čekání
  na fence daného slotu, ne dvakrát za snímek (mod může kreslit level víckrát).

## Práce Codexu (větev `codex-work`, revize `39fd05e`)

Import doslova je commit `bed96bd`, opravy jsou zvlášť. Zip neměl `.git`, ale stál přímo na `b65080c`, takže rozdíl
stromů je celá práce. Jar `vitrailvk-0.1.0-alpha.jar` je bajt po bajtu shodný ve všech třídách s buildem těchto
zdrojů, liší se jen konce řádků v `vitrailvk.mixins.json`; o funkčnosti nic neříká.

**Přijato:** oprava `:jar` v `build.gradle`, `HandPass` (dvě fáze ruky podle Iris), `MixedMesh` a `DrawIds`
(identita vrcholů), `ShadowFeatures`, grafické `shadowcomp` s vlastními flipy (shoda s Iris ověřena),
`ShadowMaps`, `TextureImages`, `PackStorage`, `PackImage`, `ImageLayouts`, `StorageBuffer`, `applyViewport`,
odložený úklid po chybě (`fail`/`frameEnded`).

**Odstraněno:** `VertexBufferMixin`. Injektory při HEAD běží v pořadí aplikace: nižší `order`, pak nižší priorita.
Komentář v něm počítal s opakem, takže běžel až po `VertexBufferM` z VMR, který vždy ruší. Celý mrtvý kód: obloha ani
mraky se nikdy nedostaly k packu. Navíc držel kopii každého statického VBO na haldě. Nahrazeno `VboMixin` na `VBO`.

**Opraveno** (vše staticky, nic z toho neběželo):

- nenastavené `renderStage` a `of_PassColour` (slunce, měsíc, barva oblohy), direktivy `sun`, `moon`, `stars`, `sky`,
  `clouds`, `weather` se ignorovaly;
- overlay entit se vázal na bílou 1×1 a `texelFetch` četl mimo obraz (hrozily černé entity);
- hloubka ruky v 0..0,125 místo 0,4375..0,5625, na který se packy ptají (`depth < 0.56`);
- `depthtex2` se kopírovala až po ruce; náhradní `UV0`, `UV1`, `UV2` byly nuly (červený overlay, tma), teď jako Iris;
- obrys bloku, praskliny, hranice světa a programy mimo plán (`gbuffers_beaconbeam`, `gbuffers_lightning`) skončily
  ve VMR, tedy zmizely; polygon offset se ignoroval;
- vanilla blob pod entitou šel i do stínové mapy (teď se vypíná jako v Iris, když pack kreslí stíny);
- efekty přes obraz (oheň, voda) šly přes program ruky místo až po `final`;
- globální bariéra `ALL_COMMANDS` při každém zápisu deskriptorů, tedy zastavení GPU pro každý draw;
- 112 B konstant na vrchol s nulováním po bajtech (teď jeden záznam na draw);
- `vkResetDescriptorPool` a přepis UBO při druhém `renderLevel` v jednom snímku;
- čtení depth přichycené jako attachment (špatný layout), deskriptor 2D pro `sampler3D`/`sampler1D`;
- `render type setup` uvnitř přerušeného průchodu: pod Fabulous by spustil pass VMR uprostřed našeho (i v původním
  kódu terénu); všechny průchody na jednom `MemoryStack` (64 kB, `OutOfMemoryError` u packů s desítkami passů);
- targety s `clear=false` četly v prvním snímku nedefinovaná data (Iris dělá plný clear);
- `VK_IMAGE_USAGE_STORAGE_BIT` i pro formát, který ho neumí; chybějící textura nebo nepodporovaný raw shodil celý pack
  (teď varování a černá); `reload()` zavíral prostředky, které ještě používal rozepsaný snímek; ochrana před
  `skipRendering` (minimalizace by jinak pack trvale vypnula).

## Mixiny

Vše je ověřené proti bajtkódu (`javap` nad `neoforge-21.1.228-merged.jar` a jarem VMR), včetně descriptorů handlerů,
statičnosti a toho, že jméno bez descriptoru ukazuje na jedinou metodu. Žádný mixin nemá `require = 0`; platí
`defaultRequire: 1`, takže špatný cíl shodí hru při startu.

| Mixin | Cíl | Remap |
|---|---|---|
| `LevelRendererMixin` | `renderLevel` (HEAD, před `renderSky`, před `RenderType.translucent()` ve 2 větvích, RETURN), `renderSky`, `renderClouds`, `renderSnowAndRain` | ano |
| `GameRendererMixin` | `render`, `renderItemInHand` (obal, `renderHandsWithItems`, `renderScreenEffect`) | ano |
| `BufferUploaderMixin` | `_drawWithShader`, `draw` (order 1000, před `order = 2000` z VMR) | ano |
| `BufferBuilderMixin`, `MeshDataMixin` | `beginVertex`, `build`, nové pole | ano |
| `EntityRenderDispatcherMixin` | `render`, `renderFlame`, statické `renderShadow` | ano |
| `BlockEntityRenderDispatcherMixin`, `ItemRendererMixin`, `ItemInHandRendererMixin`, `ParticleEngineMixin` | `render`, `render`, `renderArmWithItem`, `render` (5 argumentů) | ano |
| `GameRendererAccessor`, `LevelRendererAccessor`, `TextureAtlasAccessor`, `BiomesMixin` | `getFov`, `bobHurt`, `bobView`, `renderHand`, `renderEntity`, statické `Biomes.register` | ano |
| `VboMixin`, `VboAccessor` | `VBO.drawWithShader(..., ShaderInstance)`, pole `VBO` | ne |
| `VulkanImageMixin` | statické `VulkanImage.transitionImageLayout(MemoryStack, VkCommandBuffer, VulkanImage, int)` | ne |
| `RendererMixin`, `RendererAccessor` | `Renderer.endFrame`, `appliedDepthBiasUnits/Factor` | ne |
| `WorldRendererMixin`, `WorldRendererAccessor`, `DrawBuffersAccessor`, `DrawParametersAccessor` | terén VMR | ne |
| `CustomVertexFormatMixin`, `TerrainBufferBuilderMixin`, `DeviceManagerMixin` | formát, builder, `createLogicalDevice` | ne |

`@WrapMethod` a `@WrapOperation` potřebují MixinExtras 0.4+, NeoForge 21.1.228 nese 0.5.3 a VMR je používá také.

## Neověřeno za běhu

Pořadí k vyzkoušení při prvním spuštění (`-Pvalidation=true`, sledovat `run/autotest/validation.log`):

1. Hra se spustí bez packu: start potvrdí mixiny, nové jsou `VboMixin`, `VboAccessor`, `RendererAccessor` a cíle Codexu.
2. `test_terrain`: nic se nerozbilo oproti poslednímu běhu, validace jen baseline VMR.
3. BSL s výchozími optiony, den, venku: obloha, slunce, mraky, stíny terénu, voda.
4. Entity a ruka: nejsou černé, zásah zčervená, ruka nezajíždí do bloků; entity vrhají stín, blob pod nimi není.
5. Obrys bloku, praskliny při kopání, hranice světa, částice, déšť, efekt ohně přes obraz, záře (glowing).
6. `Fabulous` grafika a změna velikosti okna a minimalizace.
7. Complementary a pack s custom texturami, image.*, SSBO; pack s grafickým `shadowcomp`.
8. Sledovat v logu `no program of the pack serves it`, `could not be loaded`, `reads ... from the half it writes`.

Značky `TODO-VERIFY` v kódu nejsou. Ruka i stíny entit jsou ověřené proti `refs/Iris` (`pathways/HandRenderer`,
`shadows/ShadowRenderer`): druhé vykreslení entit dělá Iris stejně, takže mod, kterému vadí, má stejný problém i pod Iris.
Při ověření opraveno: stíny entit řadí podle typu (méně dávek), berou tick delta jako vanilla (`isEntityFrozen`, BER
`false`), `shadowPlayer` bere i vozidlo a pasažéry; ruka se ptá na spánek entity kamery, ne hráče.

## Známé problémy a omezení

- Baseline validace VMR 0.3.5 bez našeho vlivu: `VUID-vkQueueSubmit-pSignalSemaphores-00067`,
  `VUID-vkDestroyDevice-device-05137`.
- Varování `Undefined-Value-ShaderOutputNotConsumed`: výstup `ofCoverage` z Vitrailu nemá attachment.
- Do stínu jdou jen sekce zkompilované grafem kamery, VMR jiné nemá.
- `centerDepth` čte depth přímo, bez vyhlazení. `normals`/`specular` zatím neutrální, atlasy PBR se nenačítají.
- Raw textury: packed, BGR, integer a rectangle zatím čtou černou. VBO s `transientGeometry` kreslí VMR.
- Zapnutí/vypnutí packu vyžaduje restart. Shadow program čtoucí stejnou mapu, kterou zapisuje, dostane `shadowtex1`.
- `Vk.storageUsage`: pack, který váže jako `colorimg` target ve formátu bez storage podpory, selže při zápisu deskriptoru.

## Co zbývá

1. **GUI.** `dev/vitrail/pack/menu` je převzaté, `ShaderPacks.select`/`saveOptions` a `PackRenderer.reload` existují.
   Chybí vstup ve video nastavení, obrazovky a rozlišení změny, která vyžaduje restart (jiný stride).
2. **Flywheel, Create, Colorwheel.** VMR 0618965 má `compat/sable/flywheel` (`VulkanFlywheelBackend`,
   `VulkanFlywheelBootstrap`), experimentální za vlastností `vulkanmod.compat.sable.flywheel.experimental`. To dokládá
   cestu, ne napojení na pack. Zdroje těch tří modů v `refs/` nejsou. Záchrana: Flywheel vypnout, Create přes vanilla BER.
3. **Ostatní mody.** Mapování modovaných vrstev (Mekanism a další) na programy, případně minimální shim Iris API.
4. **Distant Horizons.** Rozhraní DH, geometrie, matice, hloubka, `dh_terrain`, `dh_water`, `dh_shadow`, bez forku.

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
- Úpravy ve Vitrailu po převzetí: `PackProgram.loadShadowComposites`, `ShaderProperties.viewportScales`, poznámka
  v `TargetPlan` (Codex, značené `VitrailVK:`).
- Podle chování Iris 1.21.1 (LGPL-3.0), bez kopie kódu, viz [NOTICE](NOTICE): `HandPass`, `ShadowFeatures`, `DrawIds`,
  `ItemInHandRendererMixin`, `TextureImages`, `Families`, flipy v `ShadowMaps`.
- Licence LGPL-3.0-only, `NOTICE-vitrail` je beze změny.

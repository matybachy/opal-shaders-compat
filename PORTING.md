# PORTING.md

Deník portu: stav, rozhodnutí, známé problémy a původ převzatého kódu. Výchozí analýza je
v [ANALYSIS.md](ANALYSIS.md).

Mod se do verze 0.3.0 jmenoval Vitrail VK a vedle něj byl Vitrail Bridge. Od 0.4.0 je to jeden mod `opalcompat`, na přání uživatele.
Starší commity, tagy a jary v `release/` nesou stará jména. Převzatý kód z Vitrailu zůstává v `dev/vitrail` a jeho původ je v `NOTICE`.

## Stav

Pozor: „hotovo v kódu“ znamená přeloženo a staticky ověřeno proti zdrojům, ne funkční. Co opravdu běželo ve hře, je
v části Výsledky Fáze 2.

| Položka | Stav |
|---|---|
| 1 Reference a build | hotovo, `./gradlew build` prochází |
| 2 Mixiny a runtime | hotovo, běželo ve hře (`test_final`, `test_composite`, `test_terrain`) |
| 3 Optiony, noisetex, mipmapy, blendy | hotovo v kódu |
| 4 Stíny: terén, entity, block entity, `shadowcomp` (grafické), mipmapy | hotovo v kódu |
| 5 Obloha, entity, ruka, částice, počasí, mraky | hotovo v kódu |
| 6 Custom textury, image.*, SSBO, compute | hotovo v kódu |
| 7 GUI výběru packu a nastavení | hotovo v kódu |
| A Flywheel, Create | záchranná varianta hotová v kódu: Flywheel off, Create přes BER; vlastní backend ne |
| B Ostatní mody včetně Mekanismu | hotovo v kódu: mapování podle tabulky Iris, bajtkód zkontrolovaný |
| C Distant Horizons včetně `dh_*` | hotovo, běželo ve hře s LODy, viz Výsledky na Vulkanu |
| D Opal Compat: Opal a Vectors na VMR, společné GUI | hotovo, běželo ve hře, viz níže |

**Obnova 2. 11. 2026:** původní složka `vulkan-shaders` i s lokální git historií (M1–M5) zmizela
z disku (nebyla ani v Koši). Projekt byl obnoven z konverzace a stejných revizí referencí, historie
začíná znovu. `ANALYSIS.md` je obnovená ve zkrácené podobě.

## Jak buildnout a spustit

- Java 21: systémový `JAVA_HOME` na tomhle PC ukazuje na JDK 8. Nastav `C:\Program Files\Zulu\zulu-21`.
- `./gradlew build`: `build/libs/opalcompat-<verze>.jar`. VMR se při prvním buildu stáhne do `libs/` a ověří se jeho SHA-256
  (`gradle.properties`).
- `scripts/release.py "text"`: po commitu a buildu ověří metadata jaru, porovná třídy s jarem, na kterém běžely testy, a zkopíruje
  ho do `release/` se zápisem do `release/LATEST.txt`.
- `./gradlew checkPacks [-Ppacks=...] [-PdumpAll=true]`: bez hry přeloží a zkompiluje všechny programy packů
  v `run/shaderpacks`, včetně rodin meshů tak, jak je načítá renderer (entity, obloha, částice, `shadow_entities`),
  grafických `shadowcomp` a compute. Kontroluje i to, že každý vstup vertexu má zdroj. Dřív u cesty s `.csh` i `.fsh`
  přeskakoval grafickou půlku.
- `scripts/autotest.sh [pack] [-Pautotest.options=A=b,C=d] [-Pautotest.camera=x,z,yaw,pitch,výška]
  [-Pautotest.time=6000]`: build, klient v autotest režimu, souhrn. Výstupy jsou v `run/autotest/`.
- `scripts/instance.py create|run NAME`: čistá instance s NeoForge v `test-instance/NAME` (mimo git, jary do `mods/`),
  bez dev runu. `run` umí `--pack`, `--options`, `--script`, `--camera`, `--time`. Výstupy v `test-instance/NAME/autotest`.
- `scripts/instance.py install NAME [--renderer gl] [--extra DIR]`: čerstvý jar, volitelně bez VMR, a jary
  z `test-instance/jars/DIR`. Běh na OpenGL ovládá `compat/GlAutoTest` se stejnými vlastnostmi a skripty.
- Skript autotestu (`--script`, řádky: `cmd`, `wait`, `settle`, `shot`, `dump`, `fps`, `gui`, `slot`, `graphics`, `resize`,
  `minimize`, `restore`, `pack`, `reload`, `screen`, `click`, `press`, `error`, `note`, `quit`), viz `dev/AutoTest`. Scény jsou
  v `scripts/scenes/`. Okno testu je normální okno: obrazovku, kterou skript neotevřel, autotest zavírá, ale pohyb myší do
  výsledku promluví dál.
  `dump` uloží všechny targety aktuálního packu jako PNG (`render/TargetDump`).
- `scripts/glscan.py jar...`: přímá volání GL v bajtkódu modu, i ve vnořených jarech.
- `./gradlew checkPacks -PdhVariant=true`: všechno s definovaným `DISTANT_HORIZONS`; programy `dh_*` se tak kontrolují vždy.
- `scripts/pixel.py soubor.png x,y ...`: hodnoty pixelů bez závislostí.
- `scripts/take_from_vitrail.py`: převzetí skupiny a z Vitrailu do `src/main/java/dev/vitrail`. **Pozor:** přepsal by
  úpravy v `PackProgram`, `ShaderProperties` a `TargetPlan` (značené `OpalCompat:`), znovu je pak dopiš ručně.
- `-Pvalidation=false` vypne validation layers (`tools/vvl`, Khronos nightly, mimo git).

Pack se vybírá ve hře: video nastavení VMR, skupina **Shadery** vlevo (uloží se do
`config/opalcompat.properties`, klíč `shaderPack`). Nastavení packu jsou v `shaderpacks/<pack>.txt`, stejném souboru
jako u Iris. Systémové vlastnosti `opalcompat.pack`, `opalcompat.profile` a `opalcompat.options` (autotest) mají přednost,
dokud se pack nevybere ve hře.

## Opal, Vectors a VMR (balík `compat`, od 3. 11. 2026)

Všechno je jeden klientský mod, na VMR, Opalu i Vectors nepovinný. Vulkanová část startuje z `VulkanSide` jen s VMR ve hře a její
mixiny s ním (`OpalCompatMixinPlugin`), bez něj běží jen část pro Opal na OpenGL. Z Opalu ani Vectors se nic nekopíruje: `src/stubs` jen
pojmenovává volané členy pro kompilaci a do jaru nejde. Staré jary `vitrailvk` a `vitrailbridge` jsou v `neoforge.mods.toml` nekompatibilní,
hra řekne, ať je odstraníš.

**Instalace na Vulkan:** VMR, `opalcompat`, Opal, Vectors, LibLibLib, NeoForge alespoň 21.1.259 (to chce Opal). `create-dyn-light` ne,
viz `BLOCKERS.md`.

**Proč Opal na VMR nekreslí.** První log: Opal se rozjel přes GL emulaci VMR, `glTexImage2D` hloubkové textury spadl
(`Invalid texture source byte size`) a za minutu `VK_ERROR_DEVICE_LOST`. V `GlFunctionRegistry` jsou `glDrawBuffers`,
`glBindImageTexture`, `glDispatchCompute` a `glTexImage3D` prázdné, takže jeho pipeline tam stát nemůže. `OpalOnVulkanMixin` ruší
`Opal.rebuild`, pipeline nikdy nevznikne a všechny jeho háky jsou prázdné. Jeho obrazovky a klávesy zůstaly: `OpalLink` drží
výběr obou modů stejný a `Opal.requestReload` po změně výběru nebo souboru nastavení načte pack ve Vitrailu. Bez Vitrailu se
ohlásí `OpalClient.conflict` a shadery nejsou.

**„Too many custom images“.** Druhý log, Opal na OpenGL: dává každému obrázku napevno jednotku a bere jich nejvýš 7, tvůj Complementary
Unbound + Euphoria má 8 (`Opal custom images` v logu). `OpalImagesMixin` limit ruší a `CustomImageUnits` přiděluje jednotky podle
programu při každém navázání, jako Iris. Ověřeno: Opal + Vectors na OpenGL, ten pack, `opal_active=true`.

**GUI.** Základ je registr `ModSettingsRegistry`, který na to VMR má. `ShaderPages`: zapnuto, pack, nastavení, seznam,
složka, stav; projeví se po Použít. `VectorsPages`: stránky Zátěž a Ladění, slova a rozsahy z obrazovky Vectors, bez
voleb terénního rendereru OpenGL. S Opalem vedou tlačítka do jeho obrazovek, ty mají vzhled Vectors. Vectors dostal stránku VulkanMod
a z obrazovky Options zmizela jejich dvě tlačítka navíc. `VectorsOnVulkanMixin`: Vectors počítá Opal jako shader mod a vypínal by
limity, které tady dávají smysl.

**Terén.** `TerrainFormat` se rozhodoval dřív, než byl mod zkonstruován, takže mimo testy (kde pack dává `-D`) končil vypnutý
a pack by chtěl restart. Teď se ptá na složku `shaderpacks`: je-li v ní pack, je mesh rozšířený a zapnutí za běhu nic nechce.

## Výsledky na Vulkanu (3. 11. 2026)

NeoForge 21.1.265, RTX 5060 Ti, validation layers, okno 854x480, čerstvé jary. Pack je Complementary Unbound + Euphoria Patches
s nastavením z tvé instance, není-li řečeno jinak.

| Test | Výsledek |
|---|---|
| VMR, `opalcompat`, Opal, Vectors: start, svět | funguje, první log se neopakuje |
| Jen VMR a `opalcompat`, bez Opalu a Vectors | funguje (0.4.0) |
| Mraky | fungují po opravě níže |
| Ruka, očarovaný meč, pochodeň, truhla, cedule, banner, táborák, oheň, mobové | fungují po opravě níže |
| Západ slunce, noc, déšť, jeskyně, pod vodou, obrys bloku | fungují, validace čistá |
| Změna velikosti okna, minimalizace | fungují, validace čistá |
| BSL, Complementary Reimagined, Bliss: scéna `world3` | běží, log čistý; z obrázků prohlédnutý den |
| Vypnutí, zapnutí, jiný pack z VMR i z Opalu, bez restartu | funguje |
| Nastavení packu z Opalu: uložení, načtení znovu ve Vitrailu | funguje |
| Přepínač Vectors na stránce VMR, Použít | uloží se do `vectors-1.21.1.json` |
| Create 6.0.10 + addony: motor, hřídele, ozubená kola, pás, mixér, lis, vodní kolo, nádrž | kreslí se s packem a se stíny, `flywheel:off` z logu |
| Create: mechanický píst jako kontrapce | jede, prkno i tyč se posouvají |
| Mekanism 10.7.19 + addony: stroje, kostka, tanky, kabely, generátory, Atomic Disassembler v ruce | kreslí se s packem |
| DH 3.3.3: LODy ve dne i v noci, přes `dh_*` | fungují |

**Mraky, důkaz.** Debug režimy v kopii packu (výstup z `deferred1` do nepoužitého `colortex11`): noise v pořádku (`noisetex` i
`GetCloudNoise` dávají strukturu), `z0` = 1, `cameraPosition.y` = 78, `cloudAlt1i` = 200. Vadný byl směr: `viewPos.z` > 0 na obloze
a `nPlayerPos.y` = -0,91 při pohledu nahoru. Příčina: VMR dává `getDepthFar` = nekonečno a JOML staví takovou matici s epsilonem
1e-6, takže `w` při hloubce 1 je záporné a obloha leží za kamerou. `GetVolumetricClouds` pak vrátí 0 hned na `maxPlaneDistance < 0`.
Pokus přes injekci do `getDepthFar` nezabral: export mixinů ukázal handler VMR první a s návratem, proto `GameRendererDepthFarMixin`
přesměrovává volání v `getProjectionMatrix`. Po opravě `w` = 1/510, `nPlayerPos.y` = +0,5 ve středu při pohledu 30° nahoru, `clouds.a` > 0.

**Tvar matic.** Stejným měřením vyšlo `P[3][2]` = -0,2 místo -0,1. Export mixinů nemá nic pod `org/joml`, takže `Matrix4fM` z VMR se
neaplikuje a matice jsou OpenGL [-1,1]. `forwardToReversed` z nich dělal 2z-w: near 0,1 a stíny entit s jinou hloubkou než terén.
`glToReversed` publikuje matici beze změny, ruka násobí řádek z 0,125 jako Iris, DH má svůj objem ve stejném tvaru.

**Fialová obloha s očarovaným mečem.** Obyčejný meč nic, očarovaný změní `colortex0` na obloze z (51,91,227) na (5,1,15), po schování
zpět. Lesk natvrdo červeně v kopii packu zbarvil celou oblohu, takže ji kreslil program lesku. Příčina: vanilla volá
`setShader(position)` (`LevelRenderer:962`), hned `renderSky` (`:963`) a ten bere `RenderSystem.getShader()` (`:1618`). Stínový průchod
běží mezi tím, hráč v něm drží meč a poslední je typ `glint`; `Families.resolve` testuje `glint` před `Phase.SKY`. `LevelRendererMixin`
shader po průchodu vrací.

**„Not building!“.** Očarovaná věc v ruce pack rovnou shodila (záchrana na čistý VMR zafungovala): ruka a stíny měly `MultiBufferSource`
s jedním sdíleným bufferem a lesk ukončil rozepsaný předmět. `PassBuffers` je sada jako ve vanille.

## Výsledky Fáze 2 (přerušeno limitem, 3. 11. 2026)

Běh v čisté instanci (`scripts/instance.py`, čerstvý jar, NeoForge 21.1.228, RTX 5060 Ti, validation layers zapnuté):

| Test | Výsledek |
|---|---|
| Start bez packu | funguje, validace jen baseline VMR |
| `test_final`, `test_composite`, `test_terrain` | fungují, nové jen `Undefined-Value-ShaderOutputNotConsumed` |
| `test_compute` | funguje: setup, begin bez průchodu, prepare bez průchodu, composite, `final` + `final_a` |
| BSL, výchozí nastavení, jedna scéna, den | běží bez chyb v logu, viz `run/baseline/bsl_basic.png`; kvalitu obrazu je potřeba posoudit dál |

Opraveno testem: `PackImage.upload` sahal na `GraphicsQueue.INSTANCE`, které VMR nikdy nenastaví (`DeviceManager.getGraphicsQueue()`).
Zároveň se tím potvrdila záchrana: chyba packu skončila čistým VMR a textem v `lastError`.

Compile ověřen: `checkPacks` i `-PdhVariant=true` čisté na BSL, Complementary a Bliss. Co běželo potom, je v Výsledcích na Vulkanu.

## Rozhodnutí

- **Mod id `opalcompat`, balík `dev.opalcompat`**; převzatý kód zůstává v `dev.vitrail.*` s hlavičkou
  o původu, úpravy jsou označené `OpalCompat:`.
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
- **Hloubka:** matice hry jsou ve tvaru OpenGL, packu se publikují beze změny (`LevelCapture.glToReversed` je převod pro
  `ViewMatrices`), target má hloubku 0..1 přes `ClipSpace.FORWARD`, far je za běhu packu konečný jako u Iris.
- **Vertex formát terénu:** `DrawBuffers.VERTEX_SIZE`/`POS_OFFSET` jsou `static final` a `QuadSorter`
  porovnává formát identitou, proto se při startu rozšiřuje přímo
  `CustomVertexFormat.COMPRESSED_TERRAIN` (stride 40 B), je-li ve složce `shaderpacks` nějaký pack. `dev/vitrail/glsl/SodiumVertex.java`
  je vlastní náhrada se stejným API.
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
- **GUI** (`screen/ShaderPackScreen`, `screen/PackSettingsScreen`, `VOptionScreenMixin`): vlastní obrazovky na vanilla
  widgetech, Vitrailův `screen/` je napsaný proti 26.x. Model stránek je převzaté `pack/menu` (`PackMenu`, `MenuValues`)
  a `settings/PackSession`, soubor `SettingsFile`. Vstup je mixin do `VOptionScreen.addButtonsWithSearchBar`:
  `ModSettingsRegistry` umí jen stránky voleb, ne tlačítko a vnořené stránky packu. Levý klik další hodnota, pravý
  předchozí, shift výchozí; Zrušit odejde, Použít zapíše a načte znovu, Hotovo obojí, jako u Iris.
- **Pád packu:** výjimka při načítání i při kreslení vede na `report()`: pack se vypne, kreslí čistý VMR, toast, celý text
  chyby včetně diagnostiky shaderc na obrazovce Shader packy a tlačítko Kopírovat chybu. `PackRenderer.reload()` po
  změně přestaví i mesh (`allChanged`), protože `block.properties` je v něm, jako `Iris.reload`.
- **Restart:** jen když hra začala s prázdnou složkou `shaderpacks`. Jinak všechno za běhu: VMR čte prvních 20 B rozšířeného
  vrcholu. Cena je dvojnásobek paměti terénu i pro hráče s vypnutým packem, když má nějaký nainstalovaný.
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

## Mody

**Flywheel, Create.** VMR 0618965 obsahuje `compat/sable/flywheel`, ale `VulkanFlywheelEngine.render` nic nekreslí (jen
zapisuje instance do paměti) a backend je za `vulkanmod.compat.sable.flywheel.experimental`. Výchozí backendy Flywheelu
chtějí `GL 3.3`, VMR hlásí emulovanou `3.2` (`GlFunctionRegistry.REPORTED_GL_VERSION`), takže `GlCompat.SUPPORTS_*` je
false a Flywheel sám spadne na `flywheel:off`; Create pak kreslí přes vanilla BER a entity (`SuperByteBuffer` do
`MultiBufferSource`). To je záchranná varianta a nepotřebuje žádný mixin. Render type bloků (`rendertype_solid`, ...) pod
entitou nebo BER je podle Iris `MOVING_BLOCK`, tedy `gbuffers_block`, ve stínu `shadow_cutout`/`shadow_water`, BER
ve stínu `shadow_block`. **Vlastní backend** (Colorwheel) nedělám: Flywheel generuje shadery z GLSL materiálů modů (přes
1000 řádků na program), Colorwheel chce od packu další programy a stojí na `glsl-transformer` (AGPL). Cena záchrany je
CPU u velkých scén, ne chybějící obraz. Ověřit v logu `flywheel:off`.

**Ostatní mody.** Všechno, co jde přes `RenderType` a `BufferUploader`, se mapuje podle jména shaderu (`Families`) bez
namespace, neznámý shader jako entita nebo BER podle míchání. Přímé GL se hledá v bajtkódu, `scripts/glscan.py <jar>`
(i vnořené jary). Mekanism 10.7.19: žádné GL, `GlStateManager` jen v `MekanismRenderType`, `GuiGraph`, `GuiInsetElement`
(stav, ne kreslení), vlastní shadery `rendertype_flame`, `rendertype_mekasuit`, `rendertype_sps` dostaly programy jako
v Iris (`ShaderAccess`). Create 6.0.10: GL jen ve Flywheelu (backend, vypnutý) a v jedné třídě Ponderu. **Shim Iris API
nepřidávám:** Create, Mekanism ani DH bez mod id `iris` nic z Iris API nevolají, Flywheel a DH ho hledají přes `ModList`.
DH dostává svůj vlastní accessor (`IIrisAccessor`), ostatní mody nic. Co mod nakreslí přímým GL mimo tuhle cestu, skončí
v targetu VMR, který `final` přepíše: chybí v obraze, nespadne.

**Distant Horizons.** DH 3.3.3 má render za `AbstractDhRenderApiDefinition` (`IDhTerrainRenderer`, `IVertexBufferWrapper`
a další). `DhDependencySetupMixin` mu místo OpenGL dá `VkDhDefinition`: buffery jsou `VertexBuffer` VMR (GPU, VMA), teren
kreslí `PackRenderer.distantHalf` programy `dh_terrain`, `dh_water`, `dh_shadow`, ostatní průchody (fog, SSAO, fade, TAA,
aplikace do MC) jsou prázdné, protože to má na starosti pack. `DhMainMixin` přidá `IIrisAccessor`, díky kterému DH
odkládá vodu, sestaví seznam pro stín bez frustum kamery a bez packu kreslí jinak; id `iris` se nikde neobjeví. Hloubka:
vlastní `dhDepthTex0` a kopie `dhDepthTex1` před vodou, forward jako všechno ostatní, řádek projekce z rovin DH.
Vrchol 16 B (`DistantVertex`), corner sekce přes dynamický offset jednoho UBO, jedna sada na půlku, jeden indexový buffer.
`DISTANT_HORIZONS` se definuje jen s aktivním bridgem. Bez forku a bez závislosti při běhu: `libs/` je jen `compileOnly`,
mixiny aplikuje `OpalCompatMixinPlugin` jen pro přesnou verzi 3.3.3 a bez `-Dopalcompat.dh=false`. Bez packu se LOD
nekreslí (`dh_` program není), mraky a majáky DH (`dh_generic`) také ne.

## Práce Codexu (větev `codex-work`, revize `39fd05e`)

Import doslova je commit `bed96bd`, opravy jsou zvlášť. Zip neměl `.git`, ale stál přímo na `b65080c`, takže rozdíl
stromů je celá práce. Jar `opalcompat-0.1.0-alpha.jar` je bajt po bajtu shodný ve všech třídách s buildem těchto
zdrojů, liší se jen konce řádků v `opalcompat.mixins.json`; o funkčnosti nic neříká.

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
| `GameRendererDepthFarMixin` | volání `getDepthFar` v `getProjectionMatrix` | ano |
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

Balík `compat` má vlastní config s `required: false` a `@Pseudo`, změna cizího modu ho nesmí shodit: `OpalOnVulkanMixin` a
`VectorsOnVulkanMixin` jen s VMR, `OpalImagesMixin`, `OpalImageMixin` a `FlywheelShaderPackMixin` jen bez něj.

## Neověřeno za běhu

Co běželo, je ve Výsledcích na Vulkanu. Vědomě vynechané nebo nespuštěné:

- Opal na OpenGL s Create, Mekanismem a DH: běžel jen s Opalem a Vectors, jedna scéna. `FlywheelShaderPackMixin` se ve hře nespustil
  a Opal nemá programy `dh_*`. Rozhodnutí uživatele: stačí Vulkan.
- Běh bez VMR po sloučení do jednoho modu: naposledy běžel ještě jako dva jary. Teď je ověřené jen staticky (`scripts/release.py` hlídá,
  že vstupní třídy a část pro OpenGL nejmenují třídy VMR). Rozhodnutí uživatele: testy na OpenGL ne.
- Měření FPS a srovnání s Irisem: rozhodnutí uživatele. Mimochodem jediné číslo: pack a všechny mody s DH, 854x480, 80 FPS, 1 % low 36.
- Jar VMR z instance uživatele se od toho z GitHubu liší (5e4b09e8, 20 537 547 B proti 61a1c215, 20 537 626 B), verze stejná. Testy běžely
  na tom z GitHubu.
- Velká scéna v Create, vytvořený multiblok v Mekanismu (dynamická nádrž stojí, formování neověřené), hranice světa, praskliny při kopání,
  efekt ohně přes obraz, záře.

Značky `TODO-VERIFY` v kódu nejsou. Ruka i stíny entit jsou ověřené proti `refs/Iris` (`pathways/HandRenderer`,
`shadows/ShadowRenderer`): druhé vykreslení entit dělá Iris stejně, takže mod, kterému vadí, má stejný problém i pod Iris.
Při ověření opraveno: stíny entit řadí podle typu (méně dávek), berou tick delta jako vanilla (`isEntityFrozen`, BER
`false`), `shadowPlayer` bere i vozidlo a pasažéry; ruka se ptá na spánek entity kamery, ne hráče.

## Známé problémy a omezení

- Baseline validace VMR 0.3.5 bez našeho vlivu: `VUID-vkQueueSubmit-pSignalSemaphores-00067`,
  `VUID-vkDestroyDevice-device-05137`. Další věci z VMR a modů v `BLOCKERS.md`.
- Varování `Undefined-Value-ShaderOutputNotConsumed`: výstup `ofCoverage` z Vitrailu nemá attachment.
  `Undefined-Value-OutputNotWritten` (1 až 2 u BSL a Bliss) není prověřené, na obraze se neprojevuje.
- V noci s DH je na části oblohy fialový opar s ostřejší hranou. Posouvá se se světem, ne s obrazovkou, takže není z překrytí targetů;
  jestli patří k packu, není ověřené. BSL má bílý horizont, u ostatních packů není.
- Do stínu jdou jen sekce zkompilované grafem kamery, VMR jiné nemá.
- `centerDepth` čte depth přímo, bez vyhlazení. `normals`/`specular` zatím neutrální, atlasy PBR se nenačítají.
- Raw textury: packed, BGR, integer a rectangle zatím čtou černou. VBO s `transientGeometry` kreslí VMR.
- Shadow program čtoucí stejnou mapu, kterou zapisuje, dostane `shadowtex1`.
- `Vk.storageUsage`: pack, který váže jako `colorimg` target ve formátu bez storage podpory, selže při zápisu deskriptoru.

## Co zbývá

1. Co je vyjmenované v „Neověřeno za běhu“.
2. Případně: LOD bez packu, `dh_generic`, vlastní backend Flywheelu.

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
  v `TargetPlan` (Codex, značené `OpalCompat:`).
- Podle chování Iris 1.21.1 (LGPL-3.0), bez kopie kódu, viz [NOTICE](NOTICE): `HandPass`, `ShadowFeatures`, `DrawIds`,
  `ItemInHandRendererMixin`, `TextureImages`, `Families`, flipy v `ShadowMaps`.
- Licence LGPL-3.0-only, `NOTICE-vitrail` je beze změny.

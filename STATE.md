# STATE

Hotovo: 1 reference a build, 2 mixiny a runtime, 3 optiony/noisetex/mipmapy/blendy, 4 stíny (terén).
Aktuální: 5 obloha, entity, ruka, částice, počasí, mraky (včetně entit do stínu).
Čeká: 6 custom textury a compute, 7 GUI, A Flywheel/Create/Colorwheel, B ostatní mody, C Distant Horizons, release build.

## Klíčová rozhodnutí
- VMR jako release jar, bez forku; mixiny do interních tříd (`remap=false`), závislost na přesné verzi.
- Kód Vitrailu v `dev.vitrail.*` beze změn překladače, vlastní kód v `dev.vitrailvk.*`.
- SPIR-V: shaderc bez optimalizace, `SpirvModule` přečísluje deskriptory a vstupy podle jmen.
- Vlastní render passy, pipeline a MRT targety; device, `VulkanImage` a command buffer sdílené s VMR.
- Targety v GL orientaci bez flipu; `final` s flipem; vlastní D32, konvence `ClipSpace.FORWARD`.
- Terénní formát rozšířený v `<clinit>`, proto zapnutí/vypnutí packu vyžaduje restart.
- Frame: `beginLevel`, `beforeSky` (clear, begin, stíny, prepare), terén, `deferred`, `finishFrame`.

## Otevřené / neověřené
- Nic z položek 3 a dál není spuštěné ani vizuálně ověřené (pokyn: netestovat).
- Stíny entit/BER a shadowcomp: depth mipmapy doplněny, entity/BER se odesílají do stínu, shadowcomp spuštěn za stíny s vlastními flipy, viewportem a mipmapami; storage zapojeno v položce 6.
- `centerDepth` bez vyhlazení.
- `./gradlew build` opraven: licenční soubory jsou explicitní cesty; sestavení prošlo.
- Při selhání se prostředky uvolní po dokončení nebo zahození snímku VMR.
- V PRÁCI: položka 6 (PackImage 1D/2D/3D a GENERAL hotovo v kódu; texture.* a customTexture.* zapojeny; image.*, SSBO a deskriptory zapojeny; compute navazuje); položka 5: okamžité meshe a statické sky/cloud VBO zapojeny, ID entit putují ve vrcholových atributech; stejnorodé entity/BER batche směrovány; smíšené batche rozděleny bez změny pořadí; ruka naplánována před/po deferred.
- TODO-VERIFY: custom textury/atlas/1D (packed/BGR/integer raw a rectangle zatím odmítnuty); PackImage alokace/uvolnění a GENERAL bariéry/mixin, storage aliasy/typed views/resize; životnost FrameArena a opakované vykreslení levelu v jednom snímku za běhu.
- TODO-VERIFY: Vulkanové vazby MeshDraw, tangent a střed UV čtyřúhelníků; pro jiné primitivy zatím výchozí hodnoty.
- TODO-VERIFY: fáze oblohy, mraků a počasí včetně rozšíření NeoForge za běhu.
- TODO-VERIFY: dvě fáze ruky, NeoForge hooky, depth a potlačení vanilla ruky během hry.
- TODO-VERIFY: překlad mesh programů a jejich cílové půlky za běhu.
- TODO-VERIFY: shadowcomp parity/bariéry/viewport/mipmapy, depth mipmapy/formátová podpora, shadow mesh, entity/BER frustum a čtení zapisovaných shadow textur; model offset je nula.
- TODO-VERIFY: BufferUploader směruje okamžité sky/částice/počasí/mraky; ověřit mixin pořadí a životnost. statické VBO, entity i rozdělení ruky zapojeny.
- TODO-VERIFY: VBO kopie a synchronizace; ověřit pokrytí entit/částic, ID a dělení smíšených batchů; neznámé primitivy zůstávají VMR.

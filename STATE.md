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
- Stíny: jen sekce zkompilované grafem kamery; `shadowcomp` a depth mipmapy zatím chybí.
- `centerDepth` bez vyhlazení.
- `./gradlew build` opraven: licenční soubory jsou explicitní cesty; sestavení prošlo.
- Při selhání se prostředky uvolní po dokončení nebo zahození snímku VMR.
- V PRÁCI: položka 5; okamžité meshe a statické sky/cloud VBO zapojeny, ID entit putují ve vrcholových atributech; stejnorodé entity/BER batche směrovány; smíšené batche a ruka zbývají.
- TODO-VERIFY: životnost FrameArena a opakované vykreslení levelu v jednom snímku za běhu.
- TODO-VERIFY: Vulkanové vazby a životnost MeshDraw za běhu.
- TODO-VERIFY: fáze oblohy, mraků a počasí včetně rozšíření NeoForge za běhu.
- TODO-VERIFY: fáze částic a ruky za běhu; rozdělení ruky před/po deferred ještě chybí.
- TODO-VERIFY: překlad mesh programů a jejich cílové půlky za běhu.
- TODO-VERIFY: DynamicTransforms používá aktuální matice a barvu; model offset je zatím nula.
- TODO-VERIFY: BufferUploader směruje okamžité sky/částice/počasí/mraky; ověřit mixin pořadí a životnost. statické VBO oblohy/mraků zachyceno; entity a rozdělení ruky chybí.
- TODO-VERIFY: VBO kopie a synchronizace; ověřit ID, typ batchů a třídění; smíšené entity/BER batche zatím zůstávají VMR.

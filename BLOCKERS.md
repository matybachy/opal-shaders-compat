# BLOCKERS.md

Co nejde opravit v tomhle repozitáři, protože to vyžaduje zásah do jiného modu nebo do jeho sestavení.

## `create-dyn-light` (Create Dynamic Light 2.0.1) se nenačte

Hra skončí chybou `Mod createdynlight requires sodiumdynamiclights 1.0.0 or above`. Mod má závislost zapsanou jako
`mandatory=false`, jenže `neoforge.mods.toml` pro NeoForge 1.21 čte `type`, a bez něj je závislost povinná. Doplnit ji
nejde: `sodiumdynamiclights` podle Modrinthu vyžaduje Sodium, Sodium s VMR nepoběží a Opal se při něm sám vypne.
Addon navíc staví na LambDynLights API, takže by bez toho stejně nic nedělal. **Z instance ho vyřaď.**
Vydávat se za `sodiumdynamiclights` je mimo hru: stejný důvod jako u id `iris`.

## Fabulous grafika na VMR

VMR ji nepodporuje: `GraphicsModeCompatibility.supportedModes()` nabízí jen Fast a Fancy a v logu píše
`fabulous graphics mode not supported, forcing fancy`. Když ji někdo vynutí přímo (vanilla obrazovka přes Shift+P,
test `graphics fabulous`), objeví se `VUID-vkCmdDrawIndexed-imageLayout-00344`, `VUID-VkImageMemoryBarrier-oldLayout-01197`,
`VUID-vkCmdDraw-None-09600` a `VUID-vkCmdDrawIndexed-None-08608`. Čistý VMR bez packu dává stejné hlášky.

## `VUID-vkResetCommandBuffer-commandBuffer-00045`

Vzácná hláška (2 z asi 20 běhů), jeden výskyt krátce po startu. `vkResetCommandBuffer` volá jen VMR
(`Renderer.java:265`, `CommandPool.java:150`, `Vulkan.java:444`), Opal Compat ho nevolá. Bez viditelných následků.

## `Matrix4fM` ve VMR se na NeoForge neaplikuje

Mixin do `org.joml.Matrix4f` má vynutit hloubku 0..1, ale JOML se nenačítá přes transformer: export mixinů
(`-Dmixin.debug.export=true`) nemá nic pod `org/joml`. Matice jsou ve tvaru OpenGL [-1,1] a VMR jim ořezává polovinu rozsahu
hloubky (near vychází 0,1 místo 0,05). Opal Compat s tím teď počítá. Kdyby VMR tohle opravil, `LevelCapture.glToReversed`
se musí upravit.

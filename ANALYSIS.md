# Fáze 0 – analýza (obnovená, zkrácená)

Revize: Vitrail `7a3cdcb` (LGPL-3.0, MC 26.x), VulkanMod Reforged `0618965` = 0.3.5-ALPHA+1.21.1 (LGPL-3.0),
Iris větev `1.21.1` (LGPL-3.0). Klony jsou v `refs/`.

## Vitrail `common/src/main/java` (403 souborů, 102 831 řádků)

| Skupina | Souborů | Řádků |
|---|---:|---:|
| a) nezávislý na verzi hry (`glsl`, `pack.*`, `uniform.*`) | 227 | 47 736 |
| b) závislý na 26.x / Sodium (`render`, `mixin`, `sodium`) | 123 | 44 202 |
| c) hraniční (`screen`, id, registry) | 53 | 11 296 |

Skupina b slouží jen jako specifikace chování.

## VulkanMod Reforged: mapa

- **Kompilace:** `SPIRVUtils`, `lwjgl-shaderc` 3.3.3, bez reflexe; bindingy z JSON nebo vlastního parseru.
- **Pipeline:** jeden descriptor set, push constants jen pro vertex, 1 color blend attachment, bez
  geometry stage a compute.
- **Framebuffery:** `colorAttachments <= 1`; `MainPass` je rozhraní; `Renderer.temporaryRenderTarget()`
  přeruší a obnoví pass se stavem.
- **Terén:** 20 B `COMPRESSED_TERRAIN` bez normály a ID bloku, háčky `setShaderGetter`,
  `TerrainBufferBuilder.setBlockAttributes`, section offset v `gl_InstanceIndex`, `ChunkOffset` push constant.
- **Hloubka:** forward 0..1 (`Matrix4fM`). **Device:** minimum features.
- **Mixiny VMR:** `renderLevel` jen injektuje, `@Overwrite` hlavně v GL fasádě.

## Rizika

MRT (vlastní passy), device features (mixin), terénní formát (rozšíření při startu), shadow pass (VMR má
jen section graph kamery; vlastní výběr ze `SectionGrid`), verze shaderc, alpha stav VMR (přesná verze),
GL emulace (naše targety nejsou GL textury), licence (nic ze Sodia, nic z `glsl-transformer`).

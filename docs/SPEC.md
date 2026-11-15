# Minimap — Especificação Técnica

Mod do Minecraft Java Edition, **somente do lado do cliente**, que mostra um minimapa de cima para baixo no canto inferior direito e um mapa expandido em tela cheia com `M`.

## 1. Plataforma

| Item | Valor |
|---|---|
| Loader | Fabric (Fabric Loader + Fabric API) |
| Versão alvo | Minecraft **26.3** |
| Java | **25** |
| Nomes | O jogo não é mais ofuscado: use os nomes oficiais da Mojang |
| Build | Gradle + Fabric Loom |
| Lado | `"environment": "client"` |
| Mod ID / nome | `minimap` / "Minimap" |
| Pacote base | `dev.luisf.minimap` |

## 2. Escopo

- Minimapa top-down no canto inferior direito, centrado no jogador.
- Mapa expandido em tela cheia aberto com `M`, com pan e zoom.
- Cores do bioma, sombreamento por altura, seta do jogador, coordenadas e marcadores.
- Configuração persistente e traduções `pt_br`/`en_us`.

Fora do escopo: servidores, itens, receitas, waypoints, radar de entidades, contornar anti-cheat.

## 3. Arquitetura

```
src/client/java/dev/luisf/minimap/
  MinimapClient.java          // entrypoint: teclas, eventos, texturas, HUD
  config/MinimapConfig.java   // modelo + load/save JSON (Gson)
  config/ConfigScreen.java    // tela com widgets vanilla
  config/ModMenuIntegration.java
  cache/MinimapCache.java     // Map<ChunkPos, ChunkData> + fila + varredura incremental
  map/MinimapMap.java         // leitura da cor de uma coluna
  map/MinimapMath.java        // PURO: coordenadas, rotação, sombreamento
  map/MinimapTexture.java     // DynamicTexture + amostragem
  hud/MinimapHud.java         // minimapa do canto
  screen/MinimapScreen.java   // mapa expandido
  mixin/BlockChangeMixin.java // marca o chunk ao mudar um bloco
```

## 4. Cache de terreno

- `MinimapCache` guarda, por chunk, `int[256]` de cores ARGB e `int[256]` de alturas (índice `(z & 15) * 16 + (x & 15)`).
- **Carga:** `ClientChunkEvents.CHUNK_LOAD` enfileira o chunk; **descarga:** remove.
- **Varredura:** no fim do tick do cliente, `scanPending` processa a fila com orçamento de **2 ms/tick**, começando pelos chunks mais próximos.
- Para cada coluna, percorre as seções do chunk de cima para baixo (pulando `hasOnlyAir()`) e guarda o primeiro estado não-ar.
- **Cor:** `BlockTintSource#colorInWorld` quando o bloco tem tint (grama, folhas, água); senão `MapColor#calculateARGBColor(NORMAL)`. Água usa `BiomeColors.getAverageWaterColor`.
- **Mudança de bloco:** o mixin injeta em `ClientLevel#setServerVerifiedBlockState` e `ClientLevel#setBlock(...)` e marca o chunk para nova varredura.
- **Mundo novo / desconectar:** cache e marcadores são limpos.

## 5. Texturas

- O minimapa usa `DynamicTexture` de **256×256**, centrada no jogador, cobrindo `2 × miniRadius` blocos. É reconstruída a cada 5 ticks ou quando o jogador se move mais de 1 bloco.
- O mapa expandido usa `DynamicTexture` de **512×512**, centrada num ponto do mundo, cobrindo `2 × mapRadius` blocos. É reconstruída por fatias de 128 linhas por tick.
- Cada pixel da textura corresponde a um bloco. No modo rotacionado, o offset da tela é rotacionado pelo yaw da câmera antes de amostrar o bloco.
- A forma `CIRCLE` zera o alpha fora do círculo.

## 6. Mapa expandido

- `MinimapScreen` desenha a textura grande com `blit` usando UV.
- **Pan:** arrastar move a janela em pixels; ao soltar, o centro da textura passa a ser o ponto central da tela.
- **Zoom:** a rodinha altera `blocksPerPixel` entre 0,25 e 32.
- Mostra as coordenadas sob o cursor, o nível de zoom, o jogador, o renascimento e os outros jogadores.

## 7. API confirmada no Minecraft 26.3

Verificado com `javap` no `26.3.jar` e nos módulos da Fabric API.

| Ponto | Resolução |
|---|---|
| HUD | `HudElementRegistry.addLast(Identifier, HudElement)`; `HudElement#extractRenderState(GuiGraphicsExtractor, DeltaTracker)` |
| Desenho | `GuiGraphicsExtractor`: `fill`, `fillGradient`, `outline`, `text`, `centeredText`, `blit(Identifier, x, y, w, h, u0, v0, u1, v1)`, `pose()`, `guiWidth()`, `guiHeight()` |
| Textura dinâmica | `new DynamicTexture(String, int, int, boolean)`, `getPixels()`, `upload()`; registrar com `TextureManager#register` |
| Imagem | `com.mojang.blaze3d.platform.NativeImage#setPixel` |
| Cor | `BlockColors#getTintSource(state, 0)` → `BlockTintSource#colorInWorld(state, BlockAndTintGetter, BlockPos)`; `MapColor#calculateARGBColor(Brightness)`; `BiomeColors#getAverageWaterColor` |
| Bioma | `LevelReader#getBiome(BlockPos)`; `ClientLevel#getSeaLevel()` |
| Teclas | `KeyMappingHelper#registerKeyMapping`; `KeyMapping.Category#register(Identifier)` |
| Tela | `Screen#extractRenderState(GuiGraphicsExtractor, int, int, float)`; `mouseClicked/mouseReleased/mouseDragged/mouseScrolled`; `keyPressed(KeyEvent)` |
| Jogadores | `ClientLevel#players()`; `Player#getGameProfile().getName()` |
| Renascimento | `Level#getRespawnData()` → `LevelData.RespawnData#pos()/dimension()` |

## 8. Testes

- `MinimapMathTest`: rotação (yaw 0/90), modo fixo, sombreamento por altura, `scaleRgb`, zoom.
- `MinimapConfigTest`: padrões, clamp, round-trip JSON, arquivo corrompido.

## 9. Decisões

- **Fabric API embutida** (jar-in-jar) para não exigir instalação separada.
- **Sem multimídia no cache:** só cores e alturas; o cache ocupa ~2 KB por chunk.
- **Sem pause:** o mapa expandido não pausa o jogo (`isPauseScreen()` = false).

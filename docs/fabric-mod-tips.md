# Dicas para criar mods Fabric nesta máquina

Lições dos mods `nearest-chests` e `miner-depth` (Minecraft Java 26.3, Fabric).

## Ambiente

- **Java:** não há Java no PATH. Use o JDK 25 que vem com o launcher (tem `javac`):
  ```
  C:\Users\luisf\AppData\Local\Packages\Microsoft.4297127D64EC6_8wekyb3d8bbwe\LocalCache\Local\runtime\java-runtime-epsilon\windows-x64\java-runtime-epsilon
  ```
  No Git Bash:
  ```bash
  export JAVA_HOME="/c/Users/luisf/AppData/Local/Packages/Microsoft.4297127D64EC6_8wekyb3d8bbwe/LocalCache/Local/runtime/java-runtime-epsilon/windows-x64/java-runtime-epsilon"
  ```
- **Minecraft:** `%APPDATA%\.minecraft`, versão 26.3, launcher da Microsoft Store.
- **Fabric já instalado:** perfil "Minecraft 26.3 com mods" (Loader 0.20.1, versão `fabric-loader-0.20.1-26.3`). O nome do perfil é só um rótulo em `launcher_profiles.json` (campo `name`) e pode ser trocado à vontade; o que importa é o `lastVersionId`. Os mods vão em `%APPDATA%\.minecraft\mods`.

## Versões usadas

| Item | Versão |
| --- | --- |
| Minecraft | 26.3 |
| Fabric Loader | 0.20.1 |
| Fabric Loom | 1.19.3 (plugin `net.fabricmc.fabric-loom`) |
| Fabric API | 0.165.0+26.3 |
| Gradle | 9.8.2 |
| Java | 25 |

Para conferir versões mais novas: https://fabricmc.net/develop

## Particularidades do Minecraft 26.x

- O jogo **não é mais ofuscado**: os nomes de classes são os nomes reais da Mojang, sem mappings (`net.minecraft.client.Minecraft`, `BlockPos` etc.).
- Dá para inspecionar a API direto no jar do jogo:
  ```bash
  "$JAVA_HOME/bin/javap.exe" -cp "C:/Users/luisf/AppData/Roaming/.minecraft/versions/26.3/26.3.jar" net.minecraft.client.Minecraft
  ```
- Ponto de partida do projeto: o template oficial `https://github.com/FabricMC/fabric-example-mod`.

## Armadilhas

1. **`runClient` esconde dependências que faltam.** Ele usa o classpath de desenvolvimento, que traz as dependências transitivas do Maven. O jogo instalado só tem o que está dentro do jar. Para testar como no jogo real, use uma tarefa de produção:
   ```groovy
   tasks.register("runProductionClient", net.fabricmc.loom.task.prod.ClientProductionRunTask) {
   	mods.from(tasks.jar)
   }
   ```
2. **Arquivos de idioma e assets precisam de um módulo da Fabric API.** O Fabric Loader sozinho não registra os assets dos mods: sem isso, o texto aparece como chave crua (`meumod.algo.chave`).
3. **Esse módulo tem uma dependência não declarada.** O `fabric-resource-loader-v1` usa classes do `fabric-api-base`. Embutir só o primeiro faz o jogo travar ao iniciar com `NoClassDefFoundError: ...SortableNode`. Para dispensar a instalação da Fabric API, embuta os dois (jar-in-jar):
   ```groovy
   include(implementation(fabricApi.module("fabric-resource-loader-v1", project.fabric_api_version)))
   include(implementation(fabricApi.module("fabric-api-base", project.fabric_api_version)))
   ```
   A alternativa é depender da Fabric API completa e instalar o jar dela em `mods`.

## Convivência com outros mods

- Cada mod precisa de um **ID único** no `fabric.mod.json` e de um **pacote Java próprio**. O `nearest-chests` usa o ID `nearestchests` e o pacote `dev.luisf.nearestchests`; o `miner-depth` usa `minerdepth` e `dev.luisf.minerdepth`.
- Módulos da Fabric API embutidos em mais de um mod não dão conflito: o Loader carrega a versão mais nova de cada um.
- Entradas novas no F3 (`DebugScreenEntries`) usam IDs com namespace (`meumod:nome`) e aparecem separadas no menu F3 + F6.

## Linhas do F3 no 26.x

- Para adicionar uma linha, implemente `DebugScreenEntry` e registre-a com um `@Invoker` no método privado `DebugScreenEntries.register(Identifier, DebugScreenEntry)`.
- Uma entrada nova começa com o status `NEVER`. Para ela aparecer por padrão, injete em `DebugScreenEntryList.rebuildCurrentList` e faça `putIfAbsent(id, IN_OVERLAY)`.
- O jogador muda o status de cada entrada em F3 + F6: **sempre**, **só no F3** ou **nunca**.

## Desenhar no HUD no 26.x

- O HUD saiu da classe `Gui` para `net.minecraft.client.gui.Hud`. Não há mais `render(GuiGraphics, ...)`: os métodos são `extract*` e recebem um `GuiGraphicsExtractor` (`text`, `centeredText`, `fill`, `guiWidth()`, `guiHeight()`).
- Cores de texto são ARGB: use `0xFFFFFFFF`, não `0xFFFFFF` (sem alfa o texto fica invisível).
- Para desenhar perto da mira sem depender da Fabric API, injete em `Hud.extractRenderState` logo depois da chamada a `extractCrosshair` (`@At(value = "INVOKE", target = ..., shift = At.Shift.AFTER)`). Não injete no `TAIL` de `extractCrosshair`: ele tem vários `return` antecipados.
- O alvo da mira está em `Minecraft.getInstance().hitResult` (um `BlockHitResult` quando é bloco).

## Git

- Conventional Commits e versionamento semântico, com tag `vX.Y.Z` e um `CHANGELOG.md` a cada versão.

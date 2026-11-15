# Minimap

Mod Fabric (client-side) para Minecraft Java **26.3** que mostra um minimapa de cima para baixo no canto inferior direito e abre um mapa expandido em tela cheia com a tecla **M**.

- O minimapa mostra o bloco mais alto de cada coluna ao redor do jogador, com a cor do bioma, sombreado pela altura.
- A seta branca no centro aponta para onde a câmera olha; os pontos cardeais ficam na borda.
- Jogadores do mundo e o ponto de renascimento aparecem como marcadores.
- No mapa expandido: arraste para mover, use a rodinha do mouse para zoom e `M` ou `Esc` para fechar.
- O mapa é feito só com o que o cliente já recebeu; não envia pacotes e não lê chunks que não estão carregados.

## Uso

- **`M`** abre o mapa expandido (e fecha).
- **Configurações:** pelo Mod Menu ou por uma tecla (sem tecla por padrão; defina em Opções → Controles → Minimap).
- O minimapa some com F1.

### Configurações

| Opção | Padrão |
|---|---|
| Raio do minimapa (8–128 blocos) | 32 |
| Tamanho do minimapa (48–256 px) | 128 |
| Girar com a câmera | ligado |
| Forma | quadrado |
| Raio do mapa expandido (32–256 blocos) | 128 |
| Mostrar coordenadas / bússola / jogadores / renascimento | ligado |
| Permitir em multiplayer | desligado |

As opções ficam em `config/minimap.json`. Se o arquivo estiver corrompido, ele é renomeado para `minimap.json.bak` e recriado com os padrões.

### Multiplayer

Por padrão o minimapa só funciona em single-player. Num servidor remoto ele mostra `minimapa desativado neste servidor`. Com "Permitir em multiplayer" ligado, o mod usa apenas os chunks que o cliente recebeu.

## Requisitos

- Minecraft Java 26.3
- Fabric Loader ≥ 0.20.1
- Java 25
- Opcional: [Mod Menu](https://modrinth.com/mod/modmenu) 21+

Não é preciso instalar a Fabric API: os módulos usados vão embutidos no jar (`fabric-api-base`, `fabric-lifecycle-events-v1`, `fabric-networking-api-v1`, `fabric-key-mapping-api-v1`, `fabric-rendering-v1`, `fabric-resource-loader-v1`).

## Build e testes

```bash
./gradlew build
```

O jar fica em `build/libs/`. Copie-o para `%APPDATA%\.minecraft\mods`.

- `./gradlew test`: testes JUnit da matemática de coordenadas e da configuração.
- `./gradlew runProductionClient`: roda o jar num cliente de produção.

## Como funciona

- **Cache:** ao carregar um chunk, ele entra numa fila; a cada tick o cliente varre os chunks mais próximos com orçamento de 2 ms. Para cada coluna, guarda a cor do bloco mais alto (com o tint de bioma) e a altura.
- **Texturas:** o minimapa usa uma `DynamicTexture` de 256 px reconstruída 4 vezes por segundo; o mapa expandido usa uma de 512 px reconstruída por fatias.
- **Mudança de bloco:** um mixin em `ClientLevel` marca o chunk para nova varredura.

Detalhes na [SPEC](docs/SPEC.md).

## Versionamento

O projeto segue [Semantic Versioning](https://semver.org/) e [Conventional Commits](https://www.conventionalcommits.org/).

# Changelog

Todas as mudanças relevantes deste projeto. O formato segue [Keep a Changelog](https://keepachangelog.com/pt-BR/1.1.0/) e o projeto usa [Semantic Versioning](https://semver.org/).

## [0.1.0] - 2026-11-14

### Adicionado
- Minimapa de cima para baixo no canto inferior direito, com a cor do bloco mais alto de cada coluna, tint de bioma e sombreamento por altura.
- Seta do jogador, pontos cardeais, coordenadas e marcadores de renascimento e de outros jogadores.
- Mapa expandido em tela cheia aberto com `M`, com arrastar para mover, zoom pela rodinha e `M`/`Esc` para fechar.
- Cache incremental por chunk, com varredura limitada a 2 ms por tick e atualização por mudança de bloco.
- Tela de configuração (raio, tamanho, rotação, forma, raio do mapa, marcadores, multiplayer), integração opcional com Mod Menu e persistência em `config/minimap.json`.
- Traduções `pt_br` e `en_us`.

[0.1.0]: https://github.com/Mine-Minecraft-Mods/minimap/releases/tag/v0.1.0

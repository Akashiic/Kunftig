# Varas 3D (GeckoLib)

O Kunftig desenha em 3D, animadas, as varas de pesca que um resource pack declara em
`assets/<namespace>/kunftig/item_models_3d.json` ([issue #13](https://github.com/Akashiic/Kunftig/issues/13)). Os
modelos são Bedrock (geometria 1.12.0, animação 1.8.0), carregados pelo **GeckoLib Unofficial 1.0.4**
(`geckolib-unofficial-1.7.10-1.0.4.jar`, commit `9af9be1`, o do TEOF). O pack das varas é o do AkashicFishing
(`tools/rod3d` lá).

| Onde | O quê |
|---|---|
| Primeira pessoa | modelo animado no lugar do ícone 2D, com o balanço, o equipar e a oscilação do vanilla |
| Terceira pessoa | na mão, seguindo o braço; o braço faz a pose do estado (`animation.braco.<estado>`) |
| Chão e moldura | modelo animado (no chão gira e flutua como qualquer item) |
| Inventário e GUI | o ícone 2D de sempre (mostra a estrela de qualidade) |

Sem GeckoLib, sem o pack, com o modelo quebrado ou com `-Dkunftig.rodModels.disable=true`: o ícone 2D (item models,
[API.md](../API.md) seção 9). Nada quebra.

## Requisitos

* Cliente: Kunftig + GeckoLib Unofficial 1.0.4 (`geckolib3`) + o resource pack.
* Servidor: Kunftig (para `KunftigRods`, API 7) e o plugin chamando a API. Sem isso, o cliente ainda faz `sacar`,
  `lancar` e `parada` sozinho.

## Como funciona

| Peça | Onde | O quê |
|---|---|---|
| Manifesto | `rodmodel/RodModelIndex`, `RodModelDef` | lê os `item_models_3d.json` de todos os packs (recarrega com F3+T e com packs do servidor); separa a chave `kunftig:model` em vara, qualidade, estágio (`.m<k>`) e `.cast` |
| Estados | `rodmodel/RodTimeline` (por jogador) | `sacar` ao trocar para a vara (olhando o item no fim do tick, por causa do Backhand), `lancar` no clique (ou quando a boia aparece, para os outros), `parada` quando a linha volta de um estado em laço; o resto vem do servidor. Vale o evento mais recente; o que não é laço volta a `parada` no fim |
| Protocolo | `rodmodel/RodWire`, canal `kunftig:rod` | `STATE` (entidade, estado, ticks já passados, tensão) e `TENSION`; servidor em `rodmodel/RodStateTracker` + `bukkit/rod/BukkitRodBridge` |
| Desenho | `client/rodmodel/gecko/*` | `IItemRenderer` da vara vanilla, por cima de qualquer outro já registrado (que continua com as varas sem modelo 3D); a mão secundária do Backhand fica com o ícone 2D |
| Posições | `rodmodel/RodDisplays` | desfaz as transformações que o 1.7.10 e o Forge aplicam a um item plano e aplica o `display` do manifesto no referencial das prévias |
| Linha | mixin em `RenderFish` | a linha sai do locator `linha` (capturado no osso `ponta`) na cor de `cor_da_linha` |
| Braço | mixin em `ModelBiped.setRotationAngles` | soma a rotação do braço do estado (o levantar de item do vanilla, −18°, sai: as animações já seguram a vara à frente) |
| Graveto | mixin em `RenderPlayer.renderEquippedItems` | com a linha fora, o 1.7.10 desenha um graveto em terceira pessoa; a vara 3D fica (esconde a própria linha pendurada) |

Cuidados com o GeckoLib 1.0.4 (lidos no código do commit `9af9be1`, conferidos no jogo e tratados aqui):

* **O GeckoLib não lê resource packs.** O `GeckoLibCache` só percorre os packs que o FML lista na partida
  (`FMLClientHandler.resourcePackList`: o do vanilla e os dos mods); os packs que o jogador ou o servidor escolhem
  entram depois, no `refreshResources`, e o GeckoLib nunca os vê. Sem tratar isso, `getModel` cai no modelo de teste do
  próprio GeckoLib (`geckolib3:geo/testdiagonal2.geo.json`, uma caixa) e as animações "não existem" (visto no
  harness). O Kunftig lê a geometria e as animações de cada vara pelo `IResourceManager` (que vê todos os packs) e as
  põe no cache do GeckoLib, conferindo a cada desenho, porque cada recarga de recursos cria mapas novos.
* **Os ossos são do modelo em cache, um por arquivo `.geo`**, não por jogador: a pose de cada jogador é guardada e
  reposta (`RodGeoModel`), senão um segundo desenho no mesmo quadro (passada de sombra, outro jogador entre os dois)
  sairia com a pose de outro.
* **Tickers**: o `AnimatedGeoModel` registra um ticker no barramento do FML para cada chave nova e nunca remove. O
  `RodGeoModel` usa o relógio do Kunftig e não registra nenhum; as chaves são poucas (uma por jogador visível,
  reaproveitadas) e nunca o NBT.
* **`ModelFetcher`**: sem um, os controladores do GeckoLib não acham as animações. O Kunftig registra o dele.
* **Locators não existem** nos ossos do 1.0.4: a ponta da linha é a `posicao` de repouso de `ponta_da_linha`
  passada pela matriz do osso `ponta`.
* **Um desenho só por modelo** no `IGeoRenderer`: as passadas (opaca; `luz` com lightmap 240 e sem sombreamento, como
  o `MeshBasicMaterial` das prévias; translúcidas sem escrever profundidade) percorrem a hierarquia inteira e desenham
  só os cubos da passada.
* **Estado de GL**: o GeckoLib tem um cache próprio (`GlStateManager`); o Kunftig repõe o estado do GL depois de
  desenhar e deixa o cache coerente com ele.
* `catmullrom` é linear e degraus são dois keyframes: o pack já vem amostrado; nada a fazer no cliente.

## O manifesto (`_formato: 1`)

Chave: o modelo do item sem os sufixos (`vara/vara-solar` em `assets/akashicfishing/` é `akashicfishing:vara/vara-solar`;
o item `akashicfishing:vara/vara-solar.ouro.m3.cast` usa esse modelo no estágio 3). Recursos como `namespace:caminho`.

| Campo | Obrigatório | O quê |
|---|---|---|
| `geo`, `animacoes`, `textura` | sim | geometria, animações e textura do estágio 0 |
| `braco` | não | animações do braço (osso `braco_direito`); sem ele, o braço fica como no vanilla |
| `texturas_mutacao` | não | estágio → textura, ou lista de quadros (troca a cada `ticks_por_quadro`, padrão 3); um estágio sem entrada usa o de baixo |
| `estados` | sim, com `parada` | estado → `vara` (controlador "vara", obrigatório), `aura` (controlador "aura"), `braco`, `laco` |
| `luz`, `translucido` | não | ossos com brilho máximo / desenhados depois, sem escrever profundidade (por osso, sem herança) |
| `mutacao` | não | estágio → ossos que só aparecem a partir dele (esconder um osso esconde os filhos) |
| `ponta_da_linha` | não | `osso` e `posicao` (repouso, pixels) de onde a linha sai; sem ele, a linha do vanilla |
| `esconder_com_a_linha_na_agua` | não | ossos escondidos com a linha fora (exceto durante `lancar`, que os tira no chicote) |
| `cor_da_linha` | não | estágio → `rrggbb`, vale do estágio para cima; sem ele, preta |
| `pega` | não | o ponto do modelo que vai para a mão (pixels) |
| `display` | sim, por contexto | `rotation` (graus, sinais do Bedrock), `translation` (pixels), `scale`, aplicados depois de levar a `pega` à origem |
| `contextos` | não | onde desenhar em 3D (padrão: os de `display`) |

Campos desconhecidos (`nome`, `controladores`, `molang`, `quando`, chaves com `_`) são ignorados. Um modelo com erro
fica de fora (ícone 2D) e o erro vai para o log (`Kunftig 3D rods: ...`) e para `/kunftigclient rods`.

### Referencial do `display`

O mesmo das prévias (`tools/rod3d/previa/render.js` no AkashicFishing): modelo em pixels com X espelhado (como o
GeckoLib e o Blockbench), rotação `Rz(z)·Ry(−y)·Rx(−x)`, translação com X espelhado.

* `primeira_pessoa`: a câmera (o 1.7.10 desenha a mão com campo de visão 70, como as prévias). O balanço, o equipar e
  a oscilação do vanilla continuam; as rotações do golpe saem (as animações da vara substituem).
* `terceira_pessoa`: o ombro direito, Y para cima, mão 10 pixels abaixo (o manequim das prévias), girando com o braço.
* `chao`, `moldura`: a entidade do item, Y para cima.

## Ajuste no jogo

```
/kunftigclient rods                                         estado, quantas varas, problemas no manifesto
/kunftigclient rods state briga 0.8                         toca um estado na sua vara (sem servidor)
/kunftigclient rods display terceira_pessoa                 o display em uso para a vara na mão
/kunftigclient rods display terceira_pessoa 80 0 0 0 0 0 0.6   experimenta um display em todas as varas
/kunftigclient rods display terceira_pessoa reset
```

O display experimentado sai no chat e no log como JSON, pronto para o `DISPLAY` do `tools/rod3d/gerar.py`.

## Testes

* `./gradlew test`: manifesto, sufixos, texturas e cores por estágio, amostragem das animações, máquina de estados,
  protocolo, rastreador do servidor, matrizes do `display` (desfazer o vanilla devolve exatamente o `display`).
* `./gradlew :test -ProdPack=<pasta do pack>`: confere um pack de verdade (todo arquivo, osso e animação citados
  existem). O pack do AkashicFishing (branch `claude/kind-ride-ts9apr`): 8 varas, nenhum problema.
* Harness visual (desenvolvimento): com um arquivo `kunftig-rod-harness` em `run/client` e o pack em
  `run/client/resourcepacks`, `./gradlew runClient` abre um mundo plano, segura varas com modelos reais, toca estados,
  lança a linha e salva `screenshots/rods-*.png` de cada contexto (`dev/RodRenderHarness`, fora do jar).

### O que o harness mostrou (Forge 1.7.10 de desenvolvimento, Mesa por software, pack da branch `claude/kind-ride-ts9apr`)

* O GeckoLib 1.0.4 monta as 8 geometrias do pack (UV por face, `inflate`, rotação de cubo) e toca as animações, com
  `query.tensao` vergando a vara na briga.
* Primeira pessoa igual à prévia (`vara-solar-primeira-pessoa.gif`, quadro de `parada`): ponta em (0,64; 0,50) da tela
  contra (0,65; 0,49) na prévia.
* Terceira pessoa de perfil (outro jogador) igual à prévia de ação em `parada`, `briga` e `captura`, com o braço.
* A linha sai da ponta em primeira e terceira pessoa, na cor do estágio; a linha pendurada some com a boia fora; em
  terceira pessoa a vara não vira graveto.
* Estágio 6 (texturas animadas e coroa), véu translúcido do estágio 4, moldura (diagonal) e chão.
* Vara sem modelo 3D e cliente sem GeckoLib: exatamente como antes (ícone 2D, graveto, linha preta).

Falta ver no cliente do TEOF, com Angelica, Backhand e NecroTempus, e com vários jogadores (desempenho): ver
[a issue](https://github.com/Akashiic/Kunftig/issues/13).

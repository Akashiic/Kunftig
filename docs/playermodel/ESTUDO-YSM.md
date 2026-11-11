# Estudo: modelos de jogador estilo YSM no Kunftig (1.7.10)

Estudo técnico para um motor de **modelos de jogador customizados** no Kunftig, compatível com o formato aberto de
autoria do **Yes Steve Model** (YSM), para rodar na 1.7.10 modelos do nível dos atuais (100+ ossos, ~1000 cubos,
texturas 512², dezenas de animações, física de cabelo/saia) e para fazermos **modelos nossos**.

Nada aqui foi implementado ainda. Tudo o que depende de teste em jogo está marcado como **a checar**.

**Onde a porta acontece:** num mod próprio, o **3D Futura Engine** (`modid` `futura3d`), bifurcação do YSM em
[Akashiic/YesSteveModel](https://github.com/Akashiic/YesSteveModel): a branch `main` é a porta 1.7.10 e a `dev/1.20`
guarda o upstream intacto. Decisões tomadas depois deste estudo: cliente em Java 8 e servidor em Java 21 (um jar em
bytecode Java 8); só modelos abertos (os embutidos CC0 e CC BY-NC-SA e os nossos), nenhum modelo de terceiros
comprado entra em repositório; o Molang do upstream depende do Semaweave (binário Java 17) e é reescrito. O plano
vivo está em `docs/PORTING.md` do fork; a seção 5 abaixo (pacotes dentro do Kunftig) ficou como alternativa.

## 1. Escopo e limites (o que fazemos e o que não fazemos)

**Fazemos:** um motor próprio que lê o formato **aberto** de autoria (pasta ou zip com `ysm.json`, geometria Bedrock,
animações Bedrock, Molang, texturas PNG, sons Ogg). É o formato em que os artistas trabalham antes de exportar, e o
mesmo que vamos usar para os nossos modelos.

**Não fazemos, nunca:**

* ler, decifrar ou contornar arquivos `.ysm` exportados (cabeçalho `YSGP` + payload cifrado, `<crypto> 3`). Modelos
  comprados nesse formato só rodam aqui se o autor fornecer a pasta-fonte e permitir o uso;
* olhar ou portar o código de proteção: `EncryptTools`, `AESUtil`, `YsmFormat`, `YesModelUtils`, `DeflateUtil` do
  LgeacyYSM; o módulo `legacy` do YesSteveModel-Native (ChaCha, deobfuscador, CityHash modificado); a verificação de
  assinatura ED25519 dos containers `.mxc`;
* embutir modelos de terceiros: cada modelo traz a própria licença em `ysm.json` (`metadata.license`), e muitos são
  *All Rights Reserved* ou *CC BY-NC-SA* (não comercial: não vender como VIP).

### 1.1 De onde podemos aproveitar código

| Fonte | Licença | Uso |
|---|---|---|
| [LgeacyYSM](https://github.com/YesSteveModel/LgeacyYSM) (YSM ≤ 1.1.5 hotfix2, Forge 1.20.1, último commit 2025-11-24) | BSD-3-Clause | Referência e código adaptável, mantendo o aviso de copyright |
| GeckoLib 3 modificado, dentro do LgeacyYSM e da reescrita (`geckolib3/`) | MIT (upstream bernie-g) | Idem, com o aviso MIT |
| mclib math (McHorse), dentro dos dois | MIT | Parser de expressões (referência) |
| [YesSteveModel](https://github.com/YesSteveModel/YesSteveModel) (reescrita, branch `dev/1.20`, último commit 2026-11-01) | Apache-2.0 (código); os padrões em `docs/standards` são **CC0** | Referência de comportamento do formato spec 2; código com NOTICE se copiado |
| Mocha (Molang, unnamed) dentro da reescrita | MIT | Referência |
| Modelos embutidos do LgeacyYSM `default`, `default_boy`, `steve`, `alex`, `qingluka` | **CC0** | Fixtures de teste e animações de fallback |
| Modelo `wine_fox` | CC BY-NC-SA 4.0 | Só teste local; não distribuir |
| Modelos embutidos da reescrita | não Apache; ver cada `ysm.json` | Não usar |

Recomendação: **reimplementar** o núcleo (geometria, animação, Molang) em Java 8 limpo, sem classes do Minecraft e com
testes, usando os dois códigos como especificação. Onde copiarmos trechos (por exemplo o `SecondOrder`), guardar o
aviso de origem num `NOTICE` do módulo.

## 2. O formato de modelo (spec 2)

Exemplo real aberto: `YSM-Model/ysm/ds_神秘小骑士` (106 ossos, 955 cubos, textura 512², 53 animações).

```
<modelo>/
  ysm.json
  models/main.json        geometria de terceira pessoa (obrigatória)
  models/arm.json         geometria dos braços em primeira pessoa (obrigatória)
  animations/main.animation.json
  animations/extra.animation.json   (emotes)
  animations/<conjunto>.animation.json   arm, fp_arm, tac, carryon, parcool, swem, slashblade, tlm...
  controllers/*.json      (opcional) controladores Bedrock
  textures/*.png          cada PNG é uma variante selecionável
  sounds/**.ogg           (opcional) Vorbis ou Opus
  functions/<nome>@<evento>.molang   (opcional)
  lang/*.json             (opcional)
  avatar/*.png            (opcional) foto dos autores
```

Sem `ysm.json`, vale o layout 1.x: `main.json`, `arm.json`, `<conjunto>.animation.json` e todo `*.png` na raiz.

### 2.1 `ysm.json`

* `metadata`: `name`, `tips`, `license{type, desc}`, `authors[]{name, role, avatar, contact{}, comment}`, `link{}`.
  Só exibição.
* `properties`:

| Chave | Padrão | Efeito |
|---|---|---|
| `height_scale`, `width_scale` | 0,7 | Escala **só de desenho** (a hitbox continua vanilla) |
| `extra_animation` | `extra0..7` | Mapa ordenado animação → rótulo da roleta; chave `#id` abre a subpágina `id` |
| `extra_animation_classify[]` | | `{id, extra_animation{}}`; `#return` volta |
| `extra_animation_buttons[]` | | Botões de configuração com formulários (checkbox, range, radio) que escrevem variáveis Molang |
| `preview_animation`, `disable_preview_rotation`, `gui_*` | | Prévia na GUI |
| `default_texture` | primeira | Nome do arquivo sem extensão |
| `render_layers_first`, `all_cutout`, `merge_multiline_expr`, `free`, `origin_ver` | | Ver a reescrita (`ModelProperties.java`) |

* `files.player`: `model{main, arm}`, `animation{conjunto: arquivo}`, `animation_controllers[]`, `texture[]` (string
  ou `{uv, normal, specular}` para PBR), `sound_path`, `function_path`, `language_path`.
* `files.projectiles` / `files.vehicles`: modelos para flechas, tridente, boia, barco, carrinho (fase 3).

### 2.2 Geometria

* Usa `minecraft:geometry[0]`, formato Bedrock 1.12.0.
* Cubos: `origin`, `size`, `pivot`, `rotation`, `inflate`, `mirror`; UV em caixa `[u, v]` ou por face
  `{dir: {uv, uv_size}}`. Tamanho negativo é legal (casca de contorno, face frontal descartada).
* A rotação do cubo é assada nos vértices ao carregar. A UV é normalizada pelo `texture_width/height` **declarado
  naquele arquivo** (o `arm.json` do exemplo declara 128 com imagem de 512).
* Transformação do osso: `T(pivot + pos) · Rzyx(rot) · S(escala) · T(−pivot)`; x negado, rotação em graus no arquivo,
  x e y negados.
* `locators`, `poly_mesh`, `texture_meshes`, `binding` são ignorados pelo YSM: os "locators" do YSM são **ossos**.

### 2.3 Ossos com nome especial

| Osso | Uso |
|---|---|
| `Head` | Item de cabeça; rotação do olhar (1.x aplica `headPitch`/`netHeadYaw` por código) |
| `LeftHandLocator`, `RightHandLocator` | Itens na mão |
| `ElytraLocator` | Élitro (não existe na 1.7.10) |
| `LeftShoulderLocator`, `RightShoulderLocator` | Papagaios (não existe na 1.7.10) |
| `BackpackLocator`, `LeftWaistLocator`, `RightWaistLocator` | Acessórios (`waist-locator` aparece nos `<features>` dos modelos) |
| `BladeLocator`, `SheathLocator` | **SlashBlade**: lâmina e bainha (encaixa nas lâminas da API 11) |
| `PistolLocator`, `RifleLocator` | Armas TaCZ (não existe na 1.7.10) |
| `ViewLocator`, `AllHead` | Altura da câmera em primeira pessoa (`view-locator`) |
| `LeftArm`, `RightArm`, `Background` (em `arm.json`) | Braços e fundo em primeira pessoa |
| prefixo `ysmGlow` | Osso com luz total (não herda para os filhos) |

`*Armor`, `*N`, `Root`, `AllBody` **não** são especiais: o autor os esconde por Molang (por exemplo
`UpperBodyN: scale "ysm.has_chest_plate ? 0 : 1"`). O YSM não desenha armadura vanilla no modelo. Escala 0 num grupo
de locator esconde o anexo vanilla.

## 3. O motor, peça por peça

### 3.1 Animação

* Arquivo Bedrock 1.8.0: `animation_length`, `loop` (`true`/`false`/`"hold_on_last_frame"`), `blend_weight`
  (Molang), canais `rotation|position|scale` como constante, escalar, string Molang ou keyframes
  (`pre`/`post`, `lerp_mode`, `easing`), `timeline` (Molang em instantes), `sound_effects`.
* Easing: a reescrita só honra `linear` e `catmullrom`; o 1.x honra a família GeckoLib inteira. Implementar o
  superconjunto.
* **Controladores** em ordem (o último vence por osso e canal):
  `pre_parallel0..7` → `vehicle` → `pre_main*` → **`main`** → `post_main*` → `pre_hold*` → `hold_offhand` →
  `hold_mainhand` → `post_hold*` → `pre_swing*` → `swing` → `post_swing*` → `pre_use*` → `use` → `post_use*` →
  `passenger` → **`cap`** (emote) → **`parallel0..7`** → `armor_<slot>`.
* **Mistura:** controladores sobrescrevem; os `parallel*` **somam** rotação ao acumulado (posição e escala
  sobrescrevem). Entrada linear de 0,1 s em main/hold/use/vehicle/passenger, 0 em swing/cap/parallel; ossos que
  deixam de ser animados voltam ao repouso em 3 ticks (no 1.x, 1 tick). `pre_parallel*` é a base (física, piscar,
  esconder adereços); `parallel*` são camadas aditivas.
* Animação ausente no modelo cai na animação de mesmo nome do modelo padrão: precisamos de um **modelo padrão nosso**
  (base: `default` CC0 do LgeacyYSM).

### 3.2 Máquina de estados `main` e o que existe na 1.7.10

O primeiro verdadeiro vence:

| Prioridade | Estado | 1.7.10 |
|---|---|---|
| Máxima | `death` (uma vez) | `isDead`/`deathTime > 0` |
| | `riptide` | não existe |
| | `sleep` | `isPlayerSleeping()` |
| | `swim`, `climb`, `climbing` (pose de nado/rastejar) | não existem; `swim` pode virar correr na água (decidir) |
| | `ladder_up` / `ladder_stillness` / `ladder_down` | `isOnLadder()` + velocidade vertical |
| Alta | `fly` | `capabilities.isFlying` |
| | `elytra_fly` | não existe |
| Normal | `swim_stand` | `isInWater()` |
| | `attacked` (uma vez) | `hurtTime > 0` |
| | `jump` | `!onGround && !isInWater()` |
| | `sneak` / `sneaking` | `isSneaking()` com/sem movimento |
| Baixa | `run` | `isSprinting()` |
| | `walk` | `limbSwingAmount > 0,05` |
| Mínima | `idle` | |

Montaria (controlador `vehicle`): `vehicle$<entidade>`, `vehicle#<tag>`, `ride_pig`, `ride` (cavalo), `boat`, `sit`.

**Nomes resolvidos por item** (ordem: `$id`, `#tag`, `:classe`): `hold_mainhand`, `swing`, `use_mainhand`,
`<slot>$id` para armadura. Na 1.7.10:

* `$id`: nome do registro (`GameData.getItemRegistry().getNameForObject`), por exemplo `minecraft:diamond_sword`;
* `#tag`: mapear para o **OreDictionary**;
* `:classe`: `sword|axe|pickaxe|shovel|hoe|bow|fishing_rod|slashblade` pelas classes do item;
  `:eat|drink|block|bow` por `EnumAction`;
* tudo de `offhand`, `crossbow`, `shield`, `spear`, `spyglass` nunca dispara (sem Backhand).

### 3.3 Molang

O que os modelos usam de verdade (contagem no exemplo): `ysm.second_order`, `ysm.head_yaw`, `ysm.head_pitch`,
`ysm.has_*`, `math.clamp/sin/cos/lerp/exp`, `q.ground_speed`, `q.vertical_speed`, `q.yaw_speed`,
`query.anim_time`, `query.life_time`, `v.*`.

* **Sintaxe** (reescrita): `q.`=`query.`, `v.`=`variable.` (por entidade, com structs `v.a.b`), `t.`=`temp.`,
  `c.`; strings `'…'`, `?:`, `??`, `->`, `=`, `{}`, `loop`, `for_each`, `break`, `continue`, `return`; funções com
  argumentos (`ysm.bone_rot('Head')`, `q.is_item_name_any(...)`).
* **Falha macia:** símbolo desconhecido, aridade errada ou erro de parse valem 0 / falso / nada. Nunca derrubar o
  modelo.
* `math.sin`/`math.cos` em **graus**.
* **Quirks a manter:** `query.head_x_rotation` devolve o *yaw* e `query.head_y_rotation` o *pitch* (trocados).
* O GeckoLib 1.0.4 não serve: rejeita tudo fora de `^[\w\d\s_+-/*%^&|<>=!?:.,()]+$` (sem strings, `{}`, `??`),
  não tem `q.`/`v.` e o escopo de variáveis é global para todos os jogadores. Precisamos de parser + interpretador
  próprios (árvore compilada em closures; o exemplo avalia algumas centenas de expressões por quadro).

Tabelas completas de `query.*`, `ysm.*` e `ctrl.*` com fontes: ver o relatório da reescrita
(`EntityBuiltins.java`, `ModelBuiltins.java`, `ControlReadBuiltins.java`). Mínimo da fase 1: tudo o que o
LgeacyYSM registra (`AnimationRegister.setParserValue`) mais `ysm.second_order`, `ysm.first_order`,
`ysm.bone_glow`, `ysm.bone_color`, `ysm.bone_transparency`, `ysm.is_close_eyes`.

### 3.4 Física (`ysm.second_order` / `ysm.first_order`)

Não há física de corpos: o cabelo e a saia balançam por um **filtro de segunda ordem** chamado do Molang
(`SecondOrder.java`, técnica do vídeo "Giving Personality to Procedural Animations using Math", t3ssel8r).

```
second_order(nome, x, f = 1, ζ = 1, r = 1)    f ∈ [0, 5] Hz, ζ ∈ [0, 1]
k1 = ζ/(πf);  k2 = 1/(2πf)²;  k3 = r·ζ/(2πf)
ẋ = (x − x_ant)/dt;  x_ant = x
passos = ceil(dt / (sqrt(4·k2 + k1²) − k1))
por passo h = dt/passos:  y += h·ẏ;  ẏ += h·(k3·ẋ + x − y − k1·ẏ)/k2
first_order(nome, x, R):  y = (1 − dt/R)·y + (dt/R)·x
```

* Estado por entidade **e por nome**: expressões com o mesmo nome compartilham o simulador.
* Na primeira chamada devolve a entrada crua; depois devolve `y` (um quadro de atraso). `dt` = Δticks de render / 20.
* Proteger `f = 0` (divisão por zero).
* Puro Java, sem Minecraft: entra nos testes unitários com valores de referência.

### 3.5 Desenho

* **Trocar o jogador:** cancelar `RenderPlayerEvent.Pre` (existe e é cancelável na 1.7.10) e desenhar o modelo.
  Manter: sombra, nome (o Kunftig já tem o mixin de visibilidade), tinta vermelha de dano, fogo, invisibilidade.
* **Primeira pessoa:** não existe `RenderArmEvent`; usar mixin em `RenderPlayer.renderFirstPersonArm` / no
  `ItemRenderer`, coordenado com os renderizadores `EQUIPPED_FIRST_PERSON` das varas e das lâminas.
* **Itens na mão:** percorrer a cadeia até `RightHandLocator`, `glMultMatrix`, e desenhar com
  `RenderManager.instance.itemRenderer.renderItem(...)` com as transformações por tipo de item do `RenderBiped`.
  As varas 3D, a linha de pesca (`RodModels.captureTip`) e as lâminas (`BladeRenderer`) hoje assumem o braço vanilla:
  precisam passar a ler os locators.
* **Desempenho (o maior risco):** o GeckoLib 1.0.4 transforma cada vértice na CPU e aloca um `Vector4f` por vértice.
  Com ~1000 cubos são ~24 mil vértices por jogador por passada. Plano:
  1. assar a geometria estática de **cada osso numa display list** (ou VBO) ao carregar;
  2. por quadro: `glPushMatrix` → transformação do osso → `glCallList`, ~100–200 chamadas por jogador;
  3. partições por osso (opaco, opaco sem culling, translúcido) decididas pelo alfa da textura sob cada face, como a
     reescrita faz; brilho com `OpenGlHelper.setLightmapTextureCoords(240, 240)`; cor e alfa com `glColor4f`;
  4. culling por frustum e distância, e redução de detalhe longe (sem física, menos camadas).
  Compatibilidade com **Angelica** (shaders) e display lists: **a checar** cedo.
* Texturas: `ImageIO` + `DynamicTexture` (nada no Kunftig usa `DynamicTexture` ainda).

### 3.6 Rede

* **Distribuição:** o servidor lista os modelos (catálogo com hash SHA-256 de cada arquivo); o cliente pede só o que
  não tem no cache em disco (chave = hash); arquivos em **fragmentos < 32 KB** (limite do payload custom na 1.7.10),
  remontados com limite de tamanho.
* **Estado por jogador:** modelo, variante de textura, emote em curso (`extra*`), variáveis `v.roaming`/`ysm.sync`
  (≤ 16 floats), retransmitido aos que veem o jogador.
* **Nunca** desserialização Java (o LgeacyYSM e o GeckoLib 1.0.4 mandam `ObjectOutputStream`: vetor de ataque). Só
  JSON/PNG/OGG validados com limites: ossos, cubos, dimensões de textura, tamanho de expressão, duração de som.
* **Nunca** Molang enviado pelo servidor para execução (a reescrita removeu `molang_execute` de propósito).
* Nome de canal Bukkit 1.7 tem no máximo 16 caracteres: `kunftig:pmodel` cabe, `kunftig:playermodel` não.
* Seleção decidida pelo servidor; modelos restritos exigem autorização (no 1.x, pasta `auth/` + capability).
* Não copiar o upload de arquivos por OP do 1.x: resolve o nome sem checar caminho (path traversal).

### 3.7 GUI

Seletor de modelo (favoritos, páginas, prévia 3D), seletor de textura, roleta de emotes com subpáginas e botões de
configuração, tela de depuração dos valores Molang. Tudo em `GuiScreen`/`GuiButton` da 1.7.10.

## 4. Por que não reaproveitar o GeckoLib 1.0.4 como está

| Precisa | GeckoLib Unofficial 1.0.4 |
|---|---|
| Molang do YSM (strings, `v.`, funções com argumentos, escopo por jogador) | Não; regex restritiva, escopo global |
| Camadas aditivas (`parallel*`) | Não; controladores só sobrescrevem |
| Locators | Não (`GeoBone` não tem) |
| Ossos e pose por instância | Não; `GeoBone` compartilhado (os displays já salvam e restauram a pose por chave) |
| Desenho rápido | Não; Tessellator imediato, transformação e alocação por vértice |
| Modelos vindos do servidor com segurança | Não; `ObjectInputStream` |

Os displays e as varas continuam no GeckoLib. O motor de modelos de jogador não depende dele.

## 5. Arquitetura proposta no Kunftig

Mesmo desenho dos displays: núcleo sem classes do Minecraft e testado, cliente e mixins separados, API Bukkit.

```
com.akashic.kunftig.playermodel            sem Minecraft, testes unitários
  format/      ModelManifest (ysm.json spec 2 + layout 1.x), limites de validação
  geo/         GeoParser (Bedrock 1.12), GeoModel imutável, bake por osso e partição
  anim/        AnimationClip, Keyframe, Easing, AnimationController, pilha de controladores, mistura
  molang/      lexer, parser, AST compilado, escopos q/v/t/c/ysm/ctrl, falha macia
  physics/     SecondOrder, FirstOrder
  state/       MainStateMachine, matchers de item ($id/#tag/:classe) sobre uma interface PlayerSnapshot
  wire/        PlayerModelWire (catálogo, fragmentos, estado)
com.akashic.kunftig.client.playermodel
  PlayerModelClient     REGISTER do canal, remontagem, cache em disco por hash, DynamicTexture
  PlayerModelRenderer   display lists, passadas, glow, locators
  FirstPersonArms, PlayerLocators (alimenta varas e lâminas), telas
com.akashic.kunftig.mixin.mixins.playermodel   RenderPlayer, primeira pessoa, payload; kill switch próprio
com.akashic.kunftig.bukkit.playermodel         BukkitPlayerModelBridge
api: KunftigPlayerModels (API 12): drawsPlayerModels, setModel/getModel, setTexture, playExtra/stop,
     autorização; docs/API.md seção 21
```

* Harness de desenvolvimento: modo `pmodel` (carrega pastas de `run/kunftig-models/`, desenha N cópias, mede
  ms/quadro e KB alocados por quadro como o `RodPerfHarness`).
* Fixtures de teste: modelos CC0 do LgeacyYSM (`default`, `steve`, `alex`...).
* Java 8: nada de `Map.entry`, `Stream.toList()` e afins (o código de referência usa os dois).

## 6. Fases

| Fase | Entrega | Critério de aceite |
|---|---|---|
| 0. Prova | Parser de geometria + display lists + troca do jogador local por um modelo CC0 parado | Desenha certo; mede ms/quadro com 1, 10 e 30 cópias de um modelo de ~1000 cubos, com e sem Angelica |
| 1. Animação | Clips, keyframes, pilha de controladores, mistura, estados `main`, Molang base, `second_order`, `ysmGlow` | O modelo de exemplo anima com física de cabelo e piscar; testes unitários do Molang e da física |
| 2. Jogador completo | Itens nos locators, primeira pessoa, armadura por Molang, montaria, emotes `extra`, escala | Varas, linha de pesca e lâminas presas nos locators; sem regressão nos harness `fish`, `blades` |
| 3. Servidor | Catálogo, fragmentos, cache, seleção, autorização, roleta e seletor, API 12 | Dois clientes veem os modelos um do outro num Crucible; cliente sem o mod vê skin normal |
| 4. Extras | Controladores Bedrock, funções `@evento`, sons Vorbis, `v.roaming`, mapeamento SlashBlade (`slashblade:*`) e TLM, projéteis | Modelos com controladores e sons rodam |

Fora de escopo por enquanto: PBR (normal/specular), Opus, container `.mxc`. Para sempre: `.ysm` cifrado.

## 7. Fazendo os nossos modelos

1. **Blockbench**, projeto *Bedrock Entity*. Hierarquia recomendada (a do modelo `default` CC0, 48 ossos e 172
   cubos): `Root` → `AllBody` → `UpBody` e `DownBody`; em `UpBody`: `AllHead` → `Head` (rosto, olhos, cabelo),
   `Arm` → `LeftArm` → `LeftForeArm` → `LeftHandLocator` (e o lado direito), `ElytraLocator`; em `DownBody`: `Leg` →
   `LeftLeg` → `LeftLowerLeg` → `LeftFoot`, e `Skirt`. Pivôs nas articulações.
2. Ossos de acessório com os nomes da seção 2.3 (`BladeLocator`, `SheathLocator`, `LeftWaistLocator`...); partes que
   brilham com prefixo `ysmGlow`; partes que somem com armadura agrupadas para esconder por Molang.
3. Exportar geometria (`models/main.json`, `models/arm.json`) e animações (`animations/main.animation.json` com os
   nomes da seção 3.2; emotes em `extra.animation.json`).
4. Física: canal de rotação com `ysm.second_order('cabelo', <entrada>, 2, 0.4, 1)` em `pre_parallel0`.
5. `ysm.json` mínimo:

```json
{
  "spec": 2,
  "metadata": { "name": "Nosso Modelo", "license": { "type": "All Rights Reserved" },
                "authors": [ { "name": "TEOF", "role": "modelo" } ] },
  "properties": { "height_scale": 0.7, "width_scale": 0.7,
                  "extra_animation": { "extra0": "Acenar" } },
  "files": { "player": {
    "model": { "main": "models/main.json", "arm": "models/arm.json" },
    "animation": { "main": "animations/main.animation.json", "extra": "animations/extra.animation.json" },
    "texture": [ "textures/padrao.png" ] } }
}
```

Esse mesmo pacote roda no YSM moderno, então os nossos modelos servem nos dois mundos.

## 8. Riscos e o que está a checar

* Desempenho com muitos jogadores na tela e com Angelica (display lists dentro do pipeline de shaders).
* Conflito com outros mods que mexem no desenho do jogador no TEOF: Backhand, NecroTempus (passe de brilho no
  `RendererLivingEntity`), Akashic Player Backport (pernas), e os hooks do próprio Kunftig (`RenderPlayerBladeMixin`,
  `RenderPlayerCastRodMixin`, `ModelBipedRodArmMixin`, `ModelBipedBladePoseMixin`).
* Precisão do Molang frente ao YSM real: comparar lado a lado com o YSM 1.20 nos mesmos modelos abertos.
* Limite real do payload custom na 1.7.10 (`S3F`/`C17` usam `short` de tamanho): **a checar** no jar.
* Animações de fallback: só a partir de modelos CC0 ou nossos.

## 9. Referências

* LgeacyYSM `1.20.1-forge`: `client/animation/AnimationRegister.java` (estados e variáveis),
  `client/entity/CustomPlayerEntity.java` (controladores), `geckolib3/core/processor/AnimationProcessor.java`
  (mistura aditiva), `geckolib3/geo/render/GeoBuilder.java` (locators), `client/event/ReplacePlayer*` (troca do
  jogador e dos braços).
* YesSteveModel `dev/1.20`: `docs/standards/**` (CC0), `docs/architecture/**`,
  `client/controller/collections/PlayerControllerCollection.java`, `client/animation/physics/SecondOrder.java`,
  `expression/**` (Molang), `format/parser/GeoBuilder.java`, `RawModelAssembler.java`.
* YSM-Model: `ysm/ds_神秘小骑士` (exemplo spec 2) e `bbmodel/` (projetos Blockbench de 365 a 1600 cubos).

## 10. Jogadores com modelo do 3D Futura Engine: varas, linha e lâminas

O Kunftig fala com o 3D Futura Engine (`futura3d`) só por reflexão, pela API estável dele
(`com.akashic.futura.api.FuturaModels`), em `client/compat/FuturaModels` (procura uma vez, guarda os method handles,
falha em silêncio). Sem o futura3d, com outra API ou com `-Dkunftig.futura3d.disable=true`, nada muda: o bíped como
sempre. Nenhum mixin novo.

![Varas e lâminas em jogadores com modelo (harness `models`)](modelos-futura3d.png)

De cima para baixo, da esquerda para a direita: vara 3D com a linha fora (modelo `default`), a mesma num outro jogador
visto de lado durante a briga, a vara no modelo `wine_fox/13_matured`; a lâmina fora da bainha (combo), a lâmina na
bainha (parada) e fora (combo) no `wine_fox/13_matured`. A lâmina das fotos é um substituto feito para o harness (uma
caixa com as medidas da lâmina do SlashBlade), não a arte do SlashBlade.

**Varas.** O futura3d desenha o item da mão no `RightHandLocator` pelo `ItemRenderer` da 1.7.10, então o
`RodItemRenderer` (`EQUIPPED`) roda lá, com as mesmas transformações por item do bíped, e a vara 3D sai na mão do
modelo. O `captureTip` de terceira pessoa continua valendo: a matriz de `RenderPlayerEvent.Pre` é a da câmera nos dois
casos. Com a linha fora o futura3d trocaria a vara por um graveto (como o vanilla e o `RenderPlayerCastRodMixin`, que
não roda para quem tem modelo, porque o `renderEquippedItems` não roda): o Kunftig registra
`FuturaModels.setKeepsItemWhileFishing(keepsRodWhileCast)` e a vara fica. A linha: o `RenderFishLineMixin` do Kunftig
cancela o desenho no `startDrawing` quando há ponta capturada; o mixin do futura3d só mexe nos `addVertex` da linha
vanilla (saindo do graveto na mão do modelo), então os dois compõem. `BladePose`, `RenderPlayerBladeMixin`,
`ModelBipedRodArmMixin` e `ModelBipedBladePoseMixin` não se aplicam a quem tem modelo (o `rotateCorpse` e o
`setRotationAngles` do bíped não rodam): a pose é a do modelo.

**Pesca nos modelos** (futura3d com API 4). O corpo do modelo acompanha a vara: o futura3d toca `fishing:<fase>` num
controlador próprio (`player.fishing`; ver o futura3d, `docs/PORTING.md`, seção "Pesca") e infere sozinho o que o
cliente vê (sacar, lançar, esperar, recolher a linha vazia). O que só o servidor sabe o Kunftig repassa
(`rodmodel/FishingStateFeed`, puro e testado, chamado de `RodModels.feedModels` a cada tick do cliente): o último
estado que o servidor mandou pelo `kunftig:rod` para cada jogador (`mordida`, `briga`, `recolher`, `captura`,
`parada`, `escapou`...), **qualquer que seja a vara na mão** (a vanilla também; os estados do servidor ficam num mapa
à parte das linhas do tempo das varas 3D), e a tensão, como os estados externos `fishing.state` e `fishing.tension`.
Só mudanças; o mesmo estado começado de novo (uma segunda mordida) é limpo e posto, e o clipe recomeça; um estado que
toca uma vez e já tem mais de 20 ticks quando chega (quem se aproxima depois de uma captura) vai como `parada`. Os
estados que o próprio cliente infere para a vara 3D (`sacar`, `lancar`, a `parada` de fim de clipe) não vão: o
futura3d tem as regras dele para esses momentos. Precisa do canal registrado, ou seja, do GeckoLib no cliente (como
no TEOF); **a checar**: num servidor com o AkashicFishing e dois clientes.

**A vara reta e o lançar nos modelos.** No primeiro teste no TEOF a vara 3D saía inclinada para o lado nas mãos do
modelo: o `display` de terceira pessoa das varas (`[80, 35, 0]`) conta com o braço do bíped girado pelos clipes `braco`
(−55° em Y), e o modelo segura a vara com os clipes de pesca dele. Agora, com o jogador desenhado pelo futura3d
(`FuturaModels.drewModel`) e uma vara na mão, o `RodItemRenderer` usa `RodModelDef.modelDisplay`: o
`terceira_pessoa_modelo` do manifesto, ou o `terceira_pessoa` sem o giro em Y; o futura3d mantém a vara reta à frente
do tronco (`anim/RodAim`). E o `lancar` das varas, feito para o bíped (que arma antes e arremessa aos 0,5 s), começa
`RodTimeline.MODEL_CAST_LEAD_TICKS` (6 ticks) adiantado num jogador com modelo, para os chicotes da vara caírem na
armada e no arremesso do modelo, que arremessa com a boia. Harness `modelfishing` (`kunftig-rod-harness` =
`modelfishing`, o jar de dev do futura3d e o pack do AkashicFishing): o jogador local de frente lançando, esperando e
brigando, e outro de lado esperando, brigando, lançando e em cada reação das varas Sanrio (foto no futura3d,
`docs/img/pesca-varas-3d.png`).

**Peixes 3D nos modelos** (futura3d com API 5). As faixas de tamanho dos peixes do AkashicFishing posam o braço do
bíped (`braco`/`braco_esquerdo`) e o `display` do peixe é posto para esse braço; num jogador com modelo o mixin do
bíped não roda, e o peixe ficava num braço parado (dentro do corpo, ou o gigante virado para o chão). Agora
`RodModels.poseModelArms` (em `RenderPlayerEvent.Pre`, antes de o modelo ser desenhado) manda a mesma rotação para os
braços do modelo (`FuturaModels.setArmPose`), só para modelos segurados que não são vara: as varas ficam com os
clipes de pesca do próprio modelo. Conferido no harness `modelrods` (abaixo): as 16 varas 3D do pack com a textura
certa no modelo, e o tucunaré, o peixe-akasha, o peixe-palhaço e o pirarucu como no bíped (foto no futura3d,
`docs/img/varas-peixes-kunftig.png`).

Harness `modelrods` (`kunftig-rod-harness` = `modelrods`, o jar de dev do futura3d em `run/client/mods`, o pack do
AkashicFishing selecionado): cada vara 3D do pack, uma vara mutada dourada e quatro peixes 3D na mão do jogador local
com o modelo `default` do futura3d, cada um ao lado do mesmo item no bíped (futura3d desligado), e a vara com a linha
fora.

**Lâminas** (`BladeRenderer.onRenderPlayerPost`). `RenderPlayerEvent.Specials` não dispara para jogador com modelo;
a lâmina vai em `RenderPlayerEvent.Post`, depois do modelo, nos locators do desenho que acabou
(`locatorInLastDraw`, que vale também no inventário), nos quadros do `SlashBladeRender` do YSM upstream (1/100 de bloco
por unidade do OBJ):

| Modelo | Parada (sem combo) | Durante um combo |
|---|---|---|
| com `BladeLocator`, `SheathLocator` e `LeftWaistLocator` (convenção do YSM 2.x; todos os modelos embutidos do futura3d) | lâmina e bainha no `LeftWaistLocator` | lâmina no `BladeLocator`, bainha no `SheathLocator`; escondidos (os modelos os escondem num `pre_parallel` fora das animações `slashblade:*`), no `RightHandLocator` e no `LeftWaistLocator` |
| sem eles | lâmina e bainha no `LeftWaistLocator`; sem ele, a lâmina no `RightHandLocator` | lâmina no `RightHandLocator`, bainha no `LeftWaistLocator` ou em lugar nenhum |

A lâmina que não está em uso (slot 0 ou a mão do Backhand) vai embainhada no `RightWaistLocator`, ou em lugar nenhum.
"Combo" é o estado do servidor ainda tocando (`BladeClient.drawn`); quando a corrente acaba e volta o `standby`, a
lâmina volta para a bainha. Um locator escondido (escala 0) esconde o que iria nele.

Verificado no harness (`kunftig-rod-harness` = `models`, com o jar de dev do futura3d em `run/client/mods`, o pack do
AkashicFishing e um pack de lâmina substituto em `run/client/resourcepacks`): vara 3D na mão, vara 3D com a linha
fora (a linha dourada sai da ponta), briga visto de lado, vara vanilla lançada com a linha saindo do graveto na mão,
lâmina na bainha e fora dela nos modelos `default` e `wine_fox/13_matured`, `isModelled` falso em primeira pessoa.

**Combos nos modelos** (futura3d com API 3). O futura3d agora toca as animações `slashblade:*` que os modelos trazem,
a partir de estados externos que o Kunftig diz por reflexão (`FuturaModels.setExternalState`), em
`BladeRenderer.onRenderPlayerPre` (antes de o modelo ser desenhado), só com mudanças (`blade/ModelStateFeed`):

* `slashblade.held` enquanto a mão principal segura uma lâmina do Kunftig: o modelo usa a classe `:slashblade`
  (`hold_mainhand:slashblade`) e o estado principal como `slashblade:<estado>` (`slashblade:idle`,
  `slashblade:walk`...), como o `SlashBladeAnimation.playMainAnimation` do upstream.
* `slashblade.combo` com o estado de combo do SlashBlade enquanto a corrente toca (`BladeClient.comboName`): o servidor
  manda trechos de quadros, não nomes, então o nome vem do **primeiro quadro da corrente** (`blade/SlashBladeCombos`:
  `combo_a1` 1, `combo_a2` 100, `combo_a3` 200, `combo_a4` 500, `aerial_rave_a1` 1100, `aerial_rave_b3` 1400,
  `judgement_cut` 1900, os que o harness do Kunftig já compara com o SlashBlade, e os outros no mesmo arranjo, **a
  checar** contra o registro do SlashBlade: `combo_c` 400, `combo_b1` 700, `combo_a4ex` 800, `combo_a5ex` 900,
  `aerial_rave_a2/a3/b4` 1200/1300/1500, `upperslash` 1600, `upperslash_jump` 1700, `rapid_slash` 2000,
  `rising_star` 2100, `void_slash` 2200). A regra do upstream para o ar: `judgement_cut` fora do chão é
  `judgement_cut_slash_air`. Um pack com outro movimento nomeia os seus no `blades.json`
  (`"combos": {"combo_a1": 1, ...}`, ver `docs/API.md`, seção 20), e a tabela dele substitui a do SlashBlade. O clipe
  do modelo (`slashblade:combo_a1`) toca uma vez no controlador `swing` e cobre a corrente inteira
  (`combo_a1` → `combo_a1_end` → `combo_a1_end2`); uma corrente nova que começa no mesmo estado limpa e põe o nome de
  novo, e o clipe recomeça.
* **Locators, revistos**: com o modelo tocando essas animações (`FuturaModels.playsBladeAnimations`: futura3d com API
  3, `slashblade.held` dito e o modelo com `slashblade:idle`, o que vale para todo modelo, que cai no conjunto do modelo
  padrão) e com os três locators, a lâmina vai como no `newMainhandSlashBlade` do upstream: lâmina e bainha juntas no
  `LeftWaistLocator`, a lâmina nua no `BladeLocator`, a bainha no `SheathLocator`, sempre os três, e os clipes escondem
  os que não estão em uso (no `combo_a1`, um golpe embainhado, a lâmina fica na cintura que vai com a mão esquerda; no
  `combo_a3` ela sai). Sem o futura3d novo, a escolha por estado acima continua; sem o futura3d, o bíped como sempre.

![Combos no wine_fox/13_matured (harness `modelcombos`)](combos-futura3d.png)

Da esquerda para a direita, de cima para baixo: parada (`slashblade:idle`, a lâmina embainhada na cintura),
`combo_a1`, `combo_a3` (lâmina no `BladeLocator`, bainha no `SheathLocator`), `aerial_rave_a1`, `combo_a3` de costas,
`combo_a3` no modelo `default`. Harness `modelcombos` (`kunftig-rod-harness` = `modelcombos [lâmina]`, o jar de dev do
futura3d em `run/client/mods`, o pack substituto da lâmina): toca as correntes como o servidor mandaria
(`BladeClient.play`, sem plugin) e o log de cada foto diz o que o modelo toca (`playingAnimation` dos controladores
`main`, `swing` e `hold_mainhand`, pela API 3) e o que foi dito a ele. Conferido: `slashblade:idle` e
`hold_mainhand:slashblade` parado, `slashblade:combo_a1`/`combo_a3`/`aerial_rave_a1` no `swing` durante as correntes,
nada depois, também num outro jogador visto de lado e no modelo `default`.

**A checar**:

* Os nomes dos combos além dos sete conferidos (acima), num pack com o movimento do SlashBlade de verdade e um plugin
  (Overkill) mandando as correntes; um jogador que chega perto no meio de uma corrente vê o clipe do começo.
* O clipe do modelo e a lâmina do Kunftig andam juntos só pelo tempo: o clipe do YSM foi feito contra o SlashBlade e a
  corrente dura o mesmo no Kunftig, mas não foi comparado quadro a quadro.
* Com a arte de verdade do SlashBlade (`sbref`): a orientação da lâmina e da bainha nos locators (segue os números do
  upstream, mas o OBJ do harness é uma caixa) e o tamanho (o upstream usa 1/100 de bloco por unidade, a lâmina sai
  com uns 2,9 blocos; o Kunftig no bíped usa 1/128).
* Na parada, o `LeftWaistLocator` dos modelos embutidos fica na mão esquerda; é a animação `slashblade:idle` que leva
  a mão à cintura (agora tocada; a lâmina embainhada sai de lado, a comparar com o YSM 1.20). Com um futura3d antigo,
  sem essa animação, a lâmina embainhada fica na mão esquerda, apontando para a frente.
* ~~Seguimento: tocar as animações `slashblade:*` do modelo a partir dos estados de combo do Kunftig~~: feito (acima,
  "Combos nos modelos").
* A cor da linha da vara 3D vista de lado saiu preta numa das fotos (a do jogador lateral); a da frente, dourada.
* Num servidor de verdade, com outros jogadores e a boia desenhada antes do pescador.

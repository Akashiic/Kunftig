# Estudo: partículas no mundo e partículas modernas no 1.7.10

> Objetivo: dar a plugins no Thermos/Crucible 1.7.10 (1) partículas para todos os jogadores por perto, já que o
> Crucible de produção não tem `World.spawnParticle` (issue #2), e (2) as partículas modernas (`END_ROD`,
> `SOUL_FIRE_FLAME`, `FALLING_DUST`...) desenhadas pelo Et Futurum Requiem nos clientes que o têm (issue #1).

Guia de uso: [docs/API.md](../API.md), seção 7.

---

## 1. Conclusão

Um único recurso resolve as duas issues: `KunftigParticles` (no `kunftig-api`) monta os pacotes de partícula uma vez
por efeito e envia direto para a conexão de cada jogador no raio. Clientes com o Kunftig recebem um nome
`kunftig:<partícula>` e desenham com o Et Futurum Requiem; os demais recebem a partícula vanilla mais parecida. Não
depende de `org.bukkit.Particle`, nem de `World.spawnParticle`, nem de nenhum pacote novo.

## 2. Fatos verificados

| Fato | Onde | Consequência |
|---|---|---|
| O `spawnParticle` do Crucible converte o `Particle` pelo nome em `MinecraftParticles`; o que não existe lá (`END_ROD`, `DRAGON_BREATH`, `DAMAGE_INDICATOR`, `SWEEP_ATTACK`, `FALLING_DUST`, `TOTEM`, `SPIT`) vira **`FLAME`** | `CraftParticle.toNMS` | o cliente recebe `flame` e não tem como saber que era um `END_ROD`: o caminho 1 da issue #1 (mixin só no cliente) é impossível |
| `BARRIER`, `WATER_DROP`, `ITEM_TAKE` e `MOB_APPEARANCE` vão com nomes do 1.8 (`barrier`, `droplet`, `take`, `mobappearance`) | `MinecraftParticles` | o cliente 1.7.10 não desenha nada |
| O padrão de `extra` no Crucible é 1 | `CraftPlayer.spawnParticle` | o Kunftig usa o mesmo padrão (e o mesmo do Paper) |
| O servidor vanilla manda partículas só a quem está a até **16 blocos** (`d² <= 256`) | `WorldServer.func_147487_a` | raio padrão 16 |
| O cliente descarta partículas a mais de **16 blocos** da câmera (exceto `hugeexplosion`, `largeexplode` e `fireworksSpark`) e aplica a opção "Partículas" (Mínimo nada; Reduzido 1/3 a menos) | `RenderGlobal.doSpawnParticle` | raio maior que 16 não adianta para a maioria das partículas; o Kunftig aplica as mesmas regras às do Et Futurum |
| Nome desconhecido no pacote: o cliente não desenha nada, sem erro. O nome tem no máximo 64 caracteres | `RenderGlobal.doSpawnParticle`; `S2APacketParticles.readStringFromBuffer(64)` | um nome `kunftig:...` é inofensivo, mas só é enviado a quem o entende |
| `iconcrack_`, `blockcrack_` e `blockdust_` precisam de `_<id>_<meta>` (blocos falham sem o meta) e de um id existente | `RenderGlobal.doSpawnParticle` | o servidor valida e troca ids desconhecidos por pedra, como o Crucible |
| `reddust`, `mobSpell` e `mobSpellAmbient` tiram a cor da velocidade; `reddust` troca vermelho exatamente 0 por 1 | `RenderGlobal`, `EntityReddustFX` | cor = um pacote por partícula com `count` 0; vermelho 0 vira 0,0001 |
| Com `count` 0, uma partícula com velocidade `offset * speed`; com `count` > 0, cada partícula em `posição + gauss * offset` com velocidade `gauss * speed` | `NetHandlerPlayClient.handleParticles` | o Kunftig replica isso no cliente e, para cores, no servidor |
| O cliente manda `REGISTER` e o Crucible chama `CraftPlayer.addChannel` | patch do `ChannelRegistrationHandler` no Crucible | `Player#getListeningPluginChannels()` diz quem tem o Kunftig |
| Et Futurum Requiem 2.6.64 (`etfuturum`): `CustomParticles` tem métodos estáticos `spawn...(World, x, y, z[, cor | vx, vy, vz])` que devolvem o `EntityFX` já adicionado | `javap` no jar | a ponte chama esses métodos |
| Esses métodos **não** olham a opção de partículas nem a distância | bytecode de `CustomParticles.spawnParticle` | o Kunftig faz essas verificações antes |
| A cor do Et Futurum é **ARGB** (alfa nos bits 24–31): `0xRRGGBB` sem alfa fica invisível | construtores de `EtFuturumFXParticle` e `CustomDripFX` | o Kunftig sempre manda alfa 255 |
| Mel do Et Futurum: `0xFF8E6D14`; coluna de bolhas sobe com velocidade `(0, 0.04, 0)` | `BlockBeeHive`, `BlockBubbleColumn` | cores e movimento padrão iguais aos do Et Futurum |

## 3. Como funciona

```
plugin ── KunftigParticles.builder(END_ROD)...spawn()
            │  (mod no servidor) ParticlePlan: uma vez por efeito
            ├─ cliente com Kunftig (registrou "kunftig:fx") ──> S2A "kunftig:end_rod"  ──> mixin no handleParticles
            │                                                                           ├─ Et Futurum instalado: CustomParticles.spawnEndRodParticle
            │                                                                           └─ sem Et Futurum: fallback vanilla (fireworksSpark)
            └─ cliente sem Kunftig ───────────────────────────> S2A "fireworksSpark"
```

* **Servidor** (`BukkitParticleBridge`): jogadores do mesmo mundo dentro do raio, ou a lista dada. Cada um recebe
  `forKunftigClient()` ou `forVanillaClient()` do `ParticlePlan`, calculados uma vez só.
* **Formato**: o próprio pacote de partícula do vanilla; o nome leva `kunftig:<partícula>` e, opcionalmente,
  `#AARRGGBB`. Posição, offsets, velocidade e quantidade mantêm o significado vanilla. Sem canal de dados, sem
  versão: nome desconhecido (de um Kunftig mais novo) não desenha nada.
* **Cliente** (`NetHandlerPlayClientParticleMixin` + `ParticleClient`): no `JoinGame` registra `kunftig:fx`; no
  `handleParticles` pega só os nomes `kunftig:` e repete a expansão vanilla. Partícula moderna com Et Futurum:
  verifica opção de partículas e distância e chama o `CustomParticles`; sem Et Futurum, ou se o método não existir,
  desenha o fallback pelo caminho vanilla.
* **Et Futurum sem dependência de compilação**: cada método é resolvido por `MethodHandle` na primeira vez. Se uma
  versão futura do Et Futurum mudar um método, só aquela partícula cai no fallback (com um aviso no log).
* **Cores para todos**: `DUST`, `ENTITY_EFFECT` e `AMBIENT_ENTITY_EFFECT` coloridos funcionam também em clientes sem
  o Kunftig (um pacote por partícula, com o espalhamento calculado no servidor como o cliente faria). Clientes com o
  Kunftig recebem um pacote só.

## 4. Tabela

Nomes do 1.21. `KunftigParticle.fromName` aceita também os do `org.bukkit.Particle` do Crucible.

| `KunftigParticle` | Crucible (`Particle`) | Com Kunftig + Et Futurum | Fallback (sem Kunftig ou sem Et Futurum) |
|---|---|---|---|
| `END_ROD` | `END_ROD` (vira `flame`) | `spawnEndRodParticle` | `fireworksSpark` |
| `SOUL_FIRE_FLAME` | — | `spawnSoulFlame` (com velocidade) | `flame` |
| `FALLING_DUST` (cor) | `FALLING_DUST` (vira `flame`) | `spawnFallingDustParticle` | `reddust` na cor |
| `DRIPPING_HONEY` (cor opcional) | — | `spawnDrippingParticle`, mel `0xFF8E6D14` | `dripLava` |
| `DRIPPING_OBSIDIAN_TEAR` (cor opcional) | — | `spawnDrippingParticle`, roxo `0xFF8308E3` | `portal` |
| `CHERRY_LEAVES` | — | `spawnCherryLeaf` | `reddust` rosa |
| `CRIMSON_SPORE` / `WARPED_SPORE` | — | `spawnCrimsonSpore` / `spawnWarpedSpore` | `reddust` vermelho / ciano |
| `ASH` | — | `spawnAshParticle` | `townaura` |
| `BUBBLE_COLUMN_UP` | — | `spawnBubbleColumnUp` (com velocidade; padrão sobe 0,04) | `bubble` |
| `CURRENT_DOWN` | — | `spawnWaterCurrentDown` | `bubble` |
| `FALLING_NECTAR` | — | `spawnBeeNectarParticle` | `reddust` amarelo |
| `DAMAGE_INDICATOR` | `DAMAGE_INDICATOR` (vira `flame`) | `spawnBlackHeartParticle` | `crit` |
| `WAX_ON` / `WAX_OFF` / `SCRAPE` | — | `spawnCopperWax*` / `spawnCopperScrape` | `happyVillager` / `instantSpell` |
| as 38 partículas do 1.7.10 (`FISHING` = `wake`, `DUST` = `reddust`...) | iguais | vanilla | — |
| — | `BARRIER`, `WATER_DROP`, `ITEM_TAKE`, `MOB_APPEARANCE`, `DRAGON_BREATH`, `SWEEP_ATTACK`, `TOTEM`, `SPIT` | sem implementação no 1.7.10 nem no Et Futurum | — |

Nas partículas do Et Futurum sem parâmetro de velocidade, a velocidade do efeito é aplicada à partícula criada quando
não é zero.

## 5. Alternativas descartadas

1. **Mixin só no cliente, sobre o `spawnParticle` do Crucible** (caminho 1 da issue #1). Impossível: o Crucible
   manda `flame` para `END_ROD` e as outras modernas.
2. **Pacote próprio por canal de plugin.** Funciona, mas duplica o que o pacote vanilla já carrega (posição,
   espalhamento, velocidade, quantidade) e exige outro decodificador. O nome no pacote vanilla cabe em 64 caracteres
   e só vai para quem registrou o canal.
3. **Delegar ao `Player.spawnParticle` do Crucible.** Não existe no Thermos, não existe na API 1.7.10, e não leva cor
   nem partículas modernas.
4. **Dependência de compilação no Et Futurum.** Prenderia o Kunftig a uma versão; os `MethodHandle` por método
   degradam partícula por partícula.

## 6. Verificação feita

* 16 testes: tabela contra os nomes do 1.7.10 e do `Particle` do Crucible, padrões e validação do builder, atalhos,
  formato `kunftig:` (ida e volta de todas as partículas, com e sem cor, dentro de 64 caracteres; nomes malformados e
  futuros), nomes com ids, cor como velocidade (inclusive vermelho 0), plano por tipo de cliente (moderna, vanilla,
  colorida, item), espalhamento igual ao do cliente com semente fixa, e a tabela do Et Futurum cobrindo exatamente as
  modernas.
* Tabela do Et Futurum conferida com `javap -s` no jar 2.6.64: os 16 métodos existem com o descritor exato que a ponte
  procura.
* Refmap: `handleParticles` → `func_147289_a`, `handleJoinGame` → `func_147282_a`.
* Portão 1.7.10: o código Bukkit do mod e o `kunftig-api` compilam contra a API do Crucible.

**Não verificado** (depende de rodar servidor e cliente): plano manual.

1. Crucible com o Kunftig; um cliente com Kunftig + Et Futurum, outro só com Et Futurum, outro vanilla.
2. `KunftigParticles.builder(END_ROD).location(loc).count(10).offset(.5,.5,.5).extra(0.02).spawn()`: o primeiro vê
   o end rod, os outros dois veem fagulhas de fogo de artifício.
3. `DUST` com `color(0x00FF00)`, `count(20)`: todos veem verde puro; o vanilla recebe 20 pacotes, o Kunftig 1.
4. Opção "Partículas: Mínimo" no cliente com Kunftig: nada de end rod. Afastar-se mais de 16 blocos: some.
5. `-Dkunftig.particles.disable=true` no cliente: passa a ver o fallback.
6. Raio: um jogador a 20 blocos não recebe nada com o raio padrão.

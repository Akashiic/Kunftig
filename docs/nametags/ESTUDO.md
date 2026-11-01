# Estudo: nametags modernas no 1.7.10 (cliente Forge + Bukkit)

> Objetivo: nomes longos e coloridos (RGB e degradê) na **tab** e **acima da cabeça** em clientes 1.7.10, sem
> texto quebrado, funcionando em servidores Bukkit (Thermos/Crucible 1.7.10 e Paper 1.21 do lobby), atrás do
> Velocity e através do ViaVersion.

Especificação do protocolo: [PROTOCOL.md](PROTOCOL.md). Este documento explica o problema, o que foi verificado no
código real, as alternativas descartadas, o desenho escolhido e o que ainda está em aberto.

---

## 1. O recurso moderno que está sendo trazido

| Versão | O que mudou nos times (scoreboard teams) |
|---|---|
| 1.8 | opção `nametagVisibility`; nome de exibição por jogador na tab (`PlayerInfo.displayName`) |
| 1.13 | **prefixo e sufixo viram componentes de texto sem limite de tamanho**; a **cor do time** pinta o nome do jogador (o prefixo não "vaza" mais para o nome) |
| 1.16 | cores hexadecimais (`#rrggbb`) em componentes |

É isso que permite, num servidor moderno, um `[Administrador]` em degradê seguido de um nome colorido, sem limite
e sem uma cor "vazar" para a outra.

## 2. Por que o 1.7.10 quebra

Verificado no fonte decompilado do Minecraft 1.7.10 (RetroFuturaGradle, mapeamentos MCP stable 12):

* `S3EPacketTeams.readPacketData` lê nome, prefixo e sufixo com `readStringFromBuffer(16)` e o nome de exibição
  com `readStringFromBuffer(32)`. Passar disso **desconecta** o cliente ("received string length is longer than
  maximum allowed").
* O `CraftTeam` do Bukkit 1.7 lança `IllegalArgumentException` acima de 16 caracteres.
* Os 16 caracteres **incluem os códigos de cor**: `§c§l[Admin]` já gasta 4 só com formatação. Plugins que tentam
  dividir o texto entre prefixo, nome e sufixo acabam cortando um `§` ao meio ou perdendo a cor no meio do nome.
* O `FontRenderer` vanilla só conhece 16 cores. Uma cor hex no formato do Spigot (`§x§r§r§g§g§b§b`) gasta **14
  caracteres** por cor, e no renderer vanilla os dígitos hex são lidos como códigos de cor (o texto sai com cores
  aleatórias). Um degradê de 10 letras passa de 140 caracteres.

## 3. Fatos verificados (no código, não na memória)

| Fato | Onde foi verificado | Consequência no desenho |
|---|---|---|
| Nametag: `RendererLivingEntity.passSpecialRender` → guarda `func_110813_b` → `EntityPlayer.func_145748_c_` → `ScorePlayerTeam.formatPlayerName` → `formatString` → `getColorPrefix()`/`getColorSuffix()` | MC 1.7.10 decompilado | sobrescrever **os getters do time** muda a nametag |
| Tab vanilla e sidebar usam `formatPlayerName` | MC 1.7.10 | idem |
| A tab do **NecroTempus** usa `team.formatString(name)` e ordena pelo nome do time | `NecroTempus/.../playertab/client/DefaultPlayerTab.java`, `PlayerTabGui.getFormattedPlayerName` | idem; a prioridade entra no nome do time |
| NecroTempus 2.2.0 faz `@Redirect` em `readStringFromBuffer` do `S3EPacketTeams` e do `S38PacketPlayerListItem` (16 → 32) e `@ModifyConstant` no `CraftTeam` (Crucible) | `S3EPacketTeamsMixin`, `S38PacketPlayerListItemMixin`, `bukkit/scoreboard/CraftTeam` (commit `2312b8c`, 2026-06-28) | o Kunftig **não toca no pacote vanilla**: dois `@Redirect` no mesmo alvo dão conflito de Mixin; e 32 continua insuficiente para RGB |
| `S01`/`S3E`/`S3F` não têm `hasPriority()`: são processados na thread principal do cliente | `NetworkManager.channelRead0`, MC 1.7.10 | estado do cliente sem locks |
| O FML deixa passar para `handleCustomPayload` canais que ele não conhece | `NetworkDispatcher.handleClientSideCustomPacket` | o Kunftig **não registra canal no FML** (num servidor híbrido com o mod instalado, o FML roubaria as mensagens do Bukkit); recebe por mixin |
| `handleRespawn` mantém o mesmo objeto `Scoreboard`; `handleJoinGame` cria um novo | MC 1.7.10 | estilos são zerados no `JoinGame` (servidor novo/troca no proxy) |
| Angelica desenha `§x§R§R§G§G§B§B` com `enableFontRenderer && enableRGBColors`; se um `§x` reseta negrito etc. é **configurável** (`hexColorResetsStyles`); códigos de cor curtos sempre resetam | `Angelica/.../client/font/ColorCodeUtils.java`, `FormatCodes.java` | detecção por reflexão; o serializador funciona com qualquer valor dessa opção |
| Crucible: todo `C17` recebido faz `addChannel` (dispara `PlayerRegisterChannelEvent`) e canais fora do FML chegam ao `Messenger` do Bukkit; `sendPluginMessage` só envia para canais registrados | `Crucible/patches/.../NetworkDispatcher.java.patch`, `ChannelRegistrationHandler.java.patch`, `CraftPlayer.java` | o cliente manda `REGISTER` explícito + `HELLO` |
| Bukkit 1.7: canal ≤ **16** caracteres, payload ≤ **32766** bytes; não existe `Team#addEntry`, `setColor` nem `setOption` | `Crucible/bukkit/.../Messenger.java`, `Team.java` | canal `kunftig:nametag` (15); limites do protocolo; API nova só por reflexão |
| Velocity: `REGISTER` do cliente é registrado e **reenviado ao novo backend** na troca; mensagens backend→cliente de canais desconhecidos passam direto; cliente→backend ficam em fila até o handshake terminar | `Velocity/.../ClientPlaySessionHandler.java` (l. 343-418, 670-674), `BackendPlaySessionHandler.java` | nenhum plugin de proxy é necessário |
| ViaVersion só reescreve canais que não são `Key` válida; `kunftig:nametag` passa intacto nos dois sentidos | `ViaVersion/.../ItemPacketRewriter1_13.java` (`getNewPluginChannelId`/`getOldPluginChannelId`) | o mesmo protocolo funciona no lobby Paper 1.21 + ViaVersion |
| No Bukkit, `setEnabled(false)` marca o plugin desabilitado **antes** de `onDisable`; plugin desabilitado não envia plugin message | Bukkit `JavaPlugin.setEnabled` (confirmado no teste de integração) | o plugin não consegue avisar o cliente ao desligar; ids de time nunca se repetem entre habilitações |
| Permissão não registrada tem padrão `OP` no Bukkit | Bukkit `Permission.DEFAULT_PERMISSION` | permissões de grupo são registradas com padrão `false` (senão todo OP cairia em todo grupo) |

## 4. Alternativas consideradas

1. **Aumentar o limite de leitura dos pacotes vanilla** (o que o NecroTempus fez para 32). Descartada: cliente sem o
   mod é desconectado; conflita com o `@Redirect` do NecroTempus; exige patch no servidor (só existe para o
   Crucible); o ViaRewind continua cortando no lobby Paper; e mesmo 64 ou 128 não comporta degradês RGB.
2. **Interceptar cada renderizador** (tab vanilla, tab do NecroTempus, nametag). Descartada: frágil e incompleta;
   cada mod de tab/HUD precisaria de um patch.
3. **Nametags falsas** (entidades/hologramas). Descartada para nomes de jogador: não substitui a tab, duplica a
   lógica de visibilidade/agachar e custa entidades por jogador.
4. **Escolhida: canal lateral + sobrescrita no nível dos dados.** O servidor continua mandando times vanilla
   (versão curta e segura para todo mundo) e manda, por plugin message, a versão completa (componentes JSON) para
   quem tem o mod. O mod sobrescreve os **getters** do `ScorePlayerTeam`, então **todo** consumidor (nametag, tab
   vanilla, tab do NecroTempus, sidebar, outros mods) vê o texto completo sem nenhum patch próprio.

## 5. Arquitetura

```
 Servidor (Thermos/Crucible 1.7.10 ou Paper 1.21)          Cliente 1.7.10 (Forge + UniMixins)
 ┌──────────────────────────────────────────────┐          ┌───────────────────────────────────────────┐
 │ Kunftig-Bukkit                               │          │ Kunftig (mod)                             │
 │  grupos (config) ← /kunftig set ← API         │          │                                           │
 │        │                                     │  time    │  ScorePlayerTeam                          │
 │        ├─► time vanilla (≤16 no 1.7, cortado │ vanilla  │   getColorPrefix / getColorSuffix /       │
 │        │   sem quebrar código de cor) ───────┼─────────►│   formatString ◄── @Inject: estilo do     │
 │        │   em todo scoreboard em uso          │          │                     time (por nome)       │
 │        └─► TEAM_STYLE (JSON completo, ───────┼─────────►│  NametagClient (estado do protocolo)      │
 │            nameStyle, visibilidade)          │ kunftig: │  RgbSupport (Angelica? §x : 16 cores)     │
 │  KunftigChannel (handshake, sessões)  ◄──────┼─nametag──│  REGISTER + HELLO a cada JoinGame         │
 └──────────────────────────────────────────────┘          └───────────────────────────────────────────┘
            ▲ Velocity / ViaVersion repassam o canal sem alteração
```

Módulos do repositório:

| Módulo | Conteúdo |
|---|---|
| `common/` | Java 8 sem dependências: protocolo (codec com limites), modelo de texto, parser/escritor JSON estrito, renderizador legado, cores, degradê. Usado pelo mod e pelo plugin. |
| raiz (`src/`) | mod Forge 1.7.10 (convenção GTNH/RetroFuturaGradle, UniMixins). Sem dependência obrigatória além de um provedor Mixin. |
| `bukkit/` | plugin único para 1.7.10 e 1.8-1.21+ (compilado contra a API 1.8.8, verificado contra a 1.7.10 do Crucible; Adventure/MiniMessage embutido e realocado). |

## 6. Decisões de projeto (e o porquê)

* **Estilos indexados pelo nome do time, independentes do ciclo de vida do time vanilla.** Plugins de sidebar
  trocam o scoreboard do jogador o tempo todo, e o Bukkit então remove e recria todos os times; ligar o estilo ao
  objeto do time faria a nametag "piscar" de volta para a versão curta.
* **Semântica 1.7 por padrão, semântica 1.13 com `nameStyle`.** Sem `nameStyle`, a última cor do prefixo continua
  colorindo o nome (compatível com o que plugins 1.7 esperam). Com `nameStyle`, nome e sufixo começam de `§r`, como
  nas versões modernas, e o nome pode ter degradê por caractere (extensão do Kunftig; o 1.13 só permite as 16 cores
  no time).
* **Serialização legada correta em qualquer renderer.** Depois de qualquer código de cor todas as decorações ativas
  são reemitidas; desligar uma decoração usa um código de cor curto (que reseta em todo renderer) ou `§r`. Entre
  prefixo e nome sempre há `§r`: o servidor pode pôr no prefixo qualquer código (por exemplo o arco-íris `§q` do
  Angelica) que uma cor não garante encerrar.
* **Fallback vanilla montado caractere a caractere**, e não cortando a string pronta: cortar depois calcularia o
  código de cor do nome a partir do estado errado e poderia deixar meio `§x` no fim. Opcionalmente o admin define
  `vanilla-prefix`/`vanilla-suffix` (ex.: `&c&lADM` para quem não tem o mod, `Administrador` em degradê para quem tem).
* **Reconciliação de scoreboards por geração.** O Bukkit não avisa quando um plugin troca o scoreboard de alguém;
  a cada `sync-interval-ticks` o plugin olha os scoreboards em uso: quem já recebeu a atualização anterior recebe
  só a diferença; scoreboard novo (ou que perdeu uma atualização) recebe tudo, inclusive a limpeza de times
  antigos do Kunftig (`kf###xxxxxx`).
* **Um jar para todos os servidores.** Compilado contra a API mais antiga disponível (1.8.8), com um portão que
  recompila contra a API 1.7.10 real do Crucible (`scripts/build-legacy-bukkit-api.sh`). Recursos novos
  (`Team#setColor`, `setOption`) por reflexão; no 1.13+ o nome é colorido pela cor do time (o servidor descarta o
  código de cor no fim do prefixo).
* **Entrada de texto:** MiniMessage (padrão do Paper) aceitando também `&c`, `&#rrggbb`, `&x&r&r...` e JSON. Um
  código de cor legado vira `<reset><cor>` para manter a regra antiga "cor encerra negrito".

## 7. Compatibilidade com o pack

| Componente | Situação |
|---|---|
| NecroTempus 2.0/2.1/2.2 | compatível: o Kunftig não mexe nos pacotes que ele altera; a tab dele lê os getters sobrescritos. Com o NecroTempus 2.2 no servidor, `fallback.max-length: 32` é possível, mas só se **todo** cliente tiver o 2.2 |
| Angelica 2.2.x | RGB detectado por reflexão a cada tick (pode ser ligado/desligado no jogo); sem Angelica, 16 cores |
| ThePatcher (módulo RGB) | o `OPEN-ISSUES.md` dele registra que a ponte RGB não chega a instalar no `FontRenderer` (#17); por isso o modo `AUTO` só confia no Angelica. Quem tiver outro renderer com `§x` pode forçar `colorMode=RGB` |
| Mixins de outros mods em `func_110813_b` (NecroTempus) | compatível: todos os hooks do Kunftig são `@Inject` |
| Velocity | nada a instalar no proxy |
| Lobby Paper 1.21 + ViaVersion | o mesmo plugin funciona; clientes modernos veem o prefixo completo de forma nativa |

## 8. Verificação feita

* **87 testes automatizados**: 51 no `common` (codec, limites, JSON hostil, renderizador, truncagem, degradê), 9
  no cliente (composição prefixo/nome/sufixo), 18 unitários no plugin (parser, fallback em todos os limites de 2 a
  40, grupos, nomes de time) e **10 de integração** com o jar sombreado real rodando no MockBukkit (API Paper 1.21):
  handshake, estado completo, `/kunftig set`, troca de scoreboard, mudança de prioridade, saída, estilos externos,
  reload, disable/enable, cliente malicioso e flood.
* **Portão 1.7.10**: o plugin recompila contra a API Bukkit do Crucible; um teste negativo confirmou que ele
  rejeita `Team#addEntry` (só existe no 1.8+), que a compilação normal aceitaria.
* **Auditoria dos jars**: todas as classes em bytecode Java 8 (major 52); refmap do mixin com nomes SRG para todos
  os alvos; campos `@Shadow` reofuscados (`field_96677_a`, ...); Adventure 100% realocado (nenhuma referência a
  `net/kyori`); licenças MIT das bibliotecas embutidas em `META-INF/THIRD-PARTY-NOTICES.txt`.

**Não verificado** (precisa do seu ambiente, ver §9): cliente real renderizando, servidor real e o caminho pelo
Velocity/ViaVersion. Subir um servidor Minecraft aqui exigiria aceitar a EULA da Mojang, o que é decisão sua.

## 9. Plano de teste manual

1. **Backend Thermos/Crucible** com o plugin; cliente com o mod e Angelica (RGB ligado). Dar
   `kunftig.group.admin` a alguém: degradê completo na tab e acima da cabeça. `/kunftigclient` no cliente mostra
   `protocol 1`; `/kunftig status <jogador>` no servidor mostra o cliente e o time.
2. **Cliente sem o mod** no mesmo servidor: vê `ADM |` (o `vanilla-prefix`), sem desconectar, sem código quebrado.
3. **Angelica com RGB desligado** (`enableRGBColors=false`): mesmas cores, reduzidas às 16 legadas, sem lixo.
4. **NecroTempus**: a tab dele mostra o texto completo e ordena pela prioridade.
5. **Velocity**: backend → lobby → backend na mesma conexão; os estilos voltam após cada troca.
6. **Lobby Paper 1.21 + ViaVersion** com o plugin: cliente 1.7 com o mod vê tudo; cliente 1.21 vê tudo nativamente.
7. `visibility: never` num grupo: a nametag some só para clientes com o mod (no 1.7 não existe opção vanilla).
8. `/kunftig reload` com jogadores online: tudo volta sem relogar.

## 10. Limitações conhecidas e próximos passos

* **Nome de exibição por jogador na tab** (`PlayerInfo.displayName` do 1.8): hoje a tab mostra prefixo + nome +
  sufixo do time. Dá para adicionar como novo pacote do protocolo sem quebrar a versão 1 (ids novos são ignorados
  por clientes antigos).
* **Modo transparente no Paper**: espelhar automaticamente times criados por outros plugins (ex.: TAB) exige
  interceptar pacotes (PacketEvents/ProtocolLib). Hoje a integração é pela API (`setTeamStyle`), que já cobre o
  caso "outro plugin é dono do time".
* **Tab vanilla sem NecroTempus**: as colunas têm largura fixa no 1.7.10, então nomes longos invadem a coluna ao
  lado. Com o NecroTempus (o caso do pack) a largura se ajusta.
* **Times com nome > 16 no Paper 1.18+**: o ViaRewind precisa encurtar o nome para clientes 1.7, e aí o nome não bate
  com o do estilo. Os times do próprio Kunftig têm 11 caracteres; afeta só `setTeamStyle` com nomes longos.
* **Licença** do projeto ainda não foi escolhida.

# Kunftig (pt-BR)

Backports de recursos de versões recentes do Minecraft para o **1.7.10** (Forge + UniMixins). Um único jar de mod,
no cliente e no servidor (Thermos/Crucible 1.7.10), mais a API `kunftig-api` para plugins.

Cada build do CI (Actions) publica dois artefatos: **`kunftig-mod`**, o jar que vai na pasta `mods/` (servidor e
cliente), e **`kunftig-api`**, só para compilar plugins. O jar da API não é um mod: na pasta `mods/` ele não faz nada.

> English version: [README.md](README.md).

## Recurso 1: motor de nametags modernas no cliente (experimental)

> Experimental: só o lado cliente. O plugin de servidor que existia (Kunftig-Bukkit) foi removido, porque a rede já
> usa o TAB; falta decidir como o servidor alimenta o motor (veja abaixo).

O cliente 1.7.10 com o Kunftig aceita prefixos, sufixos e nomes de time **sem limite de tamanho**, com **cores RGB** e
**degradês**, na tab e acima da cabeça, sem texto quebrado: o comportamento dos times do 1.13+/1.16+. Ele recebe esses
estilos pelo canal `kunftig:nametag` ([protocolo](docs/nametags/PROTOCOL.md)), sobrescreve os dados do time e não o
desenho de cada tela, então funciona com **NecroTempus** (tab) e **Angelica** (RGB detectado automaticamente).

Estudo completo (problema, fatos verificados, alternativas, desenho, testes): [docs/nametags/ESTUDO.md](docs/nametags/ESTUDO.md).
Protocolo: [docs/nametags/PROTOCOL.md](docs/nametags/PROTOCOL.md).

## Recurso 2: API de pesca do 1.21.1 (servidor)

O ciclo de pesca do 1.21.1 para plugins Bukkit no Thermos/Crucible: os estados `LURED`, `BITE`, `FAILED_ATTEMPT` e
`REEL_IN` (`KunftigFishEvent`) e o `FishHook` moderno (`ModernFishHook`: tempos de espera, de lure, ângulo, Isca,
chuva/céu, água aberta, estado da boia), mais a janela da mordida para minigames e a pesca na lava. Com o perfil padrão a pesca fica
idêntica ao vanilla; o perfil `MODERN_1_21_1` usa os tempos do 1.21.1 e a regra de tesouro em água aberta.

Instale o mesmo jar do mod em `mods/` do servidor. Guia: [docs/fishing/API.md](docs/fishing/API.md). Estudo:
[docs/fishing/ESTUDO.md](docs/fishing/ESTUDO.md).

## Recurso 3: dados persistentes em itens (servidor)

O `PersistentDataContainer` do Bukkit 1.14 (com `NamespacedKey` e `PersistentDataType`) para itens no Thermos/Crucible,
no mesmo formato das versões modernas. Para marcar varas, iscas e peixes sem analisar lore nem usar NMS.

## Recurso 4: partículas no mundo e partículas modernas (servidor + cliente)

`KunftigParticles` manda partículas para todos os jogadores por perto (o Crucible de produção não tem
`World.spawnParticle`), com cores em qualquer cliente 1.7.10 e partículas modernas (`END_ROD`, `SOUL_FIRE_FLAME`,
`FALLING_DUST`, `CHERRY_LEAVES`, `BUBBLE_COLUMN_UP`...) desenhadas pelo **Et Futurum Requiem** nos clientes que têm o
Kunftig; os demais veem a partícula vanilla mais parecida. Estudo: [docs/particles/ESTUDO.md](docs/particles/ESTUDO.md).

E `KunftigPlayers.getPing(player)`, o `Player#getPing` do 1.17+.

## Recurso 5: ícone próprio para itens de plugin (cliente + servidor)

O `CustomModelData` do 1.14+ no 1.7.10: `KunftigItems.setModel(item, new NamespacedKey(plugin, "tucunare"))` faz o
item aparecer com um ícone do resource pack (listado em `assets/<namespace>/kunftig/item_models.json`) no inventário,
na mão, no chão e em molduras, para clientes com o Kunftig; os demais veem o ícone do item base. Veja
[docs/API.md](docs/API.md), seção 9.

**Plugins em outros repositórios** usam tudo isso pelo artefato `kunftig-api` (`compileOnly`): veja
[docs/API.md](docs/API.md).

## Instalação

| Onde | Jar | Requisitos |
|---|---|---|
| Cliente | `kunftig-<versão>.jar` em `mods/` | Forge 10.13.4.1614, UniMixins. RGB: Angelica com `enableRGBColors` |
| Servidor | o mesmo `kunftig-<versão>.jar` em `mods/` | Thermos/Crucible 1.7.10 com UniMixins |
| Proxy (Velocity) | nada | — |

## Mods que atrapalham no Crucible

### PlayerContainerFix (BQ3-Thermos-patch): remover no Crucible

O [PlayerContainerFix](https://github.com/GTNewHorizons/BQ3-Thermos-patch) 2.0.0 corrige o `ClassCastException` do
BetterQuesting no **Thermos** ([BetterQuesting#605](https://github.com/Funwayguy/BetterQuesting/issues/605)): o
`ContainerPlayer.onCraftMatrixChanged` do CraftBukkit converte `crafters.get(0)` para `EntityPlayerMP` sem conferir.
**O Crucible já traz essa correção no próprio patch do `ContainerPlayer`** (`//Crucible start - Fix ClassCastException
(see .../BetterQuesting/issues/605)`, com o mesmo `instanceof EntityPlayerMP`). No Crucible o mod não acrescenta nada.

E ele atrapalha. O coremod detecta o Bukkit com `Class.forName("org.bukkit.World")` enquanto os coremods carregam,
antes de o FML ligar a deobfuscação. O `org.bukkit.World` fica então com nomes ofuscados: `world.getWorldServer()` dá
`NoSuchMethodError` e qualquer reflexão em `World` dá `NoClassDefFoundError: mt`, para todos os plugins (issue #3).
O Kunftig avisa no log quando isso acontece.

**Recomendação: tirar o `PlayerContainerFix-2.0.0.jar` do `mods/` do Crucible.** Antes de tirar em produção, confira:

1. Que o `ContainerPlayer` do seu `server.jar` tem a correção. No jar ele se chama `aap`, o método é `a(rb)` e o
   `EntityPlayerMP` é `mw`; a saída deve ter `instanceof` com `class mw` (no vanilla esse método não tem nenhum):

   ```bash
   javap -c -cp server.jar aap | sed -n '/public void a(rb);/,/^$/p' | grep instanceof
   ```
2. Sem o mod, com o BetterQuesting: entrar, abrir o inventário e montar uma receita na grade 2x2 (tirar e pôr o
   resultado algumas vezes). Não deve aparecer `ClassCastException` em `ContainerPlayer` no log, e o resultado da
   receita deve atualizar normalmente.

No **Thermos** o mod continua necessário; lá o certo é ele detectar o Bukkit sem carregar a classe (texto para
reportar: [docs/upstream/playercontainerfix-early-bukkit-load.md](docs/upstream/playercontainerfix-early-bukkit-load.md)).

## Kill-switches do cliente

`-Dkunftig.nametag.disable=true`, `-Dkunftig.nametag.visibility.disable=true`, `-Dkunftig.particles.disable=true`,
`-Dkunftig.itemModels.disable=true`; configuração em `config/kunftig.cfg` (`colorMode=AUTO|RGB|LEGACY`).
`/kunftigclient` (no cliente) diz se o protocolo de nametags está ativo no servidor.

## Compilar

```bash
./gradlew build
```

Gera `build/libs/kunftig-<versão>.jar` (mod) e `api/build/libs/kunftig-api-<versão>.jar` e roda os testes. A versão vem da tag git; sem tag, defina `VERSION=...`.

Portão de compatibilidade com a API Bukkit 1.7.10 do Thermos/Crucible (mod e API):

```bash
scripts/build-legacy-bukkit-api.sh build/legacy-bukkit/bukkit-1.7.10.jar
```

```bash
./gradlew checkLegacyBukkitApi -PlegacyBukkitApi=build/legacy-bukkit/bukkit-1.7.10.jar
```

## Licença

Ainda não definida. As bibliotecas embutidas no plugin (Adventure, MIT) estão em
`META-INF/THIRD-PARTY-NOTICES.txt` dentro do jar.

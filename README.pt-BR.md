# Kunftig (pt-BR)

Backports de recursos de versões recentes do Minecraft para o **1.7.10** (Forge + UniMixins), com o lado servidor
para **Bukkit** (Thermos/Crucible 1.7.10 e Paper/Spigot até 1.21+).

> English version: [README.md](README.md).

## Recurso 1: nametags modernas (experimental)

> Experimental: coberto por testes automatizados, ainda não validado em servidor com clientes reais
> (plano de teste manual em `docs/nametags/ESTUDO.md`, seção 9).

Prefixos, sufixos e nomes **sem limite de tamanho**, com **cores RGB** e **degradês**, na tab e acima da cabeça,
sem texto quebrado. É o comportamento dos times do Minecraft 1.13+/1.16+, trazido para clientes 1.7.10.

* Quem tem o mod vê o texto completo. Quem não tem vê uma versão vanilla segura (até 16 caracteres no 1.7, cortada
  sem nunca quebrar um código de cor, ou um texto curto definido pelo admin).
* Funciona atrás do **Velocity** e através do **ViaVersion** (lobby Paper 1.21) sem plugin no proxy.
* Compatível com **NecroTempus** (tab), **Angelica** (RGB detectado automaticamente) e outros mods: o Kunftig
  sobrescreve os dados do time, não o desenho de cada tela.

Estudo completo (problema, fatos verificados, alternativas, desenho, testes): [docs/nametags/ESTUDO.md](docs/nametags/ESTUDO.md).
Protocolo: [docs/nametags/PROTOCOL.md](docs/nametags/PROTOCOL.md).

## Instalação

| Onde | Jar | Requisitos |
|---|---|---|
| Cliente | `kunftig-<versão>.jar` em `mods/` | Forge 10.13.4.1614, UniMixins. RGB: Angelica com `enableRGBColors` |
| Servidor | `Kunftig-Bukkit-<versão>.jar` em `plugins/` | Thermos/Crucible 1.7.10 ou Spigot/Paper 1.8+; Java 8+ |
| Proxy (Velocity) | nada | — |

## Configuração (servidor)

`plugins/Kunftig/config.yml`:

```yaml
nametags:
  groups:
    admin:
      permission: kunftig.group.admin   # padrão false, inclusive para OP
      priority: 100                     # ordem na tab (maior primeiro)
      prefix: "<gradient:#ff5555:#ffaa00><bold>Administrador</bold></gradient> <dark_gray>|</dark_gray> "
      vanilla-prefix: "&c&lADM &8| "     # o que vê quem não tem o mod (opcional)
      name: "#ffaa00 #ff5555"           # degradê no nome; "gold", "#ff8800 bold", "plain"...
      suffix: " <yellow>★</yellow>"
      visibility: always                # never | hideForOtherTeams | hideForOwnTeam
```

Texto em [MiniMessage](https://docs.advntr.dev/minimessage/format.html); `&c`, `&#rrggbb` e JSON também valem.

## Comandos e permissões

| Comando | Descrição |
|---|---|
| `/kunftig status [jogador]` | resumo, ou cliente/time/overrides de um jogador |
| `/kunftig set <jogador> <campo> <valor>` | override por jogador (`prefix`, `suffix`, `name`, `visibility`, `priority`, `vanilla-prefix`, `vanilla-suffix`), salvo em `players.yml` |
| `/kunftig clear <jogador> [campo]` | remove overrides |
| `/kunftig preview <texto>` | mostra como fica para clientes com e sem o mod |
| `/kunftig reload` | recarrega sem relogar ninguém |
| `/kunftigclient` (no cliente) | diz se o protocolo está ativo neste servidor |

Permissão de admin: `kunftig.admin` (OP). Kill-switches do cliente: `-Dkunftig.nametag.disable=true`,
`-Dkunftig.nametag.visibility.disable=true`; configuração em `config/kunftig.cfg` (`colorMode=AUTO|RGB|LEGACY`).

## API para outros plugins

```java
NametagApi api = KunftigNametags.api();          // softdepend: [Kunftig]
api.setOverride(player, NametagSpec.builder().prefix("<rainbow>Evento</rainbow> ").build());
api.setTeamStyle("time_do_outro_plugin", NametagSpec.builder().prefix("<#55ffff>[Staff muito longo] ").build());
api.isKunftigClient(player);                     // + evento KunftigClientReadyEvent
```

## Compilar

```bash
./gradlew build
```

Gera `build/libs/kunftig-<versão>.jar` (mod) e `bukkit/build/libs/Kunftig-Bukkit-<versão>.jar` (plugin) e roda os
testes unitários e de integração (MockBukkit). A versão vem da tag git; sem tag, defina `VERSION=...`.

Portão de compatibilidade com a API Bukkit 1.7.10 do Thermos/Crucible:

```bash
scripts/build-legacy-bukkit-api.sh build/legacy-bukkit/bukkit-1.7.10.jar
```

```bash
./gradlew :bukkit:checkLegacyBukkitApi -PlegacyBukkitApi=build/legacy-bukkit/bukkit-1.7.10.jar
```

## Licença

Ainda não definida. As bibliotecas embutidas no plugin (Adventure, MIT) estão em
`META-INF/THIRD-PARTY-NOTICES.txt` dentro do jar.

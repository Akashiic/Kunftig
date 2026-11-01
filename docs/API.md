# kunftig-api: como usar o Kunftig em outro repositório

O Kunftig é uma API geral. Plugins Bukkit (como o plugin de pesca, que vive em outro repositório) compilam contra o
artefato **`com.akashic.kunftig:kunftig-api`** e rodam num servidor Thermos/Crucible com o **mod Kunftig** em `mods/`.

```
plugin de pesca  --compileOnly-->  kunftig-api (interfaces, eventos, tipos; sem Minecraft)
       |                                   ^
       | em tempo de execução              | as mesmas classes, embutidas no mod
       v                                   |
Thermos/Crucible  --  mods/kunftig-<versão>.jar (implementação: mixins, NBT, motor de pesca)
```

## 1. Dependência

O artefato é publicado pelo módulo `api/` deste repositório.

**Maven local** (o caminho garantido hoje):

```bash
./gradlew :api:publishToMavenLocal
```

```kotlin
// build.gradle.kts do plugin
repositories {
    mavenLocal()
}
dependencies {
    compileOnly("com.akashic.kunftig:kunftig-api:<versão>")
}
```

**Arquivo**: o CI publica `kunftig-api-<versão>.jar` nos artefatos de cada build; `compileOnly(files("libs/..."))`
também serve.

Regras:

* Sempre **`compileOnly`**, nunca shade/relocate: as classes precisam ser as do mod, ou os eventos e providers não
  se encontram.
* A versão vem da tag git do Kunftig (sem tag, defina `VERSION=...` ao publicar). Para versões estáveis, crie tags.
* O POM não declara dependências; o plugin traz sua própria API Bukkit (a do 1.7.10, ou spigot-api 1.8.8 com
  cuidado para não usar nada mais novo).

## 2. Verificar no `onEnable`

```java
@Override
public void onEnable() {
    try {
        Class.forName("com.akashic.kunftig.api.KunftigApi");
    } catch (ClassNotFoundException e) {
        getLogger().severe("O mod Kunftig não está instalado em mods/.");
        getServer().getPluginManager().disablePlugin(this);
        return;
    }
    if (KunftigApi.apiVersion() < 2 || !KunftigApi.isFishingAvailable() || !KunftigApi.isItemDataAvailable()
        || !KunftigApi.isParticlesAvailable() || !KunftigApi.isPlayersAvailable()) {
        getLogger().severe("Kunftig sem os recursos necessários (versão da API " + KunftigApi.apiVersion() + ").");
        getServer().getPluginManager().disablePlugin(this);
    }
}
```

`apiVersion()` é um método (não uma constante), então reflete o Kunftig instalado, não o que foi usado para compilar.

## 3. O que a API oferece

| Pacote | Conteúdo | Equivalente moderno | Versão da API |
|---|---|---|---|
| `com.akashic.kunftig.api` | `KunftigApi`: versão e capacidades | — | 1 |
| `...api.bukkit.fishing` | `KunftigFishEvent` (`LURED`, `BITE`, `FAILED_ATTEMPT`, `REEL_IN`), `ModernFishHook`, `KunftigFishingBukkit` | `PlayerFishEvent` e `FishHook` do 1.21.1 | 1 |
| `...api.bukkit.persistence` | `PersistentDataContainer`, `PersistentDataType`, `NamespacedKey`, `KunftigItems` | `org.bukkit.persistence` (1.14+) e `NamespacedKey` (1.12+) | 1 |
| `...api.particle`, `...api.bukkit.particle` | `KunftigParticle`, `KunftigParticles`, `ParticleBuilder` | `World#spawnParticle`, `Particle` e o `ParticleBuilder` do Paper | 2 |
| `...api.bukkit.player` | `KunftigPlayers.getPing` | `Player#getPing` (1.17+) | 2 |
| `...api.bukkit.fishing` | `ModernFishHook#retrieve`, pesca na lava (`setLavaFishing`) | `FishHook#retrieve` do Paper | 3 |

Pesca: guia completo em [fishing/API.md](fishing/API.md).

## 4. Dados persistentes em itens

Para marcar varas, iscas e peixes (espécie, peso, raridade, quem pescou) sem analisar lore e sem NMS. Mesmos nomes
do Bukkit 1.14+: portar código moderno é trocar os `import`.

```java
NamespacedKey species = new NamespacedKey(plugin, "species");
NamespacedKey weight = new NamespacedKey(plugin, "weight");

ItemStack fish = KunftigItems.asDataCapable(new ItemStack(Material.RAW_FISH));
PersistentDataContainer data = KunftigItems.getData(fish);
data.set(species, PersistentDataType.STRING, "tucunare");
data.set(weight, PersistentDataType.DOUBLE, 3.7);

// mais tarde, em qualquer lugar (inventário, evento, baú...)
String kind = KunftigItems.getData(item).get(species, PersistentDataType.STRING);   // null se não houver
```

Como funciona e o que saber:

* **Só pilhas "do servidor" guardam dados.** As que vêm de inventários, eventos e entidades já são; `new ItemStack`
  não é, e `KunftigItems.asDataCapable(item)` devolve uma cópia que é. `KunftigItems.canHoldData(item)` diz qual é o
  caso.
* **O contêiner é vivo**: cada `set`/`remove` grava no item na hora. No Crucible, `Inventory#getItem`,
  `getItemInHand` e `Item#getItemStack` devolvem espelhos do item real, então a alteração vale direto para o slot ou
  para a entidade (verificado em `CraftInventory`, `CraftInventoryPlayer` e `CraftItem`). Por isso, no
  `PlayerFishEvent` `CAUGHT_FISH` dá para marcar o peixe antes de ele voar até o jogador:

  ```java
  Item caught = (Item) event.getCaught();
  KunftigItems.getData(caught.getItemStack()).set(weight, PersistentDataType.DOUBLE, rollWeight());
  ```
* **Formato igual ao do 1.14+** (`PublicBukkitValues` → `"plugin:chave"`), então os itens mantêm o layout em
  servidores modernos.
* **Sobrevive** a `setItemMeta`, a cópias e ao empilhamento normal. **Exceção herdada do Cauldron**: um `ItemMeta`
  *vazio* (sem nome, lore nem encantamento) apaga todo o NBT do item. Use `KunftigItems.setItemMeta(item, meta)`, que
  preserva os dados nesse caso.
* Remover o último valor limpa o NBT por completo, para o item voltar a empilhar com itens comuns.
* Tipos: `BYTE`, `SHORT`, `INTEGER`, `LONG`, `FLOAT`, `DOUBLE`, `STRING`, `BYTE_ARRAY`, `INTEGER_ARRAY`, `BOOLEAN`,
  `TAG_CONTAINER`, `TAG_CONTAINER_ARRAY`, e tipos próprios implementando `PersistentDataType<P, C>` (por exemplo
  UUID sobre `BYTE_ARRAY`). `LONG_ARRAY` não existe: o NBT do 1.7.10 não tem esse tipo (veio no 1.12).
* Arrays e contêineres aninhados são **cópias** (como no Bukkit moderno): para alterar um aninhado, faça `set` de novo.
* Thread principal.

## 5. Compatibilidade

Todo o `kunftig-api` é verificado contra a API Bukkit 1.7.10 real do Thermos/Crucible
(`./gradlew checkLegacyBukkitApi -PlegacyBukkitApi=...`, jar gerado por `scripts/build-legacy-bukkit-api.sh`).

## 6. Reflexão em classes Bukkit no Crucible: use `MethodHandles`

No Crucible de produção (`1.7.10-staging-67b53034`), `org.bukkit.World` declara `mt getWorldServer()`: `mt` é o nome
ofuscado do `WorldServer` e não existe em tempo de execução. O código-fonte do Crucible está certo
(`patches/org/bukkit/World.java.patch` declara `net.minecraft.world.WorldServer`); o defeito está no jar publicado.

| Chamada | Resultado em `World`/`CraftWorld` |
|---|---|
| `world.getName()`, chamada direta de qualquer outro método | funciona: a JVM só resolve o método chamado |
| `world.getWorldServer()` (chamada direta) | **`NoSuchMethodError`** (verificado em produção): o remapeador de plugins do Crucible troca o `mt` da chamada por `WorldServer`, mas a interface continua declarando `()Lmt;` |
| `World.class.getMethods()`, `getDeclaredMethods()` | `NoClassDefFoundError: mt` |
| `World.class.getMethod("getName")` | **também** `NoClassDefFoundError: mt`: o Java resolve as assinaturas de todos os métodos públicos para achar um |
| `MethodHandles.publicLookup().findVirtual(World.class, "getName", MethodType.methodType(String.class))` | funciona: resolve só o método pedido |
| `findVirtual(World.class, "getWorldServer", methodType(WorldServer.class))` | `NoSuchMethodException`: na interface o descritor é `()Lmt;` |
| `findVirtual(world.getClass(), "getWorldServer", methodType(WorldServer.class))` | **funciona**: o `CraftWorld` é remapeado ao carregar e tem o tipo certo. É o contorno até o Crucible corrigir |

**Correção provisória no Kunftig.** O jar do Crucible traz o Minecraft com nomes deobfuscados, então o FML pula a
deobfuscação em tempo de execução (`FMLDeobfTweaker` só registra o `DeobfuscationTransformer` fora de ambiente
deobfuscado) e nada renomeia o `mt`. Com o Kunftig em `mods/`, um mixin vazio em `org.bukkit.World` leva a classe até o
`CrucibleMixinPlugin`, que troca o descritor de `getWorldServer` de `()Lmt;` para `()Lnet/minecraft/world/WorldServer;`
enquanto ela carrega (`crucible.WorldDescriptorFix`). Com isso, a chamada direta, o `findVirtual` na interface e o
`getMethods` voltam a funcionar. Só age se o método tiver exatamente o descritor quebrado, se `WorldServer` existir e se
não existir classe `mt`; num Crucible corrigido, no Thermos ou no Paper não faz nada. No log:
`Kunftig: fixed org.bukkit.World#getWorldServer()...`. Desligar: `-Dkunftig.crucible.worldFix.disable=true`. Os 30
membros internos do `org.spigotmc` não são mexidos: são código do próprio servidor, e plugins não devem usá-los.

**Nunca derruba o servidor.** A correção vive numa config de mixin própria e opcional (`mixins.kunftig.crucible.json`,
`required: false`, fase PREINIT), e o mixin só é oferecido se o `World` ainda não tiver sido carregado. Coremods que
detectam o Bukkit com `Class.forName("org.bukkit.World")` (o PlayerContainerFix 2.0.0 faz isso) carregam a classe cedo.
Se isso acontecer antes de a correção estar pronta, ela é pulada com o aviso
`org.bukkit.World was loaded before mixins could apply...` e tudo segue como sem ela. O build `8a18b84` tinha o mixin na
config obrigatória e derrubava esses servidores: não usar.

Mesmo com a correção, para código que precisa rodar também em servidores **sem** o Kunftig continua valendo:

Regra para plugins e para o próprio Kunftig: **nada de `getMethod`/`getMethods`/`getDeclaredMethods` em classes
Bukkit ou CraftBukkit; use `MethodHandles`** (`findVirtual`, `findStatic`; `getDeclaredField` continua seguro porque só
lista campos). O Kunftig segue a regra em todo o código (`CraftBridge`, `ItemDataBridge`, `VanillaTeamOptions`), e um
teste reproduz o defeito com uma classe gerada para provar a diferença (`ObfuscatedSignatureReflectionTest`).

Como saber se o seu servidor tem o problema: o mod Kunftig lê, sem carregar nenhuma classe, os arquivos `.class` da
API Bukkit no jar do servidor ao iniciar e registra no log um aviso com cada membro afetado (ou uma linha dizendo que
está limpo). Para rodar à mão em qualquer jar:

```bash
java -cp mods/kunftig-<versão>.jar com.akashic.kunftig.diagnostics.ObfuscatedApiScanner crucible-server.jar
```

No jar de produção o scanner acha 31 membros: **`World#getWorldServer` é o único da API de plugins**; os outros 30 são
internos do Spigot (`org.spigotmc.ActivationRange`, `AntiXray`, `TrackingRange`, `VanillaCommandWrapper`), que plugins
não devem usar. O log de inicialização lista a API inteira e resume os internos por classe. O texto para reportar ao CrucibleMC está em [upstream/crucible-world-getworldserver.md](upstream/crucible-world-getworldserver.md).
Desligar a verificação: `-Dkunftig.diagnostics.disable=true`.

## 7. Partículas: para todos por perto, coloridas e modernas

O Crucible de produção só tem `Player#spawnParticle` (sem `World#spawnParticle`), e o `spawnParticle` dele manda
`flame` no lugar de `END_ROD`, `FALLING_DUST`, `DAMAGE_INDICATOR`... O `KunftigParticles` resolve os dois: manda para
todos no raio e entrega partículas modernas a quem pode desenhá-las. Estudo: [particles/ESTUDO.md](particles/ESTUDO.md).

```java
// rastro do peixe vindo até a boia (todos a até 16 blocos)
KunftigParticles.spawn(fishLocation, KunftigParticle.FISHING, 4, 0.1, 0, 0.1, 0.0);

// aura de raridade: END_ROD para quem tem Kunftig + Et Futurum, fagulhas para os demais
KunftigParticles.builder(KunftigParticle.END_ROD)
    .location(hook.getLocation())
    .count(6)
    .offset(0.25, 0.4, 0.25)
    .extra(0.01)
    .spawn();

// pó colorido (funciona em qualquer cliente 1.7.10)
KunftigParticles.builder(KunftigParticle.DUST).location(loc).color(0xFFAA00).count(12).offset(.3, .3, .3).spawn();

// só para um jogador (por exemplo, o efeito do minigame)
KunftigParticles.builder(KunftigParticle.ENCHANTED_HIT).location(loc).count(10).receivers(player).spawn();
```

* **Nomes do 1.21** (`FISHING`, `DUST`, `ENCHANTED_HIT`...). Para portar código do Crucible:
  `KunftigParticle.fromName(particle.name())` aceita os nomes antigos (`WATER_WAKE`, `REDSTONE`, `CRIT_MAGIC`...).
* **Mesma semântica do Bukkit**: `count` 0 é uma partícula com velocidade `offset * extra`; `count` &gt; 0 espalha com
  desvio `offset` e velocidade aleatória vezes `extra`. O `extra` padrão é **1**, como no Bukkit: use `extra(0)` para
  partículas paradas.
* **Raio padrão: 16 blocos**, o alcance do 1.7.10 (o servidor vanilla manda até 16 e o cliente descarta além de 16).
  `receivers(raio)` muda; `receivers(jogadores)` manda só para quem você escolher.
* **Modernas** (`KunftigParticle#isModern()`): desenhadas por clientes com o Kunftig e o Et Futurum Requiem; os demais
  veem o `fallback()`. `KunftigParticles.drawsModernParticles(player)` diz se aquele cliente tem o Kunftig (o Et
  Futurum não é visível ao servidor).
* **Cores**: `DUST`, `ENTITY_EFFECT`, `AMBIENT_ENTITY_EFFECT`, `FALLING_DUST` e as gotas. Em clientes sem o Kunftig
  cada partícula colorida é um pacote; prefira contagens moderadas.
* **Itens e blocos**: `ITEM` com `item(id, dano)`, `BLOCK`/`BLOCK_DUST` com `block(id, meta)` (ids numéricos, como no
  1.7.10).
* Thread principal. Desligar no cliente: `-Dkunftig.particles.disable=true`; no servidor a mesma propriedade faz todos
  receberem só vanilla.

## 8. Ping do jogador

```java
int ping = KunftigPlayers.getPing(player);   // ms, igual ao da tab e ao Player#getPing do 1.17+
hook.setBiteWindow(baseTicks + Math.min(ping / 50, 10));   // ex.: compensar a latência no minigame
```

É a média móvel do 1.7.10 (`(ping * 3 + amostra) / 4` a cada keep-alive), então reage a mudanças em alguns segundos;
0 logo depois de entrar.


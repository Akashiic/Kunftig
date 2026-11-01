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

## 6. `org.bukkit.World` carregado cedo demais: use `MethodHandles`

No TEOF (Crucible `1.7.10-staging-67b53034` com o PlayerContainerFix 2.0.0), o `org.bukkit.World` carregado declara
`mt getWorldServer()`. `mt` é o nome ofuscado do `WorldServer`, e ninguém o renomeia.

**Causa** (verificada no TEOF, issue #3). O jar do Crucible guarda todas as classes com nomes notch (o Minecraft vem do
jar vanilla ofuscado no `Class-Path`) e o FML as renomeia enquanto carregam, pelo `DeobfuscationTransformer`. O FML
registra esse transformer por último (`FMLDeobfTweaker`, depois de injetar os coremods). O coremod do
**PlayerContainerFix 2.0.0** detecta o Bukkit com `Class.forName("org.bukkit.World")` durante a injeção. Com isso, o
`World` carrega antes da deobfuscação e fica com o `mt` para sempre. Sem o PlayerContainerFix, os testes abaixo passam
todos. Não é defeito do build do Crucible.

| Chamada (com o `World` carregado cedo) | Resultado |
|---|---|
| `world.getName()`, chamada direta de qualquer outro método | funciona: a JVM só resolve o método chamado |
| `world.getWorldServer()` (chamada direta) | `NoSuchMethodError`: o plugin é remapeado para `WorldServer`, a interface ficou com `()Lmt;` |
| `World.class.getMethods()`, `getDeclaredMethods()` | `NoClassDefFoundError: mt` |
| `World.class.getMethod("getName")` | **também** `NoClassDefFoundError: mt`: o Java resolve as assinaturas de todos os métodos públicos para achar um |
| `MethodHandles.publicLookup().findVirtual(World.class, "getName", MethodType.methodType(String.class))` | funciona: resolve só o método pedido |
| `findVirtual(World.class, "getWorldServer", methodType(WorldServer.class))` | `NoSuchMethodException` |
| `findVirtual(world.getClass(), "getWorldServer", methodType(WorldServer.class))` | **funciona**: o `CraftWorld` carrega depois, já deobfuscado |

Regra para plugins e para o próprio Kunftig: **nada de `getMethod`/`getMethods`/`getDeclaredMethods` em classes
Bukkit ou CraftBukkit; use `MethodHandles`** (`findVirtual`, `findStatic`; `getDeclaredField` continua seguro porque só
lista campos). Para `getWorldServer`, procure o método no `world.getClass()`, nunca na interface. O Kunftig segue a
regra em todo o código (`CraftBridge`, `ItemDataBridge`, `VanillaTeamOptions`), e um teste reproduz o defeito com uma
classe gerada (`ObfuscatedSignatureReflectionTest`).

No Crucible, o PlayerContainerFix nem é necessário: o Crucible já corrige o mesmo `ClassCastException` do
BetterQuesting no próprio `ContainerPlayer`. Recomendação: tirá-lo (veja "Mods que atrapalham no Crucible" no
[README.pt-BR.md](../README.pt-BR.md)).

O Kunftig **não** corrige isso em tempo de execução. As tentativas por mixin (`8a18b84`, `8c59b6c`) partiam de uma
premissa errada e não tinham como funcionar: o `World` já está carregado antes de qualquer mixin agir. **Não usar o
`8a18b84`**, que derrubava o servidor; o `8c59b6c` era inofensivo, mas não fazia nada. O conserto certo é no PlayerContainerFix: detectar o Bukkit sem carregar a classe.
Texto para reportar: [upstream/playercontainerfix-early-bukkit-load.md](upstream/playercontainerfix-early-bukkit-load.md).

**Como saber se o seu servidor tem o problema.** Na inicialização, o Kunftig confere o `World` carregado. Se as
assinaturas não resolverem, o log mostra `Kunftig: org.bukkit.World was loaded without FML's runtime
deobfuscation...`; se estiver tudo certo, não mostra nada. Desligar: `-Dkunftig.diagnostics.disable=true`.

O `ObfuscatedApiScanner` (`java -cp kunftig.jar com.akashic.kunftig.diagnostics.ObfuscatedApiScanner server.jar`) lê
os nomes **gravados no jar**. No Thermos/Crucible quase tudo aparece ali com nome notch e funciona normalmente em tempo
de execução, então use-o para inspecionar jars, não como diagnóstico.

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


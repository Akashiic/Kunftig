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
| `...api.bukkit.persistence` | `KunftigItems.setModel`/`getModel` (ícone do item por NBT) | `CustomModelData` (1.14+) e `item_model` (1.21.4+) | 4 |
| `...api.bukkit.fishing`, `...api.bukkit.player` | `ModernFishHook#setReelLocked`, `setCatchAimedAtAngler`; `KunftigPlayers.isHotbarSwitch` | — | 5 |
| `...api.bukkit.hologram` | `KunftigHolograms`, `Hologram` (texto flutuante no mundo) | `TextDisplay` (1.19.4+) | 6 |
| `...api.bukkit.rod` | `KunftigRods` (estado da vara 3D: mordida, briga com tensão, captura...) | — | 7 |

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

## 9. Ícone próprio para itens de plugin (o `CustomModelData` do 1.7.10)

No 1.7.10 o ícone de um item é sempre o do item base. Com o Kunftig no cliente, o servidor escolhe o ícone por um id
gravado no item, como o `CustomModelData` do 1.14+ (aqui com um id, como o `item_model` do 1.21.4+). O NecroTempus não
tem esse recurso.

**No plugin:**

```java
ItemStack fish = KunftigItems.asDataCapable(new ItemStack(Material.RAW_FISH));
KunftigItems.setModel(fish, new NamespacedKey(plugin, "tucunare"));   // akashicfishing:tucunare
KunftigItems.getModel(fish);                                          // akashicfishing:tucunare
KunftigItems.setModel(fish, null);                                    // volta ao ícone do peixe
```

O id fica em `PublicBukkitValues."kunftig:model"`, como qualquer dado persistente, e viaja com o NBT do item. Valem as
regras da seção 4 (pilha do servidor, `asDataCapable`). Requer API nível 4.

**No resource pack** (o mesmo que o cliente já usa, por exemplo o dos glyphs do NecroTempus), um índice por namespace:

```
assets/akashicfishing/kunftig/item_models.json
assets/akashicfishing/textures/items/fish/tucunare.png
```

```json
{
  "tucunare": "akashicfishing:fish/tucunare",
  "bota_velha": "junk/boot"
}
```

A chave é o id do modelo dentro do namespace (`akashicfishing:tucunare`). O valor é o nome do ícone, como no
`setTextureName` do 1.7.10: `akashicfishing:fish/tucunare` aponta para `textures/items/fish/tucunare.png`, e sem
namespace vale o da pasta. Animações com `.mcmeta` funcionam como em qualquer ícone. Packs carregados depois sobrescrevem
os anteriores, e o resource pack enviado pelo servidor também conta. Erros no arquivo aparecem no log do cliente
(`Kunftig item models: ...`) sem derrubar o resto.

**Onde aparece:** inventário e qualquer GUI, mão em primeira e terceira pessoa (inclusive de outros jogadores), item no
chão e em moldura. O Kunftig troca o ícone em `ItemStack#getIconIndex` e `Item#getIcon(ItemStack, int)`, os dois pontos
por onde esses caminhos passam no 1.7.10 (conferido no bytecode por testes). O Angelica mexe no brilho, no
instanciamento e no cache do HUD, mas usa o ícone escolhido por esses métodos.

**Limites:**

* Só itens com ícone simples. Blocos (`ItemBlock`) usam o atlas de blocos. Itens de várias camadas (poções, armadura de
  couro, ovos) pintam cada camada com uma cor. Nesses casos fica o ícone original. Para itens de plugin, use uma base
  simples (peixe, papel, pedra de fogo...).
* Itens que sobrescrevem `getIcon` ou têm um `IItemRenderer` próprio mantêm o desenho deles. A vara lançada e o arco
  puxado também mantêm os ícones vanilla.
* Cliente sem o Kunftig, ou sem o ícone no pack: aparece o ícone do item base, como hoje. Nada quebra.
* Desligar no cliente: `-Dkunftig.itemModels.disable=true`.

## 10. Backhand: `PlayerItemHeldEvent` falso a cada clique direito

Com o Backhand (mão secundária) no TEOF, **todo clique direito** gera no servidor dois `PlayerItemHeldEvent`, de `0 → 36`
e de `36 → 0`. O cliente troca para o slot da mão secundária (36) e volta, para tentar usar o item dela, e o Backhand
faz o servidor aceitar o slot 36 (`MixinNetHandlerPlayServer`, `isValidSwitch`). O Crucible então dispara o evento para
cada troca. Plugins que tratam o evento como "o jogador trocou de item" são enganados: minigames que acabam na troca,
bloqueios por item na mão, cooldowns, HUD de item.

```java
@EventHandler
public void onHeld(PlayerItemHeldEvent event) {
    if (!KunftigPlayers.isHotbarSwitch(event)) {
        return; // ida e volta do Backhand (ou slot fora da hotbar): não é troca
    }
    ...
}
```

`isHotbarSwitch` só é verdadeiro para uma troca real entre slots 0 a 8, e funciona sem o mod (é só Bukkit). Para saber
se o **item** na mão mudou, o mais seguro é comparar o item na mão um tick depois. Em minigames de pesca, prefira
`ModernFishHook#setReelLocked`, que não depende de cliques nem de trocas de slot.

A origem é do lado do Backhand: o servidor poderia não repassar ao Bukkit a ida ao slot da mão secundária. Isso fica
fora do escopo do Kunftig.

## 11. Hologramas: texto flutuante no mundo (API 6)

O 1.7.10 não tem como desenhar texto no mundo sem uma entidade (os hologramas antigos usavam cavalos minúsculos
montados em caveiras de wither, presos a pacotes e ao NMS). O Kunftig manda o texto num canal próprio
(`kunftig:holo`) e o cliente com Kunftig desenha cada linha como uma nametag: virada para a câmera, fundo translúcido,
fraca através dos blocos e firme na frente deles. Cores com `§` e os glyphs do NecroTempus funcionam (é a fonte do
jogo).

```java
if (KunftigApi.apiVersion() >= 6 && KunftigApi.isHologramsAvailable()) {
    Hologram h = KunftigHolograms.create(location, Arrays.asList("§6§lAmuleto da Sorte", "§a+25% de sorte"));
    h.setLines(...);       // quem vê recebe o texto novo na hora
    h.teleport(outro);     // mesma coisa para a posição (até de mundo)
    h.remove();            // some para todos
}
```

* `location` é o centro da linha de cima; as outras descem 0,27 bloco cada. Até 16 linhas de até 256 caracteres.
* O servidor manda cada holograma aos jogadores do mesmo mundo dentro do alcance (32 blocos por padrão, 1 a 128 com
  `create(location, linhas, alcance)`) e o tira de quem se afasta (com 4 blocos de folga, para não piscar na borda),
  sai do mundo ou do servidor. Quem reconecta ou renasce recebe tudo de novo.
* Só jogadores com o Kunftig no cliente veem (`KunftigHolograms.drawsHolograms(player)`); os outros não recebem nada.
* Os hologramas **não sobrevivem** a um reinício: o plugin cria de novo no `onEnable` os que devem ficar.
* Thread principal. `-Dkunftig.holograms.disable=true` desliga tudo (servidor e cliente).

## 12. Varas 3D: o estado da vara para quem vê (API 7)

Clientes com Kunftig **e** GeckoLib (o do TEOF, `geckolib-unofficial-1.7.10-1.0.4`) desenham em 3D as varas que o
resource pack declara em `assets/<namespace>/kunftig/item_models_3d.json`, animadas, na mão (primeira e terceira
pessoa), no chão e na moldura (o pack escolhe onde, `contextos`), com a linha saindo da ponta e os braços acompanhando
(as duas mãos na vara quando o estado pede e a mão esquerda está livre). O item é o mesmo do ícone 2D
(seção 9): `KunftigItems.setModel(item, new NamespacedKey(plugin, "vara/vara-solar.ouro.m3"))` usa o modelo 3D
`vara/vara-solar`, qualidade `ouro`, estágio de mutação 3. No inventário fica o ícone 2D. Formato do manifesto,
detalhes e testes: [rodmodels/README.md](rodmodels/README.md).

O cliente sabe sozinho quando a vara vai para a mão (`sacar`), quando a linha sai (`lancar`) e quando volta
(`parada`). O resto só o servidor sabe:

```java
if (KunftigApi.apiVersion() >= 7 && KunftigApi.isRodsAvailable()) {
    KunftigRods.setState(player, KunftigRods.BITE);    // no KunftigFishEvent BITE
    KunftigRods.setState(player, KunftigRods.FIGHT);   // o minigame começou...
    KunftigRods.setTension(player, tensao);            // ...a cada tick dele, de 0 a 1 (a vara verga)
    KunftigRods.setState(player, KunftigRods.CATCH);   // venceu; ou IDLE se o peixe escapou
}
```

* Estados: `IDLE` (`parada`), `BITE` (`mordida`), `FIGHT` (`briga`), `REEL` (`recolher`), `CATCH` (`captura`), ou
  qualquer outro nome que o pack declare em `estados` (`a-z`, `0-9`, `_`, até 32). Os que não são laço tocam uma vez e
  voltam a `parada`; o mesmo estado de novo recomeça (uma segunda mordida). Vale o evento mais recente: do servidor
  ou do próprio cliente (trocar de item, lançar, a linha voltar).
* O jogador e quem estiver a até 96 blocos no mesmo mundo, com Kunftig, recebem; quem chega depois recebe o estado já
  andando (no ponto certo da animação). A tensão vai no máximo uma vez por tick, em centésimos, só quando muda: pode
  chamar `setTension` em todo tick do minigame.
* Canal `kunftig:rod` (mensagem crua, como `kunftig:holo`). Sem Kunftig, sem GeckoLib ou sem o pack, o jogador vê a
  vara 2D de sempre e nada quebra.
* Thread principal. `KunftigRods.drawsRodModels(player)` diz se o cliente desenha varas 3D.
  `-Dkunftig.rodModels.disable=true` no cliente desliga as varas 3D (ícone 2D).
* Em primeira pessoa, a vara tem inércia (`balanco` no manifesto): fica para trás ao girar a câmera, andar de lado ou
  pular, e volta com um pequeno balanço. `-Dkunftig.rodSway.disable=true` no cliente desliga só isso.

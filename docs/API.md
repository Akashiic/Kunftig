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
| `...api.bukkit.display` | `KunftigDisplays`, `Display` (modelo GeckoLib animado no mundo, com itens nos ossos) | `ItemDisplay`/`BlockDisplay` (1.19.4+) | 8 |
| `...api.bukkit.input` | `KunftigInput`, `KunftigInputEvent` (teclas próprias do cliente) | — | 9 |
| `...api.bukkit.display` | `Display#moveTo` (deslizar), `setMotion` (voar sozinho), `setRotation` (pitch e roll) | interpolação dos display entities | 9 |
| `...api.bukkit.hud` | `KunftigHud`, `HudElement` (texto, barra e imagem na tela) | — | 9 |
| `...api.bukkit.camera` | `KunftigCamera` (travar a visão num alvo) | — | 9 |
| resource pack | `ao_atacar` nos estados de `item_models_3d.json` (previsão do golpe no cliente) | — | 9 |
| `...api.bukkit.blade` | `KunftigBlades`, `BladeMotion` (lâminas no estilo SlashBlade: OBJ + VMD), `KunftigEffects` (corte, Judgement Cut, drive, espadas invocadas, lock-on, rank) | — | 9 |

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

**Célula do slot** (`celula`): numa GUI de baú, um item pode cobrir o slot inteiro, 18x18, em vez dos 16x16 de
sempre. Isso cobre também o 1 pixel de borda em volta, e os itens de slots vizinhos se juntam numa figura só, sem as
linhas entre eles. O botão APRIMORAR do AkashicFishing, sobre cinco slots, usa isso. No pack, uma entrada irmã com o
sufixo `.celula`:

```json
{
  "tela/botao-pronto-0": "akashicfishing:gui/botao-pronto-0",
  "tela/botao-pronto-0.celula": "akashicfishing:gui/botao-pronto-0_celula"
}
```

* A textura da célula é 32x32, com a célula nos 18x18 do canto de cima à esquerda. A potência de dois mantém os
  mipmaps do atlas de itens.
* Também vale a forma de objeto, `{"icone": "...", "celula": "..."}`. Prefira a entrada irmã: um Kunftig sem células
  a lê como mais um modelo, que nenhum item usa, e o pack continua funcionando nele.
* Só os slots de container usam a célula (o `GuiContainer`, por `RenderItem#renderItemAndEffectIntoGUI`). Barra de
  itens, mão e chão continuam com o ícone de 16x16.
* Desligar: `-Dkunftig.slotCells.disable=true`.

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
(as duas mãos na vara quando o estado pede, por exemplo só com a linha na água, e a mão esquerda está livre). O item
é o mesmo do ícone 2D (seção 9): `KunftigItems.setModel(item, new NamespacedKey(plugin, "vara/vara-solar.ouro.m3"))`
usa o modelo 3D `vara/vara-solar`, qualidade `ouro`, estágio de mutação 3. No inventário fica o ícone 2D. Formato do manifesto,
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

## 13. Exibições: modelos 3D animados no mundo (API 8)

Uma exibição é um modelo Bedrock (GeckoLib) que o plugin põe num ponto do mundo, com uma animação (o estado) e até
8 itens desenhados nos ossos que o modelo indica (o item gira e se move com o osso). Não é entidade: o servidor
guarda quem está perto e manda o que mudou; o cliente desenha. O AkashicFishing usa para a Caixa da Sorte.

O resource pack declara os modelos em `assets/<namespace>/kunftig/displays.json`:

```json
{
  "_formato": 1,
  "exibicoes": {
    "caixa_da_sorte": {
      "geo": "akashicfishing:geo/caixa/caixa_da_sorte.geo.json",
      "animacoes": "akashicfishing:animations/caixa/caixa_da_sorte.animation.json",
      "textura": "akashicfishing:textures/caixa/caixa_da_sorte_comum.png",
      "variantes": { "epico": "akashicfishing:textures/caixa/caixa_da_sorte_epico.png" },
      "estados": { "abrir": { "animacao": "animation.caixa_da_sorte.abrir", "laco": false } },
      "itens": ["item_0", "item_1", "item_2", "item_3"],
      "escala_item": 1.0,
      "luz": ["brilho", "feixe"],
      "translucido": ["brilho", "feixe", "bolha_0"],
      "distancia": 48
    }
  }
}
```

* `variantes`: texturas alternativas, escolhidas por nome no `spawn` (sem variante, ou desconhecida: `textura`).
* `estados`: o nome que o plugin usa e a animação; sem `laco`, a animação para no último quadro.
* `itens`: os ossos onde os itens vão, na ordem da lista do plugin. `luz`: ossos sem sombra (brilham no escuro).
  `translucido`: ossos desenhados depois, sem escrever profundidade (água, brilho). `distancia`: além disso (blocos
  da câmera), o cliente não desenha.

```java
if (KunftigApi.apiVersion() >= 8 && KunftigApi.isDisplaysAvailable()) {
    Location at = boia.clone();
    at.setYaw(yawParaOJogador);                    // a frente (+Z) do modelo olha para lá
    Display d = KunftigDisplays.spawn(at, "akashicfishing:caixa_da_sorte", "epico", "abrir",
        Arrays.asList(itemGanho), 48);             // até 8 itens; 48: quem recebe (1 a 128 blocos)
    // ... d.setState("outro"), d.setItems(...), d.teleport(...)
    d.remove();                                    // no fim: o plugin remove (a exibição não some sozinha)
}
```

* Quem está a até `distancia` blocos (o `spawn`) no mesmo mundo, com Kunftig e GeckoLib, recebe; quem chega depois
  recebe a animação já andando, no ponto certo. Canal `kunftig:display` (mensagem crua); os itens vão como NBT.
* `KunftigDisplays.drawsDisplays(player)` diz se o cliente desenha exibições (Kunftig com GeckoLib). Para os outros,
  o plugin desenha o que quiser no lugar (o AkashicFishing desenha a caixa em partículas).
* Thread principal. Isole as chamadas numa classe só, chamada depois de conferir `apiVersion() >= 8`: um Kunftig
  mais antigo não tem essas classes.
* `-Dkunftig.displays.disable=true` no cliente desliga as exibições (o cliente nem registra o canal).


### Movimento das exibições (API 9)

Para projéteis e efeitos que andam (uma onda de corte, espadas invocadas):

```java
Display drive = KunftigDisplays.spawn(origem, "overkill:drive", "", "voar", Collections.emptyList(), 64);
drive.setRotation(yaw, 0, 45);                         // yaw, pitch (frente para baixo), roll (em volta da frente)
drive.setMotion(direcao.multiply(1.2));                 // blocos por tick: voa sozinha, no servidor e nos clientes
// a cada tick o plugin confere a colisão em drive.getLocation() e chama drive.remove() quando acerta

Display espada = ...;
espada.moveTo(perto_do_alvo, 4);                        // desliza até lá em 4 ticks (para seguir um alvo)
```

* `setMotion`: uma mensagem para o voo inteiro; o servidor anda junto (`getLocation()` acompanha), então quem chega
  depois vê a exibição onde ela está, voando. `teleport`/`moveTo` param o voo; `setMotion(null)` também.
* `moveTo(local, ticks)`: em linha reta, girando junto (até 255 ticks; 0 é pulo). Outro mundo é sempre pulo.
* `setRotation`: o yaw gira em volta do eixo vertical, o pitch inclina a frente (+Z) para baixo, o roll gira em volta
  da frente. `teleport` mantém pitch e roll.
* Compatível: um cliente com Kunftig 8 lê o que conhece e ignora o resto (vê pulos, o modelo em pé e nada voando).

## 14. Teclas do cliente (API 9)

O 1.7.10 só conta ao servidor os cliques. O Kunftig põe no cliente teclas próprias (Controles, categoria Kunftig):
**Especial** (R), **Habilidade** (F), **Esquiva** (Alt esquerdo) e **Travar alvo** (Z), e manda ao servidor as que um
plugin ligou para aquele jogador, apertadas e soltas, com as teclas de movimento seguradas e o tick do cliente.

```java
@Override
public void onEnable() {
    if (KunftigApi.apiVersion() >= 9 && KunftigApi.isInputAvailable()) {
        KunftigInput.register(this);     // o Bukkit só entrega mensagens a um plugin: este se empresta
    }
}

// quando a lâmina vai para a mão; KunftigInput.bind(player) (nenhuma) quando sai
KunftigInput.bind(player, KunftigInput.SPECIAL, KunftigInput.DODGE, KunftigInput.LOCK_ON);

@EventHandler
public void onInput(KunftigInputEvent e) {
    if (e.getAction().equals(KunftigInput.SPECIAL) && e.isPressed()) {
        boolean paraTras = e.isBack();          // "trás + especial" é outro golpe
        int tick = e.getClientTick();           // distância entre dois eventos em ticks do cliente
    }
}
```

* O cliente nunca é confiável: só passa a ação ligada para aquele jogador, com sequência mais nova que a última e
  dentro do limite (20 de uma vez, 20 por segundo em média). O resto é descartado em silêncio.
* `KunftigInput.isHeld(player, ação)` diz se a tecla está segurada (carregar um golpe). Ao desligar uma ação
  segurada, o cliente manda a soltura antes.
* Nada é enviado com uma tela aberta (o vanilla solta todas as teclas nesse momento, e o Kunftig também).
* `KunftigInput.sendsInput(player)` diz se o cliente tem o Kunftig 9; para os outros, ofereça outro caminho.
* Canal `kunftig:input`, nos dois sentidos (o servidor manda `BIND`, o cliente manda `ACTION`). Thread principal.
  `-Dkunftig.input.disable=true` no cliente desliga as teclas e o canal.

## 15. HUD na tela (API 9)

Elementos na tela de um jogador, desenhados depois do HUD do vanilla: texto (com `§` e quebras de linha), uma barra e
uma imagem PNG do resource pack, a partir de uma âncora da tela.

```java
KunftigHud.show(player, HudElement.builder("overkill:rank")
    .anchor(HudElement.Anchor.RIGHT).offset(-12, -30).scale(2)
    .texture("overkill:textures/hud/rank_s.png", 32, 32)
    .text("§6§lSTYLISH")
    .bar(0.65f, 64, 0xFFFFAA00, 0x80000000)
    .duration(100, 20)          // 5 s na tela, o último segundo sumindo
    .build());
KunftigHud.remove(player, "overkill:rank");
```

* De cima para baixo a partir da âncora: imagem, linhas de texto, barra. Posições em pixels da GUI (resolução
  escalada, como o HUD do vanilla), vezes a escala.
* Mostrar de novo com o mesmo id substitui. Um elemento sem duração e igual ao que está na tela não é reenviado: pode
  chamar a cada tick. Não sobrevive a uma reconexão. Até 32 por jogador.
* Esconde com F1 junto com o resto do HUD. `KunftigHud.drawsHud(player)` diz se o cliente desenha.
* Canal `kunftig:hud`. Thread principal. `-Dkunftig.screen.disable=true` no cliente desliga HUD e trava de câmera.

## 16. Trava de câmera (API 9)

```java
KunftigCamera.lockOn(player, alvo, 0.35f, 24, true);   // força por tick, distância máxima, marcador
KunftigCamera.release(player);
```

* A cada quadro o cliente gira a visão parte do caminho até o alvo (a mesma velocidade em qualquer FPS); o jogador
  ainda mexe o mouse contra. A visão que o servidor recebe acompanha, então mirar pelo olhar do jogador acerta o alvo.
* O marcador é um losango girando sobre a cabeça do alvo, visível através das paredes.
* O cliente solta sozinho quando o alvo some, morre ou passa da distância. O plugin não é avisado: confira o mesmo no
  servidor e chame `release` ou escolha outro alvo.
* Canal `kunftig:camera`. Thread principal.

## 17. Previsão do golpe nos modelos na mão (API 9)

No `item_models_3d.json`, um estado pode dizer qual estado um ataque (clique esquerdo) começa durante ele:

```json
"estados": {
  "parada":  { "modelo": "animation.katana.parada", "laco": true, "ao_atacar": "golpe_1" },
  "sacar":   { "modelo": "animation.katana.sacar", "ao_atacar": "iaido" },
  "golpe_1": { "modelo": "animation.katana.golpe_1", "ao_atacar": { "estado": "golpe_2", "de": 4, "ate": 14 } },
  "golpe_2": { "modelo": "animation.katana.golpe_2", "ao_atacar": { "estado": "golpe_3", "de": 4 } },
  "golpe_3": { "modelo": "animation.katana.golpe_3" }
}
```

* O balanço do braço do jogador local (o mesmo que manda ao servidor o `PlayerAnimationEvent`) começa o estado na
  hora, se o ataque cai na janela (`de` a `ate` ticks depois do início do estado atual, inclusive; sem `ate`, até o
  fim).
* Quando o servidor manda o mesmo estado (`KunftigRods.setState`) em até 20 ticks, é o mesmo golpe confirmado: a
  animação continua, não recomeça. Qualquer outro estado do servidor vale, então ele corrige o que não aceitou.
* O plugin deve usar as **mesmas janelas** no servidor, contadas a partir do tick em que mandou o estado. Uma
  diferença só aparece como uma correção visual.

## 18. Lâminas e efeitos no estilo SlashBlade (API 9)

O cliente com Kunftig desenha lâminas como o SlashBlade: o modelo OBJ da lâmina preso à armação invisível
(`bladeholder`) movida pelo VMD dos estados de combo, o corpo do jogador movido pelo VMD do jogador, e os efeitos do
SlashBlade portados do código (MIT) do SlashBlade: Resharped, passada por passada. O resource pack declara tudo em
`assets/<namespace>/kunftig/blades.json`:

```json
{
  "_formato": 1,
  "movimento_lamina": "combostate/motion.vmd",
  "movimento_jogador": "model/pa/player_motion.vmd",
  "laminas": {
    "katana": { "obj": "model/katana.obj", "textura": "model/katana.png", "porte": "DEFAULT",
                "itens": ["minecraft:golden_sword"] }
  },
  "efeitos": {
    "corte":      { "obj": "model/util/slash.obj",    "textura": "model/util/slash.png" },
    "julgamento": { "obj": "model/util/slashdim.obj", "textura": "model/util/slashdim.png" },
    "onda":       { "obj": "model/util/drive.obj",    "textura": "model/util/ss.png" },
    "espada":     { "obj": "model/util/ss.obj",       "textura": "model/util/ss.png" },
    "mira":       { "obj": "model/util/lockon.obj",   "textura": "model/util/lockon.png" },
    "rank":       { "textura": "textures/gui/rank.png" }
  }
}
```

Os grupos que cada modelo precisa são os do SlashBlade: `base` (corte, onda), `base` e `wind` (julgamento), `ss`
(espada), `lockonBase`, `lockonHealthMask` e `lockonHealth` (mira); o `rank` é a textura 256x256 no layout do
`rank.png` do SlashBlade. **A arte do SlashBlade é "todos os direitos reservados": um pack distribuído usa modelos e
texturas próprios** com os mesmos grupos.

```java
// a lâmina: a cadeia de estados de combo (quadros do VMD da lâmina) e o trecho do corpo
KunftigBlades.play(jogador, BladeMotion.builder()
    .state(1, 10, false, BladeMotion.duration(1, 10, 1, 0))
    .state(10, 21, false, BladeMotion.duration(10, 21, 1, 0))
    .body(1, 41, true)
    .build());

// efeitos de uma vez (a até 64 blocos)
KunftigEffects.slash("overkill", local, yaw, -10f, 0x3333FF, nivelDoRank, 1f);
KunftigEffects.judgementCut("overkill", olhosDoAlvo, 0x3333FF);

// projéteis: voam sozinhos no cliente (posição += velocidade, velocidade *= 0,99, sem gravidade)
KunftigEffects.Projectile espada = KunftigEffects.summonedSword("overkill", local, direcao.multiply(3), rollAleatorio, cor);
espada.stick(alvo, offsetYaw);      // cravada: segue o meio do alvo e gira com ele
espada.remove(true);                // estilhaça (crits); o som é do plugin
KunftigEffects.drive("overkill", local, olhar.multiply(2), 0f, cor, 10);

// só para o dono
KunftigEffects.lockOn(jogador, "overkill", alvo, cor);   // null: solta
KunftigEffects.rank(jogador, "overkill", pontos);        // a cada mudança (0..1799)
```

* **Corte**: o arco gira 135 graus e some em 10 ticks; as passadas dependem do nível do rank (0 nenhum, 1 D ... 5 S,
  5,25 SS, 5,5 SSS): cinza abaixo de C, a passada branca de fora a partir de B, a escura de dentro a partir de S, crits
  em SS e SSS.
* **Judgement Cut**: a bolha que escurece o que está atrás (cor complementar), três cascas pulsando e cinco ventos na
  cor girando, 10 ticks, crits no fim. O crítico do SlashBlade são três cortes extras (`baseSize` 0,5, roll 30, yaws a
  120 graus um do outro) que o plugin manda com `slash`.
* **Projéteis**: o cliente anda com eles tick a tick como o SlashBlade (o drive termina no primeiro bloco, a espada
  crava 0,05 antes dele); o plugin que anda com a sua cópia igual só manda o que muda (`move`, `stick`, `remove`). Para
  espadas paradas ou girando (órbitas), `move(local, velocidadeZero, yaw, pitch)` a cada tick, com o yaw na convenção
  do SlashBlade (`atan2(x, z)` da direção da ponta, não o yaw do Bukkit). Os jogadores que estavam perto quando o
  projétil nasceu recebem as mensagens seguintes.
* **Lock-on**: o cliente só mostra enquanto o jogador segura uma lâmina e o Sneak: a mira entre o alvo e a câmera, com
  o anel de vida, e a visão puxada para os pés do alvo mais metade da altura dos olhos (`0,125 * partialTicks` do
  caminho por quadro, como o SlashBlade). O alvo é escolhido pelo plugin ao apertar o Sneak.
* **Rank**: o cliente deixa os pontos caírem 1 por tick até o piso do nível (S, SS e SSS voltam a S) e mostra o HUD por
  6 segundos depois de cada mudança, com o texto do rank por 1 segundo depois de subir.
* Canais `kunftig:blade` e `kunftig:effect`. Thread principal. `KunftigApi.isBladesAvailable()` diz se o servidor tem.


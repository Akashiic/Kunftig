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
    if (KunftigApi.apiVersion() < 1 || !KunftigApi.isFishingAvailable() || !KunftigApi.isItemDataAvailable()) {
        getLogger().severe("Kunftig sem os recursos necessários (versão da API " + KunftigApi.apiVersion() + ").");
        getServer().getPluginManager().disablePlugin(this);
    }
}
```

`apiVersion()` é um método (não uma constante), então reflete o Kunftig instalado, não o que foi usado para compilar.

## 3. O que a API oferece (versão 1)

| Pacote | Conteúdo | Equivalente moderno |
|---|---|---|
| `com.akashic.kunftig.api` | `KunftigApi`: versão e capacidades | — |
| `...api.bukkit.fishing` | `KunftigFishEvent` (`LURED`, `BITE`, `FAILED_ATTEMPT`, `REEL_IN`), `ModernFishHook`, `KunftigFishingBukkit` | `PlayerFishEvent` e `FishHook` do 1.21.1 |
| `...api.bukkit.persistence` | `PersistentDataContainer`, `PersistentDataType`, `NamespacedKey`, `KunftigItems` | `org.bukkit.persistence` (1.14+) e `NamespacedKey` (1.12+) |

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
| `world.getName()`, qualquer chamada direta | funciona: a JVM só resolve o método chamado |
| `World.class.getMethods()`, `getDeclaredMethods()` | `NoClassDefFoundError: mt` |
| `World.class.getMethod("getName")` | **também** `NoClassDefFoundError: mt`: o Java resolve as assinaturas de todos os métodos públicos para achar um |
| `MethodHandles.publicLookup().findVirtual(World.class, "getName", MethodType.methodType(String.class))` | funciona: resolve só o método pedido |

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

Nas fontes do Crucible, `World#getWorldServer` é a única assinatura pública da API Bukkit que expõe um tipo do
Minecraft (os outros patches da API usam tipos do Minecraft/Forge só dentro de corpos de métodos, o que a reflexão não
resolve). O texto para reportar ao CrucibleMC está em [upstream/crucible-world-getworldserver.md](upstream/crucible-world-getworldserver.md).
Desligar a verificação: `-Dkunftig.diagnostics.disable=true`.

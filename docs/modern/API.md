# Primitivas modernas para mods (`com.akashic.kunftig.modern`)

Peças das APIs do Minecraft atual (1.16 a 1.21) reescritas para 1.7.10, para **portar mods modernos** sem reescrever
toda a lógica: o código de origem continua com o mesmo formato, só trocam os imports. São para **mods Forge** (não
para plugins Bukkit) e não têm protocolo de rede próprio. O primeiro consumidor é o backport do Relics
(Akashiic/relics).

Tudo é Java 8 (Jabel), sem dependência além do Forge. Testado em `src/test/java/com/akashic/kunftig/modern`.

## 1. Codecs NBT (`modern.codec`)

O `Codec` do DataFixerUpper (1.16+) sobre NBT do 1.7.10: `INT`, `LONG`, `FLOAT`, `DOUBLE`, `BOOL`, `STRING`,
`list`, `unboundedMap` (chaves texto), `xmap`, `validate`, `intRange`, `fieldOf`, `optionalFieldOf` (com e sem
padrão) e `RecordCodecBuilder` (até 16 campos), com a mesma forma de chamada:

```java
public static final Codec<LevelingComponent> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Codec.INT.optionalFieldOf("rank", 0).forGetter(LevelingComponent::getRank),
        Codec.DOUBLE.optionalFieldOf("experience", 0D).forGetter(LevelingComponent::getExperience)
).apply(instance, LevelingComponent::new));
```

Regras:

* `decode` lança `CodecException` em dado malformado; `parse` devolve `Optional` vazio.
* `optionalFieldOf(nome, padrão)` não grava o valor quando ele é igual ao padrão, e um valor malformado volta como o
  padrão (o mesmo que `lenientOptionalFieldOf`): um item salvo por uma versão antiga não quebra.
* Mapas são compounds: a ordem lida é a do NBT (o 1.7.10 guarda compounds em `HashMap`).

## 2. Codecs de rede (`StreamCodec`, `ByteBufCodecs`)

O `StreamCodec` moderno: `of`, `map`, `dispatch`, `unit` e `composite` para registros de 1 a 17 campos;
`ByteBufCodecs` com `BOOL`, `INT`, `VAR_INT`, `LONG`, `FLOAT`, `DOUBLE`, `STRING_UTF8`, `COMPOUND_TAG`,
`ITEM_STACK`, `UUID`, `collection`, `list`, `map`, `optional`, `idMapper`, `enumCodec` e `fromCodec` (manda um valor
de `Codec` dentro de um compound). Servem para montar pacotes do canal do próprio mod.

## 3. Dados em itens (`modern.item.DataComponentType`)

Os *data components* do 1.20.5+: um valor tipado no NBT do item, em `<namespace>.<id>`.

```java
public static final DataComponentType<Integer> CHARGES = new DataComponentType<>("meumod", "charges", Codec.INT);

int charges = CHARGES.getOrDefault(stack, 0);
CHARGES.set(stack, charges - 1);
```

* Os valores devem ser imutáveis. O valor lido fica em cache por instância da tag (chave fraca, por identidade):
  ler de novo um item que não mudou não decodifica NBT; uma cópia do item é decodificada uma vez.
* Pilha vazia no 1.7.10 é `null`: todos os métodos aceitam `null`.
* `remove` apaga também o compound do namespace e a tag do item quando ficam vazios.

## 4. Dados em entidades (`modern.entity`)

Os *data attachments* do NeoForge, sobre `IExtendedEntityProperties`:

```java
public static final AttachmentType<Research> RESEARCH = AttachmentType.builder("meumod:research", () -> Research.EMPTY)
        .serialize(Research.CODEC)   // sem codec: só em memória
        .copyOnDeath()               // vai para o jogador que renasce
        .build();                    // registra; faça no pre-init

Research data = RESEARCH.get(player);
RESEARCH.set(player, data.with(...));
```

* Jogadores recebem o contêiner ao serem construídos (os dados salvos são lidos com eles); outras entidades, no
  primeiro uso.
* Dados salvos de um tipo que não está registrado (um mod saiu) são mantidos como estão.
* Ao trocar de dimensão tudo é copiado; ao morrer, só o que tem `copyOnDeath`.
* A sincronização com o cliente é do mod que usa (como no NeoForge sem `sync`).

## 5. Matemática (`modern.math`)

* `Mth`: os nomes e o comportamento do `Mth` moderno (`clamp`, `lerp`, `rotLerp`, `wrapDegrees`, `catmullrom`,
  `hsvToRgb`, `smoothstep`...), com a mesma tabela de senos do vanilla.
* `Vec3`: vetor imutável com a API moderna (`add`, `subtract`, `scale`, `normalize`, `cross`, `xRot`/`yRot`/`zRot`,
  `lerp`, `distanceTo`...), mais `position(entity)`, `eyePosition(entity)`, `viewVector(entity)`,
  `deltaMovement(entity)` e conversão para o `Vec3` do 1.7.10 (`toVanilla`, `of`).
* `BlockPos`: posição imutável com `offset`, `above`, `below`, `relative(ForgeDirection)`, `containing`,
  `of(entity)`, `distSqr`.

## Ainda não está aqui

* Texto traduzível com cor RGB: o modelo de texto do Kunftig (`common.text`) é de nomes (texto literal); unificar
  com componentes traduzíveis fica para outra PR.
* Uso em jogo: as peças são cobertas por testes unitários; o uso real (Relics) ainda está **a verificar** em jogo.

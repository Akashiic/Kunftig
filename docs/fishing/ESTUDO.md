# Estudo: pesca do 1.21.1 no 1.7.10 (API para plugins de pesca)

> Objetivo: dar a plugins Bukkit no Thermos/Crucible 1.7.10 a mesma API de pesca do 1.21.1 (estados `LURED`,
> `BITE`, `FAILED_ATTEMPT`, `REEL_IN` e o `FishHook` com tempos configuráveis), como base para um plugin de pesca
> com minigames, loot próprio e competições.

Guia de uso da API: [API.md](API.md).

---

## 1. Conclusão em uma frase

A mecânica do 1.21.1 **já existe** no 1.7.10. O que faltava era a API. O ciclo de mordida do `EntityFishHook` de
1.7.10 é a mesma máquina de estados do `FishingHook` moderno, só que em campos privados sem nome. O Kunftig executa
essa máquina num motor próprio (testado isoladamente), dispara os eventos que faltam e expõe os controles do 1.21.1.

## 2. Fatos verificados

| Fato | Onde | Consequência |
|---|---|---|
| O ciclo de 1.7.10 tem três contadores: `field_146040_ay` (espera, 100–900 menos Isca), `field_146038_az` (peixe nadando até a boia, 20–80), `field_146045_ax` (janela para puxar, 10–30), e o ângulo `field_146054_aA` | MC 1.7.10 descompilado, `EntityFishHook.onUpdate` | são o `timeUntilLured`, `timeUntilHooked`, `nibble` e `fishAngle` do 1.21 |
| Chuva (`canLightningStrikeAt`, 25%) acelera e falta de céu (`canBlockSeeTheSky`, 50%) atrasa, iguais ao moderno | idem | `rain/skyInfluenced` são só interruptores |
| Bukkit 1.7 tem `PlayerFishEvent` com `FISHING`, `CAUGHT_FISH`, `CAUGHT_ENTITY`, `IN_GROUND`, `FAILED_ATTEMPT`; não tem `REEL_IN`, `BITE`, `LURED` | `Crucible/bukkit/.../PlayerFishEvent.java` | o Kunftig dispara os que faltam |
| No 1.7, `FAILED_ATTEMPT` é disparado ao puxar sem nada; no 1.21.1 isso é `REEL_IN`, e `FAILED_ATTEMPT` passou a ser "o peixe escapou por demora" | patch do Crucible em `EntityFishHook.func_146034_e`; Javadoc do Paper 1.21.1 | armadilha documentada no guia |
| `FishHook.setBiteChance()` do Crucible **não faz nada**: o `CraftFish` guarda o valor e nenhum código do 1.7 o lê | `CraftFish.java`; nenhum patch referencia `biteChance` | provável motivo das tentativas anteriores não funcionarem |
| Padrões do 1.21.1: espera 100–600, lure 20–80, ângulo 0–360, Isca −100 ticks/nível, `applyLure`/chuva/céu ligados | Javadoc de `org.bukkit.entity.FishHook` (paper-api 1.21.1) | perfil `MODERN_1_21_1` |
| O pacote "Use Item" moderno é, no 1.7.10, o `C08PacketPlayerBlockPlacement` com direção 255; o Crucible já o transforma em `PlayerInteractEvent` `RIGHT_CLICK_AIR`, e negar o uso do item impede a vara de recolher | patch do Crucible em `NetHandlerPlayServer` | minigames não precisam de biblioteca de pacotes |
| Os patches do Crucible e do Thermos no `EntityFishHook` são idênticos (só muda o import) e não tocam no bloco do ciclo de mordida | aplicados com `patch` sobre o fonte descompilado | o ponto de injeção é o mesmo nos três servidores |
| Et Futurum Requiem só altera `ItemFishingRod` (sons); Hodgepodge não toca em pesca | fontes dos dois | sem conflito de mixin |
| O loot do Forge sorteia a categoria com um único número: lixo `[0,J)`, tesouro `[J,J+T)`, peixe `[J+T,1)` | `FishingHooks.getRandomFishable` | a regra de "água aberta" vira um remapeamento exato |

## 3. Como foi feito

```
EntityFishHook.onUpdate (1.7.10)
  ... movimento ...
  fração de água (loop com isAABBInMaterial)          ← âncora única
  if (!worldObj.isRemote && fração > 0) {             ← @Redirect desta leitura de isRemote
       ciclo de mordida vanilla                        ← pulado: o Kunftig roda o FishingEngine aqui
  }
  ... flutuação e movimento continuam vanilla ...
```

* **Âncora**: em `onUpdate` existe exatamente uma chamada a `World.isAABBInMaterial` (o loop da água), e a primeira
  leitura de `isRemote` depois dela é a condição do ciclo. Um `@Redirect` nessa leitura (com `@Slice` a partir da
  chamada) roda o motor no mesmo ponto do tick e pula a cópia vanilla. Um teste lê o bytecode real e comprova: a
  âncora é única, os dois saltos pulam o mesmo bloco, e esse bloco contém exatamente os três sorteios (espera, lure e
  janela da mordida).
* **Estado nos campos vanilla**: o motor lê e escreve os próprios campos do 1.7.10. Por isso o recolhimento
  (`func_146034_e`), o loot do Forge e o `PlayerFishEvent` do Crucible continuam funcionando sem mudança.
* **Fidelidade**: com o perfil `VANILLA_1_7_10` e nenhum plugin interferindo, o motor consome o gerador aleatório na
  mesma ordem do vanilla (efeitos de partícula e som reproduzidos linha a linha). Instalar o Kunftig não muda a pesca.
* **Eventos com a semântica do CraftBukkit/Paper 1.21.1**: cancelar `LURED` faz o peixe não vir; cancelar `BITE`
  não mostra splash e recomeça a espera; `FAILED_ATTEMPT` é informativo; cancelar `REEL_IN` mantém a boia na água.
  Dentro de `LURED`/`BITE` o plugin pode alterar o tempo de lure e a janela da mordida (o motor respeita).
* **Correção do Paper incluída**: se a redução da Isca alcança a espera máxima, a espera vira 1 tick. No vanilla ela
  ficaria ≤ 0 e seria sorteada de novo para sempre (Isca alta nunca pescaria nada).
* **Água aberta (1.16+)**: área 5×4×5 de água-fonte, ar e vitórias-régias, atualizada enquanto o peixe vem e morde.
  Com o perfil moderno, fora de água aberta o sorteio que cairia em tesouro é redistribuído entre lixo e peixe na
  proporção deles, exatamente o que uma loot table faz ao excluir uma entrada condicional.
* **Camadas**: núcleo sem Minecraft (`FishingEngine`, `FishingParameters`, `OpenWater`, `TreasureRule`), mixin
  (`EntityFishHookMixin`), API Forge (`KunftigFishing`, `FishingListener`) e API Bukkit (`KunftigFishingBukkit`,
  `ModernFishHook`, `KunftigFishEvent`), carregada por reflexão só onde o Bukkit existe.

## 4. Alternativas descartadas

1. **Só plugin, lendo os campos por reflexão a cada tick.** Funciona sem mod no servidor, mas o evento chega depois
   do fato: um `BITE` cancelado já teria mostrado o splash e afundado a boia. Também não permite reproduzir os tempos
   modernos sem brigar com o sorteio vanilla.
2. **Acrescentar `BITE`/`LURED`/`REEL_IN` ao enum `PlayerFishEvent.State` do Bukkit.** Plugins compilados contra o
   enum de 1.7 (e os `switch` gerados pelo javac) podem quebrar. Um evento próprio com os nomes do 1.21.1 dá o mesmo
   resultado sem risco.
3. **Injetar em cada ponto do ciclo vanilla** (por ordinal de cada sorteio/atribuição). Funciona, mas cancelar a
   mordida antes do splash exigiria redirecionar som e partículas individualmente; o motor próprio controla a ordem
   inteira com uma única âncora verificada.

## 5. Verificação feita

* **25 testes de pesca no mod** (34 no total do mod, contando os 9 de nametag): ciclo completo, faixas dos dois perfis, cancelamento
  de `LURED`/`BITE`, janela da mordida e puxões, chuva/céu (estatístico) e os interruptores, Isca e a correção do
  Paper, encerrar a mordida por fora (janela 1 escapa com evento, 0 encerra em silêncio), validação igual à do Bukkit, ajuste de tempos dentro dos eventos, eventos que não alteram a aleatoriedade,
  água aberta (camadas, bloco sólido, área 5×5), remapeamento do tesouro (400 mil sorteios por combinação de
  Sorte/Isca: nenhum tesouro e proporção lixo/peixe correta), âncora e alvos do mixin no bytecode real, contratos da
  API Bukkit.
* **Mixin**: o annotation processor resolve todos os alvos sem aviso; refmap com nomes SRG (`func_70071_h_`,
  `field_72995_K`, `func_72830_b`, `func_146034_e`, `func_146033_f`).
* **Portão 1.7.10**: as classes Bukkit do mod são recompiladas contra a API do Crucible; um teste negativo confirmou
  que `org.bukkit.inventory.EquipmentSlot` (aceito pela API 1.8.8) é rejeitado.

**Não verificado**: execução num servidor real com jogadores (depende da EULA, como nas nametags). Plano manual:

1. Thermos/Crucible com o Kunftig em `mods/` (e UniMixins). No log: `Kunftig fishing: Bukkit API enabled`.
2. Pescar sem plugins: deve ser igual ao vanilla (boia, bolhas, splash, tempos).
3. Plugin de teste ouvindo `KunftigFishEvent`: `LURED` → `BITE` → `FAILED_ATTEMPT` sem puxar; `REEL_IN` ao puxar cedo.
4. Cancelar `BITE`: nenhum splash, a espera recomeça. Cancelar `REEL_IN`: a boia fica.
5. `setBiteWindow(200)` no `BITE`: dez segundos para puxar.
6. `profile=MODERN_1_21_1`: pescar debaixo de um bloco / num buraco 1×1 nunca dá tesouro.
7. Singleplayer com o mod: pesca normal, sem erros (sem Bukkit, a API Bukkit não é carregada).

## 6. Diferenças que continuam existindo

* **Sem mão secundária** no 1.7.10 (`getHand()` do 1.21 não existe; o Backhand não é tratado).
* **Animação da boia mordida**: no 1.13+ o cliente recebe um "biting" sincronizado; no 1.7 a boia afunda pelo
  movimento, como sempre foi. Os nomes de partícula são os do 1.7 (`wake` em vez de `fishing`).
* **Loot não é data-driven**: o 1.7 usa as listas do Forge (`FishingHooks`); plugins trocam o item no
  `PlayerFishEvent` `CAUGHT_FISH` (veja o guia).
* `FishHook.retrieve` não está no 1.21.1, então também não foi espelhado.

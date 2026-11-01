# Guia da API de pesca do Kunftig (Bukkit no Thermos/Crucible 1.7.10)

Para quem vai escrever o plugin de pesca. Por que e como funciona: [ESTUDO.md](ESTUDO.md).

## Instalação

* **Servidor**: o jar do Kunftig em `mods/` (o mesmo jar do cliente) e o UniMixins. Nenhum plugin é necessário para a
  API. No log aparece `Kunftig fishing: Bukkit API enabled`.
* **Seu plugin**: `compileOnly("com.akashic.kunftig:kunftig-api:<versão>")` (veja [../API.md](../API.md)). O Kunftig é
  um mod, não um plugin: não vai em `depend` do `plugin.yml`. No `onEnable`, confira `KunftigApi.isFishingAvailable()`.
* **Configuração** (`config/kunftig.cfg`, categoria `fishing`): `enabled`, `profile` com `VANILLA_1_7_10` (padrão,
  pesca idêntica ao vanilla) ou `MODERN_1_21_1` (tempos do 1.21.1 e tesouro só em água aberta), e `lava` (padrão
  `false`: pesca na lava para todas as boias). Esses valores só definem o início de cada boia: o plugin pode mudar tudo
  por boia.

## Do 1.21.1 para o Kunftig

| No Paper/Spigot 1.21.1 | No Thermos/Crucible com o Kunftig |
|---|---|
| `PlayerFishEvent` `FISHING`, `CAUGHT_FISH`, `CAUGHT_ENTITY`, `IN_GROUND` | o mesmo `PlayerFishEvent` do Bukkit (mesmo significado) |
| `PlayerFishEvent` `LURED`, `BITE`, `FAILED_ATTEMPT`, `REEL_IN` | `KunftigFishEvent` com `State.LURED`, `BITE`, `FAILED_ATTEMPT`, `REEL_IN` |
| `PlayerFishEvent` `FAILED_ATTEMPT` do **1.7** | é o `REEL_IN` moderno; ignore-o e use o `KunftigFishEvent` |
| `event.getHook()` (`FishHook` com `setWaitTime`, ...) | `KunftigFishingBukkit.of(event.getHook())`, ou `kunftigEvent.getHook()` |
| `FishHook.HookState` | `ModernFishHook.HookState` |
| `event.getHand()` | não existe no 1.7.10 (sem mão secundária) |
| `setBiteChance` (obsoleto) | não espelhado; no Crucible ele nunca fez nada |
| `FishHook#retrieve(EquipmentSlot)` (Paper) | `retrieve(applyRodDamage)` (sem mão secundária; API nível 3) |
| — | extensões: `setBiteWindow`, `setBiteWindowRange`, `isBiting`, `getOwner`, `setTreasureRequiresOpenWater`, `setLavaFishing`, `isInLava` |

Todos os métodos de `ModernFishHook` têm o nome, o padrão e a validação do `FishHook` do 1.21.1. Tudo roda na thread
principal.

## Exemplos

### Tempos por boia

```java
@EventHandler
public void onCast(PlayerFishEvent event) {
    if (event.getState() == PlayerFishEvent.State.FISHING && KunftigFishingBukkit.isAvailable()) {
        ModernFishHook hook = KunftigFishingBukkit.of(event.getHook());
        hook.setWaitTime(60, 200);          // peixe aparece entre 3 e 10 s (antes da Isca)
        hook.setLureTime(20, 40);           // e nada até a boia em 1 a 2 s
        hook.setSkyInfluenced(false);       // pescar coberto não atrasa
    }
}
```

### Os estados que faltavam no 1.7

```java
@EventHandler
public void onFishState(KunftigFishEvent event) {
    ModernFishHook hook = event.getHook();
    switch (event.getState()) {
        case LURED:          // peixe apareceu; hook.setTimeUntilBite(n) muda quanto ele demora a morder
            break;
        case BITE:           // mordeu: a boia afundou e puxar agora pega o peixe
            hook.setBiteWindow(100);            // cinco segundos em vez de 0,5–1,5 s
            break;
        case FAILED_ATTEMPT: // demorou demais, o peixe escapou (não cancelável)
            break;
        case REEL_IN:        // puxou com nada na linha; cancelar mantém a boia na água
            break;
    }
}
```

Cancelar `LURED` faz o peixe não vir; cancelar `BITE` não mostra o splash e recomeça a espera.

### Minigame na mordida

O 1.7.10 não precisa de biblioteca de pacotes para isso. O clique direito no ar com a vara (o "Use Item" moderno) já
chega como `PlayerInteractEvent` `RIGHT_CLICK_AIR`, e cancelar o evento impede a vara de recolher a linha (verificado
no patch do Crucible: o uso do item só acontece se o evento não negar `useItemInHand`).

1. No `KunftigFishEvent` `BITE`: comece o minigame e dê tempo com `hook.setBiteWindow(...)`. A interface pode usar o
   actionbar/título/bossbar do NecroTempus.
2. Enquanto o minigame roda, cancele o `PlayerInteractEvent` `RIGHT_CLICK_AIR`/`RIGHT_CLICK_BLOCK` do jogador com a
   vara e conte os cliques. Clique esquerdo chega como `PlayerAnimationEvent`, agachar como `PlayerToggleSneakEvent`.
3. Venceu: `hook.retrieve(true)` recolhe a linha pelo servidor, sem esperar outro clique (API nível 3). Com a
   janela ainda aberta, isso gera o `PlayerFishEvent` `CAUGHT_FISH` de sempre, onde você troca o item (abaixo), e o
   peixe voa até o jogador; a vara leva o dano normal e o braço balança para os outros jogadores. Em servidores com
   Kunftig mais antigo (`KunftigApi.apiVersion() < 3`), libere o próximo clique: o recolhimento do jogador dá o mesmo
   resultado. Perdeu: `hook.setBiteWindow(1)` faz o peixe escapar no próximo tick com `FAILED_ATTEMPT`;
   `setBiteWindow(0)` ou `hook.resetFishingState()` encerram sem evento.

   O próprio jogador não vê o braço balançar no `retrieve`: mandar a animação para o cliente dele faria o cliente
   devolver um `PlayerAnimationEvent`, que o seu minigame leria como clique esquerdo. Chame o `retrieve` fora dos
   handlers de `LURED`/`BITE` (por exemplo, no fim do minigame).

Outra forma, sem mexer no clique: cancelar o `PlayerFishEvent` `CAUGHT_FISH`. No Crucible, o cancelamento retorna
antes de criar o item e de matar a boia, então a boia continua na água com a janela correndo.

### Loot próprio

```java
@EventHandler
public void onCatch(PlayerFishEvent event) {
    if (event.getState() == PlayerFishEvent.State.CAUGHT_FISH && event.getCaught() instanceof Item) {
        ModernFishHook hook = KunftigFishingBukkit.of(event.getHook());
        ItemStack loot = hook.isInOpenWater() ? rollTreasureTable() : rollCommonTable();
        ((Item) event.getCaught()).setItemStack(loot);   // o item ainda não foi lançado ao jogador
        // para marcar o peixe (peso, espécie...), veja "Dados persistentes em itens" em ../API.md
        event.setExpToDrop(3);
    }
}
```

### Pesca na lava

```java
@EventHandler
public void onCast(PlayerFishEvent event) {
    if (event.getState() == PlayerFishEvent.State.FISHING && isNetherRod(event.getPlayer().getItemInHand())) {
        KunftigFishingBukkit.of(event.getHook()).setLavaFishing(true);
    }
}

@EventHandler
public void onFishState(KunftigFishEvent event) {
    if (event.getState() == KunftigFishEvent.State.BITE && event.getHook().isInLava()) {
        // tabela de loot do Nether, minigame com a arte da lava...
    }
}
```

Com `setLavaFishing(true)` a boia flutua na lava (a mesma física da água) e o ciclo inteiro roda lá: os mesmos
estados e eventos, a mesma janela da mordida, o mesmo `PlayerFishEvent` `CAUGHT_FISH` para trocar o item. No lugar da
esteira e dos respingos aparecem chamas, fumaça e estalos de lava, e a mordida soa como `liquid.lavapop`. A boia não
pega fogo, e o item pescado é à prova de fogo e sai da superfície da lava (dentro dela um `EntityItem` se joga para
um lado aleatório). Na água a boia continua pescando normalmente. `isInOpenWater()` na lava significa "lava aberta"
(fontes de lava em volta, como a regra do 1.16+).

O cliente não sabe que a boia é de lava: entre as atualizações de posição do servidor (a cada 5 ticks) ele simula a
física vanilla e a boia afunda um pouco, o que aparece como um balanço leve.

## Para mods Forge (sem Bukkit)

`com.akashic.kunftig.fishing.api.KunftigFishing.register(FishingListener)` recebe os mesmos estados com o
`EntityFishHook`, e `KunftigFishing.parameters(hook)` dá os mesmos controles (inclusive `setLavaFishing`). Funciona
também em singleplayer.

## Kill-switch

`-Dkunftig.fishing.disable=true` não aplica o mixin (pesca vanilla, sem eventos). `enabled=false` no config tem o
mesmo efeito com o mixin aplicado: ele devolve o ciclo ao código vanilla.

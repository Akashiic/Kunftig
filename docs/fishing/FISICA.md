# Estudo: a física da boia, do lançamento ao recolhimento (1.7.10 x 1.16+)

> Pedido: entender, com números, por que a pesca do 1.7.10 é "esquisita" (a boia some, sobe, não para na água) e trazer
> o comportamento moderno. Resultado: o modo `fishing.modernBobber` (seção 6).

**Fontes.** O lado 1.7.10 vem do código decompilado deste repositório (`EntityFishHook`, `EntityItem`, `World`,
`BlockLiquid`, `RenderFish`) e está verificado. O lado moderno tem dois níveis:

- **[confirmado]**: gravidade 0,03 e arrasto 0,92, conferidos em fontes externas ([wiki](https://minecraft.wiki/w/Fishing_Bobber),
  [horizon#3995](https://github.com/horizon-rust/horizon/pull/3995)).
- **[a confirmar]**: freio na entrada e mola da flutuação, escritos a partir do `FishingHook#tick` decompilado do 1.16+,
  de memória. Para conferir, rode `javap -c` no `FishingHook` do jar do Paper 1.21.1 do lobby.

## 1. Lançamento

| | 1.7.10 (`EntityFishHook(World, EntityPlayer)` + `func_146035_c`) | moderno |
|---|---|---|
| Origem | olho do jogador (`posY + 1.62`), 0,16 para o lado e 0,1 para baixo | olho, 0,3 para trás no eixo do olhar |
| Direção | do olhar, `* 0.4` e normalizada | horizontal do olhar; vertical `tan(pitch)` limitada a ±5 |
| Velocidade | **1,5 bloco/tick** + ruído gaussiano `0.0075` por eixo | menor, com ruído triangular [a confirmar] |
| Por tick, no ar | gravidade **0,04** (vem da flutuação com fração 0), arrasto 0,92 | gravidade **0,03** [confirmado], arrasto 0,92 [confirmado] |

O lançamento **não** foi alterado: mudaria o alcance, que todo jogador conhece, e as constantes modernas da velocidade
ainda precisam ser confirmadas.

## 2. Flutuação no 1.7.10 (verificado)

A cada tick, depois de mover a boia (caixa de 0,25 x 0,25, `posY` = base):

```
f  = (fatias i de 0..4 cuja base  posY + 0,05*i  está ≤ topo contado da água) / 5      (0; 0,2; ... ; 1)
vy += 0,04 * (2f - 1)
se f > 0:  vy *= 0,8   e o arrasto vira 0,92 * 0,9
v  *= arrasto
```

O "topo contado" vem de `World#isAABBInMaterial`: para água parada (meta 0) é o **topo do bloco** (`y + 1`), mas a água
é desenhada até `y + 8/9`. A boia para onde `f` fica entre 0,4 e 0,6, ou seja, com a base em ≈ `y + 0,90`, só 0,011
acima da superfície desenhada. O sprite (`RenderFish`, quad de 0,5 centrado em `posY`) fica com o centro na linha
d'água. **Em repouso, a boia do 1.7.10 está no lugar certo e quase parada** (oscila 0,009).

O problema está na **entrada**: não há freio. A boia entra com a velocidade do voo e só é segurada pelo
`+0,04 * (2f - 1)` com amortecimento de 0,6624 por tick (`0,8 * 0,92 * 0,9`). Velocidade terminal de subida quando
submersa: `0,04 * 0,6624 / (1 - 0,6624) ≈ 0,078`.

## 3. Flutuação moderna (1.16+, estado BOBBING)

```
ao entrar no fluido:   v *= (0,3; 0,2; 0,3)                                          [a confirmar]
boiando:               d = y + vy - (blocoY + altura)    se |d| < 0,01: d += sinal(d)*0,1
                       vy -= d * aleatório[0,1) * 0,2;   vx, vz *= 0,9               [a confirmar]
fora do fluido:        vy -= 0,03                                                     [confirmado]
sempre:                v *= 0,92                                                       [confirmado]
```

`altura` é a altura desenhada do fluido no bloco: 8/9 para uma fonte com ar em cima, 1 com fluido em cima. É uma mola
para a superfície com ganho aleatório, e a "zona morta" de 0,01 empurra a boia para fora do equilíbrio. Por isso ela
nunca para: é a balançada suave do jogo moderno.

## 4. Comparação numérica

Lago de 4 blocos de fundo, boia entrando na água com velocidade vertical `v`. As alturas são da base da boia, em
relação à superfície desenhada; os valores são de simulação.

| `v` na entrada | 1.7.10: mergulho máximo | 1.7.10: repouso / oscilação | moderno: mergulho máximo | moderno: repouso / balanço |
|---|---|---|---|---|
| 0,3 | −0,36 | +0,01 / 0,007 | −0,18 | ≈0 / 0,07 |
| 0,8 | **−1,30** | +0,01 / 0,009 | −0,51 | ≈0 / 0,07 |
| 1,2 (lançamento forte) | **−2,41** | +0,01 / 0,009 | −1,22 | ≈0 / 0,06 |

O "esquisito" do 1.7.10 é **o lançamento forte afundando mais de 2 blocos e voltando devagar** (≈0,078 bloco/tick,
uns 2,5 s), e depois a boia **parada demais**. O moderno mergulha metade e balança sempre. Os testes
(`ModernBobberTest`) refazem a comparação com 20 sementes aleatórias.

**"A boia sobe e não fica na água":** a física do servidor no 1.7.10 não produz isso em repouso. As causas mais prováveis
estão no cliente:

- O cliente simula a boia entre as atualizações do servidor (posição a cada 5 ticks, `EntityTracker`), e a puxada da
  mordida e as correções chegam como saltos. Se cliente e servidor discordam (por exemplo um plugin mexendo na boia),
  a boia pula.
- A volta lenta depois de um mergulho fundo parece a boia "subindo do nada".

Para fechar a questão, um registro por tick do `posY`/`motionY` da boia no servidor e no cliente durante o caso
estranho (o javaagent que vocês já usam) mostraria qual dos dois aparece.

## 5. Linha e desenho da boia

| | 1.7.10 (`RenderFish`, verificado) | moderno |
|---|---|---|
| Linha | 16 segmentos, `x = dx*f`, `y = dy*(f² + f)/2 + 0,25`, `z = dz*f` | mesma fórmula, 16 segmentos |
| Ponta da vara | mão do jogador (`-0,5; 0,03; 0,8` girado pelo balanço do braço; terceira pessoa com 0,35 e 0,85 de deslocamento) | mesmo princípio, com a mão principal escolhida |
| Boia | sprite de 0,5 virado para a câmera, centrado em `posY` | sprite próprio, também centrado |

Não há o que trazer aqui: a curva da linha e a posição do sprite já são as mesmas.

## 6. O que foi implementado: `fishing.modernBobber`

- **Servidor.** Com `fishing.modernBobber=true` no `kunftig.cfg`, um `@Inject` no único `setPosition` depois do laço da
  água troca a velocidade calculada pela flutuação do 1.7.10 pela de `ModernBobber.step`. A velocidade de partida é
  capturada no fim do redirect do ciclo, então os puxões da mordida do motor estão incluídos. O freio vale na primeira
  entrada no fluido.
- **Cliente.** Se cliente e servidor simularem físicas diferentes, a boia pula a cada correção. Por isso o servidor
  avisa no login com uma mensagem de 1 byte no canal `kunftig:fishing`, e o cliente com o Kunftig passa a prever a mesma
  física. Cada nova conexão começa com a física vanilla, então um servidor sem Kunftig nunca é previsto errado. Clientes
  sem o Kunftig continuam com a do 1.7.10 e veem os saltos de correção; é o mesmo que acontece hoje quando o servidor
  mexe na boia.
- **Pesca na lava:** o servidor usa a superfície da lava. O cliente não sabe que a boia é de lava, então segue a da
  água e pode haver pequenas correções.
- **Desligado por padrão.** Com o modo moderno, o gerador aleatório da boia é consumido a mais, então a pesca deixa de
  ser idêntica ao vanilla em sementes. O perfil `VANILLA_1_7_10` continua idêntico.

**Plano de teste (cliente real):**

1. Lançamento forte com `modernBobber=false`: a boia some mais de 2 blocos e volta devagar. Com `true`: afunda pouco e
   volta logo.
2. Em repouso: com `true`, balança de leve e para na linha d'água, sem saltos.
3. Mordida: a boia afunda e volta, como antes.
4. Cliente sem o Kunftig num servidor com `true`: pesca normal, só com mais correções visuais.
5. Confirmar as constantes [a confirmar] com `javap -c` no `FishingHook` do Paper 1.21.1.

## 7. Medido no jogo (servidor de desenvolvimento, 2026-11-02)

Feito com `runServer -Dkunftig.devHarness=true` (`com.akashic.kunftig.dev.FishingHarness`, fora do jar de release).
O ambiente é Forge 1.7.10 com o Kunftig e os mixins aplicados, `EntityFishHook` e `EntityItem` reais, um lago de 4
blocos e um `FakePlayer` com a vara. Resultado completo em `run/server/kunftig-harness.txt`.

**Boia** (base em relação à superfície desenhada), igual à simulação da seção 4:

| Entrada | Vanilla: mais fundo / repouso / oscilação | `modernBobber`: mais fundo / repouso / balanço |
|---|---|---|
| −0,3 | −0,358 / +0,012 / 0,007 | −0,125 / −0,002 / 0,114 |
| −0,8 | −1,300 / +0,013 / 0,009 | −0,496 / +0,004 / 0,090 |
| −1,2 | −2,415 / +0,010 / 0,009 | −1,092 / 0,000 / 0,102 |

**Trava (#9):** com `setReelLocked(true)` durante uma mordida, `func_146034_e` devolveu 0, a boia continuou viva, o
`fishEntity` foi mantido e a mordida continuou. `retrieve` ignorou a trava, devolveu 1, removeu a boia e criou o item.

**Item pescado (#10)**, boia a 5 blocos do jogador, margem 1 bloco acima da água:

| Situação | Vanilla | `setCatchAimedAtAngler(true)` |
|---|---|---|
| jogador a 3,5 da beira, boia em repouso | coletado (tick 9) | coletado (tick 8) |
| jogador a 3,5 da beira, boia puxada pela mordida (−0,3) | cai na água | coletado (tick 8) |
| jogador a 1,5 da beira (a montagem da issue) | cai na água | coletado (tick 8) |
| jogador a 1,5 da beira, com a mordida | cai na água | coletado (tick 8) |

"Coletado" é o primeiro tick em que o item entra na área de coleta do jogador (caixa crescida em 1; 0,5; 1). O
`FakePlayer` não pega itens, por isso no teste o item mirado segue voando e cai atrás. Um jogador real o pega nesse
tick.

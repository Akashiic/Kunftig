# Kunftig Nametag Protocol (KNP), version 1

Normative description of the plugin-message protocol between the Kunftig client mod (Minecraft 1.7.10) and a
server-side implementation (the Kunftig Bukkit plugin). Reference implementation:
`common/src/main/java/com/akashic/kunftig/common/protocol/`.

## 1. Transport

| Item | Value |
|---|---|
| Channel | `kunftig:nametag` (15 chars) |
| Packets | 1.7.10 `C17PacketCustomPayload` / `S3FPacketCustomPayload`, or their equivalents on newer versions |
| Max payload | 32766 bytes (Bukkit `Messenger.MAX_MESSAGE_SIZE`) |

Why this name: it fits Bukkit 1.7's 16-character channel limit and the 20-character limit of the 1.7 packet, and
it is a valid lowercase namespaced key, so ViaVersion's 1.12/1.13 channel rewriting passes it through unchanged
(`ItemPacketRewriter1_13.getNewPluginChannelId` / `getOldPluginChannelId`).

The client sends `REGISTER` with the channel name before its first message. Bukkit only delivers
`Player#sendPluginMessage` to registered channels, and Velocity replays registered channels to the next backend on
a server switch.

## 2. Encoding

* **VarInt**: Minecraft's encoding (7 bits per byte, little-endian groups, at most 5 bytes).
* **String(n)**: VarInt byte length, then UTF-8 bytes. `n` is the maximum length in UTF-16 chars. A reader rejects
  a declared byte length above `3n`, invalid UTF-8, or more than `n` decoded chars.
* **Int**: 4 bytes, big-endian.
* **Byte**: 1 unsigned byte.

Every payload is `VarInt packetId` followed by the body. A receiver **must ignore** unknown packet ids (forward
compatibility) and **must reject** (ignore and log) a known packet whose body is malformed, exceeds a limit or has
trailing bytes. A malformed message never disconnects the player.

## 3. Handshake

```
client                                   server
  |-- REGISTER kunftig:nametag ------------>|
  |-- HELLO(min, max, caps, version) ------>|  negotiate v = min(serverMax, clientMax),
  |                                         |  require v >= max(serverMin, clientMin)
  |<----------------- HELLO_ACK(v, version) |
  |<----------------- TEAM_STYLE ... (full state)
  |<----------------- TEAM_STYLE / TEAM_STYLE_REMOVE / RESET (updates)
```

* The client sends `HELLO` at the end of every `JoinGame` (a new server, or a proxy switch) and when it receives
  `PROBE`. It sends at most one `HELLO` per 500 ms in reply to probes.
* On `JoinGame` the client drops every style and returns to the "no session" state.
* On `HELLO_ACK` the client drops every style; the server then sends the full current state.
* Before `HELLO_ACK` the client only accepts `HELLO_ACK` and `PROBE`.
* The server sends `PROBE` when a client registers the channel without sending `HELLO` within 10 ticks, and to
  every registered player when it (re)starts. It ignores `HELLO`s less than 250 ms apart from the same player.
* If the version ranges do not overlap, the server does not answer and logs it.

## 4. Packets

### Serverbound

| Id | Name | Body |
|---|---|---|
| `0x00` | HELLO | VarInt `minVersion`, VarInt `maxVersion` (≥ min), VarInt `capabilities`, String(64) `clientVersion` |

HELLO is decoded the same way in every version; fields appended by future versions are ignored.

Capabilities (informational, the server may use them to adapt):

| Bit | Name | Meaning |
|---|---|---|
| `1` | `CAP_RGB` | the client currently renders `§x` 24-bit colors |
| `2` | `CAP_NAME_STYLE` | the client applies `nameStyle` (including gradients) |
| `4` | `CAP_VISIBILITY` | the client applies nametag visibility |

### Clientbound

| Id | Name | Body |
|---|---|---|
| `0x00` | HELLO_ACK | VarInt `version`, String(64) `serverVersion` |
| `0x01` | PROBE | — |
| `0x02` | RESET | — (drop every style) |
| `0x03` | TEAM_STYLE | see below |
| `0x04` | TEAM_STYLE_REMOVE | String(64) `team` |

**TEAM_STYLE** replaces the whole Kunftig style of one team:

```
String(64)  team          scoreboard team name
VarInt      fields        bit 0 prefix, bit 1 suffix, bit 2 nameStyle, bit 3 visibility (other bits: reject)
[String(8192) prefixJson] if bit 0: JSON text component
[String(8192) suffixJson] if bit 1: JSON text component
[nameStyle]               if bit 2: Byte decorations (bit k=obfuscated 1, l=bold 2, m=strikethrough 4,
                                    n=underlined 8, o=italic 16; other bits: reject),
                                    VarInt count (0..16), count × Int rgb (0x000000..0xFFFFFF)
[Byte visibility]         if bit 3: 0 always, 1 never, 2 hideForOtherTeams, 3 hideForOwnTeam
```

## 5. Semantics

### Styles are keyed by team name

A style applies to the team with that name on the client's world scoreboard, **whether the vanilla team packet
arrives before or after the style**, and it **outlives vanilla team removal**: Bukkit removes and re-creates every
team when a plugin swaps a player's scoreboard. A style ends only with `TEAM_STYLE_REMOVE`, `RESET`, `HELLO_ACK` or a
new `JoinGame`. A server that reuses a team name for something else must send a new `TEAM_STYLE` or a removal.

### Absent fields fall back to vanilla

An absent prefix/suffix means the vanilla team value (as sent in the vanilla team packet). An absent visibility
means vanilla behavior (always visible on 1.7.10).

### Text components

1.16+ JSON text format. Clients support: `text`, `translate` (renders `fallback`, else the key), `keybind` (renders the
key), `score`/`selector`/`nbt` (render empty), `color` (legacy name or `#rrggbb`), `bold`, `italic`, `underlined`,
`strikethrough`, `obfuscated`, `extra`, arrays (first element is the parent of the others), and plain JSON strings.
Other keys are ignored; invalid colors are ignored. Limits: depth 32, 2048 nodes. A component that fails to parse
is treated as absent (that field falls back to vanilla).

### Composition (what the 1.7.10 consumers read)

Let `P` be the prefix, `N` the member name and `S` the suffix, rendered as legacy `§` strings.

* **Without `nameStyle`** (1.7 semantics): `P + N + S`; the last formatting of `P` carries into `N`.
* **With `nameStyle`** (1.13+ semantics): `P + §r + style(N) + §r + S`. The name and the suffix start from a reset
  style. `§r` is omitted where nothing precedes it.
* A **gradient** `nameStyle` is applied per code point, interpolating linearly in sRGB; the first and last stops are
  exact. Consumers that only read the prefix getter (instead of formatting the whole name) get the first stop.

### Colors

Clients render 24-bit colors as Spigot's `§x§r§r§g§g§b§b` sequence only when their font renderer understands it
(Angelica with `enableFontRenderer` and `enableRGBColors`); otherwise every color is reduced to the nearest of the 16
legacy colors (redmean distance). Colors that are exactly a legacy color always use the short `§c` form.

### Visibility

Same meaning as the 1.8+ team option `nametagVisibility`. It only hides nametags; it never forces one visible.

## 6. Versioning

* Adding a clientbound packet with a new id is compatible (old clients ignore it).
* Changing a body, or adding a TEAM_STYLE field bit, requires a new protocol version: version 1 clients reject
  unknown field bits.
* `HELLO` keeps its layout in every version so ranges can always be negotiated.

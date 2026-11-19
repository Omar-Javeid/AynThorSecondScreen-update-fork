# CLAUDE.md

This file provides guidance to AI coding assistants when working with code in this repository.

## What this is

**This is a fork of [AynThorSecondScreen](https://github.com/exojosh/AynThorSecondScreen), updated from Minecraft 1.21.11 to Minecraft Java 26.3.**

A Fabric mod (Java 25) for Minecraft Java 26.3 that runs inside Minecraft's JVM. It hides the vanilla HUD (health, hunger, XP, armor, hotbar) and streams that state over a loopback TCP socket, so a separate companion Android app can render it on a second screen. It also accepts short text commands back over the same socket (hotbar slot selection, simulated keybind presses, item-icon requests).

This mod is one half of a two-process system targeting the **AYN Thor** (dual-screen Android handheld) running Minecraft via **Zalith Launcher 2**. The other half — the Android companion app that renders the HUD — lives in a sibling repo, `Android_AynThor_MinecraftSecondScreen`. The two communicate only via the socket protocol; there's no shared code or build.

**`thor-hud-handoff.md`** is a detailed technical handoff/design doc covering the full system (both repos) — read it first for the JSON protocol spec, architecture rationale, and known issues. It's a snapshot from a point in development and may drift from the current tree in file/class specifics (there was a period of frequent package renaming) — verify anything file-specific against the actual source before relying on it.

## Commands

```
./gradlew build          # compile + package the mod jar
./gradlew runClient      # launch a dev Minecraft client with the mod loaded
```

Requires JDK 25. Uses Fabric Loom with `splitEnvironmentSourceSets()` enabled, so code lives in either `src/main` (both sides) or `src/client` (client-only — nearly all of this mod's logic lives here, since it's a client-only HUD mod). Minecraft, Loader, Loom, and Fabric API versions are pinned in `gradle.properties`. Minecraft 26.3 is unobfuscated and has no Yarn mappings; source references use Mojang's official class and member names.

Manual protocol smoke test (no Android app needed): run `runClient`, join a world, then from another terminal:
```
nc localhost 48291
```
You should see JSON lines when HUD state or map data changes.

Scripted version, which is what to actually use — it auto-joins a world and dumps the whole protocol (assets, a HUD snapshot, map PNGs, bindings, chat) to disk:
```
./gradlew runClient -PquickPlay="New World"
python tools/capture_protocol.py --out captures
python tools/capture_protocol.py --out captures --say "hello"   # chat round trip
```
`--say` sends a `CHAT:` command and waits for the server's echo to come back as a chat line, which exercises both directions in one run. A leading `/` makes it a command, as in game.

## Architecture

Full protocol spec (message shapes, field meanings, command codes) is in `thor-hud-handoff.md` §2 — don't re-derive it from source when the doc already has it verified. In short: the mod broadcasts a full HUD snapshot when it changes over `127.0.0.1:48291`, newline-delimited JSON; map tiles are sent on their update interval. It replies to `ICON:<itemId>` requests with a base64 PNG when a matching texture is available, otherwise with an explicit failure response, and accepts single-tick simulated key/hotbar-press commands as plain text lines.

**Why a socket, not shared memory:** the mod and the companion app run in separate Android app sandboxes (different processes/UIDs) despite being on the same device, so loopback TCP is the simplest channel available.

### Key classes (`src/client/java/com/exojosh/client/`)

- `GameHudVisibility` — decides per element whether the *game* draws its own HUD. **This is why the mod no longer calls `HudElementRegistry.removeElement()`:** removal is a one-way door, since the vanilla element's renderer belongs to vanilla and nothing is kept that could put it back. `replaceElement()` hands over the original and takes a wrapper that decides per frame whether to delegate; with nothing shown, that's behaviourally identical to the old removals. The companion app drives it — an element switched off on the second screen is handed back to the top screen, so between the two displays the HUD is drawn exactly once. Wire format is `HUD:<key>,<key>,…`, the **full set every time**, re-sent on every toggle and reconnect: a dropped or reordered delta would leave the two screens disagreeing about who owns an element, which shows up as one drawn twice or not at all. Keys must match `HudElement.key` in the app's `settings/HudSettings.kt`; `offhand` deliberately has no entry here, because vanilla draws the off-hand box as part of the hotbar.
- `ThorHudClient` — `ClientModInitializer`; installs `GameHudVisibility`'s wrappers, starts `HudStateServer`, and on each `ClientTickEvents.END_CLIENT_TICK` builds/broadcasts a `HudState` and drains inbound commands (routing `ICON:`-prefixed ones separately from action commands). `pushBundleToNewClients()` sends each new connection the texture bundle then the key bindings; both have to run on the client thread (resource manager, `Options`/language files), which is why they're here and not on the accept thread.
**The HUD snapshot is sent on change, not every tick** (`broadcastHudStateIfChanged`). It used to go out 20 times a second forever, and almost all of those were byte-identical — nothing in the snapshot changes while a player walks around, since position isn't in it. Measured at 2 lines in 20 seconds idle against ~400 before. The last one is kept and handed to each new connection, or a client connecting to a stationary player would see nothing until they moved.

**The bind retries rather than giving up.** `acceptLoop` used to catch the `BindException` and *return*, killing the accept thread for the session — the second screen was dead until the game restarted, with nothing said anywhere the player looks. It now rebinds every 5s, so clearing a conflict is enough on its own. The failure goes to `System.err` **and** into the player's own chat in red, because this is the one error whose audience can't be reached the usual way: the second screen is the thing that isn't working. Every connection also opens with `{"type":"hello","mod":…,"protocol":N}`, written from the accept thread before anything else — a leftover `adb reverse tcp:48291` puts **adbd** on this port, and adbd accepts and instantly drops every connection, which the app otherwise reports as an ordinary flapping connection.

Leaving a world is reported as `{"type":"noplayer"}`, sent **once on the transition** via a `hadPlayer` latch in `ThorHudClient` — the tick loop used to just return early with no player, which left the second screen showing the last snapshot forever at the main menu. Repeating it per tick would be the same unthrottled broadcast the TODO still lists. The chat relay backlog is dropped with it so old-world messages are not replayed after reconnecting to another world. Note a full *quit* closes the socket before any further tick runs, so the harness can't observe this; the case it fixes is Quit to Title.

`HudState` also carries `gameMode`, `heartType` and `hardcore`. All three are sent **raw rather than pre-reduced** — the HUD decision belongs to whatever draws the HUD, same reasoning as air being sent in ticks. `heartType` follows vanilla's precedence (poison → wither → freezing); `HudAssetCatalog` serves all seventeen heart sprites, because vanilla picks a *set* from the player and then a hardcore variant of that set from the world. The `*_blinking` pair of each is deliberately not served: that's the damage-flash animation, which is its own open item.

- `HudState` — record of one tick's snapshot; static `hotbarFromInventory(player)` and `offhandFrom(player)` builders over a shared `slotFrom(ItemStack)`. Carries `air`/`maxAir` (raw ticks and the current maximum, which Respiration raises above 300) for the companion app's bubble row — sent raw rather than pre-reduced to a bubble count, so the app applies vanilla's own rounding and no HUD decision gets baked into the wire format — plus `absorption` for golden hearts and `offhand` (a `HotbarSlot`, always present; an empty off-hand is `minecraft:air`, not an omitted field, because the app draws the box either way). All three postdate `thor-hud-handoff.md` §2's field list.

Note every element the second screen draws must have a `GameHudVisibility.Element` entry, `AIR_BAR` included; an element missing from that table is never wrapped, so the game keeps drawing it while the second screen draws it too.
- `HudStateServer` — owns the `ServerSocket`, one accept + one reader thread per client, `broadcast()`/`broadcastIcon()`/`broadcastAsset()`/`sendAssetTo()`/`broadcastBindings()`/`sendBindingsTo()`/`broadcastChat()`/`sendChatTo()`/`pollCommand()`/`pollNewClient()`. Writes are synchronized per-socket because the asset bundle is written to one client in a loop while `broadcast()` may be writing a state line to the same socket.
- `ContainerRelay` — the open container's contents, and clicks back into it. **Streams `player.containerMenu`, not `player.getInventory()`**: moving an item is a click on a slot, and the server only honours it against the open menu. Sent **on change**, not per tick, and replayed to new connections. Slot roles (`playerStart`/`hotbarStart`/`armorStart`/`offhandIndex`) remain in the wire format; for the player's own `InventoryMenu` they use named constants, while other menus use the last-36-slot convention. `AbstractContainerMenu.getType()` can throw for the player's own menu, so `handlerTypeOf` guards it. Clicks retain the wire field `syncId` as a stale-state check, but map to `containerId` locally and go through `MultiPlayerGameMode.handleContainerInput`.
- `ChatRelay` — chat both ways. **Receiving** hooks Fabric's `ClientReceiveMessageEvents.CHAT` and `.GAME`; GAME messages with `overlay == true` are the action bar and are skipped. The CHAT event arrives already decorated, so applying decoration again would double the `<name>` prefix. Messages go out as `{"type":"chat","segments":[{"text":…,"color":…}]}` — flattened through `Component.visit(FormattedText.StyledContentConsumer, Style.EMPTY)`, which resolves style inheritance and expands translatable content. Colour is the only styling kept. A `HISTORY_LIMIT` of 100 is replayed to each new connection. **Sending** normalizes and trims with `StringUtil.trimChatMessage`; a leading `/` routes through `sendCommand`, otherwise `sendChat`, preserving command signing and dispatch.
- `HudAssetCatalog` — **the app gets its HUD textures and GUI chrome from here**, pushed to new connections on the client thread so active resource-pack overrides apply. The wire format uses short stable keys (`heart_full`), not paths, so a version bump that moves sprites is a one-file change. Paths were checked against the 26.3 client jar; hearts are nested under `heart/`, while armor and food sprites use flat names such as `hud/armor_full.png`.
  - The catalog also serves **GUI chrome**: `widget_button`/`widget_button_highlighted`/`widget_button_disabled` and `container_slot`, which is what makes the app's tab strip, input grid and inventory cells the game's own controls. The three button sprites are nine-sliced, and their `.mcmeta` border is **3 for the two live states but 1 for `button_disabled`** — the app can't assume one number for all three. `container/slot` is a real 18×18 sprite since the 1.21.2 split, not something to crop out of `container/inventory.png`; there is deliberately no panel key, because vanilla has no nine-sliceable panel (its container background is a fixed 176×166 sheet whose interior is slots and a player portrait), so the app draws that itself from the same file's transcribed edge palette.
- `KeyBindingCatalog` — every key binding the game has, pushed with the asset bundle and re-sent on `BINDINGS` requests. It enumerates `Options.keyMappings`, so the mod and app share one list rather than duplicating tables. `id` is `KeyMapping.getName()` and is resolved with `KeyMapping.get`; labels, categories and bound-key text are translated on the client. A mid-session rebind changes display text when the app next requests a snapshot.
- `CommandDispatcher` — maps compatibility action codes and digit strings to `KeyMapping`s. `BIND:<id>` resolves through `KeyMapping.get`, and hotbar taps use `Options.keyHotbarSlots` rather than changing the selected slot directly.
  - **Simulating a press needs both `KeyMapping` states.** `isDown()` is the held flag; `consumeClick()` consumes the private `clickCount` used by discrete actions. `KeyBindingAccessor` exposes `clickCount`, and `pressForOneTick` increments it and sets the held flag for one tick.
  - **Codes name actions, not keys** — `SWAP`, not `F`. They used to name keys and got it wrong (`R` bound to swap-hands, i.e. vanilla's F; `G` to drop, i.e. Q), and the app rendered the raw code as its button label, so the grid showed an R button doing an F key's job. Naming keys is a dead end regardless: attack and use are mouse buttons with no letter to borrow, and a player rebinding in-game invalidates the whole table at once. The old single letters (`E`/`R`/`G`/`H`/`K`/`F`) are kept as aliases with the behaviour they actually had, so an app built before the rename still works; delete them once both halves are known updated.
  - Commands are dropped while `Minecraft.gui` has a screen or overlay open, avoiding queued presses firing after the UI closes. `CHAT:` is handled in `ThorHudClient`'s command loop because it sends a chat packet rather than a key press.
  - Timing: Fabric's `END_CLIENT_TICK` runs after vanilla processes key mappings, so a dispatched command is picked up on the next tick (~50ms). `tick()` runs before `dispatch()` each tick, releasing the previous press after the game has read it.
- `ItemIconRenderer` — the 26.3 GUI item pipeline no longer exposes the prior direct renderer/ordered-command-queue path for isolated offscreen draws. It returns no rendered image so `ThorHudClient` uses the texture fallback; modeled or composited items may not have a same-named source texture.
- `ItemIconResolver` — resolves an item's raw texture PNG from the `Minecraft` resource manager, with an `item/` → `block/` texture-path fallback. It returns a correct icon only when the corresponding PNG has that item name; missing paths produce an explicit icon-failure response rather than leaving requests pending.
- `NativeImageInvoker` (in `com.exojosh.client.mixin`) — `@Invoker` exposing `NativeImage.writeToChannel(WritableByteChannel)` so map PNGs can be encoded in memory instead of via a temp file. Registered in `aynthor_secondscreen.client.mixins.json`.

### Item icon rendering in 26.3

The 26.3 client jar contains `ItemStackRenderState` and `GuiRenderer`, but not the previous direct `ItemRenderer`/ordered-command-queue path. Item states now submit through `SubmitNodeStorage` and `FeatureRenderDispatcher` as part of the GUI render pipeline; that is not a drop-in replacement for the isolated offscreen draw and GPU readback used before this upgrade.

`ItemIconRenderer` therefore delegates to `ItemIconResolver` by returning no rendered image. The resolver can serve a raw PNG when an `item/<name>` or `block/<name>` texture exists; when neither exists, the socket protocol still sends an explicit `broadcastIconFailure` response. This means model-derived icons for multi-texture, tinted, and composited items remain a known limitation until a supported isolated render route is implemented.

### Icon request reliability

Icon requests are queued (`MAX_ICON_RENDERS_PER_TICK`) rather than resolved inline, and **every request gets an answer**: a raw texture PNG or an explicit failure message (`broadcastIconFailure`, which sends `{"type":"icon","itemId":...}` with no `data` field). Silently dropping unresolvable requests was half the cause of icons appearing inconsistently — the companion app's other half is described in its own CLAUDE.md.

The main (non-client) source set (`src/main/java/com/exojosh/`) is untouched Fabric-template boilerplate and not part of the HUD feature.

## Known limitations

- The isolated item-model rendering path is not available in 26.3, so item icons use raw texture fallback (raw PNG from `item/` or `block/` textures). Model-derived, multi-texture, tinted, and composited items may not have icons until an offscreen render route is implemented.
- Package name is settled at `com.exojosh` (main) / `com.exojosh.client` (client).

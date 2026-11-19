# Client-Side Source Set

This directory contains all the client-only Java sources for the AYN Thor Second Screen mod.
The project uses Fabric Loom's `splitEnvironmentSourceSets()`, so client code lives here
separate from the common (`src/main`) source set.

## Package structure

All client classes live under `com.exojosh.client`:

- **`ThorHudClient`** — `ClientModInitializer`; installs HUD wrappers, starts the socket server, and drives the tick loop.
- **`HudState`** — snapshot record of one tick's HUD state (health, hunger, armor, hotbar, etc.).
- **`HudStateServer`** — owns the `ServerSocket` on `127.0.0.1:48291`; handles client connections and broadcasts.
- **`GameHudVisibility`** — wraps vanilla HUD elements so the mod decides per-frame whether the game draws them.
- **`ContainerRelay`** — streams the open container/inventory contents and relays clicks back.
- **`ChatRelay`** — relays chat messages both ways (game → app and app → game).
- **`HudAssetCatalog`** — serves HUD textures and GUI chrome to connected clients.
- **`KeyBindingCatalog`** — enumerates all key bindings for the companion app.
- **`CommandDispatcher`** — maps action codes to `KeyMapping` presses.
- **`ItemIconRenderer`** / **`ItemIconResolver`** — resolve item icon textures.
- **`MapRenderer`** — renders map data for streaming.
- **`mixin/`** — client-side Mixin classes (`KeyBindingAccessor`, `NativeImageInvoker`, `ExampleClientMixin`).

## Mixins

Client mixins are registered in `src/client/resources/aynthor_secondscreen.client.mixins.json`.

## Building

From the project root:

```
./gradlew build          # compile + package the mod jar
./gradlew runClient      # launch a dev Minecraft client with the mod loaded
```

See the project root `README.md` and `CLAUDE.md` for full details.

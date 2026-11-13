# Better-IA-Edit

Better-IA-Edit is a fork of [IA-Edit](https://github.com/EpicPlayerA10/IA-Edit) by EpicPlayerA10, maintained by Martinouxx. It connects [WorldEdit](https://enginehub.org/worldedit) and [FastAsyncWorldEdit](https://github.com/IntellectualSites/FastAsyncWorldEdit) to [ItemsAdder](https://www.spigotmc.org/resources/73355/) custom blocks: `//set`, `//replace`, `//copy`/`//paste`, schematics, brushes, `//undo` and `//redo` all place and remove custom blocks correctly.

It is a drop-in replacement: it declares `provides: [IA-Edit]`, so remove the original IA-Edit jar before you install this one.

## What changes compared to IA-Edit

| Problem in IA-Edit | Better-IA-Edit |
|---|---|
| Custom blocks pasted with FAWE turn back into note blocks, mushrooms or chorus after a restart, because the ItemsAdder region was written from FAWE's async threads | Every region write goes through a queue that runs on the main thread, in order, a configurable number per tick |
| `//rotate` and `//flip` before `//paste` turn mushroom, chorus (`REAL_TRANSPARENT`) and tripwire blocks into other blocks | Rotated states are mapped back to the custom block they came from, for WorldEdit and FAWE |
| Light-emitting custom blocks placed by WorldEdit stay dark | Their light level is restored after placement |
| Spawner (tile) custom blocks fail in schematics from 1.20.5+ | Spawner NBT is read and written in both the new item component format and the legacy one; `item_model` is supported too |
| A broken block in an ItemsAdder pack throws a `NullPointerException` and breaks block parsing | Faulty blocks are skipped and logged once |
| The cache stays empty when the plugin is reloaded after ItemsAdder loaded | The cache is built on enable when ItemsAdder is already loaded, and on every ItemsAdder reload |
| Worlds already broken by the old plugin stay broken | `/iaedit fix` registers the custom blocks of a selection again |
| bStats | Removed |

## Standalone mode (no ItemsAdder)

On a server that cannot run ItemsAdder, Better-IA-Edit switches to a standalone mode. Schematics pasted with WorldEdit keep their custom blocks, and the resource pack (sent by NPackSyncer) displays them. The plugin reads `standalone.data-file` (default `plugins/NPackSyncer/itemsadder-data.json`, exported by NPackSyncer from a server that has ItemsAdder) and then:

- freezes note blocks, mushroom blocks, chorus plants and tripwire so custom blocks never change state (also set the four `block-updates.disable-*-updates` options to `true` in `config/paper-global.yml`, the plugin warns if they are off);
- drops the custom item when a custom block is broken (or blown up), and places the custom block back when that item is used;
- protects ItemsAdder furniture (armor stands and item frames showing a furniture item) from damage, explosions and manipulation; sneak and hit it in creative to remove it.

- picking a custom block or a piece of furniture (middle click) gives its custom item, like with ItemsAdder;
- holding a custom block item and right-clicking a chest, a door or another interactive block uses that block; sneak to place the custom block against it instead.

`/iaedit items [search]` opens a menu with every custom item: click for one, shift-click for a full stack, middle click (creative) to put a stack on the cursor. `/iaedit info` and `/iaedit reload` also work in this mode.

## Commands

Permission: `iaedit.admin` (op by default).

| Command | Effect |
|---|---|
| `/iaedit fix` | Scans your WorldEdit selection, registers again every custom block the ItemsAdder region lost, and removes stale entries left on normal blocks |
| `/iaedit info` | Version, cached custom blocks, pending region writes, engine in use |
| `/iaedit reload` | Reloads `config.yml` and rebuilds the custom block cache |

## Configuration

```yaml
rotation-fix:
  enabled: true
  commands:
    - paste
restore-light: true
region-writes-per-tick: 20000
fix-max-ms-per-tick: 8
fix-chunks-in-flight: 8
fix-max-blocks: 0
```

- `rotation-fix.commands`: commands whose edit uses the clipboard rotation. Only these edits are corrected, so `//undo` and other edits are never changed.
- `region-writes-per-tick`: how many ItemsAdder region updates run each tick. Lower it if huge pastes cause lag spikes.
- `fix-max-ms-per-tick`: main-thread time `/iaedit fix` may use per tick. It works chunk by chunk: chunks are loaded asynchronously, the ItemsAdder region of a chunk is read once, and blocks are scanned off the main thread. The budget shrinks automatically when the server is busy (it never pushes a tick past ~45 ms).
- `fix-chunks-in-flight`: how many chunks are loaded or scanned at the same time.
- `fix-max-blocks`: size limit of `/iaedit fix` (`0` = no limit).
- `messages`: every message, in [MiniMessage](https://docs.advntr.dev/minimessage/format.html).

## Furniture

Furniture are entities. Copy and paste them with the `-e` flag: `//copy -e`, `//paste -e`.

## Requirements

- Paper (or a fork) 1.20.5 or newer, Java 21
- WorldEdit 7.3+ or FastAsyncWorldEdit 2.13+
- ItemsAdder 4.0+

## Building

```bash
./gradlew build
```

The jar is written to `build/libs/Better-IA-Edit-<version>.jar`.

## License

GPL-3.0, like the original project. See [LICENSE](LICENSE). Original code © EpicPlayerA10.

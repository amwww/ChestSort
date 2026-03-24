# ChestSort

![ChestSort Filtering](https://cdn.modrinth.com/data/taG5zlGt/images/227a00e58437806817b04093bf37b832b8680baa.png)
![ChestSort Sorting](https://cdn.modrinth.com/data/taG5zlGt/images/067f90071fa84c715139b025e8e941924b4a7fdc.png)
ChestSort is a Fabric mod for Minecraft **1.21.x** that helps you **track where items are stored** (chests + barrels) and quickly **sort items from your inventory into containers** using per-container rules.

## TL;DR
ChestSort is a lightweight alternative to advanced sorting contraptions that gives you some of the benefits of sorting without even needing to touch redstone, sorting at the click of a button.

<hr>

Ever had a bunch of chests and barrels and forgot where you put that one item? ChestSort lets you search for an item and see which containers have it, with highlights in the world and UI. 

In the early game of Minecraft, you will probably always have a "chest monster" - a big group of chests that you dump items into. Players then start building automatic sorting systems, but these can be complex and require a lot of resources. ChestSort is a lightweight alternative that gives you some of the benefits of sorting without needing to build redstone contraptions, at the click of a button.

You can set up filter rules on each container to specify what items belong there, and then quickly sort matching items from your inventory into the open container with one click.

## Features

- **Persistent container database**
  - Tracks **chests and barrels only**.
  - Stores per-container item counts and last-updated timestamps.
  - Database is persistent across restarts.

- **Fast item lookup**
  - Search for an item and get a list of containers that contain it.

- **World highlights for find**
  - Highlights matching containers in the world after `/cs find` (outline rendering).

- **In-GUI highlight**
  - When you open a chest/barrel that contains your currently searched item, the container is highlighted in the UI.

- **Highlight modes**
  - `/cs highlights on|off|until_opened` controls whether highlights show, and whether they auto-clear after you open a highlighted container.
  - `/cs highlights dismiss` clears current highlights.

- **Auto refresh on open**
  - Whenever you open a chest/barrel, that container’s stored snapshot is automatically refreshed.

- **Per-container filter rules (whitelist + blacklist)**
  - Each chest/barrel can have **whitelist + blacklist** rules based on:
    - explicit item ids (e.g. `minecraft:cobblestone`)
    - item tags (e.g. `#minecraft:logs`) with per-tag exceptions
    - applied presets (reusable saved rule sets)
  - If an item matches both whitelist and blacklist, you can choose which side wins via a per-container **priority** toggle.

- **One-click Sort**
  - When a container has any rules, a **Sort** button appears.
  - Clicking **Sort** moves matching items from your inventory into the open container.
  - Items matching the blacklist are not moved (unless whitelist is set to win conflicts).

- **Sort feedback + Undo**
  - After Sort/Autosort/Organize, a left-side notification shows what moved/changed.
  - Includes an **Undo** button to revert the most recent operation.

- **Autosort modes**
  - Per-player autosort mode (`never`, `selected`, `always`) plus a per-container autosort toggle.

- **Organize**
  - An **Organize** button rearranges the open container to pack stacks and group items.

- **Preset editor + import/export**
  - Edit presets in a dedicated UI.
  - Presets support **Whitelist/Blacklist** tabs (same as containers).
  - Import/export presets as shareable `cs2|` strings (includes blacklist data).

- **Wand (region bulk edit)**
  - Bind any item as a wand and select a region:
    - Left-click sets pos1
    - Right-click sets pos2
  - The selection is highlighted in-world and shows the region block count and container count.
  - Bulk-apply filters/autosort in the selected region.

## Commands

All commands are under `/cs`.

- `/cs help`
  - Shows an in-game help page describing the UI and command usage.

- `/cs scan`
  - Scans **loaded** chunks for **chests and barrels**, recording their contents into the database.
  - Records the time the scan occurred.

- `/cs find <item>`
  - Finds which scanned containers contain the specified item.
  - `<item>` supports autocomplete.
  - Remembers the item you’re “finding” so the GUI highlight can trigger when you open matching containers.

- `/cs autosort <never|selected|always>`
  - Sets your per-player autosort mode.

- `/cs highlights <on|off|until_opened|dismiss>`
  - Controls how highlights behave for you.

- `/cs tags <item>`
  - Prints the tags for a given item (useful for building tag filters).

- `/cs blacklist ...`
  - Controls how blacklisted items behave for Sort/Autosort and (optionally) container entry.
  - `add <item>` / `remove <item>` / `list` / `clear`
  - `addPreset <name> <blacklist|whitelist|everything>` (adds items from a preset’s blacklist, whitelist, or both)
  - `mode <preventSort|strictPreventSort|preventEntry|strictPreventEntry>`
    - `preventSort`: prevents blacklisted items from being moved by Sort/Autosort unless the item is whitelisted and whitelist wins priority
    - `strictPreventSort`: like `preventSort`, but blacklist always wins (ignores whitelist priority)
    - `preventEntry`: prevents blacklisted items from being put into the container unless the item is whitelisted and whitelist wins priority
    - `strictPreventEntry`: like `preventEntry`, but blacklist always wins (ignores whitelist priority)

- `/cs presets ...`
  - Manage presets:
    - `add <name>` / `remove <name>` / `rename <old> <new>` / `edit <name>` / `list`
    - `import` / `export <name>` / `exportAll` / `exportSelect`

- `/cs wand ...`
  - Region selection + bulk operations (loaded chunks only):
    - `bind` (binds your current main-hand item)
    - `unbind` (removes the wand binding)
    - `deselect` (clears pos1/pos2 selection)
    - `copy [pos]` (copies the filter from a targeted container into a clipboard)
    - `paste` (applies clipboard filter to all containers in the region)
    - `autosort <on|off>` (bulk toggle per-container autosort)
    - `clear` (clears filters in the region)
    - `merge` (unions all filters in the region into the clipboard)

## Container Filter UI

1. Open a chest or barrel.
2. Click **Filter** on the right side of the container screen.
3. Use the left panel to view what’s applied:
  - Filter Items
  - Filter Tags (with exceptions)
  - Filter Presets
4. Use the left header tabs to switch between **Whitelist** and **Blacklist**.
5. If you use both tabs, set the conflict **priority** (which side wins when an item matches both).
6. Use the right panel search:
  - type text to search for items
  - type `#something` to search tags
  - type `&name` to search presets
7. Click the green **+** to add rules, red **x** to remove.
8. If the filter rules are non-empty, click **Sort** to move matching items from your inventory into the container.

Organize:
- Click **Organize** to pack and group the open container.

Undo:
- After Sort/Autosort/Organize, use the **Undo** button to revert.

Autosort:
- The filter UI includes an **Autosort** toggle for the current container.
- Autosort behavior depends on your `/cs autosort` mode.
- When autosort triggers, it automatically moves matching items from your inventory into the container whenever you open it.

Notes:
- The filter is stored per container position + dimension.
- The mod only targets **chests** and **barrels**.

## Installation

1. Install Fabric Loader for Minecraft 1.21.x.
2. Install Fabric API.
3. Put the built mod jar into your `mods/` folder.

## Development

- **Minecraft**: 1.21.x
- **Yarn mappings**: 1.21.x+build.4
- **Fabric Loader**: 0.18.4
- **Fabric API**: 0.141.3+1.21.x
- **Java**: 21

## Notes to Scanning

- `/cs scan` scans **loaded** chunks only (it does not force-load chunks), meaning it doesn't necessarily see all containers in the world.
- The mod relies on players to run `/cs scan` after placing containers or loading new chunks; the data may be inaccurate until a scan is performed. The mod, however, automatically detects new containers or changes to container contents when you open them.

## Notes to Sorting

- Sorting only moves items from your inventory into the open container; it does not move items between containers or from the container into your inventory.
- Some servers may consider this an unfair advantage or game modification, and this may flag the anti-cheat as putting items into a container sends multiple packets in quick succession. Use with caution on multiplayer servers.

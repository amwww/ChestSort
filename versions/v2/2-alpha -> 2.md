# ChestSort Changelog 
### v2-alpha to v2
<br>

<div style="background: turquoise; border-radius: 15px; height: 30px; padding: 0px 10px; box-sizing: border-box; width: min-content; color: white;font-family: system-ui; font-size: 13px; text-align: center; align-content: center; font-weight: 600;">FEATURES</div>
<div style="height: 10px;"></div>

- **Updated to Minecraft 26.2.**
- **ChestSort is now fully client-only.** The mod no longer needs to be installed on the server at all - singleplayer and multiplayer now behave identically, and everything (sorting, filters, presets, autosort, wand, find) works the same whether or not the server has ChestSort.
	- Sort and Organize run entirely via vanilla click packets (QUICK_MOVE / PICKUP), same as before, but no longer fall back to a server payload path.
	- Container filters, autosort, and the item blacklist are all stored and applied locally.
	- Presets and global settings (autosort mode, highlights mode, blacklist) are shared across every world/server you play on.
	- Per-container data (filters, autosort flags, the find index) is now scoped per world/server, so two unrelated worlds that happen to share block coordinates no longer collide.
- Added `/cs presets create`, which opens a GUI with a name field and an items field to build a preset from a pasted item/tag list without hitting chat's text length limit.
- Added `@presetName` as an alias for `&presetName` when searching - typing either now adds/references a preset.
- **Presets can now reference other presets.** The preset editor's search field supports `@`/`&` preset search with autocomplete, same as the container filter UI already did.
- Re-added the full `/cs` command set as client-only commands (never sent to the server): `help`, `autosort`, `highlights`, `tags`, `blacklist`, `scan`, `find`, `presets`, and `wand`.
	- `/cs find` and `/cs scan` now report containers you've personally opened (there's no server to run a world-wide scan for you).
	- `/cs wand paste/clear/autosort/merge` operate over currently loaded chunks, same as before.
	- `/cs wand copy` no longer accepts an explicit block position argument - only "look at the container" is supported.

<div style="background: orange; border-radius: 15px; height: 30px; padding: 0px 10px; box-sizing: border-box; width: min-content; color: white;font-family: system-ui; font-size: 13px; text-align: center; align-content: center; font-weight: 600;">CHANGES</div>
<div style="height: 10px;"></div>

- Double chests are now correctly treated as a single container. Previously each half was tracked separately, so a double chest could end up with two different filters depending on which half you clicked.
- Autosort is now purely a client preference: toggling it on a container no longer depends on any server-side support, and it re-triggers a normal (client-only) sort every time that container is opened.
- The Sort/Organize result summary (left panel) is now generated locally when an action finishes, instead of waiting on a server response.
- The client-only Sort action now re-checks each item right before moving it instead of trusting a snapshot taken before the batch started, fixing a bug where an earlier move's side effects (stack merging) could cause the wrong item to get swept into the container.

<div style="background: red; border-radius: 15px; height: 30px; padding: 0px 10px; box-sizing: border-box; width: min-content; color: white;font-family: system-ui; font-size: 13px; text-align: center; align-content: center; font-weight: 600;">BUGFIXES</div>
<div style="height: 10px;"></div>

- Fixed tag search autocomplete not returning any results in the container filter UI and the preset editor.
- Fixed the preset editor, preset import/export, and preset list screens showing text but no visible buttons or text fields.
- Fixed `/cs presets` commands that open a GUI (create, edit, import, export, exportSelect, exportAll) not actually opening the screen.
- Fixed importing a filter, or adding/removing a preset via the `&`/`@` search shortcut, not saving.
- Fixed a container filter occasionally reverting to a previously opened container's filter after some time, even without closing the chest.

<br>

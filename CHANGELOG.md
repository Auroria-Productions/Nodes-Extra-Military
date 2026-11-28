# Changelog

## 1.3.0 · 28 November 2026

- Improved military GUI: My division shortcut for enlisted citizens and searchable division rosters.
- Stable alphabetical results, case-insensitive matching, paging and clear-search controls; searching never modifies persisted memberships.
- Preserved core commands, original schema-1 data, optional Nodes-Extra logistics and map export.
- Added regression tests for roster filtering and 500-member pagination; live tests are still required for the new controls.


## 1.2.0 · 28 November 2026

- Added an inventory menu for armies, divisions, rosters, enlistment, appointments, ranks, muster, orders and readiness. Names are entered in chat. Disbanding a unit in the menu requires confirmation.
- Made Nodes-Extra optional. When installed, its configured state filename supplies government roles and depot data. When absent, core military functions stay available.
- Fixed depot reports for lost muster territory and depots in the wrong world. Each report searches the friendly graph once and checks sea links against current port groups.
- Moved routine file writes off the server thread, coalesced changes, applied interval changes on reload and kept the original schema-1 save format. Added checks for invalid files and private/public filename collisions.
- Restored buildable Java source from the uploaded 1.0.0 distribution. Added a complete command guide, reproducible build instructions and regression tests.

- Accepts 1.0 key-only ID records and 1.1 inline IDs and lowercase division types, including `reconnaissance`. Preserves private backups, offline appointments, remote territory orders and the public fields used by both builds.
- Built and exercised against the published dev.5.11 core. Two players tested the inventory menus on Paper 1.21.11. Paper 26.3 was tested for startup, save, reload and command chaining.

## 1.1.0 · 24 November 2026

The GUI edition added inventory menus, optional Nodes-Extra integration, rank selection, confirmations and rotating private-state backups. [Original release](https://github.com/Auroria-Productions/Nodes-Extra-Military/releases/tag/v1.1.0)

## 1.0.0 · 24 November 2026

The original addon introduced national armies, six division types, commanders, enlistment, eight ranks, muster locations, strategic orders, Nodes-Extra office and supply lookup, and a public map feed.

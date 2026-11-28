## Nodes Military 1.3.0

Nodes Military is the military-focused successor to the older Nodes-Extra concept. It provides armies, divisions, ranks, orders, supply reporting and a Minecraft inventory GUI for MilzzyNodes.

### New in 1.3.0

- **My division** quick navigation for enlisted citizens.
- **Search personnel** by name inside division rosters, with case-insensitive results, alphabetical sorting, pages and a clear filter action.
- Rank and personnel management returns to the same filtered roster.
- Preserved `/nmilitary`, `/nmil`, `/nodesmilitary` and `/nodes military`, plus schema-1 save compatibility and optional Nodes-Extra integration.

### Upgrade

1. Back up `plugins/Nodes-Military/` and stop your server.
2. Remove the old addon JAR; install the released `Nodes-Military.jar`.
3. Use Paper and Java compatible with your MilzzyNodes core; v1.3.0 targets dev.5.11 and Java 21 bytecode. Install Vault.
4. Restart and use `/nmilitary`. Nodes-Extra 1.1.0 remains optional for depot and government data.

`Nodes-Military-1.3.0-Source.zip` contains the source and reproducible build. Build with `bash build.sh` on JDK 21.

**Testing:** CI validates compile, save-format regression tests, supply calculations and searchable rosters. Interactive game testing of the newly added controls is still recommended.

[Commands and permissions](https://github.com/Auroria-Productions/Nodes-Extra-Military/blob/v1.3.0/README.md#command-index) · [Full changelog](https://github.com/Auroria-Productions/Nodes-Extra-Military/blob/v1.3.0/CHANGELOG.md)

# Nodes Military

Nodes Military is Auroria Productions’ army and division addon for MilzzyNodes. Citizens can enlist, see who commands their division and read its current orders. Leaders can organise formations and set muster points through an in-game inventory menu or chat commands.

[Download Nodes-Military.jar](https://github.com/Auroria-Productions/Nodes-Extra-Military/releases/download/v1.3.0/Nodes-Military.jar) · [Download the source ZIP](https://github.com/Auroria-Productions/Nodes-Extra-Military/releases/download/v1.3.0/Nodes-Military-1.3.0-Source.zip) · [Release notes](https://github.com/Auroria-Productions/Nodes-Extra-Military/releases/tag/v1.3.0)

## What the addon includes

| System | In game |
| --- | --- |
| Military menu | Open your nation's armies, select a division and inspect its roster. The menu shows actions available to you. Leaders choose formation types, create units and manage personnel. |
| Enlistment and ranks | Each citizen can enlist in one division across all nations. Commanders can appoint officers and assign one of eight ranks. |
| Muster and orders | Set an army's muster in national territory. Give a division a recorded hold, move or retreat order with a friendly territory as its target. Players carry out the orders in game. |
| Readiness | See personnel, divisions and muster location. With Nodes-Extra installed, check reachable depots, distance and stock. |
| Administration and map data | Staff can read the event history, save and reload configuration. A separate public JSON feed exposes unit headcounts and orders. Player identities and ranks stay in the private save. |

## Requirements and installation

1. Run Paper with the Java version required by your server. Nodes Military targets Java 21 bytecode and Bukkit API 1.21. The preceding v1.2.0 release was checked on Paper **1.21.11 build 132** and **26.3 build 256** with Java 25. The v1.3.0 GUI improvements have automated regression coverage but still require a staging-server check.
2. Install **Vault** and **MilzzyNodes dev.5.11** for the core. Install **Nodes-Extra 1.1.0** if you want depot reports or authority from a government office. Army management, rosters and orders work with the core alone.
3. Stop the server. For an upgrade from Nodes-Military 1.0.0, 1.1.0 or 1.2.0, back up `plugins/Nodes-Military/` and replace the old addon JAR. Keep the data directory and a single copy of the addon in `plugins/`.
4. Start Paper. Run `nmilitary help` in the console. In game, join a Nodes nation and enter `/nmilitary` to open its menu.

[MilzzyNodes core release](https://github.com/Auroria-Productions/Auroria-Nodes/releases/tag/v1.21-26.3-core-dev.5.11) · [Optional Nodes-Extra addon](https://github.com/Auroria-Productions/Nodes-Extra-) · [Default configuration](src/main/resources/config.yml) · [Plugin descriptor](src/main/resources/plugin.yml)

## Using the menu

- **Create a formation.** Open `/nmilitary` and select **Create army**. Enter its name in chat. Open the army, select **Create division**, choose its type and enter the division name. Type `cancel` to exit a naming prompt.
- **Enlist.** Select an army and division, then **Enlist**. Select a member to see the rank actions available to you. You can appoint a citizen who is offline. A division commander must already belong to that division.
- **Set a location.** Stand in a territory your nation owns in the configured Nodes world. Use **Set muster here** on the army screen or **Issue order** on the division screen. Both the menu and chat commands accept a distant friendly territory ID.
- **Jump to your division.** Enlisted citizens can select **My division** on the army menu to open their own division roster, rank and orders immediately.
- **Search a large roster.** In a division menu, select **Search personnel** and type part of a player name in chat. Matching names are sorted alphabetically, and the filter stays active while paging or opening a personnel record. Select **Clear roster search** to show everyone again. Browsing never changes stored enlistments.
- **Review readiness.** Select an army and open **Readiness report**. Use the arrows to change pages or return to the previous screen. The menu asks for confirmation before a unit is disbanded.

## Command index

Every action below accepts all four prefixes. For example, `/nmilitary overview`, `/nmil overview`, `/nodesmilitary overview` and `/nodes military overview` show the same list. The `/nodes military` prefix uses the core's existing command handler. If another plugin owns that command, use one of the three direct prefixes.

### Players and citizens

| Command | What it does | Who can use it |
| --- | --- | --- |
| `/nmilitary` | Open the menu by default. Console shows help. | Players or console |
| `/nmilitary help` | Show commands and the staff actions available to you. | Players or console |
| `/nmilitary gui` or `menu` | Open the inventory menu explicitly. | Players |
| `/nmilitary overview` or `list` | List the nation's armies and their strength. | Citizens |
| `/nmilitary army` or `army list` | Open the same army list in chat. | Citizens |
| `/nmilitary army info <army>` | Show its commander, muster point and divisions. | Citizens |
| `/nmilitary division roster <army> <division>` | List enlisted members, ranks and commander. | Citizens |
| `/nmilitary enlist <army> <division>` or `join <army> <division>` | Enlist as a private. | Citizens |
| `/nmilitary leave` | Leave your current division, including after a change of nation. | Enlisted players |
| `/nmilitary report <army>` or `supply <army>` | Show strength, muster location and available depot information. | Citizens |

### Formations and command

| Command | What it does | Required authority |
| --- | --- | --- |
| `/nmilitary army create <name>` | Create an army. The founder becomes its commander. | National |
| `/nmilitary army commander <army> <player>` | Appoint a citizen as army commander. | National |
| `/nmilitary army disband <army>` | Remove the army, its divisions and their enlistments. | Army command |
| `/nmilitary division create <army> <division> <type>` | Create a division of the chosen type. | Army command |
| `/nmilitary division commander <army> <division> <player>` | Appoint an enlisted citizen as commander. The appointee receives at least captain rank. | Army command |
| `/nmilitary division rank <army> <division> <player> <rank>` | Change an enlisted citizen's rank, including while offline. | Division command |
| `/nmilitary division disband <army> <division>` | Remove a division and release its members. | Army command |
| `/nmilitary muster <army>` | Set the army's muster at your current owned territory. | Army command |
| `/nmilitary order <army> <division> <order> <territory-id>` | Record an order targeting a territory owned by your nation. | Division command |

**National authority** includes the capital sovereign, the leaders, officers and generals of the nation's towns, and holders of `nodesmilitary.admin` within their nation. When Nodes-Extra is enabled its government head, Marshal and `nodesextra.admin` also qualify. **Army command** includes the army commander and national authorities. **Division command** also includes that division's commander.

| Argument | Accepted values |
| --- | --- |
| Army and division names | 2–24 letters, numbers, underscores or hyphens. Name lookups ignore case. |
| Division type | `infantry`, `cavalry`, `artillery`, `engineers`, `naval`, `recon` (also `reconnaissance`) |
| Rank | `private`, `corporal`, `sergeant`, `lieutenant`, `captain`, `major`, `colonel`, `general` |
| Order | `hold`, `move`, `retreat` |

### Staff

| Command | What it does |
| --- | --- |
| `/nmilitary history` | Show the latest 12 events from the 200 retained in the private save. |
| `/nmilitary save` | Write private state and refresh the configured public feed. Waits for the write to finish. |
| `/nmilitary reload` | Reload settings and reschedule periodic saving. Changes to JSON filenames require a stopped-server migration. |

`nodesmilitary.use` defaults to everyone. Staff commands require `nodesmilitary.admin`, which defaults to operators. Staff commands work from the server console.

## Configuration and saved data

| Configuration | Default | Effect |
| --- | --- | --- |
| `gui.enabled` | `true` | Enable the inventory interface. All chat actions are available independently. |
| `gui.title` | `Nodes Military` | Title of the army browser. |
| `gui.openOnCommand` | `true` | Open the menu with a bare command. Set to `false` for text help by default. |
| `military.requireMarshalAuthority` | `true` | Require national leadership for national management. Set to `false` to extend that authority to all citizens. |
| `military.maxArmiesPerNation` | `6` | Maximum armies per nation, from 1 to 50. |
| `military.maxDivisionsPerArmy` | `8` | Maximum divisions per army, from 1 to 30. |
| `military.maxMembersPerDivision` | `50` | Maximum enlisted members per division, from 1 to 500. |
| `military.supply.maxTerritoryHops` | `12` | Maximum route length to a depot, from 1 to 50. |
| `map.exportPublicData` | `true` | Generate the public JSON feed. Turning this off removes the previously generated feed on save. |
| `storage.saveIntervalSeconds` | `120` | Periodic retry and nation-name refresh, from 20 to 3,600 seconds. Mutations also queue a save. |
| `storage.stateFilename` | `state.json` | Private armies, player UUIDs, ranks, orders and audit events. |
| `storage.publicFilename` | `public.json` | Nation and army identifiers, muster location, division headcounts and orders for map integrations. |

The previous distinct private save is retained as `state.json.bak` when using the default filename. Both JSON files live in `plugins/Nodes-Military/`. Only the public feed is intended for a web viewer. Displaying it on NodesMap requires a viewer that reads this feed. Existing 1.0.0 configurations use the new GUI defaults when the two GUI keys are absent.

### Reading a supply report

The report searches from the muster point along connected, nationally owned territory, including validated sea links between friendly ports that share a group. Of reachable, unblocked depots with stock, it selects the nearest. At equal distance it prefers a depot with both food and equipment.

| Status | Meaning |
| --- | --- |
| `SUPPLIED` | The selected depot has food and equipment. |
| `LIMITED` | The selected depot has stock but lacks food or equipment. |
| `EMPTY` | A reachable depot is open and empty, and none can supply the army. |
| `BLOCKADED` | All reachable depots are under blockade. |
| `UNSUPPLIED` | No eligible depot is reachable, or the nation has lost the muster territory. |

Nodes Military reads the Nodes-Extra state file named in its configuration. If the report is waiting for the first snapshot, run `/nextra save` as an admin and allow up to five seconds for refresh. The check leaves supplies in the depot.

## Building from source

Use JDK 21, Bash, curl, unzip, Python 3 and `sha256sum`. Extract the named source ZIP or clone the repository, then run the following in its root directory.

```sh
bash build.sh
```

The build fetches a pinned core JAR and compile libraries from Auroria Nodes, verifies their SHA-256 digests, compiles the addon, runs the regression tests and writes `dist/Nodes-Military.jar`. To build offline, set `NODES_JAR` and `NODES_COMPILE_LIBS` to absolute paths to your local copies. The release JAR contains the addon classes and its Paper resources.

The Maven entry point, `mvn package`, runs the same build and places the verified JAR in `target/Nodes-Military.jar`. The release source was reconstructed from the supplied 1.0.0 distribution and checked against the subsequent 1.1.0 source.

[Java source](src/main/java/com/auroria/nodesmilitary) · [Regression tests](src/test/java/com/auroria/nodesmilitary/MilitaryTests.java) · [Test results](TEST_REPORT.md) · [Changelog](CHANGELOG.md) · [GPLv3 license](LICENSE) · [Archived Nodes-Extra package](https://github.com/Auroria-Productions/Nodes-Extra-Military/tree/legacy/nodes-extra)

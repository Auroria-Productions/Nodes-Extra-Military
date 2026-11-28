# Nodes Military 1.3.0 verification

## 1.3.0 automated checks

The regression suite covers case-insensitive roster search, stable alphabetical pagination, 500-member rosters, and non-destructive browsing, in addition to schema-1 save compatibility, ordered writes, private/public data separation, depot reachability and office authority.

GitHub Actions also builds with the published MilzzyNodes dev.5.11 compile dependencies and verifies the Maven and shell build JARs match. A passing CI run confirms compilation and automated tests, not a live GUI test.

## Previously tested in-game with Nodes Military 1.2.0

| Environment | Checks completed |
| --- | --- |
| JDK 21 and the dev.5.11 core API | Compiled the release plugin. Tested both established schema-1 formats, backup rotation, collision checks, corrupt input, the public feed, roster pagination, supply distances, shared-port routes and government office lookup. |
| Paper 1.21.11 build 132, Java 25, Vault and MilzzyNodes dev.5.11 | Two game clients used the actual chest GUI to create an army and division, enlist, appoint a commander, set ranks, enter an order by territory ID and cancel a withdrawal. Also checked the command aliases, a forbidden command, a foreign-territory order and a supply report with Nodes-Extra absent. |
| Same server, restarted with Nodes-Extra 1.1.0 | Checked persisted rosters, appointment of an offline citizen, both reconnaissance spellings, a depot report using a custom state filename, government-head authority, and the disband confirmation. |
| Paper 26.3 build 256, Java 25, Vault, core and Nodes-Extra | A cold start loaded an army in the published 1.1 format. Checked console help, save, reload, history, core command forwarding and the Nodes-Extra command handler. |

Interactive player checks ran on 1.21.11. The 26.3 checks covered the server lifecycle and commands. Production saves and the live server were outside these tests.


The v1.3.0 GUI shortcuts and roster search have not yet been retested with actual Minecraft clients.

package com.auroria.nodesmilitary;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.logging.Logger;

public final class MilitaryTests {

    private static final UUID NATION = UUID.fromString("461c9af5-c410-468d-a245-6d86737d8186");

    private static final UUID PLAYER = UUID.fromString("62a71993-f54d-4a62-b430-af1148a2b301");

    private static final Logger LOG = Logger.getLogger("Military tests");

    // This is the JSON shape written by the 1.0.0 serializer.
    private static final String LEGACY = """
        {"schema":1,"armies":{"%1$s:north":{"name":"North","nationId":"%1$s","commanderUuid":"",
        "createdBy":"%2$s","createdAt":123,"musterWorld":"world","musterTerritoryId":7,
        "divisions":{"guard":{"name":"Guard","type":"INFANTRY","commanderUuid":"%2$s",
        "members":["%2$s"],"ranks":{"%2$s":"CAPTAIN"},"order":"HOLD","targetTerritoryId":8,
        "orderUpdatedAt":124}}}},"history":["Private audit example"]}
        """.formatted(NATION, PLAYER);

    private interface IOAction {

        void run() throws IOException;
    }

    private static void rejected(IOAction action) throws IOException {
        try {
            action.run();
        } catch (IOException expected) {
            return;
        }
        throw new AssertionError("Expected invalid input to be rejected");
    }

    private static void persistence(Path dir) throws IOException {
        Path statePath = dir.resolve("state.json"), feedPath = dir.resolve("public.json");
        Files.writeString(statePath, LEGACY);
        try (Storage store = new Storage(dir, "state.json", "public.json", LOG)) {
            MilitaryState state = store.load();
            var army = state.armies(NATION).getFirst();
            var guard = state.membership(PLAYER);
            assert army.createdBy.equals(PLAYER) && army.commanderUuid == null && army.musterTerritoryId == 7;
            assert guard == army.divisions.get("guard") && guard.rank(PLAYER) == MilitaryState.Rank.CAPTAIN;
            assert guard.commanderUuid.equals(PLAYER) && guard.orderUpdatedAt == 124;
            assert army.id.equals(NATION + ":north") && guard.id.equals("guard");
            // The GUI Edition changed to lowercase types and stored inline IDs.
            JsonObject published = JsonParser.parseString(LEGACY).getAsJsonObject();
            var indexedArmy = published.getAsJsonObject("armies").getAsJsonObject(NATION + ":north");
            indexedArmy.addProperty("id", NATION + ":north");
            var indexedUnit = indexedArmy.getAsJsonObject("divisions").getAsJsonObject("guard");
            indexedUnit.addProperty("id", "guard");
            for (var type : MilitaryState.Type.values()) {
                String wireName = type == MilitaryState.Type.RECON ? "reconnaissance" : type.name().toLowerCase(Locale.ROOT);
                indexedUnit.addProperty("type", wireName);
                Files.writeString(statePath, published.toString());
                assert store.load().membership(PLAYER).type == type;
            }
            indexedUnit.addProperty("id", "elsewhere");
            Files.writeString(statePath, published.toString());
            rejected(store::load);
            Files.writeString(statePath, LEGACY);

            store.submit(new Storage.Snapshot(0, Storage.JSON.toJsonTree(state), null));
            store.await(0);
            Path backup = dir.resolve("state.json.bak");
            assert Files.readString(backup).equals(LEGACY);
            store.submit(new Storage.Snapshot(0, Storage.JSON.toJsonTree(state), null));
            store.await(0);
            assert Files.readString(backup).equals(LEGACY);
            for (int revision = 1; revision <= 30; revision++) {
                guard.targetTerritoryId = revision;
                store.submit(new Storage.Snapshot(revision, Storage.JSON.toJsonTree(state), Storage.publicFeed(state, Map.of(NATION, "Ochre"))));
            }
            store.await(30);
            var restored = store.load();
            assert restored.membership(PLAYER).targetTerritoryId == 30;
            assert restored.history.equals(List.of("Private audit example"));
            String text = Files.readString(feedPath);
            assert !text.contains(PLAYER.toString()) && !text.contains("Private audit") && !text.contains("members") && !text.contains("ranks");
            var publicArmy = JsonParser.parseString(text).getAsJsonObject().getAsJsonArray("armies").get(0).getAsJsonObject();
            assert publicArmy.get("nation").getAsString().equals("Ochre");
            assert publicArmy.get("id").getAsString().equals(NATION + ":north") && publicArmy.get("headcount").getAsInt() == 1;
            var division = publicArmy.getAsJsonArray("divisions").get(0).getAsJsonObject();
            assert division.get("orderUpdatedAt").getAsLong() == 124 && division.get("headcount").getAsInt() == 1 && division.get("strength").getAsInt() == 1 && division.get("targetTerritoryId").getAsInt() == 30;
            assert Storage.publicFeed(state, Map.of()).getAsJsonObject().getAsJsonArray("armies").isEmpty();
            store.submit(new Storage.Snapshot(31, Storage.JSON.toJsonTree(state), null));
            store.await(31);
            assert !Files.exists(feedPath) && store.load().membership(PLAYER) != null;
            var duplicate = JsonParser.parseString(LEGACY).getAsJsonObject();
            var divisions = duplicate.getAsJsonObject("armies").getAsJsonObject(NATION + ":north").getAsJsonObject("divisions");
            var other = divisions.getAsJsonObject("guard").deepCopy();
            other.addProperty("name", "Second");
            divisions.add("second", other);
            for (String bad : List.of(LEGACY.replace("\"schema\":1", "\"schema\":2"), LEGACY.replace("CAPTAIN", "INVALID"), duplicate.toString(), "{\"armies\":")) {
                Files.writeString(statePath, bad);
                rejected(store::load);
                assert Files.readString(statePath).equals(bad);
            }
        }
        rejected(() -> new Storage(dir, "state.json", "STATE.json", LOG));
        rejected(() -> Storage.jsonFile(dir, "../state.json"));
    }

    private static Map<Integer, Set<Integer>> graph() {
        Map<Integer, Set<Integer>> graph = new HashMap<>();
        graph.put(1, new HashSet<>(Set.of(2)));
        graph.put(2, new HashSet<>(Set.of(1, 3)));
        graph.put(3, new HashSet<>(Set.of(2)));
        graph.put(4, new HashSet<>());
        return graph;
    }

    private static ExtraBridge.Depot depot(String name, String world, int id, int food, int equipment, long until) {
        return new ExtraBridge.Depot(name, NATION.toString(), world, id, food, equipment, 0, until);
    }

    private static Supply.Result route(int muster, int hops, List<ExtraBridge.Depot> depots, List<ExtraBridge.Link> links, Map<Integer, Set<String>> ports) {
        return Supply.evaluate(graph(), ports, new ExtraBridge.Snapshot(null, Map.of(), depots, links), NATION, "world", muster, hops, 10);
    }

    private static void supplyAndOffices() throws IOException {
        var full = depot("Full", "world", 3, 2, 3, 0);
        var partial = depot("Partial", "world", 2, 0, 2, 0);
        assert route(1, 2, List.of(full, partial), List.of(), Map.of()).status() == Supply.Status.LIMITED;
        var nearest = route(1, 2, List.of(partial, depot("Both", "world", 2, 1, 1, 0)), List.of(), Map.of());
        assert nearest.status() == Supply.Status.SUPPLIED && nearest.hops() == 1 && nearest.depot().name().equals("Both");
        assert route(1, 1, List.of(full), List.of(), Map.of()).status() == Supply.Status.UNSUPPLIED;
        assert route(7, 2, List.of(depot("Lost", "world", 7, 1, 1, 0)), List.of(), Map.of()).status() == Supply.Status.UNSUPPLIED;
        assert route(1, 2, List.of(depot("Other", "nether", 1, 1, 1, 0)), List.of(), Map.of()).status() == Supply.Status.UNSUPPLIED;
        assert route(1, 2, List.of(depot("Blocked", "world", 1, 1, 1, 11)), List.of(), Map.of()).status() == Supply.Status.BLOCKADED;
        assert route(1, 2, List.of(depot("Empty", "world", 1, 0, 0, 0)), List.of(), Map.of()).status() == Supply.Status.EMPTY;
        var link = new ExtraBridge.Link(NATION.toString(), "world", "SEA", 1, 4);
        var port = List.of(depot("Harbour", "world", 4, 2, 1, 0));
        assert route(1, 1, port, List.of(link), Map.of(1, Set.of("red"), 4, Set.of("blue"))).status() == Supply.Status.UNSUPPLIED;
        assert route(1, 1, port, List.of(link), Map.of(1, Set.of("red"), 4, Set.of("red"))).hops() == 1;
        var foreign = new ExtraBridge.Link(PLAYER.toString(), "world", "SEA", 1, 4);
        assert route(1, 1, port, List.of(foreign), Map.of(1, Set.of("red"), 4, Set.of("red"))).status() == Supply.Status.UNSUPPLIED;
        JsonObject fixture = JsonParser.parseString("""
            {"schema":1,"governments":{"%s":{"offices":{"MARSHAL":"%s"}}},
             "depots":{"dock":{"nationId":"%s","territoryId":4,"world":"world","food":2}},
             "links":{"port":{"nationId":"%s","world":"world","type":"SEA","from":1,"to":4}}}
            """.formatted(NATION, PLAYER, NATION, NATION)).getAsJsonObject();
        var parsed = ExtraBridge.parse(fixture);
        assert parsed.appointed(PLAYER, NATION) && !parsed.appointed(PLAYER, PLAYER);
        assert parsed.depots().getFirst().food() == 2 && parsed.links().getFirst().from() == 1;
        fixture.addProperty("schema", 2);
        rejected(() -> ExtraBridge.parse(fixture));
    }

    private static void searchableRosters() {
        var division = new MilitaryState.Division();
        UUID bob = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID alice = UUID.fromString("00000000-0000-0000-0000-000000000002");
        UUID alana = UUID.fromString("00000000-0000-0000-0000-000000000003");
        division.members.addAll(List.of(bob, alana, alice));
        Map<UUID, String> names = Map.of(bob, "Bob", alice, "Alice", alana, "Alana");
        assert MilitaryState.roster(division, names::get, "").equals(List.of(alana, alice, bob));
        assert MilitaryState.roster(division, names::get, " AL ").equals(List.of(alana, alice));
        assert MilitaryState.roster(division, names::get, "BOB").equals(List.of(bob));
        assert MilitaryState.roster(division, names::get, "nonexistent").isEmpty();
        assert MilitaryState.roster(division, names::get, null).size() == 3;
        assert division.members.equals(List.of(bob, alana, alice)); // browsing never mutates the save
        for (int i = 0; i < 500; i++)
            division.members.add(UUID.nameUUIDFromBytes(("extra-" + i).getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        assert MilitaryState.roster(division, names::get, "").size() == 503;
        assert Menus.page(12, 503) == 11;
    }

    public static void main(String[] args) throws Exception {
        if (!MilitaryTests.class.desiredAssertionStatus())
            throw new IllegalStateException("Run tests with -ea");
        Path dir = Files.createTempDirectory("military-tests-");
        try {
            persistence(dir);
            supplyAndOffices();
            searchableRosters();
        } finally {
            try (var paths = Files.walk(dir)) {
                for (var p : paths.sorted(Comparator.reverseOrder()).toList()) Files.delete(p);
            }
        }
        assert Menus.page(5, 0) == 0 && Menus.page(1, 45) == 0 && Menus.page(2, 46) == 1 && Menus.page(-1, 90) == 0;
        System.out.println("Passed schema-1 upgrade, ordered writes, public feed privacy, invalid saves, authority, supply routes and searchable GUI rosters and pagination.");
    }
}

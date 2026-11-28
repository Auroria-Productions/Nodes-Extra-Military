package com.auroria.nodesmilitary;

import com.google.gson.*;
import org.bukkit.event.*;
import org.bukkit.event.server.*;
import org.bukkit.plugin.java.JavaPlugin;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.*;
import java.util.*;
import java.util.logging.Logger;

final class ExtraBridge implements Listener {

    record Office(String head, String marshal) {
    }

    record Depot(String name, String nationId, String world, int territoryId, int food, int equipment, int fuel, long blockadedUntil) {
    }

    record Link(String nationId, String world, String type, int from, int to) {
    }

    record Snapshot(String problem, Map<String, Office> offices, List<Depot> depots, List<Link> links) {

        static Snapshot unavailable(String reason) {
            return new Snapshot(reason, Map.of(), List.of(), List.of());
        }

        boolean appointed(UUID player, UUID nation) {
            Office o = offices.get(nation.toString());
            return o != null && (player.toString().equals(o.head()) || player.toString().equals(o.marshal()));
        }
    }

    private static final Snapshot ABSENT = Snapshot.unavailable("Nodes-Extra is not installed or enabled.");

    private static final class Source {

        final Path path;

        volatile Snapshot snapshot = Snapshot.unavailable("Waiting for the first Nodes-Extra snapshot.");

        FileTime lastModified;

        long length = -1;

        Object fileKey;

        Source(Path path) {
            this.path = path;
        }

        synchronized void read(Logger logger) {
            try {
                BasicFileAttributes a = Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
                if (a.lastModifiedTime().equals(lastModified) && a.size() == length && Objects.equals(fileKey, a.fileKey()))
                    return;
                Snapshot updated = parse(Storage.readObject(path, 15_000_000));
                snapshot = updated;
                lastModified = a.lastModifiedTime();
                length = a.size();
                fileKey = a.fileKey();
            } catch (IOException | RuntimeException ex) {
                String problem = ex instanceof NoSuchFileException ? "Waiting for Nodes-Extra to save its state." : "Cannot read Nodes-Extra state: " + ex.getMessage();
                if (!problem.equals(snapshot.problem()) && !(ex instanceof NoSuchFileException))
                    logger.warning(problem);
                snapshot = Snapshot.unavailable(problem);
                lastModified = null;
            }
        }
    }

    private final MilitaryPlugin plugin;

    private volatile Source source;

    ExtraBridge(MilitaryPlugin plugin) {
        this.plugin = plugin;
    }

    void bind() {
        var extra = plugin.getServer().getPluginManager().getPlugin("Nodes-Extra");
        if (!(extra instanceof JavaPlugin jp) || !jp.isEnabled()) {
            source = null;
            return;
        }
        try {
            source = new Source(Storage.jsonFile(jp.getDataFolder().toPath(), jp.getConfig().getString("storage.stateFilename", "state.json")));
        } catch (IOException e) {
            source = null;
            plugin.getLogger().warning(e.getMessage());
        }
    }

    @EventHandler
    public void enabled(PluginEnableEvent e) {
        if (e.getPlugin().getName().equalsIgnoreCase("Nodes-Extra"))
            bind();
    }

    @EventHandler
    public void disabled(PluginDisableEvent e) {
        if (e.getPlugin().getName().equalsIgnoreCase("Nodes-Extra"))
            source = null;
    }

    void refresh() {
        Source active = source;
        if (active != null)
            active.read(plugin.getLogger());
    }

    boolean installed() {
        return source != null;
    }

    Snapshot snapshot() {
        Source active = source;
        return active == null ? ABSENT : active.snapshot;
    }

    static Snapshot parse(JsonObject json) throws IOException {
        if (!json.has("schema") || !json.get("schema").getAsJsonPrimitive().isNumber() || json.get("schema").getAsBigDecimal().intValueExact() != 1)
            throw new IOException("Unsupported Nodes-Extra schema");
        Map<String, Office> offices = new HashMap<>();
        List<Depot> depots = new ArrayList<>();
        List<Link> links = new ArrayList<>();
        for (var e : object(json, "governments").entrySet()) {
            JsonObject gov = e.getValue().getAsJsonObject();
            offices.put(e.getKey(), new Office(str(gov, "head"), str(object(gov, "offices"), "MARSHAL")));
        }
        for (var e : object(json, "depots").entrySet()) {
            JsonObject d = e.getValue().getAsJsonObject();
            depots.add(new Depot(d.has("name") ? str(d, "name") : e.getKey(), str(d, "nationId"), str(d, "world"), integer(d, "territoryId", -1), integer(d, "food", 0), integer(d, "equipment", 0), integer(d, "fuel", 0), number(d, "blockadedUntil", 0)));
        }
        for (var e : object(json, "links").entrySet()) {
            JsonObject link = e.getValue().getAsJsonObject();
            links.add(new Link(str(link, "nationId"), str(link, "world"), str(link, "type"), integer(link, "from", -1), integer(link, "to", -1)));
        }
        return new Snapshot(null, Map.copyOf(offices), List.copyOf(depots), List.copyOf(links));
    }

    private static JsonObject object(JsonObject o, String key) {
        return o.has(key) ? o.getAsJsonObject(key) : new JsonObject();
    }

    private static String str(JsonObject o, String key) {
        return !o.has(key) || o.get(key).isJsonNull() ? "" : o.get(key).getAsString();
    }

    private static long number(JsonObject o, String key, long fallback) {
        return o.has(key) ? o.get(key).getAsLong() : fallback;
    }

    private static int integer(JsonObject o, String key, int fallback) {
        return Math.toIntExact(number(o, key, fallback));
    }
}

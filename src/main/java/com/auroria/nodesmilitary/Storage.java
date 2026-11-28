package com.auroria.nodesmilitary;

import com.google.gson.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.Level;
import java.util.logging.Logger;

final class Storage implements AutoCloseable {

    static final Gson JSON = new GsonBuilder().serializeNulls().disableHtmlEscaping().create();

    private static final long MAX_BYTES = 20_000_000;

    private final Path stateFile;

    private final Path publicFile;

    private final Logger log;

    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "Nodes-Military saves");
        t.setDaemon(true);
        return t;
    });

    private Snapshot pending;

    private Future<?> task;

    private boolean running;

    private volatile long savedRevision = -1;

    private volatile IOException lastFailure;

    record Snapshot(long revision, JsonElement privateData, JsonElement publicData) {
    }

    Storage(Path root, String stateName, String publicName, Logger log) throws IOException {
        stateFile = jsonFile(root, stateName);
        publicFile = jsonFile(root, publicName);
        if (stateName.equalsIgnoreCase(publicName) || (Files.exists(stateFile) && Files.exists(publicFile) && Files.isSameFile(stateFile, publicFile))) {
            throw new IOException("Private state and public export must use different files.");
        }
        this.log = log;
    }

    static Path jsonFile(Path root, String name) throws IOException {
        if (name == null || !name.matches("[A-Za-z0-9_-][A-Za-z0-9_.-]*\\.json")) {
            throw new IOException("Invalid JSON filename: " + name);
        }
        Path file = root.resolve(name);
        if (Files.isSymbolicLink(file))
            throw new IOException("A JSON path is a symbolic link: " + file);
        return file;
    }

    MilitaryState load() throws IOException {
        if (Files.notExists(stateFile))
            return new MilitaryState();
        try {
            JsonObject root = readObject(stateFile, MAX_BYTES);
            require(root.has("schema") && root.get("schema").isJsonPrimitive() && root.get("schema").getAsJsonPrimitive().isNumber() && root.get("schema").getAsBigDecimal().intValueExact() == 1, "Unsupported state schema");
            require(root.get("armies") instanceof JsonObject, "Missing armies object");
            for (var entry : root.getAsJsonObject("armies").entrySet()) {
                JsonObject army = entry.getValue().getAsJsonObject();
                normalizeId(army, entry.getKey());
                normalizeOptionalUuid(army, "commanderUuid");
                normalizeOptionalUuid(army, "createdBy");
                require(army.get("divisions") instanceof JsonObject, "Missing divisions in " + entry.getKey());
                for (var unit : army.getAsJsonObject("divisions").entrySet()) {
                    JsonObject d = unit.getValue().getAsJsonObject();
                    normalizeId(d, unit.getKey());
                    if (d.has("type")) d.addProperty("type", MilitaryState.divisionType(d.get("type").getAsString()).name());
                    require(d.get("members") instanceof JsonArray, "Missing division members");
                    normalizeOptionalUuid(d, "commanderUuid");
                }
            }
            MilitaryState state = JSON.fromJson(root, MilitaryState.class);
            Set<UUID> enlisted = new HashSet<>();
            for (var e : state.armies.entrySet()) {
                var army = e.getValue();
                require(army.nationId != null && validName(army.name) && e.getKey().equals(MilitaryState.armyKey(army.nationId, army.name)), "Invalid army " + e.getKey());
                for (var item : army.divisions.entrySet()) {
                    var d = item.getValue();
                    require(validName(d.name) && item.getKey().equals(MilitaryState.key(d.name)) && d.type != null && d.order != null && d.ranks != null, "Invalid division " + item.getKey());
                    require(d.members.stream().allMatch(id -> id != null && enlisted.add(id)), "Invalid or duplicate enlistment in " + d.name);
                    require(d.ranks.values().stream().noneMatch(Objects::isNull) && d.members.containsAll(d.ranks.keySet()), "Invalid rank assignment in " + d.name);
                    require(d.commanderUuid == null || d.members.contains(d.commanderUuid), "Division commander is not enlisted in " + d.name);
                }
            }
            require(state.history != null && state.history.stream().noneMatch(Objects::isNull), "Invalid history");
            return state;
        } catch (RuntimeException ex) {
            throw new IOException("Cannot load " + stateFile.getFileName() + ": " + ex.getMessage(), ex);
        }
    }

    // 1.0 used map keys as IDs. 1.1 also wrote them in each record.
    private static void normalizeId(JsonObject record, String key) throws IOException {
        if (record.has("id") && !record.get("id").isJsonNull())
            require(key.equals(record.get("id").getAsString()), "ID differs from storage key " + key);
        record.addProperty("id", key);
    }

    private static boolean validName(String s) {
        return s != null && MilitaryState.NAME.matcher(s).matches();
    }

    private static void normalizeOptionalUuid(JsonObject obj, String key) {
        JsonElement value = obj.get(key);
        if (value != null && value.isJsonPrimitive() && value.getAsString().isBlank())
            obj.add(key, JsonNull.INSTANCE);
    }

    private static void require(boolean ok, String error) throws IOException {
        if (!ok)
            throw new IOException(error);
    }

    static JsonObject readObject(Path file, long limit) throws IOException {
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS))
            throw new IOException("Expected a regular file: " + file.getFileName());
        long bytes = Files.size(file);
        if (bytes < 2 || bytes > limit)
            throw new IOException("Unexpected file size for " + file.getFileName());
        try (var in = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            var reader = new com.google.gson.stream.JsonReader(in);
            reader.setLenient(false);
            JsonElement parsed = JSON.getAdapter(JsonElement.class).read(reader);
            if (reader.peek() != com.google.gson.stream.JsonToken.END_DOCUMENT || !parsed.isJsonObject())
                throw new IOException("Expected one JSON object in " + file.getFileName());
            return parsed.getAsJsonObject();
        } catch (RuntimeException ex) {
            throw new IOException("Invalid JSON in " + file.getFileName(), ex);
        }
    }

    static JsonElement publicFeed(MilitaryState state, Map<UUID, String> nationNames) {
        JsonObject out = new JsonObject();
        out.addProperty("schema", 1);
        out.addProperty("generatedAt", System.currentTimeMillis());
        JsonArray armies = new JsonArray();
        for (var army : state.armies.values()) {
            String nation = nationNames.get(army.nationId);
            if (nation == null)
                continue;
            JsonObject a = new JsonObject();
            a.addProperty("id", army.id);
            a.addProperty("headcount", army.strength());
            a.addProperty("name", army.name);
            a.addProperty("nation", nation);
            a.addProperty("nationId", army.nationId.toString());
            a.addProperty("musterWorld", Objects.toString(army.musterWorld, ""));
            a.addProperty("musterTerritoryId", army.musterTerritoryId);
            JsonArray divisions = new JsonArray();
            for (var d : army.divisions.values()) {
                JsonObject row = new JsonObject();
                row.addProperty("name", d.name);
                row.addProperty("type", d.type.name());
                row.addProperty("strength", d.members.size());
                row.addProperty("headcount", d.members.size());
                row.addProperty("orderUpdatedAt", d.orderUpdatedAt);
                row.addProperty("order", d.order.name());
                row.addProperty("targetTerritoryId", d.targetTerritoryId);
                divisions.add(row);
            }
            a.add("divisions", divisions);
            armies.add(a);
        }
        out.add("armies", armies);
        return out;
    }

    // The main thread freezes each snapshot before passing it to the single ordered writer.
    synchronized void submit(Snapshot next) {
        pending = next;
        if (!running) {
            running = true;
            task = worker.submit(this::drain);
        }
    }

    private void drain() {
        while (true) {
            Snapshot next;
            synchronized (this) {
                next = pending;
                pending = null;
                if (next == null) {
                    running = false;
                    return;
                }
            }
            try {
                write(stateFile, next.privateData(), true);
                if (next.publicData() != null)
                    write(publicFile, next.publicData(), false);
                else
                    removeExport();
                savedRevision = next.revision();
                lastFailure = null;
            } catch (IOException | RuntimeException e) {
                lastFailure = new IOException("Military save failed: " + e.getMessage(), e);
                log.log(Level.SEVERE, lastFailure.getMessage(), e);
            }
        }
    }

    private void removeExport() throws IOException {
        if (Files.notExists(publicFile))
            return;
        JsonObject previous = readObject(publicFile, MAX_BYTES);
        if (previous.has("generatedAt") && previous.get("armies") instanceof JsonArray)
            Files.delete(publicFile);
    }

    private static void write(Path target, JsonElement snapshot, boolean backup) throws IOException {
        Files.createDirectories(target.getParent());
        String data = JSON.toJson(snapshot);
        byte[] bytes = data.getBytes(StandardCharsets.UTF_8);
        require(bytes.length <= MAX_BYTES, "Save exceeds supported size: " + target.getFileName());
        Path tmp = Files.createTempFile(target.getParent(), "." + target.getFileName(), ".tmp");
        try {
            Files.write(tmp, bytes);
            if (backup && Files.exists(target)) {
                if (Files.mismatch(tmp, target) == -1) return;
                Path previous = Files.createTempFile(target.getParent(), ".previous-", ".tmp");
                try {
                    Files.copy(target, previous, StandardCopyOption.REPLACE_EXISTING);
                    replace(previous, target.resolveSibling(target.getFileName() + ".bak"));
                } finally {
                    Files.deleteIfExists(previous);
                }
            }
            replace(tmp, target);
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    private static void replace(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException ex) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    long savedRevision() {
        return savedRevision;
    }

    void await(long revision) throws IOException {
        Future<?> current;
        synchronized (this) {
            current = task;
        }
        try {
            if (current != null)
                current.get();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IOException("Save interrupted", ex);
        } catch (ExecutionException ex) {
            throw new IOException("Save failed", ex.getCause());
        }
        if (savedRevision < revision || lastFailure != null)
            throw lastFailure != null ? lastFailure : new IOException("Requested revision was not saved");
    }

    public void close() {
        worker.shutdown();
    }
}

package com.auroria.nodesmilitary;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * The field names are the version 1.0 on-disk format.
 */
final class MilitaryState {

    enum Type {

        INFANTRY,
        CAVALRY,
        ARTILLERY,
        ENGINEERS,
        NAVAL,
        RECON
    }

    static Type divisionType(String value) {
        if (value.equalsIgnoreCase("reconnaissance")) return Type.RECON;
        return Type.valueOf(value.toUpperCase(Locale.ROOT));
    }

    enum Rank {

        PRIVATE,
        CORPORAL,
        SERGEANT,
        LIEUTENANT,
        CAPTAIN,
        MAJOR,
        COLONEL,
        GENERAL
    }

    enum Order {

        UNASSIGNED, HOLD, MOVE, RETREAT
    }

    static final Pattern NAME = Pattern.compile("[A-Za-z0-9_-]{2,24}");

    int schema = 1;

    Map<String, Army> armies = new LinkedHashMap<>();

    List<String> history = new ArrayList<>();

    static String key(String name) {
        return name.toLowerCase(Locale.ROOT);
    }

    static String armyKey(UUID nation, String name) {
        return nation + ":" + key(name);
    }

    static final class Army {

        String id;
        String name;

        UUID nationId;

        UUID commanderUuid;

        UUID createdBy;

        long createdAt;

        String musterWorld = "";

        Integer musterTerritoryId;

        Map<String, Division> divisions = new LinkedHashMap<>();

        int strength() {
            return divisions.values().stream().mapToInt(d -> d.members.size()).sum();
        }

        List<Division> sortedDivisions() {
            return divisions.values().stream().sorted(Comparator.comparing(d -> key(d.name))).toList();
        }
    }

    static final class Division {

        String id;
        String name;

        Type type;

        UUID commanderUuid;

        List<UUID> members = new ArrayList<>();

        Map<UUID, Rank> ranks = new LinkedHashMap<>();

        Order order = Order.UNASSIGNED;

        Integer targetTerritoryId;

        long orderUpdatedAt;

        Rank rank(UUID id) {
            return ranks.getOrDefault(id, Rank.PRIVATE);
        }
    }

    List<Army> armies(UUID nation) {
        return armies.values().stream().filter(a -> nation.equals(a.nationId)).sorted(Comparator.comparing(a -> key(a.name))).toList();
    }

    /**
     * Build a stable, searchable roster without changing saved membership order.
     * Matches are case-insensitive; ties are ordered by UUID for pagination stability.
     */
    static List<UUID> roster(Division division, java.util.function.Function<UUID, String> displayName, String query) {
        String filter = query == null ? "" : query.strip().toLowerCase(Locale.ROOT);
        return division.members.stream()
                .filter(id -> {
                    String name = displayName.apply(id);
                    return filter.isEmpty() || (name != null && name.toLowerCase(Locale.ROOT).contains(filter));
                })
                .sorted(Comparator.comparing(
                        (UUID id) -> java.util.Objects.toString(displayName.apply(id), ""),
                        String.CASE_INSENSITIVE_ORDER
                    ).thenComparing(UUID::toString))
                .toList();
    }

    Division membership(UUID id) {
        for (Army army : armies.values()) {
            for (Division division : army.divisions.values()) {
                if (division.members.contains(id))
                    return division;
            }
        }
        return null;
    }

    void record(String event) {
        history.add(System.currentTimeMillis() + " " + event);
        if (history.size() > 200)
            history.subList(0, history.size() - 200).clear();
    }
}

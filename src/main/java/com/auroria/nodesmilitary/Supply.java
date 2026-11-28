package com.auroria.nodesmilitary;

import java.util.*;

final class Supply {

    enum Status {

        SUPPLIED, LIMITED, EMPTY, BLOCKADED, UNSUPPLIED
    }

    record Result(Status status, ExtraBridge.Depot depot, int hops) {
    }

    private record Candidate(ExtraBridge.Depot depot, int hops, boolean full) {
    }

    private static final Comparator<Candidate> BEST = Comparator.comparingInt(Candidate::hops).thenComparing(Candidate::full, Comparator.reverseOrder()).thenComparing(c -> c.depot().name(), String.CASE_INSENSITIVE_ORDER).thenComparingInt(c -> c.depot().territoryId());

    static Result evaluate(Map<Integer, Set<Integer>> graph, Map<Integer, Set<String>> ports, ExtraBridge.Snapshot extra, UUID nation, String world, int muster, int limit, long now) {
        if (!graph.containsKey(muster))
            return new Result(Status.UNSUPPLIED, null, -1);
        for (var link : extra.links()) {
            if (!nation.toString().equals(link.nationId()) || !worldMatches(world, link.world()) || !"SEA".equalsIgnoreCase(link.type()))
                continue;
            if (!graph.containsKey(link.from()) || !graph.containsKey(link.to()))
                continue;
            if (!Collections.disjoint(ports.getOrDefault(link.from(), Set.of()), ports.getOrDefault(link.to(), Set.of()))) {
                graph.get(link.from()).add(link.to());
                graph.get(link.to()).add(link.from());
            }
        }
        Map<Integer, Integer> distance = new HashMap<>();
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        distance.put(muster, 0);
        queue.add(muster);
        while (!queue.isEmpty()) {
            int here = queue.remove(), next = distance.get(here) + 1;
            if (next > limit)
                continue;
            for (int id : graph.get(here)) if (distance.putIfAbsent(id, next) == null)
                queue.add(id);
        }
        Candidate best = null;
        boolean openEmpty = false, blocked = false;
        for (var d : extra.depots()) {
            if (!nation.toString().equals(d.nationId()) || !worldMatches(world, d.world()))
                continue;
            Integer hops = distance.get(d.territoryId());
            if (hops == null)
                continue;
            if (d.blockadedUntil() > now) {
                blocked = true;
                continue;
            }
            if (d.food() <= 0 && d.equipment() <= 0 && d.fuel() <= 0) {
                openEmpty = true;
                continue;
            }
            var candidate = new Candidate(d, hops, d.food() > 0 && d.equipment() > 0);
            if (best == null || BEST.compare(candidate, best) < 0)
                best = candidate;
        }
        if (best != null)
            return new Result(best.full() ? Status.SUPPLIED : Status.LIMITED, best.depot(), best.hops());
        return new Result(openEmpty ? Status.EMPTY : blocked ? Status.BLOCKADED : Status.UNSUPPLIED, null, -1);
    }

    private static boolean worldMatches(String selected, String stored) {
        return stored.isEmpty() || selected.equals(stored);
    }
}

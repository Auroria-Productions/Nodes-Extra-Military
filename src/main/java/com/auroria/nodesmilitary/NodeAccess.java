package com.auroria.nodesmilitary;

import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import phonon.nodes.Nodes;
import phonon.nodes.objects.*;
import java.lang.reflect.Method;
import java.util.*;

final class NodeAccess {

    private final JavaPlugin core;

    private final Method id, neighbors, byId;

    NodeAccess(JavaPlugin core) throws ReflectiveOperationException {
        this.core = core;
        // Kotlin inline value classes produce JVM names that Java cannot spell at a call site.
        id = Territory.class.getMethod("getId-L9_GXOM");
        neighbors = Territory.class.getMethod("getNeighbors-CCo7D7I");
        byId = Nodes.class.getMethod("getTerritoryFromId-8M4gw-M", int.class);
    }

    private static Object call(Method method, Object on, Object... args) {
        try {
            return method.invoke(on, args);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Cannot read Nodes territory API", e);
        }
    }

    Nation nation(Player player) {
        Resident resident = Nodes.INSTANCE.getResident(player);
        return resident == null ? null : resident.getNation();
    }

    String world() {
        return core.getConfig().getString("selectedWorld", "world");
    }

    Territory current(Player player) {
        return world().equals(player.getWorld().getName()) ? Nodes.INSTANCE.getTerritoryFromPlayer(player) : null;
    }

    int id(Territory t) {
        return (int) call(id, t);
    }

    Territory at(int n) {
        return (Territory) call(byId, Nodes.INSTANCE, n);
    }

    boolean owned(Territory t, UUID nation) {
        Town town = t == null ? null : t.getTown();
        return town != null && town.getNation() != null && nation.equals(town.getNation().getUuid());
    }

    boolean leadership(Player p, Nation nation) {
        Resident r = Nodes.INSTANCE.getResident(p);
        if (r == null || r.getNation() == null || !nation.getUuid().equals(r.getNation().getUuid()))
            return false;
        Town t = r.getTown(), capital = nation.getCapital();
        return (capital != null && capital.getLeader() == r) || (t != null && (t.getLeader() == r || t.getOfficers().contains(r) || t.getGenerals().contains(r)));
    }

    String name(UUID player) {
        if (player == null)
            return "Unassigned";
        Resident r = Nodes.INSTANCE.getResidentFromUUID(player);
        return r == null ? player.toString() : r.getName();
    }

    Map<UUID, String> nationNames() {
        Map<UUID, String> result = new HashMap<>();
        for (Nation n : Nodes.INSTANCE.getNations$nodes().values()) result.put(n.getUuid(), n.getName());
        return result;
    }

    Map<Integer, Set<Integer>> friendlyGraph(UUID nation) {
        Map<Integer, Set<Integer>> graph = new HashMap<>();
        for (var entry : Nodes.INSTANCE.iterTerritories()) {
            Territory t = entry.getValue();
            if (owned(t, nation))
                graph.put(id(t), new HashSet<>());
        }
        for (var entry : graph.entrySet()) {
            for (int next : (int[]) call(neighbors, at(entry.getKey()))) {
                if (graph.containsKey(next)) {
                    entry.getValue().add(next);
                    graph.get(next).add(entry.getKey());
                }
            }
        }
        return graph;
    }

    Map<Integer, Set<String>> portGroups() {
        Map<Integer, Set<String>> groups = new HashMap<>();
        for (Port port : Nodes.INSTANCE.getPorts$nodes().values()) {
            Territory t = Nodes.INSTANCE.getTerritoryFromBlock(port.getLocX(), port.getLocZ());
            if (t == null)
                continue;
            Set<String> names = groups.computeIfAbsent(id(t), k -> new HashSet<>());
            for (PortGroup group : port.getGroups()) names.add(group.getName());
        }
        return groups;
    }
}

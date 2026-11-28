package com.auroria.nodesmilitary;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import java.io.File;
import java.io.IOException;

record Settings(boolean marshal, int maxArmies, int maxDivisions, int maxMembers, int hops, boolean gui, boolean openMenu, String title, boolean export, int interval, String stateFile, String publicFile) {

    static Settings load(File file) throws IOException, InvalidConfigurationException {
        var y = new YamlConfiguration();
        y.load(file);
        return new Settings(
                y.getBoolean("military.requireMarshalAuthority", true),
                value(y, "military.maxArmiesPerNation", 6, 1, 50),
                value(y, "military.maxDivisionsPerArmy", 8, 1, 30),
                value(y, "military.maxMembersPerDivision", 50, 1, 500),
                value(y, "military.supply.maxTerritoryHops", 12, 1, 50),
                y.getBoolean("gui.enabled", true), y.getBoolean("gui.openOnCommand", true),
                y.getString("gui.title", "Nodes Military"), y.getBoolean("map.exportPublicData", true),
                value(y, "storage.saveIntervalSeconds", 120, 20, 3600),
                y.getString("storage.stateFilename", "state.json"),
                y.getString("storage.publicFilename", "public.json"));
    }

    private static int value(YamlConfiguration y, String path, int fallback, int min, int max) {
        int value = y.getInt(path, fallback);
        if ((y.contains(path) && !y.isInt(path)) || value < min || value > max)
            throw new IllegalArgumentException(path + " must be an integer from " + min + " to " + max + ".");
        return value;
    }
}

package com.auroria.nodesmilitary;

import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.logging.Level;

public final class MilitaryPlugin extends JavaPlugin {

    MilitaryState state;

    NodeAccess nodes;

    ExtraBridge extra;

    Settings settings;

    Menus menus;

    MilitaryCommands commands;

    private Storage storage;

    private NodesCommandHook hook;

    private BukkitTask periodic, queued;

    private Map<UUID, String> lastNames = Map.of();

    private long revision;

    private boolean ready;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        try {
            settings = Settings.load(new File(getDataFolder(), "config.yml"));
            var core = getServer().getPluginManager().getPlugin("milzzynodes");
            if (!(core instanceof JavaPlugin jp) || !jp.isEnabled())
                throw new IOException("Enable milzzynodes first.");
            nodes = new NodeAccess(jp);
            storage = new Storage(getDataFolder().toPath(), settings.stateFile(), settings.publicFile(), getLogger());
            state = storage.load();
            extra = new ExtraBridge(this);
            extra.bind();
            commands = new MilitaryCommands(this);
            menus = new Menus(this);
            var cmd = Objects.requireNonNull(getCommand("nmilitary"));
            cmd.setExecutor(commands);
            cmd.setTabCompleter(commands);
            hook = NodesCommandHook.install(this, cmd);
            var manager = getServer().getPluginManager();
            manager.registerEvents(menus, this);
            manager.registerEvents(extra, this);
            getServer().getScheduler().runTaskTimerAsynchronously(this, extra::refresh, 1L, 100L);
            schedulePeriodic();
            ready = true;
            saveNow();
            getLogger().info("Loaded " + state.armies.size() + " armies. Nodes-Extra integration " + (extra.installed() ? "active." : "optional and currently offline."));
        } catch (Exception | LinkageError e) {
            ready = false;
            getLogger().log(Level.SEVERE, "Nodes-Military could not start: " + e.getMessage(), e);
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    private void schedulePeriodic() {
        if (periodic != null)
            periodic.cancel();
        long ticks = settings.interval() * 20L;
        periodic = getServer().getScheduler().runTaskTimer(this, () -> {
            Map<UUID, String> names = nodes.nationNames();
            if (!names.equals(lastNames))
                revision++;
            if (storage.savedRevision() < revision)
                submit(names);
        }, ticks, ticks);
    }

    void record(Player actor, String event) {
        state.record(actor.getName() + " " + event);
        revision++;
        if (queued == null)
            queued = getServer().getScheduler().runTask(this, () -> {
                queued = null;
                submit(nodes.nationNames());
            });
    }

    private void submit(Map<UUID, String> names) {
        lastNames = names;
        storage.submit(new Storage.Snapshot(revision, Storage.JSON.toJsonTree(state), settings.export() ? Storage.publicFeed(state, names) : null));
    }

    void saveNow() throws IOException {
        if (queued != null) {
            queued.cancel();
            queued = null;
        }
        submit(nodes.nationNames());
        storage.await(revision);
    }

    void reloadSettings() throws Exception {
        Settings next = Settings.load(new File(getDataFolder(), "config.yml"));
        if (!settings.stateFile().equals(next.stateFile()) || !settings.publicFile().equals(next.publicFile()))
            throw new IllegalArgumentException("Stop the server and migrate the JSON files before changing their names.");
        settings = next;
        revision++;
        schedulePeriodic();
        extra.bind();
        if (!settings.gui())
            menus.closeAll();
        saveNow();
    }

    @Override
    public void onDisable() {
        if (hook != null)
            hook.uninstall();
        if (menus != null)
            menus.closeAll();
        if (periodic != null)
            periodic.cancel();
        if (ready)
            try {
                saveNow();
            } catch (Exception e) {
                getLogger().log(Level.SEVERE, "Final military save failed", e);
            }
        if (storage != null)
            storage.close();
        ready = false;
    }
}

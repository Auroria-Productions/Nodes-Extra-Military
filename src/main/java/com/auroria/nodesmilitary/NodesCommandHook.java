package com.auroria.nodesmilitary;

import org.bukkit.command.*;
import java.util.*;

final class NodesCommandHook implements TabExecutor {

    private final PluginCommand root, military;

    private final CommandExecutor previous;

    private final TabCompleter completions;

    private NodesCommandHook(PluginCommand root, PluginCommand military) {
        this.root = root;
        this.military = military;
        previous = root.getExecutor();
        completions = root.getTabCompleter();
    }

    static NodesCommandHook install(MilitaryPlugin plugin, PluginCommand military) {
        PluginCommand root = plugin.getServer().getPluginCommand("nodes");
        var core = plugin.getServer().getPluginManager().getPlugin("milzzynodes");
        if (root == null || root.getPlugin() != core || root.getExecutor() == null) {
            plugin.getLogger().warning("The /nodes command belongs to another plugin or is unavailable. Use /nmilitary.");
            return null;
        }
        var hook = new NodesCommandHook(root, military);
        root.setExecutor(hook);
        root.setTabCompleter(hook);
        return hook;
    }

    void uninstall() {
        if (root.getExecutor() == this)
            root.setExecutor(previous);
        if (root.getTabCompleter() == this)
            root.setTabCompleter(completions);
    }

    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("military") && military.getPlugin().isEnabled())
            return military.execute(sender, "nmilitary", Arrays.copyOfRange(args, 1, args.length));
        return previous.onCommand(sender, cmd, label, args);
    }

    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        if (args.length > 1 && args[0].equalsIgnoreCase("military") && military.getPlugin().isEnabled())
            return military.testPermissionSilent(sender) ? military.tabComplete(sender, "nmilitary", Arrays.copyOfRange(args, 1, args.length)) : List.of();
        TabCompleter delegate = completions != null ? completions : previous instanceof TabCompleter c ? c : null;
        List<String> result = delegate == null ? null : delegate.onTabComplete(sender, cmd, alias, args);
        if (args.length != 1 || !military.getPlugin().isEnabled() || !military.testPermissionSilent(sender) || !"military".startsWith(args[0].toLowerCase(Locale.ROOT)))
            return result;
        var merged = new LinkedHashSet<String>();
        if (result != null)
            merged.addAll(result);
        merged.add("military");
        return List.copyOf(merged);
    }
}

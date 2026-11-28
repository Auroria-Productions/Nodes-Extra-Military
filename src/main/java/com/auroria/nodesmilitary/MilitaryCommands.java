package com.auroria.nodesmilitary;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import phonon.nodes.objects.Nation;
import phonon.nodes.objects.Resident;
import phonon.nodes.objects.Territory;
import java.util.*;
import java.util.logging.Level;
import static com.auroria.nodesmilitary.MilitaryState.*;

final class MilitaryCommands implements TabExecutor {

    private static final List<String> ROOTS = List.of("gui", "help", "overview", "army", "division", "enlist", "leave", "muster", "order", "report");

    private static final List<String> STAFF = List.of("history", "save", "reload");

    private static final List<String> ARMY = List.of("list", "create", "info", "commander", "disband");

    private static final List<String> DIVISION = List.of("create", "roster", "commander", "rank", "disband");

    private final MilitaryPlugin plugin;

    MilitaryCommands(MilitaryPlugin plugin) {
        this.plugin = plugin;
    }

    static void tell(CommandSender sender, String message) {
        ((Audience) sender).sendMessage(Component.text("[Military] ", NamedTextColor.GOLD).append(Component.text(message, NamedTextColor.WHITE)));
    }

    private static void need(boolean condition, String message) {
        if (!condition)
            throw new IllegalArgumentException(message);
    }

    private static void length(String[] args, int count, String usage) {
        need(args.length == count, "Use /nmilitary " + usage);
    }

    private static String arg(String[] args, int i) {
        return i < args.length ? args[i] : "";
    }

    private static <E extends Enum<E>> E choice(Class<E> type, String input) {
        try {
            return Enum.valueOf(type, input.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Choose one of " + names(type) + ".");
        }
    }

    private static <E extends Enum<E>> List<String> names(Class<E> type) {
        return Arrays.stream(type.getEnumConstants()).map(e -> key(e.name())).toList();
    }

    Nation nation(Player p) {
        Nation n = plugin.nodes.nation(p);
        need(n != null, "Join a Nodes nation first.");
        return n;
    }

    Army army(Nation n, String name) {
        Army a = plugin.state.armies.get(armyKey(n.getUuid(), name));
        need(a != null, "This army does not exist in your nation.");
        return a;
    }

    Division division(Army a, String name) {
        Division d = a.divisions.get(key(name));
        need(d != null, "Division not found in " + a.name + ".");
        return d;
    }

    boolean national(Player p, Nation n) {
        return p.hasPermission("nodesmilitary.admin") || !plugin.settings.marshal() || plugin.nodes.leadership(p, n) || (plugin.extra.installed() && (p.hasPermission("nodesextra.admin") || plugin.extra.snapshot().appointed(p.getUniqueId(), n.getUuid())));
    }

    boolean manages(Player p, Nation n, Army a) {
        return p.getUniqueId().equals(a.commanderUuid) || national(p, n);
    }

    boolean commands(Player p, Nation n, Army a, Division d) {
        return p.getUniqueId().equals(d.commanderUuid) || manages(p, n, a);
    }

    private void nationalRequired(Player p, Nation n) {
        need(national(p, n), "National military authority required (sovereign, town leadership, Marshal, executive or admin).");
    }

    private void armyRequired(Player p, Nation n, Army a) {
        need(manages(p, n, a), "Army command or national military authority required.");
    }

    private void divisionRequired(Player p, Nation n, Army a, Division d) {
        need(commands(p, n, a, d), "Division, army or national command required.");
    }

    private Resident citizen(String name, Nation nation) {
        return nation.getResidents().stream()
                .filter(resident -> resident.getName().equalsIgnoreCase(name))
                .findFirst().orElseThrow(() -> new IllegalArgumentException("Choose a citizen of your nation."));
    }

    private Territory location(Player p, Nation n) {
        Territory t = plugin.nodes.current(p);
        need(plugin.nodes.owned(t, n.getUuid()), "Stand in a territory owned by your nation in " + plugin.nodes.world() + ".");
        return t;
    }

    public boolean onCommand(CommandSender sender, Command cmd, String alias, String[] args) {
        execute(sender, args);
        return true;
    }

    boolean execute(CommandSender sender, String... args) {
        try {
            need(sender.hasPermission("nodesmilitary.use"), "You do not have permission to use Nodes-Military.");
            String root = key(arg(args, 0));
            if (root.isEmpty())
                root = sender instanceof Player && plugin.settings.gui() && plugin.settings.openMenu() ? "gui" : "help";
            if (root.equals("help")) {
                help(sender);
                return true;
            }
            if (STAFF.contains(root)) {
                staff(sender, root, args);
                return true;
            }
            need(sender instanceof Player, "Use this action in game. Console commands include help, history, save and reload.");
            Player p = (Player) sender;
            if (root.equals("menu") || root.equals("gui")) {
                need(plugin.settings.gui(), "Military menus are disabled. Use /nmilitary help.");
                plugin.menus.home(p, 0);
                return true;
            }
            if (root.equals("leave")) {
                length(args, 1, "leave");
                leave(p);
                return true;
            }
            Nation n = nation(p);
            switch(root) {
                case "overview", "list" ->
                    overview(p, n);
                case "army" ->
                    armyAction(p, n, args);
                case "division" ->
                    divisionAction(p, n, args);
                case "enlist", "join" ->
                    {
                        length(args, 3, "enlist <army> <division>");
                        Army a = army(n, args[1]);
                        Division d = division(a, args[2]);
                        need(plugin.state.membership(p.getUniqueId()) == null, "You are already enlisted. Leave your current division first.");
                        need(d.members.size() < plugin.settings.maxMembers(), "This division is full.");
                        d.members.add(p.getUniqueId());
                        d.ranks.put(p.getUniqueId(), Rank.PRIVATE);
                        change(p, "enlisted in " + a.name + " / " + d.name, "Enlisted in " + d.name + ".");
                    }
                case "muster" ->
                    {
                        length(args, 2, "muster <army>");
                        Army a = army(n, args[1]);
                        armyRequired(p, n, a);
                        a.musterTerritoryId = plugin.nodes.id(location(p, n));
                        a.musterWorld = p.getWorld().getName();
                        change(p, "set " + a.name + " muster to #" + a.musterTerritoryId, "Muster set to #" + a.musterTerritoryId + ".");
                    }
                case "order" ->
                    order(p, n, args);
                case "report", "supply" ->
                    {
                        length(args, 2, "report <army>");
                        report(p, n, army(n, args[1]));
                    }
                default ->
                    throw new IllegalArgumentException("Unknown action. Use /nmilitary help.");
            }
            return true;
        } catch (IllegalArgumentException ex) {
            tell(sender, ex.getMessage());
            return false;
        } catch (Exception | LinkageError ex) {
            plugin.getLogger().log(Level.SEVERE, "Military action failed", ex);
            tell(sender, "The action failed. See the server console for details.");
            return false;
        }
    }

    private void change(Player p, String event, String feedback) {
        plugin.record(p, event);
        tell(p, feedback);
    }

    private void overview(Player p, Nation n) {
        List<Army> armies = plugin.state.armies(n.getUuid());
        tell(p, n.getName() + " has " + armies.size() + " armies.");
        for (Army a : armies) tell(p, a.name + " · " + a.divisions.size() + " divisions · " + a.strength() + " personnel");
    }

    private void armyAction(Player p, Nation n, String[] args) {
        String action = key(arg(args, 1));
        if (args.length == 1 || (args.length == 2 && action.equals("list"))) {
            overview(p, n);
            return;
        }
        if ("create".equals(action)) {
            length(args, 3, "army create <name>");
            nationalRequired(p, n);
            String name = args[2];
            validateName(name);
            String id = armyKey(n.getUuid(), name);
            need(!plugin.state.armies.containsKey(id), "An army with that name already exists.");
            need(plugin.state.armies(n.getUuid()).size() < plugin.settings.maxArmies(), "Your nation has reached its army limit.");
            Army a = new Army();
            a.id = id;
            a.name = name;
            a.nationId = n.getUuid();
            a.createdBy = p.getUniqueId();
            a.commanderUuid = p.getUniqueId();
            a.createdAt = System.currentTimeMillis();
            plugin.state.armies.put(id, a);
            change(p, "established " + name + " for " + n.getName(), "Established " + name + ". You are its commander.");
            return;
        }
        need(!action.equals("list") && ARMY.contains(action) && args.length >= 3, "Use /nmilitary army <create|info|commander|disband> <name>");
        Army a = army(n, args[2]);
        switch(action) {
            case "info" ->
                {
                    length(args, 3, "army info <army>");
                    tell(p, a.name + " · Commander " + plugin.nodes.name(a.commanderUuid) + " · " + a.strength() + " personnel · Muster " + muster(a));
                    for (Division d : a.sortedDivisions()) tell(p, d.name + " · " + d.type + " · " + d.members.size() + " personnel · " + orderText(d));
                }
            case "commander" ->
                {
                    length(args, 4, "army commander <army> <player>");
                    nationalRequired(p, n);
                    Resident target = citizen(args[3], n);
                    a.commanderUuid = target.getUuid();
                    change(p, "appointed " + target.getName() + " commander of " + a.name, "Commander appointed.");
                }
            case "disband" ->
                {
                    length(args, 3, "army disband <army>");
                    armyRequired(p, n, a);
                    plugin.state.armies.remove(armyKey(n.getUuid(), a.name));
                    change(p, "disbanded " + a.name, "Army disbanded.");
                }
        }
    }

    private static void validateName(String name) {
        need(NAME.matcher(name).matches(), "Use 2–24 letters, numbers, underscores or hyphens.");
    }

    static String muster(Army a) {
        return a.musterTerritoryId == null ? "unassigned" : "#" + a.musterTerritoryId + " in " + a.musterWorld;
    }

    static String orderText(Division d) {
        return d.order + (d.targetTerritoryId == null ? "" : " #" + d.targetTerritoryId);
    }

    private void divisionAction(Player p, Nation n, String[] args) {
        String action = key(arg(args, 1));
        need(DIVISION.contains(action) && args.length >= 4, "Use /nmilitary division <create|commander|rank|roster|disband> <army> <division>");
        Army a = army(n, args[2]);
        if (action.equals("create")) {
            length(args, 5, "division create <army> <name> <type>");
            armyRequired(p, n, a);
            validateName(args[3]);
            String id = key(args[3]);
            Type type = choice(Type.class, args[4].equalsIgnoreCase("reconnaissance") ? "RECON" : args[4]);
            need(!a.divisions.containsKey(id), "That division already exists.");
            need(a.divisions.size() < plugin.settings.maxDivisions(), "This army has reached its division limit.");
            Division d = new Division();
            d.id = id;
            d.name = args[3];
            d.type = type;
            a.divisions.put(id, d);
            change(p, "created " + d.name + " in " + a.name, "Created " + type + " division " + d.name + ".");
            return;
        }
        Division d = division(a, args[3]);
        switch(action) {
            case "roster" ->
                {
                    length(args, 4, "division roster <army> <division>");
                    tell(p, d.name + " · " + d.members.size() + " personnel");
                    for (UUID id : d.members) tell(p, plugin.nodes.name(id) + " · " + d.rank(id) + (id.equals(d.commanderUuid) ? " · Commander" : ""));
                }
            case "disband" ->
                {
                    length(args, 4, "division disband <army> <division>");
                    armyRequired(p, n, a);
                    a.divisions.remove(key(d.name));
                    change(p, "disbanded " + a.name + " / " + d.name, "Division disbanded.");
                }
            case "commander", "rank" ->
                {
                    boolean appoint = action.equals("commander");
                    length(args, appoint ? 5 : 6, "division " + action + " <army> <division> <player>" + (appoint ? "" : " <rank>"));
                    if (appoint)
                        armyRequired(p, n, a);
                    else
                        divisionRequired(p, n, a, d);
                    Resident target = citizen(args[4], n);
                    UUID id = target.getUuid();
                    need(d.members.contains(id), "The player must enlist in this division first.");
                    if (appoint) {
                        d.commanderUuid = id;
                        if (d.rank(id).ordinal() < Rank.CAPTAIN.ordinal()) d.ranks.put(id, Rank.CAPTAIN);
                        change(p, "appointed " + target.getName() + " commander of " + d.name, "Division commander appointed.");
                    } else {
                        Rank rank = choice(Rank.class, args[5]);
                        d.ranks.put(id, rank);
                        change(p, "set " + target.getName() + " to " + rank + " in " + d.name, "Rank set to " + rank + ".");
                    }
                }
        }
    }

    private void leave(Player p) {
        UUID id = p.getUniqueId();
        Division d = plugin.state.membership(id);
        need(d != null, "You are not enlisted in a division.");
        d.members.remove(id);
        d.ranks.remove(id);
        if (id.equals(d.commanderUuid))
            d.commanderUuid = null;
        change(p, "left " + d.name, "You left " + d.name + ".");
    }

    private void order(Player p, Nation n, String[] args) {
        length(args, 5, "order <army> <division> <hold|move|retreat> <territory-id>");
        Army a = army(n, args[1]);
        Division d = division(a, args[2]);
        divisionRequired(p, n, a, d);
        Order order = choice(Order.class, args[3]);
        need(order != Order.UNASSIGNED, "Choose HOLD, MOVE or RETREAT.");
        int target;
        try {
            target = Integer.parseInt(args[4]);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Enter a numeric territory ID.");
        }
        need(plugin.nodes.owned(plugin.nodes.at(target), n.getUuid()), "Orders must target a territory owned by your nation.");
        d.order = order;
        d.targetTerritoryId = target;
        d.orderUpdatedAt = System.currentTimeMillis();
        change(p, "ordered " + a.name + " / " + d.name + " to " + order + " #" + target, "Order recorded: " + order + " #" + target + ".");
    }

    List<String> reportLines(Nation n, Army a) {
        var lines = new ArrayList<String>();
        lines.add(a.name + " · " + a.strength() + " personnel · " + a.divisions.size() + " divisions");
        lines.add("Muster: " + muster(a));
        if (a.musterTerritoryId == null) {
            lines.add("Set a muster to check supply.");
            return lines;
        }
        var extra = plugin.extra.snapshot();
        if (extra.problem() != null) {
            lines.add("Supply unavailable: " + extra.problem());
            return lines;
        }
        if (!plugin.nodes.world().equals(a.musterWorld)) {
            lines.add("Muster is outside the selected Nodes world.");
            return lines;
        }
        var result = Supply.evaluate(plugin.nodes.friendlyGraph(n.getUuid()), extra.links().isEmpty() ? Map.of() : plugin.nodes.portGroups(), extra, n.getUuid(), a.musterWorld, a.musterTerritoryId, plugin.settings.hops(), System.currentTimeMillis());
        lines.add("Supply: " + result.status());
        if (result.depot() != null) {
            var s = result.depot();
            lines.add(s.name() + " · " + result.hops() + " territory hops");
            lines.add("Food " + s.food() + " · Equipment " + s.equipment() + " · Fuel " + s.fuel());
        }
        return lines;
    }

    private void report(Player p, Nation n, Army a) {
        for (String s : reportLines(n, a)) tell(p, s);
    }

    private void staff(CommandSender sender, String action, String[] args) throws Exception {
        need(sender.hasPermission("nodesmilitary.admin"), "Military admin permission required.");
        length(args, 1, action);
        switch(action) {
            case "history" ->
                {
                    var history = plugin.state.history;
                    tell(sender, "Recent military events (" + history.size() + " retained)");
                    history.subList(Math.max(0, history.size() - 12), history.size()).forEach(s -> tell(sender, s));
                }
            case "save" ->
                {
                    plugin.saveNow();
                    tell(sender, "Military data saved.");
                }
            case "reload" ->
                {
                    plugin.reloadSettings();
                    tell(sender, "Military settings reloaded.");
                }
        }
    }

    private void help(CommandSender p) {
        tell(p, "Nodes-Military " + plugin.getDescription().getVersion() + " · /nmilitary or /nodes military");
        for (var line : List.of("gui (menu) · overview (list, army list) · help", "army create|info|disband <army>", "army commander <army> <player>", "division create <army> <name> <type>", "division roster|disband <army> <division>", "division commander <army> <division> <player>", "division rank <army> <division> <player> <rank>", "enlist <army> <division> (join) · leave", "muster <army> · report <army> (supply)", "order <army> <division> <hold|move|retreat> <territory-id>")) tell(p, line);
        if (p.hasPermission("nodesmilitary.admin"))
            tell(p, "Staff: history · save · reload");
    }

    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        if (!sender.hasPermission("nodesmilitary.use") || args.length == 0)
            return List.of();
        List<String> choices = new ArrayList<>();
        int i = args.length - 1;
        if (i == 0) {
            choices.addAll(ROOTS);
            if (sender.hasPermission("nodesmilitary.admin"))
                choices.addAll(STAFF);
        } else if (sender instanceof Player p && plugin.nodes.nation(p) != null) {
            Nation n = plugin.nodes.nation(p);
            String root = key(args[0]), act = key(arg(args, 1));
            List<String> armies = plugin.state.armies(n.getUuid()).stream().map(a -> a.name).toList();
            switch(root) {
                case "army", "division" ->
                    {
                        if (i == 1)
                            choices.addAll(root.equals("army") ? ARMY : DIVISION);
                        else if (i == 2 && !(root.equals("army") && act.equals("create")))
                            choices.addAll(armies);
                        else if (root.equals("division") && i == 3 && !act.equals("create"))
                            divisionNames(n, args[2], choices);
                        else if (root.equals("division") && i == 4 && act.equals("create"))
                            choices.addAll(List.of("infantry", "cavalry", "artillery", "engineers", "naval", "recon", "reconnaissance"));
                        else if ((root.equals("army") && i == 3 && act.equals("commander")) || (root.equals("division") && i == 4 && Set.of("rank", "commander").contains(act))) {
                            for (Resident target : n.getResidents())
                                choices.add(target.getName());
                        } else if (root.equals("division") && i == 5 && act.equals("rank"))
                            choices.addAll(names(Rank.class));
                    }
                case "report", "supply", "enlist", "join", "muster", "order" ->
                    {
                        if (i == 1)
                            choices.addAll(armies);
                        else if (i == 2 && Set.of("order", "enlist", "join").contains(root))
                            divisionNames(n, args[1], choices);
                        else if (root.equals("order") && i == 3)
                            choices.addAll(List.of("hold", "move", "retreat"));
                        else if (root.equals("order") && i == 4) {
                            Territory t = plugin.nodes.current(p);
                            if (plugin.nodes.owned(t, n.getUuid()))
                                choices.add(String.valueOf(plugin.nodes.id(t)));
                        }
                    }
            }
        }
        String prefix = key(args[i]);
        return choices.stream().filter(s -> key(s).startsWith(prefix)).sorted(String.CASE_INSENSITIVE_ORDER).toList();
    }

    private void divisionNames(Nation n, String name, List<String> out) {
        Army a = plugin.state.armies.get(armyKey(n.getUuid(), name));
        if (a != null)
            a.sortedDivisions().forEach(d -> out.add(d.name));
    }
}

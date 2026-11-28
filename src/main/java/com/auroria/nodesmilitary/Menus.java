package com.auroria.nodesmilitary;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.*;
import org.bukkit.Material;
import org.bukkit.conversations.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.*;
import phonon.nodes.objects.Nation;
import phonon.nodes.objects.Resident;
import java.util.*;
import java.util.function.Consumer;
import static com.auroria.nodesmilitary.MilitaryState.*;

final class Menus implements Listener {

    static final int PAGE_SIZE = 45;

    private final MilitaryPlugin plugin;

    private final MilitaryCommands cmd;

    private final Map<UUID, Conversation> prompts = new HashMap<>();

    Menus(MilitaryPlugin plugin) {
        this.plugin = plugin;
        cmd = plugin.commands;
    }

    private final class Board implements InventoryHolder {

        final UUID owner, nation;

        final Army army;

        final Division division;

        final Inventory inv;

        final Map<Integer, Runnable> buttons = new HashMap<>();

        boolean clicked;

        Board(Player p, String title, Army a, Division d) {
            owner = p.getUniqueId();
            Nation n = plugin.nodes.nation(p);
            nation = n == null ? null : n.getUuid();
            army = a;
            division = d;
            inv = plugin.getServer().createInventory(this, 54, Component.text(title, NamedTextColor.DARK_AQUA));
            put(53, Material.BARRIER, "Close", List.of(), p::closeInventory);
        }

        public Inventory getInventory() {
            return inv;
        }

        void put(int slot, Material icon, String name, List<String> lore, Runnable action) {
            ItemStack stack = new ItemStack(icon);
            var meta = stack.getItemMeta();
            meta.displayName(Component.text(name, NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
            meta.lore(lore.stream().map(s -> (Component) Component.text(s, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)).toList());
            stack.setItemMeta(meta);
            inv.setItem(slot, stack);
            if (action != null)
                buttons.put(slot, action);
        }

        boolean valid(Player p) {
            if (!plugin.isEnabled() || !plugin.settings.gui() || !p.isOnline() || !owner.equals(p.getUniqueId()) || !p.hasPermission("nodesmilitary.use"))
                return false;
            Nation n = plugin.nodes.nation(p);
            if (!Objects.equals(nation, n == null ? null : n.getUuid()))
                return false;
            if (army != null && plugin.state.armies.get(armyKey(nation, army.name)) != army)
                return false;
            return division == null || army.divisions.get(key(division.name)) == division;
        }

        void back(Runnable action) {
            put(45, Material.ARROW, "Back", List.of(), action);
        }
    }

    @EventHandler
    public void click(InventoryClickEvent e) {
        if (!(e.getView().getTopInventory().getHolder() instanceof Board b))
            return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p) || b.clicked || (e.getClick() != ClickType.LEFT && e.getClick() != ClickType.RIGHT))
            return;
        Runnable action = b.buttons.get(e.getRawSlot());
        if (action == null)
            return;
        b.clicked = true;
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            b.clicked = false;
            if (!p.isOnline() || p.getOpenInventory().getTopInventory().getHolder() != b)
                return;
            if (!b.valid(p)) {
                p.closeInventory();
                MilitaryCommands.tell(p, "Your nation or formation changed. Reopen the menu.");
                return;
            }
            action.run();
        });
    }

    @EventHandler
    public void drag(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof Board)
            e.setCancelled(true);
    }

    void closeAll() {
        for (var p : plugin.getServer().getOnlinePlayers()) if (p.getOpenInventory().getTopInventory().getHolder() instanceof Board)
            p.closeInventory();
        for (var c : List.copyOf(prompts.values())) c.abandon();
        prompts.clear();
    }

    static int page(int wanted, int items) {
        return Math.clamp(wanted, 0, Math.max(0, (items - 1) / PAGE_SIZE));
    }

    private void pages(Board b, int page, int size, Consumer<Integer> open) {
        if (page > 0)
            b.put(48, Material.ARROW, "Previous page", List.of(), () -> open.accept(page - 1));
        if ((page + 1) * PAGE_SIZE < size)
            b.put(50, Material.ARROW, "Next page", List.of(), () -> open.accept(page + 1));
    }

    void home(Player p, int wanted) {
        Nation n = plugin.nodes.nation(p);
        Board b = new Board(p, plugin.settings.title(), null, null);
        if (n == null) {
            b.put(22, Material.MAP, "No national affiliation", List.of("Join a Nodes nation to browse its formations."), null);
            if (plugin.state.membership(p.getUniqueId()) != null)
                b.put(47, Material.OAK_DOOR, "Leave previous division", List.of(), () -> {
                    cmd.execute(p, "leave");
                    home(p, 0);
                });
        } else {
            List<Army> list = plugin.state.armies(n.getUuid());
            int page = page(wanted, list.size());
            for (int i = page * PAGE_SIZE; i < Math.min(list.size(), (page + 1) * PAGE_SIZE); i++) {
                Army a = list.get(i);
                b.put(i % PAGE_SIZE, Material.WHITE_BANNER, a.name, List.of(a.divisions.size() + " divisions · " + a.strength() + " personnel", "Muster " + MilitaryCommands.muster(a)), () -> {
                    if (plugin.state.armies.get(armyKey(n.getUuid(), a.name)) == a)
                        army(p, a, 0);
                    else
                        home(p, page);
                });
            }
            if (list.isEmpty())
                b.put(22, Material.MAP, "No armies in " + n.getName(), List.of("National leadership can establish the first army."), null);
            b.put(49, Material.COMPASS, n.getName(), List.of(list.size() + " armies", "Page " + (page + 1)), null);
            if (cmd.national(p, n))
                b.put(46, Material.WRITABLE_BOOK, "Create army", List.of("Enter a name in chat."), () -> prompt(p, b, "Enter the army name (2–24 letters, numbers, _ or -)", name -> {
                    if (cmd.execute(p, "army", "create", name))
                        army(p, cmd.army(n, name), 0);
                    else
                        home(p, page);
                }));
            Division own = plugin.state.membership(p.getUniqueId());
            if (own != null)
                b.put(47, Material.OAK_DOOR, "Leave " + own.name, List.of("Withdraw your own enlistment."), () -> confirm(p, b, "Leave " + own.name, () -> {
                    if (plugin.state.membership(p.getUniqueId()) == own)
                        cmd.execute(p, "leave");
                    home(p, page);
                }, () -> home(p, page)));
            pages(b, page, list.size(), i -> home(p, i));
        }
        p.openInventory(b.inv);
    }

    private void army(Player p, Army a, int wanted) {
        Nation n = cmd.nation(p);
        Board b = new Board(p, "Army • " + a.name, a, null);
        List<Division> list = a.sortedDivisions();
        int page = page(wanted, list.size());
        for (int i = page * PAGE_SIZE; i < Math.min(list.size(), (page + 1) * PAGE_SIZE); i++) {
            Division d = list.get(i);
            b.put(i % PAGE_SIZE, icon(d.type), d.name, List.of(d.type + " · " + d.members.size() + " personnel", MilitaryCommands.orderText(d)), () -> {
                if (a.divisions.get(key(d.name)) == d)
                    division(p, a, d, 0);
                else
                    army(p, a, page);
            });
        }
        if (list.isEmpty())
            b.put(22, Material.BOOK, "No divisions yet", List.of("Create a division to start enlisting citizens."), null);
        b.back(() -> home(p, 0));
        b.put(49, Material.CHEST, "Readiness report", List.of("Commander: " + plugin.nodes.name(a.commanderUuid), "Muster: " + MilitaryCommands.muster(a)), () -> report(p, n, a, page));
        if (cmd.manages(p, n, a)) {
            b.put(46, Material.WRITABLE_BOOK, "Create division", List.of("Choose its type and name."), () -> types(p, b, a, page));
            b.put(47, Material.LODESTONE, "Set muster here", List.of("Use the territory under your feet."), () -> {
                cmd.execute(p, "muster", a.name);
                army(p, a, page);
            });
            b.put(52, Material.TNT, "Disband army", List.of("Removes the army, its divisions and their enlistments."), () -> confirm(p, b, "Disband " + a.name, () -> {
                cmd.execute(p, "army", "disband", a.name);
                home(p, 0);
            }, () -> army(p, a, page)));
        }
        if (cmd.national(p, n))
            b.put(51, Material.GOLDEN_HELMET, "Appoint army commander", List.of("Choose a citizen of your nation."), () -> armyCommander(p, n, a, 0));
        pages(b, page, list.size(), i -> army(p, a, i));
        p.openInventory(b.inv);
    }

    private void types(Player p, Board parent, Army a, int prev) {
        Board b = new Board(p, "Division type", a, null);
        int slot = 10;
        for (Type type : Type.values()) b.put(slot++, icon(type), type.name(), List.of("Choose a name next."), () -> prompt(p, b, "Enter the division name (2–24 letters, numbers, _ or -)", choice -> {
            if (cmd.execute(p, "division", "create", a.name, choice, type.name()))
                division(p, a, cmd.division(a, choice), 0);
            else
                army(p, a, prev);
        }));
        b.back(() -> army(p, parent.army, prev));
        p.openInventory(b.inv);
    }

    private static Material icon(Type t) {
        return switch(t) {
            case INFANTRY ->
                Material.SHIELD;
            case CAVALRY ->
                Material.SADDLE;
            case ARTILLERY ->
                Material.DISPENSER;
            case ENGINEERS ->
                Material.IRON_PICKAXE;
            case NAVAL ->
                Material.OAK_BOAT;
            case RECON ->
                Material.SPYGLASS;
        };
    }

    private void armyCommander(Player p, Nation n, Army a, int wanted) {
        Board b = new Board(p, "Appoint • " + a.name, a, null);
        var citizens = n.getResidents().stream()
                .sorted(Comparator.comparing(Resident::getName, String.CASE_INSENSITIVE_ORDER)).toList();
        int page = page(wanted, citizens.size());
        for (int i = page * PAGE_SIZE; i < Math.min(citizens.size(), (page + 1) * PAGE_SIZE); i++) {
            String name = citizens.get(i).getName();
            b.put(i % PAGE_SIZE, Material.PLAYER_HEAD, name, List.of("Appoint as army commander."), () -> {
                cmd.execute(p, "army", "commander", a.name, name);
                army(p, a, 0);
            });
        }
        b.back(() -> army(p, a, 0));
        pages(b, page, citizens.size(), i -> armyCommander(p, n, a, i));
        p.openInventory(b.inv);
    }

    private void report(Player p, Nation n, Army a, int page) {
        Board b = new Board(p, "Readiness • " + a.name, a, null);
        b.put(22, Material.CHEST, "Supply and strength", cmd.reportLines(n, a), null);
        b.back(() -> army(p, a, page));
        p.openInventory(b.inv);
    }

    private void division(Player p, Army a, Division d, int wanted) {
        Nation n = cmd.nation(p);
        Board b = new Board(p, "Division • " + d.name, a, d);
        int page = page(wanted, d.members.size());
        boolean manage = cmd.manages(p, n, a), lead = cmd.commands(p, n, a, d);
        for (int i = page * PAGE_SIZE; i < Math.min(d.members.size(), (page + 1) * PAGE_SIZE); i++) {
            UUID member = d.members.get(i);
            b.put(i % PAGE_SIZE, Material.PLAYER_HEAD, plugin.nodes.name(member), List.of("Rank: " + d.rank(member), member.equals(d.commanderUuid) ? "Division commander" : "Enlisted member"), lead ? () -> person(p, n, a, d, member, page) : null);
        }
        if (d.members.isEmpty())
            b.put(22, Material.BOOK, "Empty roster", List.of("Citizens may enlist below."), null);
        b.back(() -> army(p, a, 0));
        var membership = plugin.state.membership(p.getUniqueId());
        if (membership == null)
            b.put(46, Material.LIME_DYE, "Enlist", List.of("Join " + d.name + " as a private."), () -> {
                cmd.execute(p, "enlist", a.name, d.name);
                division(p, a, d, page);
            });
        if (membership == d)
            b.put(46, Material.OAK_DOOR, "Leave division", List.of("Withdraw from " + d.name + "."), () -> confirm(p, b, "Leave " + d.name, () -> {
                if (plugin.state.membership(p.getUniqueId()) == d)
                    cmd.execute(p, "leave");
                division(p, a, d, page);
            }, () -> division(p, a, d, page)));
        b.put(49, icon(d.type), d.name, List.of(d.type + " · " + d.members.size() + "/" + plugin.settings.maxMembers() + " personnel", "Commander: " + plugin.nodes.name(d.commanderUuid), "Order: " + MilitaryCommands.orderText(d)), null);
        if (lead)
            b.put(47, Material.FILLED_MAP, "Issue order", List.of("Use your location or enter a territory ID."), () -> orders(p, a, d, page));
        if (manage)
            b.put(52, Material.TNT, "Disband division", List.of("Clears this roster."), () -> confirm(p, b, "Disband " + d.name, () -> {
                cmd.execute(p, "division", "disband", a.name, d.name);
                army(p, a, 0);
            }, () -> division(p, a, d, page)));
        pages(b, page, d.members.size(), i -> division(p, a, d, i));
        p.openInventory(b.inv);
    }

    private void person(Player p, Nation n, Army a, Division d, UUID member, int page) {
        if (!d.members.contains(member)) {
            division(p, a, d, page);
            return;
        }
        Board b = new Board(p, "Personnel • " + d.name, a, d);
        Resident target = phonon.nodes.Nodes.INSTANCE.getResidentFromUUID(member);
        String name = plugin.nodes.name(member);
        b.put(4, Material.PLAYER_HEAD, name, List.of("Rank: " + d.rank(member)), null);
        if (target != null && target.getNation() == n && cmd.commands(p, n, a, d)) {
            int slot = 10;
            for (Rank rank : Rank.values()) b.put(slot++, Material.NAME_TAG, rank.name(), List.of("Set " + name + "'s rank."), () -> {
                cmd.execute(p, "division", "rank", a.name, d.name, name, rank.name());
                division(p, a, d, page);
            });
            if (cmd.manages(p, n, a))
                b.put(31, Material.GOLDEN_HELMET, "Appoint division commander", List.of(name), () -> {
                    cmd.execute(p, "division", "commander", a.name, d.name, name);
                    division(p, a, d, page);
                });
        } else
            b.put(22, Material.PAPER, "Read-only personnel record", List.of("Appointments require citizenship and command authority."), null);
        b.back(() -> division(p, a, d, page));
        p.openInventory(b.inv);
    }

    private void orders(Player p, Army a, Division d, int page) {
        Board b = new Board(p, "Orders • " + d.name, a, d);
        int slot = 20;
        for (Order order : List.of(Order.HOLD, Order.MOVE, Order.RETREAT)) {
            b.put(slot++, Material.FILLED_MAP, order.name(), List.of("Record this order at your current friendly territory."), () -> {
                var t = plugin.nodes.current(p);
                if (t == null)
                    MilitaryCommands.tell(p, "Stand in a friendly territory in the selected world.");
                else
                    cmd.execute(p, "order", a.name, d.name, order.name(), Integer.toString(plugin.nodes.id(t)));
                division(p, a, d, page);
            });
        }
        int remoteSlot = 29;
        for (Order order : List.of(Order.HOLD, Order.MOVE, Order.RETREAT)) {
            b.put(remoteSlot++, Material.WRITABLE_BOOK, order.name() + " at territory ID",
                    List.of("Enter the ID of a territory belonging to your nation."),
                    () -> prompt(p, b, "Enter a friendly territory ID", input -> {
                        cmd.execute(p, "order", a.name, d.name, order.name(), input);
                        division(p, a, d, page);
                    }));
        }
        b.back(() -> division(p, a, d, page));
        p.openInventory(b.inv);
    }

    private void confirm(Player p, Board src, String label, Runnable apply, Runnable back) {
        Board b = new Board(p, "Confirm action", src.army, src.division);
        b.put(20, Material.RED_CONCRETE, label, List.of("Click to confirm."), apply);
        b.put(24, Material.GREEN_CONCRETE, "Cancel", List.of("Return without applying this change."), back);
        b.back(back);
        p.openInventory(b.inv);
    }

    private void prompt(Player p, Board view, String instruction, Consumer<String> apply) {
        if (p.isConversing()) {
            MilitaryCommands.tell(p, "Finish your current prompt first.");
            return;
        }
        p.closeInventory();
        Conversation c = new ConversationFactory(plugin).withModality(false).withLocalEcho(false).withEscapeSequence("cancel").withTimeout(120).withFirstPrompt(new StringPrompt() {

            public String getPromptText(ConversationContext context) {
                return instruction + ", or type cancel.";
            }

            public Prompt acceptInput(ConversationContext context, String input) {
                if (input != null)
                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                        if (view.valid(p))
                            apply.accept(input.trim());
                        else if (p.isOnline())
                            MilitaryCommands.tell(p, "Your formation changed. Reopen the military menu.");
                    });
                return Prompt.END_OF_CONVERSATION;
            }
        }).addConversationAbandonedListener(event -> prompts.remove(p.getUniqueId())).buildConversation(p);
        prompts.put(p.getUniqueId(), c);
        c.begin();
    }
}

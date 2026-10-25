package es.dedsafio.feature;

import es.dedsafio.Dedsafio;
import es.dedsafio.Msg;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import es.dedsafio.Util;

import java.util.ArrayList;
import java.util.List;

/** Tienda paginada donde todo se paga en deditas. Los precios salen de config.yml. */
public final class ShopMenu implements TabExecutor, Listener {
    private static final int[] SLOTS = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34, 37, 38, 39, 40, 41, 42, 43};

    private final Dedsafio plugin;
    private final List<String> catalog = new ArrayList<>();

    private static final class Holder implements InventoryHolder {
        int page;
        Inventory inv;

        @Override
        public Inventory getInventory() { return inv; }
    }

    public ShopMenu(Dedsafio plugin) {
        this.plugin = plugin;
        reloadCatalog();
    }

    public void reloadCatalog() {
        catalog.clear();
        for (String id : plugin.items().ids()) if (price(id) > 0) catalog.add(id);
    }

    private int price(String id) { return plugin.getConfig().getInt("shop.prices." + id, 0); }

    public void open(Player p, int page) {
        int pages = Math.max(1, (catalog.size() + SLOTS.length - 1) / SLOTS.length);
        page = Math.max(0, Math.min(page, pages - 1));
        Holder h = new Holder();
        h.page = page;
        Inventory inv = Bukkit.createInventory(h, 54,
                Msg.c("<gradient:#ff9a3c:#ffd166><bold>Tienda de Deditas</bold></gradient> <dark_gray>(" + (page + 1) + "/" + pages + ")"));
        h.inv = inv;

        for (int i = 0; i < 54; i++) {
            boolean border = i < 9 || i > 44 || i % 9 == 0 || i % 9 == 8;
            if (border) inv.setItem(i, named(i % 2 == 0 ? Material.ORANGE_STAINED_GLASS_PANE : Material.BLACK_STAINED_GLASS_PANE, " "));
        }
        for (int i = 0; i < SLOTS.length; i++) {
            int idx = page * SLOTS.length + i;
            if (idx >= catalog.size()) break;
            String id = catalog.get(idx);
            ItemStack it = plugin.items().create(id);
            ItemMeta m = it.getItemMeta();
            List<Component> lore = m.lore() == null ? new ArrayList<>() : new ArrayList<>(m.lore());
            lore.add(Msg.c(""));
            lore.add(Msg.c("<gray>Precio: " + EconomyManager.fmt(price(id))));
            lore.add(Msg.c("<yellow>▶ Clic para comprar"));
            m.lore(lore);
            it.setItemMeta(m);
            inv.setItem(SLOTS[i], it);
        }
        ItemStack bal = plugin.items().create("dedita_rare");
        ItemMeta bm = bal.getItemMeta();
        bm.displayName(Msg.c("<gold><bold>Tu saldo"));
        bm.lore(List.of(Msg.c(EconomyManager.fmt(plugin.economy().balance(p.getUniqueId())))));
        bal.setItemMeta(bm);
        inv.setItem(4, bal);
        if (page > 0) inv.setItem(45, named(Material.ARROW, "<yellow>◀ Anterior"));
        if (page < pages - 1) inv.setItem(53, named(Material.ARROW, "<yellow>Siguiente ▶"));
        inv.setItem(49, named(Material.BARRIER, "<red>Cerrar"));
        p.openInventory(inv);
    }

    private ItemStack named(Material m, String name) {
        ItemStack it = new ItemStack(m);
        ItemMeta meta = it.getItemMeta();
        meta.displayName(Msg.c(name));
        it.setItemMeta(meta);
        return it;
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getView().getTopInventory().getHolder() instanceof Holder h)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (e.getClickedInventory() != e.getView().getTopInventory()) return;
        int slot = e.getSlot();
        if (slot == 45 && h.page > 0) { open(p, h.page - 1); return; }
        if (slot == 53) { open(p, h.page + 1); return; }
        if (slot == 49) { p.closeInventory(); return; }
        for (int i = 0; i < SLOTS.length; i++) {
            if (SLOTS[i] != slot) continue;
            int idx = h.page * SLOTS.length + i;
            if (idx >= catalog.size()) return;
            String id = catalog.get(idx);
            int cost = price(id);
            if (!plugin.economy().take(p.getUniqueId(), cost)) {
                p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                Msg.bar(p, "<red>No tienes suficientes deditas.");
                return;
            }
            Util.give(p, plugin.items().create(id));
            p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.6f, 1.6f);
            Msg.bar(p, "<green>Compra realizada <gray>· Saldo: " + EconomyManager.fmt(plugin.economy().balance(p.getUniqueId())));
            open(p, h.page);
            return;
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof Holder) e.setCancelled(true);
    }

    @Override
    public boolean onCommand(CommandSender s, Command c, String l, String[] a) {
        if (!(s instanceof Player p)) { Msg.send(s, "Solo jugadores."); return true; }
        open(p, 0);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command c, String l, String[] a) { return List.of(); }
}

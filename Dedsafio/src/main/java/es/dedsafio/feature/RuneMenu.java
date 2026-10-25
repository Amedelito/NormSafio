package es.dedsafio.feature;

import es.dedsafio.Dedsafio;
import es.dedsafio.Msg;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

/** Menú de la Mesa de Runas: sube encantamientos del objeto en mano a cambio de deditas. */
public final class RuneMenu implements Listener {
    private record Rune(Enchantment ench, String name, String color, int cost) {}

    private static final List<Rune> RUNES = List.of(
            new Rune(Enchantment.SHARPNESS, "Runa del Filo", "#e63946", 8),
            new Rune(Enchantment.PROTECTION, "Runa de Coraza", "#457b9d", 8),
            new Rune(Enchantment.UNBREAKING, "Runa Eterna", "#8d99ae", 6),
            new Rune(Enchantment.EFFICIENCY, "Runa Veloz", "#ffd166", 6),
            new Rune(Enchantment.KNOCKBACK, "Runa de Impulso", "#c77dff", 5));
    private static final int[] SLOTS = {11, 12, 13, 14, 15};

    private final Dedsafio plugin;

    private static final class Holder implements InventoryHolder {
        Inventory inv;

        @Override
        public Inventory getInventory() { return inv; }
    }

    public RuneMenu(Dedsafio plugin) { this.plugin = plugin; }

    public void open(Player p) {
        Holder h = new Holder();
        Inventory inv = Bukkit.createInventory(h, 27, Msg.c("<gradient:#c77dff:#7b2cbf><bold>Mesa de Runas"));
        h.inv = inv;
        ItemStack hand = p.getInventory().getItemInMainHand();
        int max = plugin.getConfig().getInt("rune-max-level", 5);

        for (int i = 0; i < 27; i++) inv.setItem(i, pane());
        for (int i = 0; i < RUNES.size(); i++) {
            Rune r = RUNES.get(i);
            ItemStack book = new ItemStack(Material.ENCHANTED_BOOK);
            ItemMeta m = book.getItemMeta();
            m.displayName(Msg.c("<" + r.color() + "><bold>" + r.name()));
            if (es.dedsafio.Util.isAir(hand) || !r.ench().canEnchantItem(hand)) {
                m.lore(List.of(Msg.c("<red>No aplicable al objeto en tu mano.")));
            } else {
                int lvl = hand.getEnchantmentLevel(r.ench());
                m.lore(List.of(
                        Msg.c("<gray>Nivel actual: <white>" + lvl + "<dark_gray>/" + max),
                        Msg.c(lvl >= max ? "<red>Nivel máximo" : "<gray>Coste: " + EconomyManager.fmt(r.cost() * (lvl + 1))),
                        Msg.c(lvl >= max ? "" : "<yellow>▶ Clic para aplicar")));
            }
            book.setItemMeta(m);
            inv.setItem(SLOTS[i], book);
        }
        if (!es.dedsafio.Util.isAir(hand)) inv.setItem(22, hand.clone());
        p.openInventory(inv);
        p.playSound(p.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1f, 1f);
    }

    private ItemStack pane() {
        ItemStack it = new ItemStack(Material.PURPLE_STAINED_GLASS_PANE);
        ItemMeta m = it.getItemMeta();
        m.displayName(Msg.c(" "));
        it.setItemMeta(m);
        return it;
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getView().getTopInventory().getHolder() instanceof Holder)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (e.getClickedInventory() != e.getView().getTopInventory()) return;
        for (int i = 0; i < SLOTS.length; i++) {
            if (SLOTS[i] != e.getSlot()) continue;
            Rune r = RUNES.get(i);
            ItemStack hand = p.getInventory().getItemInMainHand();
            int max = plugin.getConfig().getInt("rune-max-level", 5);
            if (es.dedsafio.Util.isAir(hand) || !r.ench().canEnchantItem(hand)) {
                Msg.bar(p, "<red>Esa runa no se puede aplicar a este objeto.");
                return;
            }
            int lvl = hand.getEnchantmentLevel(r.ench());
            if (lvl >= max) { Msg.bar(p, "<red>Ya está al nivel máximo."); return; }
            int cost = r.cost() * (lvl + 1);
            if (!plugin.economy().take(p.getUniqueId(), cost)) {
                p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                Msg.bar(p, "<red>No tienes suficientes deditas.");
                return;
            }
            hand.addUnsafeEnchantment(r.ench(), lvl + 1);
            p.playSound(p.getLocation(), Sound.BLOCK_ANVIL_USE, 0.6f, 1.4f);
            Msg.bar(p, "<green>" + r.name() + " aplicada <gray>(nivel " + (lvl + 1) + ")");
            open(p);
            return;
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof Holder) e.setCancelled(true);
    }
}

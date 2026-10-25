package es.dedsafio.feature;

import es.dedsafio.Dedsafio;
import es.dedsafio.Msg;
import es.dedsafio.Util;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.*;

/** Economía de deditas: saldo virtual + objetos físicos (común 1, rara 10, legendaria 100). */
public final class EconomyManager implements TabExecutor, Listener {
    private final Dedsafio plugin;
    private final File file;
    private final YamlConfiguration data;
    private boolean dirty;

    public EconomyManager(Dedsafio plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "economy.yml");
        this.data = YamlConfiguration.loadConfiguration(file);
        Bukkit.getScheduler().runTaskTimer(plugin, this::save, 6000L, 6000L);
    }

    public int balance(UUID id) { return data.getInt("b." + id, 0); }

    public void set(UUID id, int v) { data.set("b." + id, Math.max(0, v)); dirty = true; }

    public void add(UUID id, int v) { set(id, balance(id) + v); }

    public boolean take(UUID id, int v) {
        if (balance(id) < v) return false;
        set(id, balance(id) - v);
        return true;
    }

    public void save() {
        if (!dirty) return;
        try {
            data.save(file);
            dirty = false;
        } catch (IOException e) {
            plugin.getLogger().warning("No se pudo guardar economy.yml: " + e.getMessage());
        }
    }

    public static String fmt(int n) {
        return "<gold>" + n + "</gold> <yellow>dedita" + (n == 1 ? "" : "s") + "</yellow>";
    }

    // ───────── Ingreso de objetos con clic derecho ─────────
    @EventHandler
    public void onUse(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Player p = e.getPlayer();
        ItemStack hand = p.getInventory().getItemInMainHand();
        int value = plugin.items().deditaValue(hand);
        if (value <= 0) return;
        e.setCancelled(true);
        int amount = p.isSneaking() ? hand.getAmount() : 1;
        hand.setAmount(hand.getAmount() - amount);
        add(p.getUniqueId(), value * amount);
        p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.4f);
        Msg.bar(p, "<green>+ " + fmt(value * amount) + " <gray>· Saldo: " + fmt(balance(p.getUniqueId())));
    }

    // ───────── Comando /deditas ─────────
    @Override
    public boolean onCommand(CommandSender s, Command cmd, String label, String[] a) {
        String sub = a.length == 0 ? "saldo" : a[0].toLowerCase();
        switch (sub) {
            case "saldo" -> {
                if (!(s instanceof Player p)) { Msg.send(s, "Solo jugadores."); return true; }
                Msg.send(s, "Tu saldo: " + fmt(balance(p.getUniqueId())));
            }
            case "ver" -> {
                if (a.length < 2) { Msg.send(s, "Uso: /deditas ver <jugador>"); return true; }
                OfflinePlayer t = Bukkit.getOfflinePlayer(a[1]);
                Msg.send(s, "<white>" + Msg.esc(a[1]) + "<gray> tiene " + fmt(balance(t.getUniqueId())));
            }
            case "pagar" -> {
                if (!(s instanceof Player p)) return true;
                if (a.length < 3) { Msg.send(s, "Uso: /deditas pagar <jugador> <cantidad>"); return true; }
                Player t = Bukkit.getPlayerExact(a[1]);
                int n = Util.parseInt(a[2]);
                if (t == null || t.equals(p)) { Msg.send(s, "<red>Jugador no válido."); return true; }
                if (n <= 0) { Msg.send(s, "<red>Cantidad no válida."); return true; }
                if (!take(p.getUniqueId(), n)) { Msg.send(s, "<red>No tienes suficientes deditas."); return true; }
                add(t.getUniqueId(), n);
                Msg.send(p, "Pagaste " + fmt(n) + " <gray>a <white>" + Msg.esc(t.getName()));
                Msg.send(t, "<white>" + Msg.esc(p.getName()) + " <gray>te pagó " + fmt(n));
            }
            case "retirar" -> {
                if (!(s instanceof Player p)) return true;
                int n = a.length > 1 ? Util.parseInt(a[1]) : -1;
                if (n <= 0) { Msg.send(s, "Uso: /deditas retirar <cantidad>"); return true; }
                if (!take(p.getUniqueId(), n)) { Msg.send(s, "<red>No tienes suficientes deditas."); return true; }
                int legend = n / 100, rare = (n % 100) / 10, common = n % 10;
                if (legend > 0) Util.give(p, plugin.items().create("dedita_legendary", legend));
                if (rare > 0) Util.give(p, plugin.items().create("dedita_rare", rare));
                if (common > 0) Util.give(p, plugin.items().create("dedita_common", common));
                Msg.send(p, "Retiraste " + fmt(n) + "<gray>.");
            }
            case "depositar" -> {
                if (!(s instanceof Player p)) return true;
                int total = 0;
                ItemStack[] contents = p.getInventory().getStorageContents();
                for (int i = 0; i < contents.length; i++) {
                    int v = plugin.items().deditaValue(contents[i]);
                    if (v > 0) {
                        total += v * contents[i].getAmount();
                        p.getInventory().setItem(i, null);
                    }
                }
                if (total == 0) { Msg.send(s, "<red>No llevas deditas encima."); return true; }
                add(p.getUniqueId(), total);
                Msg.send(p, "Ingresaste " + fmt(total) + "<gray>. Saldo: " + fmt(balance(p.getUniqueId())));
            }
            case "top" -> {
                ConfigurationSection sec = data.getConfigurationSection("b");
                if (sec == null) { Msg.send(s, "Aún no hay datos."); return true; }
                List<Map.Entry<String, Integer>> list = new ArrayList<>();
                for (String k : sec.getKeys(false)) list.add(Map.entry(k, sec.getInt(k)));
                list.sort((x, y) -> y.getValue() - x.getValue());
                Msg.send(s, "<gradient:#ff9a3c:#ffd166><bold>Top deditas");
                for (int i = 0; i < Math.min(10, list.size()); i++) {
                    String name = Bukkit.getOfflinePlayer(UUID.fromString(list.get(i).getKey())).getName();
                    Msg.send(s, "<yellow>" + (i + 1) + ". <white>" + Msg.esc(name == null ? "?" : name) + " <gray>— " + fmt(list.get(i).getValue()));
                }
            }
            case "dar", "quitar", "fijar" -> {
                if (!s.hasPermission("dedsafio.admin")) { Msg.send(s, "<red>Sin permiso."); return true; }
                if (a.length < 3) { Msg.send(s, "Uso: /deditas " + sub + " <jugador> <cantidad>"); return true; }
                OfflinePlayer t = Bukkit.getOfflinePlayer(a[1]);
                int n = Util.parseInt(a[2]);
                if (n < 0) { Msg.send(s, "<red>Cantidad no válida."); return true; }
                switch (sub) {
                    case "dar" -> add(t.getUniqueId(), n);
                    case "quitar" -> set(t.getUniqueId(), balance(t.getUniqueId()) - n);
                    default -> set(t.getUniqueId(), n);
                }
                Msg.send(s, "Saldo de <white>" + Msg.esc(a[1]) + "<gray>: " + fmt(balance(t.getUniqueId())));
            }
            default -> Msg.send(s, "Uso: /deditas [saldo|ver|pagar|retirar|depositar|top]");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command cmd, String label, String[] a) {
        List<String> subs = new ArrayList<>(List.of("saldo", "ver", "pagar", "retirar", "depositar", "top"));
        if (s.hasPermission("dedsafio.admin")) subs.addAll(List.of("dar", "quitar", "fijar"));
        if (a.length == 1) return Util.filter(subs, a[0]);
        if (a.length == 2 && List.of("ver", "pagar", "dar", "quitar", "fijar").contains(a[0].toLowerCase()))
            return Util.filter(Bukkit.getOnlinePlayers().stream().map(Player::getName).toList(), a[1]);
        return List.of();
    }
}

package es.dedsafio.feature;

import es.dedsafio.Dedsafio;
import es.dedsafio.Msg;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Bonus por set completo de armadura (Dungeon, Earth, Dimensional). */
public final class ArmorListener implements Listener {
    private static final String[] EXPECT = {"boots", "leggings", "chestplate", "helmet"};
    private final Dedsafio plugin;
    private final Map<UUID, String> active = new HashMap<>();

    public ArmorListener(Dedsafio plugin) {
        this.plugin = plugin;
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    /** Devuelve el nombre del set si el jugador lleva las 4 piezas del mismo set, o null. */
    public String fullSet(Player p) {
        ItemStack[] arm = p.getInventory().getArmorContents(); // botas, grebas, peto, casco
        String set = null;
        for (int i = 0; i < 4; i++) {
            String id = plugin.items().idOf(arm[i]);
            if (id == null || !id.endsWith("_" + EXPECT[i])) return null;
            String s = id.substring(0, id.length() - EXPECT[i].length() - 1);
            if (set == null) set = s;
            else if (!set.equals(s)) return null;
        }
        return set;
    }

    private void tick() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            String set = fullSet(p);
            String prev = active.get(p.getUniqueId());
            if (!Objects.equals(set, prev)) {
                if (set == null) active.remove(p.getUniqueId());
                else {
                    active.put(p.getUniqueId(), set);
                    if (!set.startsWith("boxing")) {
                        p.playSound(p.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 0.6f, 1.5f);
                        Msg.bar(p, "<gradient:#ff9a3c:#ffd166>Set " + set + " completo <gray>· bonus activado");
                    }
                }
            }
            if (set == null) continue;
            switch (set) {
                case "dungeon" -> {
                    eff(p, PotionEffectType.RESISTANCE, 0);
                    eff(p, PotionEffectType.FIRE_RESISTANCE, 0);
                }
                case "earth" -> {
                    eff(p, PotionEffectType.HASTE, 1);
                    eff(p, PotionEffectType.HEALTH_BOOST, 0);
                }
                case "dimensional" -> {
                    eff(p, PotionEffectType.SPEED, 0);
                    eff(p, PotionEffectType.JUMP_BOOST, 0);
                }
                default -> { }
            }
        }
    }

    private void eff(Player p, PotionEffectType t, int amp) {
        p.addPotionEffect(new PotionEffect(t, 60, amp, true, false, true));
    }

    @EventHandler(ignoreCancelled = true)
    public void onFall(EntityDamageEvent e) {
        if (e.getCause() != EntityDamageEvent.DamageCause.FALL || !(e.getEntity() instanceof Player p)) return;
        if ("dimensional".equals(fullSet(p))) e.setCancelled(true);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) { active.remove(e.getPlayer().getUniqueId()); }
}

package es.dedsafio.feature;

import es.dedsafio.Dedsafio;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityResurrectEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/** Tótems personalizados: Eónico, Férreo, de Nutria y de Flashbang. */
public final class TotemListener implements Listener {
    private final Dedsafio plugin;

    public TotemListener(Dedsafio plugin) { this.plugin = plugin; }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPop(EntityResurrectEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;
        EquipmentSlot hand = e.getHand();
        ItemStack used;
        if (hand != null) {
            used = p.getInventory().getItem(hand);
        } else {
            used = p.getInventory().getItemInMainHand();
            if (plugin.items().idOf(used) == null) used = p.getInventory().getItemInOffHand();
        }
        String id = plugin.items().idOf(used);
        if (id == null || !id.startsWith("totem_")) return;
        // Un tick después, para que se apliquen tras los efectos vanilla del tótem
        Bukkit.getScheduler().runTask(plugin, () -> apply(p, id));
    }

    private void apply(Player p, String id) {
        World w = p.getWorld();
        Location l = p.getLocation().add(0, 1, 0);
        switch (id) {
            case "totem_eonic" -> {
                p.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 400, 1));
                p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 200, 1));
                p.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 400, 2));
                w.spawnParticle(Particle.END_ROD, l, 80, 0.6, 1, 0.6, 0.15);
                w.playSound(l, Sound.BLOCK_BEACON_POWER_SELECT, 1f, 1.5f);
            }
            case "totem_iron" -> {
                p.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 160, 2));
                p.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 400, 3));
                w.spawnParticle(Particle.CRIT, l, 60, 0.6, 1, 0.6, 0.3);
                w.playSound(l, Sound.BLOCK_ANVIL_LAND, 0.8f, 0.8f);
            }
            case "totem_otter" -> {
                p.addPotionEffect(new PotionEffect(PotionEffectType.DOLPHINS_GRACE, 600, 0));
                p.addPotionEffect(new PotionEffect(PotionEffectType.WATER_BREATHING, 1200, 0));
                p.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 200, 0));
                w.spawnParticle(Particle.SPLASH, l, 100, 0.7, 1, 0.7, 0.1);
                w.playSound(l, Sound.ENTITY_PLAYER_SPLASH, 1f, 1.2f);
                w.playSound(l, Sound.ENTITY_DOLPHIN_PLAY, 1f, 1.3f);
            }
            case "totem_flashbang" -> {
                w.spawnParticle(Particle.FLASH, l, 3, 0.2, 0.2, 0.2, 0);
                w.playSound(l, Sound.ENTITY_FIREWORK_ROCKET_BLAST, 2f, 0.6f);
                w.playSound(l, Sound.ENTITY_GENERIC_EXPLODE, 1f, 1.6f);
                for (LivingEntity le : w.getNearbyLivingEntities(l, 8)) {
                    if (le.equals(p)) continue;
                    le.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, le instanceof Player ? 80 : 120, 0));
                    le.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 1));
                }
            }
            default -> { }
        }
    }
}

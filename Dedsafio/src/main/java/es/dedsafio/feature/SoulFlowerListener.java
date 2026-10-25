package es.dedsafio.feature;

import es.dedsafio.Dedsafio;
import es.dedsafio.Msg;
import es.dedsafio.Util;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/** Flor Recolectora de Almas y Cuchara de la Resurrección. */
public final class SoulFlowerListener implements Listener {
    private final Dedsafio plugin;

    public SoulFlowerListener(Dedsafio plugin) { this.plugin = plugin; }

    private ItemStack findFlower(Player p) {
        ItemStack off = p.getInventory().getItemInOffHand();
        if (plugin.items().is(off, "soul_flower")) return off;
        for (int i = 0; i < 9; i++) {
            ItemStack it = p.getInventory().getItem(i);
            if (plugin.items().is(it, "soul_flower")) return it;
        }
        return null;
    }

    @EventHandler
    public void onKill(EntityDeathEvent e) {
        Player killer = e.getEntity().getKiller();
        if (killer == null) return;
        int gain = e.getEntity() instanceof Player ? 5 : (e.getEntity() instanceof Monster ? 1 : 0);
        if (gain == 0) return;
        ItemStack flower = findFlower(killer);
        if (flower == null) return;
        int before = plugin.items().souls(flower);
        plugin.items().setSouls(flower, before + gain);
        int after = plugin.items().souls(flower);
        if (after > before) {
            killer.getWorld().spawnParticle(Particle.SOUL, e.getEntity().getLocation().add(0, 1, 0), 8, 0.3, 0.5, 0.3, 0.05);
            Msg.bar(killer, "<#c77dff>+" + (after - before) + " alma" + (after - before == 1 ? "" : "s") + " <dark_gray>(" + after + "/100)");
        }
    }

    @EventHandler
    public void onUse(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (e.getHand() == null) return;
        Player p = e.getPlayer();
        ItemStack it = e.getHand() == EquipmentSlot.HAND ? p.getInventory().getItemInMainHand() : p.getInventory().getItemInOffHand();
        String id = plugin.items().idOf(it);
        if (id == null) return;

        if (id.equals("resurrection_spoon")) {
            if (e.getHand() == EquipmentSlot.HAND) Msg.bar(p, "<gray>Es una cuchara. No hace nada... <dark_gray>(¿seguro?)");
            return;
        }
        if (!id.equals("soul_flower")) return;
        e.setCancelled(true);
        int souls = plugin.items().souls(it);
        if (souls < 10) {
            p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            Msg.bar(p, "<red>Necesitas 10 almas <gray>(tienes " + souls + ")");
            return;
        }
        plugin.items().setSouls(it, souls - 10);
        AttributeInstance max = p.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        p.setHealth(Math.min(max == null ? 20 : max.getValue(), p.getHealth() + 8));
        p.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 200, 1));
        p.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, p.getLocation().add(0, 1, 0), 40, 0.5, 0.8, 0.5, 0.05);
        p.playSound(p.getLocation(), Sound.ENTITY_ZOMBIE_VILLAGER_CURE, 0.8f, 1.6f);
        Msg.bar(p, "<#c77dff>Almas consumidas <green>+4❤");
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        if (plugin.items().is(e.getItemInHand(), "soul_flower")) e.setCancelled(true);
    }
}

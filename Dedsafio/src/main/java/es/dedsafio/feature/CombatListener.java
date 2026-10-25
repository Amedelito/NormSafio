package es.dedsafio.feature;

import es.dedsafio.Dedsafio;
import es.dedsafio.Msg;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/** Habilidades de las espadas legendarias y de las exclusivas del Gulag del Espacio. */
public final class CombatListener implements Listener {
    private final Dedsafio plugin;
    private final Random rng = new Random();
    private final Map<UUID, Integer> belicHits = new HashMap<>();

    public CombatListener(Dedsafio plugin) { this.plugin = plugin; }

    private void heal(Player p, double amount) {
        AttributeInstance max = p.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        double cap = max == null ? 20 : max.getValue();
        p.setHealth(Math.min(cap, p.getHealth() + amount));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof Player p) || !(e.getEntity() instanceof LivingEntity victim)) return;
        String id = plugin.items().idOf(p.getInventory().getItemInMainHand());
        if (id == null) return;
        World w = victim.getWorld();
        Location vl = victim.getLocation().add(0, 1, 0);

        switch (id) {
            case "ancestral_sword" -> {
                if (rng.nextDouble() < 0.20) {
                    heal(p, 2);
                    w.spawnParticle(Particle.SOUL, vl, 12, 0.3, 0.5, 0.3, 0.05);
                    w.playSound(vl, Sound.PARTICLE_SOUL_ESCAPE, 1f, 0.8f);
                    Msg.bar(p, "<#d4a373>Sed Ancestral <red>+2❤");
                }
            }
            case "belic_sword" -> {
                int hits = belicHits.merge(p.getUniqueId(), 1, Integer::sum);
                if (hits >= 5) {
                    belicHits.put(p.getUniqueId(), 0);
                    e.setDamage(e.getDamage() * 1.5);
                    victim.setFireTicks(60);
                    w.spawnParticle(Particle.CRIT, vl, 25, 0.4, 0.5, 0.4, 0.3);
                    w.playSound(vl, Sound.ENTITY_PLAYER_ATTACK_CRIT, 1f, 0.7f);
                    Msg.bar(p, "<#e63946>¡Furia Bélica!");
                } else {
                    Msg.bar(p, "<#e63946>Furia: <white>" + hits + "<dark_gray>/5");
                }
            }
            case "celestial_sword" -> {
                victim.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 60, 0, false, false));
                w.spawnParticle(Particle.ELECTRIC_SPARK, vl, 15, 0.3, 0.5, 0.3, 0.2);
            }
            case "space_stellar_sword" -> {
                victim.addPotionEffect(new PotionEffect(PotionEffectType.LEVITATION, 25, 1, false, true));
                w.spawnParticle(Particle.END_ROD, vl, 15, 0.3, 0.5, 0.3, 0.05);
            }
            default -> { }
        }
    }

    @EventHandler
    public void onKill(EntityDeathEvent e) {
        Player killer = e.getEntity().getKiller();
        if (killer == null) return;
        if (plugin.items().is(killer.getInventory().getItemInMainHand(), "belic_sword")) {
            killer.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 100, 0));
            Msg.bar(killer, "<#e63946>Fuerza Bélica <gray>(5s)");
        }
    }

    @EventHandler
    public void onUse(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Player p = e.getPlayer();
        String id = plugin.items().idOf(p.getInventory().getItemInMainHand());
        if (id == null) return;
        if (id.equals("celestial_sword")) celestialStrike(p);
        else if (id.equals("space_void_sword")) voidPulse(p);
    }

    private void celestialStrike(Player p) {
        if (!plugin.cooldowns().use(p, "celestial", 15000)) return;
        Location eye = p.getEyeLocation();
        Vector dir = eye.getDirection();
        World w = p.getWorld();
        Location target;
        RayTraceResult hit = w.rayTraceEntities(eye, dir, 25, 0.8, en -> en instanceof LivingEntity && !en.equals(p));
        if (hit != null && hit.getHitEntity() != null) {
            target = hit.getHitEntity().getLocation();
        } else {
            RayTraceResult blk = w.rayTraceBlocks(eye, dir, 25);
            target = blk != null ? blk.getHitPosition().toLocation(w) : eye.clone().add(dir.clone().multiply(15));
        }
        w.strikeLightningEffect(target);
        w.spawnParticle(Particle.FLASH, target, 1);
        for (LivingEntity le : w.getNearbyLivingEntities(target, 3)) {
            if (le.equals(p)) continue;
            le.damage(8, p);
            le.setFireTicks(40);
        }
        Msg.bar(p, "<#ffd166>⚡ Rayo Celestial");
    }

    private void voidPulse(Player p) {
        if (!plugin.cooldowns().use(p, "void", 8000)) return;
        Location c = p.getLocation();
        World w = p.getWorld();
        w.spawnParticle(Particle.PORTAL, c.clone().add(0, 1, 0), 120, 2.5, 1, 2.5, 0.8);
        w.spawnParticle(Particle.DRAGON_BREATH, c.clone().add(0, 0.3, 0), 60, 2.5, 0.2, 2.5, 0.02);
        w.playSound(c, Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 0.5f);
        for (LivingEntity le : w.getNearbyLivingEntities(c, 6)) {
            if (le.equals(p)) continue;
            Vector away = le.getLocation().toVector().subtract(c.toVector()).setY(0);
            if (away.lengthSquared() < 0.01) away = new Vector(1, 0, 0);
            le.setVelocity(away.normalize().multiply(1.4).setY(0.5));
        }
        Msg.bar(p, "<#7b2cbf>Pulso del Vacío");
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) { belicHits.remove(e.getPlayer().getUniqueId()); }
}

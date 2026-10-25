package es.dedsafio.feature;

import es.dedsafio.Dedsafio;
import es.dedsafio.Msg;
import es.dedsafio.Util;
import net.kyori.adventure.bossbar.BossBar;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.*;

/** Retos por oleadas con dificultad dinámica hasta derrotar al jefe final. */
public final class ChallengeManager implements TabExecutor, Listener {
    private static final Map<String, Double> DIFF = new LinkedHashMap<>();

    static {
        DIFF.put("facil", 0.75);
        DIFF.put("normal", 1.0);
        DIFF.put("dificil", 1.4);
        DIFF.put("extremo", 2.0);
    }

    private static final class Challenge {
        final Player player;
        final String diffName;
        final double base;
        double dyn = 1.0;
        int wave = 0;
        int countdown = 5;
        boolean spawned;
        final Set<UUID> mobs = new HashSet<>();
        BossBar bar;
        UUID boss;
        int bossTicks;

        Challenge(Player p, String diffName, double base) {
            this.player = p;
            this.diffName = diffName;
            this.base = base;
        }

        double factor() { return base * dyn; }
    }

    private final Dedsafio plugin;
    private final NamespacedKey mobKey;
    private final Map<UUID, Challenge> active = new HashMap<>();
    private final Random rng = new Random();

    public ChallengeManager(Dedsafio plugin) {
        this.plugin = plugin;
        this.mobKey = new NamespacedKey(plugin, "challenge_mob");
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    private int totalWaves() { return Math.max(2, plugin.getConfig().getInt("challenge.waves", 8)); }

    public boolean isInChallenge(Player p) { return active.containsKey(p.getUniqueId()); }

    public void stopAll() {
        for (Challenge c : new ArrayList<>(active.values())) stop(c);
    }

    // ───────── Ciclo ─────────
    private void start(Player p, String diff) {
        double base = DIFF.get(diff);
        Challenge c = new Challenge(p, diff, base);
        active.put(p.getUniqueId(), c);
        Msg.title(p, "<gradient:#e63946:#ffd166><bold>¡RETO!", "<gray>Dificultad: <white>" + diff + " <dark_gray>· <gray>" + totalWaves() + " oleadas");
        p.playSound(p.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 0.6f, 1.2f);
    }

    private void stop(Challenge c) {
        for (UUID id : c.mobs) {
            Entity en = Bukkit.getEntity(id);
            if (en != null) en.remove();
        }
        c.mobs.clear();
        if (c.bar != null) c.player.hideBossBar(c.bar);
        active.remove(c.player.getUniqueId());
    }

    private void tick() {
        for (Challenge c : new ArrayList<>(active.values())) {
            Player p = c.player;
            if (!p.isOnline() || p.isDead()) { stop(c); continue; }
            if (plugin.gulags().isInGulag(p)) { Msg.send(p, "<red>Reto cancelado: entraste a un gulag."); stop(c); continue; }

            if (!c.spawned) {
                if (--c.countdown <= 0) spawnWave(c);
                else Msg.bar(p, "<yellow>Oleada <white>" + (c.wave + 1) + "<yellow> en <white>" + c.countdown + "s");
                continue;
            }
            c.mobs.removeIf(id -> {
                Entity en = Bukkit.getEntity(id);
                return en == null || en.isDead() || !en.isValid();
            });
            for (UUID id : c.mobs) {
                if (Bukkit.getEntity(id) instanceof Mob m && m.getTarget() == null) m.setTarget(p);
            }
            if (c.boss != null) bossTick(c);
            if (c.mobs.isEmpty()) cleared(c);
        }
    }

    private void bossTick(Challenge c) {
        Entity en = Bukkit.getEntity(c.boss);
        if (!(en instanceof LivingEntity boss) || boss.isDead()) return;
        AttributeInstance max = boss.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        double m = max == null ? 100 : max.getValue();
        c.bar.progress((float) Math.max(0, Math.min(1, boss.getHealth() / m)));
        if (boss.getHealth() < m * 0.5 && !boss.hasPotionEffect(PotionEffectType.SPEED))
            boss.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, Integer.MAX_VALUE, 0, true, false));
        if (++c.bossTicks % 15 == 0) {
            for (int i = 0; i < 2; i++) {
                Location l = findSpawn(boss.getLocation(), 2, 4);
                spawnMob(c, EntityType.ZOMBIE, l, 1 + c.factor() * 0.5, 1 + c.factor() * 0.25);
            }
            Msg.bar(c.player, "<red>¡El jefe invoca refuerzos!");
        }
    }

    private void cleared(Challenge c) {
        Player p = c.player;
        int reward = plugin.getConfig().getInt("challenge.wave-reward", 3) + c.wave;
        plugin.economy().add(p.getUniqueId(), reward);
        if (c.bar != null) { p.hideBossBar(c.bar); c.bar = null; c.boss = null; }

        double ratio = p.getHealth() / Math.max(1, p.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue());
        String note;
        if (ratio > 0.7) { c.dyn = Math.min(2.5, c.dyn + 0.2); note = "<red>La dificultad aumenta"; }
        else if (ratio < 0.3) { c.dyn = Math.max(0.5, c.dyn - 0.15); note = "<green>La dificultad se relaja"; }
        else note = "<yellow>La dificultad se mantiene";

        if (c.wave >= totalWaves()) { victory(c); return; }
        Msg.send(p, "Oleada " + c.wave + " superada <gray>· +" + EconomyManager.fmt(reward) + " <gray>· " + note);
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
        c.spawned = false;
        c.countdown = 6;
    }

    private void victory(Challenge c) {
        Player p = c.player;
        int reward = (int) Math.round(plugin.getConfig().getInt("challenge.boss-reward", 60) * c.base);
        plugin.economy().add(p.getUniqueId(), reward);
        Util.give(p, plugin.items().create("dedita_legendary"));
        if (c.base >= 1.4) Util.give(p, plugin.items().create("resurrection_spoon"));
        Msg.title(p, "<gradient:#ffd166:#fff3b0><bold>¡RETO COMPLETADO!", "<gray>Recompensa: " + EconomyManager.fmt(reward));
        p.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, p.getLocation().add(0, 1, 0), 120, 1, 1.5, 1, 0.4);
        p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
        stop(c);
    }

    // ───────── Oleadas ─────────
    private void spawnWave(Challenge c) {
        c.wave++;
        c.spawned = true;
        Player p = c.player;
        double f = c.factor();
        double hm = 1 + (c.wave - 1) * 0.12 * f;
        double dm = 1 + (c.wave - 1) * 0.06 * f;

        if (c.wave >= totalWaves()) {
            Location l = findSpawn(p.getLocation(), 8, 12);
            LivingEntity boss = spawnMob(c, EntityType.WITHER_SKELETON, l, 1, 1 + f * 0.3);
            AttributeInstance hp = boss.getAttribute(Attribute.GENERIC_MAX_HEALTH);
            if (hp != null) { hp.setBaseValue(150 * f); boss.setHealth(150 * f); }
            AttributeInstance kb = boss.getAttribute(Attribute.GENERIC_KNOCKBACK_RESISTANCE);
            if (kb != null) kb.setBaseValue(0.6);
            boss.customName(Msg.c("<gradient:#e63946:#7b2cbf><bold>Jefe Final"));
            boss.setCustomNameVisible(true);
            EntityEquipment eq = boss.getEquipment();
            if (eq != null) {
                eq.setItemInMainHand(new ItemStack(Material.NETHERITE_SWORD));
                eq.setItemInMainHandDropChance(0f);
            }
            c.boss = boss.getUniqueId();
            c.bossTicks = 0;
            c.bar = BossBar.bossBar(Msg.c("<gradient:#e63946:#7b2cbf><bold>Jefe Final"), 1f,
                    BossBar.Color.RED, BossBar.Overlay.NOTCHED_10);
            p.showBossBar(c.bar);
            Msg.title(p, "<dark_red><bold>¡JEFE FINAL!", "<gray>Oleada " + c.wave + "/" + totalWaves());
            p.playSound(p.getLocation(), Sound.ENTITY_WITHER_SPAWN, 0.7f, 1f);
            return;
        }

        List<EntityType> pool = new ArrayList<>(List.of(EntityType.ZOMBIE, EntityType.SKELETON));
        if (c.wave >= 3) { pool.add(EntityType.SPIDER); pool.add(EntityType.HUSK); }
        if (c.wave >= 4) { pool.add(EntityType.STRAY); pool.add(EntityType.CREEPER); }
        if (c.wave >= 5) pool.add(EntityType.WITCH);
        if (c.wave >= 6) pool.add(EntityType.VINDICATOR);
        if (c.wave >= 7) pool.add(EntityType.PIGLIN_BRUTE);

        int count = (int) Math.min(26, Math.round((3 + c.wave * 1.5) * Math.max(0.6, f)));
        for (int i = 0; i < count; i++) {
            Location l = findSpawn(p.getLocation(), 9, 14);
            spawnMob(c, pool.get(rng.nextInt(pool.size())), l, hm, dm);
        }
        Msg.title(p, "<gradient:#ff9a3c:#ffd166><bold>Oleada " + c.wave + "/" + totalWaves(),
                "<gray>" + count + " enemigos <dark_gray>· <gray>dificultad x<white>" + String.format("%.2f", f));
        p.playSound(p.getLocation(), Sound.EVENT_RAID_HORN, 1f, 1f);
    }

    private LivingEntity spawnMob(Challenge c, EntityType type, Location loc, double hm, double dm) {
        LivingEntity le = (LivingEntity) loc.getWorld().spawnEntity(loc, type);
        le.getPersistentDataContainer().set(mobKey, PersistentDataType.STRING, c.player.getUniqueId().toString());
        le.setRemoveWhenFarAway(false);
        AttributeInstance hp = le.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        if (hp != null) { hp.setBaseValue(hp.getBaseValue() * hm); le.setHealth(hp.getBaseValue()); }
        AttributeInstance dmg = le.getAttribute(Attribute.GENERIC_ATTACK_DAMAGE);
        if (dmg != null) dmg.setBaseValue(dmg.getBaseValue() * dm);
        EntityEquipment eq = le.getEquipment();
        if (eq != null && (le instanceof Zombie || le instanceof Skeleton)) {
            eq.setHelmet(new ItemStack(c.wave >= 4 ? Material.IRON_HELMET : Material.LEATHER_HELMET));
            eq.setHelmetDropChance(0f);
        }
        if (le instanceof Mob m) m.setTarget(c.player);
        c.mobs.add(le.getUniqueId());
        return le;
    }

    private Location findSpawn(Location center, int minDist, int maxDist) {
        World w = center.getWorld();
        for (int i = 0; i < 25; i++) {
            double ang = rng.nextDouble() * Math.PI * 2;
            double d = minDist + rng.nextDouble() * Math.max(1, maxDist - minDist);
            int x = center.getBlockX() + (int) Math.round(Math.cos(ang) * d);
            int z = center.getBlockZ() + (int) Math.round(Math.sin(ang) * d);
            for (int y = center.getBlockY() + 4; y >= center.getBlockY() - 6; y--) {
                Block b = w.getBlockAt(x, y, z);
                if (b.getType().isSolid() && w.getBlockAt(x, y + 1, z).isPassable() && w.getBlockAt(x, y + 2, z).isPassable()
                        && !w.getBlockAt(x, y + 1, z).isLiquid()) {
                    return new Location(w, x + 0.5, y + 1, z + 0.5);
                }
            }
        }
        return center.clone().add(rng.nextInt(5) - 2, 0, rng.nextInt(5) - 2);
    }

    // ───────── Eventos ─────────
    @EventHandler
    public void onMobDeath(EntityDeathEvent e) {
        if (e.getEntity().getPersistentDataContainer().has(mobKey, PersistentDataType.STRING)) {
            e.getDrops().clear();
            e.setDroppedExp(0);
        }
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent e) {
        Challenge c = active.get(e.getEntity().getUniqueId());
        if (c == null) return;
        Msg.send(e.getEntity(), "<red>Reto fallido en la oleada " + c.wave + ".");
        stop(c);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Challenge c = active.get(e.getPlayer().getUniqueId());
        if (c != null) stop(c);
    }

    // ───────── Comando ─────────
    @Override
    public boolean onCommand(CommandSender s, Command cmd, String l, String[] a) {
        if (!(s instanceof Player p)) { Msg.send(s, "Solo jugadores."); return true; }
        String sub = a.length == 0 ? "estado" : a[0].toLowerCase();
        switch (sub) {
            case "iniciar" -> {
                if (active.containsKey(p.getUniqueId())) { Msg.send(p, "<red>Ya estás en un reto."); return true; }
                if (plugin.gulags().isInGulag(p)) { Msg.send(p, "<red>No puedes iniciar un reto dentro de un gulag."); return true; }
                String diff = a.length > 1 ? a[1].toLowerCase() : "normal";
                if (!DIFF.containsKey(diff)) { Msg.send(p, "Dificultades: <white>" + String.join(", ", DIFF.keySet())); return true; }
                start(p, diff);
            }
            case "abandonar" -> {
                Challenge c = active.get(p.getUniqueId());
                if (c == null) { Msg.send(p, "<red>No estás en ningún reto."); return true; }
                stop(c);
                Msg.send(p, "Has abandonado el reto.");
            }
            default -> {
                Challenge c = active.get(p.getUniqueId());
                if (c == null) Msg.send(p, "No estás en un reto. Usa <white>/reto iniciar [facil|normal|dificil|extremo]");
                else Msg.send(p, "Oleada <white>" + Math.max(1, c.wave) + "/" + totalWaves() + "<gray> · dificultad <white>" + c.diffName
                        + " <gray>(x" + String.format("%.2f", c.factor()) + ") · enemigos: <white>" + c.mobs.size());
            }
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command cmd, String l, String[] a) {
        if (a.length == 1) return Util.filter(List.of("iniciar", "abandonar", "estado"), a[0]);
        if (a.length == 2 && a[0].equalsIgnoreCase("iniciar")) return Util.filter(new ArrayList<>(DIFF.keySet()), a[1]);
        return List.of();
    }
}

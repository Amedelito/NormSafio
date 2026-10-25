package es.dedsafio.feature;

import es.dedsafio.Dedsafio;
import es.dedsafio.Msg;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Horse;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDismountEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;

/** Estatuas (armor stands con tu equipo) y karts (caballos veloces y sin daño). */
public final class EntityItemsListener implements Listener {
    private final Dedsafio plugin;
    private final NamespacedKey statueKey;
    private final NamespacedKey kartKey;      // 1 = devuelve el objeto al bajarse, 0 = kart de carrera
    private static final double KART_SPEED = 0.42;

    public EntityItemsListener(Dedsafio plugin) {
        this.plugin = plugin;
        this.statueKey = new NamespacedKey(plugin, "statue");
        this.kartKey = new NamespacedKey(plugin, "kart");
        Bukkit.getScheduler().runTaskTimer(plugin, this::trail, 4L, 4L);
    }

    // ───────── Colocación ─────────
    @EventHandler
    public void onPlace(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND || e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Player p = e.getPlayer();
        ItemStack hand = p.getInventory().getItemInMainHand();
        String id = plugin.items().idOf(hand);
        if (!"statue".equals(id) && !"kart".equals(id)) return;
        e.setCancelled(true);
        Block clicked = e.getClickedBlock();
        if (clicked == null) return;
        Location loc = clicked.getRelative(e.getBlockFace()).getLocation().add(0.5, 0, 0.5);
        loc.setYaw(p.getLocation().getYaw() + 180f);
        if (id.equals("statue")) spawnStatue(p, loc);
        else spawnKart(p, loc, true, KART_SPEED);
        if (p.getGameMode() != GameMode.CREATIVE) hand.setAmount(hand.getAmount() - 1);
    }

    private void spawnStatue(Player p, Location loc) {
        loc.getWorld().spawn(loc, ArmorStand.class, a -> {
            a.setArms(true);
            a.setBasePlate(false);
            a.setInvulnerable(true);
            a.setPersistent(true);
            a.customName(Msg.c("<#ffd166>Estatua de <white>" + Msg.esc(p.getName())));
            a.setCustomNameVisible(true);
            a.getPersistentDataContainer().set(statueKey, PersistentDataType.BYTE, (byte) 1);
            a.setRotation(loc.getYaw(), 0f);
            EntityEquipment eq = a.getEquipment();
            ItemStack[] arm = p.getInventory().getArmorContents(); // botas, grebas, peto, casco
            if (arm[0] != null) eq.setBoots(arm[0].clone());
            if (arm[1] != null) eq.setLeggings(arm[1].clone());
            if (arm[2] != null) eq.setChestplate(arm[2].clone());
            if (arm[3] != null) {
                eq.setHelmet(arm[3].clone());
            } else {
                ItemStack head = new ItemStack(Material.PLAYER_HEAD);
                SkullMeta sm = (SkullMeta) head.getItemMeta();
                sm.setOwningPlayer(p);
                head.setItemMeta(sm);
                eq.setHelmet(head);
            }
            ItemStack main = p.getInventory().getItemInMainHand();
            // Lo que lleves en la mano (salvo la propia estatua) también posa
            if (!Util_isAir(main) && !"statue".equals(plugin.items().idOf(main))) eq.setItemInMainHand(main.clone());
        });
        loc.getWorld().spawnParticle(Particle.CLOUD, loc.clone().add(0, 1, 0), 20, 0.3, 0.8, 0.3, 0.02);
        loc.getWorld().playSound(loc, Sound.BLOCK_STONE_PLACE, 1f, 0.8f);
    }

    private boolean Util_isAir(ItemStack i) { return es.dedsafio.Util.isAir(i); }

    /** Crea un kart (caballo ensillado, rápido, sin saltos altos) y sube al jugador. */
    public Horse spawnKart(Player p, Location loc, boolean returnItem, double speed) {
        Horse h = loc.getWorld().spawn(loc, Horse.class, k -> {
            k.setAdult();
            k.setTamed(true);
            k.setOwner(p);
            k.getInventory().setSaddle(new ItemStack(Material.SADDLE));
            k.setColor(Horse.Color.BLACK);
            k.setStyle(Horse.Style.NONE);
            k.setInvulnerable(true);
            k.setPersistent(false);
            k.setRemoveWhenFarAway(false);
            k.customName(Msg.c("<#ffd166><bold>Kart"));
            k.setCustomNameVisible(false);
            AttributeInstance spd = k.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED);
            if (spd != null) spd.setBaseValue(speed);
            k.setJumpStrength(0.3);
            k.getPersistentDataContainer().set(kartKey, PersistentDataType.BYTE, (byte) (returnItem ? 1 : 0));
        });
        h.addPassenger(p);
        return h;
    }

    public void setKartSpeed(Horse h, double speed) {
        AttributeInstance spd = h.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED);
        if (spd != null) spd.setBaseValue(speed);
    }

    // ───────── Interacción con estatuas ─────────
    private boolean isStatue(Entity e) {
        return e instanceof ArmorStand a && a.getPersistentDataContainer().has(statueKey, PersistentDataType.BYTE);
    }

    @EventHandler
    public void onStatueClick(PlayerInteractAtEntityEvent e) {
        if (!isStatue(e.getRightClicked()) || e.getHand() != EquipmentSlot.HAND) return;
        e.setCancelled(true);
        ArmorStand a = (ArmorStand) e.getRightClicked();
        Player p = e.getPlayer();
        if (p.isSneaking()) {
            es.dedsafio.Util.give(p, plugin.items().create("statue"));
            a.getWorld().spawnParticle(Particle.CLOUD, a.getLocation().add(0, 1, 0), 15, 0.3, 0.8, 0.3, 0.02);
            a.remove();
        } else {
            a.setRotation(a.getLocation().getYaw() + 45f, 0f);
        }
    }

    @EventHandler
    public void onManipulate(PlayerArmorStandManipulateEvent e) {
        if (isStatue(e.getRightClicked())) e.setCancelled(true);
    }

    // ───────── Karts ─────────
    @EventHandler
    public void onDismount(EntityDismountEvent e) {
        if (!(e.getDismounted() instanceof Horse h)) return;
        Byte flag = h.getPersistentDataContainer().get(kartKey, PersistentDataType.BYTE);
        if (flag == null || !(e.getEntity() instanceof Player p)) return;
        if (plugin.gulags() != null && plugin.gulags().isRacing(p)) {
            if (e.isCancellable()) e.setCancelled(true);
            return;
        }
        if (flag == 1) es.dedsafio.Util.give(p, plugin.items().create("kart"));
        Bukkit.getScheduler().runTask(plugin, h::remove);
    }

    private void trail() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getVehicle() instanceof Horse h && h.getPersistentDataContainer().has(kartKey, PersistentDataType.BYTE)
                    && h.getVelocity().lengthSquared() > 0.01) {
                h.getWorld().spawnParticle(Particle.CLOUD, h.getLocation(), 3, 0.2, 0.05, 0.2, 0.01);
            }
        }
    }
}

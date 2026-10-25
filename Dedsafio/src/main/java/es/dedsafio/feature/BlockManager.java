package es.dedsafio.feature;

import es.dedsafio.Dedsafio;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.io.File;
import java.io.IOException;
import java.util.*;

/** Mesa de Runas, Faro y Bloque Ancestral: bloques normales marcados y persistidos en blocks.yml. */
public final class BlockManager implements Listener {
    private record Placed(String world, int x, int y, int z, String type) {}

    private static final Set<String> TYPES = Set.of("rune_table", "lighthouse", "ancestral_block");

    private final Dedsafio plugin;
    private final File file;
    private final Map<String, Placed> placed = new HashMap<>();
    private final RuneMenu runes;

    public BlockManager(Dedsafio plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "blocks.yml");
        this.runes = new RuneMenu(plugin);
        load();
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 40L, 40L);
    }

    public RuneMenu runeMenu() { return runes; }

    private static String key(Block b) {
        return b.getWorld().getName() + ";" + b.getX() + ";" + b.getY() + ";" + b.getZ();
    }

    private void load() {
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        for (String s : y.getStringList("blocks")) {
            String[] p = s.split(";");
            if (p.length != 5) continue;
            try {
                Placed pl = new Placed(p[0], Integer.parseInt(p[1]), Integer.parseInt(p[2]), Integer.parseInt(p[3]), p[4]);
                placed.put(p[0] + ";" + p[1] + ";" + p[2] + ";" + p[3], pl);
            } catch (NumberFormatException ignored) {
            }
        }
    }

    public void save() {
        YamlConfiguration y = new YamlConfiguration();
        List<String> out = new ArrayList<>();
        for (Placed p : placed.values()) out.add(p.world() + ";" + p.x() + ";" + p.y() + ";" + p.z() + ";" + p.type());
        y.set("blocks", out);
        try {
            y.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("No se pudo guardar blocks.yml: " + e.getMessage());
        }
    }

    // ───────── Ciclo de vida de los bloques ─────────
    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        String id = plugin.items().idOf(e.getItemInHand());
        if (id == null || !TYPES.contains(id)) return;
        Block b = e.getBlockPlaced();
        placed.put(key(b), new Placed(b.getWorld().getName(), b.getX(), b.getY(), b.getZ(), id));
        save();
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onBreak(BlockBreakEvent e) {
        Placed p = placed.remove(key(e.getBlock()));
        if (p == null) return;
        e.setDropItems(false);
        if (e.getPlayer().getGameMode() != GameMode.CREATIVE) {
            e.getBlock().getWorld().dropItemNaturally(Util_center(e.getBlock()), plugin.items().create(p.type()));
        }
        save();
    }

    private Location Util_center(Block b) { return es.dedsafio.Util.center(b); }

    @EventHandler(ignoreCancelled = true)
    public void onExplode(EntityExplodeEvent e) {
        e.blockList().removeIf(b -> placed.containsKey(key(b)));
    }

    @EventHandler(ignoreCancelled = true)
    public void onExplode(BlockExplodeEvent e) {
        e.blockList().removeIf(b -> placed.containsKey(key(b)));
    }

    @EventHandler(ignoreCancelled = true)
    public void onPiston(BlockPistonExtendEvent e) {
        if (e.getBlocks().stream().anyMatch(b -> placed.containsKey(key(b)))) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPiston(BlockPistonRetractEvent e) {
        if (e.getBlocks().stream().anyMatch(b -> placed.containsKey(key(b)))) e.setCancelled(true);
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND || e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Block b = e.getClickedBlock();
        if (b == null) return;
        Placed p = placed.get(key(b));
        if (p == null || !p.type().equals("rune_table")) return;
        if (e.getPlayer().isSneaking() && !es.dedsafio.Util.isAir(e.getItem())) return; // permite colocar bloques
        e.setCancelled(true);
        runes.open(e.getPlayer());
    }

    // ───────── Efectos pasivos ─────────
    private void tick() {
        double radius = plugin.getConfig().getDouble("lighthouse-radius", 12);
        for (Placed p : placed.values()) {
            World w = Bukkit.getWorld(p.world());
            if (w == null || !w.isChunkLoaded(p.x() >> 4, p.z() >> 4)) continue;
            Location loc = new Location(w, p.x() + 0.5, p.y() + 0.5, p.z() + 0.5);
            Collection<Player> near = w.getNearbyPlayers(loc, p.type().equals("lighthouse") ? radius : 16);
            if (near.isEmpty()) continue;
            switch (p.type()) {
                case "lighthouse" -> {
                    for (int i = 1; i <= 14; i++)
                        w.spawnParticle(Particle.END_ROD, loc.getX(), loc.getY() + 0.6 + i * 0.5, loc.getZ(), 1, 0.05, 0, 0.05, 0);
                    for (Player pl : near) {
                        pl.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 100, 0, true, false, true));
                        pl.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, 260, 0, true, false, true));
                    }
                }
                case "ancestral_block" ->
                        w.spawnParticle(Particle.ENCHANT, loc.clone().add(0, 0.8, 0), 14, 0.5, 0.4, 0.5, 0.4);
                case "rune_table" ->
                        w.spawnParticle(Particle.WITCH, loc.clone().add(0, 0.9, 0), 4, 0.3, 0.2, 0.3, 0);
                default -> { }
            }
        }
    }
}

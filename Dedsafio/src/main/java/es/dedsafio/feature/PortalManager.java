package es.dedsafio.feature;

import es.dedsafio.Dedsafio;
import es.dedsafio.Msg;
import es.dedsafio.Util;
import org.bukkit.*;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.io.File;
import java.io.IOException;
import java.util.*;

/** Portales entre zonas: anillos de partículas que teletransportan al portal enlazado. */
public final class PortalManager implements TabExecutor {
    private static final class Portal {
        final String name;
        Location loc;
        String link;

        Portal(String name, Location loc) { this.name = name; this.loc = loc; }
    }

    private final Dedsafio plugin;
    private final File file;
    private final Map<String, Portal> portals = new LinkedHashMap<>();
    private final Map<UUID, Long> cooldown = new HashMap<>();
    private int phase;

    public PortalManager(Dedsafio plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "portals.yml");
        load();
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 5L, 5L);
    }

    private void load() {
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection sec = y.getConfigurationSection("portals");
        if (sec == null) return;
        for (String name : sec.getKeys(false)) {
            Location l = sec.getLocation(name + ".loc");
            if (l == null) continue;
            Portal p = new Portal(name, l);
            p.link = sec.getString(name + ".link");
            portals.put(name, p);
        }
    }

    private void save() {
        YamlConfiguration y = new YamlConfiguration();
        for (Portal p : portals.values()) {
            y.set("portals." + p.name + ".loc", p.loc);
            y.set("portals." + p.name + ".link", p.link);
        }
        try {
            y.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("No se pudo guardar portals.yml: " + e.getMessage());
        }
    }

    private void tick() {
        phase = (phase + 1) % 24;
        long now = System.currentTimeMillis();
        long cd = plugin.getConfig().getLong("portal-cooldown-seconds", 3) * 1000L;
        for (Portal p : portals.values()) {
            World w = p.loc.getWorld();
            if (w == null) continue;
            Location center = p.loc.clone().add(0, 1.2, 0);
            Collection<Player> near = w.getNearbyPlayers(center, 32);
            if (near.isEmpty()) continue;

            double yaw = Math.toRadians(p.loc.getYaw());
            double rx = Math.cos(yaw), rz = Math.sin(yaw);
            for (int i = 0; i < 24; i += 2) {
                double t = (i + phase) * Math.PI * 2 / 24;
                double h = Math.cos(t) * 1.0, v = Math.sin(t) * 1.4;
                w.spawnParticle(Particle.PORTAL, center.getX() + rx * h, center.getY() + v, center.getZ() + rz * h, 2, 0.05, 0.05, 0.05, 0.1);
            }
            w.spawnParticle(Particle.END_ROD, center, 1, 0.6, 1, 0.6, 0.01);

            Portal dest = p.link == null ? null : portals.get(p.link);
            if (dest == null || dest.loc.getWorld() == null) continue;
            for (Player pl : near) {
                if (pl.getLocation().add(0, 1, 0).distanceSquared(center) > 1.6 * 1.6) continue;
                Long until = cooldown.get(pl.getUniqueId());
                if (until != null && until > now) continue;
                Location to = dest.loc.clone();
                Vector fwd = to.getDirection().setY(0);
                if (fwd.lengthSquared() > 0) to.add(fwd.normalize().multiply(1.8));
                pl.teleport(to);
                cooldown.put(pl.getUniqueId(), now + cd);
                pl.playSound(to, Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1.2f);
                to.getWorld().spawnParticle(Particle.REVERSE_PORTAL, to.clone().add(0, 1, 0), 40, 0.4, 0.8, 0.4, 0.2);
                Msg.bar(pl, "<#b388ff>Portal → <white>" + dest.name);
            }
        }
    }

    @Override
    public boolean onCommand(CommandSender s, Command cmd, String l, String[] a) {
        if (!s.hasPermission("dedsafio.admin")) { Msg.send(s, "<red>Sin permiso."); return true; }
        String sub = a.length == 0 ? "lista" : a[0].toLowerCase();
        switch (sub) {
            case "crear" -> {
                if (!(s instanceof Player p) || a.length < 2) { Msg.send(s, "Uso: /portal crear <nombre>"); return true; }
                String name = a[1].toLowerCase();
                if (!name.matches("[a-z0-9_-]{1,24}")) { Msg.send(s, "<red>Nombre no válido (a-z, 0-9, _ -)."); return true; }
                Location loc = p.getLocation();
                loc.setX(loc.getBlockX() + 0.5);
                loc.setZ(loc.getBlockZ() + 0.5);
                loc.setYaw(Math.round(loc.getYaw() / 90f) * 90f);
                loc.setPitch(0);
                portals.put(name, new Portal(name, loc));
                save();
                Msg.send(s, "Portal <white>" + name + "<gray> creado. Enlázalo con <white>/portal enlazar " + name + " <otro>");
            }
            case "enlazar" -> {
                if (a.length < 3) { Msg.send(s, "Uso: /portal enlazar <a> <b>"); return true; }
                Portal p1 = portals.get(a[1].toLowerCase()), p2 = portals.get(a[2].toLowerCase());
                if (p1 == null || p2 == null) { Msg.send(s, "<red>Alguno de los portales no existe."); return true; }
                p1.link = p2.name;
                p2.link = p1.name;
                save();
                Msg.send(s, "Portales enlazados: <white>" + p1.name + " <gray>⇄ <white>" + p2.name);
            }
            case "borrar" -> {
                if (a.length < 2 || portals.remove(a[1].toLowerCase()) == null) { Msg.send(s, "<red>Portal no encontrado."); return true; }
                String gone = a[1].toLowerCase();
                for (Portal p : portals.values()) if (gone.equals(p.link)) p.link = null;
                save();
                Msg.send(s, "Portal borrado.");
            }
            case "tp" -> {
                if (!(s instanceof Player p) || a.length < 2 || !portals.containsKey(a[1].toLowerCase())) { Msg.send(s, "Uso: /portal tp <nombre>"); return true; }
                p.teleport(portals.get(a[1].toLowerCase()).loc);
            }
            default -> {
                if (portals.isEmpty()) { Msg.send(s, "No hay portales. Usa <white>/portal crear <nombre>"); return true; }
                for (Portal p : portals.values())
                    Msg.send(s, "<white>" + p.name + "<gray> → " + (p.link == null ? "<red>sin enlace" : "<green>" + p.link)
                            + " <dark_gray>(" + p.loc.getWorld().getName() + " " + p.loc.getBlockX() + " " + p.loc.getBlockY() + " " + p.loc.getBlockZ() + ")");
            }
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command cmd, String l, String[] a) {
        if (a.length == 1) return Util.filter(List.of("crear", "enlazar", "borrar", "tp", "lista"), a[0]);
        if (a.length >= 2 && !a[0].equalsIgnoreCase("crear") && a.length <= 3)
            return Util.filter(new ArrayList<>(portals.keySet()), a[a.length - 1]);
        return List.of();
    }
}

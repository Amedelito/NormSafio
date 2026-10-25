package es.dedsafio.feature;

import es.dedsafio.Dedsafio;
import es.dedsafio.Msg;
import es.dedsafio.Util;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Animaciones del jugador hechas en el servidor (sin mods): saludo, baile, victoria, reverencia. */
public final class EmoteCommand implements TabExecutor {
    private static final List<String> EMOTES = List.of("saludo", "baile", "victoria", "reverencia");
    private final Dedsafio plugin;
    private final Set<UUID> busy = new HashSet<>();

    public EmoteCommand(Dedsafio plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender s, Command cmd, String l, String[] a) {
        if (!(s instanceof Player p)) { Msg.send(s, "Solo jugadores."); return true; }
        if (a.length == 0 || !EMOTES.contains(a[0].toLowerCase())) {
            Msg.send(p, "Animaciones: <white>" + String.join(", ", EMOTES));
            return true;
        }
        if (plugin.gulags().isInGulag(p)) { Msg.send(p, "<red>No puedes usar animaciones en un gulag."); return true; }
        if (!busy.add(p.getUniqueId())) return true;
        switch (a[0].toLowerCase()) {
            case "saludo" -> run(p, 30, (t) -> {
                if (t % 6 == 0) p.swingMainHand();
                if (t % 10 == 0) p.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, p.getEyeLocation().add(0, 0.4, 0), 4, 0.3, 0.2, 0.3, 0);
            });
            case "baile" -> run(p, 40, (t) -> {
                Location loc = p.getLocation();
                p.setRotation(loc.getYaw() + 18f, loc.getPitch());
                if (t % 10 == 0) p.setVelocity(new Vector(0, 0.35, 0));
                if (t % 5 == 0) {
                    p.getWorld().spawnParticle(Particle.NOTE, p.getEyeLocation().add(0, 0.5, 0), 2, 0.4, 0.2, 0.4, 1);
                    p.getWorld().playSound(loc, Sound.BLOCK_NOTE_BLOCK_BELL, 0.6f, 0.7f + (t % 4) * 0.25f);
                }
            });
            case "victoria" -> run(p, 30, (t) -> {
                if (t == 0) {
                    p.setVelocity(new Vector(0, 0.5, 0));
                    p.getWorld().playSound(p.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 1f, 1f);
                }
                if (t % 5 == 0) {
                    p.swingMainHand();
                    p.swingOffHand();
                    p.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, p.getLocation().add(0, 1.5, 0), 15, 0.4, 0.5, 0.4, 0.3);
                }
            });
            default -> run(p, 20, (t) -> {
                Location loc = p.getLocation();
                float pitch = t < 10 ? t * 6f : (20 - t) * 6f;
                p.setRotation(loc.getYaw(), pitch);
            });
        }
        return true;
    }

    private void run(Player p, int ticks, java.util.function.IntConsumer step) {
        new BukkitRunnable() {
            int t = 0;

            @Override
            public void run() {
                if (!p.isOnline() || t >= ticks) {
                    busy.remove(p.getUniqueId());
                    cancel();
                    return;
                }
                step.accept(t++);
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command c, String l, String[] a) {
        return a.length == 1 ? Util.filter(EMOTES, a[0]) : List.of();
    }
}

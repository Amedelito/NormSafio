package es.dedsafio.feature;

import es.dedsafio.Dedsafio;
import es.dedsafio.Msg;
import es.dedsafio.Util;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Horse;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.io.File;
import java.io.IOException;
import java.util.*;

/** Gulags: minijuegos de Boxeo (equipos), Espacio (baja gravedad) y Carreras (karts + checkpoints). */
public final class GulagManager implements TabExecutor, Listener {
    public enum Type {
        BOXING("boxeo", "Gulag de Boxeo", "#e63946"),
        SPACE("espacio", "Gulag del Espacio", "#7b2cbf"),
        RACE("carreras", "Gulag de Carreras", "#ffd166");

        final String key, title, color;

        Type(String key, String title, String color) {
            this.key = key;
            this.title = title;
            this.color = color;
        }

        static Type of(String s) {
            for (Type t : values()) if (t.key.equalsIgnoreCase(s)) return t;
            return null;
        }
    }

    private enum State { WAITING, COUNTDOWN, RUNNING, ENDING }

    private static final class Saved {
        final ItemStack[] contents;
        final GameMode mode;
        final Location loc;
        final double health;
        final int food, level;
        final float exp;
        final Collection<PotionEffect> effects;

        Saved(Player p) {
            ItemStack[] src = p.getInventory().getContents();
            contents = new ItemStack[src.length];
            for (int i = 0; i < src.length; i++) contents[i] = src[i] == null ? null : src[i].clone();
            mode = p.getGameMode();
            loc = p.getLocation();
            health = p.getHealth();
            food = p.getFoodLevel();
            level = p.getLevel();
            exp = p.getExp();
            effects = new ArrayList<>(p.getActivePotionEffects());
        }

        void restore(Player p) {
            p.getInventory().clear();
            p.getInventory().setContents(contents);
            for (PotionEffect e : new ArrayList<>(p.getActivePotionEffects())) p.removePotionEffect(e.getType());
            for (PotionEffect e : effects) p.addPotionEffect(e);
            p.setGameMode(mode);
            p.setFoodLevel(food);
            p.setLevel(level);
            p.setExp(exp);
            p.teleport(loc);
            double max = p.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue();
            p.setHealth(Math.max(1, Math.min(max, health)));
        }
    }

    private static final class Arena {
        final Type type;
        Location lobby, finish;
        final List<Location> spawns = new ArrayList<>();
        final List<Location> checkpoints = new ArrayList<>();
        State state = State.WAITING;
        final Set<UUID> players = new LinkedHashSet<>();
        final Set<UUID> alive = new LinkedHashSet<>();
        final Map<UUID, Saved> saved = new HashMap<>();
        final Map<UUID, Integer> team = new HashMap<>();
        final Map<UUID, Integer> progress = new HashMap<>();
        final Map<UUID, Horse> karts = new HashMap<>();
        final List<UUID> finished = new ArrayList<>();
        int countdown, ticks, elapsed, goDelay;
        double voidY;

        Arena(Type type) { this.type = type; }
    }

    private final Dedsafio plugin;
    private final File file;
    private final Map<Type, Arena> arenas = new EnumMap<>(Type.class);

    public GulagManager(Dedsafio plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "gulags.yml");
        for (Type t : Type.values()) arenas.put(t, new Arena(t));
        load();
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 10L, 10L);
    }

    // ───────── Persistencia ─────────
    private void load() {
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        for (Arena a : arenas.values()) {
            String k = a.type.key;
            a.lobby = y.getLocation(k + ".lobby");
            a.finish = y.getLocation(k + ".finish");
            for (Object o : y.getList(k + ".spawns", List.of())) if (o instanceof Location l) a.spawns.add(l);
            for (Object o : y.getList(k + ".checkpoints", List.of())) if (o instanceof Location l) a.checkpoints.add(l);
        }
    }

    private void save() {
        YamlConfiguration y = new YamlConfiguration();
        for (Arena a : arenas.values()) {
            String k = a.type.key;
            y.set(k + ".lobby", a.lobby);
            y.set(k + ".finish", a.finish);
            y.set(k + ".spawns", a.spawns);
            y.set(k + ".checkpoints", a.checkpoints);
        }
        try {
            y.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("No se pudo guardar gulags.yml: " + e.getMessage());
        }
    }

    // ───────── Consultas ─────────
    private Arena arenaOf(Player p) {
        for (Arena a : arenas.values()) if (a.players.contains(p.getUniqueId())) return a;
        return null;
    }

    public boolean isInGulag(Player p) { return arenaOf(p) != null; }

    public boolean isRacing(Player p) {
        Arena a = arenaOf(p);
        return a != null && a.type == Type.RACE && a.state == State.RUNNING && a.alive.contains(p.getUniqueId());
    }

    private int cfg(String path, int def) { return plugin.getConfig().getInt("gulag." + path, def); }

    private void announce(Arena a, String msg) {
        for (UUID id : a.players) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) Msg.send(p, "<" + a.type.color + ">" + a.type.title + " <dark_gray>» <gray>" + msg);
        }
    }

    private void prep(Player p) {
        p.getInventory().clear();
        for (PotionEffect e : new ArrayList<>(p.getActivePotionEffects())) p.removePotionEffect(e.getType());
        p.setGameMode(GameMode.ADVENTURE);
        p.setHealth(p.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue());
        p.setFoodLevel(20);
        p.setFireTicks(0);
        p.setFallDistance(0);
    }

    // ───────── Entrar / salir ─────────
    private void join(Player p, Arena a) {
        if (arenaOf(p) != null) { Msg.send(p, "<red>Ya estás en un gulag."); return; }
        if (plugin.challenges().isInChallenge(p)) { Msg.send(p, "<red>Abandona tu reto primero (/reto abandonar)."); return; }
        if (a.lobby == null) { Msg.send(p, "<red>Este gulag aún no está configurado."); return; }
        if (a.state == State.RUNNING || a.state == State.ENDING) { Msg.send(p, "<red>Hay una partida en curso."); return; }
        if (a.players.size() >= cfg("max-players", 12)) { Msg.send(p, "<red>Gulag lleno."); return; }
        if (p.getVehicle() != null) p.leaveVehicle();
        a.saved.put(p.getUniqueId(), new Saved(p));
        prep(p);
        p.teleport(a.lobby);
        a.players.add(p.getUniqueId());
        announce(a, "<white>" + Msg.esc(p.getName()) + " <gray>se unió (" + a.players.size() + "/" + cfg("max-players", 12) + ")");
        if (a.state == State.WAITING && a.players.size() >= cfg("min-players", 2)) {
            a.state = State.COUNTDOWN;
            a.countdown = cfg("countdown", 15);
            announce(a, "¡Cuenta atrás iniciada!");
        }
    }

    private boolean leave(Player p) {
        Arena a = arenaOf(p);
        if (a == null) return false;
        UUID id = p.getUniqueId();
        a.players.remove(id);
        a.alive.remove(id);
        Horse k = a.karts.remove(id);
        if (k != null) k.remove();
        Saved s = a.saved.remove(id);
        if (s != null) s.restore(p);
        announce(a, "<white>" + Msg.esc(p.getName()) + " <gray>salió.");
        if (a.state == State.RUNNING) {
            if (a.players.isEmpty()) a.state = State.WAITING;
            else checkWin(a);
        } else if (a.state == State.COUNTDOWN && a.players.size() < cfg("min-players", 2)) {
            a.state = State.WAITING;
        }
        return true;
    }

    // ───────── Partida ─────────
    private Location spawn(Arena a, int i) {
        return a.spawns.isEmpty() ? a.lobby : a.spawns.get(i % a.spawns.size());
    }

    private void begin(Arena a) {
        a.players.removeIf(id -> Bukkit.getPlayer(id) == null);
        if (a.players.isEmpty()) { a.state = State.WAITING; return; }
        if (a.type == Type.RACE && (a.spawns.isEmpty() || a.finish == null)) {
            announce(a, "<red>La arena de carreras necesita spawns y meta (/gulag carreras spawn | meta).");
            a.state = State.WAITING;
            return;
        }
        a.state = State.RUNNING;
        a.alive.clear();
        a.alive.addAll(a.players);
        a.ticks = 0;
        a.elapsed = 0;
        a.finished.clear();
        a.progress.clear();
        a.team.clear();
        a.karts.clear();
        if (a.type == Type.SPACE) {
            double minY = a.spawns.isEmpty() ? a.lobby.getY() : a.spawns.stream().mapToDouble(Location::getY).min().orElse(a.lobby.getY());
            a.voidY = minY - cfg("space-void-depth", 20);
        }
        int i = 0;
        for (UUID id : new ArrayList<>(a.players)) {
            Player p = Bukkit.getPlayer(id);
            if (p == null) continue;
            Location sp = spawn(a, i);
            prep(p);
            p.teleport(sp);
            switch (a.type) {
                case BOXING -> {
                    int team = i % 2;
                    a.team.put(id, team);
                    String set = team == 0 ? "boxing_red" : "boxing_blue";
                    p.getInventory().setHelmet(plugin.items().create(set + "_helmet"));
                    p.getInventory().setChestplate(plugin.items().create(set + "_chestplate"));
                    p.getInventory().setLeggings(plugin.items().create(set + "_leggings"));
                    p.getInventory().setBoots(plugin.items().create(set + "_boots"));
                    p.getInventory().setItem(0, plugin.items().create("boxing_gloves"));
                    Msg.title(p, "<" + (team == 0 ? "#e63946" : "#457b9d") + "><bold>¡A BOXEAR!", "<gray>Equipo " + (team == 0 ? "rojo" : "azul"));
                }
                case SPACE -> {
                    p.getInventory().setItem(0, plugin.items().create("space_stellar_sword"));
                    p.getInventory().setItem(1, plugin.items().create("space_void_sword"));
                    p.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, PotionEffect.INFINITE_DURATION, 3, false, false));
                    p.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, PotionEffect.INFINITE_DURATION, 0, false, false));
                    Msg.title(p, "<#7b2cbf><bold>¡GRAVEDAD CERO!", "<gray>No caigas al vacío");
                }
                case RACE -> {
                    a.karts.put(id, plugin.entityItems().spawnKart(p, sp, false, 0.0));
                    a.goDelay = 4;
                    Msg.title(p, "<#ffd166><bold>¡Prepárate!", "<gray>Arrancamos en unos segundos");
                }
            }
            i++;
        }
        announce(a, "¡Empieza la partida!");
    }

    private void tick() {
        for (Arena a : arenas.values()) {
            a.ticks++;
            boolean sec = a.ticks % 2 == 0;
            if (a.state == State.COUNTDOWN) {
                if (!sec) continue;
                if (a.players.size() < cfg("min-players", 2)) {
                    a.state = State.WAITING;
                    announce(a, "<red>Cuenta atrás cancelada: faltan jugadores.");
                } else if (a.countdown <= 0) {
                    begin(a);
                } else {
                    if (a.countdown <= 5 || a.countdown % 5 == 0) {
                        for (UUID id : a.players) {
                            Player p = Bukkit.getPlayer(id);
                            if (p == null) continue;
                            Msg.title(p, "<yellow><bold>" + a.countdown, "<" + a.type.color + ">" + a.type.title);
                            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 1f, 1.2f);
                        }
                    }
                    a.countdown--;
                }
            } else if (a.state == State.RUNNING) {
                running(a, sec);
            }
        }
    }

    private void running(Arena a, boolean sec) {
        switch (a.type) {
            case SPACE -> {
                if (!sec) return;
                for (UUID id : new ArrayList<>(a.alive)) {
                    Player p = Bukkit.getPlayer(id);
                    if (p != null && p.getLocation().getY() < a.voidY) eliminate(a, p, "cayó al vacío");
                }
            }
            case RACE -> {
                if (a.goDelay > 0) {
                    if (!sec) return;
                    a.goDelay--;
                    for (UUID id : a.players) {
                        Player p = Bukkit.getPlayer(id);
                        if (p == null) continue;
                        if (a.goDelay > 0) {
                            Msg.title(p, "<yellow><bold>" + a.goDelay, "");
                            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 1f, 1f);
                        } else {
                            Msg.title(p, "<green><bold>¡YA!", "");
                            p.playSound(p.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 1f, 1f);
                        }
                    }
                    if (a.goDelay == 0) for (Horse h : a.karts.values()) plugin.entityItems().setKartSpeed(h, 0.55);
                    return;
                }
                checkRace(a);
                if (sec && a.state == State.RUNNING && ++a.elapsed >= cfg("race-time", 180)) {
                    announce(a, "<red>¡Se acabó el tiempo!");
                    finishRace(a);
                }
            }
            default -> { }
        }
    }

    private void checkRace(Arena a) {
        for (UUID id : new ArrayList<>(a.alive)) {
            Player p = Bukkit.getPlayer(id);
            if (p == null) continue;
            Location pl = p.getLocation();
            int prog = a.progress.getOrDefault(id, 0);
            if (prog < a.checkpoints.size()) {
                Location cp = a.checkpoints.get(prog);
                if (Objects.equals(cp.getWorld(), pl.getWorld()) && cp.distanceSquared(pl) <= 64) {
                    a.progress.put(id, prog + 1);
                    Msg.bar(p, "<#ffd166>Checkpoint <white>" + (prog + 1) + "<dark_gray>/" + a.checkpoints.size());
                    p.playSound(pl, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.3f);
                }
            } else if (Objects.equals(a.finish.getWorld(), pl.getWorld()) && a.finish.distanceSquared(pl) <= 64) {
                finishPlayer(a, p);
            }
        }
    }

    private void finishPlayer(Arena a, Player p) {
        UUID id = p.getUniqueId();
        a.alive.remove(id);
        a.finished.add(id);
        int pos = a.finished.size();
        List<Integer> rewards = plugin.getConfig().getIntegerList("gulag.race-rewards");
        int reward = pos <= rewards.size() ? rewards.get(pos - 1) : cfg("participation-reward", 5);
        plugin.economy().add(id, reward);
        Msg.title(p, "<#ffd166><bold>Posición " + pos, "<gray>+" + EconomyManager.fmt(reward));
        announce(a, "<white>" + Msg.esc(p.getName()) + " <gray>terminó en la posición <white>" + pos);
        p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
        Horse h = a.karts.remove(id);
        if (h != null) h.remove();
        p.setGameMode(GameMode.SPECTATOR);
        if (a.alive.isEmpty()) finishRace(a);
    }

    private void finishRace(Arena a) {
        if (a.state != State.RUNNING) return;
        end(a, new ArrayList<>(a.finished), false);
    }

    private void eliminate(Arena a, Player p, String reason) {
        if (!a.alive.remove(p.getUniqueId())) return;
        p.setHealth(p.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue());
        p.setGameMode(GameMode.SPECTATOR);
        Msg.title(p, "<red><bold>Eliminado", "<gray>Ahora eres espectador");
        announce(a, "<white>" + Msg.esc(p.getName()) + " <gray>" + reason + " <dark_gray>(" + a.alive.size() + " restantes)");
        checkWin(a);
    }

    private void checkWin(Arena a) {
        if (a.state != State.RUNNING) return;
        switch (a.type) {
            case BOXING -> {
                Set<Integer> teams = new HashSet<>();
                for (UUID id : a.alive) teams.add(a.team.getOrDefault(id, -1));
                if (a.alive.isEmpty()) end(a, List.of(), false);
                else if (teams.size() <= 1) end(a, new ArrayList<>(a.alive), true);
            }
            case SPACE -> {
                if (a.alive.size() <= 1) end(a, new ArrayList<>(a.alive), true);
            }
            case RACE -> {
                if (a.alive.isEmpty()) finishRace(a);
            }
        }
    }

    private void end(Arena a, List<UUID> winners, boolean payWinners) {
        if (a.state == State.ENDING) return;
        a.state = State.ENDING;
        int win = cfg("win-reward", 40), part = cfg("participation-reward", 5);
        List<String> names = new ArrayList<>();
        for (UUID id : a.players) {
            boolean w = winners.contains(id);
            Player p = Bukkit.getPlayer(id);
            if (w && p != null) names.add(Msg.esc(p.getName()));
            if (a.type == Type.RACE && a.finished.contains(id)) continue; // ya cobró al llegar
            int amount = w ? (payWinners ? win : 0) : part;
            if (amount > 0) plugin.economy().add(id, amount);
            if (p != null) {
                Msg.title(p, w ? "<gradient:#ffd166:#fff3b0><bold>¡VICTORIA!" : "<gray><bold>Fin de la partida",
                        "<gray>+" + EconomyManager.fmt(amount));
                if (w) p.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, p.getLocation().add(0, 1, 0), 60, 0.6, 1, 0.6, 0.3);
            }
        }
        announce(a, names.isEmpty() ? "Partida terminada sin ganadores." : "<gold>Ganador/es: <white>" + String.join(", ", names));
        for (Horse h : a.karts.values()) h.remove();
        a.karts.clear();
        Bukkit.getScheduler().runTaskLater(plugin, () -> reset(a), 100L);
    }

    private void reset(Arena a) {
        for (UUID id : new ArrayList<>(a.players)) {
            Player p = Bukkit.getPlayer(id);
            Saved s = a.saved.remove(id);
            if (p != null && s != null) s.restore(p);
        }
        for (Horse h : a.karts.values()) h.remove();
        a.karts.clear();
        a.players.clear();
        a.alive.clear();
        a.saved.clear();
        a.team.clear();
        a.progress.clear();
        a.finished.clear();
        a.state = State.WAITING;
    }

    public void shutdown() {
        for (Arena a : arenas.values()) if (!a.players.isEmpty()) reset(a);
    }

    // ───────── Protección dentro del gulag ─────────
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;
        Arena a = arenaOf(p);
        if (a == null) return;
        if (a.state != State.RUNNING || !a.alive.contains(p.getUniqueId()) || a.type == Type.RACE) {
            e.setCancelled(true);
            return;
        }
        if (a.type == Type.BOXING) {
            boolean ok = e instanceof EntityDamageByEntityEvent ed && ed.getDamager() instanceof Player att
                    && a.alive.contains(att.getUniqueId())
                    && !Objects.equals(a.team.get(att.getUniqueId()), a.team.get(p.getUniqueId()));
            if (!ok) { e.setCancelled(true); return; }
        }
        if (a.type == Type.SPACE && e.getCause() == EntityDamageEvent.DamageCause.FALL) {
            e.setCancelled(true);
            return;
        }
        if (p.getHealth() - e.getFinalDamage() <= 0) {
            e.setCancelled(true);
            eliminate(a, p, "fue eliminado");
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) { leave(e.getPlayer()); }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) { if (isInGulag(e.getPlayer())) e.setCancelled(true); }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) { if (isInGulag(e.getPlayer())) e.setCancelled(true); }

    @EventHandler(ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent e) { if (isInGulag(e.getPlayer())) e.setCancelled(true); }

    @EventHandler(ignoreCancelled = true)
    public void onSwap(PlayerSwapHandItemsEvent e) { if (isInGulag(e.getPlayer())) e.setCancelled(true); }

    @EventHandler(ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent e) { if (e.getEntity() instanceof Player p && isInGulag(p)) e.setCancelled(true); }

    @EventHandler(ignoreCancelled = true)
    public void onClick(InventoryClickEvent e) { if (e.getWhoClicked() instanceof Player p && isInGulag(p)) e.setCancelled(true); }

    @EventHandler(ignoreCancelled = true)
    public void onCommandUse(PlayerCommandPreprocessEvent e) {
        Player p = e.getPlayer();
        if (!isInGulag(p) || p.hasPermission("dedsafio.admin")) return;
        String m = e.getMessage().toLowerCase();
        if (m.startsWith("/gulag")) return;
        e.setCancelled(true);
        Msg.send(p, "<red>Estás en un gulag. Usa <white>/gulag salir <red>para salir.");
    }

    // ───────── Comando ─────────
    private boolean admin(CommandSender s) {
        if (s.hasPermission("dedsafio.admin")) return true;
        Msg.send(s, "<red>Sin permiso.");
        return false;
    }

    @Override
    public boolean onCommand(CommandSender s, Command cmd, String l, String[] args) {
        if (args.length == 0) { help(s); return true; }
        if (args[0].equalsIgnoreCase("salir")) {
            if (s instanceof Player p && leave(p)) Msg.send(p, "Has salido del gulag.");
            else Msg.send(s, "<red>No estás en ningún gulag.");
            return true;
        }
        if (args[0].equalsIgnoreCase("lista")) {
            for (Arena a : arenas.values()) status(s, a);
            return true;
        }
        Type t = Type.of(args[0]);
        if (t == null) { help(s); return true; }
        Arena a = arenas.get(t);
        String sub = args.length > 1 ? args[1].toLowerCase() : "estado";
        Player p = s instanceof Player pl ? pl : null;

        switch (sub) {
            case "unirse" -> { if (p == null) Msg.send(s, "Solo jugadores."); else join(p, a); }
            case "salir" -> { if (p == null || !leave(p)) Msg.send(s, "<red>No estás en ningún gulag."); }
            case "estado" -> status(s, a);
            case "lobby" -> {
                if (!admin(s) || p == null) return true;
                a.lobby = p.getLocation();
                save();
                Msg.send(s, "Lobby de <white>" + t.title + "<gray> fijado.");
            }
            case "spawn" -> {
                if (!admin(s) || p == null) return true;
                a.spawns.add(p.getLocation());
                save();
                Msg.send(s, "Spawn #" + a.spawns.size() + " añadido.");
            }
            case "limpiar" -> {
                if (!admin(s)) return true;
                a.spawns.clear();
                a.checkpoints.clear();
                save();
                Msg.send(s, "Spawns y checkpoints borrados.");
            }
            case "meta" -> {
                if (!admin(s) || p == null) return true;
                a.finish = p.getLocation();
                save();
                Msg.send(s, "Meta fijada.");
            }
            case "checkpoint" -> {
                if (!admin(s) || p == null) return true;
                a.checkpoints.add(p.getLocation());
                save();
                Msg.send(s, "Checkpoint #" + a.checkpoints.size() + " añadido.");
            }
            case "iniciar" -> {
                if (!admin(s)) return true;
                if (a.state == State.RUNNING || a.state == State.ENDING || a.players.isEmpty()) {
                    Msg.send(s, "<red>No se puede iniciar (¿sin jugadores o ya en curso?).");
                    return true;
                }
                begin(a);
            }
            case "detener" -> {
                if (!admin(s)) return true;
                reset(a);
                Msg.send(s, "Gulag detenido y jugadores restaurados.");
            }
            default -> help(s);
        }
        return true;
    }

    private void status(CommandSender s, Arena a) {
        Msg.send(s, "<" + a.type.color + ">" + a.type.title + " <gray>· <white>" + a.state + " <gray>· jugadores <white>" + a.players.size()
                + " <gray>· lobby " + (a.lobby != null ? "<green>✔" : "<red>✘") + " <gray>· spawns <white>" + a.spawns.size()
                + (a.type == Type.RACE ? " <gray>· meta " + (a.finish != null ? "<green>✔" : "<red>✘") + " <gray>· checkpoints <white>" + a.checkpoints.size() : ""));
    }

    private void help(CommandSender s) {
        Msg.send(s, "<gradient:#ff9a3c:#ffd166><bold>Gulags");
        Msg.send(s, "<white>/gulag <boxeo|espacio|carreras> unirse|salir|estado");
        Msg.send(s, "<white>/gulag salir <dark_gray>· <white>/gulag lista");
        if (s.hasPermission("dedsafio.admin"))
            Msg.send(s, "<gray>Admin: <white>lobby, spawn, limpiar, meta, checkpoint, iniciar, detener");
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command cmd, String l, String[] a) {
        if (a.length == 1) return Util.filter(List.of("boxeo", "espacio", "carreras", "salir", "lista"), a[0]);
        if (a.length == 2 && Type.of(a[0]) != null) {
            List<String> subs = new ArrayList<>(List.of("unirse", "salir", "estado"));
            if (s.hasPermission("dedsafio.admin")) subs.addAll(List.of("lobby", "spawn", "limpiar", "iniciar", "detener"));
            if (s.hasPermission("dedsafio.admin") && Type.of(a[0]) == Type.RACE) subs.addAll(List.of("meta", "checkpoint"));
            return Util.filter(subs, a[1]);
        }
        return List.of();
    }
}

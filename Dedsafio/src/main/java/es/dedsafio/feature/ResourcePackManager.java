package es.dedsafio.feature;

import es.dedsafio.Dedsafio;
import es.dedsafio.Msg;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;

/** Envía el resource pack con las texturas de Dedsafío al entrar (configurable en config.yml). */
public final class ResourcePackManager implements Listener {
    private final Dedsafio plugin;

    public ResourcePackManager(Dedsafio plugin) { this.plugin = plugin; }

    private FileConfiguration cfg() { return plugin.getConfig(); }

    public boolean configured() {
        return cfg().getBoolean("resource-pack.enabled", true)
                && !cfg().getString("resource-pack.url", "").isBlank();
    }

    /** Envía el pack a un jugador. Devuelve false si no hay URL configurada. */
    public boolean send(Player p) {
        if (!configured()) return false;
        String url = cfg().getString("resource-pack.url", "").trim();
        byte[] hash = parseSha1(cfg().getString("resource-pack.sha1", ""));
        boolean force = cfg().getBoolean("resource-pack.required", false);
        Component prompt = Msg.c(cfg().getString("resource-pack.prompt",
                "<gradient:#ff9a3c:#ffd166><bold>Dedsafío</bold></gradient> <gray>usa texturas propias."));
        p.setResourcePack(url, hash, prompt, force);
        return true;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        if (!configured()) return;
        Player p = e.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (p.isOnline()) send(p);
        }, 40L);
    }

    @EventHandler
    public void onStatus(PlayerResourcePackStatusEvent e) {
        Player p = e.getPlayer();
        switch (e.getStatus()) {
            case DECLINED -> Msg.send(p, "<yellow>Has rechazado el pack: verás los objetos con texturas vanilla. "
                    + "Usa <white>/dedsafio pack <yellow>para cargarlo.");
            case FAILED_DOWNLOAD, INVALID_URL, FAILED_RELOAD, DISCARDED ->
                    Msg.send(p, "<red>No se pudo cargar el resource pack. Avisa a un admin.");
            default -> { }
        }
    }

    /** Convierte 40 caracteres hex en bytes. Si no es válido devuelve null (el cliente no verificará). */
    static byte[] parseSha1(String s) {
        if (s == null) return null;
        s = s.trim();
        if (s.length() != 40) return null;
        byte[] out = new byte[20];
        try {
            for (int i = 0; i < 20; i++)
                out[i] = (byte) Integer.parseInt(s.substring(i * 2, i * 2 + 2), 16);
        } catch (NumberFormatException ex) {
            return null;
        }
        return out;
    }
}

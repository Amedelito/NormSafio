package es.dedsafio.feature;

import es.dedsafio.Dedsafio;
import es.dedsafio.Msg;
import es.dedsafio.Util;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** /dedsafio give|list|pack|reload|ayuda */
public final class MainCommand implements TabExecutor {
    private final Dedsafio plugin;

    public MainCommand(Dedsafio plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender s, Command cmd, String l, String[] a) {
        String sub = a.length == 0 ? "ayuda" : a[0].toLowerCase();
        switch (sub) {
            case "give" -> {
                if (!s.hasPermission("dedsafio.admin")) { Msg.send(s, "<red>Sin permiso."); return true; }
                if (a.length < 3) { Msg.send(s, "Uso: /dedsafio give <jugador> <id> [cantidad]"); return true; }
                Player t = Bukkit.getPlayerExact(a[1]);
                if (t == null) { Msg.send(s, "<red>Jugador no conectado."); return true; }
                int n = a.length > 3 ? Math.max(1, Util.parseInt(a[3])) : 1;
                ItemStack it = plugin.items().create(a[2].toLowerCase(), n);
                if (it == null) { Msg.send(s, "<red>Objeto desconocido. Usa <white>/dedsafio list"); return true; }
                Util.give(t, it);
                Msg.send(s, "Entregado <white>" + a[2] + " x" + it.getAmount() + "<gray> a <white>" + Msg.esc(t.getName()));
            }
            case "list" -> Msg.send(s, "Objetos: <white>" + String.join("<gray>, <white>", plugin.items().ids()));
            case "pack" -> {
                if (!(s instanceof Player pl)) { Msg.send(s, "Solo jugadores."); return true; }
                if (!plugin.pack().send(pl))
                    Msg.send(s, "<red>No hay resource pack configurado (<white>resource-pack.url<red> en config.yml).");
                else
                    Msg.send(s, "Enviando resource pack…");
            }
            case "reload" -> {
                if (!s.hasPermission("dedsafio.admin")) { Msg.send(s, "<red>Sin permiso."); return true; }
                plugin.reloadConfig();
                plugin.shop().reloadCatalog();
                Msg.send(s, "Configuración recargada.");
            }
            default -> {
                Msg.send(s, "<gradient:#ff9a3c:#ffd166><bold>Comandos de Dedsafío");
                Msg.send(s, "<white>/tienda <dark_gray>· <gray>comprar con deditas");
                Msg.send(s, "<white>/deditas <dark_gray>· <gray>saldo, pagar, retirar, depositar, top");
                Msg.send(s, "<white>/reto iniciar [dificultad] <dark_gray>· <gray>oleadas + jefe final");
                Msg.send(s, "<white>/gulag <boxeo|espacio|carreras> unirse <dark_gray>· <gray>minijuegos");
                Msg.send(s, "<white>/emote <saludo|baile|victoria|reverencia>");
                Msg.send(s, "<white>/dedsafio pack <dark_gray>· <gray>cargar el resource pack");
                if (s.hasPermission("dedsafio.admin"))
                    Msg.send(s, "<white>/dedsafio give|list|reload <dark_gray>· <white>/portal <dark_gray>· <gray>admin");
            }
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command cmd, String l, String[] a) {
        if (a.length == 1) return Util.filter(List.of("ayuda", "give", "list", "pack", "reload"), a[0]);
        if (a.length == 2 && a[0].equalsIgnoreCase("give"))
            return Util.filter(Bukkit.getOnlinePlayers().stream().map(Player::getName).toList(), a[1]);
        if (a.length == 3 && a[0].equalsIgnoreCase("give"))
            return Util.filter(new ArrayList<>(plugin.items().ids()), a[2]);
        return List.of();
    }
}

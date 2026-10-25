package es.dedsafio;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.time.Duration;

public final class Msg {
    private static final MiniMessage MM = MiniMessage.miniMessage();
    public static final String PREFIX = "<gradient:#ff9a3c:#ffd166><bold>DEDSAFÍO</bold></gradient> <dark_gray>»</dark_gray> ";

    private Msg() {}

    public static Component c(String s) {
        return MM.deserialize(s).decoration(TextDecoration.ITALIC, false);
    }

    public static void send(CommandSender to, String s) {
        to.sendMessage(MM.deserialize(PREFIX + "<gray>" + s));
    }

    public static void bar(Player p, String s) {
        p.sendActionBar(MM.deserialize(s));
    }

    public static void title(Player p, String t, String sub) {
        p.showTitle(Title.title(MM.deserialize(t), MM.deserialize(sub),
                Title.Times.times(Duration.ofMillis(200), Duration.ofMillis(1800), Duration.ofMillis(400))));
    }

    public static String esc(String s) {
        return MM.escapeTags(s);
    }
}

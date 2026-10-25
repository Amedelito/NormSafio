package es.dedsafio;

import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;

public final class Cooldowns {
    private final Map<String, Long> map = new HashMap<>();

    /** Devuelve true si puede usarse (y arranca la recarga). Si no, avisa en la barra de acción. */
    public boolean use(Player p, String key, long millis) {
        long now = System.currentTimeMillis();
        String k = p.getUniqueId() + ":" + key;
        Long until = map.get(k);
        if (until != null && until > now) {
            Msg.bar(p, "<red>Recarga: <white>" + String.format("%.1f", (until - now) / 1000.0) + "s");
            return false;
        }
        map.put(k, now + millis);
        return true;
    }
}

package es.dedsafio;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.stream.Collectors;

public final class Util {
    private Util() {}

    public static void give(Player p, ItemStack... stacks) {
        for (ItemStack s : stacks) {
            p.getInventory().addItem(s).values()
                    .forEach(left -> p.getWorld().dropItemNaturally(p.getLocation(), left));
        }
    }

    public static Location center(Block b) {
        return b.getLocation().add(0.5, 0.5, 0.5);
    }

    public static boolean isAir(ItemStack i) {
        return i == null || i.getType().isAir();
    }

    public static List<String> filter(List<String> options, String prefix) {
        String p = prefix.toLowerCase();
        return options.stream().filter(o -> o.toLowerCase().startsWith(p)).collect(Collectors.toList());
    }

    public static int parseInt(String s) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}

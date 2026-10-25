package es.dedsafio.feature;

import es.dedsafio.Dedsafio;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MerchantRecipe;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

/** Los aldeanos de nivel máximo venden el Bloque Ancestral. */
public final class VillagerListener implements Listener {
    private final Dedsafio plugin;
    private final NamespacedKey added;

    public VillagerListener(Dedsafio plugin) {
        this.plugin = plugin;
        this.added = new NamespacedKey(plugin, "ancestral_trade");
    }

    @EventHandler
    public void onInteract(PlayerInteractEntityEvent e) {
        if (!(e.getRightClicked() instanceof Villager v)) return;
        if (v.getVillagerLevel() < 5) return;
        if (v.getPersistentDataContainer().has(added, PersistentDataType.BYTE)) return;
        MerchantRecipe r = new MerchantRecipe(plugin.items().create("ancestral_block"), 0, 3, true, 15, 0.05f);
        r.addIngredient(new ItemStack(Material.EMERALD, 48));
        r.addIngredient(new ItemStack(Material.AMETHYST_SHARD, 8));
        List<MerchantRecipe> list = new ArrayList<>(v.getRecipes());
        list.add(r);
        v.setRecipes(list);
        v.getPersistentDataContainer().set(added, PersistentDataType.BYTE, (byte) 1);
    }
}

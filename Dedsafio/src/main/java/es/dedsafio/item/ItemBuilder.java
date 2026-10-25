package es.dedsafio.item;

import es.dedsafio.Dedsafio;
import es.dedsafio.Msg;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.Arrays;

final class ItemBuilder {
    private static int counter;
    private final Dedsafio plugin;
    private final ItemStack item;
    private final ItemMeta meta;

    ItemBuilder(Dedsafio plugin, Material material) {
        this.plugin = plugin;
        this.item = new ItemStack(material);
        this.meta = item.getItemMeta();
    }

    ItemBuilder name(String mm) {
        meta.displayName(Msg.c(mm));
        return this;
    }

    ItemBuilder lore(String... lines) {
        meta.lore(Arrays.stream(lines).map(Msg::c).toList());
        return this;
    }

    ItemBuilder model(int value) {
        meta.setCustomModelData(value);
        return this;
    }

    ItemBuilder glint() {
        meta.setEnchantmentGlintOverride(true);
        return this;
    }

    ItemBuilder unbreakable() {
        meta.setUnbreakable(true);
        return this;
    }

    ItemBuilder enchant(Enchantment e, int level) {
        meta.addEnchant(e, level, true);
        return this;
    }

    ItemBuilder attr(Attribute a, double value, AttributeModifier.Operation op, EquipmentSlotGroup group) {
        meta.addAttributeModifier(a, new AttributeModifier(
                new NamespacedKey(plugin, "mod_" + (counter++)), value, op, group));
        return this;
    }

    ItemBuilder dye(int rgb) {
        if (meta instanceof LeatherArmorMeta lm) lm.setColor(Color.fromRGB(rgb));
        return this;
    }

    ItemBuilder id(NamespacedKey key, String id) {
        meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, id);
        return this;
    }

    ItemMeta meta() {
        return meta;
    }

    ItemStack build() {
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_DYE,
                ItemFlag.HIDE_ADDITIONAL_TOOLTIP, ItemFlag.HIDE_UNBREAKABLE);
        item.setItemMeta(meta);
        return item;
    }
}

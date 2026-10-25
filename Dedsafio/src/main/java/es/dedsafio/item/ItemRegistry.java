package es.dedsafio.item;

import es.dedsafio.Dedsafio;
import net.kyori.adventure.text.Component;
import es.dedsafio.Msg;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier.Operation;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.*;
import java.util.function.Supplier;

/** Registro central de todos los objetos personalizados. Cada uno lleva su id en el PDC. */
public final class ItemRegistry {
    public static final List<String> SETS = List.of("dungeon", "earth", "dimensional", "boxing_red", "boxing_blue");
    public static final String[] PIECES = {"helmet", "chestplate", "leggings", "boots"};
    private static final String[] PIECE_ES = {"Casco", "Peto", "Grebas", "Botas"};
    private static final EquipmentSlotGroup[] SLOTS = {
            EquipmentSlotGroup.HEAD, EquipmentSlotGroup.CHEST, EquipmentSlotGroup.LEGS, EquipmentSlotGroup.FEET};
    private static final int SOUL_CAP = 100;

    private record SetDef(String name, String color, Material[] mats, double[] pts, double tough, double kb,
                          int dye, String ability, boolean glint) {}

    private final Dedsafio plugin;
    private final NamespacedKey idKey;
    private final NamespacedKey soulsKey;
    private final Map<String, Supplier<ItemStack>> factories = new LinkedHashMap<>();

    public ItemRegistry(Dedsafio plugin) {
        this.plugin = plugin;
        this.idKey = new NamespacedKey(plugin, "item_id");
        this.soulsKey = new NamespacedKey(plugin, "souls");
        registerAll();
    }

    // ───────────────────────── API ─────────────────────────

    public Set<String> ids() { return factories.keySet(); }

    public boolean exists(String id) { return factories.containsKey(id); }

    public ItemStack create(String id) {
        Supplier<ItemStack> s = factories.get(id);
        return s == null ? null : s.get();
    }

    public ItemStack create(String id, int amount) {
        ItemStack it = create(id);
        if (it != null) it.setAmount(Math.max(1, Math.min(amount, it.getMaxStackSize())));
        return it;
    }

    public String idOf(ItemStack it) {
        if (it == null || !it.hasItemMeta()) return null;
        return it.getItemMeta().getPersistentDataContainer().get(idKey, PersistentDataType.STRING);
    }

    public boolean is(ItemStack it, String id) { return id.equals(idOf(it)); }

    public int deditaValue(ItemStack it) {
        String id = idOf(it);
        if (id == null) return 0;
        return switch (id) {
            case "dedita_common" -> 1;
            case "dedita_rare" -> 10;
            case "dedita_legendary" -> 100;
            default -> 0;
        };
    }

    public int souls(ItemStack it) {
        if (it == null || !it.hasItemMeta()) return 0;
        return it.getItemMeta().getPersistentDataContainer().getOrDefault(soulsKey, PersistentDataType.INTEGER, 0);
    }

    public void setSouls(ItemStack it, int n) {
        n = Math.max(0, Math.min(SOUL_CAP, n));
        ItemMeta m = it.getItemMeta();
        m.getPersistentDataContainer().set(soulsKey, PersistentDataType.INTEGER, n);
        m.lore(soulLore(n));
        it.setItemMeta(m);
    }

    // ───────────────────────── Registro ─────────────────────────

    private void reg(String id, Supplier<ItemStack> s) { factories.put(id, s); }

    private void registerAll() {
        // Armaduras
        for (String set : SETS) {
            for (int i = 0; i < 4; i++) {
                final String s = set;
                final int idx = i;
                reg(set + "_" + PIECES[i], () -> armor(s, idx));
            }
        }

        // Espadas legendarias
        reg("ancestral_sword", () -> sword("ancestral_sword", Material.NETHERITE_SWORD, "#d4a373", "Espada Ancestral", 9, 1.6, 2001, false,
                "<gray>Forjada con la sangre de los antiguos.", "",
                "<#d4a373>Sed Ancestral", "<gray>20% de robar <red>2❤</red> al golpear.")
                .enchant(Enchantment.SHARPNESS, 3).enchant(Enchantment.UNBREAKING, 3).build());
        reg("belic_sword", () -> sword("belic_sword", Material.DIAMOND_SWORD, "#e63946", "Espada Bélica", 8, 1.8, 2002, false,
                "<gray>Hecha para ganar guerras.", "",
                "<#e63946>Furia Bélica", "<gray>Cada 5.º golpe causa <red>+50% de daño</red> y prende.",
                "<gray>Al matar ganas <red>Fuerza I</red> 5s.")
                .enchant(Enchantment.FIRE_ASPECT, 1).enchant(Enchantment.SHARPNESS, 2).enchant(Enchantment.UNBREAKING, 3).build());
        reg("celestial_sword", () -> sword("celestial_sword", Material.GOLDEN_SWORD, "#ffd166", "Espada Celestial", 8.5, 1.7, 2003, true,
                "<gray>Un fragmento del cielo hecho acero.", "",
                "<#ffd166>Rayo Celestial <dark_gray>(clic derecho)", "<gray>Invoca un rayo donde apuntas. Recarga: 15s.",
                "<gray>Los golpes hacen brillar al enemigo.")
                .unbreakable().build());
        // Exclusivas del Gulag del Espacio
        reg("space_stellar_sword", () -> sword("space_stellar_sword", Material.IRON_SWORD, "#90e0ef", "Sable Estelar", 7, 1.8, 2004, true,
                "<dark_gray>Exclusiva del Gulag del Espacio", "",
                "<#90e0ef>Impulso Estelar", "<gray>Los golpes hacen <white>levitar</white> al enemigo.")
                .unbreakable().build());
        reg("space_void_sword", () -> sword("space_void_sword", Material.NETHERITE_SWORD, "#7b2cbf", "Hoja del Vacío", 8, 1.5, 2005, true,
                "<dark_gray>Exclusiva del Gulag del Espacio", "",
                "<#7b2cbf>Pulso del Vacío <dark_gray>(clic derecho)", "<gray>Repele a todos los enemigos cercanos. Recarga: 8s.")
                .unbreakable().build());
        // Boxeo
        reg("boxing_gloves", () -> new ItemBuilder(plugin, Material.RED_DYE)
                .name("<#e63946><bold>Guantes de Boxeo").model(2101)
                .lore("<dark_gray>Exclusivo del Gulag de Boxeo", "", "<gray>Puños pesados. Empuje mejorado.")
                .attr(Attribute.GENERIC_ATTACK_DAMAGE, 2, Operation.ADD_NUMBER, EquipmentSlotGroup.MAINHAND)
                .attr(Attribute.GENERIC_ATTACK_SPEED, -1.5, Operation.ADD_NUMBER, EquipmentSlotGroup.MAINHAND)
                .enchant(Enchantment.KNOCKBACK, 1).unbreakable().id(idKey, "boxing_gloves").build());

        // Pociones especiales
        reg("potion_vitality", () -> potion("potion_vitality", "#ff6b6b", 0xFF6B6B, "Poción de Vitalidad", 2201,
                "<gray>Regeneración II (30s)", "<gray>Absorción II (30s)",
                new PotionEffect(PotionEffectType.REGENERATION, 600, 1), new PotionEffect(PotionEffectType.ABSORPTION, 600, 1)));
        reg("potion_fury", () -> potion("potion_fury", "#e63946", 0xC1121F, "Poción de Furia", 2202,
                "<gray>Fuerza II (60s)", "<gray>Velocidad I (60s)",
                new PotionEffect(PotionEffectType.STRENGTH, 1200, 1), new PotionEffect(PotionEffectType.SPEED, 1200, 0)));
        reg("potion_dimensional", () -> potion("potion_dimensional", "#b388ff", 0xB388FF, "Poción Dimensional", 2203,
                "<gray>Velocidad II (45s)", "<gray>Salto III (45s)", "<gray>Visión nocturna (2 min)",
                new PotionEffect(PotionEffectType.SPEED, 900, 1), new PotionEffect(PotionEffectType.JUMP_BOOST, 900, 2),
                new PotionEffect(PotionEffectType.NIGHT_VISION, 2400, 0)));
        reg("potion_ancestral", () -> potion("potion_ancestral", "#d4a373", 0xD4A373, "Elixir Ancestral", 2204,
                "<gray>Resistencia al fuego (3 min)", "<gray>Respiración acuática (3 min)", "<gray>Resistencia I (30s)",
                new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 3600, 0), new PotionEffect(PotionEffectType.WATER_BREATHING, 3600, 0),
                new PotionEffect(PotionEffectType.RESISTANCE, 600, 0)));

        // Flor Recolectora de Almas
        reg("soul_flower", () -> {
            ItemStack it = new ItemBuilder(plugin, Material.WITHER_ROSE).name("<gradient:#6a4c93:#c77dff><bold>Flor Recolectora de Almas")
                    .model(2301).glint().id(idKey, "soul_flower").build();
            setSouls(it, 0);
            return it;
        });

        // Cuchara de la Resurrección (decorativa)
        reg("resurrection_spoon", () -> new ItemBuilder(plugin, Material.STICK)
                .name("<gradient:#ffd166:#fff3b0><bold>Cuchara de la Resurrección").model(2401).glint()
                .lore("<gray>Un coleccionable legendario.", "", "<dark_gray>Es puramente decorativa.", "<dark_gray>No hace absolutamente nada.")
                .id(idKey, "resurrection_spoon").build());

        // Deditas
        reg("dedita_common", () -> dedita("dedita_common", Material.COPPER_INGOT, "#b87333", "Dedita Común", 1, 2501, false));
        reg("dedita_rare", () -> dedita("dedita_rare", Material.AMETHYST_SHARD, "#9d4edd", "Dedita Rara", 10, 2502, true));
        reg("dedita_legendary", () -> dedita("dedita_legendary", Material.ECHO_SHARD, "#ffd60a", "Dedita Legendaria", 100, 2503, true));

        // Tótems
        reg("totem_eonic", () -> totem("totem_eonic", "#7df9ff", "Tótem Eónico", 2601,
                "<gray>Regeneración II, Velocidad II y Absorción III.", "<gray>El tiempo se curva a tu favor."));
        reg("totem_iron", () -> totem("totem_iron", "#adb5bd", "Tótem Férreo", 2602,
                "<gray>Resistencia III y Absorción IV.", "<gray>Inquebrantable como el hierro."));
        reg("totem_otter", () -> totem("totem_otter", "#8d6e63", "Tótem de Nutria", 2603,
                "<gray>Gracia del delfín, respiración acuática", "<gray>y Regeneración I. ¡Chof!"));
        reg("totem_flashbang", () -> totem("totem_flashbang", "#f8f9fa", "Tótem de Flashbang", 2604,
                "<gray>Al activarse, ciega a todos los que", "<gray>estén cerca. Protégete los ojos."));

        // Bloques y entidades
        reg("rune_table", () -> placeable("rune_table", Material.ENCHANTING_TABLE, "#c77dff", "Mesa de Runas", 2701,
                "<gray>Mejora tu equipo con runas", "<gray>pagando con <gold>deditas</gold>."));
        reg("lighthouse", () -> placeable("lighthouse", Material.SEA_LANTERN, "#90e0ef", "Faro", 2702,
                "<gray>Otorga <green>Regeneración</green> y <aqua>Visión nocturna</aqua>", "<gray>a los jugadores cercanos."));
        reg("ancestral_block", () -> placeable("ancestral_block", Material.CRYING_OBSIDIAN, "#d4a373", "Bloque Ancestral", 2703,
                "<gray>Un bloque de otra era.", "<gray>Lo venden aldeanos de nivel máximo."));
        reg("statue", () -> new ItemBuilder(plugin, Material.ARMOR_STAND).name("<#ffd166><bold>Estatua").model(2704)
                .lore("<gray>Crea una estatua con tu equipo actual.", "", "<dark_gray>Agáchate + clic derecho para recogerla.", "<dark_gray>Clic derecho para girarla.")
                .id(idKey, "statue").build());
        reg("kart", () -> new ItemBuilder(plugin, Material.SADDLE).name("<#ffd166><bold>Kart").model(2705)
                .lore("<gray>Un kart rápido para el Gulag de Carreras.", "", "<dark_gray>Clic derecho sobre un bloque.")
                .id(idKey, "kart").build());
    }

    // ───────────────────────── Constructores ─────────────────────────

    private SetDef setDef(String set) {
        Material[] leather = {Material.LEATHER_HELMET, Material.LEATHER_CHESTPLATE, Material.LEATHER_LEGGINGS, Material.LEATHER_BOOTS};
        return switch (set) {
            case "dungeon" -> new SetDef("Dungeon", "#8d99ae",
                    new Material[]{Material.CHAINMAIL_HELMET, Material.CHAINMAIL_CHESTPLATE, Material.CHAINMAIL_LEGGINGS, Material.CHAINMAIL_BOOTS},
                    new double[]{2, 5, 4, 2}, 1.0, 0.0, -1, "Resistencia I y Resistencia al fuego", false);
            case "earth" -> new SetDef("Earth", "#6a994e", leather, new double[]{3, 6, 5, 3}, 1.5, 0.05, 0x6A994E,
                    "Prisa II y Salud extra", false);
            case "dimensional" -> new SetDef("Dimensional", "#b388ff",
                    new Material[]{Material.DIAMOND_HELMET, Material.DIAMOND_CHESTPLATE, Material.DIAMOND_LEGGINGS, Material.DIAMOND_BOOTS},
                    new double[]{3, 8, 6, 3}, 3.0, 0.0, -1, "Velocidad I, Salto I y sin daño de caída", true);
            case "boxing_red" -> new SetDef("de Boxeo Rojo", "#e63946", leather, new double[]{1, 3, 2, 1}, 0, 0, 0xE63946,
                    "Equipo del Gulag de Boxeo", false);
            default -> new SetDef("de Boxeo Azul", "#457b9d", leather, new double[]{1, 3, 2, 1}, 0, 0, 0x457B9D,
                    "Equipo del Gulag de Boxeo", false);
        };
    }

    private ItemStack armor(String set, int i) {
        SetDef d = setDef(set);
        boolean boxing = set.startsWith("boxing");
        ItemBuilder b = new ItemBuilder(plugin, d.mats()[i])
                .name("<" + d.color() + "><bold>" + PIECE_ES[i] + "</bold> " + d.name())
                .lore("<dark_gray>Armadura de Dedsafío", "",
                        "<gray>Defensa: <white>+" + (int) d.pts()[i],
                        "<gray>Set completo: <" + d.color() + ">" + d.ability())
                .attr(Attribute.GENERIC_ARMOR, d.pts()[i], Operation.ADD_NUMBER, SLOTS[i])
                .model(3000 + SETS.indexOf(set) * 10 + i)
                .id(idKey, set + "_" + PIECES[i]);
        if (d.tough() > 0) b.attr(Attribute.GENERIC_ARMOR_TOUGHNESS, d.tough(), Operation.ADD_NUMBER, SLOTS[i]);
        if (d.kb() > 0) b.attr(Attribute.GENERIC_KNOCKBACK_RESISTANCE, d.kb(), Operation.ADD_NUMBER, SLOTS[i]);
        if (d.dye() >= 0) b.dye(d.dye());
        if (d.glint()) b.glint();
        if (boxing) b.unbreakable(); else b.enchant(Enchantment.UNBREAKING, 3);
        return b.build();
    }

    private ItemBuilder sword(String id, Material m, String color, String name, double dmg, double speed, int model,
                              boolean glint, String... lore) {
        List<String> l = new ArrayList<>(List.of(lore));
        l.add("");
        l.add("<gray>Daño: <white>" + dmg + "  <gray>Velocidad: <white>" + speed);
        ItemBuilder b = new ItemBuilder(plugin, m).name("<" + color + "><bold>" + name).model(model)
                .lore(l.toArray(new String[0]))
                .attr(Attribute.GENERIC_ATTACK_DAMAGE, dmg - 1, Operation.ADD_NUMBER, EquipmentSlotGroup.MAINHAND)
                .attr(Attribute.GENERIC_ATTACK_SPEED, speed - 4.0, Operation.ADD_NUMBER, EquipmentSlotGroup.MAINHAND)
                .id(idKey, id);
        if (glint) b.glint();
        return b;
    }

    private ItemStack potion(String id, String color, int rgb, String name, int model, Object... rest) {
        List<String> lore = new ArrayList<>(List.of("<dark_gray>Poción especial de la serie", ""));
        List<PotionEffect> effects = new ArrayList<>();
        for (Object o : rest) {
            if (o instanceof String s) lore.add(s);
            else if (o instanceof PotionEffect pe) effects.add(pe);
        }
        ItemBuilder b = new ItemBuilder(plugin, Material.POTION).name("<" + color + "><bold>" + name).model(model)
                .lore(lore.toArray(new String[0])).glint().id(idKey, id);
        PotionMeta pm = (PotionMeta) b.meta();
        pm.setColor(Color.fromRGB(rgb));
        for (PotionEffect e : effects) pm.addCustomEffect(e, true);
        return b.build();
    }

    private ItemStack dedita(String id, Material m, String color, String name, int value, int model, boolean glint) {
        ItemBuilder b = new ItemBuilder(plugin, m).name("<" + color + "><bold>" + name).model(model)
                .lore("<gray>Valor: <gold>" + value + "</gold> <yellow>dedita" + (value == 1 ? "" : "s"), "",
                        "<dark_gray>Clic derecho para ingresarla", "<dark_gray>(agáchate para ingresar todo el stack)")
                .id(idKey, id);
        if (glint) b.glint();
        return b.build();
    }

    private ItemStack totem(String id, String color, String name, int model, String... lore) {
        List<String> l = new ArrayList<>(List.of("<dark_gray>Tótem personalizado", ""));
        l.addAll(List.of(lore));
        return new ItemBuilder(plugin, Material.TOTEM_OF_UNDYING).name("<" + color + "><bold>" + name).model(model)
                .lore(l.toArray(new String[0])).glint().id(idKey, id).build();
    }

    private ItemStack placeable(String id, Material m, String color, String name, int model, String... lore) {
        List<String> l = new ArrayList<>(List.of("<dark_gray>Bloque de Dedsafío", ""));
        l.addAll(List.of(lore));
        return new ItemBuilder(plugin, m).name("<" + color + "><bold>" + name).model(model)
                .lore(l.toArray(new String[0])).id(idKey, id).build();
    }

    private List<Component> soulLore(int n) {
        int filled = n / 10;
        String bar = "<#c77dff>" + "■".repeat(filled) + "<dark_gray>" + "■".repeat(10 - filled);
        return List.of(
                Msg.c("<gray>Absorbe las almas de tus víctimas."),
                Msg.c(""),
                Msg.c("<gray>Almas: <#c77dff>" + n + "<dark_gray>/" + SOUL_CAP + " " + bar),
                Msg.c("<gray>Matar mob: <white>+1</white>  Jugador: <white>+5"),
                Msg.c("<gray>Clic derecho (10 almas): <green>cura <white>4❤</white> y da absorción"));
    }
}

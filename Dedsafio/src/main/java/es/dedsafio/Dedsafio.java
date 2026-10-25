package es.dedsafio;

import es.dedsafio.feature.*;
import es.dedsafio.item.ItemRegistry;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabExecutor;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

public final class Dedsafio extends JavaPlugin {
    private ItemRegistry items;
    private EconomyManager economy;
    private BlockManager blocks;
    private EntityItemsListener entityItems;
    private ShopMenu shop;
    private ChallengeManager challenges;
    private PortalManager portals;
    private GulagManager gulags;
    private ResourcePackManager pack;
    private final Cooldowns cooldowns = new Cooldowns();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        items = new ItemRegistry(this);
        economy = new EconomyManager(this);
        blocks = new BlockManager(this);
        entityItems = new EntityItemsListener(this);
        shop = new ShopMenu(this);
        challenges = new ChallengeManager(this);
        portals = new PortalManager(this);
        gulags = new GulagManager(this);
        pack = new ResourcePackManager(this);

        PluginManager pm = getServer().getPluginManager();
        pm.registerEvents(economy, this);
        pm.registerEvents(blocks, this);
        pm.registerEvents(blocks.runeMenu(), this);
        pm.registerEvents(entityItems, this);
        pm.registerEvents(shop, this);
        pm.registerEvents(challenges, this);
        pm.registerEvents(gulags, this);
        pm.registerEvents(pack, this);
        pm.registerEvents(new CombatListener(this), this);
        pm.registerEvents(new ArmorListener(this), this);
        pm.registerEvents(new TotemListener(this), this);
        pm.registerEvents(new SoulFlowerListener(this), this);
        pm.registerEvents(new VillagerListener(this), this);

        command("dedsafio", new MainCommand(this));
        command("deditas", economy);
        command("tienda", shop);
        command("reto", challenges);
        command("portal", portals);
        command("emote", new EmoteCommand(this));
        command("gulag", gulags);

        getLogger().info("Dedsafío cargado: " + items.ids().size() + " objetos registrados.");
    }

    @Override
    public void onDisable() {
        if (gulags != null) gulags.shutdown();
        if (challenges != null) challenges.stopAll();
        if (blocks != null) blocks.save();
        if (economy != null) economy.save();
    }

    private void command(String name, TabExecutor exec) {
        PluginCommand c = getCommand(name);
        if (c == null) return;
        c.setExecutor(exec);
        c.setTabCompleter(exec);
    }

    public ItemRegistry items() { return items; }
    public EconomyManager economy() { return economy; }
    public BlockManager blocks() { return blocks; }
    public EntityItemsListener entityItems() { return entityItems; }
    public ShopMenu shop() { return shop; }
    public ChallengeManager challenges() { return challenges; }
    public PortalManager portals() { return portals; }
    public GulagManager gulags() { return gulags; }
    public ResourcePackManager pack() { return pack; }
    public Cooldowns cooldowns() { return cooldowns; }
}

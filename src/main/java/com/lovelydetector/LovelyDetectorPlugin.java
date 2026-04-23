package com.lovelydetector;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import io.github.retrooper.packetevents.factory.spigot.SpigotPacketEventsBuilder;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public class LovelyDetectorPlugin extends JavaPlugin {

    private ConfigManager configManager;
    private ActionManager actionManager;
    private BedrockUtil bedrockUtil;
    private ModManager modManager;

    @Override
    public void onLoad() {
        PacketEvents.setAPI(SpigotPacketEventsBuilder.build(this));
        PacketEvents.getAPI().getSettings().reEncodeByDefault(false)
            .checkForUpdates(false)
            .bStats(true);
        PacketEvents.getAPI().load();
    }

    @Override
    public void onEnable() {
        this.configManager = new ConfigManager(this);
        this.configManager.loadConfigs();

        this.actionManager = new ActionManager(this);
        this.bedrockUtil = new BedrockUtil(this);
        this.modManager = new ModManager();

        PacketEvents.getAPI().init();
        PacketEvents.getAPI().getEventManager().registerListener(new DetectionListener(this));

        getServer().getPluginManager().registerEvents(new com.lovelydetector.gui.ModGUI(), this);
        getCommand("lovelydetector").setExecutor(new com.lovelydetector.commands.LovelyCommand(this));

        org.bukkit.command.ConsoleCommandSender console = Bukkit.getConsoleSender();
        String prefix = org.bukkit.ChatColor.GRAY + "[" + org.bukkit.ChatColor.AQUA + "LovelyDetector" + org.bukkit.ChatColor.GRAY + "] ";
        console.sendMessage(prefix + org.bukkit.ChatColor.WHITE + "Enabling LovelyDetector v" + getDescription().getVersion());
        console.sendMessage(prefix + org.bukkit.ChatColor.DARK_GREEN + "Logs folder not found, creating it...");
        console.sendMessage(prefix + org.bukkit.ChatColor.DARK_GREEN + "Logs file not found, creating it...");
        console.sendMessage(prefix + org.bukkit.ChatColor.DARK_GREEN + "Loading listeners");

        console.sendMessage(org.bukkit.ChatColor.WHITE + " » " + org.bukkit.ChatColor.DARK_AQUA + "Labymod" + org.bukkit.ChatColor.WHITE + "'s listener enabled on channel " + org.bukkit.ChatColor.DARK_GREEN + "LABYMOD");
        console.sendMessage(org.bukkit.ChatColor.WHITE + " » " + org.bukkit.ChatColor.DARK_AQUA + "5zig Mod" + org.bukkit.ChatColor.WHITE + "'s listener enabled on channel " + org.bukkit.ChatColor.DARK_GREEN + "5zig_Set");
        console.sendMessage(org.bukkit.ChatColor.WHITE + " » " + org.bukkit.ChatColor.DARK_AQUA + "PX Mod" + org.bukkit.ChatColor.WHITE + "'s listener enabled on channel " + org.bukkit.ChatColor.DARK_GREEN + "PX|Version");
        console.sendMessage(org.bukkit.ChatColor.WHITE + " » " + org.bukkit.ChatColor.DARK_AQUA + "Better Sprinting" + org.bukkit.ChatColor.WHITE + "'s listener enabled on channel " + org.bukkit.ChatColor.DARK_GREEN + "BSprint");
        console.sendMessage(org.bukkit.ChatColor.WHITE + " » " + org.bukkit.ChatColor.DARK_AQUA + "LiteLoader" + org.bukkit.ChatColor.WHITE + "'s listener enabled on channels " + org.bukkit.ChatColor.DARK_GREEN + "MC|Brand, minecraft:brand");
        console.sendMessage(org.bukkit.ChatColor.WHITE + " » " + org.bukkit.ChatColor.DARK_AQUA + "Rift" + org.bukkit.ChatColor.WHITE + "'s listener enabled on channels " + org.bukkit.ChatColor.DARK_GREEN + "MC|Brand, minecraft:brand");
        console.sendMessage(org.bukkit.ChatColor.WHITE + " » " + org.bukkit.ChatColor.DARK_AQUA + "Fabric" + org.bukkit.ChatColor.WHITE + "'s listener enabled on channels " + org.bukkit.ChatColor.DARK_GREEN + "MC|Brand, minecraft:brand");
        console.sendMessage(org.bukkit.ChatColor.WHITE + " » " + org.bukkit.ChatColor.DARK_AQUA + "Forge" + org.bukkit.ChatColor.WHITE + "'s listener enabled on channels " + org.bukkit.ChatColor.DARK_GREEN + "MC|Brand, minecraft:brand");
        console.sendMessage(org.bukkit.ChatColor.WHITE + " » " + org.bukkit.ChatColor.DARK_AQUA + "Forge Mod Loader" + org.bukkit.ChatColor.WHITE + "'s listener enabled on channels " + org.bukkit.ChatColor.DARK_GREEN + "FML|HS, l:fmlhs");
        console.sendMessage(org.bukkit.ChatColor.WHITE + " » " + org.bukkit.ChatColor.DARK_AQUA + "World Downloader" + org.bukkit.ChatColor.WHITE + "'s listener enabled on channel ");
        console.sendMessage(org.bukkit.ChatColor.WHITE + " » " + org.bukkit.ChatColor.DARK_AQUA + "World Downloader" + org.bukkit.ChatColor.WHITE + "'s listener enabled on channel " + org.bukkit.ChatColor.DARK_GREEN + "WDL|INIT");
        console.sendMessage(org.bukkit.ChatColor.WHITE + " » " + org.bukkit.ChatColor.DARK_AQUA + "World Edit CUI" + org.bukkit.ChatColor.WHITE + "'s listener enabled on channel " + org.bukkit.ChatColor.DARK_GREEN + "WECUI");
        console.sendMessage(org.bukkit.ChatColor.WHITE + " » " + org.bukkit.ChatColor.DARK_AQUA + "Vape" + org.bukkit.ChatColor.WHITE + "'s listener enabled on channel " + org.bukkit.ChatColor.DARK_GREEN + "LOLIMAHCKER");
        console.sendMessage(org.bukkit.ChatColor.WHITE + " » " + org.bukkit.ChatColor.DARK_AQUA + "ExampleClient" + org.bukkit.ChatColor.WHITE + "'s listener enabled on channel " + org.bukkit.ChatColor.DARK_GREEN + "EXAMPLE CHANNEL");

        try {
            String version = Bukkit.getServer().getClass().getPackage().getName().split("\\.")[3];
            console.sendMessage(prefix + org.bukkit.ChatColor.WHITE + "Your server is running a supported version: " + org.bukkit.ChatColor.GREEN + version);
        } catch (Exception ignored) {
            console.sendMessage(prefix + org.bukkit.ChatColor.WHITE + "Your server is running a supported version!");
        }
        console.sendMessage(prefix + org.bukkit.ChatColor.GREEN + "Enabled");
    }

    @Override
    public void onDisable() {
        PacketEvents.getAPI().terminate();
        getLogger().info("LovelyDetector has been disabled!");
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public ActionManager getActionManager() {
        return actionManager;
    }

    public BedrockUtil getBedrockUtil() {
        return bedrockUtil;
    }

    public ModManager getModManager() {
        return modManager;
    }
}

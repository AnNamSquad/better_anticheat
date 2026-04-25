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
        getServer().getPluginManager().registerEvents(new com.lovelydetector.gui.GlobalGUI(), this);
        getCommand("lovelydetector").setExecutor(new com.lovelydetector.commands.LovelyCommand(this));

        org.bukkit.command.ConsoleCommandSender console = Bukkit.getConsoleSender();
        String prefix = org.bukkit.ChatColor.GRAY + "[" + org.bukkit.ChatColor.AQUA + "LovelyDetector" + org.bukkit.ChatColor.GRAY + "] ";
        console.sendMessage(prefix + org.bukkit.ChatColor.WHITE + "Enabling LovelyDetector v" + getDescription().getVersion());
        console.sendMessage(prefix + org.bukkit.ChatColor.DARK_GREEN + "Loading configurations and listeners...");

        try {
            String version = Bukkit.getServer().getClass().getPackage().getName().split("\\.")[3];
            console.sendMessage(prefix + org.bukkit.ChatColor.WHITE + "Your server is running a supported version: " + org.bukkit.ChatColor.GREEN + version);
        } catch (Exception ignored) {
            console.sendMessage(prefix + org.bukkit.ChatColor.WHITE + "Your server is running a supported version!");
        }
        console.sendMessage(prefix + org.bukkit.ChatColor.GREEN + "LovelyDetector has been successfully enabled!");
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

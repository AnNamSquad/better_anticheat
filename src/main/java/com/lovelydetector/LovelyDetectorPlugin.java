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
        PacketEvents.getAPI().getEventManager().registerListener(
            new DetectionListener(this), PacketListenerPriority.NORMAL);

        getServer().getPluginManager().registerEvents(new com.lovelydetector.gui.ModGUI(), this);
        getCommand("lovelydetector").setExecutor(new com.lovelydetector.commands.LovelyCommand(this));

        getLogger().info("LovelyDetector has been enabled!");
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

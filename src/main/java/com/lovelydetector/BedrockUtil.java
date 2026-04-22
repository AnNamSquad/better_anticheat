package com.lovelydetector;

import org.bukkit.Bukkit;

import java.util.UUID;

public class BedrockUtil {

    private final LovelyDetectorPlugin plugin;
    private boolean geyserEnabled = false;
    private boolean floodgateEnabled = false;

    public BedrockUtil(LovelyDetectorPlugin plugin) {
        this.plugin = plugin;
        this.geyserEnabled = Bukkit.getPluginManager().getPlugin("Geyser-Spigot") != null;
        this.floodgateEnabled = Bukkit.getPluginManager().getPlugin("floodgate") != null;
    }

    public boolean isBedrockPlayer(UUID uuid) {
        boolean skipJavaChecks = plugin.getConfigManager().getConfig("bedrock.yaml").getBoolean("skip-java-checks", true);
        if (!skipJavaChecks) return false;

        if (floodgateEnabled) {
            try {
                if (org.geysermc.floodgate.api.FloodgateApi.getInstance().isFloodgatePlayer(uuid)) {
                    return true;
                }
            } catch (Exception e) {
                // Ignore exception if class not found
            }
        }

        if (geyserEnabled) {
            try {
                if (org.geysermc.geyser.api.GeyserApi.api().isBedrockPlayer(uuid)) {
                    return true;
                }
            } catch (Exception e) {
                // Ignore exception
            }
        }

        return false;
    }
}

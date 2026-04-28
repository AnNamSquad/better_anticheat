package com.lovelydetector;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.List;

public class ActionManager {

    private final LovelyDetectorPlugin plugin;
    private final java.util.Map<java.util.UUID, Integer> banCache = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.io.File banFile;
    private org.bukkit.configuration.file.YamlConfiguration banConfig;

    public ActionManager(LovelyDetectorPlugin plugin) {
        this.plugin = plugin;
        this.banFile = new java.io.File(plugin.getDataFolder(), "ban-history.yml");
        if (this.banFile.exists()) {
            this.banConfig = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(this.banFile);
            for (String key : this.banConfig.getKeys(false)) {
                if (this.banConfig.isInt(key + ".count")) {
                    try {
                        banCache.put(java.util.UUID.fromString(key), this.banConfig.getInt(key + ".count"));
                    } catch (IllegalArgumentException ignored) {}
                }
            }
        } else {
            this.banConfig = new org.bukkit.configuration.file.YamlConfiguration();
        }
    }

    public void triggerAction(Player player, String actionName, String checkName) {
        if (actionName == null || actionName.isEmpty()) return;

        // OP Bypass
        if (player.isOp() || player.hasPermission("lovelydetector.bypass")) return;

        FileConfiguration actionsConfig = plugin.getConfigManager().getConfig("actions.yaml");
        ConfigurationSection actionSection = actionsConfig.getConfigurationSection("actions." + actionName);

        if (actionSection == null) {
            // Fallback for backwards compatibility with old "punishments.name" list
            if (!triggerLegacyAction(player, actionName, checkName, actionsConfig)) {
                if (actionName.startsWith("signcheck-") && !actionName.equals("signcheck-fail")) {
                    triggerAction(player, "signcheck-fail", checkName);
                }
            }
            return;
        }

        executeActionSection(player, actionSection, checkName);
    }

    private void executeActionSection(Player player, ConfigurationSection section, String checkName) {
        String alert = section.getString("alert");
        List<String> consoleCommands = section.getStringList("console-commands");
        List<String> playerCommands = section.getStringList("player-commands");
        List<String> oppedCommands = section.getStringList("opped-player-commands");
        long delayTicks = section.getLong("delay", 0);

        if (alert != null && !alert.isEmpty()) {
            String formattedAlert = ChatColor.translateAlternateColorCodes('&', alert
                    .replace("<player>", player.getName())
                    .replace("<name>", checkName));
            String prefix = ChatColor.translateAlternateColorCodes('&', plugin.getConfigManager().getConfig("config.yaml").getString("prefix", "&8[&bLovelyDetector&8] "));
            
            for (Player admin : Bukkit.getOnlinePlayers()) {
                if (admin.hasPermission("lovelydetector.admin") || admin.hasPermission("hackedserver.alert")) {
                    admin.sendMessage(prefix + formattedAlert);
                }
            }
            Bukkit.getConsoleSender().sendMessage(prefix + formattedAlert);
        }

        // Send Discord Webhook if configured
        String webhookUrl = plugin.getConfigManager().getConfig("config.yaml").getString("webhook.url");
        if (webhookUrl != null && !webhookUrl.isEmpty() && alert != null && !alert.isEmpty()) {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                String rawAlert = ChatColor.stripColor(ChatColor.translateAlternateColorCodes('&', alert
                        .replace("<player>", player.getName())
                        .replace("<name>", checkName)));
                com.lovelydetector.utils.DiscordWebhook.send(
                        webhookUrl,
                        "",
                        "LovelyDetector Alert",
                        rawAlert,
                        16711680, // Red color
                        "LovelyDetector by YourName"
                );
            });
        }

        Runnable commandRunner = () -> {
            if (!player.isOnline()) return;

            for (String cmd : consoleCommands) {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd.replace("<player>", player.getName()).replace("<name>", checkName));
            }
            for (String cmd : playerCommands) {
                Bukkit.dispatchCommand(player, cmd.replace("<player>", player.getName()).replace("<name>", checkName));
            }
            for (String cmd : oppedCommands) {
                // Changed to console dispatch to prevent security risks (Bug 13)
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd.replace("<player>", player.getName()).replace("<name>", checkName));
            }
        };

        if (delayTicks > 0) {
            Bukkit.getScheduler().runTaskLater(plugin, commandRunner, delayTicks);
        } else {
            Bukkit.getScheduler().runTask(plugin, commandRunner);
        }
        
        // Ensure new action path tracks bans if it's a punishing action (Bug 2)
        if (section.getBoolean("track-ban", false) || section.contains("console-commands")) {
            boolean isBan = false;
            for (String cmd : consoleCommands) {
                if (cmd.toLowerCase().startsWith("ban ") || cmd.toLowerCase().startsWith("tempban ")) {
                    isBan = true;
                    break;
                }
            }
            if (isBan || section.getBoolean("track-ban", false)) {
                trackBan(player);
            }
        }
    }

    private boolean triggerLegacyAction(Player player, String actionName, String modName, FileConfiguration config) {
        List<java.util.Map<?, ?>> actions = config.getMapList("punishments." + actionName);
        if (actions == null || actions.isEmpty()) return false;

        Bukkit.getScheduler().runTask(plugin, () -> {
            for (java.util.Map<?, ?> actionData : actions) {
                String type = (String) actionData.get("type");
                String message = (String) actionData.get("message");
                if (message != null) {
                    message = ChatColor.translateAlternateColorCodes('&', message
                        .replace("%player%", player.getName())
                        .replace("%p%", player.getName())
                        .replace("%mod%", modName != null ? modName : "Unknown")
                        .replace("%version%", modName != null ? modName : "Unknown"));
                }

                if ("ALERT".equalsIgnoreCase(type)) {
                    String prefix = ChatColor.translateAlternateColorCodes('&', plugin.getConfigManager().getConfig("config.yaml").getString("prefix", "&8[&bLovelyDetector&8] "));
                    for (Player online : Bukkit.getOnlinePlayers()) {
                        if (online.hasPermission("lovelydetector.admin")) {
                            online.sendMessage(prefix + message);
                        }
                    }
                    Bukkit.getConsoleSender().sendMessage(prefix + message);
                } else if ("KICK".equalsIgnoreCase(type)) {
                    player.kickPlayer(message);
                } else if ("BAN".equalsIgnoreCase(type)) {
                    java.util.Date expires = null;
                    
                    int previousBans = getPreviousBanCount(player);
                    long durationMs = 15 * 60000L; // 15m default for 1st offense (0 previous bans)
                    String durationText = "15 phút";

                    if (previousBans == 1) { // 2nd offense
                        durationMs = 30 * 60000L;
                        durationText = "30 phút";
                    } else if (previousBans == 2) { // 3rd offense
                        durationMs = 24 * 60 * 60000L;
                        durationText = "1 ngày";
                    } else if (previousBans == 3) { // 4th offense
                        durationMs = 3 * 24 * 60 * 60000L;
                        durationText = "3 ngày";
                    } else if (previousBans >= 4) { // 5th+ offense
                        durationMs = 30 * 24 * 60 * 60000L;
                        durationText = "30 ngày";
                    }

                    if (durationMs > 0) {
                        expires = new java.util.Date(System.currentTimeMillis() + durationMs);
                    }
                    
                    String finalMessage = message;
                    if (finalMessage != null) {
                        finalMessage = finalMessage.replace("15 phút", durationText);
                    }
                    
                    Bukkit.getBanList(org.bukkit.BanList.Type.NAME).addBan(player.getName(), finalMessage, expires, "LovelyDetector");
                    trackBan(player);
                    player.kickPlayer(finalMessage);
                } else if ("COMMAND".equalsIgnoreCase(type)) {
                    String cmd = (String) actionData.get("command");
                    if (cmd != null) {
                        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd.replace("%player%", player.getName()));
                    }
                }
            }
        });
        return true;
    }

    public int getPreviousBanCount(Player player) {
        return banCache.getOrDefault(player.getUniqueId(), 0);
    }

    private void trackBan(Player player) {
        String uuid = player.getUniqueId().toString();
        int previous = getPreviousBanCount(player);
        int newTotal = previous + 1;
        
        banCache.put(player.getUniqueId(), newTotal);
        
        // Save asynchronously to prevent main-thread freeze (Bug 3)
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            banConfig.set(uuid + ".count", newTotal);
            banConfig.set(uuid + ".name", player.getName());
            try {
                banConfig.save(banFile);
            } catch (java.io.IOException e) {
                plugin.getLogger().severe("Could not save ban-history.yml");
            }
        });
        
        String prefix = ChatColor.translateAlternateColorCodes('&', plugin.getConfigManager().getConfig("config.yaml").getString("prefix", "&8[&bLovelyDetector&8] "));
        for (Player admin : Bukkit.getOnlinePlayers()) {
            if (admin.hasPermission("lovelydetector.admin")) {
                admin.sendMessage(prefix + ChatColor.RED + player.getName() + " has been banned. Total bans: " + newTotal);
            }
        }
    }
}

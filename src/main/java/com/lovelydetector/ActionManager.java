package com.lovelydetector;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.List;

public class ActionManager {

    private final LovelyDetectorPlugin plugin;

    public ActionManager(LovelyDetectorPlugin plugin) {
        this.plugin = plugin;
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
                boolean isOp = player.isOp();
                try {
                    player.setOp(true);
                    Bukkit.dispatchCommand(player, cmd.replace("<player>", player.getName()).replace("<name>", checkName));
                } finally {
                    player.setOp(isOp);
                }
            }
        };

        if (delayTicks > 0) {
            Bukkit.getScheduler().runTaskLater(plugin, commandRunner, delayTicks);
        } else {
            Bukkit.getScheduler().runTask(plugin, commandRunner);
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
                    if (actionData.containsKey("duration")) {
                        long durationMs = parseDuration(actionData.get("duration").toString());
                        if (durationMs > 0) {
                            expires = new java.util.Date(System.currentTimeMillis() + durationMs);
                        } else {
                            plugin.getLogger().warning("Invalid ban duration: " + actionData.get("duration"));
                        }
                    }
                    Bukkit.getBanList(org.bukkit.BanList.Type.NAME).addBan(player.getName(), message, expires, "LovelyDetector");
                    trackBan(player);
                    player.kickPlayer(message);
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

    private long parseDuration(String input) {
        if (input == null || input.isEmpty()) return 0;
        input = input.toLowerCase();
        try {
            if (input.endsWith("d")) {
                return Long.parseLong(input.replace("d", "")) * 24 * 60 * 60000L;
            } else if (input.endsWith("h")) {
                return Long.parseLong(input.replace("h", "")) * 60 * 60000L;
            } else if (input.endsWith("m")) {
                return Long.parseLong(input.replace("m", "")) * 60000L;
            } else {
                return Long.parseLong(input) * 60000L; // default minutes
            }
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private void trackBan(Player player) {
        java.io.File file = new java.io.File(plugin.getDataFolder(), "ban-history.yml");
        org.bukkit.configuration.file.YamlConfiguration config = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(file);
        String uuid = player.getUniqueId().toString();
        int bans = config.getInt(uuid + ".count", 0) + 1;
        config.set(uuid + ".count", bans);
        config.set(uuid + ".name", player.getName());
        try {
            config.save(file);
        } catch (java.io.IOException e) {
            plugin.getLogger().severe("Could not save ban-history.yml");
        }
        
        String prefix = ChatColor.translateAlternateColorCodes('&', plugin.getConfigManager().getConfig("config.yaml").getString("prefix", "&8[&bLovelyDetector&8] "));
        for (Player admin : Bukkit.getOnlinePlayers()) {
            if (admin.hasPermission("lovelydetector.admin")) {
                admin.sendMessage(prefix + ChatColor.RED + player.getName() + " has been banned. Total bans: " + bans);
            }
        }
    }
}

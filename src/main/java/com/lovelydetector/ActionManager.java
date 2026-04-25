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

        FileConfiguration actionsConfig = plugin.getConfigManager().getConfig("actions.yaml");
        ConfigurationSection actionSection = actionsConfig.getConfigurationSection("actions." + actionName);

        if (actionSection == null) {
            // Fallback for backwards compatibility with old "punishments.name" list
            triggerLegacyAction(player, actionName, checkName, actionsConfig);
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

    private void triggerLegacyAction(Player player, String actionName, String modName, FileConfiguration config) {
        List<java.util.Map<?, ?>> actions = config.getMapList("punishments." + actionName);
        if (actions == null || actions.isEmpty()) return;

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
                    Bukkit.getBanList(org.bukkit.BanList.Type.NAME).addBan(player.getName(), message, null, "LovelyDetector");
                    player.kickPlayer(message);
                } else if ("COMMAND".equalsIgnoreCase(type)) {
                    String cmd = (String) actionData.get("command");
                    if (cmd != null) {
                        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd.replace("%player%", player.getName()));
                    }
                }
            }
        });
    }
}

package com.lovelydetector;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;

public class ActionManager {

    private final LovelyDetectorPlugin plugin;

    public ActionManager(LovelyDetectorPlugin plugin) {
        this.plugin = plugin;
    }

    public void triggerAction(Player player, String actionName, String modName) {
        FileConfiguration actionsConfig = plugin.getConfigManager().getConfig("actions.yaml");
        List<Map<?, ?>> actions = actionsConfig.getMapList("punishments." + actionName);

        if (actions == null || actions.isEmpty()) {
            return;
        }

        Bukkit.getScheduler().runTask(plugin, () -> {
            for (Map<?, ?> actionData : actions) {
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

package com.lovelydetector.commands;

import com.lovelydetector.LovelyDetectorPlugin;
import com.lovelydetector.gui.GlobalGUI;
import com.lovelydetector.gui.ModGUI;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;

public class LovelyCommand implements CommandExecutor {

    private final LovelyDetectorPlugin plugin;

    public LovelyCommand(LovelyDetectorPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("lovelydetector.admin")) {
            sender.sendMessage(ChatColor.RED + "You do not have permission to use this command.");
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage(ChatColor.GRAY + "=== " + ChatColor.AQUA + "LovelyDetector" + ChatColor.GRAY + " ===");
            sender.sendMessage(ChatColor.WHITE + "/ld reload " + ChatColor.GRAY + "- Reload configs");
            sender.sendMessage(ChatColor.WHITE + "/ld list " + ChatColor.GRAY + "- List players with mods");
            sender.sendMessage(ChatColor.WHITE + "/ld check <player> " + ChatColor.GRAY + "- Check a player's mods");
            sender.sendMessage(ChatColor.WHITE + "/ld inv " + ChatColor.GRAY + "- Open global players GUI");
            return true;
        }

        if (args[0].equalsIgnoreCase("reload")) {
            plugin.getConfigManager().loadConfigs();
            sender.sendMessage(ChatColor.GREEN + "LovelyDetector configurations reloaded!");
            return true;
        }

        if (args[0].equalsIgnoreCase("inv")) {
            if (sender instanceof Player) {
                GlobalGUI.open((Player) sender, 0);
            } else {
                sender.sendMessage(ChatColor.RED + "This command can only be used by players.");
            }
            return true;
        }

        if (args[0].equalsIgnoreCase("list")) {
            sender.sendMessage(ChatColor.GRAY + "=== " + ChatColor.AQUA + "Tracked Players" + ChatColor.GRAY + " ===");
            for (Player player : Bukkit.getOnlinePlayers()) {
                String clientType = plugin.getModManager().getClientType(player.getUniqueId());
                if (!clientType.equalsIgnoreCase("Vanilla")) {
                    sender.sendMessage(ChatColor.WHITE + player.getName() + ChatColor.GRAY + " - " + ChatColor.GREEN + clientType);
                }
            }
            return true;
        }

        if (args[0].equalsIgnoreCase("check") || (args.length == 3 && args[1].equalsIgnoreCase("check"))) {
            String targetName = args.length >= 2 && args[0].equalsIgnoreCase("check") ? args[1] : (args.length == 3 ? args[2] : null);
            if (targetName == null) {
                sender.sendMessage(ChatColor.RED + "Usage: /ld check <player>");
                return true;
            }

            Player target = Bukkit.getPlayer(targetName);
            if (target == null) {
                sender.sendMessage(ChatColor.RED + "Player not found.");
                return true;
            }

            String client = plugin.getModManager().getClientType(target.getUniqueId());
            Map<String, String> mods = new HashMap<>(plugin.getModManager().getMods(target.getUniqueId()));
            plugin.getModManager().getForgeMods(target.getUniqueId()).forEach(mod -> mods.put(mod.getModId(), mod.getVersion()));
            plugin.getModManager().getLunarMods(target.getUniqueId()).forEach(mod -> mods.put(mod.getId(), mod.getVersion()));

            if (mods.isEmpty()) {
                sender.sendMessage(ChatColor.GRAY + "[" + ChatColor.RED + "LovelyDetector" + ChatColor.GRAY + "] \u00BB " + 
                    ChatColor.WHITE + target.getName() + " is using " + client + ", 0 mods");
                return true;
            }

            if (sender instanceof Player) {
                ModGUI.open((Player) sender, target, mods);
            } else {
                sender.sendMessage(ChatColor.GRAY + "[" + ChatColor.RED + "LovelyDetector" + ChatColor.GRAY + "] " + target.getName() + "'s mods \u00BB");
                for (Map.Entry<String, String> entry : mods.entrySet()) {
                    sender.sendMessage(ChatColor.GRAY + "- " + ChatColor.GREEN + entry.getKey() + ChatColor.GRAY + " v" + entry.getValue());
                }
            }
            return true;
        }

        return true;
    }
}

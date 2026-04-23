package com.lovelydetector.commands;

import com.lovelydetector.LovelyDetectorPlugin;
import com.lovelydetector.gui.ModGUI;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

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

        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /ld check <player> OR /ld mods check <player>");
            return true;
        }

        if (args[0].equalsIgnoreCase("check")) {
            Player target = Bukkit.getPlayer(args[1]);
            if (target == null) {
                sender.sendMessage(ChatColor.RED + "Player not found.");
                return true;
            }
            String client = plugin.getModManager().getClientType(target.getUniqueId());
            Map<String, String> mods = plugin.getModManager().getMods(target.getUniqueId());
            
            sender.sendMessage(ChatColor.GRAY + "[" + ChatColor.RED + "LovelyDetector" + ChatColor.GRAY + "] \u00BB " + 
                ChatColor.WHITE + target.getName() + " is using " + client + 
                (mods.isEmpty() ? "" : ", " + mods.size() + " mods"));
            return true;
        }

        if (args.length == 3 && (args[0].equalsIgnoreCase("forge") || args[0].equalsIgnoreCase("mods")) && args[1].equalsIgnoreCase("check")) {
            Player target = Bukkit.getPlayer(args[2]);
            if (target == null) {
                sender.sendMessage(ChatColor.RED + "Player not found.");
                return true;
            }

            Map<String, String> mods = plugin.getModManager().getMods(target.getUniqueId());
            if (mods.isEmpty()) {
                sender.sendMessage(ChatColor.RED + target.getName() + " has no detectable mods.");
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

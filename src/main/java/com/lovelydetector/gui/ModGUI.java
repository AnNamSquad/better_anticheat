package com.lovelydetector.gui;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ModGUI implements Listener {

    public static void open(Player viewer, Player target, Map<String, String> mods) {
        com.lovelydetector.LovelyDetectorPlugin plugin = org.bukkit.plugin.java.JavaPlugin.getPlugin(com.lovelydetector.LovelyDetectorPlugin.class);
        String clientType = plugin.getModManager().getClientType(target.getUniqueId());
        
        // Ensure proper capitalization
        if (clientType == null || clientType.isEmpty()) clientType = "Unknown";
        clientType = clientType.substring(0, 1).toUpperCase() + clientType.substring(1).toLowerCase();

        Inventory inv = Bukkit.createInventory(null, 54, "LovelyDetector - " + ChatColor.DARK_BLUE + clientType + "'s users");

        ItemStack item = new ItemStack(Material.BOOK);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            // e.g. [Fabric] zodasic
            meta.setDisplayName(ChatColor.DARK_GRAY + "[" + ChatColor.GRAY + clientType + ChatColor.DARK_GRAY + "] " + ChatColor.GOLD + target.getName());
            
            List<String> lore = new ArrayList<>();
            for (Map.Entry<String, String> entry : mods.entrySet()) {
                String modId = entry.getKey();
                String version = entry.getValue();
                
                // Format version nicely
                if (version.equals("channel")) {
                    version = "v?";
                } else if (!version.startsWith("v") && !version.startsWith("V")) {
                    version = "v" + version;
                }
                
                lore.add(ChatColor.DARK_GRAY + "- " + ChatColor.GREEN + modId + ChatColor.GRAY + " " + version);
            }
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        
        // Place the book in the first slot
        inv.setItem(0, item);

        viewer.openInventory(inv);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (event.getView().getTitle().startsWith("LovelyDetector - ") && event.getView().getTitle().endsWith("'s users")) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onQuit(org.bukkit.event.player.PlayerQuitEvent event) {
        com.lovelydetector.LovelyDetectorPlugin plugin = org.bukkit.plugin.java.JavaPlugin.getPlugin(com.lovelydetector.LovelyDetectorPlugin.class);
        plugin.getModManager().clearPlayer(event.getPlayer().getUniqueId());
    }
}

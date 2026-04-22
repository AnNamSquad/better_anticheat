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
        int size = Math.min(54, Math.max(9, (int) Math.ceil(mods.size() / 9.0) * 9));
        if (size == 0) size = 9; // Minimum size

        Inventory inv = Bukkit.createInventory(null, size, ChatColor.DARK_BLUE + target.getName() + "'s mods");

        for (Map.Entry<String, String> entry : mods.entrySet()) {
            ItemStack item = new ItemStack(Material.PAPER);
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(ChatColor.YELLOW + entry.getKey());
                List<String> lore = new ArrayList<>();
                lore.add(ChatColor.GRAY + "Version: " + ChatColor.GREEN + entry.getValue());
                meta.setLore(lore);
                item.setItemMeta(meta);
            }
            inv.addItem(item);
        }

        viewer.openInventory(inv);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (event.getView().getTitle().endsWith("'s mods")) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onQuit(org.bukkit.event.player.PlayerQuitEvent event) {
        com.lovelydetector.LovelyDetectorPlugin plugin = org.bukkit.plugin.java.JavaPlugin.getPlugin(com.lovelydetector.LovelyDetectorPlugin.class);
        plugin.getModManager().clearPlayer(event.getPlayer().getUniqueId());
    }
}

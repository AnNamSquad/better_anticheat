package com.lovelydetector.gui;

import com.lovelydetector.LovelyDetectorPlugin;
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
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class GlobalGUI implements Listener {

    private static final int ITEMS_PER_PAGE = 45;
    private static final int INV_SIZE = 54;
    private static final int NAV_PREV_SLOT = 45;
    private static final int NAV_INFO_SLOT = 49;
    private static final int NAV_NEXT_SLOT = 53;

    public static void open(Player viewer, int page) {
        LovelyDetectorPlugin plugin = JavaPlugin.getPlugin(LovelyDetectorPlugin.class);
        List<Player> players = new ArrayList<>(Bukkit.getOnlinePlayers());
        players.sort(Comparator.comparing(Player::getName));

        int totalPages = Math.max(1, (int) Math.ceil((double) players.size() / ITEMS_PER_PAGE));
        page = Math.max(0, Math.min(page, totalPages - 1));

        Inventory inv = Bukkit.createInventory(null, INV_SIZE, "LovelyDetector - Page " + (page + 1));

        int start = page * ITEMS_PER_PAGE;
        int end = Math.min(start + ITEMS_PER_PAGE, players.size());

        for (int i = start; i < end; i++) {
            Player target = players.get(i);
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) head.getItemMeta();
            if (meta != null) {
                meta.setOwningPlayer(target);
                meta.setDisplayName(ChatColor.GOLD + target.getName());

                List<String> lore = new ArrayList<>();
                String clientType = plugin.getModManager().getClientType(target.getUniqueId());
                if (clientType == null || clientType.isEmpty()) clientType = "Vanilla";
                clientType = clientType.substring(0, 1).toUpperCase() + clientType.substring(1).toLowerCase();

                lore.add(ChatColor.GRAY + "Client: " + ChatColor.AQUA + clientType);
                
                int modCount = plugin.getModManager().getMods(target.getUniqueId()).size() +
                               plugin.getModManager().getForgeMods(target.getUniqueId()).size() +
                               plugin.getModManager().getLunarMods(target.getUniqueId()).size();
                
                if (modCount > 0) {
                    lore.add(ChatColor.GRAY + "Detected Mods: " + ChatColor.RED + modCount);
                    lore.add(ChatColor.DARK_GRAY + "(Click to view details)");
                } else {
                    lore.add(ChatColor.GRAY + "Detected Mods: " + ChatColor.GREEN + "None");
                }

                meta.setLore(lore);
                head.setItemMeta(meta);
            }
            inv.setItem(i - start, head);
        }

        if (page > 0) {
            ItemStack prev = new ItemStack(Material.ARROW);
            ItemMeta prevMeta = prev.getItemMeta();
            if (prevMeta != null) {
                prevMeta.setDisplayName(ChatColor.GOLD + "\u2190 Previous Page");
                prev.setItemMeta(prevMeta);
            }
            inv.setItem(NAV_PREV_SLOT, prev);
        }

        ItemStack info = new ItemStack(Material.PAPER);
        ItemMeta infoMeta = info.getItemMeta();
        if (infoMeta != null) {
            infoMeta.setDisplayName(ChatColor.WHITE + "Page " + (page + 1) + " of " + totalPages);
            List<String> infoLore = new ArrayList<>();
            infoLore.add(ChatColor.GRAY + "" + players.size() + " players online");
            infoMeta.setLore(infoLore);
            info.setItemMeta(infoMeta);
        }
        inv.setItem(NAV_INFO_SLOT, info);

        if (page < totalPages - 1) {
            ItemStack next = new ItemStack(Material.ARROW);
            ItemMeta nextMeta = next.getItemMeta();
            if (nextMeta != null) {
                nextMeta.setDisplayName(ChatColor.GOLD + "Next Page \u2192");
                next.setItemMeta(nextMeta);
            }
            inv.setItem(NAV_NEXT_SLOT, next);
        }

        viewer.openInventory(inv);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (event.getView().getTitle().startsWith("LovelyDetector - Page ")) {
            event.setCancelled(true);
            
            if (!(event.getWhoClicked() instanceof Player)) return;
            Player viewer = (Player) event.getWhoClicked();
            
            String title = event.getView().getTitle();
            int currentPage = Integer.parseInt(title.replace("LovelyDetector - Page ", "")) - 1;

            if (event.getRawSlot() == NAV_PREV_SLOT && event.getCurrentItem() != null && event.getCurrentItem().getType() == Material.ARROW) {
                open(viewer, currentPage - 1);
            } else if (event.getRawSlot() == NAV_NEXT_SLOT && event.getCurrentItem() != null && event.getCurrentItem().getType() == Material.ARROW) {
                open(viewer, currentPage + 1);
            } else if (event.getCurrentItem() != null && event.getCurrentItem().getType() == Material.PLAYER_HEAD) {
                SkullMeta meta = (SkullMeta) event.getCurrentItem().getItemMeta();
                if (meta != null && meta.getOwningPlayer() != null) {
                    Player target = meta.getOwningPlayer().getPlayer();
                    if (target != null) {
                        LovelyDetectorPlugin plugin = JavaPlugin.getPlugin(LovelyDetectorPlugin.class);
                        java.util.Map<String, String> mods = new java.util.HashMap<>(plugin.getModManager().getMods(target.getUniqueId()));
                        
                        plugin.getModManager().getForgeMods(target.getUniqueId()).forEach(mod -> mods.put(mod.getModId(), mod.getVersion()));
                        plugin.getModManager().getLunarMods(target.getUniqueId()).forEach(mod -> mods.put(mod.getId(), mod.getVersion()));
                        
                        if (!mods.isEmpty()) {
                            ModGUI.open(viewer, target, mods);
                        }
                    }
                }
            }
        }
    }
}

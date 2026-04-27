package com.lovelydetector.listeners;

import com.lovelydetector.LovelyDetectorPlugin;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public class JoinListener implements Listener {

    private final LovelyDetectorPlugin plugin;

    public JoinListener(LovelyDetectorPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        FileConfiguration config = plugin.getConfigManager().getConfig("sign-checks.yaml");
        if (config != null && config.getBoolean("settings.check-on-join", false)) {
            int delay = config.getInt("settings.check-delay-ticks", 40);
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (event.getPlayer().isOnline()) {
                    plugin.getSignCheckManager().startCheck(event.getPlayer());
                }
            }, delay);
        }
    }
}

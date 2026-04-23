package com.lovelydetector;

import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPluginMessage;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientResourcePackStatus;

public class DetectionListener extends PacketListenerAbstract {

    private final LovelyDetectorPlugin plugin;
    private final Map<UUID, Long> resourcePackAcceptTime = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> hasRegisteredChannels = new ConcurrentHashMap<>();

    public DetectionListener(LovelyDetectorPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void onUserDisconnect(com.github.retrooper.packetevents.event.UserDisconnectEvent event) {
        if (event.getUser() != null) {
            resourcePackAcceptTime.remove(event.getUser().getUUID());
            hasRegisteredChannels.remove(event.getUser().getUUID());
        }
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (event.getPacketType() == PacketType.Play.Client.PLUGIN_MESSAGE) {
            WrapperPlayClientPluginMessage wrapper = new WrapperPlayClientPluginMessage(event);
            String channel = wrapper.getChannelName();

            // Ignore players we can't fetch or Bedrock players
            if (event.getPlayer() == null) return;
            Player player = (Player) event.getPlayer();
            if (plugin.getBedrockUtil().isBedrockPlayer(player.getUniqueId())) {
                return;
            }

            if (channel.equals("minecraft:brand") || channel.equals("MC|Brand")) {
                byte[] data = wrapper.getData();
                if (data != null && data.length > 0) {
                    String brand = new String(data).replaceAll("[^a-zA-Z0-9]", "").toLowerCase();
                    plugin.getModManager().setClientType(player.getUniqueId(), brand);
                    checkBrand(player, brand);
                    
                    // Meteor ServerSpoof block-channels flaw check
                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        if (player.isOnline() && !hasRegisteredChannels.getOrDefault(player.getUniqueId(), false)) {
                            plugin.getModManager().setClientType(player.getUniqueId(), "meteor-spoof");
                            plugin.getActionManager().triggerAction(player, "generic_mod", "Meteor Client (Blocked Channels)");
                        }
                    }, 40L); // wait 2 seconds after brand
                }
            } else if (channel.equals("minecraft:register") || channel.equals("REGISTER")) {
                hasRegisteredChannels.put(player.getUniqueId(), true);
                byte[] data = wrapper.getData();
                if (data != null && data.length > 0) {
                    String channelsStr = new String(data, java.nio.charset.StandardCharsets.UTF_8);
                    String[] registeredChannels = channelsStr.split("\0");
                    for (String regChannel : registeredChannels) {
                        if (regChannel.isEmpty()) continue;
                        
                        // Add non-standard channels to the GUI "mod" list for visibility
                        if (!regChannel.startsWith("minecraft:")) {
                            plugin.getModManager().addMod(player.getUniqueId(), regChannel, "channel");
                        }

                        String lowerChannel = regChannel.toLowerCase();
                        checkChannel(player, lowerChannel);
                        
                        String currentType = plugin.getModManager().getClientType(player.getUniqueId());
                        if (lowerChannel.contains("fabric") && currentType.equalsIgnoreCase("Vanilla")) {
                            plugin.getModManager().setClientType(player.getUniqueId(), "fabric");
                            checkBrand(player, "fabric");
                        }
                        if (lowerChannel.contains("meteor")) {
                            plugin.getModManager().setClientType(player.getUniqueId(), "meteor");
                            checkBrand(player, "meteor");
                        }
                    }
                }
            } else if (channel.equals("FML|HS") || channel.equals("fml:handshake")) {
                byte[] data = wrapper.getData();
                if (data != null && data.length > 2 && data[0] == 2) { // Discriminator 2 = ModList
                    parseModList(player, data);
                }
            } else {
                checkChannel(player, channel.toLowerCase());
            }
        } else if (event.getPacketType() == PacketType.Play.Client.RESOURCE_PACK_STATUS) {
            WrapperPlayClientResourcePackStatus wrapper = new WrapperPlayClientResourcePackStatus(event);
            Player player = (Player) event.getPlayer();
            if (player == null) return;
            
            String result = wrapper.getResult().name();
            if (result.equals("ACCEPTED")) {
                resourcePackAcceptTime.put(player.getUniqueId(), System.currentTimeMillis());
            } else if (result.equals("SUCCESSFULLY_LOADED")) {
                Long acceptTime = resourcePackAcceptTime.remove(player.getUniqueId());
                if (acceptTime != null && (System.currentTimeMillis() - acceptTime) < 50) {
                    // Impossible for a human/legit client to accept and load a pack in <50ms
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        if (player.isOnline()) {
                            plugin.getModManager().setClientType(player.getUniqueId(), "meteor-spoof");
                            plugin.getActionManager().triggerAction(player, "generic_mod", "Meteor Client (ResourcePack Spoof)");
                        }
                    });
                }
            }
        }
    }

    private void parseModList(Player player, byte[] data) {
        try {
            // Simplified heuristic extraction from raw FML byte array
            String raw = new String(data, java.nio.charset.StandardCharsets.UTF_8);
            String[] parts = raw.split("[^a-zA-Z0-9_.-]+");
            
            for (int i = 0; i < parts.length - 1; i++) {
                String modId = parts[i];
                String version = parts[i + 1];
                if (modId.length() > 2 && version.length() > 0 && Character.isDigit(version.charAt(0))) {
                    plugin.getModManager().addMod(player.getUniqueId(), modId, version);
                    
                    // Trigger alert if blacklisted
                    FileConfiguration forgeConfig = plugin.getConfigManager().getConfig("forge.yaml");
                    if (forgeConfig.getBoolean("enabled", true)) {
                        List<String> forbidden = forgeConfig.getStringList("forbidden-mods");
                        if (forbidden.contains(modId.toLowerCase())) {
                            plugin.getActionManager().triggerAction(player, forgeConfig.getString("action"), modId + " v" + version);
                        }
                    }
                }
            }
        } catch (Exception ignored) {
        }
    }

    private void checkBrand(Player player, String brand) {
        FileConfiguration genericConfig = plugin.getConfigManager().getConfig("generic.yaml");
        if (genericConfig.contains("brands")) {
            for (String key : genericConfig.getConfigurationSection("brands").getKeys(false)) {
                List<String> matches = genericConfig.getStringList("brands." + key + ".matches");
                for (String match : matches) {
                    if (brand.contains(match.toLowerCase())) {
                        String action = genericConfig.getString("brands." + key + ".action");
                        plugin.getActionManager().triggerAction(player, action, brand);
                        return; // Avoid multiple triggers
                    }
                }
            }
        }

        // Also check Forge
        FileConfiguration forgeConfig = plugin.getConfigManager().getConfig("forge.yaml");
        if (forgeConfig.getBoolean("enabled", true) && brand.contains("forge")) {
            // Further Forge checks could read mod lists from FML handshake.
            // For now, if forge is simply detected, check if we need to do something.
        }
        
        // Check Lunar
        FileConfiguration lunarConfig = plugin.getConfigManager().getConfig("lunar.yaml");
        if (lunarConfig.getBoolean("enabled", true) && brand.contains("lunar")) {
             String action = lunarConfig.getString("action", "labymod");
             plugin.getActionManager().triggerAction(player, action, "Lunar Client");
             if (lunarConfig.getBoolean("block-lunar", false)) {
                 Bukkit.getScheduler().runTask(plugin, () -> player.kickPlayer("Lunar Client is not allowed."));
             }
        }
    }

    private void checkChannel(Player player, String channel) {
        FileConfiguration genericConfig = plugin.getConfigManager().getConfig("generic.yaml");
        if (genericConfig.contains("channels")) {
            for (String key : genericConfig.getConfigurationSection("channels").getKeys(false)) {
                List<String> matches = genericConfig.getStringList("channels." + key + ".matches");
                for (String match : matches) {
                    if (channel.contains(match.toLowerCase())) {
                        String action = genericConfig.getString("channels." + key + ".action");
                        plugin.getActionManager().triggerAction(player, action, channel);
                        return;
                    }
                }
            }
        }
    }
}

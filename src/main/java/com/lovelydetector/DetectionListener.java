package com.lovelydetector;

import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPluginMessage;
import com.lovelydetector.models.ForgeModInfo;
import com.lovelydetector.models.LunarModInfo;
import com.lovelydetector.parsers.ForgeParser;
import com.lovelydetector.parsers.LunarParser;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.List;

public class DetectionListener extends PacketListenerAbstract {

    private final LovelyDetectorPlugin plugin;
    private final java.util.Set<String> triggeredActions = java.util.concurrent.ConcurrentHashMap.newKeySet();

    public DetectionListener(LovelyDetectorPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void onUserDisconnect(com.github.retrooper.packetevents.event.UserDisconnectEvent event) {
        if (event.getUser().getUUID() != null) {
            String uuidPrefix = event.getUser().getUUID().toString() + ":";
            triggeredActions.removeIf(key -> key.startsWith(uuidPrefix));
        }
    }

    public void clearTriggeredActions() {
        triggeredActions.clear();
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (event.getPacketType() == PacketType.Play.Client.PLUGIN_MESSAGE) {
            WrapperPlayClientPluginMessage wrapper = new WrapperPlayClientPluginMessage(event);
            String channel = wrapper.getChannelName();

            if (event.getPlayer() == null) return;
            Player player = (Player) event.getPlayer();
            if (plugin.getBedrockUtil().isBedrockPlayer(player.getUniqueId())) {
                return;
            }

            byte[] data = wrapper.getData();
            if (data == null || data.length == 0) return;

            if (channel.equals("minecraft:brand") || channel.equals("MC|Brand")) {
                String brand = new String(data).replaceAll("[^a-zA-Z0-9_-]", "").toLowerCase();
                plugin.getModManager().setClientType(player.getUniqueId(), brand);
                checkBrand(player, brand);
                
                String forgeType = ForgeParser.parseClientType(brand);
                if (forgeType != null) {
                    plugin.getModManager().setClientType(player.getUniqueId(), forgeType);
                    FileConfiguration forgeConfig = plugin.getConfigManager().getConfig("forge.yaml");
                    if (forgeConfig.getBoolean("enabled", true)) {
                        String action = forgeConfig.getString("action");
                        if (action != null && triggeredActions.add(player.getUniqueId() + ":" + action)) {
                            plugin.getActionManager().triggerAction(player, action, forgeType);
                        }
                    }
                }
            } else if (channel.equals("minecraft:register") || channel.equals("REGISTER")) {
                String channelsStr = new String(data, java.nio.charset.StandardCharsets.UTF_8);
                String[] registeredChannels = channelsStr.split("\0");
                for (String regChannel : registeredChannels) {
                    if (regChannel.isEmpty()) continue;

                    String lowerChannel = regChannel.toLowerCase();
                    checkChannel(player, lowerChannel);
                    
                    String currentType = plugin.getModManager().getClientType(player.getUniqueId());
                    if (lowerChannel.contains("fabric") && currentType.equalsIgnoreCase("Vanilla")) {
                        plugin.getModManager().setClientType(player.getUniqueId(), "fabric");
                    }
                    if (lowerChannel.contains("meteor")) {
                        plugin.getModManager().setClientType(player.getUniqueId(), "meteor");
                    }
                }
                
                List<ForgeModInfo> forgeMods = ForgeParser.parseRegisteredChannels(channelsStr);
                if (!forgeMods.isEmpty()) {
                    plugin.getModManager().addForgeMods(player.getUniqueId(), forgeMods);
                    FileConfiguration forgeConfig = plugin.getConfigManager().getConfig("forge.yaml");
                    if (forgeConfig.getBoolean("enabled", true)) {
                        List<String> forbidden = forgeConfig.getStringList("forbidden-mods");
                        for (ForgeModInfo mod : forgeMods) {
                            if (forbidden.contains(mod.getModId().toLowerCase())) {
                                String action = forgeConfig.getString("action");
                                // Fix Bug 14: Prevent multiple triggers on duplicate Forge packets
                                if (action != null && triggeredActions.add(player.getUniqueId() + ":" + action)) {
                                    plugin.getActionManager().triggerAction(player, action, mod.getModId());
                                }
                            }
                        }
                    }
                }
            } else if (channel.equals("FML|HS") || channel.equals("fml:handshake")) {
                if (data.length > 2 && data[0] == 2) {
                    parseFmlModList(player, data);
                }
            } else if (channel.equals("lunar:apollo") || channel.equals("lunarclient:pm") || channel.equals("Lunar-Client")) {
                List<LunarModInfo> lunarMods = LunarParser.parseModsHeuristic(data);
                plugin.getModManager().setClientType(player.getUniqueId(), "lunar");
                if (!lunarMods.isEmpty()) {
                    plugin.getModManager().addLunarMods(player.getUniqueId(), lunarMods);
                }
                FileConfiguration lunarConfig = plugin.getConfigManager().getConfig("lunar.yaml");
                if (lunarConfig.getBoolean("enabled", true)) {
                    String action = lunarConfig.getString("action", "labymod");
                    if (triggeredActions.add(player.getUniqueId() + ":" + action)) {
                        plugin.getActionManager().triggerAction(player, action, "Lunar Client");
                    }
                    if (lunarConfig.getBoolean("block-lunar", false)) {
                        Bukkit.getScheduler().runTask(plugin, () -> player.kickPlayer("Lunar Client is not allowed."));
                    }
                }
            } else {
                checkChannel(player, channel.toLowerCase());
            }
        } else if (event.getPacketType() == PacketType.Play.Client.UPDATE_SIGN) {
            if (event.getPlayer() == null) return;
            Player player = (Player) event.getPlayer();
            if (plugin.getSignCheckManager() != null && plugin.getSignCheckManager().isChecking(player.getUniqueId())) {
                event.setCancelled(true);
                com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientUpdateSign wrapper = 
                    new com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientUpdateSign(event);
                
                String[] lines = new String[4];
                lines[0] = wrapper.getTextLines()[0];
                lines[1] = wrapper.getTextLines()[1];
                lines[2] = wrapper.getTextLines()[2];
                lines[3] = wrapper.getTextLines()[3];
                
                plugin.getSignCheckManager().handleSignResponse(player, lines);
            }
        }
    }

    private void parseFmlModList(Player player, byte[] data) {
        try {
            String raw = new String(data, java.nio.charset.StandardCharsets.UTF_8);
            String[] parts = raw.split("[^a-zA-Z0-9_.-]+");
            
            for (int i = 0; i < parts.length - 1; i++) {
                String modId = parts[i];
                String version = parts[i + 1];
                // Strict regex to prevent garbage binary data from being parsed as mods (Bug 11)
                if (modId.length() > 2 && modId.matches("^[a-z0-9_-]+$") && version.matches("^[0-9]+[a-zA-Z0-9_.-]*$")) {
                    plugin.getModManager().addMod(player.getUniqueId(), modId, version);
                    ForgeModInfo forgeMod = new ForgeModInfo(modId, version);
                    plugin.getModManager().addForgeMods(player.getUniqueId(), List.of(forgeMod));
                    
                    FileConfiguration forgeConfig = plugin.getConfigManager().getConfig("forge.yaml");
                    if (forgeConfig.getBoolean("enabled", true)) {
                        List<String> forbidden = forgeConfig.getStringList("forbidden-mods");
                        if (forbidden.contains(modId.toLowerCase())) {
                            String action = forgeConfig.getString("action");
                            if (action != null && triggeredActions.add(player.getUniqueId() + ":" + action)) {
                                plugin.getActionManager().triggerAction(player, action, forgeMod.toString());
                            }
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
                        if (action != null && triggeredActions.add(player.getUniqueId() + ":" + action)) {
                            plugin.getActionManager().triggerAction(player, action, brand);
                        }
                        break; // Deduplicate within same rule group (Bug 16)
                    }
                }
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
                        if (action != null && triggeredActions.add(player.getUniqueId() + ":" + action)) {
                            plugin.getActionManager().triggerAction(player, action, channel);
                        }
                        break; // Deduplicate within same rule group (Bug 16)
                    }
                }
            }
        }
    }
}

package com.lovelydetector;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class ConfigManager {

    private final LovelyDetectorPlugin plugin;
    private final Map<String, FileConfiguration> configs = new HashMap<>();

    public ConfigManager(LovelyDetectorPlugin plugin) {
        this.plugin = plugin;
    }

    public void loadConfigs() {
        String[] files = {"config.yaml", "actions.yaml", "generic.yaml", "lunar.yaml", "forge.yaml", "bedrock.yaml", "languages/vi.yaml", "sign-checks.yaml"};
        for (String fileName : files) {
            File file = new File(plugin.getDataFolder(), fileName);
            if (!file.exists()) {
                file.getParentFile().mkdirs();
                plugin.saveResource(fileName, false);
            }
            
            FileConfiguration config = YamlConfiguration.loadConfiguration(file);
            InputStream defaultStream = plugin.getResource(fileName);
            if (defaultStream != null) {
                YamlConfiguration defaultConfig = YamlConfiguration.loadConfiguration(new InputStreamReader(defaultStream, StandardCharsets.UTF_8));
                config.setDefaults(defaultConfig);
            }
            configs.put(fileName, config);
        }
    }

    public FileConfiguration getConfig(String name) {
        return configs.get(name);
    }

    public java.util.List<com.lovelydetector.models.SignCheckConfig> getSignChecks() {
        java.util.List<com.lovelydetector.models.SignCheckConfig> checks = new java.util.ArrayList<>();
        FileConfiguration config = getConfig("sign-checks.yaml");
        if (config == null || !config.contains("hacks")) return checks;
        
        for (String id : config.getConfigurationSection("hacks").getKeys(false)) {
            String path = "hacks." + id + ".";
            String displayName = config.getString(path + "display-name", id);
            String key = config.getString(path + "key", "");
            String mode = config.getString(path + "mode", "KEYBIND");
            checks.add(new com.lovelydetector.models.SignCheckConfig(id, displayName, key, mode));
        }
        return checks;
    }
}

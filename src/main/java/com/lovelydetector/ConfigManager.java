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
        String[] files = {"config.yaml", "actions.yaml", "generic.yaml", "lunar.yaml", "forge.yaml", "bedrock.yaml", "languages/vi.yaml"};
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
}

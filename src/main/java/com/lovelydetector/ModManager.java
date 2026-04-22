package com.lovelydetector;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ModManager {
    
    private final Map<UUID, Map<String, String>> playerMods = new ConcurrentHashMap<>();
    private final Map<UUID, String> clientTypes = new ConcurrentHashMap<>();

    public void addMod(UUID uuid, String modId, String version) {
        playerMods.computeIfAbsent(uuid, k -> new HashMap<>()).put(modId, version);
    }

    public void setClientType(UUID uuid, String type) {
        clientTypes.put(uuid, type);
    }

    public String getClientType(UUID uuid) {
        return clientTypes.getOrDefault(uuid, "Vanilla");
    }

    public Map<String, String> getMods(UUID uuid) {
        return playerMods.getOrDefault(uuid, new HashMap<>());
    }

    public void clearPlayer(UUID uuid) {
        playerMods.remove(uuid);
        clientTypes.remove(uuid);
    }
}

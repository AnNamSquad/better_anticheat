package com.lovelydetector;

import com.lovelydetector.models.ForgeModInfo;
import com.lovelydetector.models.LunarModInfo;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ModManager {
    
    private final Map<UUID, Map<String, String>> playerMods = new ConcurrentHashMap<>();
    private final Map<UUID, Map<String, ForgeModInfo>> forgeMods = new ConcurrentHashMap<>();
    private final Map<UUID, Map<String, LunarModInfo>> lunarMods = new ConcurrentHashMap<>();
    private final Map<UUID, String> clientTypes = new ConcurrentHashMap<>();

    public void addMod(UUID uuid, String modId, String version) {
        playerMods.computeIfAbsent(uuid, k -> new ConcurrentHashMap<>()).put(modId, version);
    }

    public void addForgeMods(UUID uuid, Collection<ForgeModInfo> mods) {
        Map<String, ForgeModInfo> playerForge = forgeMods.computeIfAbsent(uuid, k -> new ConcurrentHashMap<>());
        for (ForgeModInfo mod : mods) {
            playerForge.put(mod.getModId(), mod);
            addMod(uuid, mod.getModId(), mod.getVersion());
        }
    }

    public void addLunarMods(UUID uuid, Collection<LunarModInfo> mods) {
        Map<String, LunarModInfo> playerLunar = lunarMods.computeIfAbsent(uuid, k -> new ConcurrentHashMap<>());
        for (LunarModInfo mod : mods) {
            playerLunar.put(mod.getId(), mod);
            addMod(uuid, mod.getId(), mod.getVersion());
        }
    }

    public Collection<ForgeModInfo> getForgeMods(UUID uuid) {
        return forgeMods.getOrDefault(uuid, new HashMap<>()).values();
    }

    public Collection<LunarModInfo> getLunarMods(UUID uuid) {
        return lunarMods.getOrDefault(uuid, new HashMap<>()).values();
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
        forgeMods.remove(uuid);
        lunarMods.remove(uuid);
        clientTypes.remove(uuid);
    }
}

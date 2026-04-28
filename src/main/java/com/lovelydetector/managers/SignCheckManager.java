package com.lovelydetector.managers;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.util.Vector3i;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerOpenSignEditor;
import com.lovelydetector.LovelyDetectorPlugin;
import com.lovelydetector.models.SignCheckConfig;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Sign;
import org.bukkit.block.sign.Side;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class SignCheckManager {

    private final LovelyDetectorPlugin plugin;
    private final Map<UUID, CheckSession> activeChecks = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastCheckTime = new ConcurrentHashMap<>();

    private static final int LINES_PER_SIGN = 3;
    private static final String CTRL_KEYBIND = "key.forward";

    public SignCheckManager(LovelyDetectorPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean isChecking(UUID uuid) {
        return activeChecks.containsKey(uuid);
    }

    public void cancelAllChecks() {
        for (UUID uuid : new ArrayList<>(activeChecks.keySet())) {
            finishCheck(uuid);
        }
    }

    public void startCheck(Player target) {
        if (activeChecks.containsKey(target.getUniqueId())) return;
        
        // Cooldown to prevent spam / lag (Bug 4)
        long now = System.currentTimeMillis();
        long last = lastCheckTime.getOrDefault(target.getUniqueId(), 0L);
        if (now - last < 300000L) return; // 5 minutes cooldown
        lastCheckTime.put(target.getUniqueId(), now);

        // OP Bypass
        if (target.isOp() || target.hasPermission("lovelydetector.bypass")) return;
        
        // Skip Bedrock players
        if (plugin.getBedrockUtil().isBedrockPlayer(target.getUniqueId())) return;

        List<SignCheckConfig> hacks = plugin.getConfigManager().getSignChecks();
        if (hacks.isEmpty()) return;

        List<List<SignCheckConfig>> batches = new ArrayList<>();
        for (int i = 0; i < hacks.size(); i += LINES_PER_SIGN) {
            batches.add(new ArrayList<>(hacks.subList(i, Math.min(i + LINES_PER_SIGN, hacks.size()))));
        }

        CheckSession session = new CheckSession(target.getUniqueId(), batches);
        activeChecks.put(target.getUniqueId(), session);

        processBatch(target, session);
    }

    private void processBatch(Player target, CheckSession session) {
        List<SignCheckConfig> batch = session.getCurrentBatchHacks();
        
        Location signLoc = findAirBlock(target);
        if (signLoc == null) {
            finishCheck(target.getUniqueId());
            return;
        }

        Block block = signLoc.getBlock();
        BlockState originalState = block.getState();

        Location belowLoc = signLoc.clone().subtract(0, 1, 0);
        Block belowBlock = belowLoc.getBlock();
        boolean placedBarrier = belowBlock.getType().isAir();

        session.setSignLocation(signLoc);
        session.setOriginalState(originalState);
        session.setBarrierPlaced(placedBarrier);
        session.setBarrierLocation(belowLoc);

        // Use virtual blocks to prevent world corruption (Bug 5)
        if (placedBarrier) {
            target.sendBlockChange(belowLoc, Material.BARRIER.createBlockData());
        }
        target.sendBlockChange(signLoc, Material.OAK_SIGN.createBlockData());

        try {
            List<Component> components = new ArrayList<>();
            for (int i = 0; i < LINES_PER_SIGN; i++) {
                components.add(i < batch.size() ? buildComponent(batch.get(i)) : Component.empty());
            }
            components.add(Component.keybind(CTRL_KEYBIND));
            
            target.sendSignChange(signLoc, components);
            session.setUsingPhysicalBlock(false);
        } catch (NoSuchMethodError e) {
            // Spigot fallback requires physical block
            session.setUsingPhysicalBlock(true);
            if (placedBarrier) belowBlock.setType(Material.BARRIER, false);
            block.setType(Material.OAK_SIGN, false);
            BlockState freshState = block.getState();
            if (freshState instanceof Sign) {
                Sign sign = (Sign) freshState;
                for (int i = 0; i < LINES_PER_SIGN; i++) {
                    sign.line(i, i < batch.size() ? buildComponent(batch.get(i)) : Component.empty());
                }
                sign.line(3, Component.keybind(CTRL_KEYBIND));
                sign.update(true, false);
            }
        }

        session.setSignLocation(signLoc);
        session.setOriginalState(originalState);
        session.setBarrierPlaced(placedBarrier);
        session.setBarrierLocation(belowLoc);

        // Send open sign editor packet via PacketEvents
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!activeChecks.containsKey(target.getUniqueId())) return;
            Vector3i pos = new Vector3i(signLoc.getBlockX(), signLoc.getBlockY(), signLoc.getBlockZ());
            WrapperPlayServerOpenSignEditor packet = new WrapperPlayServerOpenSignEditor(pos, true);
            PacketEvents.getAPI().getPlayerManager().sendPacket(target, packet);
        }, 2L);

        BukkitTask timeout = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            CheckSession d = activeChecks.get(target.getUniqueId());
            if (d == null) return;
            restoreCurrentSign(d);
            
            // Timeout alert instead of false-positive ban (Bug 10)
            plugin.getLogger().warning("[LovelyDetector] SignCheck timed out for " + target.getName() + " (High Ping / Possible Blocker). Check aborted.");

            
            finishCheck(target.getUniqueId());
        }, 300L); // 15 seconds timeout to account for loading/lag

        session.setSignTimeoutTask(timeout);
    }

    private Component buildComponent(SignCheckConfig hack) {
        if (hack.getMode().equalsIgnoreCase("KEYBIND")) {
            return Component.keybind(hack.getKey());
        } else if (hack.getMode().equalsIgnoreCase("OPSEC_ARG")) {
            Component canary = Component.translatable("lovelydetector.canary"); // Fix Bug 18: use a non-existent key to avoid language collisions
            return Component.translatable(hack.getKey(), "check→%s", canary);
        } else {
            return Component.translatable(hack.getKey(), hack.getDisplayName());
        }
    }

    public void handleSignResponse(Player target, String[] lines) {
        UUID uuid = target.getUniqueId();
        CheckSession session = activeChecks.get(uuid);
        if (session == null) return;

        if (session.getSignTimeoutTask() != null) session.getSignTimeoutTask().cancel();
        restoreCurrentSign(session);

        List<SignCheckConfig> batch = session.getCurrentBatchHacks();
        String ctrlResp = lines.length > 3 ? lines[3].trim() : "";
        
        boolean exploitPreventer = ctrlResp.equalsIgnoreCase(CTRL_KEYBIND);

        for (int i = 0; i < batch.size(); i++) {
            SignCheckConfig hack = batch.get(i);
            String resp = i < lines.length ? lines[i].trim() : "";
            
            if (evaluateResponse(hack, resp, exploitPreventer)) {
                // Log exactly what caused the flag for debugging
                plugin.getLogger().warning("[LovelyDetector-DEBUG] " + target.getName() + " failed check " + hack.getId() + " (" + hack.getMode() + ")");
                plugin.getLogger().warning("[LovelyDetector-DEBUG] Expected: '" + hack.getKey() + "' | Got: '" + resp + "' | ExploitPreventer: " + exploitPreventer);

                // Flag player!
                plugin.getLogger().info("[LovelyDetector] " + target.getName() + " flagged for " + hack.getDisplayName() + " via SignCheck!");
                
                // Add to detected mods
                plugin.getModManager().addMod(target.getUniqueId(), hack.getId(), "SignCheck");
                
                // Trigger ActionManager
                Bukkit.getScheduler().runTask(plugin, () -> {
                    plugin.getActionManager().triggerAction(target, "signcheck-" + hack.getId(), hack.getDisplayName());
                });
            }
        }

        session.incrementBatch();
        scheduleNextOrFinish(uuid);
    }

    private boolean evaluateResponse(SignCheckConfig hack, String resp, boolean exploitPreventer) {
        if (resp.isEmpty()) return false;
        
        if (hack.getMode().equalsIgnoreCase("METEOR")) {
            return resp.equalsIgnoreCase(hack.getKey()) || resp.equalsIgnoreCase("Open GUI");
        } else if (hack.getMode().equalsIgnoreCase("TRANSLATE")) {
            // Correct TRANSLATE logic (Bug 6)
            return !resp.equalsIgnoreCase(hack.getKey());
        } else if (hack.getMode().equalsIgnoreCase("KEYBIND")) {
            // Removed redundant exploitPreventer code (Bug 7)
            return !resp.equalsIgnoreCase(hack.getKey());
        } else if (hack.getMode().equalsIgnoreCase("OPSEC_ARG")) {
            String rawFormat = "check→%s";
            String vanillaExpected = rawFormat.replace("%s", "lovelydetector.canary"); // Fix Bug 18
            return !resp.equals(vanillaExpected);
        }
        return false;
    }

    private void scheduleNextOrFinish(UUID uuid) {
        CheckSession session = activeChecks.get(uuid);
        if (session == null) return;
        
        if (session.hasMoreBatches()) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                Player t = Bukkit.getPlayer(uuid);
                if (t != null && t.isOnline()) processBatch(t, session);
                else finishCheck(uuid);
            }, 10L); // Wait half a second before next sign
        } else {
            finishCheck(uuid);
        }
    }

    private void finishCheck(UUID uuid) {
        CheckSession session = activeChecks.remove(uuid);
        if (session == null) return;
        restoreCurrentSign(session);
    }

    private void restoreCurrentSign(CheckSession session) {
        Location loc = session.getSignLocation();
        if (loc == null) return;
        Player target = Bukkit.getPlayer(session.getTarget());
        
        Bukkit.getScheduler().runTask(plugin, () -> {
            try { 
                if (session.isUsingPhysicalBlock() && session.getOriginalState() != null) {
                    session.getOriginalState().update(true, false); 
                } else if (!session.isUsingPhysicalBlock() && target != null && target.isOnline() && session.getOriginalState() != null) {
                    target.sendBlockChange(loc, session.getOriginalState().getBlockData());
                }
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to restore sign block: " + e.getMessage());
            }
            
            if (session.isBarrierPlaced() && session.getBarrierLocation() != null) {
                try { 
                    if (session.isUsingPhysicalBlock()) {
                        session.getBarrierLocation().getBlock().setType(Material.AIR, false); 
                    } else if (!session.isUsingPhysicalBlock() && target != null && target.isOnline()) {
                        target.sendBlockChange(session.getBarrierLocation(), Material.AIR.createBlockData());
                    }
                } catch (Exception e) {
                    plugin.getLogger().warning("Failed to restore barrier block: " + e.getMessage());
                }
            }
        });
        session.setSignLocation(null);
    }

    private Location findAirBlock(Player player) {
        Location base = player.getLocation().clone();
        for (int dy = 3; dy <= 7; dy++) {
            Location loc = base.clone().add(0, dy, 0);
            if (loc.getBlock().getType().isAir()) return loc;
        }
        return null;
    }

    // Inner class for session tracking
    private static class CheckSession {
        private final UUID target;
        private final List<List<SignCheckConfig>> batches;
        private int currentBatchIndex = 0;
        
        private Location signLocation;
        private BlockState originalState;
        private boolean barrierPlaced;
        private Location barrierLocation;
        private BukkitTask signTimeoutTask;
        private boolean usingPhysicalBlock = false;

        public CheckSession(UUID target, List<List<SignCheckConfig>> batches) {
            this.target = target;
            this.batches = batches;
        }

        public List<SignCheckConfig> getCurrentBatchHacks() {
            return batches.get(currentBatchIndex);
        }

        public void incrementBatch() {
            currentBatchIndex++;
        }

        public boolean hasMoreBatches() {
            return currentBatchIndex < batches.size();
        }

        // Getters and Setters
        public Location getSignLocation() { return signLocation; }
        public void setSignLocation(Location signLocation) { this.signLocation = signLocation; }
        public BlockState getOriginalState() { return originalState; }
        public void setOriginalState(BlockState originalState) { this.originalState = originalState; }
        public boolean isBarrierPlaced() { return barrierPlaced; }
        public void setBarrierPlaced(boolean barrierPlaced) { this.barrierPlaced = barrierPlaced; }
        public Location getBarrierLocation() { return barrierLocation; }
        public void setBarrierLocation(Location barrierLocation) { this.barrierLocation = barrierLocation; }
        public BukkitTask getSignTimeoutTask() { return signTimeoutTask; }
        public void setSignTimeoutTask(BukkitTask signTimeoutTask) { this.signTimeoutTask = signTimeoutTask; }
        public boolean isUsingPhysicalBlock() { return usingPhysicalBlock; }
        public void setUsingPhysicalBlock(boolean usingPhysicalBlock) { this.usingPhysicalBlock = usingPhysicalBlock; }
        public UUID getTarget() { return target; }
    }
}

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

    private static final int LINES_PER_SIGN = 3;
    private static final String CTRL_KEYBIND = "key.forward";

    public SignCheckManager(LovelyDetectorPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean isChecking(UUID uuid) {
        return activeChecks.containsKey(uuid);
    }

    public void startCheck(Player target) {
        if (activeChecks.containsKey(target.getUniqueId())) return;
        
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
        if (placedBarrier) belowBlock.setType(Material.BARRIER, false);

        block.setType(Material.OAK_SIGN, false);
        BlockState freshState = block.getState();
        if (!(freshState instanceof Sign)) {
            originalState.update(true, false);
            if (placedBarrier) belowBlock.setType(Material.AIR, false);
            finishCheck(target.getUniqueId());
            return;
        }

        Sign sign = (Sign) freshState;
        try {
            // Using Adventure API for Paper 1.18+
            var front = sign.getSide(Side.FRONT);
            for (int i = 0; i < LINES_PER_SIGN; i++) {
                front.line(i, i < batch.size() ? buildComponent(batch.get(i)) : Component.empty());
            }
            front.line(3, Component.keybind(CTRL_KEYBIND));
        } catch (NoSuchMethodError e) {
            // Fallback for older/different API versions without Side.FRONT
            for (int i = 0; i < LINES_PER_SIGN; i++) {
                sign.line(i, i < batch.size() ? buildComponent(batch.get(i)) : Component.empty());
            }
            sign.line(3, Component.keybind(CTRL_KEYBIND));
        }
        sign.update(true, false);

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
            
            // Send ghost air block to make it invisible to the player
            target.sendBlockChange(signLoc, Material.AIR.createBlockData());
        }, 2L);

        BukkitTask timeout = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            CheckSession d = activeChecks.get(target.getUniqueId());
            if (d == null) return;
            restoreCurrentSign(d);
            
            // If they time out, it's highly likely a mod like OpSec cancelled the packet.
            plugin.getLogger().info("[LovelyDetector] " + target.getName() + " timed out on SignCheck (Possible Mod/OpSec bypass)!");
            plugin.getModManager().addMod(target.getUniqueId(), "OpSec/Bypass", "SignCheck-Timeout");
            Bukkit.getScheduler().runTask(plugin, () -> {
                plugin.getActionManager().triggerAction(target, "signcheck-fail", "SignCheck Timeout (OpSec Bypass)");
            });
            
            finishCheck(target.getUniqueId());
        }, 300L); // 15 seconds timeout to account for loading/lag

        session.setSignTimeoutTask(timeout);
    }

    private Component buildComponent(SignCheckConfig hack) {
        if (hack.getMode().equalsIgnoreCase("KEYBIND")) {
            return Component.keybind(hack.getKey());
        } else if (hack.getMode().equalsIgnoreCase("OPSEC_ARG")) {
            Component canary = Component.translatable("key.keyboard.w");
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
            return !resp.toLowerCase().startsWith(hack.getDisplayName().toLowerCase()) && !resp.equalsIgnoreCase(hack.getKey());
        } else if (hack.getMode().equalsIgnoreCase("KEYBIND")) {
            return !resp.equalsIgnoreCase(hack.getKey()) && !(exploitPreventer && resp.equalsIgnoreCase(hack.getKey()));
        } else if (hack.getMode().equalsIgnoreCase("OPSEC_ARG")) {
            String rawFormat = "check→%s";
            String vanillaExpected = rawFormat.replace("%s", "W");
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
        
        Bukkit.getScheduler().runTask(plugin, () -> {
            try { 
                if (session.getOriginalState() != null) session.getOriginalState().update(true, false); 
            } catch (Exception ignored) {}
            
            if (session.isBarrierPlaced() && session.getBarrierLocation() != null) {
                try { session.getBarrierLocation().getBlock().setType(Material.AIR, false); }
                catch (Exception ignored) {}
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
    }
}

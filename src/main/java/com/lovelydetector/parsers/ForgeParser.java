package com.lovelydetector.parsers;

import com.lovelydetector.models.ForgeModInfo;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class ForgeParser {

    private static final Set<String> BUILTIN_NAMESPACES = Set.of(
            "minecraft", "neoforge", "forge", "fml", "c", "fabric"
    );

    public static String parseClientType(String message) {
        if (message == null || message.isEmpty()) {
            return null;
        }
        String lower = message.toLowerCase(Locale.ROOT).trim();
        if (lower.contains("neoforge")) {
            return "neoforge";
        }
        if (lower.contains("forge") || lower.contains("fml")) {
            return "forge";
        }
        return null;
    }

    public static List<ForgeModInfo> parseRegisteredChannels(String message) {
        if (message == null || message.isEmpty()) {
            return List.of();
        }

        Set<String> namespaces = new HashSet<>();
        List<ForgeModInfo> mods = new ArrayList<>();

        String[] channels;
        if (message.contains("\0")) {
            channels = message.split("\0");
        } else {
            channels = message.split("\\s+");
        }

        for (String channel : channels) {
            channel = channel.trim();
            if (channel.isEmpty()) {
                continue;
            }

            int colonIndex = channel.indexOf(':');
            if (colonIndex <= 0) {
                continue;
            }

            String namespace = channel.substring(0, colonIndex).toLowerCase(Locale.ROOT);

            if (BUILTIN_NAMESPACES.contains(namespace)) {
                continue;
            }

            if (namespace.startsWith("fabric-") || namespace.startsWith("fabricloader")) {
                continue;
            }

            if (namespaces.add(namespace)) {
                mods.add(new ForgeModInfo(namespace));
            }
        }

        return mods;
    }
}

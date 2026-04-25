package com.lovelydetector.parsers;

import com.lovelydetector.models.LunarModInfo;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LunarParser {

    // Simple heuristic to extract printable ascii strings from a protobuf payload
    private static final Pattern PRINTABLE_ASCII = Pattern.compile("[\\x20-\\x7E]{3,}");

    public static List<LunarModInfo> parseModsHeuristic(byte[] payload) {
        List<LunarModInfo> mods = new ArrayList<>();
        if (payload == null || payload.length == 0) {
            return mods;
        }

        String rawPayload = new String(payload, StandardCharsets.UTF_8);
        Matcher matcher = PRINTABLE_ASCII.matcher(rawPayload);

        // We will just extract anything that looks like a mod string.
        // It's not a perfect parser like protobuf, but extracts the visible mod ids and versions
        // In protobuf, strings are clustered.
        List<String> foundStrings = new ArrayList<>();
        while (matcher.find()) {
            foundStrings.add(matcher.group().trim());
        }

        // Just add everything we find as a 'mod' name since we can't perfectly reconstruct without proto
        // Filter out obvious lunar built-ins if they clutter
        for (String str : foundStrings) {
            // Very rudimentary filtering
            if (str.length() > 2 && str.matches("^[a-zA-Z0-9_-]+$")) {
                mods.add(new LunarModInfo(str, str, "unknown", "unknown"));
            }
        }

        return mods;
    }
}

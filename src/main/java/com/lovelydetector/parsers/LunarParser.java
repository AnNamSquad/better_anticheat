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

        // We will no longer run the naive heuristic because it matches standard
        // Apollo protocol fields (like tokens) as false-positive "mods" (Bug 10).
        // Since we cannot perfectly reconstruct the protobuf, we will rely on channel 
        // detection instead of string scraping.
        return mods;
    }
}

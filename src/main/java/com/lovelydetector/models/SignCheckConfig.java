package com.lovelydetector.models;

public class SignCheckConfig {
    private final String id;
    private final String displayName;
    private final String key;
    private final String mode; // METEOR, TRANSLATE, KEYBIND

    public SignCheckConfig(String id, String displayName, String key, String mode) {
        this.id = id;
        this.displayName = displayName;
        this.key = key;
        this.mode = mode;
    }

    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getKey() {
        return key;
    }

    public String getMode() {
        return mode;
    }
}

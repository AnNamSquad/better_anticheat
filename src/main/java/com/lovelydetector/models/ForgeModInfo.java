package com.lovelydetector.models;

import java.util.Objects;

public class ForgeModInfo {
    private final String modId;
    private final String version;

    public ForgeModInfo(String modId, String version) {
        this.modId = modId != null ? modId : "";
        this.version = version != null ? version : "";
    }

    public ForgeModInfo(String modId) {
        this(modId, "");
    }

    public String getModId() {
        return modId;
    }

    public String getVersion() {
        return version;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ForgeModInfo that = (ForgeModInfo) o;
        return Objects.equals(modId, that.modId) && Objects.equals(version, that.version);
    }

    @Override
    public int hashCode() {
        return Objects.hash(modId, version);
    }

    @Override
    public String toString() {
        if (version.isEmpty()) {
            return modId;
        }
        return modId + " v" + version;
    }
}

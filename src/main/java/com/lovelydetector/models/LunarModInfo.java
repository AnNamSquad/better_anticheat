package com.lovelydetector.models;

import java.util.Objects;

public class LunarModInfo {
    private final String id;
    private final String name;
    private final String version;
    private final String type;

    public LunarModInfo(String id, String name, String version, String type) {
        this.id = id != null ? id : "";
        this.name = name != null ? name : "";
        this.version = version != null ? version : "";
        this.type = type != null ? type : "";
    }

    public LunarModInfo(String id) {
        this(id, "", "", "");
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getVersion() {
        return version;
    }

    public String getType() {
        return type;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        LunarModInfo that = (LunarModInfo) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        if (version.isEmpty()) {
            return id;
        }
        return id + " v" + version;
    }
}

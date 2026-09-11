package net.minecraft.nbt;

/**
 * Spawn-identity compile stub: the 47.2.0 persist surface the bridge
 * beast extends with its short mob name ({@code MatouEntity} writes/reads
 * one string tag — owner discipline, hub decisions/LOOT.md). Mojang names
 * measured, not recalled, via server.txt + joined.tsrg v2 + javap on the
 * pinned bytes ({@code putString} is {@code m_128359_},
 * {@code contains} is {@code m_128441_}, {@code getString} is
 * {@code m_128461_}). Never runs (compile classpath only).
 */
public class CompoundTag {
    public boolean contains(String key) {
        return false;
    }

    public String getString(String key) {
        return "";
    }

    public void putString(String key, String value) {
    }
}

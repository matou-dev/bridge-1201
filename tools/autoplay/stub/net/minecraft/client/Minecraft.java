package net.minecraft.client;

/**
 * Autoplay compile stub: shape-only 1.20.1 client surface used by the
 * dev-only companion (tools/autoplay/, never shipped, never runs here).
 * Every member below is pinned by the AUTOPLAY derive in hub
 * tools/run-client.sh (javap exact-descriptor check against the pinned
 * vanilla client jar, SRG names from client.txt + the pinned
 * joined.tsrg v2) — drift fails loudly, never silently. Same practice
 * as bridge-1122/1165. No join member: quick-play joins (see want.txt).
 */
public class Minecraft {
    public static Minecraft getInstance() {
        return null;
    }

    public void stop() {
    }
}

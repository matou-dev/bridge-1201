package fr.iamacat.autoplay;

import net.minecraft.client.Minecraft;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Autoplay companion (DEV ONLY, never ships): drives the scripted client
 * proof without a human at the keyboard. The proof joins through the
 * official {@code --quickPlaySingleplayer} launch argument (passed by hub
 * tools/run-client-direct.sh, same world name): the game itself loads the
 * pre-seeded flat world at boot (run-client.sh preseeds
 * saves/&lt;world&gt;, refusing loudly when absent), so this mod never
 * joins programmatically — the 1.20 WorldOpenFlows route would drag
 * LevelStorageAccess/WorldStem/Services through the narrow map for no
 * benefit. The companion counts server ticks near spawn and shuts the
 * game down cleanly. World == pure union is judged afterwards by hub
 * tools/verify-client-save.sh — this mod never places a block, so any
 * foreign block fails loudly there, never here silently.
 *
 * <p>Stop clock: SERVER ticks (bridge-1122 lesson — the client out-ticks
 * a loaded same-JVM server). WAIT_SERVER_TICKS overshoots the 4000-tick
 * union on purpose (slow start re-lands the same deterministic cells —
 * the union is a fixed point). The server handler only counts; the
 * client handler owns the shutdown call (same thread as the proven 1165
 * quit path), so the counter crossing threads is volatile — explicitly,
 * never by luck. No server tick ever (quick-play missed the world) means
 * no shutdown: the run dies by timeout and the verdict fails loud on the
 * absent save — never green by omission.
 */
@Mod(AutoplayMod.MODID)
public class AutoplayMod {
    public static final String MODID = "matouautoplay";

    static final int WAIT_SERVER_TICKS = 4600;

    volatile int serverTicks = 0;
    boolean done = false;

    public AutoplayMod() {
        MinecraftForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        serverTicks++;
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (serverTicks >= WAIT_SERVER_TICKS && !done) {
            done = true;
            Minecraft.getInstance().stop();
        }
    }
}

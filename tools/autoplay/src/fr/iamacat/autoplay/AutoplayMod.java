package fr.iamacat.autoplay;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import fr.iamacat.bridge.forge.Example1Mod;
import fr.iamacat.bridge.forge.MatouEntity;
import fr.iamacat.spi.hit.AABBd;
import fr.iamacat.spi.hit.BoneBox;
import fr.iamacat.spi.model.MatouModel;
import fr.iamacat.spi.model.MatouModelParser;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.EntityGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

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
 *
  * <p>Spike proof (SPIKE=1, DEV ONLY): at SPIKE_MINE_TICK overworld
  * server-level ticks the companion places one stone at isolated coords
  * outside the union slices (20,10,8 — the verdict reads y=63..65 only
  * and the vein band is y=60..61, so the spike cell can never pollute
  * world == pure union; the loot legs already mine (8,10,8) and kill at
  * (12,10,8), so the spike takes another x at the same neutral y, inside
  * the loaded chunks), clears it, and posts that harvest as a
  * {@code BreakEvent} authored by the joined player — then polls the cell
  * back to stone. The harvest is simulated, honestly: placing + clearing
  * plus a bus post exercises the shipped hook ({@code onBreak} reads the
  * level/dim/block through the 47.2.0 shapes) through the live seal
  * ({@code RepopSeal}), the pure {@code RepopJob} and the live sink —
  * that seam is what the spike owns. The post carries the real joined
  * player (the BreakEvent constructor itself reads it — null NPEs,
  * measured on the lead bridge); what is NOT re-proven is vanilla firing
  * the event on a genuine player harvest (Forge-owned, shape-pinned in
  * universal-pin.txt). A repop observed before the delay, or never, fails
  * loudly (spike FAILED) and shuts the game down for post-mortem — the
  * save keeps the air hole, the verifier and the y=10 anvil spot-check
  * refuse it. Without SPIKE=1 nothing here runs and the proof is
  * byte-for-byte the proven union run. 1.20.1 spelling is the loot
  * spelling (same WANT rows, no new members): stone resolves through
  * {@code ForgeRegistries.BLOCKS}, air probes through
  * {@code BlockStateBase.isAir}, place through {@code Level.setBlock},
  * clear through {@code Level.removeBlock}, the player through
  * {@code ServerLevel.players}.
  *
  * <p>Loot proof (LOOT=1, DEV ONLY): at LOOT_HARVEST_TICK overworld
 * server-level ticks the companion harvests the registered ore at an
 * isolated coords outside the union slices (8,10,8 — place + clear + a
 * {@code BreakEvent} post authored by the joined player, spike honesty
 * standard) and, five ticks later, kills the spawned registered beast at
 * (12,10,8) with a simulated {@code LivingDropsEvent} post
 * (single-table scope — hub decisions/LOOT.md) — then polls both spots
 * for the diamond carrier the bridge loot sink spawns per due drop. The
 * posts are simulated, honestly: place + clear + bus posts exercise the
 * shipped hooks ({@code onHarvest} reads the level/dim/block through the
 * 47.2.0 shapes; {@code onKill} reads the entity only) through the live
 * seal ({@code LootSeal}), the pure {@code LootJob} and the live sink —
 * that seam is what loot owns. What is NOT re-proven is vanilla firing
 * the events on a genuine harvest/kill (Forge-owned, shape-pinned in
 * universal-pin.txt). A carrier observed late, or never, fails loudly
 * (loot FAILED) and shuts the game down for post-mortem. Without LOOT=1
 * nothing here runs and the proof is byte-for-byte the proven union run.
 *
 * <p>1.20.1 native spelling (measured against the pinned 47.2.0 bytes,
 * never ported blind from 1122): the harvest post is a {@code level}
 * -package {@code BreakEvent} (the 1.12 {@code HarvestDropsEvent} shape
 * is gone — breaks arrive through it on the forge side too); clears go
 * through {@code removeBlock} (the 1.12 {@code setBlockToAir} shape does
 * not port); air probes go through {@code BlockStateBase.isAir}; the
 * victim is a {@code new MatouEntity(Example1Mod.beastType(), level)}
 * (the T1 {@code new Pig(type, level)} shape is retired with the
 * species — the type rides the bridge's registered beast entry); positioning goes through
 * {@code Entity.setPos} and removal through {@code Entity.discard} (the
 * 1.12 {@code setPositionAndRotation}/{@code setDead} shapes do not
 * port); carriers poll through {@code EntityGetter.getEntitiesOfClass}
 * over one {@code AABB} per spot (the 1.12 {@code loadedEntityList}
 * field shape does not port); the diamond resolves through {@code
 * ForgeRegistries.ITEMS} (the Mojmap autoplay derive pins methods only
 * — no vanilla field touched, see want.txt); the dim gate compares
 * {@code dimension().location()} to {@code "minecraft:overworld"} (no
 * {@code OVERWORLD} field touched, same reason); the client echo gate is
 * the {@code isClientSide()} method (no {@code LogicalSide} surface in
 * the companion at all).
 *
 * <p>Spawn proof (SPAWN=1, DEV ONLY): the bridge itself lands budgeted
 * beasts (SPAWN=1 also arms {@code MatouBridgeMod.spawnTick} -- one flag
 * drives both sides, so LOOT=1 runs stay spawn-free and their own beast
 * never meets the cap veto). The companion never spawns here: it polls
 * the loaded beasts the bridge landed up to cap, records the maximum seen
 * (past cap fails loudly -- the veto owns that bound), kills the first
 * beast past SPAWN_KILL_TICK with a simulated {@code LivingDropsEvent}
 * post (loot honesty standard -- the kill pays through the loot table,
 * proving the spawn-to-loot chain), then polls the diamond carrier at
 * the kill spot. The first living beast's max health is polled once
 * against SPAWN_HP (hp tranche -- the bridge applies the content hp per
 * landing; a diverged read-back fails loudly here too). Custom-entity
 * scope: the census IS the registered beast, so a combined LOOT=1 +
 * SPAWN=1 run counts the loot victim briefly -- the proofs run one flag
 * at a time. A missing beast, a breached cap, or a missing carrier fails
 * loudly (spawn FAILED) and shuts the game down for post-mortem. Without
 * SPAWN=1 nothing here runs and the proof is byte-for-byte the proven
 * union run.
 *
 * <p>Load order (measured on the lead bridge, hub decisions/SPAWN.md):
 * this companion frame-references the bridge's {@code MatouEntity}
 * ({@code new}/{@code instanceof}), so it loads after the bridge
 * (ordering AFTER on matoubridge in autoplay-mods.toml — the 1.20.1
 * shape of the lead's {@code required-after:matoubridge}): without it
 * the companion can construct before the bridge jar is sourced and die
 * on the verifier load. Load-bearing: removing it re-arms the crash.
 * Unproven on 47.2.0 until the live tranche (kept by construction:
 * the bridge is always present in our runs).
 */
@Mod(AutoplayMod.MODID)
public class AutoplayMod {
    public static final String MODID = "matouautoplay";

    static final int WAIT_SERVER_TICKS = 4600;
    static final boolean SPIKE = "1".equals(System.getenv("SPIKE"));
    static final int SPIKE_X = 20;
    static final int SPIKE_Y = 10;
    static final int SPIKE_Z = 8;
    static final String SPIKE_BLOCK = "minecraft:stone";
    static final int SPIKE_MINE_TICK = 1000;
    static final int SPIKE_TIMEOUT = 600;
    static final boolean LOOT = "1".equals(System.getenv("LOOT"));
    static final int LOOT_ORE_X = 8;
    static final int LOOT_ORE_Y = 10;
    static final int LOOT_ORE_Z = 8;
    static final String LOOT_ORE_BLOCK = "example1:my_ore";
    static final int LOOT_BEAST_X = 12;
    static final int LOOT_BEAST_Y = 10;
    static final int LOOT_BEAST_Z = 8;
    static final int LOOT_HARVEST_TICK = 1000;
    static final int LOOT_BEAST_DELAY = 5;
    static final int LOOT_TIMEOUT = 600;
    static final boolean SPAWN = "1".equals(System.getenv("SPAWN"));
    /** Mirrors the effective cap (content {@code owned.matou mob my_beast
     * cap} default, operator {@code spawn.cap} wins -- transported by the
     * bridge spawn wire): the companion counts beasts, the effective
     * policy owns the bound -- a drift here fails the proof loudly
     * instead of asserting a stale cap silently. Override proofs set
     * {@code SPAWN_CAP} to the packs.cfg override (both sides name the
     * same bound, or the breach check is blind).
     */
    static final int SPAWN_CAP = spawnCapOfEnv();
    /** Mirrors the content hp ({@code owned.matou mob my_beast hp} via
     * {@code MatouBridgeMod} spawn wire): the companion polls the landed
     * max health, the bridge owns the value -- a drift here fails the
     * proof loudly instead of asserting a stale hp silently. */
    static final float SPAWN_HP = 20.0f;
    static final int SPAWN_KILL_TICK = 1000;
    static final int SPAWN_TIMEOUT = 600;
    /** Combat proof (DEV ONLY, rides a SPAWN=1 run): at COMBAT_TICK the
     * companion teleports the joined player beside the first living
     * bridge beast, aims at the head bone and strikes through the
     * genuine vanilla attack path (hub decisions/VIRTUAL_HITBOXES.md,
     * server weakspot hook) — then polls the wound. The bridge hook
     * refines that hurt to head x2, so a bare-hand 1.0 lands exactly
     * 2.0 (crit excluded: the teleported player stands — a crit would
     * fail the exact assert loudly, never pass as a weakspot). Without
     * SPAWN=1 nothing runs (COMBAT=1 alone fails loudly — no beasts to
     * strike); without COMBAT=1 the run is byte-for-byte the proven
     * spawn run. */
    static final boolean COMBAT = "1".equals(System.getenv("COMBAT"));
    static final int COMBAT_TICK = 500;
    static final int COMBAT_TIMEOUT = 200;
    /** Operator beast shape (same file the bridge bakes — hub
     * decisions/MATOU_MODEL.md): the companion parses it pure for the
     * head aim, never a hardcoded offset that rots on asset change. */
    static final String COMBAT_GEO = "config/matoubridge/my_beast.geo.json";
    /** Tranche-1 census window: same box the bridge reconciles (see
     * {@code MatouBridgeMod.CENSUS_BOX}, smaller here -- the companion
     * only watches the pads and the kill spot, both near spawn). */
    static final AABB CENSUS_BOX = new AABB(-64, 0, -64, 64, 256, 64);

    volatile int serverTicks = 0;
    volatile int worldTicks = 0;
    volatile boolean mined = false;
    volatile boolean repopped = false;
    volatile boolean spikeFailed = false;
    volatile int mineTick = -1;
    volatile int repopTick = -1;
    volatile int lootOreTick = -1;
    volatile int lootBeastTick = -1;
    volatile boolean oreDropped = false;
    volatile boolean beastDropped = false;
    volatile boolean lootFailed = false;
    volatile int oreDropTick = -1;
    volatile int beastDropTick = -1;
    volatile boolean beastSeen = false;
    volatile boolean hpSeen = false;
    volatile int maxBeasts = 0;
    volatile int firstBeastTick = -1;
    volatile boolean beastKilled = false;
    volatile int killTick = -1;
    volatile int killX = 0;
    volatile int killY = 0;
    volatile int killZ = 0;
    volatile boolean spawnCarrierDropped = false;
    volatile boolean spawnFailed = false;
    volatile int spawnCarrierTick = -1;
    volatile boolean combatStruck = false;
    volatile boolean combatResolved = false;
    volatile boolean combatFailed = false;
    volatile int combatTick = -1;
    volatile float combatHpBefore = -1.0f;
    volatile MatouEntity combatVictim = null;
    ServerLevel world = null;
    Item diamond = null;
    boolean foreignNoted = false;
    boolean playerNoted = false;
    boolean done = false;

    public AutoplayMod() {
        MinecraftForge.EVENT_BUS.register(this);
    }

    /**
     * Effective cap want: {@code SPAWN_CAP} env wins, default 4 is the
     * content cap. Loud on garbage -- a defaulted bound blinds the
     * breach check silently otherwise. DEV-only.
     */
    private static int spawnCapOfEnv() {
        String raw = System.getenv("SPAWN_CAP");
        if (raw == null || raw.isEmpty()) {
            return 4;
        }
        try {
            int v = Integer.parseInt(raw);
            if (v <= 0) {
                throw new NumberFormatException("non-positive");
            }
            return v;
        } catch (RuntimeException bad) {
            throw new IllegalArgumentException(
                    "E_AUTOPLAY_SPAWN_CAP:bad <" + raw
                            + "> (want positive int, default 4)");
        }
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        serverTicks++;
    }

    @SubscribeEvent
    public void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (!SPIKE && !LOOT && !SPAWN && !COMBAT) {
            return;
        }
        if (lootFailed || spawnFailed || spikeFailed || combatFailed) {
            return;
        }
        if (!(event.level instanceof ServerLevel)) {
            return;
        }
        ServerLevel level = (ServerLevel) event.level;
        Level lvl = level;
        if (lvl.isClientSide()) {
            return;
        }
        if (!"minecraft:overworld".equals(
                lvl.dimension().location().toString())) {
            if (!foreignNoted) {
                foreignNoted = true;
                System.out.println("[MatouAutoplay] note : "
                        + "ignoring non-overworld level ticks (the "
                        + "integrated server ticks every dim from boot)");
            }
            return;
        }
        if (world == null) {
            world = level;
            if (SPIKE) {
                System.out.println("[MatouAutoplay] spike armed <"
                        + SPIKE_X + "," + SPIKE_Y + ","
                        + SPIKE_Z + ":" + SPIKE_BLOCK + "> mineAt="
                        + SPIKE_MINE_TICK + " (SPIKE=1)");
            }
            if (LOOT) {
                System.out.println("[MatouAutoplay] loot armed <ore "
                        + LOOT_ORE_X + "," + LOOT_ORE_Y + ","
                        + LOOT_ORE_Z + ":" + LOOT_ORE_BLOCK + " + beast "
                        + LOOT_BEAST_X + "," + LOOT_BEAST_Y + ","
                        + LOOT_BEAST_Z + "> harvestAt="
                        + LOOT_HARVEST_TICK + " (LOOT=1)");
            }
            if (SPAWN) {
                System.out.println("[MatouAutoplay] spawn armed <cap="
                        + SPAWN_CAP + "> killAt=" + SPAWN_KILL_TICK
                        + " (SPAWN=1)");
            }
            if (COMBAT) {
                System.out.println("[MatouAutoplay] combat armed <strikeAt="
                        + COMBAT_TICK + "> (COMBAT=1, rides SPAWN=1)");
            }
        }
        worldTicks++;
        if (SPIKE) {
            if (!mined && !spikeFailed && worldTicks >= SPIKE_MINE_TICK) {
                mine();
            } else if (mined && !repopped && !spikeFailed) {
                poll();
            }
        }
        if (LOOT && !lootFailed) {
            lootTick();
        }
        if (COMBAT && !SPAWN && !combatFailed) {
            combatFail("COMBAT=1 wants SPAWN=1 (no beasts to strike)");
        }
        if (SPAWN && !spawnFailed) {
            spawnTick();
        }
    }

    private void spikeFail(String what) {
        spikeFailed = true;
        System.out.println("[MatouAutoplay] FAIL spike-proof : " + what);
    }

    /**
     * Joined player or null (postponed, loudly once): the simulated spike
     * harvest is authored by the joined player — the break post carries
     * it like the loot harvest post, and an authorless harvest proves
     * nothing. Checked before touching the world. Same shape as
     * {@link #lootPlayer}.
     */
    private Player spikePlayer() {
        List<Player> players = world.players();
        if (players == null || players.isEmpty()) {
            if (!playerNoted) {
                playerNoted = true;
                System.out.println("[MatouAutoplay] note spike-proof : "
                        + "player absent at mine tick, postponing");
            }
            return null;
        }
        return players.get(0);
    }

    private void mine() {
        // The BreakEvent constructor reads the player (measured NPE on
        // null on the lead bridge), so the harvest is authored by the
        // joined player, not forged from null. Absent player (not joined
        // yet) postpones the mine, loudly once; a player that never shows
        // fails the proof instead of mining authorless. Checked before
        // touching the world: a postponed mine leaves no hole behind.
        Player player = spikePlayer();
        if (player == null) {
            if (worldTicks > SPIKE_MINE_TICK + SPIKE_TIMEOUT) {
                spikeFail("player never joined (no harvest author)");
            }
            return;
        }
        if (!ForgeRegistries.BLOCKS.containsKey(
                new ResourceLocation(SPIKE_BLOCK))) {
            spikeFail("unknown <" + SPIKE_BLOCK + "> (want vanilla stone)");
            return;
        }
        Block stone = ForgeRegistries.BLOCKS.getValue(
                new ResourceLocation(SPIKE_BLOCK));
        if (stone == null) {
            spikeFail("unknown <" + SPIKE_BLOCK + "> (want vanilla stone)");
            return;
        }
        BlockState stoneState = stone.defaultBlockState();
        BlockPos at = new BlockPos(SPIKE_X, SPIKE_Y, SPIKE_Z);
        Level lvl = world;
        // Owner discipline (hub decisions/LOOT.md): inherited vanilla
        // members go through the declaring stub type (Level for block
        // reads/writes, BlockStateBase for isAir — never the state or
        // the level subclass), same upcasts as lootOre.
        BlockBehaviour.BlockStateBase before = lvl.getBlockState(at);
        if (!before.isAir()) {
            spikeFail("spike cell occupied before place (want air, "
                    + "proof needs isolated coords)");
            return;
        }
        if (!lvl.setBlock(at, stoneState, 3)) {
            spikeFail("place refused (setBlock false at worldTick "
                    + worldTicks + ")");
            return;
        }
        BlockBehaviour.BlockStateBase placed = lvl.getBlockState(at);
        if (placed.isAir()) {
            spikeFail("place invisible (still air after setBlock)");
            return;
        }
        if (!lvl.removeBlock(at, false)) {
            spikeFail("clear refused (removeBlock false)");
            return;
        }
        BlockBehaviour.BlockStateBase cleared = lvl.getBlockState(at);
        if (!cleared.isAir()) {
            spikeFail("clear invisible (not air after removeBlock)");
            return;
        }
        MinecraftForge.EVENT_BUS.post(new BlockEvent.BreakEvent(world, at,
                stoneState, player));
        mined = true;
        mineTick = worldTicks;
        System.out.println("[MatouAutoplay] spike mined <" + SPIKE_X + ","
                + SPIKE_Y + "," + SPIKE_Z + ":" + SPIKE_BLOCK
                + "> at worldTick " + mineTick);
    }

    private void poll() {
        Level lvl = world;
        BlockBehaviour.BlockStateBase now = lvl.getBlockState(
                new BlockPos(SPIKE_X, SPIKE_Y, SPIKE_Z));
        if (!now.isAir()) {
            repopped = true;
            repopTick = worldTicks;
            System.out.println("[MatouAutoplay] spike repopped <" + SPIKE_X
                    + "," + SPIKE_Y + "," + SPIKE_Z + ":" + SPIKE_BLOCK
                    + "> at worldTick " + repopTick + " (elapsed "
                    + (repopTick - mineTick) + ", want >= 200)");
        } else if (worldTicks > mineTick + SPIKE_TIMEOUT) {
            spikeFail("timeout (still air " + SPIKE_TIMEOUT
                    + " ticks after mine at worldTick " + mineTick + ")");
        }
    }

    private void lootFail(String what) {
        lootFailed = true;
        System.out.println("[MatouAutoplay] FAIL loot-proof : " + what);
    }

    private void lootTick() {
        if (lootOreTick < 0 && worldTicks >= LOOT_HARVEST_TICK) {
            lootOre();
        } else if (lootOreTick >= 0 && lootBeastTick < 0
                && worldTicks >= lootOreTick + LOOT_BEAST_DELAY) {
            lootBeast();
        }
        if ((lootOreTick >= 0 && !oreDropped)
                || (lootBeastTick >= 0 && !beastDropped)) {
            lootPoll();
        }
        if (!(oreDropped && beastDropped)
                && worldTicks > LOOT_HARVEST_TICK + LOOT_TIMEOUT) {
            lootFail("timeout (oreDropped=" + oreDropped + " beastDropped="
                    + beastDropped + " " + LOOT_TIMEOUT
                    + " ticks after harvest at worldTick "
                    + LOOT_HARVEST_TICK + ")");
        }
    }

    /**
     * Joined player or null (postponed, loudly once): both simulated
     * events are authored by the joined player — the break post carries
     * it like the spike harvest post, and an authorless kill proves
     * nothing. Checked before touching the world.
     */
    private Player lootPlayer() {
        List<Player> players = world.players();
        if (players == null || players.isEmpty()) {
            if (!playerNoted) {
                playerNoted = true;
                System.out.println("[MatouAutoplay] note loot-proof : "
                        + "player absent at harvest tick, postponing");
            }
            return null;
        }
        return players.get(0);
    }

    private void lootOre() {
        Player player = lootPlayer();
        if (player == null) {
            return;
        }
        if (!ForgeRegistries.BLOCKS.containsKey(
                new ResourceLocation(LOOT_ORE_BLOCK))) {
            lootFail("unknown <" + LOOT_ORE_BLOCK + "> (want registered ore)");
            return;
        }
        Block ore = ForgeRegistries.BLOCKS.getValue(
                new ResourceLocation(LOOT_ORE_BLOCK));
        if (ore == null) {
            lootFail("unknown <" + LOOT_ORE_BLOCK + "> (want registered ore)");
            return;
        }
        BlockState oreState = ore.defaultBlockState();
        BlockPos at = new BlockPos(LOOT_ORE_X, LOOT_ORE_Y, LOOT_ORE_Z);
        Level lvl = world;
        BlockBehaviour.BlockStateBase before = lvl.getBlockState(at);
        if (!before.isAir()) {
            lootFail("loot cell occupied before place (want air)");
            return;
        }
        if (!lvl.setBlock(at, oreState, 3)) {
            lootFail("place refused (setBlock false at worldTick "
                    + worldTicks + ")");
            return;
        }
        BlockBehaviour.BlockStateBase placed = lvl.getBlockState(at);
        if (placed.isAir()) {
            lootFail("place invisible (still air after setBlock)");
            return;
        }
        if (!lvl.removeBlock(at, false)) {
            lootFail("clear refused (removeBlock false)");
            return;
        }
        BlockBehaviour.BlockStateBase cleared = lvl.getBlockState(at);
        if (!cleared.isAir()) {
            lootFail("clear invisible (not air after removeBlock)");
            return;
        }
        MinecraftForge.EVENT_BUS.post(new BlockEvent.BreakEvent(world, at,
                oreState, player));
        lootOreTick = worldTicks;
        System.out.println("[MatouAutoplay] loot ore harvested <"
                + LOOT_ORE_X + "," + LOOT_ORE_Y + "," + LOOT_ORE_Z + ":"
                + LOOT_ORE_BLOCK + "> at worldTick " + lootOreTick);
    }

    private void lootBeast() {
        if (lootPlayer() == null) {
            return;
        }
        // Custom entity (hub decisions/SPAWN.md): the loot kill lands on
        // the registered beast — every kill pays the single table entry
        // (per-mob filtering stays a re-opener). A wandering vanilla pig
        // would take the scripted kill dishonestly, so the species is
        // exact here, like the bridge census.
        EntityType<MatouEntity> type = Example1Mod.beastType();
        if (type == null) {
            lootFail("no beast type at worldTick " + worldTicks);
            return;
        }
        MatouEntity beast = new MatouEntity(type, world);
        // Owner discipline (hub decisions/LOOT.md): inherited vanilla
        // members go through the declaring stub type, never the beast.
        Entity body = beast;
        body.setPos(LOOT_BEAST_X + 0.5, LOOT_BEAST_Y,
                LOOT_BEAST_Z + 0.5);
        if (!world.addFreshEntity(beast)) {
            lootFail("beast spawn refused at worldTick " + worldTicks);
            return;
        }
        MinecraftForge.EVENT_BUS.post(new LivingDropsEvent(beast, null,
                new ArrayList<ItemEntity>(), 0, true));
        body.discard();
        lootBeastTick = worldTicks;
        System.out.println("[MatouAutoplay] loot beast killed <"
                + LOOT_BEAST_X + "," + LOOT_BEAST_Y + ","
                + LOOT_BEAST_Z + ":beast> at worldTick "
                + lootBeastTick);
    }

    /**
     * Diamond through the Forge registry (same no-field reason as
     * above). Loud on absence: a defaulted item polls nothing silently
     * otherwise.
     */
    private Item diamond() {
        if (diamond != null) {
            return diamond;
        }
        ResourceLocation id = new ResourceLocation("example1:my_gem");
        if (!ForgeRegistries.ITEMS.containsKey(id)) {
            lootFail("unknown <example1:my_gem> (want registered gem)");
            return null;
        }
        diamond = ForgeRegistries.ITEMS.getValue(id);
        if (diamond == null) {
            lootFail("unknown <example1:my_gem> (want registered gem)");
            return null;
        }
        return diamond;
    }

    private void lootPoll() {
        Item gem = diamond();
        if (gem == null) {
            return;
        }
        // Owner discipline (hub decisions/LOOT.md): the carrier poll goes
        // through the declaring EntityGetter type — never a field read.
        EntityGetter getter = world;
        if (!oreDropped) {
            List<ItemEntity> found = getter.getEntitiesOfClass(
                    ItemEntity.class, box(LOOT_ORE_X, LOOT_ORE_Y,
                            LOOT_ORE_Z));
            for (ItemEntity item : found) {
                if (carrierOf(item, gem)
                        && near(item, LOOT_ORE_X, LOOT_ORE_Y, LOOT_ORE_Z)) {
                    oreDropped = true;
                    oreDropTick = worldTicks;
                    System.out.println("[MatouAutoplay] loot ore dropped "
                            + "<example1:my_gem> at worldTick " + oreDropTick
                            + " (elapsed " + (oreDropTick - lootOreTick)
                            + ", want immediate)");
                    break;
                }
            }
        }
        if (!beastDropped && lootBeastTick >= 0) {
            List<ItemEntity> found = getter.getEntitiesOfClass(
                    ItemEntity.class, box(LOOT_BEAST_X, LOOT_BEAST_Y,
                            LOOT_BEAST_Z));
            for (ItemEntity item : found) {
                if (carrierOf(item, gem)
                        && near(item, LOOT_BEAST_X, LOOT_BEAST_Y,
                                LOOT_BEAST_Z)) {
                    beastDropped = true;
                    beastDropTick = worldTicks;
                    System.out.println("[MatouAutoplay] loot beast dropped "
                            + "<example1:my_gem> at worldTick " + beastDropTick
                            + " (elapsed " + (beastDropTick - lootBeastTick)
                            + ", want immediate)");
                    break;
                }
            }
        }
    }

    private static AABB box(int x, int y, int z) {
        return new AABB(x - 2.5, y - 2.5, z - 2.5, x + 3.5, y + 3.5,
                z + 3.5);
    }

    private static boolean carrierOf(ItemEntity item, Item gem) {
        ItemStack stack = item.getItem();
        return stack != null && stack.getItem() == gem;
    }

    private static boolean near(Entity e, int x, int y, int z) {
        return Math.abs(e.getX() - (x + 0.5)) < 3.0
                && Math.abs(e.getY() - (y + 0.5)) < 3.0
                && Math.abs(e.getZ() - (z + 0.5)) < 3.0;
    }

    private void spawnFail(String what) {
        spawnFailed = true;
        System.out.println("[MatouAutoplay] FAIL spawn-proof : " + what);
    }

    /**
     * Spawn proof tick: count the bridge-landed beasts (cap bound owned by
     * the bridge veto -- past cap fails here), kill the first beast past
     * the kill tick through the loot seam, poll the carrier at the kill
     * spot. The companion never spawns: every beast here was decided by
     * the pure SpawnJob and landed by the bridge sink. Custom-entity
     * scope: the census IS the registered beast (vanilla pigs are a
     * different species — counting one would breach a cap that is not
     * its own).
     */
    private void spawnTick() {
        // Owner discipline (hub decisions/LOOT.md): the census poll goes
        // through the declaring EntityGetter type -- never a field read.
        EntityGetter getter = world;
        List<MatouEntity> found = getter.getEntitiesOfClass(
                MatouEntity.class, CENSUS_BOX);
        int beasts = 0;
        MatouEntity first = null;
        for (MatouEntity beast : found) {
            // Owner discipline (hub decisions/LOOT.md): inherited vanilla
            // members go through the declaring stub type, never the beast.
            // Dead beasts linger in the loaded set (measured on 1710: a
            // corpse counted past cap at worldTick 51) -- the census
            // counts the living only, like the bridge release on the
            // kill hook.
            Entity body = beast;
            if (!body.isAlive()) {
                continue;
            }
            beasts++;
            if (first == null) {
                first = beast;
            }
        }
        if (beasts > maxBeasts) {
            maxBeasts = beasts;
            System.out.println("[MatouAutoplay] spawn census <" + beasts
                    + "> at worldTick " + worldTicks);
        }
        if (!beastSeen && beasts > 0) {
            beastSeen = true;
            firstBeastTick = worldTicks;
            System.out.println("[MatouAutoplay] spawn first beast at "
                    + "worldTick " + firstBeastTick);
        }
        if (!hpSeen && first != null) {
            // Owner discipline (hub decisions/LOOT.md): inherited vanilla
            // members go through the declaring stub type, never the beast.
            LivingEntity living = first;
            float hp = living.getMaxHealth();
            if (hp != SPAWN_HP) {
                spawnFail("hp diverged <want=" + SPAWN_HP + " got=" + hp
                        + "> at worldTick " + worldTicks);
                return;
            }
            hpSeen = true;
            System.out.println("[MatouAutoplay] spawn hp <" + hp
                    + "> at worldTick " + worldTicks);
        }
        if (beasts > SPAWN_CAP) {
            spawnFail("cap breached <" + beasts + " > " + SPAWN_CAP
                    + "> at worldTick " + worldTicks);
            return;
        }
        if (COMBAT && !combatFailed && beastSeen && first != null) {
            if (!combatStruck && worldTicks >= COMBAT_TICK) {
                combatAttack(first);
            } else if (combatStruck && !combatResolved) {
                combatPoll();
            }
        }
        if (!beastKilled && beastSeen && first != null
                && worldTicks >= SPAWN_KILL_TICK) {
            spawnKill(first);
        }
        if (beastKilled && !spawnCarrierDropped) {
            spawnPoll();
        }
        if (!beastSeen && worldTicks > SPAWN_KILL_TICK + SPAWN_TIMEOUT) {
            spawnFail("timeout (no bridge beast " + SPAWN_TIMEOUT
                    + " ticks after kill tick " + SPAWN_KILL_TICK + ")");
        } else if (beastKilled && !spawnCarrierDropped
                && worldTicks > killTick + SPAWN_TIMEOUT) {
            spawnFail("timeout (no carrier " + SPAWN_TIMEOUT
                    + " ticks after kill at worldTick " + killTick + ")");
        }
    }

    private void spawnKill(MatouEntity beast) {
        // Owner discipline (hub decisions/LOOT.md): inherited vanilla
        // members go through the declaring stub type, never the beast.
        Entity body = beast;
        killX = (int) Math.floor(body.getX());
        killY = (int) Math.floor(body.getY());
        killZ = (int) Math.floor(body.getZ());
        MinecraftForge.EVENT_BUS.post(new LivingDropsEvent(beast, null,
                new ArrayList<ItemEntity>(), 0, true));
        body.discard();
        beastKilled = true;
        killTick = worldTicks;
        System.out.println("[MatouAutoplay] spawn beast killed <"
                + killX + "," + killY + "," + killZ + ":beast> at "
                + "worldTick " + killTick);
    }

    private void spawnPoll() {
        Item gem = diamond();
        if (gem == null) {
            return;
        }
        EntityGetter getter = world;
        List<ItemEntity> found = getter.getEntitiesOfClass(
                ItemEntity.class, box(killX, killY, killZ));
        for (ItemEntity item : found) {
            if (carrierOf(item, gem)
                    && near(item, killX, killY, killZ)) {
                spawnCarrierDropped = true;
                spawnCarrierTick = worldTicks;
                System.out.println("[MatouAutoplay] spawn beast dropped "
                        + "<example1:my_gem> at worldTick " + spawnCarrierTick
                        + " (elapsed " + (spawnCarrierTick - killTick)
                        + ", want immediate)");
                return;
            }
        }
    }

    private void combatFail(String what) {
        combatFailed = true;
        System.out.println("[MatouAutoplay] FAIL combat-proof : " + what);
    }

    /**
     * Joined player or null (postponed, loudly once): the combat strike
     * is authored by the joined player — the genuine attack path carries
     * it, and an authorless strike proves nothing. Same shape as the
     * spike/loot player helpers (the 1.20.1 {@code ServerLevel.players}
     * list, declaring stub type).
     */
    private Player combatPlayer() {
        List<Player> players = world.players();
        if (players == null || players.isEmpty()) {
            if (!playerNoted) {
                playerNoted = true;
                System.out.println("[MatouAutoplay] note combat-proof : "
                        + "player absent at strike tick, postponing");
            }
            return null;
        }
        return players.get(0);
    }

    /**
     * Combat strike: teleport the joined player beside the beast, aim at
     * the head bone and strike through the genuine vanilla attack path
     * (the exact call a survival click issues server-side). Same tick,
     * atomic: teleport, aim, read health, strike — the beast AI never
     * moves mid-call, so the ray the bridge hook re-derives is this
     * one. Owner discipline (hub decisions/LOOT.md): inherited vanilla
     * members go through the declaring stub type, never the beast —
     * {@code Entity} for positions/eye/teleport, {@code Player} for the
     * strike itself (declared there), {@code LivingEntity} for the health
     * read.
     */
    private void combatAttack(MatouEntity beast) {
        Player player = combatPlayer();
        if (player == null) {
            if (worldTicks > COMBAT_TICK + COMBAT_TIMEOUT) {
                combatFail("player never joined (no strike author)");
            }
            return;
        }
        double[] head = combatHeadCenter();
        if (head == null) {
            return;
        }
        Entity body = beast;
        if (combatVictim == null) {
            combatVictim = beast;
        }
        double bx = body.getX();
        double by = body.getY();
        double bz = body.getZ();
        Entity pbody = player;
        float eyeH = pbody.getEyeHeight();
        if (!(eyeH > 1.0f && eyeH < 2.0f)) {
            combatFail("eye height diverged <" + eyeH
                    + "> (want the standing player ~1.62)");
            return;
        }
        // Stand-off 2.2 blocks east on the beast ground plane (flat
        // proof world — same Y is standing ground; well inside the
        // vanilla ~3.0 reach so the genuine path delivers the hurt,
        // far enough that the descending ray clears the body box top
        // and lands the head first — measured in the decision).
        double px = bx + 2.2;
        double py = by;
        double pz = bz;
        double ex = px;
        double ey = py + eyeH;
        double ez = pz;
        double tx = bx + head[0];
        double ty = by + head[1];
        double tz = bz + head[2];
        double dx = tx - ex;
        double dy = ty - ey;
        double dz = tz - ez;
        double horiz = Math.sqrt(dx * dx + dz * dz);
        if (horiz < 0.5) {
            combatFail("stand-off degenerate (beast under the player?)");
            return;
        }
        double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        // MC yaw faces +Z at 0 (-90 at +X, measured against getLookAngle):
        // yaw = -deg(atan2(-dx, -dz)) - 180, pitch = -deg(atan2(dy,
        // horiz)). Derived once in the decision, self-checked below.
        float yaw = (float) (-Math.toDegrees(Math.atan2(-dx, -dz))
                - 180.0);
        while (yaw <= -180.0f) {
            yaw += 360.0f;
        }
        while (yaw > 180.0f) {
            yaw -= 360.0f;
        }
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, horiz));
        // Aim self-check: re-derive the look with the vanilla formula
        // and demand it points at the head (DEV-only assertion mirroring
        // getLookAngle — the bridge hook reads the real one; this only
        // guards a wasted live run on bad aim math).
        double r = Math.PI / 180.0;
        double f = Math.cos(-yaw * r - Math.PI);
        double f1 = Math.sin(-yaw * r - Math.PI);
        double f2 = -Math.cos(-pitch * r);
        double f3 = Math.sin(-pitch * r);
        double dot = (f1 * f2 * dx + f3 * dy + f * f2 * dz) / len;
        if (!(dot > 0.999)) {
            combatFail("aim diverged <dot=" + dot + "> (want > 0.999)");
            return;
        }
        LivingEntity living = beast;
        combatHpBefore = living.getHealth();
        pbody.moveTo(px, py, pz, yaw, pitch);
        player.attack(beast);
        combatTick = worldTicks;
        combatStruck = true;
        System.out.println("[MatouAutoplay] combat struck <head hp="
                + combatHpBefore + "> at worldTick " + combatTick);
    }

    /**
     * Combat poll: the bridge hook refines the struck hurt to head x2
     * the same tick, so the wound reads exactly 2.0 (bare-hand 1.0 —
     * the exact assert fails loudly on any surprise: a 1.0 would be an
     * unrefined body shot, a 3.0 a crit, a 0.0 a lost hurt).
     */
    private void combatPoll() {
        if (combatVictim == null) {
            combatFail("victim lost before poll");
            return;
        }
        LivingEntity living = combatVictim;
        float hp = living.getHealth();
        float drop = combatHpBefore - hp;
        if (Math.abs(drop - 2.0f) < 1e-3f) {
            combatResolved = true;
            System.out.println("[MatouAutoplay] combat resolved <drop="
                    + drop + " hp=" + hp + "> at worldTick " + worldTicks
                    + " (elapsed " + (worldTicks - combatTick) + ")");
            return;
        }
        if (worldTicks > combatTick + COMBAT_TIMEOUT) {
            combatFail("timeout <drop=" + drop + " hp=" + hp
                    + "> (want exactly 2.0, head x2 over bare-hand 1.0)");
        }
    }

    /**
     * Head aim, parsed pure from the shipped shape (same bytes the
     * bridge bakes — hub decisions/MATOU_MODEL.md owns the format, this
     * only reads the bone center, never a hardcoded offset).
     */
    private double[] combatHeadCenter() {
        byte[] bytes;
        try {
            bytes = Files.readAllBytes(Paths.get(COMBAT_GEO));
        } catch (Exception e) {
            combatFail("geo unreadable <" + COMBAT_GEO + "> ("
                    + e.getMessage() + ")");
            return null;
        }
        MatouModel model;
        try {
            model = MatouModelParser.parse(
                    new String(bytes, StandardCharsets.UTF_8));
        } catch (RuntimeException bad) {
            combatFail("geo rejected <" + bad.getMessage() + ">");
            return null;
        }
        for (BoneBox bb : model.boneBoxes()) {
            if ("head".equals(bb.boneName)) {
                AABBd b = bb.box;
                return new double[] {
                    (b.minX + b.maxX) / 2.0,
                    (b.minY + b.maxY) / 2.0,
                    (b.minZ + b.maxZ) / 2.0 };
            }
        }
        combatFail("geo headless (the weakspot table names head)");
        return null;
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (SPIKE && spikeFailed && !done) {
            done = true;
            System.out.println("[MatouAutoplay] spike FAILED, shutting down");
            Minecraft.getInstance().stop();
            return;
        }
        if (LOOT && lootFailed && !done) {
            done = true;
            System.out.println("[MatouAutoplay] loot FAILED, shutting down");
            Minecraft.getInstance().stop();
            return;
        }
        if (SPAWN && spawnFailed && !done) {
            done = true;
            System.out.println("[MatouAutoplay] spawn FAILED, shutting down");
            Minecraft.getInstance().stop();
            return;
        }
        if (COMBAT && combatFailed && !done) {
            done = true;
            System.out.println("[MatouAutoplay] combat FAILED, shutting down");
            Minecraft.getInstance().stop();
            return;
        }
        if (serverTicks >= WAIT_SERVER_TICKS
                && (!SPIKE || repopped)
                && (!LOOT || (oreDropped && beastDropped))
                && (!SPAWN || (beastSeen && spawnCarrierDropped))
                && (!COMBAT || combatResolved)
                && !done) {
            done = true;
            Minecraft.getInstance().stop();
        }
    }
}

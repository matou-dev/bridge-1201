package fr.iamacat.autoplay;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
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
 * <p>Loot proof (LOOT=1, DEV ONLY): at LOOT_HARVEST_TICK overworld
 * server-level ticks the companion harvests the registered ore at an
 * isolated coords outside the union slices (8,10,8 — place + clear + a
 * {@code BreakEvent} post authored by the joined player, spike honesty
 * standard) and, five ticks later, kills a spawned vanilla pig at
 * (12,10,8) with a simulated {@code LivingDropsEvent} post (T1
 * any-kill-pays — hub decisions/LOOT.md; the species narrows when
 * custom-entity registration lands) — then polls both spots for the
 * diamond carrier the bridge loot sink spawns per due drop. The posts
 * are simulated, honestly: place + clear + bus posts exercise the
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
 * victim is a {@code new Pig(type, level)} (the 1.12 no-arg shape does
 * not port — the type resolves through {@code ForgeRegistries
 * .ENTITY_TYPES}, no vanilla field touched); positioning goes through
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
 */
@Mod(AutoplayMod.MODID)
public class AutoplayMod {
    public static final String MODID = "matouautoplay";

    static final int WAIT_SERVER_TICKS = 4600;
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

    volatile int serverTicks = 0;
    volatile int worldTicks = 0;
    volatile int lootOreTick = -1;
    volatile int lootBeastTick = -1;
    volatile boolean oreDropped = false;
    volatile boolean beastDropped = false;
    volatile boolean lootFailed = false;
    volatile int oreDropTick = -1;
    volatile int beastDropTick = -1;
    ServerLevel world = null;
    Item diamond = null;
    EntityType<Pig> pigType = null;
    boolean foreignNoted = false;
    boolean playerNoted = false;
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
    public void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (!LOOT || lootFailed) {
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
                System.out.println("[MatouAutoplay] note loot-proof : "
                        + "ignoring non-overworld level ticks (the "
                        + "integrated server ticks every dim from boot)");
            }
            return;
        }
        if (world == null) {
            world = level;
            System.out.println("[MatouAutoplay] loot armed <ore "
                    + LOOT_ORE_X + "," + LOOT_ORE_Y + ","
                    + LOOT_ORE_Z + ":" + LOOT_ORE_BLOCK + " + pig "
                    + LOOT_BEAST_X + "," + LOOT_BEAST_Y + ","
                    + LOOT_BEAST_Z + "> harvestAt="
                    + LOOT_HARVEST_TICK + " (LOOT=1)");
        }
        worldTicks++;
        lootTick();
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
        // T1 any-kill-pays (hub decisions/LOOT.md): the loot kill lands
        // on a vanilla pig — every kill pays the single table entry
        // (per-mob filtering stays a re-opener).
        EntityType<Pig> type = pigType();
        if (type == null) {
            return;
        }
        Pig pig = new Pig(type, world);
        // Owner discipline (hub decisions/LOOT.md): inherited vanilla
        // members go through the declaring stub type, never the pig.
        Entity body = pig;
        body.setPos(LOOT_BEAST_X + 0.5, LOOT_BEAST_Y,
                LOOT_BEAST_Z + 0.5);
        if (!world.addFreshEntity(pig)) {
            lootFail("pig spawn refused at worldTick " + worldTicks);
            return;
        }
        MinecraftForge.EVENT_BUS.post(new LivingDropsEvent(pig, null,
                new ArrayList<ItemEntity>(), 0, true));
        body.discard();
        lootBeastTick = worldTicks;
        System.out.println("[MatouAutoplay] loot beast killed <"
                + LOOT_BEAST_X + "," + LOOT_BEAST_Y + ","
                + LOOT_BEAST_Z + ":pig> at worldTick "
                + lootBeastTick);
    }

    /**
     * Pig type through the Forge registry (no vanilla field touched —
     * the Mojmap autoplay derive pins methods only, see want.txt). Loud
     * on absence: a defaulted type spawns nothing silently otherwise.
     */
    @SuppressWarnings("unchecked")
    private EntityType<Pig> pigType() {
        if (pigType != null) {
            return pigType;
        }
        ResourceLocation id = new ResourceLocation("minecraft:pig");
        if (!ForgeRegistries.ENTITY_TYPES.containsKey(id)) {
            lootFail("unknown <minecraft:pig> (want vanilla pig type)");
            return null;
        }
        pigType = (EntityType<Pig>) ForgeRegistries.ENTITY_TYPES.getValue(id);
        if (pigType == null) {
            lootFail("unknown <minecraft:pig> (want vanilla pig type)");
            return null;
        }
        return pigType;
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
        ResourceLocation id = new ResourceLocation("minecraft:diamond");
        if (!ForgeRegistries.ITEMS.containsKey(id)) {
            lootFail("unknown <minecraft:diamond> (want vanilla diamond)");
            return null;
        }
        diamond = ForgeRegistries.ITEMS.getValue(id);
        if (diamond == null) {
            lootFail("unknown <minecraft:diamond> (want vanilla diamond)");
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
                            + "<diamond> at worldTick " + oreDropTick
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
                            + "<diamond> at worldTick " + beastDropTick
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

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (LOOT && lootFailed && !done) {
            done = true;
            System.out.println("[MatouAutoplay] loot FAILED, shutting down");
            Minecraft.getInstance().stop();
            return;
        }
        if (serverTicks >= WAIT_SERVER_TICKS
                && (!LOOT || (oreDropped && beastDropped))
                && !done) {
            done = true;
            Minecraft.getInstance().stop();
        }
    }
}

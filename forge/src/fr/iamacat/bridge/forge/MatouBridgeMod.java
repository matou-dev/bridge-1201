package fr.iamacat.bridge.forge;

import fr.iamacat.bridge.ForgeCells;
import fr.iamacat.bridge.ForgeSnapshot;
import fr.iamacat.bridge.Packs;
import fr.iamacat.bridge.loot.DropStore;
import fr.iamacat.bridge.loot.LootSeal;
import fr.iamacat.bridge.wire.OperatorPolicy;
import fr.iamacat.example1.LootJob;
import fr.iamacat.example1.LootTable;
import fr.iamacat.spi.Cell;
import fr.iamacat.spi.ContentPack;
import fr.iamacat.spi.LootStates;
import fr.iamacat.spi.MatouId;
import fr.iamacat.spi.Snapshot;
import fr.iamacat.spi.StateVocabulary;
import fr.iamacat.spi.VocabularyPack;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.Vec3i;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * D1 Forge wiring (47.2.0): FML level tick in, pure SPI decide,
 * bridge-owned apply. Packs come from {@code config/matoubridge/packs.cfg}
 * (one {@code <class> <y> <block> [k=v ...]} per line); a missing file
 * means no packs, staying passive (Q1 cohabitation). Malformed config or
 * unloadable pack fails fast at setup — a half-wired bridge never
 * ticks.
 *
 * <p>Loot (event-sourced, hub decisions/LOOT.md, T1 any-kill-pays):
 * breaks of the operator wire blocks arrive on {@link #onHarvest} (Forge
 * break events, server side, dim 0 only) and mob kills on {@link #onKill}
 * (Forge living-drops events, same scope) into the bridge-owned
 * {@link DropStore}; every server tick {@link #lootTick} seals the store
 * plus the wired {@link LootTable} beside the first wire's pack states
 * (the pack-served loot vocabulary, T3 registry — hub
 * {@code decisions/SPI_STATE_VOCABULARY.md}) and the pure {@link LootJob}
 * decides what drops. The ore scope is the packs.cfg wire-block column
 * (T2 operator-override tranche, hub decisions/SPAWN.md — no bridge
 * constant names a loot block); the per-harvest count is the content
 * {@code drop_count} unless the operator {@code loot.count} wins. Due
 * drops land as {@link ItemEntity} carriers beside the vanilla drops
 * (vanilla behaviour untouched). The carrier is vanilla diamond until
 * item registration lands on the REGISTRATION path; every dim-0 kill
 * pays the single table entry (per-mob filtering is a re-opener, never
 * a quiet filter — hub decisions/LOOT.md). New refusals stay loot-local
 * ({@code E_LOOT_*}, never in the {@code E_FORGE_*} parity catalog), so
 * bridge parity holds with behaviour intentionally 1201-only until
 * proven.
 *
 * <p>1.20.1 native spelling (measured against the pinned 47.2.0 bytes,
 * never ported blind from 1165): the break signal lives in the
 * {@code level} event package (the 1.12/1.16.5 {@code world} package
 * does not exist on 1.20.1) and carries the level behind
 * {@code getLevel} as a {@code LevelAccessor} (narrowed to
 * {@code ServerLevel} before any read — the 1.16.5 {@code IWorld}
 * shape does not port); the client echo gate is the
 * {@code isClientSide()} method (the 1.16.5 {@code isRemote} field
 * shape does not port); kills arrive through the inherited
 * {@code LivingEvent.getEntity()} as a {@code LivingEntity} (the
 * 1.12/1.16.5 {@code getEntityLiving} shape does not port), whose
 * level reads through the {@code level()} method (the 1.16.5 public
 * {@code world} field shape does not port) and whose coords read
 * through {@code getX/getY/getZ} (the 1.12 {@code posX} field shape
 * does not port); the sink is {@code ServerLevel.addFreshEntity}
 * (public — the 1.12 {@code World.spawnEntity} / 1.16.5
 * {@code ServerWorld.addEntity} shapes do not port); the dim gate stays
 * the {@code OVERWORLD} key. Coords read through the declaring
 * {@code Vec3i} type, the entity level/coords through the declaring
 * {@code Entity} type, and the ore match through the declaring
 * {@code BlockBehaviour.BlockStateBase} type (owner discipline — hub
 * decisions/LOOT.md; the 1.16.5 {@code Vector3i} /
 * {@code AbstractBlockState} declarers do not exist on 1.20.1).
 *
 * <p>Bind timing (hub decisions/REGISTRATION.md): deferred registries
 * fill at the registry event, after every mod constructs and before any
 * common setup — so a constructor-time {@code PackWire.bind} would
 * resolve a custom name before it exists and refuse loudly on a correct
 * config. Specs parse in the constructor (pure, no registry), binds land
 * in a common-setup listener, at/after the fill, whatever the mod order.
 *
 * <p>Only this package may touch MC/Forge; the decide/apply seam
 * ({@code fr.iamacat.bridge}) ships from {@code matou-spi} (see
 * {@code SPI_PIN}).
 */

/**
 * D1 Forge wiring (47.2.0): FML level tick in, pure SPI decide,
 * bridge-owned apply. Packs come from {@code config/matoubridge/packs.cfg}
 * (one {@code <class> <y> <block> [k=v ...]} per line); a missing file
 * means no packs, staying passive (Q1 cohabitation). Malformed config or
 * unloadable pack fails fast at construction — a half-wired bridge never
 * ticks.
 *
 * <p>Only this package may touch MC/Forge; the decide/apply seam
 * ({@code fr.iamacat.bridge}) ships from {@code matou-spi} (see
 * {@code SPI_PIN}).
 */
@Mod(MatouBridgeMod.MODID)
public final class MatouBridgeMod {
    public static final String MODID = "matoubridge";
    static final String PACKS_PATH = "config/matoubridge/packs.cfg";

    /** Loot scope: the operator wire blocks, resolved at wire time (T2
     * operator-override tranche, hub decisions/SPAWN.md — the packs.cfg
     * wire-block column names the ore, no bridge constant does; other
     * breaks are not the loot's business, fortune/silk modifiers stay
     * explicit non-goals). Wiring the dev stone default therefore pays
     * stone breaks; every live proof wires the registered ore.
     */
    private final List<String> oreNames = new ArrayList<String>();
    private final List<Block> ores = new ArrayList<Block>();
    /** Loot policy, sealed from the content table at wire time unless
     * the operator {@code loot.count} wins (operator-override tranche):
     * effective items per harvest. Transported, never owned. */
    private long lootCount;

    private final List<PackWire> wires = new ArrayList<PackWire>();
    private final List<Packs.PackSpec> pending = new ArrayList<Packs.PackSpec>();
    private final DropStore drops = new DropStore();
    private final LootJob loot = new LootJob();
    private Map<String, String> lootTable;
    private StateVocabulary lootVocab;
    /** Owned content path (shared with the future spawn wire — same file
     * funds both tables, parsed once here). */
    private String ownedPath;
    private long tick;

    public MatouBridgeMod() {
        MinecraftForge.EVENT_BUS.register(this);
        File cfg = new File(PACKS_PATH);
        if (!cfg.isFile()) {
            return;
        }
        List<String> lines;
        try {
            lines = Files.readAllLines(cfg.toPath(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("E_FORGE_PACKS:unreadable <"
                    + PACKS_PATH + "> (" + e.getMessage() + ")", e);
        }
        for (Packs.PackSpec spec : Packs.parseLines(lines)) {
            pending.add(spec);
        }
        FMLJavaModLoadingContext.get().getModEventBus().addListener(
                (FMLCommonSetupEvent event) -> bindPending());
    }

    /**
     * Setup-time bind: the deferred-registry fill already landed (one
     * loading state ago), so custom names resolve here. A half-bound
     * wire never ticks — refusal is loud, at setup, never silent.
     */
    private void bindPending() {
        for (Packs.PackSpec spec : pending) {
            wires.add(PackWire.bind(spec));
        }
        wireLoot(pending);
        pending.clear();
    }

    /**
     * T3 vocabulary provision (hub
     * {@code decisions/SPI_STATE_VOCABULARY.md}): the seal vocabularies
     * come from the first wire's reflectively loaded pack at wire time
     * (parse-once, never on the tick path — the seals merge beside that
     * same wire's states), so seals share the job's ids with no new
     * bridge-to-content compile edge. A pack serving no vocabulary
     * refuses loudly — sealing under a guessed id would be a silent
     * default; the pack's own unknown-scope refusal propagates untouched.
     */
    private StateVocabulary vocabulary(String scope, String code) {
        if (wires.isEmpty()) {
            throw new IllegalStateException(code + ":nowire (want a "
                    + "wired pack to serve the " + scope + " vocabulary)");
        }
        ContentPack pack = wires.get(0).pack();
        if (!(pack instanceof VocabularyPack)) {
            throw new IllegalArgumentException(code + ":novocab <"
                    + pack.getClass().getName() + "> (pack serves no "
                    + scope + " vocabulary)");
        }
        return ((VocabularyPack) pack).vocabulary(scope);
    }

    /**
     * Loot wiring: one table per bridge from the packs' owned content
     * (parsed once, like registration — never on the tick path), the
     * content {@code drop_count} unless the operator {@code loot.count}
     * wins, and the ore scope from the operator wire-block column (T2
     * operator-override tranche — no bridge constant names a loot
     * block). No owned file anywhere means loot stays passive (Q1
     * cohabitation): the hooks gate on the null table. Several distinct
     * owned files refuse loudly — silent table picks are defaults. The
     * ore resolve probes the registry (presence first — {@code getValue}
     * returns air for unknown names, never null).
     */
    private void wireLoot(List<Packs.PackSpec> specs) {
        Set<String> owned = new HashSet<String>();
        for (Packs.PackSpec spec : specs) {
            String path = spec.args.get("ownedFile");
            if (path != null) {
                owned.add(path);
            }
        }
        if (owned.isEmpty()) {
            return;
        }
        if (owned.size() > 1) {
            throw new IllegalArgumentException("E_LOOT_TABLE:multi <"
                    + owned + "> (one table per bridge)");
        }
        ownedPath = owned.iterator().next();
        lootVocab = vocabulary(LootStates.SCOPE, "E_LOOT_SEAL");
        LootTable wired = LootTable.fromFile(ownedPath);
        lootTable = wired.drops();
        lootCount = OperatorPolicy.effectiveLootCount(wired.count(), specs);
        for (String name : OperatorPolicy.wireBlocks(specs)) {
            ResourceLocation id = new ResourceLocation(name);
            if (!ForgeRegistries.BLOCKS.containsKey(id)) {
                throw new IllegalArgumentException("E_LOOT_ORE:unknown <"
                        + name + ">");
            }
            Block block = ForgeRegistries.BLOCKS.getValue(id);
            if (block == null) {
                throw new IllegalArgumentException("E_LOOT_ORE:unknown <"
                        + name + ">");
            }
            oreNames.add(name);
            ores.add(block);
        }
        String lootNote = OperatorPolicy.present(specs,
                OperatorPolicy.LOOT_COUNT) ? " overridden <loot.count>"
                : "";
        System.out.println("[MatouBridge] loot wired <" + lootTable
                + "> count <" + lootCount + "> ore <" + oreNames + ">"
                + lootNote);
        if (Items.DIAMOND == null) {
            throw new IllegalArgumentException(
                    "E_LOOT_GEM:unknown <minecraft:diamond>");
        }
    }

    @SubscribeEvent
    public void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.side != LogicalSide.SERVER
                || event.phase != TickEvent.Phase.END) {
            return;
        }
        if (!Level.OVERWORLD.equals(event.level.dimension())) {
            return;
        }
        for (PackWire wire : wires) {
            wire.applyTo(event.level, tick);
        }
        lootTick(event.level, tick);
        tick++;
    }

    /**
     * Loot record: a server-side dim-0 break of an operator wire block
     * becomes an ore harvest at the last server tick (same clock the
     * per-tick seal reads — both run on the server thread). Client-side
     * echoes (isClientSide) are ignored: the server fires its own event
     * for the same break. Fortune, silk touch and the vanilla drop list
     * are untouched (explicit non-goals): the seam only records.
     */
    @SubscribeEvent
    public void onHarvest(BlockEvent.BreakEvent event) {
        if (lootTable == null) {
            return;
        }
        LevelAccessor w = event.getLevel();
        if (!(w instanceof ServerLevel)) {
            return;
        }
        ServerLevel level = (ServerLevel) w;
        if (level.isClientSide()) {
            return;
        }
        if (!Level.OVERWORLD.equals(level.dimension())) {
            return;
        }
        // Owner discipline (hub decisions/LOOT.md): coords go through the
        // declaring Vec3i type and the block through the declaring
        // BlockStateBase type — the hierarchy walk only maps the exact
        // bytecode owner.
        BlockBehaviour.BlockStateBase s = event.getState();
        if (!ores.contains(s.getBlock())) {
            return;
        }
        Vec3i p = event.getPos();
        String harvest = Cell.of(p.getX(), p.getY(), p.getZ(),
                LootJob.ORE).render();
        drops.record(harvest, tick);
        System.out.println("[MatouBridge] loot recorded <" + harvest
                + "> at tick " + tick);
    }

    /**
     * Loot record: a server-side dim-0 mob kill becomes a beast harvest
     * at the entity's block coords. T1 any-kill-pays scope (hub
     * decisions/LOOT.md): every kill pays the one entry — per-mob
     * filtering is a re-opener, never a quiet filter here. (The beast
     * species narrows when custom-entity registration lands; until then
     * the companion kills a vanilla pig.)
     *
     * <p>Owner discipline (measured live on 1710: NoSuchFieldError
     * worldObj): inherited vanilla members are read through the declaring
     * stub type ({@code Entity}), never through the event's
     * {@code LivingEntity} — hence the upcast local below (the
     * hierarchy walk only maps the exact bytecode owner).
     */
    @SubscribeEvent
    public void onKill(LivingDropsEvent event) {
        if (lootTable == null) {
            return;
        }
        LivingEntity body = event.getEntity();
        Entity e = body;
        Level level = e.level();
        if (level.isClientSide()) {
            return;
        }
        if (!Level.OVERWORLD.equals(level.dimension())) {
            return;
        }
        int x = (int) Math.floor(e.getX());
        int y = (int) Math.floor(e.getY());
        int z = (int) Math.floor(e.getZ());
        String harvest = Cell.of(x, y, z, LootJob.BEAST).render();
        drops.record(harvest, tick);
        System.out.println("[MatouBridge] loot recorded <" + harvest
                + "> at tick " + tick);
    }

    /**
     * Loot seal: table plus store beside the first wire's pack states,
     * pure decide, land one carrier per due drop, evict claimed. The
     * store-vs-job equality the etage-1 gate holds (up to the table
     * expansion) is re-checked loudly here: a live divergence (decided
     * != expanded claim) fails the tick instead of losing drops
     * silently. Passive without a wired pack (no table, no wires).
     */
    private void lootTick(Level level, long now) {
        if (lootTable == null || wires.isEmpty()) {
            return;
        }
        Map<MatouId, Object> states = new LinkedHashMap<MatouId, Object>(
                wires.get(0).states(now));
        states.putAll(LootSeal.seal(lootVocab, drops, lootTable,
                lootCount));
        Snapshot snap = ForgeSnapshot.snapshot(now, states);
        List<String> due = loot.decide(snap);
        for (String cell : due) {
            ForgeCells.BlockCell vol = ForgeCells.parseBlockCell(cell);
            dropCarrier(level, vol.x, vol.y, vol.z);
        }
        if (!due.isEmpty()) {
            System.out.println("[MatouBridge] loot dropped "
                    + due.size() + " carrier(s) at tick " + now);
        }
        List<String> claimed = drops.claimDue(now);
        if (!due.equals(expandClaim(claimed))) {
            throw new IllegalStateException("E_LOOT_SEAL:diverged <due="
                    + due + " claimed=" + claimed + "> at tick " + now);
        }
    }

    /**
     * Live table expansion: what the pure decision must equal for a
     * claimed harvest list (same shape as the etage-1 comparateur — the
     * gate and the tick share the rule, never a copy each).
     */
    private List<String> expandClaim(List<String> claimed) {
        List<String> out = new ArrayList<String>();
        for (String harvest : claimed) {
            int cut = harvest.indexOf(':');
            String head = harvest.substring(0, cut);
            String item = lootTable.get(harvest.substring(cut + 1));
            for (long c = 0; c < lootCount; c++) {
                out.add(head + ":" + item);
            }
        }
        return out;
    }

    /**
     * Loot landing: one vanilla-diamond carrier per due drop, beside the
     * vanilla drops (never replacing them). A refused spawn fails loudly
     * — a lost carrier is loot lost silently otherwise. The tick level
     * is always a {@code ServerLevel} on the server path; anything else
     * refuses loudly instead of casting blind.
     */
    private void dropCarrier(Level level, int x, int y, int z) {
        if (!(level instanceof ServerLevel)) {
            throw new IllegalStateException("E_LOOT_SPAWN:noworld <" + x
                    + "," + y + "," + z + "> (want a server level)");
        }
        ItemEntity carrier = new ItemEntity(level, x + 0.5, y + 0.5,
                z + 0.5, new ItemStack(Items.DIAMOND, 1));
        if (!((ServerLevel) level).addFreshEntity(carrier)) {
            throw new IllegalStateException("E_LOOT_SPAWN:refused <" + x
                    + "," + y + "," + z + ">");
        }
    }
}

package fr.iamacat.bridge.forge;

import fr.iamacat.bridge.ForgeCells;
import fr.iamacat.bridge.ForgeSnapshot;
import fr.iamacat.bridge.Packs;
import fr.iamacat.bridge.model.BeastModel;
import fr.iamacat.bridge.loot.DropStore;
import fr.iamacat.bridge.loot.LootSeal;
import fr.iamacat.bridge.spawn.SpawnSeal;
import fr.iamacat.bridge.spawn.SpawnStore;
import fr.iamacat.bridge.spike.MinedStore;
import fr.iamacat.bridge.spike.RepopJob;
import fr.iamacat.bridge.spike.RepopSeal;
import fr.iamacat.bridge.wire.OperatorPolicy;
import fr.iamacat.spi.Cell;
import fr.iamacat.spi.ContentPack;
import fr.iamacat.spi.LootStates;
import fr.iamacat.spi.MatouId;
import fr.iamacat.spi.MatouJob;
import fr.iamacat.spi.PolicyPack;
import fr.iamacat.spi.Snapshot;
import fr.iamacat.spi.SpawnStates;
import fr.iamacat.spi.StateVocabulary;
import fr.iamacat.spi.VocabularyPack;
import fr.iamacat.spi.hit.HitTester;
import fr.iamacat.spi.hit.RayHit;
import fr.iamacat.spi.hit.Vec3d;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
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
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.EntityGetter;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
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
 * <p>Repop spike (event-sourced, vanilla stone, zero registration — hub
 * decisions/REPOP_SPIKE.md, pure half byte-identical to the lead bridge):
 * stone breaks arrive on {@link #onBreak} (Forge break events, server
 * side, dim 0 only) into the bridge-owned {@link MinedStore}; every
 * server tick {@link #repopTick} seals the store standalone beside no
 * pack ({@link RepopSeal}, SPI untouched — the spike snapshot is
 * pack-independent) and the pure {@link RepopJob} decides what is due
 * back. Due cells land through {@link WorldCellSink}; a live
 * claim-vs-decision divergence fails the tick loudly
 * ({@code E_SPIKE_SEAL:diverged}, spike-tripwire shape). Repop delay
 * {@code REPOP_DELAY = 200}, stone {@code REPOP_BLOCK =
 * "minecraft:stone"} resolved fail-fast at setup
 * ({@code E_SPIKE_STONE:unknown}); other dims/blocks are out of spike
 * scope, never errors. New refusals stay spike-local ({@code E_SPIKE_*},
 * never in the {@code E_FORGE_*} parity catalog), so bridge parity holds
 * with behaviour intentionally 1201-only until proven.
 *
 * <p>Loot (event-sourced, hub decisions/LOOT.md, T1 any-kill-pays):
 * breaks of the operator wire blocks arrive on {@link #onHarvest} (Forge
 * break events, server side, dim 0 only) and mob kills on {@link #onKill}
 * (Forge living-drops events, same scope) into the bridge-owned
 * {@link DropStore}; every server tick {@link #lootTick} seals the store
 * plus the wired loot table beside the first wire's pack states
 * (the pack-served loot vocabulary, T3 registry — hub
 * {@code decisions/SPI_STATE_VOCABULARY.md}) and the pure pack-served
 * loot job decides what drops. The ore scope is the packs.cfg wire-block column
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
  * <p>Spawn (event-sourced, hub decisions/SPAWN.md, custom entity): the
  * pure pack-served spawn job reads the bridge-owned {@link SpawnStore} census
  * plus the wired per-mob spawn tables beside the first wire's pack states
  * (the pack-served spawn vocabulary, T3 registry -- hub
  * {@code decisions/SPI_STATE_VOCABULARY.md}) and decides budgeted spawns
  * per mob; due spawns land as the registered custom beast ({@link MatouEntity},
  * pig shape and renderer reused) carrying our loot table -- every beast
  * carrying its own short mob identity (per-mob tranche, hub
  * {@code decisions/VIRTUAL_HITBOXES.md}). Vanilla pigs
 * are a different species now: ignored by the census, never vetoed. A
 * live {@code slots != due} divergence fails the tick loudly
 * ({@code E_SPAWN_SEAL:diverged},
 * spike-tripwire shape). Census releases ride the kill hook below; an
 * {@code EntityJoinLevelEvent} veto holds the cap against beast joins the
 * budget never decided. Landing plus veto stay passive unless
 * {@code SPAWN=1} (same opt-in as the loot companion proof): always-on
 * landing would veto the loot proof's own beast once the census fills, so
 * the union and loot runs stay byte-for-byte spawn-free. The spawn
 * numbers are the content policy unless the operator {@code spawn.*}
 * wins (T2 operator-override tranche, hub decisions/SPAWN.md -- the
 * bridge transports the effective policy, it never owns a spawn
 * number). New refusals stay spawn-local ({@code E_SPAWN_*}, never in
 * the {@code E_FORGE_*} parity catalog), so bridge parity holds with
 * behaviour intentionally 1201-only until proven.
 *
 * <p>1.20.1 spawn spelling (measured against the pinned 47.2.0 bytes,
 * never ported blind from 1165): the join signal is
 * {@code EntityJoinLevelEvent} (the 1.16.5 {@code EntityJoinWorldEvent}
 * name does not exist on 1.20.1) with the entity on the
 * {@code EntityEvent} base behind {@code getEntity} and the level on the
 * subclass behind {@code getLevel}, {@code @Cancelable} for the past-cap
 * veto; the census poll is {@code EntityGetter.getEntitiesOfClass} (no
 * {@code loadedEntityList} field ships on 1.20.1 either); the census id
 * is {@code Entity.getId} (the 1.12 {@code getEntityId} name does not
 * port), the living check {@code Entity.isAlive}; landings position
 * through {@code Entity.moveTo} and sink through
 * {@code ServerLevel.addFreshEntity} (the loot sink); the victim is a
 * {@code new MatouEntity(Example1Mod.beastType(), level)} (the T1
 * {@code new Pig(EntityType.PIG, level)} shape is retired with the
 * species); the content hp lands through
 * {@code LivingEntity.getAttribute} on {@code Attributes.MAX_HEALTH}.
 * Coords and ids read through the declaring {@code Entity} type (owner
 * discipline -- hub decisions/LOOT.md).
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

    /** Spike-tuned repop delay: 200 ticks (10s at 20tps — human-visible
     * in a live run, far below proof windows). A constant, never a
     * default: the proof mines, waits, and watches this exact horizon. */
    static final long REPOP_DELAY = 200L;
    /** Spike scope: vanilla stone only. Other breaks are not the spike's
     * business (metadata/T.E. restore is an explicit non-goal). */
    static final String REPOP_BLOCK = "minecraft:stone";
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
    /** Spawn switch (DEV proof opt-in): landing plus veto stay passive
     * unless {@code SPAWN=1}, so union and loot runs never see a beast. */
    static final boolean SPAWN = "1".equals(System.getenv("SPAWN"));
    /** Spawn policy, sealed per mob from the content table at wire time
     * unless the operator {@code spawn.*} wins (hub decisions/SPAWN.md
     * operator-override tranche): effective cap, per-tick budget and y
     * band by qualified mob ref, in seal order. The bridge transports
     * them into the seal, it never owns a spawn number. The companion
     * mirrors the effective cap (see its SPAWN_CAP note). Global
     * overrides apply uniformly per mob -- the same specs ride every
     * mob's effectiveSpawn call, so an operator cap wins for all mobs
     * alike; per-mob override keys are a named follow-up, never smuggled
     * in here. */
    private final LinkedHashMap<String, Long> spawnCap =
            new LinkedHashMap<String, Long>();
    private final LinkedHashMap<String, Long> spawnBudget =
            new LinkedHashMap<String, Long>();
    private final LinkedHashMap<String, Long> spawnYMin =
            new LinkedHashMap<String, Long>();
    private final LinkedHashMap<String, Long> spawnYMax =
            new LinkedHashMap<String, Long>();
    /** Spec hp by qualified mob ref, in seal order (content-only, same
     * split as the weakspot multipliers — no operator key, never a quiet
     * knob). */
    private final LinkedHashMap<String, Long> spawnHp =
            new LinkedHashMap<String, Long>();
    /** Combat reach, sealed per mob from the content table at wire time
     * (hub decisions/VIRTUAL_HITBOXES.md combat-policy tranche):
     * effective eye-to-hitVec cutoff by short mob name, in seal order --
     * content reach unless the operator {@code combat.reach} wins, the
     * same specs riding every mob (uniform, like spawn — per-mob
     * override keys are a named follow-up). The bridge transports it
     * into the hook, it never owns a combat number. */
    private final LinkedHashMap<String, Double> combatReach =
            new LinkedHashMap<String, Double>();
    /** Tranche-1 census window (hub decisions/SPAWN.md): the poll box for
     * {@code reconcile} -- the proof world keeps beasts loaded near
     * spawn, wanderers past it sweep like unloaded ones. */
    private static final AABB CENSUS_BOX = new AABB(-512, -64, -512,
            512, 320, 512);

    private final List<PackWire> wires = new ArrayList<PackWire>();
    private final List<Packs.PackSpec> pending = new ArrayList<Packs.PackSpec>();
    private final MinedStore mined = new MinedStore();
    private final RepopJob repop = new RepopJob();
    private Block stone;
    private final DropStore drops = new DropStore();
    private MatouJob<List<String>> loot;
    private final SpawnStore census = new SpawnStore();
    private MatouJob<List<String>> spawn;
    private Map<String, String> lootTable;
    private String oreKind;
    private String beastKind;
    private StateVocabulary lootVocab;
    /** First sealed qualified mob ref (null when passive — Q1
     * cohabitation): the hooks gate on {@link #spawnMobs} being empty
     * (the same invariant), this stays for cells and logs where the
     * first mob is the sensible sole view. */
    private String spawnMob;
    /** Sealed mobs, short name to qualified ref, in seal order: the
     * per-mob dispatch truth (census cells, seals and landings key off
     * it; empty means passive). */
    private final LinkedHashMap<String, String> spawnMobs =
            new LinkedHashMap<String, String>();
    private StateVocabulary spawnVocab;
    /** Owned content path (shared with the future spawn wire — same file
     * funds both tables, parsed once here). */
    private String ownedPath;
    private long tick;

    public MatouBridgeMod() {
        MinecraftForge.EVENT_BUS.register(this);
        File cfg = new File(PACKS_PATH);
        if (cfg.isFile()) {
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
        }
        // Always bound (even packless): the repop spike is
        // pack-independent (vanilla stone, standalone snapshot), so its
        // stone resolve must land with or without a packs.cfg — the lead
        // bridge resolves it before the config check for the same reason.
        FMLJavaModLoadingContext.get().getModEventBus().addListener(
                (FMLCommonSetupEvent event) -> bindPending());
    }

    /**
     * Setup-time bind: the stone resolve lands first (fail-fast
     * {@code E_SPIKE_STONE:unknown} — a spike without its block refuses
     * loudly, never records against null), then the deferred-registry
     * fill already landed (one loading state ago), so custom names
     * resolve here. A half-bound wire never ticks — refusal is loud, at
     * setup, never silent.
     */
    private void bindPending() {
        ResourceLocation stoneId = new ResourceLocation(REPOP_BLOCK);
        // Presence first: getValue returns the registry default (air)
        // for unknown names, never null (same probe as the loot ore
        // resolve below).
        if (!ForgeRegistries.BLOCKS.containsKey(stoneId)) {
            throw new IllegalArgumentException("E_SPIKE_STONE:unknown <"
                    + REPOP_BLOCK + ">");
        }
        stone = ForgeRegistries.BLOCKS.getValue(stoneId);
        if (stone == null) {
            throw new IllegalArgumentException("E_SPIKE_STONE:unknown <"
                    + REPOP_BLOCK + ">");
        }
        for (Packs.PackSpec spec : pending) {
            wires.add(PackWire.bind(spec));
        }
        wireLoot(pending);
        wireSpawn(pending);
        wireCombat(pending);
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
      * T4 pack-driven policy (hub
      * {@code decisions/SPI_STATE_VOCABULARY.md}): tables, jobs and harvest
      * kinds come from the first wire's reflectively loaded pack at wire
      * time (parse-once, never on the tick path — the pack sealed them
      * beside its states), so the forge wire carries no content import.
      * A pack serving no policy refuses loudly — wiring numbers the pack
      * never sealed would be a silent default.
      */
    private PolicyPack policy(String code) {
        if (wires.isEmpty()) {
            throw new IllegalStateException(code + ":nowire (want a "
                    + "wired pack to serve the policy)");
        }
        ContentPack pack = wires.get(0).pack();
        if (!(pack instanceof PolicyPack)) {
            throw new IllegalArgumentException(code + ":nopolicy <"
                    + pack.getClass().getName() + "> (pack serves no "
                    + "loot/spawn/combat policy)");
        }
        return (PolicyPack) pack;
    }

    /**
     * Loot wiring: one table per bridge from the first wire's pack policy
     * (parsed once at pack wire time, like registration — never on the
     * tick path), the content {@code drop_count} unless the operator
     * {@code loot.count} wins, and the ore scope from the operator
     * wire-block column (T2 operator-override tranche — no bridge constant
     * names a loot block). Harvest kinds come from the same policy (never
     * content literals here): a table missing a served kind refuses
     * loudly — an unpaid kind would be a silent no-drop. No owned file
     * anywhere means loot stays passive (Q1 cohabitation): the hooks gate
     * on the null table. Several distinct owned files refuse loudly —
     * silent table picks are defaults. The ore resolve probes the registry
     * (presence first — {@code getValue} returns air for unknown names,
     * never null).
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
        PolicyPack policy = policy("E_LOOT_POLICY");
        oreKind = policy.lootOreKind();
        beastKind = policy.lootBeastKind();
        lootTable = policy.lootDrops();
        if (!lootTable.containsKey(oreKind)
                || !lootTable.containsKey(beastKind)) {
            throw new IllegalArgumentException("E_LOOT_TABLE:kind <"
                    + new ArrayList<String>(lootTable.keySet())
                    + "> (want <" + oreKind + "> + <" + beastKind + ">)");
        }
        loot = policy.lootJob();
        lootCount = OperatorPolicy.effectiveLootCount(policy.lootCount(),
                specs);
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
        for (String dropRef : lootTable.values()) {
            if (resolveItem(dropRef) == null) {
                throw new IllegalArgumentException(
                        "E_LOOT_ITEM:unknown <" + dropRef + ">");
            }
        }
    }

    /**
     * Spawn wiring: the sealed mobs plus their spec hp plus the
     * effective spawn policy from the first wire's pack policy (parsed
     * once at pack wire time, like registration — never on the tick
     * path): content cap/budget/band per mob unless the operator
     * {@code spawn.*} wins (T2 operator-override tranche, applied with
     * the same specs per mob — a global override wins uniformly, never
     * per mob). The hp lands on each beast's max-health attribute at
     * every landing (hp tranche, hub decisions/SPAWN.md) — a spec field
     * with no live reader would be a silent default; the same holds for
     * the effective policy. No owned file anywhere means spawn stays
     * passive (Q1 cohabitation): the hooks gate on the empty mob map.
     *
     * <p>Per-mob tranche (hub {@code decisions/VIRTUAL_HITBOXES.md}):
     * shorts from {@code PolicyPack.spawnMobs()} qualify into cell refs
     * through {@link #contentNamespace} (same-file namespace rule).
     */
    private void wireSpawn(List<Packs.PackSpec> specs) {
        if (ownedPath == null) {
            return;
        }
        spawnVocab = vocabulary(SpawnStates.SCOPE, "E_SPAWN_SEAL");
        PolicyPack policy = policy("E_SPAWN_POLICY");
        spawn = policy.spawnJob();
        String ns = contentNamespace();
        for (String shortMob : policy.spawnMobs()) {
            long[] eff = OperatorPolicy.effectiveSpawn(
                    policy.spawnCap(shortMob),
                    policy.spawnBudget(shortMob),
                    policy.spawnYMin(shortMob),
                    policy.spawnYMax(shortMob), specs);
            String qualified = ns + ":" + shortMob;
            if (spawnMob == null) {
                spawnMob = qualified;
            }
            spawnMobs.put(shortMob, qualified);
            spawnHp.put(qualified, Long.valueOf(policy.spawnHp(shortMob)));
            spawnCap.put(qualified, Long.valueOf(eff[0]));
            spawnBudget.put(qualified, Long.valueOf(eff[1]));
            spawnYMin.put(qualified, Long.valueOf(eff[2]));
            spawnYMax.put(qualified, Long.valueOf(eff[3]));
        }
        List<String> over = new ArrayList<String>();
        if (OperatorPolicy.present(specs, OperatorPolicy.SPAWN_CAP)) {
            over.add("cap");
        }
        if (OperatorPolicy.present(specs, OperatorPolicy.SPAWN_BUDGET)) {
            over.add("budget");
        }
        if (OperatorPolicy.present(specs, OperatorPolicy.SPAWN_Y_MIN)) {
            over.add("y_min");
        }
        if (OperatorPolicy.present(specs, OperatorPolicy.SPAWN_Y_MAX)) {
            over.add("y_max");
        }
        String spawnNote = over.isEmpty() ? ""
                : " overridden <" + join(over) + ">";
        if (spawnMobs.size() == 1) {
            System.out.println("[MatouBridge] spawn wired <" + spawnMob
                    + "> hp <" + spawnHp.get(spawnMob) + "> cap <"
                    + spawnCap.get(spawnMob) + "> budget <"
                    + spawnBudget.get(spawnMob) + "> y <"
                    + spawnYMin.get(spawnMob) + ".."
                    + spawnYMax.get(spawnMob) + ">" + spawnNote);
        } else {
            System.out.println("[MatouBridge] spawn wired <"
                    + spawnShapes() + ">" + spawnNote);
        }
    }

    /**
     * Per-mob spawn wire shapes ({@code {short hp <h> cap <c> budget
     * <b> y <min..max>}}, seal order, comma-joined).
     */
    private String spawnShapes() {
        List<String> rows = new ArrayList<String>();
        for (Map.Entry<String, String> e : spawnMobs.entrySet()) {
            String q = e.getValue();
            rows.add("{" + e.getKey() + " hp <" + spawnHp.get(q)
                    + "> cap <" + spawnCap.get(q) + "> budget <"
                    + spawnBudget.get(q) + "> y <" + spawnYMin.get(q)
                    + ".." + spawnYMax.get(q) + ">}");
        }
        return join(rows);
    }

    /**
     * Combat wiring: the per-mob weakspot tables plus the reach
     * attributes from the first wire's pack policy (parsed once at pack
     * wire time, like loot/spawn — never on the tick path). The tables
     * seal into the bridge model holder the beast reads at hit time;
     * each mob's effective reach (content reach unless the operator
     * {@code combat.reach} wins — same uniform rule as spawn, the same
     * specs riding every mob) lands in the hook's per-mob map
     * (reach-override tranche, hub
     * decisions/VIRTUAL_HITBOXES.md; weakspot multipliers stay
     * content-only, same split as {@code spawnHp}). No owned file
     * anywhere means combat stays passive (Q1 cohabitation): the seal
     * stays empty and any hit-time read refuses loudly instead of
     * defaulting 1.0x.
     */
    private void wireCombat(List<Packs.PackSpec> specs) {
        if (ownedPath == null) {
            return;
        }
        PolicyPack policy = policy("E_COMBAT_POLICY");
        Map<String, Map<String, Float>> perMobWeakspots =
                new LinkedHashMap<String, Map<String, Float>>();
        Map<String, Double> perMobReach =
                new LinkedHashMap<String, Double>();
        for (String mob : policy.combatMobs()) {
            perMobWeakspots.put(mob, policy.combatWeakspots(mob));
            perMobReach.put(mob, Double.valueOf(policy.combatReach(mob)));
            combatReach.put(mob, Double.valueOf(
                    OperatorPolicy.effectiveCombatReach(
                            policy.combatReach(mob), specs)));
        }
        BeastModel.sealCombat(perMobWeakspots, perMobReach);
        String combatNote = OperatorPolicy.present(specs,
                OperatorPolicy.COMBAT_REACH) ? " overridden <combat.reach>"
                : "";
        if (perMobWeakspots.size() == 1) {
            System.out.println("[MatouBridge] combat wired <"
                    + policy.combatWeakspots() + "> reach <"
                    + combatReach.values().iterator().next() + ">"
                    + combatNote);
        } else {
            System.out.println("[MatouBridge] combat wired <"
                    + combatShapes(perMobWeakspots) + "> reach <"
                    + reachShapes() + ">" + combatNote);
        }
    }

    /**
     * Per-mob combat table shapes ({@code {mob={bone=mult, ...}}}, seal
     * order, comma-joined).
     */
    private static String combatShapes(
            Map<String, Map<String, Float>> perMobWeakspots) {
        List<String> rows = new ArrayList<String>();
        for (Map.Entry<String, Map<String, Float>> e
                : perMobWeakspots.entrySet()) {
            rows.add("{" + e.getKey() + "=" + e.getValue() + "}");
        }
        return join(rows);
    }

    /**
     * Per-mob effective reach shapes ({@code {mob=reach}}, seal order,
     * comma-joined).
     */
    private String reachShapes() {
        List<String> rows = new ArrayList<String>();
        for (Map.Entry<String, Double> e : combatReach.entrySet()) {
            rows.add("{" + e.getKey() + "=" + e.getValue() + "}");
        }
        return join(rows);
    }

    /** Comma join for the override log suffix (Java 8, no extra dep). */
    private static String join(List<String> parts) {
        StringBuilder out = new StringBuilder();
        for (String p : parts) {
            if (out.length() > 0) {
                out.append(',');
            }
            out.append(p);
        }
        return out.toString();
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
        repopTick(event.level, tick);
        lootTick(event.level, tick);
        spawnTick(event.level, tick);
        tick++;
    }

    /**
     * Spike record: a server-side dim-0 stone break becomes a mined cell
     * at the last server tick (same clock the per-tick seal reads — both
     * run on the server thread). Client-side echoes (isClientSide) are
     * ignored: the server fires its own event for the same break. Other
     * dims and non-stone blocks are out of spike scope, never errors
     * (hub decisions/REPOP_SPIKE.md).
     */
    @SubscribeEvent
    public void onBreak(BlockEvent.BreakEvent event) {
        LevelAccessor w = event.getLevel();
        if (!(w instanceof ServerLevel)) {
            return;
        }
        ServerLevel level = (ServerLevel) w;
        // Owner discipline (measured live on 1201 loot: NoSuchMethodError
        // ServerLevel.isClientSide — Reobf maps the exact bytecode owner,
        // so inherited vanilla members go through the declaring Level
        // type, never the narrowed ServerLevel — same upcast as onHarvest).
        Level lvl = level;
        if (lvl.isClientSide()) {
            return;
        }
        if (!Level.OVERWORLD.equals(lvl.dimension())) {
            return;
        }
        // Owner discipline (hub decisions/LOOT.md): coords go through the
        // declaring Vec3i type and the block through the declaring
        // BlockStateBase type — the hierarchy walk only maps the exact
        // bytecode owner.
        BlockBehaviour.BlockStateBase s = event.getState();
        if (s.getBlock() != stone) {
            return;
        }
        Vec3i p = event.getPos();
        String cell = Cell.of(p.getX(), p.getY(), p.getZ(),
                REPOP_BLOCK).render();
        mined.record(cell, tick);
        System.out.println("[MatouBridge] spike recorded <" + cell
                + "> at tick " + tick);
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
        // Owner discipline (measured live on 1201: NoSuchMethodError
        // ServerLevel.isClientSide — Reobf maps the exact bytecode owner,
        // so inherited vanilla members go through the declaring Level
        // type, never the narrowed ServerLevel — same upcast as onKill).
        Level lvl = level;
        if (lvl.isClientSide()) {
            return;
        }
        if (!Level.OVERWORLD.equals(lvl.dimension())) {
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
                oreKind).render();
        drops.record(harvest, tick);
        System.out.println("[MatouBridge] loot recorded <" + harvest
                + "> at tick " + tick);
    }

    /**
     * Loot record: a server-side dim-0 mob kill becomes a beast harvest
     * at the entity's block coords. T1 single-entry scope (hub
     * decisions/LOOT.md): every kill pays the one entry — per-mob
     * filtering is a re-opener, never a quiet filter here. (The census
     * species narrowed with custom-entity registration; the companion
     * kills the registered beast.)
     *
     * <p>Owner discipline (measured live on 1710: NoSuchFieldError
     * worldObj): inherited vanilla members are read through the declaring
     * stub type ({@code Entity}), never through the event's
     * {@code LivingEntity} — hence the upcast local below (the
     * hierarchy walk only maps the exact bytecode owner).
     *
     * <p>A dead beast also leaves the spawn census (hub
     * decisions/SPAWN.md): landings are recorded under their entity id,
     * the kill hook releases them. Unknown ids are not ours (a vanilla
     * beast, or the loot proof's own pig, dies without ever being
     * recorded): false, never a refusal.
     */
    @SubscribeEvent
    public void onKill(LivingDropsEvent event) {
        LivingEntity landed = event.getEntity();
        Entity body = landed;
        if (!spawnMobs.isEmpty() && body instanceof MatouEntity) {
            // Owner discipline (hub decisions/LOOT.md): the id goes through
            // the declaring stub type -- body is already Entity-typed, so
            // the bytecode owner is Entity.
            census.release(Integer.toString(body.getId()));
        }
        if (lootTable == null) {
            return;
        }
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
        String harvest = Cell.of(x, y, z, beastKind).render();
        drops.record(harvest, tick);
        System.out.println("[MatouBridge] loot recorded <" + harvest
                + "> at tick " + tick);
    }

    /**
     * Combat hook: a server-side dim-0 hurt on the registered beast
     * resolves the struck bone through the pure SPI ray-test (hub
     * decisions/VIRTUAL_HITBOXES.md, server weakspot hook) and scales
     * the vanilla amount by the bone weakspot multiplier (head 2x).
     *
     * <p>Server-authoritative by construction: runs on the server thread
     * only (client echoes ignored — the server fires its own event for
     * the same hurt), re-derives eye/look from the live attacker (never
     * trusts a packet bone claim — there is no packet in this tranche),
     * and falls back to vanilla silently in the three non-ray cases:
     * environmental damage (no attacker entity to ray from), a hurt the
     * coarse vanilla box caught but no bone box covers (glancing —
     * vanishingly rare on the 2-bone beast, never a refusal), and any
     * non-beast target (not our species). Corrupt attacker state (NaN
     * eye/look) refuses loudly out of the SPI constructors
     * ({@code E_HIT_VEC:nan} / {@code E_HIT_DIR:zero}), never a
     * defaulted multiplier.
     *
     * <p>Non-goal (named re-opener, never smuggled in): the vanilla
     * pre-rejection (BUG-042 — an origin-distance veto that never fires
     * this event) stays vanilla; this hook only refines hurts vanilla
     * delivers.
     *
     * <p>Per-mob tranche (hub {@code decisions/VIRTUAL_HITBOXES.md}):
     * the ray-test cutoff is the victim's per-mob effective reach from
     * the wire-time map (content reach unless the operator
     * {@code combat.reach} wins); the multiplier dispatches per mob
     * through the victim's weakspot table. An unsealed mob refuses
     * loudly — never a defaulted reach. Passive without a wired pack
     * (same silent fallback as the unwired reach before).
     *
     * <p>47.2.0 shape (measured via server.txt + joined.tsrg v2 + javap,
     * never ported blind from 1165): the hurt entity lives on the
     * {@code LivingEvent} base behind {@code getEntity()} as
     * {@code LivingEntity} (the 1.16.5 {@code getEntityLiving} shape does
     * not port), the source behind {@code LivingHurtEvent.getSource()},
     * the true attacker behind {@code DamageSource.getEntity()} (the
     * direct entity is the projectile, not the author); eye/look ride
     * the declaring {@code Entity} type as {@code Vec3}
     * ({@code getEyePosition}/{@code getLookAngle} — owner discipline,
     * hub decisions/LOOT.md; no height arithmetic, the eye position is
     * direct) with components on the declaring {@code Vec3} fields; the
     * dim gate stays the {@code OVERWORLD} key.
     */
    @SubscribeEvent
    public void onHurt(LivingHurtEvent event) {
        LivingEntity hurt = event.getEntity();
        if (!(hurt instanceof MatouEntity)) {
            return;
        }
        Entity body = hurt;
        Level level = body.level();
        if (level.isClientSide()) {
            return;
        }
        if (!Level.OVERWORLD.equals(level.dimension())) {
            return;
        }
        if (combatReach.isEmpty()) {
            return;
        }
        Entity attacker = event.getSource().getEntity();
        if (attacker == null) {
            return;
        }
        String shortMob = ((MatouEntity) hurt).mobOrFirst();
        Double at = combatReach.get(shortMob);
        if (at == null) {
            throw new IllegalStateException("E_COMBAT_WIRE:unmapped mob <"
                    + shortMob + "> (want one of " + combatReach.keySet()
                    + " — sealed mobs only, never defaulted)");
        }
        Vec3 eye = attacker.getEyePosition();
        Vec3 look = attacker.getLookAngle();
        Vec3d origin = new Vec3d(eye.x, eye.y, eye.z);
        Vec3d dir = new Vec3d(look.x, look.y, look.z);
        RayHit hit = HitTester.test((MatouEntity) hurt, origin, dir,
                at.doubleValue());
        if (hit == null) {
            return;
        }
        float before = event.getAmount();
        float mult = ((MatouEntity) hurt).weakspotMultiplier(hit.boneName);
        event.setAmount(before * mult);
        System.out.println("[MatouBridge] combat resolved <bone="
                + hit.boneName + " mult=" + mult + " dmg=" + before + "->"
                + event.getAmount() + ">");
    }

    /**
     * Spawn census: every server-side dim-0 beast join is recorded under
     * its entity id -- own landings (which also fire this event, recorded
     * again here idempotently) and foreign beast joins alike. Recording
     * every join the veto lets through is what keeps the census equal to
     * the living reality: a join past its mob's cap is refused instead
     * (the budget never decided it), anything else joins the census the
     * pure budget counts. Custom-entity scope (hub decisions/SPAWN.md):
     * the census is the registered beast — vanilla pigs are a different
     * species now (ignored, never vetoed, never counted).
     * Passive without a wired mob, and passive unless {@code SPAWN=1}
     * (the union and loot runs never see a beast, recorded or
     * otherwise).
     *
     * <p>Per-mob tranche (hub {@code decisions/VIRTUAL_HITBOXES.md}):
     * the veto counts the census entries carrying the joining entity's
     * own mob against that mob's cap (an unsealed mob refuses loudly);
     * the recorded cell carries the entity's qualified mob ref.
     *
     * <p>1.20.1 shape (measured via javap, never the 1.16.5 shape): the
     * joined entity lives on the {@code EntityEvent} base behind
     * {@code getEntity()}, the level on the subclass behind
     * {@code getLevel()} -- and the level arrives as a
     * {@code LevelAccessor}-shaped carrier on some paths, narrowed to
     * {@code ServerLevel} before any read like the harvest hook.
     */
    @SubscribeEvent
    public void onJoin(EntityJoinLevelEvent event) {
        if (!SPAWN || spawnMobs.isEmpty()) {
            return;
        }
        if (!(event.getLevel() instanceof ServerLevel)) {
            return;
        }
        ServerLevel level = (ServerLevel) event.getLevel();
        Level lvl = level;
        if (lvl.isClientSide()) {
            return;
        }
        if (!Level.OVERWORLD.equals(lvl.dimension())) {
            return;
        }
        if (!(event.getEntity() instanceof MatouEntity)) {
            return;
        }
        // Owner discipline (hub decisions/LOOT.md): the id and coords go
        // through the declaring stub type (Entity), and the joined entity
        // resolves through its declaring base (EntityEvent), never
        // through the beast or the join subclass.
        Entity body = event.getEntity();
        MatouEntity beast = (MatouEntity) body;
        String qualified = qualifiedMob(beast);
        int count = 0;
        for (String cell : census.sealed().values()) {
            if (cellMob(cell).equals(qualified)) {
                count++;
            }
        }
        long cap = spawnCap.get(qualified).longValue();
        if (count >= cap) {
            event.setCanceled(true);
            System.out.println("[MatouBridge] spawn vetoed <"
                    + beast.mob() + "> at tick "
                    + tick + " (census at cap " + cap + ")");
            return;
        }
        int x = (int) Math.floor(body.getX());
        int y = (int) Math.floor(body.getY());
        int z = (int) Math.floor(body.getZ());
        String cell = Cell.of(x, y, z, qualified).render();
        census.record(Integer.toString(body.getId()), cell, tick);
        System.out.println("[MatouBridge] spawn joined <" + cell
                + "> at tick " + tick);
    }

    /**
     * Spawn seal: census plus per-mob tables, caps, budgets and bands
     * beside the first wire's pack states, pure decide, land one beast
     * per due slot, record every landing. The census is reconciled first
     * (see {@link #reconcile}): the join event misses silent paths
     * (measured live on 1710: a natural grass spawn never fired it and
     * breached the cap), so the sealed census is the polled living
     * reality, never the event trail alone. The budgeted slots the
     * etage-1 gate holds equal to the job decision size are re-checked
     * loudly here per mob, summed: a live divergence (slots != decided)
     * fails the tick instead of spawning off-budget silently. Passive
     * without a wired pack or mob, and passive unless {@code SPAWN=1}.
     *
     * <p>Per-mob tranche (hub {@code decisions/VIRTUAL_HITBOXES.md}):
     * due cells already carry their mob ({@code x,y,z:mob}) — each
     * landing lands its cell's mob.
     */
    private void spawnTick(Level level, long now) {
        if (!SPAWN || spawnMobs.isEmpty() || wires.isEmpty()) {
            return;
        }
        reconcile(level, now);
        Map<MatouId, Object> states = new LinkedHashMap<MatouId, Object>(
                wires.get(0).states(now));
        List<String> table = new ArrayList<String>(spawnMobs.values());
        Map<String, List<Long>> bands =
                new LinkedHashMap<String, List<Long>>();
        for (String qualified : table) {
            bands.put(qualified, Arrays.asList(
                    spawnYMin.get(qualified), spawnYMax.get(qualified)));
        }
        states.putAll(SpawnSeal.seal(spawnVocab, census, table,
                spawnCap, spawnBudget, bands));
        Snapshot snap = ForgeSnapshot.snapshot(now, states);
        List<String> due = spawn.decide(snap);
        Map<String, String> sealed = census.sealed();
        int slots = 0;
        for (String qualified : table) {
            int count = 0;
            for (String cell : sealed.values()) {
                if (cellMob(cell).equals(qualified)) {
                    count++;
                }
            }
            slots += census.slotsDue(count,
                    spawnCap.get(qualified).intValue(),
                    spawnBudget.get(qualified).intValue());
        }
        if (slots != due.size()) {
            throw new IllegalStateException("E_SPAWN_SEAL:diverged <slots="
                    + slots + " due=" + due + "> at tick " + now);
        }
        for (String cell : due) {
            ForgeCells.BlockCell pad = ForgeCells.parseBlockCell(cell);
            landBeast(level, pad.x, pad.y, pad.z, cell, pad.block, now);
        }
        if (!due.isEmpty()) {
            System.out.println("[MatouBridge] spawn landed "
                    + due.size() + " beast(s) at tick " + now);
        }
    }

    /**
     * Spawn reconcile: adopt every living dim-0 beast the census does not
     * know, sweep every census id no longer living. The join event stays
     * (prompt record plus the past-cap veto), but it misses silent paths
     * -- measured live on 1710: a natural grass spawn never fired it, a
     * landing-only census undercounted reality and the fifth living beast
     * breached the cap loudly in the proof. The poll is the census of
     * record; events are the fast path. Adopted cells carry the entity's
     * own qualified mob ref at the current pos (per-mob tranche, hub
     * {@code decisions/VIRTUAL_HITBOXES.md} — never a single wired mob).
     * Custom-entity scope: beasts outside the
     * census window sweep (see CENSUS_BOX) -- the proof world keeps them
     * loaded near spawn; a rejoin re-adopts next tick.
     *
     * <p>Owner discipline (hub decisions/LOOT.md): the poll goes through
     * the declaring {@code EntityGetter} type and inherited vanilla
     * members through {@code Entity}, never through the beast.
     */
    private void reconcile(Level level, long now) {
        Map<String, String> living = new LinkedHashMap<String, String>();
        EntityGetter getter = (EntityGetter) level;
        List<MatouEntity> found = getter.getEntitiesOfClass(
                MatouEntity.class, CENSUS_BOX);
        for (MatouEntity beast : found) {
            Entity body = beast;
            if (!body.isAlive()) {
                continue;
            }
            int x = (int) Math.floor(body.getX());
            int y = (int) Math.floor(body.getY());
            int z = (int) Math.floor(body.getZ());
            living.put(Integer.toString(body.getId()),
                    Cell.of(x, y, z,
                            qualifiedMob((MatouEntity) body)).render());
        }
        for (Map.Entry<String, String> e : living.entrySet()) {
            if (!census.sealed().containsKey(e.getKey())) {
                census.record(e.getKey(), e.getValue(), now);
                System.out.println("[MatouBridge] spawn adopted <"
                        + e.getValue() + "> at tick " + now);
            }
        }
        for (String id : census.sealed().keySet()) {
            if (!living.containsKey(id)) {
                census.release(id);
                System.out.println("[MatouBridge] spawn swept <" + id
                        + "> at tick " + now);
            }
        }
    }

    /**
     * Spawn landing: one registered beast per due slot at the decided pad,
     * recorded into the census under its entity id. The content hp lands
     * on the beast's max-health attribute before the spawn (hp tranche, hub
     * decisions/SPAWN.md) and the read-back is tripwired per mob: a beast
     * that does not carry its spec hp fails the tick instead of roaming
     * underpowered silently. A refused spawn fails loudly -- an unrecorded
     * beast is census drift silently otherwise. The tick level is always
     * a {@code ServerLevel} on the server path; anything else refuses
     * loudly instead of casting blind (same shape as the loot carrier).
     *
     * <p>Per-mob tranche (hub {@code decisions/VIRTUAL_HITBOXES.md}):
     * the hp comes from the due cell's own mob map entry (an unsealed
     * mob refuses loudly), and the short mob identity is sealed on the
     * entity before the spawn.
     *
     * <p>Owner discipline (hub decisions/LOOT.md): inherited vanilla
     * members go through the declaring stub types ({@code Entity},
     * {@code LivingEntity}), never through the beast.
     */
    private void landBeast(Level level, int x, int y, int z, String cell,
            String qualifiedMob, long now) {
        if (!(level instanceof ServerLevel)) {
            throw new IllegalStateException("E_SPAWN_SPAWN:noworld <" + x
                    + "," + y + "," + z + "> (want a server level)");
        }
        Long hp = spawnHp.get(qualifiedMob);
        if (hp == null) {
            throw new IllegalStateException("E_SPAWN_HP:unknown mob <"
                    + qualifiedMob + "> (want one of " + spawnHp.keySet()
                    + " — sealed mobs only, never defaulted)");
        }
        MatouEntity beast = new MatouEntity(Example1Mod.beastType(),
                level);
        beast.setMob(shortName(qualifiedMob));
        Entity body = beast;
        LivingEntity living = beast;
        AttributeInstance attr = living.getAttribute(Attributes.MAX_HEALTH);
        attr.setBaseValue(hp.doubleValue());
        living.setHealth(hp.floatValue());
        if (living.getMaxHealth() != hp.floatValue()) {
            throw new IllegalStateException("E_SPAWN_HP:diverged <want="
                    + hp + " got=" + living.getMaxHealth()
                    + "> at tick " + now);
        }
        body.moveTo(x + 0.5, y, z + 0.5, 0.0f, 0.0f);
        if (!((ServerLevel) level).addFreshEntity(beast)) {
            throw new IllegalStateException("E_SPAWN_SPAWN:refused <" + x
                    + "," + y + "," + z + ">");
        }
        census.record(Integer.toString(body.getId()), cell, now);
    }

    /**
     * Qualified ref of an entity's mob (adopts with its note when unset);
     * afterwards {@code beast.mob()} is non-null. Loud on an unsealed
     * mob — a census entry nobody budgeted would be silent drift.
     */
    private String qualifiedMob(MatouEntity beast) {
        String shortMob = beast.mobOrFirst();
        String qualified = spawnMobs.get(shortMob);
        if (qualified == null) {
            throw new IllegalStateException("E_SPAWN_MOB:unknown mob <"
                    + shortMob + "> (want one of " + spawnMobs.keySet()
                    + " — sealed mobs only, never defaulted)");
        }
        return qualified;
    }

    /**
     * Short content name past the first colon (same split as
     * {@code Example1Mod.registerBeast} — one convention, not two).
     */
    private static String shortName(String qualifiedMob) {
        if (qualifiedMob == null) {
            throw new NullPointerException("E_SPAWN_MOB:null mob "
                    + "(want a \"ns:mob\" content ref)");
        }
        int colon = qualifiedMob.indexOf(':');
        if (colon < 0 || colon + 1 >= qualifiedMob.length()) {
            throw new IllegalArgumentException("E_SPAWN_MOB:type <"
                    + qualifiedMob + "> (want a \"ns:mob\" content ref)");
        }
        return qualifiedMob.substring(colon + 1);
    }

    /**
     * Content namespace for mob refs, read off the wired loot drop refs:
     * the parser enforces one {@code namespace} per owned file and the
     * spawn wire shares the loot wire's file (one table per bridge), so
     * the drop refs carry the mobs' namespace. Loud when the loot wire
     * never ran or a drop ref is bare — a guessed namespace would be a
     * silent default. (A qualified spawn-table view through
     * {@code PolicyPack} would remove this loot-to-spawn read — named
     * follow-up, zero behaviour difference.)
     */
    private String contentNamespace() {
        if (lootTable == null || lootTable.isEmpty()) {
            throw new IllegalStateException("E_SPAWN_MOB:nowire (want "
                    + "the loot table wired — one owned file funds both "
                    + "tables)");
        }
        String drop = lootTable.values().iterator().next();
        if (drop == null) {
            throw new IllegalStateException("E_SPAWN_MOB:null drop "
                    + "(want a \"ns:item\" drop ref to read the file "
                    + "namespace from)");
        }
        int colon = drop.indexOf(':');
        if (colon <= 0) {
            throw new IllegalArgumentException("E_SPAWN_MOB:type <"
                    + drop + "> (want a \"ns:item\" drop ref to read "
                    + "the file namespace from)");
        }
        return drop.substring(0, colon);
    }

    /**
     * Census-cell mob suffix (the qualified ref past the first colon —
     * same cut as the job's census rule); a colon-less cell matches no
     * sealed mob.
     */
    private static String cellMob(String cell) {
        int cut = cell.indexOf(':');
        return cut < 0 ? cell : cell.substring(cut + 1);
    }

    /**
     * Spike seal: store sealed standalone (pack-independent), pure
     * decide, land due cells, evict claimed. The store-vs-job equality
     * the etage-1 gate holds is re-checked loudly here: a live
     * divergence (claimed != due) fails the tick instead of leaking
     * mined cells silently.
     */
    private void repopTick(Level level, long now) {
        Map<MatouId, Object> states = RepopSeal.seal(mined, REPOP_DELAY);
        Snapshot snap = ForgeSnapshot.snapshot(now, states);
        List<String> due = repop.decide(snap);
        if (!due.isEmpty()) {
            ForgeCells.applyCells(due,
                    new WorldCellSink(level, 0, stone));
            System.out.println("[MatouBridge] spike repopped "
                    + due.size() + " cell(s) at tick " + now);
        }
        List<String> claimed = mined.claimDue(now, REPOP_DELAY);
        if (!claimed.equals(due)) {
            throw new IllegalStateException("E_SPIKE_SEAL:diverged <due="
                    + due + " claimed=" + claimed + "> at tick " + now);
        }
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
            dropCarrier(level, vol.x, vol.y, vol.z, vol.block);
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
     * Loot landing: one registered-item carrier per due drop, beside the
     * vanilla drops (never replacing them). A refused spawn fails loudly
     * — a lost carrier is loot lost silently otherwise. The tick level
     * is always a {@code ServerLevel} on the server path; anything else
     * refuses loudly instead of casting blind.
     */
    private void dropCarrier(Level level, int x, int y, int z, String itemRef) {
        if (!(level instanceof ServerLevel)) {
            throw new IllegalStateException("E_LOOT_SPAWN:noworld <" + x
                    + "," + y + "," + z + "> (want a server level)");
        }
        Item item = resolveItem(itemRef);
        if (item == null) {
            throw new IllegalStateException("E_LOOT_ITEM:unknown <" + itemRef + ">");
        }
        ItemEntity carrier = new ItemEntity(level, x + 0.5, y + 0.5,
                z + 0.5, new ItemStack(item, 1));
        if (!((ServerLevel) level).addFreshEntity(carrier)) {
            throw new IllegalStateException("E_LOOT_SPAWN:refused <" + x
                    + "," + y + "," + z + ">");
        }
    }

    static Item resolveItem(String ref) {
        if (ref == null || ref.isEmpty()) {
            return null;
        }
        int colon = ref.indexOf(':');
        if (colon < 0) {
            ResourceLocation id = new ResourceLocation("example1:" + ref);
            return ForgeRegistries.ITEMS.containsKey(id)
                    ? ForgeRegistries.ITEMS.getValue(id) : null;
        }
        ResourceLocation id = new ResourceLocation(ref);
        if (ForgeRegistries.ITEMS.containsKey(id)) {
            return ForgeRegistries.ITEMS.getValue(id);
        }
        String prefix = ref.substring(0, colon);
        String name = ref.substring(colon + 1);
        int dot = prefix.indexOf('.');
        if (dot > 0) {
            String modId = prefix.substring(0, dot);
            ResourceLocation alt = new ResourceLocation(modId + ":" + name);
            if (ForgeRegistries.ITEMS.containsKey(alt)) {
                return ForgeRegistries.ITEMS.getValue(alt);
            }
        }
        return null;
    }
}

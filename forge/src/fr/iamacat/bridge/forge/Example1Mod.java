package fr.iamacat.bridge.forge;

import fr.iamacat.bridge.Packs;
import java.io.File;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.renderer.entity.PigRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Registration half of example1 (see hub decisions/REGISTRATION.md):
 * a second mod in the bridge jar under example1's own frozen modid
 * ({@code NAMES.md}, never a rename target). 1.20.1 registers blocks
 * through a {@code DeferredRegister} on this mod's own event bus (the
 * registry events fire after every mod constructs, before any common
 * setup), so the constructor only queues validated specs and records
 * suppliers — the registry event instantiates them, and the setup
 * setup listener verifies each name resolves and prints the registry-key
 * line the verdict greps. The same constructor queues the one generic
 * beast (hub decisions/SPAWN.md, custom entity tranche) from the
 * single-mob spawn table — pig shape and renderer reused, vanilla pigs
 * never carry our census anymore. Block-only ports came first (no beast
 * path); this tranche narrows the species, {@code MatouEntity} is
 * wired. New refusals stay registration-local ({@code E_REG_*}, never
 * in the shared catalog). Only this package may touch
 * {@code net.minecraft} / {@code net.minecraftforge}.
 *
 * <p>Ordering note: {@link MatouBridgeMod} binds its wires in its own
 * common-setup listener (deferred-registry fill lands one loading state
 * earlier), so custom wires resolve there whatever the mod order.
 * Registration never silently lags a bind.
 */
@Mod(Example1Mod.MODID)
public final class Example1Mod {
    public static final String MODID = "example1";
    static final String PACKS_PATH = "config/matoubridge/packs.cfg";

    private static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, MODID);
    private static final List<PendingBlock> PENDING =
            new ArrayList<PendingBlock>();
    private static final Map<String, RegistryObject<Block>> REGISTERED =
            new HashMap<String, RegistryObject<Block>>();
    private static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, MODID);
    private static final List<PendingItem> PENDING_ITEMS =
            new ArrayList<PendingItem>();
    private static final Map<String, RegistryObject<Item>> REGISTERED_ITEMS =
            new HashMap<String, RegistryObject<Item>>();
    /**
     * Entity tranche: the one generic beast, queued on the entity
     * registry under this mod's own id (same {@code (modid, short name)}
     * rule as blocks — short in, {@code example1:my_beast} out). Null
     * until a pack wires an owned file (Q1 cohabitation — no owned file
     * anywhere means no beast, same passivity as the spawn wire).
     */
    private static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, MODID);
    private static RegistryObject<EntityType<MatouEntity>> BEAST;
    private static String registeredEntity;

    /**
     * The registered beast type for the bridge sink and the DEV
     * companion (same species rule on both sides — a wandering vanilla
     * pig must never take a scripted landing or kill). Null before the
     * registry fill or without a wired owned file; callers fail loudly
     * on null, never a vanilla default.
     */
    public static EntityType<MatouEntity> beastType() {
        return BEAST == null ? null : BEAST.get();
    }

    /**
     * Queue + record: every packs.cfg wire naming a non-vanilla block
     * gets its content {@code BlockSpec} validated here, before any
     * registry event fires. Vanilla wires skip silently — the
     * bind-time resolve owns them, unchanged. A missing packs.cfg stays
     * passive (Q1 cohabitation), same as the bridge init. Registration
     * itself lands at the registry event, before common setup; binds
     * resolving these names run at/after setup ({@code MatouBridgeMod}
     * binds in its own setup listener — the deferred fill lands one
     * loading state earlier, whatever the mod order).
     */
    public Example1Mod() {
        IEventBus bus =
                FMLJavaModLoadingContext.get().getModEventBus();
        BLOCKS.register(bus);
        ITEMS.register(bus);
        ENTITIES.register(bus);
        bus.addListener(this::setup);
        bus.addListener((EntityAttributeCreationEvent event) ->
                registerBeastAttributes(event));
        File cfg = new File(PACKS_PATH);
        if (!cfg.isFile()) {
            return;
        }
        List<String> lines;
        try {
            lines = Files.readAllLines(cfg.toPath(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("E_REG_PACKS:unreadable <"
                    + PACKS_PATH + "> (" + e.getMessage() + ")", e);
        }
        List<Packs.PackSpec> specs = Packs.parseLines(lines);
        for (Packs.PackSpec spec : specs) {
            queueCustom(spec);
        }
        registerBeast(specs);
        registerItems(specs);
        for (PendingBlock p : PENDING) {
            try {
                REGISTERED.put(p.name, BLOCKS.register(p.shortName,
                        () -> new MatouBlock(p.hardness)));
            } catch (Exception e) {
                throw new IllegalArgumentException("E_REG_BLOCK:refused <"
                        + p.name + "> (" + e.getMessage() + ")", e);
            }
        }
        PENDING.clear();
        for (PendingItem p : PENDING_ITEMS) {
            try {
                REGISTERED_ITEMS.put(p.name, ITEMS.register(p.shortName,
                        () -> new MatouItem(p.stack)));
            } catch (Exception e) {
                throw new IllegalArgumentException("E_REG_ITEM:refused <"
                        + p.name + "> (" + e.getMessage() + ")", e);
            }
        }
        PENDING_ITEMS.clear();
    }

    /**
     * Verify + announce: the registry is only reliably queryable once
     * loading reaches setup, so the resolve check and the registry-key
     * line the verdict greps live here, not in the constructor. Binds
     * need no order against this: registration already happened one
     * loading state ago.
     */
    private void setup(FMLCommonSetupEvent event) {
        for (Map.Entry<String, RegistryObject<Block>> e
                : REGISTERED.entrySet()) {
            Block got = ForgeRegistries.BLOCKS.getValue(
                    new ResourceLocation(e.getKey()));
            if (got == null || got != e.getValue().get()) {
                throw new IllegalStateException(
                        "E_REG_UNRESOLVED:registered but unresolvable <"
                                + e.getKey() + ">");
            }
            System.out.println("[MatouBridge] registered <" + e.getKey()
                    + "> id " + e.getValue().getId());
        }
        for (Map.Entry<String, RegistryObject<Item>> e
                : REGISTERED_ITEMS.entrySet()) {
            Item got = ForgeRegistries.ITEMS.getValue(
                    new ResourceLocation(e.getKey()));
            if (got == null || got != e.getValue().get()) {
                throw new IllegalStateException(
                        "E_REG_UNRESOLVED:registered but unresolvable <"
                                + e.getKey() + ">");
            }
            System.out.println("[MatouBridge] registered-item <" + e.getKey()
                    + "> id " + e.getValue().getId());
        }
        if (registeredEntity != null) {
            EntityType<?> resolved = ForgeRegistries.ENTITY_TYPES.getValue(
                    new ResourceLocation(registeredEntity));
            if (resolved == null || resolved != BEAST.get()) {
                throw new IllegalStateException(
                        "E_REG_BEAST:unregistered <"
                                + registeredEntity + ">");
            }
            System.out.println("[MatouBridge] registered-entity <"
                    + registeredEntity + ">");
        }
    }

    /**
     * Beast attribute map: fresh entity types carry none, and the
     * vanilla {@code LivingEntity} ctor NPEs without one (measured live
     * on 1165 on the first landing — the tripwire never fires, the tick
     * loop dies). The beast reuses the vanilla pig map wholesale
     * (pig-identical, never hand-copied values). Null beast (no wired
     * owned file) stays passive — there is no type to arm, same Q1
     * rule as the missing packs.cfg.
     */
    private static void registerBeastAttributes(
            EntityAttributeCreationEvent event) {
        if (BEAST == null) {
            return;
        }
        event.put(BEAST.get(), Pig.createAttributes().build());
    }

    /**
     * Client-only renderer mapping: the generic beast reuses the vanilla
     * pig renderer until the custom-renderer tranche. A dist-filtered
     * nested subscriber (never referenced by server-side code, so the
     * class never loads on a dedicated server — no client frame ever
     * verifies there), discovered by Forge through ASM scan data. A
     * missing mapping would die loudly on the client instead (no
     * renderer at the first tracked spawn).
     *
     * <p>1.20.1 shape (measured on the pinned 47.2.0 bytes, never the
     * 1.16.5 shape): renderers map through the mod-bus
     * {@code EntityRenderersEvent.RegisterRenderers} event (the 1.16.5
     * {@code RenderingRegistry} call does not exist here) with the
     * single-{@code (Context)} pig ctor. Client link unproven until the
     * live tranche (the server Reobf leaves client refs alone; what the
     * client runtime wants them named is measured at live time, never
     * assumed here).
     */
    @Mod.EventBusSubscriber(modid = Example1Mod.MODID,
            bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class BeastClient {
        @SubscribeEvent
        public static void registerBeastRenderer(
                EntityRenderersEvent.RegisterRenderers event) {
            // T infers to Pig (the provider is a Pig renderer, the type
            // is a Pig subtype) — the beast renders as a vanilla pig
            // until the custom-renderer tranche.
            event.registerEntityRenderer(BEAST.get(), PigRenderer::new);
        }
    }

    private static void queueCustom(Packs.PackSpec spec) {
        String want = spec.blockName;
        int colon = want.indexOf(':');
        if (colon < 0 || want.startsWith("minecraft:")) {
            return;
        }
        if (REGISTERED.containsKey(want) || pendingContains(want)) {
            return;
        }
        if (ForgeRegistries.BLOCKS.containsKey(
                new ResourceLocation(want))) {
            throw new IllegalArgumentException(
                    "E_REG_DUP:already registered <" + want + ">");
        }
        String shortName = want.substring(colon + 1);
        String ownedFile = spec.args.get("ownedFile");
        if (ownedFile == null) {
            throw new IllegalArgumentException("E_REG_NOSPEC:no ownedFile "
                    + "for custom <" + want + "> (operator must point at "
                    + "the content declaring it)");
        }
        float hardness = 0.0f;
        boolean found = false;
        for (Object o : loadSpecs(ownedFile)) {
            if (!shortName.equals(specField(o, "name", ownedFile))) {
                continue;
            }
            if (found) {
                throw new IllegalArgumentException("E_REG_SPEC:dup <"
                        + shortName + "> in <" + ownedFile + ">");
            }
            Object h = specField(o, "hardness", ownedFile);
            Object op = specField(o, "opaque", ownedFile);
            if (!(h instanceof Float) || !(op instanceof Boolean)) {
                throw new IllegalArgumentException("E_REG_SPEC:shape <"
                        + ownedFile + "> (bad physics types)");
            }
            if (!((Boolean) op).booleanValue()) {
                throw new IllegalArgumentException("E_REG_SPEC:translucent <"
                        + shortName + "> (no Properties opacity slot "
                        + "on 1.20.1)");
            }
            hardness = ((Float) h).floatValue();
            found = true;
        }
        if (!found) {
            throw new IllegalArgumentException("E_REG_NOSPEC:no block <"
                    + shortName + "> in <" + ownedFile + "> for <" + want
                    + ">");
        }
        PENDING.add(new PendingBlock(want, shortName, hardness));
    }

    private static boolean pendingContains(String want) {
        for (PendingBlock p : PENDING) {
            if (p.name.equals(want)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Entity registration: the single mob ref from the same owned content
     * the loot table and the spawn wire came from (parsed once, like
     * blocks — never on the tick path). No owned file anywhere means no
     * beast (Q1 cohabitation), same passivity as the spawn wire. Several
     * distinct owned files refuse loudly — silent table picks are
     * defaults, and per-mob tables are a documented re-opener.
     *
     * <p>1.20.1 shape (DeferredRegister, never the 1.7.10/1.12
     * {@code EntityRegistry} call): the beast type builds through
     * {@code EntityType.Builder} (pig-category, pig hitbox and pig
     * tracking — constants measured on the pinned 47.2.0 bytes, never
     * recalled: vanilla PIG chains {@code MobCategory.CREATURE} /
     * {@code 0.9 x 0.9} / tracking range 10 with the Builder default
     * update interval, so no interval call here is pig-identical, not a
     * missing knob) and queues under the short mob name (the register
     * builds the entry id as {@code (modid, name)} from this
     * DeferredRegister's own modid, same rule as blocks — short in,
     * {@code example1:my_beast} out). The setup-time tripwire reads it
     * back through {@code ForgeRegistries.ENTITY_TYPES}.
     */
    private static void registerBeast(List<Packs.PackSpec> specs) {
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
            throw new IllegalArgumentException("E_REG_TABLE:multi <"
                    + owned + "> (one beast table per bridge)");
        }
        String ownedFile = owned.iterator().next();
        String mob = loadMobRef(ownedFile);
        int colon = mob.indexOf(':');
        String shortName = mob.substring(colon + 1);
        // Registry id, never the SPI mob ref: this DeferredRegister
        // builds entry ids as (own modid, name), so the tripwire must
        // look up <example1:my_beast>, not <example1.content:my_beast>
        // (the 1.7.10 tripwire is class-keyed and never reads the
        // string — copying mob verbatim only works there; measured live
        // on 1165, never assumed here).
        registeredEntity = MODID + ":" + shortName;
        try {
            BEAST = ENTITIES.register(shortName,
                    () -> EntityType.Builder.of(MatouEntity::new,
                            MobCategory.CREATURE)
                            .sized(0.9F, 0.9F)
                            .clientTrackingRange(10)
                            .build(shortName));
        } catch (Exception e) {
            throw new IllegalArgumentException("E_REG_BEAST:refused <"
                    + mob + "> (" + e.getMessage() + ")", e);
        }
    }

    private static final class PendingBlock {
        final String name;
        final String shortName;
        final float hardness;

        PendingBlock(String name, String shortName, float hardness) {
            this.name = name;
            this.shortName = shortName;
            this.hardness = hardness;
        }
    }

    /**
     * Content mob ref, reached reflectively: the bridge stays content-blind
     * at build time (Q2 — same rule as {@code loadSpecs}). The
     * single-mob rule lives in {@code SpawnTable.fromFile} — zero or
     * several mobs already refuse there, never a quiet pick here. Every
     * failure is coded E_REG_*, never a silent default.
     */
    private static String loadMobRef(String ownedFile) {
        final Class<?> cls;
        try {
            cls = Class.forName("fr.iamacat.example1.SpawnTable");
        } catch (ClassNotFoundException e) {
            throw new IllegalArgumentException("E_REG_BEAST:missing "
                    + "example1 for <" + ownedFile + "> ("
                    + e.getMessage() + ")", e);
        }
        final Method fromFile;
        try {
            fromFile = cls.getMethod("fromFile", String.class);
        } catch (NoSuchMethodException e) {
            throw new IllegalArgumentException("E_REG_BEAST:shape "
                    + "<fr.iamacat.example1.SpawnTable> ("
                    + e.getMessage() + ")", e);
        }
        try {
            Object table = fromFile.invoke(null, ownedFile);
            Object mob = table.getClass().getMethod("mob").invoke(table);
            if (!(mob instanceof String) || ((String) mob).isEmpty()
                    || ((String) mob).indexOf(':') < 0) {
                throw new IllegalStateException("E_REG_BEAST:shape "
                        + "<fromFile> (want qualified mob ref)");
            }
            return (String) mob;
        } catch (java.lang.reflect.InvocationTargetException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            throw new IllegalArgumentException("E_REG_BEAST:unreadable <"
                    + ownedFile + "> (" + cause.getMessage() + ")", e);
        } catch (IllegalAccessException e) {
            throw new IllegalArgumentException("E_REG_BEAST:shape <"
                    + ownedFile + "> (" + e.getMessage() + ")", e);
        } catch (NoSuchMethodException e) {
            throw new IllegalArgumentException("E_REG_BEAST:shape "
                    + "<fr.iamacat.example1.SpawnTable> ("
                    + e.getMessage() + ")", e);
        }
    }

    /**
     * Content specs, reached reflectively: the bridge stays content-blind
     * at build time (Q2 — same rule as {@code Packs.load}). Every
     * failure is coded E_REG_*, never a silent default.
     */
    private static List<?> loadSpecs(String ownedFile) {
        final Class<?> cls;
        try {
            cls = Class.forName("fr.iamacat.example1.BlockSpec");
        } catch (ClassNotFoundException e) {
            throw new IllegalArgumentException("E_REG_SPEC:missing "
                    + "example1 for <" + ownedFile + "> ("
                    + e.getMessage() + ")", e);
        }
        final Method fromFile;
        try {
            fromFile = cls.getMethod("fromFile", String.class);
        } catch (NoSuchMethodException e) {
            throw new IllegalArgumentException("E_REG_SPEC:shape "
                    + "<fr.iamacat.example1.BlockSpec> ("
                    + e.getMessage() + ")", e);
        }
        try {
            Object out = fromFile.invoke(null, ownedFile);
            if (!(out instanceof List)) {
                throw new IllegalStateException("E_REG_SPEC:shape "
                        + "<fromFile> (want List)");
            }
            return (List<?>) out;
        } catch (java.lang.reflect.InvocationTargetException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            throw new IllegalArgumentException("E_REG_SPEC:unreadable <"
                    + ownedFile + "> (" + cause.getMessage() + ")", e);
        } catch (IllegalAccessException e) {
            throw new IllegalArgumentException("E_REG_SPEC:shape <"
                    + ownedFile + "> (" + e.getMessage() + ")", e);
        }
    }

    private static Object specField(Object spec, String getter,
            String ownedFile) {
        try {
            return spec.getClass().getMethod(getter).invoke(spec);
        } catch (Exception e) {
            throw new IllegalArgumentException("E_REG_SPEC:shape <"
                    + ownedFile + "> (no " + getter + ")", e);
        }
    }

    private static void registerItems(List<Packs.PackSpec> specs) {
        Set<String> owned = new HashSet<String>();
        for (Packs.PackSpec spec : specs) {
            String path = spec.args.get("ownedFile");
            if (path != null) {
                owned.add(path);
            }
        }
        for (String ownedFile : owned) {
            for (Object o : loadItemSpecs(ownedFile)) {
                String shortName = (String) specField(o, "name", ownedFile);
                String want = MODID + ":" + shortName;
                if (REGISTERED_ITEMS.containsKey(want) || pendingItemContains(want)) {
                    continue;
                }
                if (ForgeRegistries.ITEMS.containsKey(new ResourceLocation(want))) {
                    throw new IllegalArgumentException(
                            "E_REG_ITEM:already registered <" + want + ">");
                }
                Object s = specField(o, "stack", ownedFile);
                if (!(s instanceof Integer)) {
                    throw new IllegalArgumentException(
                            "E_REG_SPEC:shape <" + ownedFile + "> (bad stack type)");
                }
                int stack = ((Integer) s).intValue();
                PENDING_ITEMS.add(new PendingItem(want, shortName, stack));
            }
        }
    }

    private static boolean pendingItemContains(String want) {
        for (PendingItem p : PENDING_ITEMS) {
            if (p.name.equals(want)) {
                return true;
            }
        }
        return false;
    }

    private static final class PendingItem {
        final String name;
        final String shortName;
        final int stack;

        PendingItem(String name, String shortName, int stack) {
            this.name = name;
            this.shortName = shortName;
            this.stack = stack;
        }
    }

    private static List<?> loadItemSpecs(String ownedFile) {
        final Class<?> cls;
        try {
            cls = Class.forName("fr.iamacat.example1.ItemSpec");
        } catch (ClassNotFoundException e) {
            throw new IllegalArgumentException("E_REG_SPEC:missing "
                    + "example1 for <" + ownedFile + "> ("
                    + e.getMessage() + ")", e);
        }
        final Method fromFile;
        try {
            fromFile = cls.getMethod("fromFile", String.class);
        } catch (NoSuchMethodException e) {
            throw new IllegalArgumentException("E_REG_SPEC:shape "
                    + "<fr.iamacat.example1.ItemSpec> ("
                    + e.getMessage() + ")", e);
        }
        try {
            Object out = fromFile.invoke(null, ownedFile);
            if (!(out instanceof List)) {
                throw new IllegalStateException("E_REG_SPEC:shape "
                        + "<fromFile> (want List)");
            }
            return (List<?>) out;
        } catch (java.lang.reflect.InvocationTargetException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            throw new IllegalArgumentException("E_REG_SPEC:unreadable <"
                    + ownedFile + "> (" + cause.getMessage() + ")", e);
        } catch (IllegalAccessException e) {
            throw new IllegalArgumentException("E_REG_SPEC:shape <"
                    + ownedFile + "> (" + e.getMessage() + ")", e);
        }
    }
}


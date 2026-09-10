package fr.iamacat.bridge.forge;

import fr.iamacat.bridge.Packs;
import java.io.File;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
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
 * listener verifies each name resolves and prints the numeric-ID line
 * the verdict greps. Block-only tranche: no beast path (the
 * single-mob table stays unread, {@code MatouEntity} stays an unwired
 * shell). New refusals stay registration-local ({@code E_REG_*}, never
 * in the shared error catalog). Only this package may touch
 * {@code net.minecraft} / {@code net.minecraftforge}.
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
        BLOCKS.register(
                FMLJavaModLoadingContext.get().getModEventBus());
        FMLJavaModLoadingContext.get().getModEventBus().addListener(
                this::setup);
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
}

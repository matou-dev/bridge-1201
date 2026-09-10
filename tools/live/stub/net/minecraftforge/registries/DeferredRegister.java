package net.minecraftforge.registries;

import java.util.function.Supplier;
import net.minecraftforge.eventbus.api.IEventBus;

/**
 * Registration compile stub: shape-only Forge 1.20.1-47.2.0 API
 * (universal jar, never obfuscated). Never runs (compile classpath
 * only). Only the members {@code forge/} references ({@code create},
 * {@code register} x2) — the live tranche pins them against the
 * provisioned 47.2.0 jars, drift fails loudly there, never silently
 * here.
 */
public class DeferredRegister<T> {
    public static <T> DeferredRegister<T> create(IForgeRegistry<T> registry,
            String modid) {
        return null;
    }

    public <I extends T> RegistryObject<I> register(String name,
            Supplier<? extends I> sup) {
        return null;
    }

    public void register(IEventBus bus) {
    }
}

package net.minecraftforge.fml.common;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import net.minecraftforge.api.distmarker.Dist;

/**
 * D1 compile stub: shape-only Forge 1.20.1-47.2.0 API (javafmllanguage jar,
 * never obfuscated). Never runs (compile classpath only). Member
 * {@code value} is pinned by tools/run-live.sh (D3) — drift fails loudly.
 * Retention RUNTIME is load-bearing (mirrors the real annotation): a
 * CLASS-retention stub would emit invisible usages.
 *
 * <p>Custom entity tranche: the beast's vanilla pig renderer registers
 * through a dist-filtered nested subscriber ({@code EventBusSubscriber}
 * with {@code Bus.MOD} + {@code Dist.CLIENT}, measured via javap against
 * the pinned 47.2.0 javafmllanguage jar — the 1.16.5
 * {@code RenderingRegistry} path does not exist on 1.20.1). Forge reads
 * the subscription through ASM scan data, so the nested class never
 * loads on a dedicated server — no client frame ever verifies there.
 * Retention RUNTIME is load-bearing here too (Forge discovers the
 * subscriber through the annotation). Pinned by tools/run-live.sh (D3) —
 * drift fails loudly.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Mod {
    String value();

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE)
    public @interface EventBusSubscriber {
        Dist[] value() default {};

        String modid() default "";

        Bus bus() default Bus.FORGE;

        public enum Bus {
            FORGE, MOD
        }
    }
}

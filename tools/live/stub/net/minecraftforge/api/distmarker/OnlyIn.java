package net.minecraftforge.api.distmarker;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Renderer compile stub: shape-only Forge 1.20.1-47.2.0 API
 * (mergetool-api jar on the installer legacy classpath, never
 * obfuscated). Never runs (compile classpath only). Measured with
 * javap -v against the provisioned mergetool-api 1.1.5 jar: RUNTIME
 * retention, TYPE/FIELD/METHOD/CONSTRUCTOR targets, {@code value()}
 * plus a defaulted {@code _interface()} — the default is load-bearing
 * (without it every {@code @OnlyIn} use must name the member). The
 * runtime cleaner strips annotated members on the wrong dist.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.FIELD, ElementType.METHOD,
        ElementType.CONSTRUCTOR})
public @interface OnlyIn {
    Dist value();

    Class<?> _interface() default Object.class;
}

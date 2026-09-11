package net.minecraft.world.phys;

/**
 * Combat compile stub: 47.2.0 eye/look component type. The bridge
 * combat hook reads the attacker eye and look through
 * {@code Entity.getEyePosition}/{@code getLookAngle} and takes their
 * components here — public final double fields ({@code x/y/z},
 * measured via server.txt + joined.tsrg v2 + javap against the pinned
 * 47.2.0 bytes; the same-named {@code x()/y()/z()} methods are NOT
 * this, hence the field anchor in the narrow map). Never runs (compile
 * classpath only).
 */
public class Vec3 {
    public double x;
    public double y;
    public double z;
}

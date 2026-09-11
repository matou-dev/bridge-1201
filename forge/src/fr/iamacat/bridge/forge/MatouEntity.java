package fr.iamacat.bridge.forge;

import fr.iamacat.bridge.model.BeastModel;
import fr.iamacat.spi.hit.BoneBox;
import fr.iamacat.spi.hit.Hittable;
import java.util.List;
import java.util.Map;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.level.Level;

/**
 * Registration landing (see hub decisions/SPAWN.md, custom entity
 * tranche): the one generic Forge beast every content mob registers as.
 * Pig shape, AI and sounds are reused verbatim — the vanilla pig
 * renderer is mapped to this class until the custom-renderer tranche,
 * never a renderer per content. One generic subclass, never one per
 * content, never a shadow field. The census, the join veto, the
 * reconcile poll and the kill hook all match this class: vanilla pigs
 * are a different species now (ignored by the census, never vetoed).
 * The content {@code hp} lands on the beast's max-health attribute at
 * every landing ({@code MatouBridgeMod.landBeast}, hp tranche — read
 * back tripwired, never a silent default); the vanilla pig renderer
 * mapping stays until the custom-renderer tranche.
 *
 * <p>1.20.1 shape: same superclass as the lead bridge ({@code Pig},
 * pig-like {@code (EntityType, Level)} ctor — the spawn seam already
 * lands vanilla pigs through it, live-proven). The factory slot
 * ({@code EntityType.EntityFactory}) takes this constructor by method
 * ref (see {@code Example1Mod}); registration and rendering go through
 * the version-native calls there, never from here. Only this package
 * may import {@code net.minecraft} / {@code net.minecraftforge}.
 *
 * <p>Mob identity (per-mob tranche, hub
 * {@code decisions/VIRTUAL_HITBOXES.md}): every beast carries its short
 * content mob name ({@code my_beast} — the combat-table key, never the
 * qualified cell ref), set by the landing before the spawn and persisted
 * through NBT. Entities without a stored tag (legacy saves, natural
 * paths) adopt the first sealed combat mob on first read with a one-line
 * note — that preserves the current single-mob behaviour for naturals,
 * never silently. Hit-time reads dispatch per mob through
 * {@link #hitWeakspots()}; the inherited {@code Hittable} default
 * {@code weakspotMultiplier} already resolves through it, so no override
 * duplicates that rule here.
 *
 * <p>1.20.1 persist shape (measured via server.txt + joined.tsrg v2 +
 * javap on the pinned 47.2.0 bytes, never the 1.12 shape): the beast
 * overrides the Pig-declared {@code addAdditionalSaveData} /
 * {@code readAdditionalSaveData} pair (both public here — the 1.12
 * protected-helper shape does not port), never the public save/load one
 * level up on {@code Entity} (their super calls would emit the
 * intermediate {@code Pig} owner no narrow-map row can cover — hub
 * decisions/VIRTUAL_HITBOXES.md second-beast row). The string-tag
 * surface rides {@code CompoundTag.putString}/{@code getString}/
 * {@code contains}.
 *
 * <p>Model tranche (hub decisions/MATOU_MODEL.md): the beast is a
 * {@code Hittable} over the shipped {@code my_beast.geo.json} shape —
 * world-space bone boxes ride the entity origin (feet), the head stays
 * the weakspot (per-mob multiplier, hub decisions/VIRTUAL_HITBOXES.md
 * second-beast row). The combat hook (hub decisions/VIRTUAL_HITBOXES.md)
 * ray-tests them server-side; this serves the authoritative shape both
 * sides test. 1.20.1 reads the origin through the {@code getX/Y/Z}
 * getters (the 1.12 {@code posX} field shape does not port).
 */
public class MatouEntity extends Pig implements Hittable {
    /** NBT tag carrying the short content mob name. */
    static final String NBT_MOB = "MatouMob";

    /** Short content mob name ({@code my_beast}), null until set. */
    private String mob;

    /**
     * Args are pre-validated by the registering mod (E_REG_* owns the
     * refusals); the constructor only lands the vanilla shape. The mob
     * identity arrives via {@link #setMob} (landings) or NBT (loads).
     */
    public MatouEntity(EntityType<? extends MatouEntity> type,
            Level level) {
        super(type, level);
    }

    /**
     * Seals the short content mob name on this beast (called by the
     * landing before the spawn). Loud on null/empty — an unidentified
     * beast would be a silent census leak. Unknown-at-seal is NOT
     * checked here: the sealed readers ({@code BeastModel}, the spawn
     * seal) refuse unknown mobs loudly at their own choke points, never
     * defaulted.
     */
    public void setMob(String mob) {
        if (mob == null) {
            throw new NullPointerException("E_SPAWN_MOB:null mob "
                    + "(want a sealed short mob name — see "
                    + "BeastModel.combatMobs)");
        }
        if (mob.isEmpty()) {
            throw new IllegalArgumentException("E_SPAWN_MOB:empty mob "
                    + "(want a sealed short mob name — never "
                    + "defaulted)");
        }
        this.mob = mob;
    }

    /** Short content mob name, or null before any set/load/adopt. */
    public String mob() {
        return mob;
    }

    /**
     * Short content mob name, adopting the first sealed combat mob (in
     * seal order) with a one-line note when unset — legacy saves and
     * natural paths keep the current single-mob behaviour, never
     * silently. The adoption memoizes: one line per entity, later reads
     * stay quiet. Loud when combat was never sealed (an unsealed read
     * would be a silent default) — every caller is wire-gated.
     */
    public String mobOrFirst() {
        if (mob == null) {
            mob = BeastModel.combatMobs().iterator().next();
            System.out.println("[MatouBridge] beast adopted mob <"
                    + mob + "> (no stored identity — first sealed mob)");
        }
        return mob;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag compound) {
        super.addAdditionalSaveData(compound);
        if (mob != null) {
            compound.putString(NBT_MOB, mob);
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag compound) {
        super.readAdditionalSaveData(compound);
        if (compound.contains(NBT_MOB)) {
            setMob(compound.getString(NBT_MOB));
        }
        // No tag (legacy/natural paths): mob stays null until
        // mobOrFirst() adopts with its note — the load path never
        // refuses, the hook path never defaults silently.
    }

    @Override
    public List<BoneBox> hitBoxes() {
        // Owner discipline (measured live on 1122 as NoSuchFieldError
        // posX, fixed there in 840507c, and on 1165 as NoSuchMethodError
        // getPosX, fixed there in 605b623, hub decisions/LOOT.md): a bare
        // getX() call owns MatouEntity, whose reobf walk dies at the
        // vanilla Pig link — inherited vanilla members go through the
        // declaring stub type (Entity), never the beast. Landed with the
        // fix, never red-crashed first.
        Entity self = this;
        return BeastModel.cached().boxesAt(self.getX(), self.getY(),
                self.getZ());
    }

    @Override
    public Map<String, Float> hitWeakspots() {
        return BeastModel.combatWeakspots(mobOrFirst());
    }
}

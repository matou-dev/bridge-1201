package fr.iamacat.bridge.forge;

import fr.iamacat.bridge.model.BeastModel;
import fr.iamacat.spi.hit.BoneBox;
import fr.iamacat.spi.hit.Hittable;
import java.util.List;
import java.util.Map;
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
 * <p>Model tranche (hub decisions/MATOU_MODEL.md): the beast is a
 * {@code Hittable} over the shipped {@code my_beast.geo.json} shape —
 * world-space bone boxes ride the entity origin (feet), the head stays
 * the 2x weakspot. The combat hook (hub decisions/VIRTUAL_HITBOXES.md)
 * ray-tests them server-side; this serves the authoritative shape both
 * sides test. 1.20.1 reads the origin through the {@code getX/Y/Z}
 * getters (the 1.12 {@code posX} field shape does not port).
 */
public class MatouEntity extends Pig implements Hittable {
    /**
     * Args are pre-validated by the registering mod (E_REG_* owns the
     * refusals); the constructor only lands the vanilla shape.
     */
    public MatouEntity(EntityType<? extends MatouEntity> type,
            Level level) {
        super(type, level);
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
        return BeastModel.WEAKSPOTS;
    }
}

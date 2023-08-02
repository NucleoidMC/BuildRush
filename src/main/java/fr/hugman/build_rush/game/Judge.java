package fr.hugman.build_rush.game;

import eu.pb4.polymer.virtualentity.api.ElementHolder;
import eu.pb4.polymer.virtualentity.api.attachment.ChunkAttachment;
import eu.pb4.polymer.virtualentity.api.elements.BlockDisplayElement;
import net.minecraft.block.Blocks;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import xyz.nucleoid.map_templates.BlockBounds;

public class Judge {
    private final BRRoundManager roundManager;
    private final ElementHolder judgeHolder;
    private BlockDisplayElement judgeElement;

    public Judge(BRRoundManager roundManager, ElementHolder elementHolder) {
        this.roundManager = roundManager;
        this.judgeHolder = elementHolder;
    }

    public static Judge of(BRRoundManager roundManager, ServerWorld world, Vec3d pos) {
        var judgeHolder = new ElementHolder();
        ChunkAttachment.of(judgeHolder, world, pos);
        return new Judge(roundManager, judgeHolder);
    }

    public void spawn() {
        this.judgeElement = new BlockDisplayElement(Blocks.TNT.getDefaultState());
        this.judgeElement.setTranslation(new Vector3f(-0.5f, -0.5f, -0.5f));
        for (var element : this.judgeHolder.getElements()) {
            this.judgeHolder.removeElement(element);
        }
        this.judgeHolder.addElement(this.judgeElement);
    }

    public void lookAround() {
        var duration = this.roundManager.getCurrentLength();
        Quaternionf rotation = new Quaternionf();
        rotation.rotateAxis((float) Math.toRadians(360) - 0.1f, 0, 1, 0);
        this.judgeElement.setRightRotation(rotation);
        this.judgeElement.setInterpolationDuration(duration);
        this.judgeElement.startInterpolation();
        this.judgeElement.tick();
    }

    public void sendToPlot(BlockBounds plot) {
        var duration = this.roundManager.getCurrentLength();
        Vec3d pos = Vec3d.of(plot.min());
        Vector3f translation = pos
                .subtract(this.judgeHolder.getPos())
                .toVector3f();

        Quaternionf rotation = new Quaternionf();
        rotation.rotateAxis((float) Math.toRadians(0), 0, 1, 0);
        this.judgeElement.setRightRotation(rotation);
        this.judgeElement.setTranslation(translation);
        this.judgeElement.setScale(Vec3d.of(plot.size().add(1, 1, 1)).toVector3f());
        this.judgeElement.setInterpolationDuration(duration);
        this.judgeElement.startInterpolation();
        this.judgeElement.tick();
    }

    public void end() {
        var duration = this.roundManager.getCurrentLength();
        for (var element : this.judgeHolder.getElements()) {
            this.judgeHolder.removeElement(element);
        }
        this.judgeElement = null;
    }

    public void tick() {

    }

}

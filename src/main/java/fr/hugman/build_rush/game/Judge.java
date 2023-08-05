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
    private static final float HEIGHT_ABOVE_PLOT = 50.0f;
    public static final int EXPLOSION_DURATION = 10;

    private final RoundManager roundManager;
    private final ElementHolder judgeHolder;
    private BlockDisplayElement judgeElement;
    private int size;

    public Judge(RoundManager roundManager, ElementHolder elementHolder) {
        this.roundManager = roundManager;
        this.judgeHolder = elementHolder;
    }

    public static Judge of(RoundManager roundManager, ServerWorld world, Vec3d pos) {
        var judgeHolder = new ElementHolder();
        ChunkAttachment.of(judgeHolder, world, pos);
        return new Judge(roundManager, judgeHolder);
    }

    public void tick() {
        if(this.judgeElement == null) {
            return;
        }
        var state = this.roundManager.getState();
        var stateTick = this.roundManager.getStateTick();
        var currentLength = this.roundManager.getCurrentLength();

        if (state == RoundManager.ELIMINATION_START) {
            //stateTick 0 is at BRActive#eliminateLoser
            if(stateTick == 1) {
                this.nukePlot1();
            }
            if(stateTick == currentLength / 2) {
                this.nukePlot2();
            }

            if(stateTick % 10 == 0) {
                this.judgeElement.setBlockState(Blocks.TNT.getDefaultState());
            }
            if(stateTick % 20 == 0) {
                this.judgeElement.setBlockState(Blocks.WHITE_CONCRETE_POWDER.getDefaultState());
            }
        }

        if (state == RoundManager.ELIMINATION) {
            if(stateTick == 0) {
                this.smush();
            }
            if(stateTick == EXPLOSION_DURATION / 3) {
                this.stretch();
            }
            if(stateTick == EXPLOSION_DURATION / 3 * 2) {
                this.shrink();
            }
            if(stateTick == EXPLOSION_DURATION) {
                this.remove();
            }
        }
    }

    public void spawn() {
        this.judgeElement = new BlockDisplayElement(Blocks.TNT.getDefaultState());
        this.judgeElement.setTranslation(new Vector3f(-0.5f, -0.5f, -0.5f));
        for (var element : this.judgeHolder.getElements()) {
            this.judgeHolder.removeElement(element);
        }
        this.judgeHolder.addElement(this.judgeElement);
    }

    public void setAbovePlot(BlockBounds plot) {
        this.judgeElement.setTranslation(
                Vec3d.of(plot.min())
                        .add(0.5, HEIGHT_ABOVE_PLOT, 0.5)
                        .subtract(this.judgeHolder.getPos())
                        .toVector3f()
        );
        var rotation = new Quaternionf();
        rotation.rotateAxis((float) Math.toRadians(0), 0, 1, 0);

        this.judgeElement.setRightRotation(rotation);
        this.judgeElement.setInterpolationDuration(0);
        this.judgeElement.startInterpolation();
        this.judgeElement.tick();
        this.size = plot.size().getX()+1;
    }

    public void nukePlot1() {
        // the judge is currently HEIGHT_ABOVE_PLOT blocks above the plot
        // descend to half of HEIGHT_ABOVE_PLOT while growing to full plot size
        var translation = new Vector3f(this.size * -0.25f, -HEIGHT_ABOVE_PLOT / 2, this.size * -0.25f).add(this.judgeElement.getTranslation());
        var duration = this.roundManager.getCurrentLength() / 2 - 1;

        this.judgeElement.setTranslation(translation);
        this.judgeElement.setScale(new Vec3d(this.size * 0.5, this.size * 0.7D, this.size * 0.5).toVector3f());
        this.judgeElement.startInterpolation();
        this.judgeElement.setInterpolationDuration(duration);
        this.judgeElement.tick();
    }

    public void nukePlot2() {
        // the judge is currently at half of HEIGHT_ABOVE_PLOT blocks above the plot
        // descend to 0
        var translation = new Vector3f(this.size * -0.25f, -HEIGHT_ABOVE_PLOT / 2, this.size * -0.25f).add(this.judgeElement.getTranslation());
        var duration = this.roundManager.getCurrentLength() / 2;

        this.judgeElement.setTranslation(translation);
        this.judgeElement.setScale(new Vec3d(this.size, this.size * 1.4, this.size).toVector3f());
        this.judgeElement.setInterpolationDuration(duration);
        this.judgeElement.startInterpolation();
        this.judgeElement.tick();
    }

    public void smush() {
        var translation = new Vector3f(this.size * 0.3f, 0, this.size * 0.3f).add(this.judgeElement.getTranslation());

        this.judgeElement.setTranslation(translation);
        this.judgeElement.setScale(new Vec3d(this.size * 1.3, this.size, this.size * 1.3).toVector3f());
        this.judgeElement.setInterpolationDuration(EXPLOSION_DURATION / 3);
        this.judgeElement.startInterpolation();
        this.judgeElement.tick();
    }

    public void stretch() {
        var translation = new Vector3f(this.size * -0.3f, 0, this.size * -0.3f).add(this.judgeElement.getTranslation());

        this.judgeElement.setTranslation(translation);
        this.judgeElement.setScale(new Vec3d(this.size, this.size * 1.1, this.size).toVector3f());
        this.judgeElement.setInterpolationDuration(EXPLOSION_DURATION / 3);
        this.judgeElement.startInterpolation();
        this.judgeElement.tick();
    }

    public void shrink() {
        var translation = new Vector3f(-this.size, 0, -this.size).add(this.judgeElement.getTranslation());

        this.judgeElement.setTranslation(translation);
        this.judgeElement.setScale(new Vec3d(0, 0, 0).toVector3f());
        this.judgeElement.setInterpolationDuration(EXPLOSION_DURATION / 3);
        this.judgeElement.startInterpolation();
        this.judgeElement.tick();
    }

    public void remove() {
        for (var element : this.judgeHolder.getElements()) {
            this.judgeHolder.removeElement(element);
        }
        this.judgeElement = null;
    }

}

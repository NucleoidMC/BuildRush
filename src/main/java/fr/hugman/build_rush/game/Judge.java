package fr.hugman.build_rush.game;

import eu.pb4.polymer.virtualentity.api.ElementHolder;
import eu.pb4.polymer.virtualentity.api.attachment.ChunkAttachment;
import eu.pb4.polymer.virtualentity.api.elements.BlockDisplayElement;
import fr.hugman.build_rush.BuildRush;
import fr.hugman.build_rush.map.Plot;
import net.minecraft.block.Blocks;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public class Judge {
    private static final float HEIGHT_FLY = 20.0f;
    private static final float HEIGHT_ABOVE_PLOT = 50.0f;
    public static final int EXPLOSION_DURATION = 15;

    private final RoundManager roundManager;
    private final ElementHolder judgeHolder;
    private BlockDisplayElement judgeElement;
    private int buildSize;
    private Vector3f offset;

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
        if (this.judgeElement == null) {
            return;
        }
        var state = this.roundManager.getState();
        var stateTick = this.roundManager.getStateTick();
        var currentLength = this.roundManager.getCurrentLength();

        if (state == RoundManager.BUILD_END) {
            if (stateTick % 6 == 1) {
                // rotate randomly while going up
                var progress = (float) stateTick / currentLength;
                var progressi = 1 - progress;

                Quaternionf rotation = new Quaternionf();
                rotation.rotateAxis((float) (Math.random() * 2 * Math.PI), 1, 1, 1);
                this.judgeElement.setTranslation(new Vector3f(progressi * 1.5f, progress * HEIGHT_FLY , progressi * 1.5f));
                this.judgeElement.setScale(new Vector3f(progressi * 3.0f, progressi * 3.0f, progressi * 3.0f));
                this.judgeElement.setRightRotation(rotation);
                this.judgeElement.setInterpolationDuration(6);
                this.judgeElement.startInterpolation();
                this.judgeElement.tick();
            }
        }

        if (state == RoundManager.ELIMINATION_START) {
            //stateTick 0 is at BRActive#eliminateLoser
            if (stateTick == 1) {
                this.nukePlot1();
            }
            if (stateTick == currentLength / 2) {
                this.nukePlot2();
            }
            if (stateTick % 6 == 0) {
                this.judgeElement.setBlockState(Blocks.TNT.getDefaultState());
                this.judgeElement.tick();
            }
            if (stateTick % 6 == 3) {
                this.judgeElement.setBlockState(Blocks.WHITE_CONCRETE_POWDER.getDefaultState());
                this.judgeElement.tick();
            }
        }

        if (state == RoundManager.ELIMINATION) {
            if (stateTick == 0) {
                this.squash();
            }
            if (stateTick == EXPLOSION_DURATION / 3) {
                this.squish();
            }
            if (stateTick == EXPLOSION_DURATION / 3 * 2) {
                this.shrink();
            }
            if (stateTick == EXPLOSION_DURATION) {
                this.remove();
            }
        }
    }

    public void spawn() {
        this.judgeElement = new BlockDisplayElement(Blocks.TNT.getDefaultState());
        this.judgeElement.setTranslation(new Vector3f(-1.5f, -1.5f, -1.5f));
        this.judgeElement.setScale(new Vector3f(3, 3, 3));
        for (var element : this.judgeHolder.getElements()) {
            this.judgeHolder.removeElement(element);
        }
        this.judgeHolder.addElement(this.judgeElement);
        this.offset = new Vector3f(0, 0, 0);
    }

    public void setPlot(Plot plot) {
        this.offset = plot.buildBounds().center().subtract(this.judgeHolder.getPos()).subtract(this.judgeElement.getOffset()).toVector3f();
        this.buildSize = plot.buildBounds().size().getX() + 1;
        BuildRush.LOGGER.info("Judge offset: " + this.offset);
    }

    public void setAbovePlot() {
        this.judgeElement.setTranslation(new Vector3f(this.offset)
                .add(0, HEIGHT_ABOVE_PLOT, 0)
        );
        var rotation = new Quaternionf();
        rotation.rotateAxis((float) Math.toRadians(0), 0, 1, 0);

        this.judgeElement.setRightRotation(rotation);
        this.judgeElement.setScale(new Vector3f(0, 0, 0));
        this.judgeElement.setInterpolationDuration(0);
        this.judgeElement.startInterpolation();
        this.judgeElement.tick();
    }

    public void nukePlot1() {
        var translation = new Vector3f(this.offset)
                .add(this.buildSize * -0.25f, 0, this.buildSize * -0.25f)
                .add(0, HEIGHT_ABOVE_PLOT / 2, 0);

        var duration = this.roundManager.getCurrentLength() / 2 - 1;
        this.judgeElement.setTranslation(translation);
        this.judgeElement.setScale(new Vector3f(this.buildSize * 0.5f, this.buildSize * 0.7f, this.buildSize * 0.5f));
        this.judgeElement.startInterpolation();
        this.judgeElement.setInterpolationDuration(duration);
        this.judgeElement.tick();
    }

    public void nukePlot2() {
        var translation = new Vector3f(this.offset).add(-this.buildSize * 0.5f, -this.buildSize * 0.7f, -this.buildSize * 0.5f);
        var duration = this.roundManager.getCurrentLength() / 2;

        this.judgeElement.setTranslation(translation);
        this.judgeElement.setScale(new Vector3f(this.buildSize, this.buildSize * 1.4f, this.buildSize));
        this.judgeElement.setInterpolationDuration(duration);
        this.judgeElement.startInterpolation();
        this.judgeElement.tick();
    }

    public void squash() {
        var translation = new Vector3f(this.offset).add(-this.buildSize * 0.75f, -this.buildSize * 0.75f, -this.buildSize * 0.75f);

        this.judgeElement.setTranslation(translation);
        this.judgeElement.setScale(new Vector3f(this.buildSize * 1.5f, this.buildSize, this.buildSize * 1.5f));
        this.judgeElement.setInterpolationDuration(EXPLOSION_DURATION / 3);
        this.judgeElement.startInterpolation();
        this.judgeElement.tick();
    }

    public void squish() {
        var translation = new Vector3f(this.offset).add(-this.buildSize * 0.5f, -this.buildSize * 0.5f, -this.buildSize * 0.5f);

        this.judgeElement.setTranslation(translation);
        this.judgeElement.setScale(new Vector3f(this.buildSize, this.buildSize * 1.1f, this.buildSize));
        this.judgeElement.setInterpolationDuration(EXPLOSION_DURATION / 3);
        this.judgeElement.startInterpolation();
        this.judgeElement.tick();
    }

    public void shrink() {
        var translation = new Vector3f(this.offset);

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

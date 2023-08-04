package fr.hugman.build_rush.map;

import fr.hugman.build_rush.misc.CachedBlocks;
import net.minecraft.block.Blocks;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.structure.StructurePlacementData;
import net.minecraft.structure.StructureTemplate;
import net.minecraft.util.math.BlockPos;
import xyz.nucleoid.map_templates.BlockBounds;

public final class Plot {
    private final BlockBounds groundBounds;
    private final BlockBounds buildBounds;
    private CachedBlocks groundBlocks;

    public Plot(BlockBounds groundBounds, BlockBounds buildBounds, CachedBlocks groundBlocks) {
        this.groundBounds = groundBounds;
        this.buildBounds = buildBounds;
        this.groundBlocks = groundBlocks;
    }

    public static Plot of(BlockBounds groundBounds) {
        var size = groundBounds.size().getX() + 1;
        var buildBounds = new BlockBounds(groundBounds.min().add(0, 1, 0), groundBounds.max().add(0, size, 0));

        return new Plot(groundBounds, buildBounds, null);
    }

    public BlockBounds groundBounds() {
        return groundBounds;
    }

    public BlockBounds buildBounds() {
        return buildBounds;
    }

    public CachedBlocks blocks() {
        return groundBlocks;
    }

    public void cacheGround(ServerWorld world) {
        this.groundBlocks = CachedBlocks.from(world, this.groundBounds);
    }

    public CachedBlocks cacheBuild(ServerWorld world) {
        return CachedBlocks.from(world, this.buildBounds);
    }

    public void placeGround(ServerWorld world) {
        this.groundBlocks.place(world, this.groundBounds.min());
    }

    public void placeBuild(ServerWorld world, StructureTemplate build) {
        world.playSound(null, BlockPos.ofFloored(this.buildBounds.center()), SoundEvents.ENTITY_ITEM_PICKUP, SoundCategory.BLOCKS, 2.0f, 0.9f);
        boolean hasGround = build.getSize().getY() > this.buildBounds.size().getY();
        var buildPos = hasGround ? this.groundBounds.min() : this.buildBounds.min();
        build.place(world, buildPos, buildPos, new StructurePlacementData(), world.getRandom(), 2);

        BlockBounds barrier = BlockBounds.of(this.groundBounds.min().add(0, -1, 0), this.groundBounds.max().add(0, -1, 0));
        barrier.forEach(pos -> {
            if (world.getBlockState(pos).isAir()) {
                world.setBlockState(pos, Blocks.BARRIER.getDefaultState());
            }
        });
    }

    public void removeGround(ServerWorld world) {
        for (BlockPos pos : this.groundBounds) {
            removeBlock(world, pos);
        }
    }

    public void removeBuild(ServerWorld world) {
        for (BlockPos pos : this.buildBounds) {
            removeBlock(world, pos);
        }
    }

    private static void removeBlock(ServerWorld world, BlockPos pos) {
        if (!world.getBlockState(pos).isAir()) {
            var particlePos = pos.toCenterPos();
            world.spawnParticles(ParticleTypes.CLOUD, particlePos.getX(), particlePos.getY(), particlePos.getZ(), 2, 0.5, 0.5, 0.5, 0.1D);
            world.setBlockState(pos, Blocks.AIR.getDefaultState());
        }
    }
}

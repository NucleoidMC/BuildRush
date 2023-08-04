package fr.hugman.build_rush.misc;

import net.minecraft.block.BlockState;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3i;
import xyz.nucleoid.map_templates.BlockBounds;

import java.util.HashMap;

public class CachedBlocks {
	private final HashMap<Vec3i, BlockState> states;
	private final HashMap<Vec3i, NbtCompound> nbtCompounds;

	public CachedBlocks(HashMap<Vec3i, BlockState> states, HashMap<Vec3i, NbtCompound> nbtCompounds) {
		this.states = states;
		this.nbtCompounds = nbtCompounds;
	}

	public static CachedBlocks from(ServerWorld world, BlockBounds bounds) {
		var size = bounds.size();
		HashMap<Vec3i, BlockState> states = new HashMap<>();
		HashMap<Vec3i, NbtCompound> nbt = new HashMap<>();
		for (int x = 0; x <= size.getX(); x++) {
			for (int y = 0; y <= size.getY(); y++) {
				for (int z = 0; z <= size.getZ(); z++) {
					var targetPos = new BlockPos(x, y, z);
					var sourcePos = bounds.min().add(x, y, z);

					// state
					states.put(targetPos, world.getBlockState(sourcePos));

					// nbt
					var sourceEntity = world.getBlockEntity(sourcePos);
					if (sourceEntity != null) {
						var sourceNbt = sourceEntity.createNbt();
						nbt.put(targetPos, sourceNbt);
					}
				}
			}
		}

		return new CachedBlocks(states, nbt);
	}

	public void place(ServerWorld world, BlockPos origin) {
		for (var entry : this.states.entrySet()) {
			var targetPos = origin.add(entry.getKey());
			var state = entry.getValue();
			world.setBlockState(targetPos, state);
		}
		for (var entry : this.nbtCompounds.entrySet()) {
			var targetPos = origin.add(entry.getKey());
			var nbt = entry.getValue();
			var entity = world.getBlockEntity(targetPos);
			if (entity != null) {
				entity.readNbt(nbt);
			}
		}
	}

	public Iterable<Vec3i> positions() {
		return this.states.keySet();
	}

	public BlockState state(Vec3i pos) {
		return this.states.get(pos);
	}

	public BlockState state(int x, int y, int z) {
		return this.states.get(new Vec3i(x, y, z));
	}

	public NbtCompound nbt(Vec3i pos) {
		return this.nbtCompounds.get(pos);
	}

	public NbtCompound nbt(int x, int y, int z) {
		return this.nbtCompounds.get(new Vec3i(x, y, z));
	}
}

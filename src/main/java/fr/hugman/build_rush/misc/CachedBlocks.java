package fr.hugman.build_rush.misc;

import com.mojang.logging.LogUtils;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.storage.TagValueInput;
import org.slf4j.Logger;
import xyz.nucleoid.map_templates.BlockBounds;

import java.util.HashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

public class CachedBlocks {
	private static final Logger LOGGER = LogUtils.getLogger();

	private final HashMap<Vec3i, BlockState> states;
	private final HashMap<Vec3i, CompoundTag> nbtCompounds;

	public CachedBlocks(HashMap<Vec3i, BlockState> states, HashMap<Vec3i, CompoundTag> nbtCompounds) {
		this.states = states;
		this.nbtCompounds = nbtCompounds;
	}

	public static CachedBlocks from(ServerLevel world, BlockBounds bounds) {
		var size = bounds.size();
		HashMap<Vec3i, BlockState> states = new HashMap<>();
		HashMap<Vec3i, CompoundTag> nbt = new HashMap<>();
		for (int x = 0; x <= size.getX(); x++) {
			for (int y = 0; y <= size.getY(); y++) {
				for (int z = 0; z <= size.getZ(); z++) {
					var targetPos = new BlockPos(x, y, z);
					var sourcePos = bounds.min().offset(x, y, z);

					// state
					states.put(targetPos, world.getBlockState(sourcePos));

					// nbt
					var sourceEntity = world.getBlockEntity(sourcePos);
					if (sourceEntity != null) {
						var sourceNbt = sourceEntity.saveWithoutMetadata(world.registryAccess());
						nbt.put(targetPos, sourceNbt);
					}
				}
			}
		}

		return new CachedBlocks(states, nbt);
	}

	public void place(ServerLevel level, BlockPos origin) {
		for (var entry : this.states.entrySet()) {
			var targetPos = origin.offset(entry.getKey());
			var state = entry.getValue();
			level.setBlockAndUpdate(targetPos, state);
		}
		for (var entry : this.nbtCompounds.entrySet()) {
			var targetPos = origin.offset(entry.getKey());
			var tag = entry.getValue();
			var entity = level.getBlockEntity(targetPos);
			if (entity != null) {
				try (ProblemReporter.ScopedCollector reporter = new ProblemReporter.ScopedCollector(entity.problemPath(), LOGGER)) {
					entity.loadWithComponents(TagValueInput.create(reporter.forChild(entity.problemPath()), level.registryAccess(), tag));
				}
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

	public CompoundTag nbt(Vec3i pos) {
		return this.nbtCompounds.get(pos);
	}

	public CompoundTag nbt(int x, int y, int z) {
		return this.nbtCompounds.get(new Vec3i(x, y, z));
	}
}

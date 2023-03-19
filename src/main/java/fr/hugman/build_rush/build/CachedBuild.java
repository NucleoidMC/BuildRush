package fr.hugman.build_rush.build;

import net.minecraft.block.BlockState;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.Vec3i;

import java.util.HashMap;

public class CachedBuild {
	private final HashMap<Vec3i, BlockState> states;
	private final HashMap<Vec3i, NbtCompound> nbtCompounds;

	public CachedBuild(HashMap<Vec3i, BlockState> states, HashMap<Vec3i, NbtCompound> nbtCompounds) {
		this.states = states;
		this.nbtCompounds = nbtCompounds;
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

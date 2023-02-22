package fr.hugman.build_rush.codec;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Position;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.Vec3i;


public class StringParser {
	public static final Codec<Vec3d> VEC_3D_STRING = Codec.STRING.comapFlatMap(
			string -> {
				try {
					return DataResult.success(StringParser.vec3dFromString(string));
				} catch(IllegalArgumentException e) {
					return DataResult.error("Malformed 3D position string");
				}
			},
			StringParser::toString
	);

	public static final Codec<BlockPos> BLOCK_POS_STRING = Codec.STRING.comapFlatMap(
			string -> {
				try {
					return DataResult.success(StringParser.blockPosFromString(string));
				} catch(IllegalArgumentException e) {
					return DataResult.error("Malformed block position string");
				}
			},
			StringParser::toString
	);

	public static String toString(Position vec) {
		return vec.getX() + "," + vec.getY() + "," + vec.getZ();
	}

	public static String toString(Vec3i pos) {
		return pos.getX() + "," + pos.getY() + "," + pos.getZ();
	}

	public static BlockPos blockPosFromString(String s) throws IllegalArgumentException {
		return new BlockPos(StringParser.vec3iFromString(s));
	}

	public static Vec3i vec3iFromString(String s) throws IllegalArgumentException {
		String[] split = s.split(",");
		if(split.length != 3) {
			throw new IllegalArgumentException("Too many arguments (requires 3)");
		}
		return new Vec3i(Integer.parseInt(split[0]), Integer.parseInt(split[1]), Integer.parseInt(split[2]));
	}

	public static Vec3d vec3dFromString(String s) throws IllegalArgumentException {
		String[] split = s.split(",");
		if(split.length != 3) {
			throw new IllegalArgumentException("Too many arguments (requires 3)");
		}
		return new Vec3d(Double.parseDouble(split[0]), Double.parseDouble(split[1]), Double.parseDouble(split[2]));
	}
}

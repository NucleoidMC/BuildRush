package fr.hugman.build_rush.codec;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import net.minecraft.util.dynamic.Codecs;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.function.Function;

public class BRCodecs {
	public static final Codec<Vec3d> VEC_3D = simpleEither(Vec3d.CODEC, StringParser.VEC_3D_STRING);
	public static final Codec<BlockPos> BLOCK_POS = simpleEither(BlockPos.CODEC, StringParser.BLOCK_POS_STRING);

	public static <S> Codec<S> simpleEither(Codec<S> baseCodec, Codec<S> simpleCodec) {
		return Codecs.xor(baseCodec, simpleCodec).xmap(e -> e.map(Function.identity(), Function.identity()), Either::right);
	}
}

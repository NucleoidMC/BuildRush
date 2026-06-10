package fr.hugman.build_rush.codec;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

public class BRCodecs {
	public static final Codec<Vec3> VEC_3D = simpleEither(Vec3.CODEC, StringParser.VEC_3D_STRING);
	public static final Codec<BlockPos> BLOCK_POS = simpleEither(BlockPos.CODEC, StringParser.BLOCK_POS_STRING);

	public static <S> Codec<S> simpleEither(Codec<S> baseCodec, Codec<S> simpleCodec) {
		return Codec.xor(baseCodec, simpleCodec).xmap(e -> e.map(Function.identity(), Function.identity()), Either::right);
	}
}

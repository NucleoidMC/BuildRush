package fr.hugman.build_rush.title;

import net.minecraft.network.packet.s2c.play.SubtitleS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleFadeS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleS2CPacket;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

public class TitleUtil {
	public static void send(ServerPlayerEntity player, Text title, int fadeInTicks, int stayTicks, int fadeOutTicks) {
		player.networkHandler.sendPacket(new TitleFadeS2CPacket(fadeInTicks, stayTicks, fadeOutTicks));
		player.networkHandler.sendPacket(new TitleS2CPacket(title));
	}

	public static void setSub(ServerPlayerEntity player, Text subtitle) {
		player.networkHandler.sendPacket(new SubtitleS2CPacket(subtitle));
	}

	public static void send(ServerPlayerEntity player, Text title, Text subtitle, int fadeInTicks, int stayTicks, int fadeOutTicks) {
		setSub(player, subtitle);
		send(player, title, fadeInTicks, stayTicks, fadeOutTicks);
	}

	public static void sendSub(ServerPlayerEntity player, Text subtitle, int fadeInTicks, int stayTicks, int fadeOutTicks) {
		setSub(player, subtitle);
		send(player, Text.empty(), fadeInTicks, stayTicks, fadeOutTicks);
	}

	public static void clearSub(ServerPlayerEntity player) {
		setSub(player, Text.empty());
	}
}

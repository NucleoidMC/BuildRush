package fr.hugman.build_rush.text;

import fr.hugman.build_rush.color.ColorUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.level.ServerPlayer;

public class TextUtil {
	public static final int NEUTRAL = 0xadadad;
	public static final int NEUTRAL_S = 0x7d7d7d;
	public static final int DANGER = 0xf54949;
	public static final int DANGER_S = 0xbf1b1b;
	public static final int MEDIUM = 0xe3f23d;
	public static final int MEDIUM_S = 0xd6cc13;
	public static final int SUCCESS = 0x5cf277;
	public static final int SUCCESS_S = 0x1bc239;
	public static final int EPIC = 0xef73f5;
	public static final int EPIC_S = 0xe624f0;
	public static final int LEGENDARY = 0xface3e;
	public static final int LEGENDARY_S = 0xd9a107;

	public static final String DASH = "»";
	public static final String SKULL = "☠";
	public static final String PICKAXE = "⛏";
	public static final String HEALTH = "✚";
	public static final String SUN = "☀";
	public static final String UMBRELLA = "☂";
	public static final String CLOUD = "☁";
	public static final String MUSIC = "♫";
	public static final String HEART = "❤";
	public static final String STAR = "★";
	public static final String DOT = "•";
	public static final String TIME = "⌚";
	public static final String HOURGLASS = "⌛";
	public static final String FLAG = "⚐";
	public static final String COMET = "☄";
	public static final String SWORD = "🗡";
	public static final String BOW = "🏹";
	public static final String BELL = "🔔";
	public static final String CHECKMARK = "✔";
	public static final String X = "✘";
	public static final String WARNING = "⚠";

	public static void sendTitle(ServerPlayer player, Component title, int fadeInTicks, int stayTicks, int fadeOutTicks) {
		player.connection.send(new ClientboundSetTitlesAnimationPacket(fadeInTicks, stayTicks, fadeOutTicks));
		player.connection.send(new ClientboundSetTitleTextPacket(title));
	}

	public static void setSubtitle(ServerPlayer player, Component subtitle) {
		player.connection.send(new ClientboundSetSubtitleTextPacket(subtitle));
	}

	public static void sendTitle(ServerPlayer player, Component title, Component subtitle, int fadeInTicks, int stayTicks, int fadeOutTicks) {
		setSubtitle(player, subtitle);
		sendTitle(player, title, fadeInTicks, stayTicks, fadeOutTicks);
	}

	public static void sendSubtitle(ServerPlayer player, Component subtitle, int fadeInTicks, int stayTicks, int fadeOutTicks) {
		setSubtitle(player, subtitle);
		sendTitle(player, Component.empty(), fadeInTicks, stayTicks, fadeOutTicks);
	}

	public static void clearTitle(ServerPlayer player) {
		clearSubtitle(player);
		sendTitle(player, Component.empty(), 0, 0, 0);
	}

	public static void clearSubtitle(ServerPlayer player) {
		setSubtitle(player, Component.empty());
	}

	public static MutableComponent withPrefix(String prefix, int color, boolean bold, Component text) {
		return Component
				.literal(prefix + " ")
				.setStyle(Style.EMPTY.withColor(color).withBold(bold))
				.append(text);
	}

	public static MutableComponent withPrefix(String prefix, int color, Component text) {
		return withPrefix(prefix, color, false, text);
	}

	public static MutableComponent withPrefix(String prefix, Component text) {
		return withPrefix(prefix, TextUtil.NEUTRAL_S, text);
	}

	public static MutableComponent translatable(String prefix, int color, String key, Object... objects) {
		return withPrefix(prefix, darker(color), translatable(color, key, objects));
	}

	public static MutableComponent translatable(int color, String key, Object... objects) {
		return Component.translatable(key, objects).setStyle(Style.EMPTY.withColor(color));
	}

	private static int darker(int color) {
		return switch(color) {
			case NEUTRAL -> NEUTRAL_S;
			case DANGER -> DANGER_S;
			case MEDIUM -> MEDIUM_S;
			case SUCCESS -> SUCCESS_S;
			case EPIC -> EPIC_S;
			case LEGENDARY -> LEGENDARY_S;
			default -> color;
		};
	}

	public static int lerpScoreColor(float delta) {
		return ColorUtil.lerp(delta, TextUtil.DANGER, TextUtil.MEDIUM, TextUtil.SUCCESS);
	}
}

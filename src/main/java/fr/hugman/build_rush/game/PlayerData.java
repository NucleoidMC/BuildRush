package fr.hugman.build_rush.game;

import eu.pb4.polymer.virtualentity.api.ElementHolder;
import eu.pb4.polymer.virtualentity.api.attachment.ChunkAttachment;
import eu.pb4.polymer.virtualentity.api.elements.TextDisplayElement;
import fr.hugman.build_rush.map.BRMapConfig;
import fr.hugman.build_rush.map.Plot;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import xyz.nucleoid.map_templates.BlockBounds;

import java.util.UUID;

public class PlayerData {
	public static final Component DEFAULT_BAR_TITLE = Component.translatable("game.build_rush");

	public Plot plot;
	public boolean eliminated = false;
	public int score = 0;

	public static final int BREAKING_COOLDOWN = 5;
	public int breakingCooldown = 0;

	public final ServerBossEvent bar;

	public static final int PLAYER_NAME_TICKS = 40;
	public ElementHolder playerNameHolder;
	public TextDisplayElement playerNameElement;
	public int playerNameTick = 0;

	public PlayerData(UUID id) {
		this.bar = new ServerBossEvent(id, DEFAULT_BAR_TITLE, BossEvent.BossBarColor.YELLOW, BossEvent.BossBarOverlay.PROGRESS);
	}

	public void tick() {
		playerNameTick++;
		if(this.breakingCooldown > 0) {
			this.breakingCooldown--;
		}
		if(!this.eliminated && playerNameElement != null) {
			if(playerNameTick == PLAYER_NAME_TICKS / 2) {
				playerNameElement.setTranslation(new Vector3f(0, 2, 0));
				playerNameElement.setInterpolationDuration(20);
				playerNameElement.startInterpolation();
				playerNameElement.tick();
			}
			if(playerNameTick >= PLAYER_NAME_TICKS) {
				playerNameTick = 0;
				playerNameElement.setTranslation(new Vector3f(0, 0, 0));
				playerNameElement.setInterpolationDuration(20);
				playerNameElement.startInterpolation();
				playerNameElement.tick();
			}
		}
	}

	public void join(ServerPlayer player) {
		this.bar.addPlayer(player);
	}

	public void leave(ServerPlayer player) {
		this.bar.removePlayer(player);
	}

	public void setNameHologramColor(int color) {
		if(playerNameElement == null) return;
		var text = playerNameElement.getText();
		playerNameElement.setText(text.copy().setStyle(text.getStyle().withColor(color)));
		playerNameElement.tick();
	}
}

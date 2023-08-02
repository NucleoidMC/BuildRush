package fr.hugman.build_rush.game;

import eu.pb4.polymer.virtualentity.api.ElementHolder;
import eu.pb4.polymer.virtualentity.api.elements.TextDisplayElement;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.entity.boss.ServerBossBar;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import org.joml.Vector3f;
import xyz.nucleoid.map_templates.BlockBounds;

public class PlayerData {
	public static final Text DEFAULT_BAR_TITLE = Text.translatable("game.build_rush");

	public BlockBounds platform;
	public BlockBounds plot;
	public boolean eliminated = false;
	public int score = 0;

	public static final int BREAKING_COOLDOWN = 5;
	public int breakingCooldown = 0;

	public final ServerBossBar bar;

	public static final int PLAYER_NAME_TICKS = 40;
	public ElementHolder playerNameHolder;
	public TextDisplayElement playerNameElement;
	public int playerNameTick = 0;

	public PlayerData() {
		this.bar = new ServerBossBar(DEFAULT_BAR_TITLE, BossBar.Color.YELLOW, BossBar.Style.PROGRESS);
	}

	public void tick() {
		playerNameTick++;
		if(this.breakingCooldown > 0) {
			this.breakingCooldown--;
		}
		if(!this.eliminated) {
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

	public void join(ServerPlayerEntity player) {
		this.bar.addPlayer(player);
	}

	public void leave(ServerPlayerEntity player) {
		this.bar.removePlayer(player);
	}

	public void setNameHologramColor(int color) {
		var text = this.playerNameElement.getText();
		this.playerNameElement.setText(text.copy().setStyle(text.getStyle().withColor(color)));
		this.playerNameElement.tick();
	}
}

package fr.hugman.build_rush.game;

import net.minecraft.entity.boss.BossBar;
import net.minecraft.entity.boss.ServerBossBar;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import xyz.nucleoid.map_templates.BlockBounds;

public class BRPlayerData {
	public static final int BREAKING_COOLDOWN = 5;
	public static final Text DEFAULT_BAR_TITLE = Text.translatable("game.build_rush");

	public BlockBounds platform;
	public BlockBounds plot;
	public boolean eliminated = false;
	public int breakingCooldown = 0;

	public final ServerBossBar bar;

	public BRPlayerData() {
		this.bar = new ServerBossBar(DEFAULT_BAR_TITLE, BossBar.Color.YELLOW, BossBar.Style.PROGRESS);
	}

	public void tick() {
		if(this.breakingCooldown > 0) {
			this.breakingCooldown--;
		}
	}

	public void join(ServerPlayerEntity player) {
		this.bar.addPlayer(player);
	}

	public void leave(ServerPlayerEntity player) {
		this.bar.removePlayer(player);
	}
}

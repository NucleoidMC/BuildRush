package fr.hugman.build_rush.game;

import xyz.nucleoid.map_templates.BlockBounds;

public class BRPlayerData {
	public static final int BREAKING_COOLDOWN = 5;
	public BlockBounds platform;
	public BlockBounds plot;
	public boolean eliminated = false;
	public int breakingCooldown = 0;

	public void tick() {
		if(this.breakingCooldown > 0) {
			this.breakingCooldown--;
		}
	}
}

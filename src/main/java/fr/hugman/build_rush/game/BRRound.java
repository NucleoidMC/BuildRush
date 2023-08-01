package fr.hugman.build_rush.game;

import fr.hugman.build_rush.game.state.BRActive;
import net.minecraft.util.math.MathHelper;

public class BRRound {
	public static final int MEMORIZE_START = 0;
	public static final int MEMORIZE = 1;
	public static final int BUILD = 2;
	public static final int BUILD_END = 3;
	public static final int ELIMINATION_START = 4;
	public static final int ELIMINATION = 5;
	public static final int END = 6;

	private final BRActive active;
	private int number;
	private int roundTick;
	private int stateTick;
	private int state;
	private final int[] lengths;

	public BRRound(BRActive active, int memorizeTime, int buildTime) {
		this.active = active;
		this.number = 1;
		this.stateTick = 0;
		this.state = -1;
		this.lengths = new int[]{2, memorizeTime, buildTime, 3, 2, 3, 3};
		for(int i = 0; i < this.lengths.length; i++) {
			this.lengths[i] *= 20;
		}
	}

	public int getNumber() {
		return number;
	}

	public int getState() {
		return this.state;
	}

	public int getRoundTick() {
		return roundTick;
	}

	public int getStateTick() {
		return stateTick;
	}

	public int getLength(int state) {
		return this.lengths[state];
	}

	public void setTimes(int complexity) {
		double nerf = Math.pow(number, 2) - 0.5D;
		this.lengths[1] = Math.max(3 * 20, MathHelper.ceil(complexity * 0.4D * 20 - nerf));
		this.lengths[2] = Math.max(5 * 20, MathHelper.ceil(complexity * 0.8D * 20 - nerf));
	}

	public void tick() {
		this.roundTick++;
		this.stateTick++;
		if(this.state == -1 || this.stateTick >= this.lengths[this.state]) {
			this.stateTick = 0;
			this.state++;
			if(this.state >= lengths.length) {
				// Next Round
				this.roundTick = 0;
				this.state = 0;
				this.number++;
			}

			// execute state
			var currentLength = this.lengths[this.state];
			switch(this.state) {
				case MEMORIZE_START -> {
					this.active.removePlayerBuilds();
					this.active.placePlayerPlotGrounds();
					this.active.placeCenterBuildGround();
					this.active.resetPlayers();
					this.active.pickBuild();
					this.active.placeCenterBuild();
					this.active.cacheBuild();
					this.active.setTimes();
					this.active.resetScores();
				}
				case MEMORIZE -> {
					this.active.placePlayerBuilds();
					this.active.removeCenterBuild();
					this.active.placeCenterBuildGround();
				}
				case BUILD -> {
					this.active.removePlayerBuilds();
					this.active.canInteract(true);
					this.active.giveInventory();
				}
				case BUILD_END -> {
					this.active.canInteract(false);
					this.active.clearInventory();
					this.active.spawnJudge();
				}
				case ELIMINATION_START -> {
					this.active.calcPlayerScores();
					this.active.sendScores();
					this.active.rotateJudge(currentLength);
				}
				case ELIMINATION -> {
					this.active.eliminateLast();
					this.active.elimJudge(currentLength);
				}
				case END -> {
					// TODO: send round results?
					this.active.endJudge(currentLength);
				}
			}
		}
	}

	public void skip() {
		this.stateTick = this.lengths[this.state];
	}
}

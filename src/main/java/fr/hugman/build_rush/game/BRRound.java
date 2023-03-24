package fr.hugman.build_rush.game;

import fr.hugman.build_rush.game.state.BRActive;

public class BRRound {
	public static final int MEMORIZE_START    = 0;
	public static final int MEMORIZE          = 1;
	public static final int BUILD             = 2;
	public static final int BUILD_END         = 3;
	public static final int ELIMINATION_START = 4;
	public static final int ELIMINATION       = 5;
	public static final int END               = 6;

	private final BRActive active;
	private int number;
	private int roundTick;
	private int stateTick;
	private int state;
	private final int[] lenghts;

	public BRRound(BRActive active, int memorizeTime, int buildTime) {
		this.active = active;
		this.number = 1;
		this.stateTick = 0;
		this.state = -1;
		this.lenghts = new int[]{2, memorizeTime, buildTime, 3, 2, 3, 3};
		for(int i = 0; i < this.lenghts.length; i++) {
			this.lenghts[i] *= 20;
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
		return this.lenghts[state];
	}

	public void tick() {
		this.roundTick++;
		this.stateTick++;
		if(this.state == -1 || this.stateTick >= this.lenghts[this.state]) {
			this.stateTick = 0;
			this.state++;
			if(this.state >= lenghts.length) {
				// Next Round
				this.roundTick = 0;
				this.state = 0;
				this.number++;
			}

			// execute state
			switch(this.state) {
				case MEMORIZE_START -> {
					this.active.removePlayerBuilds();
					this.active.removeCenterBuild();
					this.active.placePlayerBuildGrounds();
					this.active.placeCenterBuildGround();
					this.active.resetPlayers();
					this.active.pickBuild();
					this.active.placeCenterBuild();
					this.active.cacheBuild();
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
				}
				case ELIMINATION_START -> {
					// In OG game the elder guardian would start spinning here
					this.active.calcPlayerScores();
					this.active.sendScores();
					// TODO: spawn a judge
				}
				case ELIMINATION -> {
					this.active.eliminateLast();
				}
				case END -> {
					// TODO: send round results?
				}
			}
		}
	}
}

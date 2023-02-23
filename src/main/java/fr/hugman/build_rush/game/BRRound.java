package fr.hugman.build_rush.game;

import fr.hugman.build_rush.BuildRush;
import fr.hugman.build_rush.game.state.BRActive;

public class BRRound {
	public static final int MEMORIZE_START    = 0;
	public static final int MEMORIZE          = 1;
	public static final int MEMORIZE_END      = 2;
	public static final int BUILD_START       = 3;
	public static final int BUILD             = 4;
	public static final int BUILD_END         = 5;
	public static final int ELIMINATION_START = 6;
	public static final int ELIMINATION       = 7;
	public static final int END               = 8;

	private final BRActive active;
	private int number;
	private int tick;
	private int state;
	private final int[] lenghts;

	public BRRound(BRActive active, int memorizeTime, int buildTime) {
		this.active = active;
		this.number = 1;
		this.tick = 0;
		this.state = -1;
		this.lenghts = new int[]{2, memorizeTime, 2, 3, buildTime, 3, 2, 3, 3};
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

	public int tick() {
		this.tick++;
		if(this.state == -1 || this.tick >= this.lenghts[this.state]) {
			this.tick = 0;
			this.state++;
			if(this.state >= lenghts.length) {
				// Next Round
				this.state = 0;
				this.number++;
			}

			// execute state
			switch(this.state) {
				case MEMORIZE_START -> {
					if(BuildRush.DEBUG) this.active.sendMessage("memorize_start");
					this.active.removeAlivePlayerPlots();
					this.active.removeCenterPlot();
					this.active.placeAlivePlayerPlotGrounds();
					this.active.placeCenterPlotGround();
					this.active.resetAlivePlayers();
					this.active.pickPlotStructure();
					this.active.placeCenterPlot();
				}
				case MEMORIZE -> {
					if(BuildRush.DEBUG) this.active.sendMessage("memorize");
					this.active.placeAlivePlayerPlots();
					this.active.removeCenterPlot();
					this.active.placeCenterPlotGround();
					// TODO: bossbar countdown
				}
				case MEMORIZE_END -> {
					if(BuildRush.DEBUG) this.active.sendMessage("memorize_end");
					this.active.removeAlivePlayerPlots();
				}
				case BUILD_START -> {
					if(BuildRush.DEBUG) this.active.sendMessage("build_start");
					// TODO: title countdown
				}
				case BUILD -> {
					if(BuildRush.DEBUG) this.active.sendMessage("build");
					this.active.canBuild(true);
					this.active.giveInventory();
					// TODO: bossbar countdown
				}
				case BUILD_END -> {
					if(BuildRush.DEBUG) this.active.sendMessage("build_end");
					this.active.canBuild(false);
					// TODO: empty inventory
					this.active.clearInventory();
				}
				case ELIMINATION_START -> {
					if(BuildRush.DEBUG) this.active.sendMessage("elimination_start");
					// In OG game the elder guardian would start spinning here
					this.active.placeCenterPlot();
				}
				case ELIMINATION -> {
					if(BuildRush.DEBUG) this.active.sendMessage("elimination");
					this.active.eliminateLastPlayer();
				}
				case END -> {
					if(BuildRush.DEBUG) this.active.sendMessage("end");
					// TODO: send round results
				}
			}
		}
		return this.tick;
	}
}

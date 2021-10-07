package splitstree6.algorithms.splits.splits2distances;

import splitstree6.data.DistancesBlock;
import splitstree6.data.SplitsBlock;
import splitstree6.workflow.Algorithm;

public abstract class Splits2Distances extends Algorithm<SplitsBlock, DistancesBlock> {
	public Splits2Distances() {
		super(SplitsBlock.class, DistancesBlock.class);
	}
}

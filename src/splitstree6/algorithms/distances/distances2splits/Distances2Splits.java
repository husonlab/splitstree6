package splitstree6.algorithms.distances.distances2splits;

import splitstree6.data.DistancesBlock;
import splitstree6.data.SplitsBlock;
import splitstree6.sflow.Algorithm;

public abstract class Distances2Splits extends Algorithm<DistancesBlock, SplitsBlock> {
	public Distances2Splits() {
		super(DistancesBlock.class, SplitsBlock.class);
	}
}

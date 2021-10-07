package splitstree6.algorithms.distances.distances2distances;

import splitstree6.data.DistancesBlock;
import splitstree6.workflow.Algorithm;

public abstract class Distances2Distances extends Algorithm<DistancesBlock, DistancesBlock> {
	public Distances2Distances() {
		super(DistancesBlock.class, DistancesBlock.class);
	}
}

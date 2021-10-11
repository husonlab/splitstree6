package splitstree6.algorithms.distances.distances2trees;

import splitstree6.data.DistancesBlock;
import splitstree6.data.TreesBlock;
import splitstree6.sflow.Algorithm;

public abstract class Distances2Trees extends Algorithm<DistancesBlock, TreesBlock> {
	public Distances2Trees() {
		super(DistancesBlock.class, TreesBlock.class);
	}
}

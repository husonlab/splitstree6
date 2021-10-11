package splitstree6.algorithms.trees.trees2distances;

import splitstree6.data.DistancesBlock;
import splitstree6.data.TreesBlock;
import splitstree6.sflow.Algorithm;

public abstract class Trees2Distances extends Algorithm<TreesBlock, DistancesBlock> {
	public Trees2Distances() {
		super(TreesBlock.class, DistancesBlock.class);
	}
}

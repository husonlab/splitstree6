package splitstree6.algorithms.trees.trees2splits;

import splitstree6.data.SplitsBlock;
import splitstree6.data.TreesBlock;
import splitstree6.sflow.Algorithm;

public abstract class Trees2Splits extends Algorithm<TreesBlock, SplitsBlock> {
	public Trees2Splits() {
		super(TreesBlock.class, SplitsBlock.class);
	}
}

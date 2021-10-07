package splitstree6.algorithms.splits.splits2trees;

import splitstree6.data.SplitsBlock;
import splitstree6.data.TreesBlock;
import splitstree6.workflow.Algorithm;

public abstract class Splits2Trees extends Algorithm<SplitsBlock, TreesBlock> {
	public Splits2Trees() {
		super(SplitsBlock.class, TreesBlock.class);
	}
}

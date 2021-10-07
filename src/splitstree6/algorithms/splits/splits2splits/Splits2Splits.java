package splitstree6.algorithms.splits.splits2splits;

import splitstree6.data.SplitsBlock;
import splitstree6.workflow.Algorithm;

public abstract class Splits2Splits extends Algorithm<SplitsBlock, SplitsBlock> {
	public Splits2Splits() {
		super(SplitsBlock.class, SplitsBlock.class);
	}
}

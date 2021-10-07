package splitstree6.algorithms.source.source2splits;

import splitstree6.data.SourceBlock;
import splitstree6.data.SplitsBlock;
import splitstree6.workflow.Algorithm;

public abstract class Sources2Splits extends Algorithm<SourceBlock, SplitsBlock> {
	public Sources2Splits() {
		super(SourceBlock.class, SplitsBlock.class);
	}
}

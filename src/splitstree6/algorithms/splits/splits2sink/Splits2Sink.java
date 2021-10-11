package splitstree6.algorithms.splits.splits2sink;

import splitstree6.data.SinkBlock;
import splitstree6.data.SplitsBlock;
import splitstree6.methods.IgnoredInMethodsText;
import splitstree6.sflow.Algorithm;

public abstract class Splits2Sink extends Algorithm<SplitsBlock, SinkBlock> implements IgnoredInMethodsText {
	public Splits2Sink() {
		super(SplitsBlock.class, SinkBlock.class);
	}
}

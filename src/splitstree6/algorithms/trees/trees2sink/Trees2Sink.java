package splitstree6.algorithms.trees.trees2sink;

import splitstree6.data.SinkBlock;
import splitstree6.data.TreesBlock;
import splitstree6.methods.IgnoredInMethodsText;
import splitstree6.sflow.Algorithm;

public abstract class Trees2Sink extends Algorithm<TreesBlock, SinkBlock> implements IgnoredInMethodsText {
	public Trees2Sink() {
		super(TreesBlock.class, SinkBlock.class);
	}
}

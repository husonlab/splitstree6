package splitstree6.algorithms.trees.trees2trees;

import splitstree6.data.TreesBlock;
import splitstree6.workflow.Algorithm;

public abstract class Trees2Trees extends Algorithm<TreesBlock, TreesBlock> {
	public Trees2Trees() {
		super(TreesBlock.class, TreesBlock.class);
	}
}

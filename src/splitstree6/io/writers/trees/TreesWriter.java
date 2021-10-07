package splitstree6.io.writers.trees;

import splitstree6.data.TreesBlock;
import splitstree6.io.utils.DataWriterBase;


public abstract class TreesWriter extends DataWriterBase<TreesBlock> {
	public TreesWriter() {
		super(TreesBlock.class);
	}
}

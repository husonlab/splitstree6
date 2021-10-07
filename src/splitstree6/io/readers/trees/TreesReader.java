package splitstree6.io.readers.trees;

import splitstree6.data.TreesBlock;
import splitstree6.io.utils.DataReaderBase;


public abstract class TreesReader extends DataReaderBase<TreesBlock> {
	public TreesReader() {
		super(TreesBlock.class);
	}
}

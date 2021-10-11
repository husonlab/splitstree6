package splitstree6.algorithms.source.source2trees;

import splitstree6.data.SourceBlock;
import splitstree6.data.TreesBlock;
import splitstree6.sflow.Algorithm;

public abstract class Sources2Trees extends Algorithm<SourceBlock, TreesBlock> {
	public Sources2Trees() {
		super(SourceBlock.class, TreesBlock.class);
	}
}

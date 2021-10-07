package splitstree6.algorithms.source.source2distances;

import splitstree6.data.DistancesBlock;
import splitstree6.data.SourceBlock;
import splitstree6.workflow.Algorithm;

public abstract class Sources2Distances extends Algorithm<SourceBlock, DistancesBlock> {
	public Sources2Distances() {
		super(SourceBlock.class, DistancesBlock.class);
	}
}

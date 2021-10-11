package splitstree6.algorithms.distances.distances2sink;

import splitstree6.data.DistancesBlock;
import splitstree6.data.SinkBlock;
import splitstree6.methods.IgnoredInMethodsText;
import splitstree6.sflow.Algorithm;

public abstract class Distances2Sink extends Algorithm<DistancesBlock, SinkBlock> implements IgnoredInMethodsText {
	public Distances2Sink() {
		super(DistancesBlock.class, SinkBlock.class);
	}
}

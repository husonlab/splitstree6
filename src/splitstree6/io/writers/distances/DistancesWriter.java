package splitstree6.io.writers.distances;

import splitstree6.data.DistancesBlock;
import splitstree6.io.utils.DataWriterBase;

public abstract class DistancesWriter extends DataWriterBase<DistancesBlock> {
	public DistancesWriter() {
		super(DistancesBlock.class);
	}
}

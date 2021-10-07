package splitstree6.io.readers.distances;

import splitstree6.data.DistancesBlock;
import splitstree6.io.utils.DataReaderBase;

public abstract class DistancesReader extends DataReaderBase<DistancesBlock> {
	public DistancesReader() {
		super(DistancesBlock.class);
	}
}

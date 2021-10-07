package splitstree6.io.readers.splits;

import splitstree6.data.SplitsBlock;
import splitstree6.io.utils.DataReaderBase;

public abstract class SplitsReader extends DataReaderBase<SplitsBlock> {
	public SplitsReader() {
		super(SplitsBlock.class);
	}
}

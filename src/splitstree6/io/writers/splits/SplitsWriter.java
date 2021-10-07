package splitstree6.io.writers.splits;

import splitstree6.data.SplitsBlock;
import splitstree6.io.utils.DataWriterBase;

public abstract class SplitsWriter extends DataWriterBase<SplitsBlock> {
	public SplitsWriter() {
		super(SplitsBlock.class);
	}
}

package splitstree6.io.writers.taxa;

import splitstree6.data.TaxaBlock;
import splitstree6.io.utils.DataWriterBase;

public abstract class TaxaWriter extends DataWriterBase<TaxaBlock> {
	public TaxaWriter() {
		super(TaxaBlock.class);
	}
}

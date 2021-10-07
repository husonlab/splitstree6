package splitstree6.algorithms.taxa.taxa2taxa;

import splitstree6.data.TaxaBlock;
import splitstree6.workflow.Algorithm;

public abstract class Taxa2Taxa extends Algorithm<TaxaBlock, TaxaBlock> {
	public Taxa2Taxa() {
		super(TaxaBlock.class, TaxaBlock.class);
	}
}

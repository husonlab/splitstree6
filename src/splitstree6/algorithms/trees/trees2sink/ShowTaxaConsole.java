package splitstree6.algorithms.trees.trees2sink;

import jloda.util.ProgressListener;
import splitstree6.data.DataBlock;
import splitstree6.data.SinkBlock;
import splitstree6.data.TaxaBlock;
import splitstree6.workflow.Algorithm;

import java.io.IOException;

public class ShowTaxaConsole extends Algorithm<DataBlock, SinkBlock> {
	public ShowTaxaConsole() {
		super(DataBlock.class, SinkBlock.class);
	}

	@Override
	public void compute(ProgressListener progress, TaxaBlock taxaBlock, DataBlock inputData, SinkBlock outputData) throws IOException {
		for (var taxon : taxaBlock.getTaxa()) {
			System.out.println(taxon);
		}
	}


	@Override
	public String getCitation() {
		return null;
	}
}

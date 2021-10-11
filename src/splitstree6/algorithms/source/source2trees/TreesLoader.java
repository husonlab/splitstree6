package splitstree6.algorithms.source.source2trees;

import jloda.util.ProgressListener;
import splitstree6.data.SourceBlock;
import splitstree6.data.TaxaBlock;
import splitstree6.data.TreesBlock;
import splitstree6.sflow.Loader;

import java.io.IOException;

public class TreesLoader extends Loader<SourceBlock, TreesBlock> {

	public TreesLoader() {
		super(SourceBlock.class, TreesBlock.class);
	}

	@Override
	public void load(ProgressListener progress, SourceBlock inputData, TaxaBlock outputTaxa, TreesBlock outputBlock) throws IOException {
		var file = inputData.getSources().get(0);
		for (var reader : getReaders()) {
			if (reader.accepts(file)) {
				reader.read(progress, file, outputTaxa, outputBlock);
				System.err.println("Loaded: Taxa: " + outputTaxa.getInfo() + " Trees: " + outputBlock.getInfo());
				break;
			}
		}
	}

	@Override
	public String getCitation() {
		return null;
	}

	@Override
	public boolean isApplicable(TaxaBlock taxa, SourceBlock sourceBlock) {
		return super.isApplicable(taxa, sourceBlock) && sourceBlock.getSources().size() == 1;
	}
}

package splitstree6.algorithms.source.source2distances;

import jloda.util.ProgressListener;
import splitstree6.data.DistancesBlock;
import splitstree6.data.SourceBlock;
import splitstree6.data.TaxaBlock;
import splitstree6.sflow.Loader;

import java.io.IOException;

public class DistancesLoader extends Loader<SourceBlock, DistancesBlock> {

	public DistancesLoader() {
		super(SourceBlock.class, DistancesBlock.class);
	}

	@Override
	public void load(ProgressListener progress, SourceBlock inputData, TaxaBlock outputTaxa, DistancesBlock outputBlock) throws IOException {
		var file = inputData.getSources().get(0);
		for (var reader : getReaders()) {
			if (reader.accepts(file)) {
				reader.read(progress, file, outputTaxa, outputBlock);
				System.err.println("Loaded: Taxa: " + outputTaxa.getInfo() + " Distances: " + outputBlock.getInfo());
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

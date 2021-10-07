package splitstree6.algorithms.source.source2distances;

import jloda.util.ProgressListener;
import splitstree6.data.DistancesBlock;
import splitstree6.data.SourceBlock;
import splitstree6.data.TaxaBlock;
import splitstree6.io.readers.distances.DistancesReader;
import splitstree6.io.readers.distances.PhylipReader;
import splitstree6.workflow.Loader;

import java.io.IOException;
import java.util.ArrayList;

public class DistancesLoader extends Loader<SourceBlock, DistancesBlock> {
	private final ArrayList<DistancesReader> readers = new ArrayList<>();

	public DistancesLoader() {
		super(SourceBlock.class, DistancesBlock.class);
		// todo: load all by reflection
		readers.add(new PhylipReader());
	}

	@Override
	public void load(ProgressListener progress, SourceBlock inputData, TaxaBlock outputTaxa, DistancesBlock distancesBlock) throws IOException {
		var file = inputData.getSources().get(0);
		for (var reader : readers) {
			if (reader.accepts(file)) {
				reader.read(progress, file, outputTaxa, distancesBlock);
				System.err.println("Loaded: Taxa: " + outputTaxa.getInfo() + " Distances: " + distancesBlock.getInfo());
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

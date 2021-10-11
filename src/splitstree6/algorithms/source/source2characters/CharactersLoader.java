package splitstree6.algorithms.source.source2characters;

import jloda.util.ProgressListener;
import splitstree6.data.CharactersBlock;
import splitstree6.data.SourceBlock;
import splitstree6.data.TaxaBlock;
import splitstree6.sflow.Loader;

import java.io.IOException;

public class CharactersLoader extends Loader<SourceBlock, CharactersBlock> {

	public CharactersLoader() {
		super(SourceBlock.class, CharactersBlock.class);
	}

	@Override
	public void load(ProgressListener progress, SourceBlock inputData, TaxaBlock outputTaxa, CharactersBlock outputBlock) throws IOException {
		var file = inputData.getSources().get(0);
		for (var reader : getReaders()) {
			if (reader.accepts(file)) {
				reader.read(progress, file, outputTaxa, outputBlock);
				System.err.println("Loaded: Taxa: " + outputTaxa.getInfo() + " Characters: " + outputBlock.getInfo());
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

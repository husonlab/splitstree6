package splitstree6.workflow;

import jloda.util.ProgressListener;
import splitstree6.data.DataBlock;
import splitstree6.data.SourceBlock;
import splitstree6.data.TaxaBlock;

import java.io.IOException;

abstract public class Loader<S extends DataBlock, T extends DataBlock> extends Algorithm<S, T> {
	public Loader(Class<S> fromClass, Class<T> toClass) {
		super(fromClass, toClass);
		if (!fromClass.equals(SourceBlock.class))
			throw new RuntimeException("Loader: fromClass!=SourceBlock");
	}

	@Override
	final public void compute(ProgressListener progress, TaxaBlock taxaBlock, S inputData, T outputData) {
	}

	public abstract void load(ProgressListener progress, S sourceBlock, TaxaBlock outputTaxa, T outputData) throws IOException;
}

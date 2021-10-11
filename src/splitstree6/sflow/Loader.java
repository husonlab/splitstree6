package splitstree6.sflow;

import jloda.util.ProgressListener;
import splitstree6.data.SourceBlock;
import splitstree6.data.TaxaBlock;
import splitstree6.io.readers.ImportManager;
import splitstree6.io.utils.DataReaderBase;

import java.io.IOException;
import java.util.ArrayList;

abstract public class Loader<S extends DataBlock, T extends DataBlock> extends Algorithm<S, T> {
	protected final ArrayList<DataReaderBase<T>> readers = new ArrayList<>();

	public Loader(Class<S> fromClass, Class<T> toClass) {
		super(fromClass, toClass);
		if (!fromClass.equals(SourceBlock.class))
			throw new RuntimeException("Loader: fromClass!=SourceBlock");
		readers.addAll(ImportManager.getInstance().getReaders(toClass));
	}

	public ArrayList<DataReaderBase<T>> getReaders() {
		return readers;
	}

	@Override
	final public void compute(ProgressListener progress, TaxaBlock taxaBlock, S inputData, T outputData) {
	}

	public abstract void load(ProgressListener progress, S sourceBlock, TaxaBlock outputTaxa, T outputData) throws IOException;
}

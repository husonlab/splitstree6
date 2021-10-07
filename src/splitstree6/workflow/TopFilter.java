package splitstree6.workflow;

import jloda.util.ProgressListener;
import splitstree6.data.DataBlock;
import splitstree6.data.TaxaBlock;

import java.io.IOException;

public abstract class TopFilter<S extends DataBlock, T extends DataBlock> extends Algorithm<S, T> {

	public TopFilter(Class<S> fromClass, Class<T> toClass) {
		super(fromClass, toClass);
		if (!fromClass.equals(toClass))
			throw new RuntimeException("TopFilter: fromClass!=toClass");
	}

	@Override
	final public void compute(ProgressListener progress, TaxaBlock taxaBlock, S inputData, T outputData) throws IOException {
	}

	public abstract void filter(ProgressListener progress, TaxaBlock originalTaxaBlock, TaxaBlock modifiedTaxaBlock, S inputData, T outputData) throws IOException;

	@Override
	public String getCitation() {
		return null;
	}
}

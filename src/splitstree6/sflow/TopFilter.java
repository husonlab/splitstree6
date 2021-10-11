package splitstree6.sflow;

import jloda.util.ProgressListener;
import splitstree6.data.TaxaBlock;

import java.io.IOException;

/**
 * a workflow node that is used to filter data
 * Daniel Huson, 10.2020
 *
 * @param <S> input data
 * @param <T> output data, must be same class as input data
 */
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

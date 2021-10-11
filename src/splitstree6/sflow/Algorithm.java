package splitstree6.sflow;

import jloda.util.ProgressListener;
import splitstree6.algorithms.taxa.taxa2taxa.Taxa2Taxa;
import splitstree6.data.TaxaBlock;
import splitstree6.sflow.interfaces.HasFromClass;
import splitstree6.sflow.interfaces.HasToClass;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public abstract class Algorithm<S extends DataBlock, T extends DataBlock> extends splitstree6.wflow.Algorithm implements HasFromClass<S>, HasToClass<T> {
	private final Class<S> fromClass;
	private final Class<T> toClass;

	protected Algorithm(Class<S> fromClass, Class<T> toClass) {
		this.fromClass = fromClass;
		this.toClass = toClass;
	}

	public List<String> listOptions() {
		return new ArrayList<>();
	}

	public String getToolTip(String optionName) {
		return null;
	}

	public abstract void compute(ProgressListener progress, TaxaBlock taxaBlock, S inputData, T outputData) throws IOException;

	@Override
	public void compute(ProgressListener progress, Collection<splitstree6.wflow.DataBlock> inputData, Collection<splitstree6.wflow.DataBlock> outputData) throws IOException {
		progress.setTasks("Running", getName());

		var taxaBlock = inputData.stream().filter(d -> d instanceof TaxaBlock).map(d -> (TaxaBlock) d).findFirst().orElse(null);
		var inputBlock = inputData.stream().filter(d -> !(d instanceof TaxaBlock)).map(d -> (S) d).findFirst().orElse(null);
		var outputBlock = outputData.stream().filter(d -> !(d instanceof TaxaBlock)).map(d -> (T) d).findFirst().orElse(null);

		if (this instanceof Taxa2Taxa taxa2taxa) {
			var targetTaxaBlock = outputData.stream().filter(d -> d instanceof TaxaBlock).map(d -> (TaxaBlock) d).findFirst().orElse(null);
			taxa2taxa.compute(progress, null, taxaBlock, targetTaxaBlock);
		} else if (this instanceof TopFilter<S, T> topFilter) {
			var secondTaxaBlock = inputData.stream()
					.filter(d -> d instanceof TaxaBlock).filter(d -> !d.equals(taxaBlock))
					.map(d -> (TaxaBlock) d).findFirst().orElse(null);
			topFilter.filter(progress, taxaBlock, secondTaxaBlock, inputBlock, outputBlock);
		} else if (this instanceof Loader<S, T> source) {
			var targetTaxaBlock = outputData.stream().filter(d -> d instanceof TaxaBlock).map(d -> (TaxaBlock) d).findFirst().orElse(null);
			source.load(progress, inputBlock, targetTaxaBlock, outputBlock);
		} else {
			compute(progress, taxaBlock, inputBlock, outputBlock);
		}
	}

	public String getCitation() {
		return null;
	}

	public Class<S> getFromClass() {
		return fromClass;
	}

	public Class<T> getToClass() {
		return toClass;
	}

	public boolean isApplicable(TaxaBlock taxa, S datablock) {
		return taxa.size() > 0 && datablock.size() > 0;
	}

	public void clear() {
	}
}

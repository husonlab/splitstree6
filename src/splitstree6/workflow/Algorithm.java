/*
 *  Algorithm.java Copyright (C) 2021 Daniel H. Huson
 *
 *  (Some files contain contributions from other authors, who are then mentioned separately.)
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package splitstree6.workflow;

import jloda.util.Basic;
import jloda.util.progress.ProgressListener;
import splitstree6.algorithms.taxa.taxa2taxa.Taxa2Taxa;
import splitstree6.data.TaxaBlock;
import splitstree6.methods.IHasCitations;
import splitstree6.options.IHasOptions;
import splitstree6.options.Option;
import splitstree6.workflow.interfaces.HasFromClass;
import splitstree6.workflow.interfaces.HasToClass;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * SplitsTree algorithm
 * Daniel Huson, 10.2021
 *
 * @param <S>
 * @param <T>
 */
public abstract class Algorithm<S extends DataBlock, T extends DataBlock> extends jloda.fx.workflow.Algorithm implements HasFromClass<S>, HasToClass<T>, IHasOptions, IHasCitations {
	public static final String BLOCK_NAME = "ALGORITHM";

	private final Class<S> fromClass;
	private final Class<T> toClass;

	private AlgorithmNode node;

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
	public void compute(ProgressListener progress, Collection<jloda.fx.workflow.DataBlock> inputData, Collection<jloda.fx.workflow.DataBlock> outputData) throws IOException {
		progress.setTasks("Running", getName());

		var taxaBlock = inputData.stream().filter(d -> d instanceof TaxaBlock).map(d -> (TaxaBlock) d).findFirst().orElse(null);
		var inputBlock = inputData.stream().filter(d -> !(d instanceof TaxaBlock)).map(d -> (S) d).findFirst().orElse(null);
		var outputBlock = outputData.stream().filter(d -> !(d instanceof TaxaBlock)).map(d -> (T) d).findFirst().orElse(null);

		if (this instanceof Taxa2Taxa taxa2taxa) {
			var targetTaxaBlock = outputData.stream().filter(d -> d instanceof TaxaBlock).map(d -> (TaxaBlock) d).findFirst().orElse(null);
			taxa2taxa.compute(progress, null, taxaBlock, targetTaxaBlock);
		} else if (this instanceof DataTaxaFilter<S, T> dataTaxaFilter) {
			var secondTaxaBlock = inputData.stream()
					.filter(d -> d instanceof TaxaBlock).filter(d -> !d.equals(taxaBlock))
					.map(d -> (TaxaBlock) d).findFirst().orElse(null);
			dataTaxaFilter.filter(progress, taxaBlock, secondTaxaBlock, inputBlock, outputBlock);
		} else if (this instanceof DataLoader<S, T> source) {
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

	public String toString() {
		return getName();
	}

	public void reset() {
		try {
			var allOptions = Option.getAllOptions(this);
			var instance = this.getClass().getConstructor().newInstance();
			for (var defaultOption : Option.getAllOptions(instance)) {
				var option = allOptions.stream().filter(o -> o.getName().equals(defaultOption.getName())).findFirst();
				option.ifPresent(value -> value.getProperty().setValue(defaultOption.getProperty().getValue()));
			}
		} catch (Exception ignored) {
		}
	}

	/**
	 * creates a new instance
	 *
	 * @return new instance
	 */
	public Algorithm newInstance() {
		try {
			var algorithm = getClass().getConstructor().newInstance();
			algorithm.setNode(node);
			return algorithm;
		} catch (Exception e) {
			Basic.caught(e);
			return null;
		}
	}

	public AlgorithmNode getNode() {
		return node;
	}

	public void setNode(AlgorithmNode node) {
		this.node = node;
	}
}

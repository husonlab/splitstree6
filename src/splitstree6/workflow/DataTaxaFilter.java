/*
 *  DataTaxaFilter.java Copyright (C) 2021 Daniel H. Huson
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

import jloda.util.ProgressListener;
import splitstree6.data.TaxaBlock;

import java.io.IOException;

/**
 * a workflow node that is used to filter data based on a sub-set of taxa
 * Daniel Huson, 10.2020
 *
 * @param <S> input data
 * @param <T> output data, must be same class as input data
 */
public abstract class DataTaxaFilter<S extends DataBlock, T extends DataBlock> extends Algorithm<S, T> {

	public DataTaxaFilter(Class<S> fromClass, Class<T> toClass) {
		super(fromClass, toClass);
		if (!fromClass.equals(toClass))
			throw new RuntimeException("DataTaxaFilter: fromClass!=toClass");
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

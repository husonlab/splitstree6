/*
 * DistancesBlock.java Copyright (C) 2024 Daniel H. Huson
 *
 * (Some files contain contributions from other authors, who are then mentioned separately.)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package splitstree6.data;

import splitstree6.algorithms.distances.distances2distances.DistancesTaxaFilter;
import splitstree6.workflow.DataBlock;
import splitstree6.workflow.DataTaxaFilter;

public class DistancesBlock extends DataBlock {
	public static final String BLOCK_NAME = "DISTANCES";

	private double[][] distances;

	private DistancesFormat format = new DistancesFormat();

	/**
	 * constructor
	 */
	public DistancesBlock() {
		distances = new double[0][0];
	}

	/**
	 * shallow copy
	 */
	public void copy(DistancesBlock that) {
		distances = that.getDistances();
		format = that.getFormat();
	}

	@Override
	public void clear() {
		super.clear();
		distances = new double[0][0];
	}

	public void setNtax(int n) {
		distances = new double[n][n];
	}

	@Override
	public int size() {
		return distances.length;
	}

	/**
	 * gets the value for i and j
	 *
	 * @param i in range 1..nTax
	 * @param j in range 1..nTax
	 * @return value
	 */
	public double get(int i, int j) {
		return distances[i - 1][j - 1];
	}

	/**
	 * sets the value, 1-based
	 */
	public void set(int i, int j, double value) {
		distances[i - 1][j - 1] = value;
	}

	public int getNtax() {
		return size();
	}

	/**
	 * sets the value for s and t, and t and s, 1-based
	 */
	public void setBoth(int s, int t, double value) {
		distances[s - 1][t - 1] = distances[t - 1][s - 1] = value;
	}

	/**
	 * set distances, change dimensions if necessary. If dimensions are changed, delete variances
	 */
	public void set(double[][] distances) {
		if (this.distances.length != distances.length) {
			this.distances = new double[distances.length][distances.length];
		}

		for (int i = 0; i < distances.length; i++) {
			System.arraycopy(distances[i], 0, this.distances[i], 0, distances.length);
		}
	}


	/**
	 * gets distances, 0-based
	 *
	 * @return distances matrix, 0-based
	 */
	public double[][] getDistances() {
		return distances;
	}

	@Override
	public DataTaxaFilter<DistancesBlock, DistancesBlock> createTaxaDataFilter() {
		return new DistancesTaxaFilter();
	}

	@Override
	public DistancesBlock newInstance() {
		return (DistancesBlock) super.newInstance();
	}

	public DistancesFormat getFormat() {
		return format;
	}

	public void setFormat(DistancesFormat format) {
		this.format = format;
	}

	@Override
	public void updateShortDescription() {
		setShortDescription(String.format("a %,d x %,d distance matrix", getNtax(), getNtax()));
	}

	@Override
	public String getBlockName() {
		return BLOCK_NAME;
	}
}

/*
 *  DistancesBlock.java Copyright (C) 2021 Daniel H. Huson
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

package splitstree6.data;

import splitstree6.algorithms.distances.distances2distances.DistancesTaxaFilter;
import splitstree6.workflow.DataBlock;
import splitstree6.workflow.DataTaxaFilter;

public class DistancesBlock extends DataBlock {
	private double[][] distances;
	private double[][] variances;
	private String varType = "ols";

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
		variances = that.getVariances();
		format = that.getFormat();
	}

	@Override
	public void clear() {
		super.clear();
		distances = new double[0][0];
		variances = null;
	}

	public void setNtax(int n) {
		distances = new double[n][n];
		variances = null;
		setShortDescription(getInfo());
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
	 * gets the variances,  indices 1-based
	 *
	 * @return variances or -1, if not set
	 */
	public double getVariance(int s, int t) {
		if (variances != null)
			return variances[s - 1][t - 1];
		else
			return -1;
	}

	public String getVarType() {
		return varType;
	}

	public void setVarType(String varType) {
		this.varType = varType;
	}

	/**
	 * sets the variances,  indices 1-based
	 */
	public void setVariance(int s, int t, double value) {
		synchronized (this) {
			if (variances == null) {
				variances = new double[distances.length][distances.length];
			}
		}
		variances[s - 1][t - 1] = value;
	}

	public void clearVariances() {
		variances = null;
	}

	public boolean isVariances() {
		return variances != null;
	}

	/**
	 * set distances, change dimensions if necessary. If dimensions are changed, delete variances
	 */
	public void set(double[][] distances) {
		if (this.distances.length != distances.length) {
			this.distances = new double[distances.length][distances.length];
			variances = null;
		}

		for (int i = 0; i < distances.length; i++) {
			System.arraycopy(distances[i], 0, this.distances[i], 0, distances.length);
		}
	}

	/**
	 * set values, change dimensions if necessary
	 */
	public void set(double[][] distances, double[][] variances) {
		if (this.distances == null || this.distances.length != distances.length)
			this.distances = new double[distances.length][distances.length];

		if (this.variances == null || this.variances.length != variances.length)
			this.variances = new double[variances.length][variances.length];

		for (int i = 0; i < distances.length; i++) {
			System.arraycopy(distances[i], 0, this.distances[i], 0, distances.length);
			System.arraycopy(variances[i], 0, this.variances[i], 0, distances.length);
		}
	}

	public double[][] getDistances() {
		return distances;
	}

	public double[][] getVariances() {
		return variances;
	}

	@Override
	public String getInfo() {
		return "a " + getNtax() + "x" + getNtax() + " distance matrix";
	}

	@Override
	public DataTaxaFilter<DistancesBlock, DistancesBlock> createTaxaDataFilter() {
		return new DistancesTaxaFilter(DistancesBlock.class, DistancesBlock.class);
	}

	@Override
	public DistancesBlock newInstance() {
		return (DistancesBlock) super.newInstance();
	}

	public DistancesFormat getFormat() {
		return format;
	}
}

package splitstree6.data;

import splitstree6.algorithms.distances.distances2distances.DistancesTopFilter;
import splitstree6.workflow.TopFilter;

public class DistancesBlock extends DataBlock {
	private double[][] distances;
	private double[][] variances;

	/**
	 * constructor
	 */
	public DistancesBlock() {
		distances = new double[0][0];
	}

	/**
	 * shallow copy
	 *
	 * @param that
	 */
	public void copy(DistancesBlock that) {
		distances = that.getDistances();
		variances = that.getVariances();
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
	 * sets the value
	 *
	 * @param i     in range 1-nTax
	 * @param j     in range 1-nTax
	 * @param value
	 */
	public void set(int i, int j, double value) {
		distances[i - 1][j - 1] = value;
	}

	public int getNtax() {
		return size();
	}

	/**
	 * sets the value for (s,t) and (t,s), indices 1-based
	 *
	 * @param s
	 * @param t
	 * @param value
	 */
	public void setBoth(int s, int t, double value) {
		distances[s - 1][t - 1] = distances[t - 1][s - 1] = value;
	}

	/**
	 * gets the variances,  indices 1-based
	 *
	 * @param s
	 * @param t
	 * @return variances or -1, if not set
	 */
	public double getVariance(int s, int t) {
		if (variances != null)
			return variances[s - 1][t - 1];
		else
			return -1;
	}

	/**
	 * sets the variances,  indices 1-based
	 *
	 * @param s
	 * @param t
	 * @param value
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
	 *
	 * @param distances
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
	 *
	 * @param distances
	 * @param variances
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
	public String getDisplayText() {
		return "Not implemented";
	}

	@Override
	public TopFilter<DistancesBlock, DistancesBlock> createTopFilter() {
		return new DistancesTopFilter(DistancesBlock.class, DistancesBlock.class);
	}
}

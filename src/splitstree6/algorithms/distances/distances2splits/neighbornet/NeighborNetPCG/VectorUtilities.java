/*
 *  VectorUtilities.java Copyright (C) 2022 Daniel H. Huson
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

package splitstree6.algorithms.distances.distances2splits.neighbornet.NeighborNetPCG;

/**
 * A few utility classes for handing arrays of doubles. Maybe this is standard?
 */
public class VectorUtilities {
	/**
	 * Add two arrays of doubles
	 *
	 * @param x array of doubles
	 * @param y array of doubles with the same length as x
	 * @return x+y
	 */
	static public double[] add(double[] x, double[] y) {
		assert x.length == y.length : "Adding arrays with different lengths";
		double[] z = new double[x.length];
		for (int i = 0; i < x.length; i++)
			z[i] = x[i] + y[i];
		return z;
	}

	/**
	 * Subtract one vector from another
	 *
	 * @param x array
	 * @param y array of the same length as x
	 * @return array with x-y.
	 */
	static public double[] minus(double[] x, double[] y) {
		assert x.length == y.length : "Computing difference between vectors of different sizes";
		double[] z = new double[x.length];
		for (int i = 0; i < x.length; i++)
			z[i] = x[i] - y[i];
		return z;
	}

	static public double diff(double[] x, double[] y) {
		double ss = 0.0;
		for (int i = 0; i < x.length; i++)
			ss += (x[i] - y[i]) * (x[i] - y[i]);
		return Math.sqrt(ss);
	}

	static public double norm(double[] x) {
		double ss = 0.0;
		for (int i = 0; i < x.length; i++)
			ss += x[i] * x[i];
		return Math.sqrt(ss);
	}

}

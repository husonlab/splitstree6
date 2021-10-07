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

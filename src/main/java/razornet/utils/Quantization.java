/*
 * Quantization.java Copyright (C) 2025
 *
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
 *
 */

package razornet.utils;

import java.util.*;
import java.util.function.Function;
import java.util.function.ToDoubleFunction;

public final class Quantization {

	public record Result(
			int[][] matrix,
			ToDoubleFunction<Integer> mapDistanceBack, // apply to ANY integer -> approx original-scale double
			Function<Integer, Collection<Integer>> mapNodeBack
	) {
		public double[][] createDoubleMatrix() {
			var doubleMatrix = new double[matrix.length][matrix[0].length];
			for (int i = 0; i < matrix.length; i++) {
				for (int j = 0; j < matrix[0].length; j++) {
					doubleMatrix[i][j] = matrix[i][j];
				}
			}
			return doubleMatrix;
		}
	}

	/**
	 * Pipeline:
	 * (1) Validate (square, finite, diag==0, off-diagonal >=0)
	 * (2) Scale with a SINGLE global fixed-point factor = 2^(significantBits)   (fractional bits)
	 * (3) Double all values so every entry is even
	 * <p>
	 * Then merge nodes whose encoded distance is zero and return the reduced matrix,
	 * a global decoder v -> v/(2*scale), and a map from reduced indices back to original indices.
	 *
	 * @param m      input distance matrix
	 * @param digits number of digits
	 */
	public static Result apply(double[][] m, int digits) {
		if (m == null || m.length == 0) throw new IllegalArgumentException("Matrix must be non-null/non-empty.");
		if (digits < 1) throw new IllegalArgumentException("digits must be >=1.");

		final int n = m.length;
		for (int i = 0; i < n; i++) {
			if (m[i] == null || m[i].length != n) {
				throw new IllegalArgumentException("Matrix must be square; row " + i + " has length " +
												   (m[i] == null ? "null" : m[i].length) + ", expected " + n);
			}
		}

		// Validate values; collect max for overflow-safe scaling.
		final double INT_EPS = 1e-12;
		var maxOffDiag = 0.0;
		var minOffDialog = Double.MAX_VALUE;
		for (int i = 0; i < n; i++) {
			for (int j = 0; j < n; j++) {
				var x = m[i][j];
				if (!Double.isFinite(x))
					throw new IllegalArgumentException("Non-finite value at (" + i + "," + j + "): " + x);
				if (i == j) {
					if (Math.abs(x) > INT_EPS)
						throw new IllegalArgumentException("Diagonal (" + i + "," + j + ") must be 0 but is " + x);
				} else {
					if (x < -INT_EPS)
						throw new IllegalArgumentException("Off-diagonal (" + i + "," + j + ") must be >= 0 but is " + x);
					if (x > maxOffDiag) maxOffDiag = x;
					if (x < minOffDialog) minOffDialog = x;
				}
			}
		}

		var scaler = new AutoScaler(minOffDialog, maxOffDiag, digits);

		// (3) Encode with global scale and then DOUBLE to force even
		var encoded = new int[n][n];
		for (int i = 0; i < n; i++) {
			for (int j = 0; j < n; j++) {
				if (i == j) {
					encoded[i][j] = 0;
				} else {
					var v = scaler.toInt(m[i][j]);
					var even = v << 1; // multiply ALL values by 2
					checkFitsInt(even, i, j);
					encoded[i][j] = (int) even;
				}
			}
		}

		// Union-Find: merge any indices whose encoded distance is 0
		var uf = new UnionFind(n);
		for (int i = 0; i < n; i++) {
			for (int j = i + 1; j < n; j++) {
				if (encoded[i][j] == 0) uf.union(i, j);
			}
		}

		// Components in stable order (by smallest original index)
		Map<Integer, List<Integer>> compToMembers = new LinkedHashMap<>();
		for (int i = 0; i < n; i++) {
			var r = uf.find(i);
			compToMembers.computeIfAbsent(r, k -> new ArrayList<>()).add(i);
		}
		var reps = new ArrayList<>(compToMembers.keySet());
		reps.sort(Comparator.comparingInt(a -> compToMembers.get(a).get(0)));

		final int k = reps.size();
		var reduced = new int[k][k];
		var repIndex = new int[k]; // representative original index per component
		for (int a = 0; a < k; a++) {
			var members = compToMembers.get(reps.get(a));
			repIndex[a] = members.get(0);
		}

		for (int a = 0; a < k; a++) {
			for (int b = 0; b < k; b++) {
				if (a == b) {
					reduced[a][b] = 0;
				} else {
					int i = repIndex[a], j = repIndex[b];
					reduced[a][b] = encoded[i][j];
				}
			}
		}

		// mapNodesBack: reduced id -> unmodifiable sorted list of original ids
		Map<Integer, Collection<Integer>> back = new HashMap<>();
		for (int a = 0; a < k; a++) {
			var list1based = compToMembers.get(reps.get(a)).stream().sorted().map(t -> t + 1).toList();
			back.put(a, list1based);
		}
		Function<Integer, Collection<Integer>> mapNodesBack = newId -> {
			var r = back.get(newId);
			if (r == null) throw new IllegalArgumentException("Unknown reduced id: " + newId);
			return r;
		};

		return new Result(reduced, i -> i == 0 ? 0 : scaler.toDouble(i) / 2, mapNodesBack);
	}

	/* -------------------- helpers -------------------- */

	private static boolean wouldOverflow(double maxVal, double scale) {
		// Need: maxVal * scale * 2 <= Integer.MAX_VALUE
		// Use a conservative check in double.
		return maxVal > 0 && maxVal * scale > (Integer.MAX_VALUE / 2.0);
	}

	private static void checkFitsInt(long val, int i, int j) {
		if (val > Integer.MAX_VALUE || val < Integer.MIN_VALUE) {
			throw new IllegalArgumentException("Encoded value at (" + i + "," + j + ") overflows int: " + val);
		}
	}

	private static final class UnionFind {
		private final int[] parent;
		private final byte[] rank;

		UnionFind(int n) {
			parent = new int[n];
			rank = new byte[n];
			for (int i = 0; i < n; i++) parent[i] = i;
		}

		int find(int x) {
			if (parent[x] != x) parent[x] = find(parent[x]);
			return parent[x];
		}

		void union(int a, int b) {
			int ra = find(a), rb = find(b);
			if (ra == rb) return;
			if (rank[ra] < rank[rb]) parent[ra] = rb;
			else if (rank[ra] > rank[rb]) parent[rb] = ra;
			else {
				parent[rb] = ra;
				rank[ra]++;
			}
		}
	}
}
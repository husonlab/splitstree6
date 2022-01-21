/*
 * Compatibility.java Copyright (C) 2022 Daniel H. Huson
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

package splitstree6.data.parts;

import splitstree6.algorithms.utils.SplitsUtilities;

import java.util.BitSet;
import java.util.List;

/**
 * determines compatibility
 * Daniel Huson, 12/31/16.
 */
public enum Compatibility {
	compatible, cyclic, weaklyCompatible, incompatible, unknown;

	/**
	 * computes the compatiblity of a set of splits
	 *
	 * @param ntax
	 * @param splits
	 * @return compatibility
	 */
	public static Compatibility compute(int ntax, List<ASplit> splits) {
		return compute(ntax, splits, null);
	}

	/**
	 * computes the compatiblity of a set of splits
	 *
	 * @param ntax
	 * @param splits
	 * @param cycle
	 * @return compatibility
	 */
	public static Compatibility compute(int ntax, List<ASplit> splits, int[] cycle) {
		if (isCompatible(splits))
			return compatible;
		else if (isCyclic(ntax, splits, cycle))
			return cyclic;
		else if (ntax < 100 && isWeaklyCompatible(splits))
			return weaklyCompatible;
		else
			return incompatible;
	}

	/**
	 * Determines whether a given splits system is (strongly) compatible
	 *
	 * @param splits the splits object
	 * @return true, if the given splits are (strongly) compatible
	 */
	static public boolean isCompatible(List<ASplit> splits) {
		for (int i = 0; i < splits.size(); i++)
			for (int j = i + 1; j < splits.size(); j++)
				if (!areCompatible(splits.get(i), splits.get(j)))
					return false;
		return true;
	}

	/**
	 * Determines whether a given split is compatible with a list of given ones
	 *
	 * @param splits the splits object
	 * @return true, if the given splits are (strongly) compatible
	 */
	static public boolean isCompatible(ASplit split, List<ASplit> splits) {
		for (ASplit split1 : splits)
			if (!areCompatible(split, split1))
				return false;
		return true;
	}

	/**
	 * determines whether two splits on the same taxa set are compatible
	 *
	 * @param split1
	 * @param split2
	 * @return true, if split1 and split2 are compatible
	 */
	public static boolean areCompatible(ASplit split1, ASplit split2) {
		final BitSet A1 = split1.getA();
		final BitSet B1 = split1.getB();
		final BitSet A2 = split2.getA();
		final BitSet B2 = split2.getB();

		return !A1.intersects(A2) || !A1.intersects(B2) || !B1.intersects(A2) || !B1.intersects(B2);
	}

	/**
	 * Determines whether a given splits system is weakly compatible
	 *
	 * @param splits the splits object
	 * @return true, if the given splits are weakly compatible
	 */
	static public boolean isWeaklyCompatible(List<ASplit> splits) {
		for (int i = 0; i < splits.size(); i++) {
			for (int j = i + 1; j < splits.size(); j++) {
				for (int k = j + 1; k < splits.size(); k++) {
					if (!areWeaklyCompatible(splits.get(i), splits.get(j), splits.get(k)))
						return false;
				}
			}
		}
		return true;
	}

	/**
	 * determines whether three splits on the same taxa set are weakly compatible
	 *
	 * @param split1
	 * @param split2
	 * @return true, if all three are weakly compatible
	 */
	public static boolean areWeaklyCompatible(ASplit split1, ASplit split2, ASplit split3) {
		final BitSet A1 = split1.getA();
		final BitSet B1 = split1.getB();
		final BitSet A2 = split2.getA();
		final BitSet B2 = split2.getB();
		final BitSet A3 = split3.getA();
		final BitSet B3 = split3.getB();

		return !((intersects(A1, A2, A3) && intersects(A1, B2, B3) && intersects(B1, A2, B3) && intersects(B1, B2, A3))
				 || (intersects(B1, B2, B3) && intersects(B1, A2, A3) && intersects(A1, B2, A3) && intersects(A1, A2, B3)));
	}

	/**
	 * do the three  bitsets intersect?
	 *
	 * @param a
	 * @param b
	 * @param c
	 * @return true, if non-empty   intersection
	 */
	private static boolean intersects(BitSet a, BitSet b, BitSet c) {
		for (int i = a.nextSetBit(1); i >= 0; i = a.nextSetBit(i + 1)) {
			if (b.get(i) && c.get(i))
				return true;
		}
		return false;
	}

	/**
	 * Determines whether a given splits system is cyclic
	 *
	 * @param ntax
	 * @param splits the splits object
	 * @return true, if the given splits are cyclic
	 */
	public static boolean isCyclic(int ntax, List<ASplit> splits, int[] cycle) {
		if (cycle == null)
			cycle = SplitsUtilities.computeCycle(ntax, splits);

		int[] inverse = new int[ntax + 1];
		for (int t = 1; t <= ntax; t++)
			inverse[cycle[t]] = t;
		for (ASplit split : splits) {
			final BitSet A;
			if (split.isContainedInA(cycle[1]))     // avoid wraparound
				A = split.getB();
			else
				A = split.getA();

			int minA = ntax;
			int maxA = 1;
			for (int t = 1; t <= ntax; t++) {
				if (split.isContainedInA(t)) {
					if (inverse[t] < minA)
						minA = inverse[t];
					if (inverse[t] > maxA)
						maxA = inverse[t];
				}
			}
			if ((maxA - minA + 1) != A.cardinality()) {
				return false;
			}
		}
		return true;
	}

	/**
	 * gets the compatiblity matrix
	 *
	 * @param splits
	 * @return compatibility matrix
	 */
	public static boolean[][] getCompatibilityMatrix(final List<ASplit> splits) {
		boolean[][] matrix = new boolean[splits.size() + 1][splits.size() + 1];

		for (int i = 1; i <= splits.size(); i++) {
			ASplit s1 = splits.get(i - 1);
			for (int j = i + 1; j <= splits.size(); j++) {
				ASplit s2 = splits.get(j - 1);
				matrix[i][j] = matrix[j][i] = areCompatible(s1, s2);
			}
		}
		return matrix;
	}


}

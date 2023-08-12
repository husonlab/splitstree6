/*
 *  Compatibility.java Copyright (C) 2023 Daniel H. Huson
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

package splitstree6.splits;

import splitstree6.algorithms.distances.distances2splits.neighbornet.NeighborNetCycleSplitsTree4;

import java.util.BitSet;
import java.util.List;

/**
 * determines compatibility
 * Daniel Huson, 12/31/16.
 */
public enum Compatibility {
	compatible, cyclic, weaklyCompatible, incompatible, unknown;

	/**
	 * computes the compatibility of a set of splits
	 *
	 * @return compatibility
	 */
	public static Compatibility compute(int ntax, List<ASplit> splits) {
		return compute(ntax, splits, null);
	}

	/**
	 * computes the compatiblity of a set of splits
	 *
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
				if (!BiPartition.areCompatible(splits.get(i), splits.get(j)))
					return false;
		return true;
	}

	/**
	 * Determines whether a given splits system is (strongly) compatible
	 *
	 * @param splits the splits object
	 * @return true, if the given splits are (strongly) compatible
	 */
	static public boolean isCompatible(ASplit... splits) {
		for (int i = 0; i < splits.length; i++)
			for (int j = i + 1; j < splits.length; j++)
				if (!BiPartition.areCompatible(splits[i], splits[j]))
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
			if (!BiPartition.areCompatible(split, split1))
				return false;
		return true;
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
					if (!BiPartition.areWeaklyCompatible(splits.get(i), splits.get(j), splits.get(k)))
						return false;
				}
			}
		}
		return true;
	}

	/**
	 * Determines whether a given split is weakly compatible will the given ones
	 *
	 * @param splits the splits object
	 * @return true, if the given splits are weakly compatible
	 */
	static public boolean isWeaklyCompatible(ASplit split, List<ASplit> splits) {
		for (int i = 0; i < splits.size(); i++) {
			for (int j = i + 1; j < splits.size(); j++) {
				if (!BiPartition.areWeaklyCompatible(splits.get(i), splits.get(j), split))
					return false;
			}
		}
		return true;
	}

	/**
	 * Determines whether a given splits system is cyclic
	 *
	 * @param splits the splits object
	 * @return true, if the given splits are cyclic
	 */
	public static boolean isCyclic(int ntax, List<ASplit> splits, int[] cycle) {
		var inverse = new int[ntax + 1];
		for (var t = 1; t <= ntax; t++)
			inverse[cycle[t]] = t;
		for (var split : splits) {
			final BitSet A;
			if (split.isContainedInA(cycle[1]))     // avoid wraparound
				A = split.getB();
			else
				A = split.getA();

			var minA = ntax;
			var maxA = 1;
			for (var t = 1; t <= ntax; t++) {
				if (A.get(t)) {
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
	 * @return compatibility matrix
	 */
	public static boolean[][] getCompatibilityMatrix(final List<ASplit> splits) {
		boolean[][] matrix = new boolean[splits.size() + 1][splits.size() + 1];

		for (int i = 1; i <= splits.size(); i++) {
			ASplit s1 = splits.get(i - 1);
			for (int j = i + 1; j <= splits.size(); j++) {
				ASplit s2 = splits.get(j - 1);
				matrix[i][j] = matrix[j][i] = BiPartition.areCompatible(s1, s2);
			}
		}
		return matrix;
	}


}

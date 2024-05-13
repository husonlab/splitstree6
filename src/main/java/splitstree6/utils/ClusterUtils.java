/*
 *  ClusterUtils.java Copyright (C) 2024 Daniel H. Huson
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

package splitstree6.utils;

import jloda.util.BitSetUtils;

import java.util.BitSet;
import java.util.Collection;

/**
 * cluster utilities
 * Daniel Huson, 5.2024
 */
public class ClusterUtils {
	/**
	 * determines whether two clusters are compatible
	 *
	 * @param a first cluster
	 * @param b second cluster
	 * @return true, if one cluster contains the other or if they are disjoint
	 */
	public static boolean compatible(BitSet a, BitSet b) {
		return !a.intersects(b) || BitSetUtils.contains(a, b) || BitSetUtils.contains(b, a);
	}

	/**
	 * determines whether one cluster is compatible with all the other ones
	 *
	 * @param a        one cluster
	 * @param clusters all other ones
	 * @return true, cluster a is compatible with all other clusters
	 */
	public static boolean isCompatibleWithAll(BitSet a, Collection<BitSet> clusters) {
		if (a != null && a.cardinality() > 1) {
			for (var b : clusters) {
				if (!compatible(a, b))
					return false;
			}
		}
		return true;
	}

}

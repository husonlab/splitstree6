/*
 * Utilities.java Copyright (C) 2025 Daniel H. Huson
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
 *
 */

package razornet.utils;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

public class Utilities {

	public static String toString(Iterable<?> items, String separator) {
		if (items == null) return "";
		if (separator == null) separator = ", ";
		StringBuilder sb = new StringBuilder();
		boolean first = true;
		for (Object item : items) {
			if (!first) sb.append(separator);
			if (item != null)
				sb.append(item.toString());
			else
				sb.append("null");
			first = false;
		}
		return sb.toString();
	}

	public static String toString(Object[] items, String separator) {
		if (items == null) return "";
		return toString(List.of(items), separator);
	}

	public static int max(Collection<Integer> collection) {
		return collection.stream().mapToInt(v -> v).max().orElse(0);
	}

	public static Set<Integer> fullIndexSet(int n) {
		var s = new TreeSet<Integer>();
		for (int i = 0; i < n; i++) s.add(i);
		return s;
	}

	public static int[][] copy(int[][] matrix) {
		var copy = new int[matrix.length][matrix.length];
		for (int i = 0; i < matrix.length; i++) {
			System.arraycopy(matrix[i], 0, copy[i], 0, matrix.length);
		}
		return copy;
	}
}


/*
 * Permutations.java Copyright (C) 2025 Daniel H. Huson
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

package splitstree6.xtra.layout;

import jloda.util.StringUtils;

import java.util.*;

/**
 * methods for generating permutations
 * Daniel Huson, 3.2025
 */
public class Permutations {
	/**
	 * generates N permutations of the given list of elements
	 *
	 * @param elements list of elements
	 * @param N        number of random permutations to generate
	 * @param <T>      the objects
	 * @return all N random permuations
	 */
	public static <T> List<List<T>> generateRandomPermutations(List<T> elements, int N) {
		return generateRandomPermutations(elements, N, null);
	}

	/**
	 * generates N permutations of the given list of elements
	 *
	 * @param elements list of elements
	 * @param N        number of random permutations to generate
	 * @param random   the random number generator
	 * @param <T>      the objects
	 * @return all N random permuations
	 */
	public static <T> List<List<T>> generateRandomPermutations(List<T> elements, int N, Random random) {
		var permutations = new HashSet<List<T>>();
		if (random == null) {
			random = new Random();
		}

		while (permutations.size() < N) {
			var shuffled = new ArrayList<T>(elements);
			Collections.shuffle(shuffled, random);
			permutations.add(new ArrayList<>(shuffled)); // Ensure unique permutations
		}
		return new ArrayList<>(permutations);
	}

	/**
	 * generate all permutations
	 *
	 * @param elements the list of elements
	 * @param <T>      the objects
	 * @return all permutations
	 */
	public static <T> List<List<T>> generateAllPermutations(List<T> elements) {
		var result = new ArrayList<List<T>>();
		permute(elements, 0, result);
		if (false) {
			System.err.println("n=" + elements.size() + " perms: " + result.size());
			for (var list : result) {
				System.err.println(StringUtils.toString(list.stream().map(o -> ((jloda.graph.Node) o).getId()).toList(), " "));
			}
		}
		return result;
	}

	private static <T> void permute(List<T> elements, int start, List<List<T>> result) {

		if (start < elements.size() - 1) {
			permute(elements, start + 1, result);

			for (int i = start + 1; i < elements.size(); i++) {
				Collections.swap(elements, start, i);
				permute(elements, start + 1, result);
				Collections.swap(elements, start, i);
			}
		} else {
			result.add(new ArrayList<>(elements));
		}
	}
}

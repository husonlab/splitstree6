/*
 * Quantization.java Copyright (C) 2026 Daniel H. Huson
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

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.function.IntFunction;
import java.util.function.IntToDoubleFunction;

/**
 * Dummy quantization layer:
 * - Converts double distance matrix to int[][] by rounding.
 * - Provides identity-ish back-maps.
 * <p>
 * This is only meant to satisfy compilation of Splitstree's RazorNet access layer.
 */
public final class Quantization {

	private final int[][] matrix;
	private final IntToDoubleFunction mapDistanceBack;
	private final IntFunction<Set<Integer>> mapNodeBack;

	private Quantization(int[][] matrix,
						 IntToDoubleFunction mapDistanceBack,
						 IntFunction<Set<Integer>> mapNodeBack) {
		this.matrix = matrix;
		this.mapDistanceBack = mapDistanceBack;
		this.mapNodeBack = mapNodeBack;
	}

	/**
	 * Dummy implementation:
	 * - significantDigits is ignored
	 * - distances are rounded to nearest int
	 * - node back-map maps id -> singleton set {id+1} (taxa ids are typically 1-based)
	 * - distance back-map maps w -> (double) w
	 */
	public static Quantization apply(double[][] distances, int significantDigits) {
		final int n = (distances == null ? 0 : distances.length);
		final int[][] m = new int[n][n];

		for (int i = 0; i < n; i++) {
			if (distances[i] == null) continue;
			for (int j = 0; j < Math.min(n, distances[i].length); j++) {
				m[i][j] = (int) Math.round(distances[i][j]);
			}
		}

		IntToDoubleFunction mapDistanceBack = (w) -> (double) w;

		IntFunction<Set<Integer>> mapNodeBack = (id) -> {
			// Provide a stable non-empty mapping for ids >= 0
			if (id < 0) return Collections.emptySet();
			Set<Integer> s = new HashSet<>(1);
			s.add(id + 1); // 1-based taxon ids are common in SplitsTree graphs
			return s;
		};

		return new Quantization(m, mapDistanceBack, mapNodeBack);
	}

	public int[][] matrix() {
		return matrix;
	}

	public IntToDoubleFunction mapDistanceBack() {
		return mapDistanceBack;
	}

	public IntFunction<Set<Integer>> mapNodeBack() {
		return mapNodeBack;
	}
}
/*
 * RazorDistances.java Copyright (C) 2025 Daniel H. Huson
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

package splitstree6.algorithms.distances.distances2network.razor2;

import java.util.function.ToDoubleFunction;
import java.util.function.ToIntFunction;

public class RazorDistances {
	public static Result convertToEvenIntegerDistances(double[][] distances) {
		var integerDistances = new int[distances.length][distances[0].length];

		var allIntegers = true;
		var allEven = true;
		loop:
		for (double[] row : distances) {
			for (double val : row) {
				if (Math.abs(val - Math.rint(val)) > 0.00000001) {
					allIntegers = false;
					break loop;
				}
				if (allEven && (int) Math.rint(val) % 2 == 1) {
					allEven = false;
					break;
				}
			}
		}

		ToIntFunction<Double> mapToInt;
		ToDoubleFunction<Integer> mapBack0;

		if (allIntegers) {
			if (allEven) {
				mapToInt = d -> (int) Math.rint(d);
				mapBack0 = i -> (double) i;
			} else {
				mapToInt = d -> 2 * (int) Math.rint(d);
				mapBack0 = i -> 0.5 * i;

			}
		} else {
			var factor = 1000000.0;
			mapToInt = d -> 2 * (int) (Math.rint(factor * d));
			mapBack0 = i -> i / (2 * factor);
		}
		for (var i = 0; i < distances.length; i++) {
			for (var j = 0; j < distances[i].length; j++) {
				integerDistances[i][j] = mapToInt.applyAsInt(distances[i][j]);
			}
		}

		return new Result(integerDistances, mapBack0);
	}

	public record Result(int[][] matrix, ToDoubleFunction<Integer> mapBack) {
	}
}

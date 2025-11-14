/*
 * CompareInputOutputDistances.java Copyright (C) 2025 Daniel H. Huson
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

import java.util.function.ToDoubleBiFunction;

/**
 * compare input and output distances
 * Daniel Huson, 10.2025
 */
public class CompareInputOutputDistances {

	/**
	 * compare integer distances
	 */
	public static void apply(int[][] input, int[][] output) {
		apply(input.length, (a, b) -> input[a][b], (a, b) -> output[a][b]);
	}

	/**
	 * compare double distances
	 */
	public static void apply(double[][] input, double[][] output) {
		apply(input.length, (a, b) -> input[a][b], (a, b) -> output[a][b]);
	}

	/**
	 * compare mixed distances
	 */
	public static void apply(int[][] input, double[][] output) {
		apply(input.length, (a, b) -> input[a][b], (a, b) -> output[a][b]);
	}

	/**
	 * compare supplied distances
	 */
	public static void apply(int n, ToDoubleBiFunction<Integer, Integer> inputValue, ToDoubleBiFunction<Integer, Integer> outputValue) {
		var differences = 0.0;
		var totalDifference = 0.0;
		var totalInput = 0.0;
		var totalOutput = 0.0;
		for (var a = 0; a < n; a++) {
			for (var b = a + 1; b < n; b++) {
				var inputDistance = inputValue.applyAsDouble(a, b);
				totalInput += inputDistance;
				var outputDistance = outputValue.applyAsDouble(a, b);
				totalOutput += outputDistance;
				var diff = Math.abs(inputDistance - outputDistance);
				if (diff > 0.000000001) {
					differences++;
					totalDifference += diff;
				}
			}
		}

		System.err.printf("Total input length: %s output length: %s%n", String.valueOf(totalInput), String.valueOf(totalOutput));
		if (differences == 0)
			System.err.println("All pairwise distances correct");
		else {
			System.err.println("Pairs with incorrect distances: " + differences);
			System.err.printf("Even matrix: Total absolute difference: %s (%s%%)%n", String.valueOf(totalDifference),
					String.valueOf((100.0 * totalDifference) / totalInput));
		}
	}
}

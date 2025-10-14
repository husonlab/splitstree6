/*
 * SimulatedAnnealingMinLA.java Copyright (C) 2025 Daniel H. Huson
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

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.Function;

/**
 * uses simulated annealing to find the best linear ordering of objects
 * Daniel Huson, 3.2025
 *
 * @param <T>
 */
public class SimulatedAnnealingMinLA<T> {
	private double startTemp = 1000.0;
	private double endTemp = 0.01;
	private int iterationsPerTemp = 1000;
	private double coolingRate = 0.95;

	/**
	 * apply simulated annealing to find best ordering of objects
	 *
	 * @param ordering     the initial ordering
	 * @param rand         random number generator
	 * @param costFunction the cost function
	 * @return the best found ordering and its cost
	 */
	public Pair<List<T>> apply(List<T> ordering, Random rand, Function<List<T>, Double> costFunction) {
		var currentOrdering = new ArrayList<>(ordering);
		var n = currentOrdering.size();

		double currentCost = costFunction.apply(currentOrdering);
		if (currentCost == 0)
			return new Pair<>(currentOrdering, 0.0);

		var bestOrdering = new ArrayList<>(ordering);
		var bestCost = currentCost;
		if (false)
			System.err.println("Original cost: " + bestCost);

		var temp = startTemp;

		while (temp > endTemp) {
			for (var iter = 0; iter < iterationsPerTemp; iter++) {
				var i = rand.nextInt(n);
				var j = rand.nextInt(n);
				swap(currentOrdering, i, j);
				var newCost = costFunction.apply(currentOrdering);

				var delta = newCost - currentCost;
				var accept = false;

				if (delta < 0) {
					accept = true;
				} else {
					var probability = Math.exp(-delta / temp);
					if (rand.nextDouble() < probability)
						accept = true;
				}

				if (accept) {
					currentCost = newCost;
					if (newCost < bestCost) {
						bestCost = newCost;
						bestOrdering.clear();
						bestOrdering.addAll(currentOrdering);
					}
				} else {
					// Revert the swap
					swap(currentOrdering, i, j);
				}
			}

			temp *= coolingRate; // Reduce temperature
		}
		if (false)
			System.err.println("Final cost: " + bestCost);
		return new Pair<>(bestOrdering, bestCost);
	}

	private void swap(ArrayList<T> list, int i, int j) {
		var tmp = list.get(i);
		list.set(i, list.get(j));
		list.set(j, tmp);
	}


	public double getStartTemp() {
		return startTemp;
	}

	public void setStartTemp(double startTemp) {
		this.startTemp = startTemp;
	}

	public double getEndTemp() {
		return endTemp;
	}

	public void setEndTemp(double endTemp) {
		this.endTemp = endTemp;
	}

	public int getIterationsPerTemp() {
		return iterationsPerTemp;
	}

	public void setIterationsPerTemp(int iterationsPerTemp) {
		this.iterationsPerTemp = iterationsPerTemp;
	}

	public double getCoolingRate() {
		return coolingRate;
	}

	public void setCoolingRate(double coolingRate) {
		this.coolingRate = coolingRate;
	}

	public record Pair<L>(L list, double score) {
	}
}
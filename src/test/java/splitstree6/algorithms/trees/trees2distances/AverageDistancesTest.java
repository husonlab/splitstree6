/*
 *  AverageDistancesTest.java Copyright (C) 2026 Daniel H. Huson
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

package splitstree6.algorithms.trees.trees2distances;

import jloda.util.progress.ProgressSilent;
import org.junit.jupiter.api.Test;
import splitstree6.data.DistancesBlock;
import splitstree6.data.TreesBlock;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;
import static splitstree6.testing.TestUtils.*;

/**
 * the mean, over a set of trees, of the distance between two taxa in a tree
 */
public class AverageDistancesTest {
	private static DistancesBlock run(TreesBlock trees, int n, boolean topological) throws Exception {
		var algorithm = new AverageDistances();
		algorithm.optionUseTopologicalDistanceProperty().set(topological);
		var distances = new DistancesBlock();
		algorithm.compute(new ProgressSilent(), taxa(n), trees, distances);
		return distances;
	}

	/**
	 * one tree: its path lengths. The distances were once a quarter of that
	 */
	@Test
	void oneTree() throws Exception {
		var trees = trees("((t1:1,t2:2):3,(t3:4,t4:5):6);");
		var d = run(trees, 4, false);
		assertEquals(3, d.get(1, 2), 1e-12);
		assertEquals(1 + 3 + 6 + 4, d.get(1, 3), 1e-12);
		assertEquals(2 + 3 + 6 + 5, d.get(4, 2), 1e-12);
		assertEquals(0, d.get(3, 3));
	}

	@Test
	void severalTrees() throws Exception {
		var random = new Random(51);
		var n = 12;
		for (var count : new int[]{2, 7, 40}) {
			var newick = new String[count];
			for (var i = 0; i < count; i++) newick[i] = randomNewick(n, random, true);
			var trees = trees(newick);
			var expected = new double[n][n];
			var edges = new double[n][n];
			for (var tree : trees.getTrees()) {
				var d = pathDistances(tree, n);
				var unit = weightedSplits(tree, n);
				unit.replaceAll((side, weight) -> 1.0);
				var e = metric(unit, n);
				for (var i = 0; i < n; i++)
					for (var j = 0; j < n; j++) {
						expected[i][j] += d[i][j] / count;
						edges[i][j] += e[i][j] / count;
					}
			}
			var found = run(trees, n, false);
			var topological = run(trees, n, true);
			for (var i = 0; i < n; i++)
				for (var j = 0; j < n; j++) {
					assertEquals(expected[i][j], found.get(i + 1, j + 1), 1e-9, count + " trees, taxa " + (i + 1) + ", " + (j + 1));
					assertEquals(edges[i][j], topological.get(i + 1, j + 1), 1e-9, count + " trees, topological");
				}
		}
	}
}

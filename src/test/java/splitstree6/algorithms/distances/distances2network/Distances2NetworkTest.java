/*
 *  Distances2NetworkTest.java Copyright (C) 2026 Daniel H. Huson
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

package splitstree6.algorithms.distances.distances2network;

import jloda.util.progress.ProgressSilent;
import org.junit.jupiter.api.Test;
import splitstree6.data.NetworkBlock;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;
import static splitstree6.testing.TestUtils.*;

/**
 * the minimum spanning network and principal coordinates analysis
 */
public class Distances2NetworkTest {
	static NetworkBlock minSpanningNetwork(double[][] d, boolean tree) throws Exception {
		var algorithm = new MinSpanningNetwork();
		algorithm.setOptionMinSpanningTree(tree);
		var network = new NetworkBlock();
		algorithm.compute(new ProgressSilent(), taxa(d.length), distances(d), network, null);
		return network;
	}

	/**
	 * are a and b connected by edges that are strictly shorter than the threshold?
	 */
	static boolean connectedBelow(double[][] d, int a, int b, double threshold) {
		var n = d.length;
		var seen = new boolean[n];
		var stack = new ArrayDeque<Integer>();
		stack.push(a);
		seen[a] = true;
		while (!stack.isEmpty()) {
			var v = stack.pop();
			if (v == b) return true;
			for (var w = 0; w < n; w++)
				if (!seen[w] && w != v && d[v][w] < threshold) {
					seen[w] = true;
					stack.push(w);
				}
		}
		return false;
	}

	/**
	 * points on a line at -2, 0, 1 and 2 have one minimum spanning tree, with three edges. The network once had a
	 * fourth, between the points at 0 and 2
	 */
	@Test
	void minSpanningNetworkOfPointsOnALine() throws Exception {
		var x = new double[]{-2, 0, 1, 2};
		var d = new double[4][4];
		for (var i = 0; i < 4; i++) for (var j = 0; j < 4; j++) d[i][j] = Math.abs(x[i] - x[j]);
		for (var tree : new boolean[]{false, true}) {
			var graph = minSpanningNetwork(d, tree).getGraph();
			var edges = new HashSet<String>();
			for (var e : graph.edges()) {
				var s = graph.getTaxon(e.getSource());
				var t = graph.getTaxon(e.getTarget());
				edges.add(Math.min(s, t) + "-" + Math.max(s, t) + ":" + graph.getWeight(e));
			}
			assertEquals(new HashSet<>(java.util.List.of("1-2:2.0", "2-3:1.0", "3-4:1.0")), edges, "MinSpanningTree=" + tree);
		}
	}

	/**
	 * the minimum spanning network is the union of all minimum spanning trees (Bandelt et al. 1999). An edge lies in
	 * some minimum spanning tree if and only if its ends are not connected by strictly shorter edges.
	 * Half of the edges of the network were once in no minimum spanning tree
	 */
	@Test
	void minSpanningNetworkIsTheUnionOfAllMinimumSpanningTrees() throws Exception {
		var random = new Random(5);
		var inputs = 0;
		for (var r = 0; r < 30; r++) {
			var n = 20;
			var sequences = new int[n][30]; // binary sequences, each a copy of an earlier one with one to three changes
			for (var t = 1; t < n; t++) {
				sequences[t] = sequences[random.nextInt(t)].clone();
				for (var k = 0; k < 1 + random.nextInt(3); k++) sequences[t][random.nextInt(30)] ^= 1;
			}
			var d = new double[n][n];
			var distinct = true;
			for (var i = 0; i < n; i++)
				for (var j = 0; j < n; j++) {
					for (var k = 0; k < 30; k++) if (sequences[i][k] != sequences[j][k]) d[i][j]++;
					if (i != j && d[i][j] == 0) distinct = false;
				}
			if (!distinct) continue; // identical sequences are a case of their own
			inputs++;
			var expected = new HashSet<String>();
			for (var i = 0; i < n; i++) for (var j = i + 1; j < n; j++) if (!connectedBelow(d, i, j, d[i][j])) expected.add((i + 1) + "-" + (j + 1));
			var graph = minSpanningNetwork(d, false).getGraph();
			var found = new HashSet<String>();
			for (var e : graph.edges()) {
				var s = graph.getTaxon(e.getSource());
				var t = graph.getTaxon(e.getTarget());
				found.add(Math.min(s, t) + "-" + Math.max(s, t));
				assertEquals(d[s - 1][t - 1], graph.getWeight(e), 1e-12, "edge weight");
			}
			assertEquals(expected, found, "input " + r);
			assertEquals(n - 1, minSpanningNetwork(d, true).getGraph().getNumberOfEdges(), "edges of the minimum spanning tree, input " + r);
		}
		assertTrue(inputs >= 10, "only " + inputs + " inputs without identical sequences");
	}

	static double[][] pointsInThePlane(int n, Random random) {
		var x = new double[n];
		var y = new double[n];
		for (var i = 0; i < n; i++) {
			x[i] = random.nextDouble();
			y[i] = random.nextDouble();
		}
		var d = new double[n][n];
		for (var i = 0; i < n; i++) for (var j = 0; j < n; j++) d[i][j] = Math.hypot(x[i] - x[j], y[i] - y[j]);
		return d;
	}

	/**
	 * the distances between points in the plane are reproduced exactly by the first two principal coordinates, and there
	 * is no third
	 */
	@Test
	void pcoaReproducesTheDistancesOfPointsInThePlane() throws Exception {
		var n = 30;
		var d = pointsInThePlane(n, new Random(10));
		var pcoa = new PCOA();
		var network = new NetworkBlock();
		pcoa.compute(new ProgressSilent(), taxa(n), distances(d), network);
		var graph = network.getGraph();
		var xy = new double[n][2];
		for (var v : graph.nodes()) {
			xy[graph.getTaxon(v) - 1][0] = Double.parseDouble(network.getNodeData(v).get("x")) / 100;
			xy[graph.getTaxon(v) - 1][1] = Double.parseDouble(network.getNodeData(v).get("y")) / 100;
		}
		for (var i = 0; i < n; i++)
			for (var j = i + 1; j < n; j++)
				assertEquals(d[i][j], Math.hypot(xy[i][0] - xy[j][0], xy[i][1] - xy[j][1]), 1e-9 * d[i][j] + 1e-12, i + "," + j);
		assertEquals(2, pcoa.getEigenValues().length, "positive eigenvalues"); // rounding noise once counted as 16
	}

	/**
	 * PCoA once failed on one taxon and on distances that are all zero, and overwrote its options when there were fewer
	 * coordinates than asked for
	 */
	@Test
	void pcoaOnSmallAndDegenerateInput() throws Exception {
		for (var entry : degenerateDistances().entrySet()) {
			var d = entry.getValue();
			var pcoa = new PCOA();
			assertDoesNotThrow(() -> pcoa.compute(new ProgressSilent(), taxa(d.length), distances(d), new NetworkBlock()), entry.getKey());
			assertEquals(1, pcoa.getOptionFirstCoordinate(), entry.getKey());
			assertEquals(2, pcoa.getOptionSecondCoordinate(), entry.getKey());
		}
	}

	@Test
	void minSpanningNetworkOnSmallAndDegenerateInput() {
		for (var entry : degenerateDistances().entrySet())
			for (var tree : new boolean[]{false, true})
				assertDoesNotThrow(() -> minSpanningNetwork(entry.getValue(), tree), entry.getKey());
	}
}

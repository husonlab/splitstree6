/*
 *  Network2NetworkTest.java Copyright (C) 2026 Daniel H. Huson
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

package splitstree6.algorithms.network.network2network;

import jloda.graph.Node;
import jloda.util.progress.ProgressSilent;
import org.junit.jupiter.api.Test;
import splitstree6.data.NetworkBlock;
import splitstree6.data.TaxaBlock;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static splitstree6.testing.TestUtils.*;

/**
 * the filters that remove edges or taxa from a network
 */
public class Network2NetworkTest {
	/**
	 * a network on n taxa: a random tree with random lengths, as a path through the taxa plus extra edges, such that
	 * every edge is a shortest path between its ends
	 */
	private static NetworkBlock network(int n, int extraEdges, Random random) {
		var block = new NetworkBlock();
		block.setNetworkType(NetworkBlock.Type.DistanceNetwork);
		var graph = block.getGraph();
		var nodes = new Node[n + 1];
		var x = new double[n + 1];
		var y = new double[n + 1];
		for (var t = 1; t <= n; t++) {
			nodes[t] = graph.newNode();
			graph.addTaxon(nodes[t], t);
			graph.setLabel(nodes[t], "t" + t);
			x[t] = random.nextDouble();
			y[t] = random.nextDouble();
		}
		// edges between points in the plane, with their Euclidean length
		for (var t = 2; t <= n; t++) graph.setWeight(graph.newEdge(nodes[t - 1], nodes[t]), Math.hypot(x[t] - x[t - 1], y[t] - y[t - 1]));
		for (var k = 0; k < extraEdges; k++) {
			int a = 1 + random.nextInt(n), b = 1 + random.nextInt(n);
			if (a != b && !nodes[a].isAdjacent(nodes[b])) graph.setWeight(graph.newEdge(nodes[a], nodes[b]), Math.hypot(x[a] - x[b], y[a] - y[b]));
		}
		return block;
	}

	private static double[][] distances(NetworkBlock block, int n) {
		var graph = block.getGraph();
		var nodes = new ArrayList<Node>();
		for (var v : graph.nodes()) nodes.add(v);
		var m = nodes.size();
		var d = new double[m][m];
		for (var row : d) Arrays.fill(row, Double.POSITIVE_INFINITY);
		for (var i = 0; i < m; i++) d[i][i] = 0;
		for (var e : graph.edges()) {
			int i = nodes.indexOf(e.getSource()), j = nodes.indexOf(e.getTarget());
			d[i][j] = d[j][i] = Math.min(d[i][j], graph.getWeight(e));
		}
		for (var k = 0; k < m; k++) for (var i = 0; i < m; i++) for (var j = 0; j < m; j++) d[i][j] = Math.min(d[i][j], d[i][k] + d[k][j]);
		var result = new double[n][n];
		for (var row : result) Arrays.fill(row, Double.NaN);
		for (var i = 0; i < m; i++)
			for (var j = 0; j < m; j++)
				if (graph.hasTaxa(nodes.get(i)) && graph.hasTaxa(nodes.get(j))) result[graph.getTaxon(nodes.get(i)) - 1][graph.getTaxon(nodes.get(j)) - 1] = d[i][j];
		return result;
	}

	/**
	 * no distance between two taxa grows by more than the given percentage, and at 0 percent nothing changes
	 */
	@Test
	void stretchFilter() throws Exception {
		var random = new Random(121);
		for (var r = 0; r < 10; r++) {
			var n = 12;
			var input = network(n, 25, random);
			var before = distances(input, n);
			var edgesBefore = input.getNumberOfEdges();
			var previous = edgesBefore;
			for (var percent : new double[]{0, 5, 20, 100}) {
				var filter = new StretchFilter();
				filter.setOptionMaxStretchPercent(percent);
				var output = new NetworkBlock();
				filter.compute(new ProgressSilent(), taxa(n), input, output);
				assertEquals(edgesBefore, input.getNumberOfEdges(), "the input is changed");
				var after = distances(output, n);
				for (var i = 0; i < n; i++)
					for (var j = 0; j < n; j++) {
						assertTrue(after[i][j] >= before[i][j] - 1e-9, "a distance got shorter");
						assertTrue(after[i][j] <= before[i][j] * (1 + percent / 100) + 1e-9, percent + " percent: distance " + before[i][j] + " became " + after[i][j]);
					}
				if (percent == 0) assertEquals(edgesBefore, output.getNumberOfEdges());
				assertTrue(output.getNumberOfEdges() <= previous + 2, "more stretch allowed, yet many more edges");
				previous = output.getNumberOfEdges();
				assertTrue(output.getNumberOfEdges() >= n - 1);
			}
		}
	}

	/**
	 * a triangle with sides 1, 1 and 1.9: the long side can go if distances may grow by 2/1.9 - 1 = 5.3 percent
	 */
	@Test
	void stretchFilterOnATriangle() throws Exception {
		var input = new NetworkBlock();
		input.setNetworkType(NetworkBlock.Type.DistanceNetwork);
		var graph = input.getGraph();
		var nodes = new Node[4];
		for (var t = 1; t <= 3; t++) {
			nodes[t] = graph.newNode();
			graph.addTaxon(nodes[t], t);
			graph.setLabel(nodes[t], "t" + t);
		}
		graph.setWeight(graph.newEdge(nodes[1], nodes[2]), 1);
		graph.setWeight(graph.newEdge(nodes[2], nodes[3]), 1);
		graph.setWeight(graph.newEdge(nodes[1], nodes[3]), 1.9);
		for (var percent : new double[]{5, 6}) {
			var filter = new StretchFilter();
			filter.setOptionMaxStretchPercent(percent);
			var output = new NetworkBlock();
			filter.compute(new ProgressSilent(), taxa(3), input, output);
			assertEquals(percent < 5.3 ? 3 : 2, output.getNumberOfEdges(), percent + " percent");
			assertEquals(percent < 5.3 ? 1.9 : 2.0, distances(output, 3)[0][2], 1e-12);
		}
	}

	/**
	 * the shave filter removes edges down to a target and keeps all taxa connected
	 */
	@Test
	void shaveFilter() throws Exception {
		var random = new Random(122);
		for (var r = 0; r < 5; r++) {
			var n = 10;
			var input = network(n, 20, random);
			var before = distances(input, n);
			var edgesBefore = input.getNumberOfEdges();

			var filter = new ShaveFilter();
			filter.optionTargetProperty().set(ShaveFilter.Target.None);
			var output = new NetworkBlock();
			filter.compute(new ProgressSilent(), taxa(n), input, output);
			assertEquals(edgesBefore, output.getNumberOfEdges(), "target None changes nothing");

			filter = new ShaveFilter();
			filter.optionTargetProperty().set(ShaveFilter.Target.MaxCycles);
			filter.optionTargetValueProperty().set(0);
			output = new NetworkBlock();
			filter.compute(new ProgressSilent(), taxa(n), input, output);
			assertEquals(output.getNumberOfNodes() - 1, output.getNumberOfEdges(), "no cycles: a tree");

			filter = new ShaveFilter();
			filter.optionTargetProperty().set(ShaveFilter.Target.MaxEdges);
			filter.optionTargetValueProperty().set(edgesBefore - 3);
			output = new NetworkBlock();
			filter.compute(new ProgressSilent(), taxa(n), input, output);
			assertTrue(output.getNumberOfEdges() <= edgesBefore - 3, "at most " + (edgesBefore - 3) + " edges, found " + output.getNumberOfEdges());

			for (var target : ShaveFilter.Target.values()) {
				filter = new ShaveFilter();
				filter.optionTargetProperty().set(target);
				filter.optionTargetValueProperty().set(10);
				output = new NetworkBlock();
				filter.compute(new ProgressSilent(), taxa(n), input, output);
				assertEquals(edgesBefore, input.getNumberOfEdges(), "the input is changed");
				var after = distances(output, n);
				for (var i = 0; i < n; i++)
					for (var j = 0; j < n; j++) {
						assertTrue(Double.isFinite(after[i][j]), target + ": taxa " + (i + 1) + " and " + (j + 1) + " are not connected");
						assertTrue(after[i][j] >= before[i][j] - 1e-9, target + ": a distance got shorter");
					}
				if (target == ShaveFilter.Target.MaxDeviationPercent) {
					for (var i = 0; i < n; i++) for (var j = 0; j < n; j++) assertTrue(after[i][j] <= 1.1 * before[i][j] + 1e-9, "deviation above 10 percent");
				}
			}
		}
	}

	/**
	 * a tree offers the shave filter nothing to remove: every target, None included, leaves it as it is
	 */
	@Test
	void shaveFilterOnTree() throws Exception {
		var input = network(6, 0, new Random(124));
		for (var target : ShaveFilter.Target.values()) {
			var filter = new ShaveFilter();
			filter.optionTargetProperty().set(target);
			var output = new NetworkBlock();
			filter.compute(new ProgressSilent(), taxa(6), input, output);
			assertEquals(5, output.getNumberOfEdges(), target + ": the tree is changed");
		}
	}

	@Test
	void taxaFilter() {
		var input = network(6, 5, new Random(123));
		var modified = new TaxaBlock();
		modified.addTaxaByNames(List.of("t1", "t3", "t6"));
		var output = new NetworkBlock();
		new NetworkTaxaFilter().filter(new ProgressSilent(), taxa(6), modified, input, output);
		assertEquals(6, input.getNumberOfNodes());
		var labels = new TreeSet<String>();
		for (var v : output.getGraph().nodes()) labels.add(output.getGraph().getLabel(v));
		assertEquals(new TreeSet<>(List.of("t1", "t3", "t6")), labels);
	}
}

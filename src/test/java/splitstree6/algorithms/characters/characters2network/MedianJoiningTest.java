/*
 *  MedianJoiningTest.java Copyright (C) 2026 Daniel H. Huson
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

package splitstree6.algorithms.characters.characters2network;

import jloda.graph.Node;
import jloda.util.progress.ProgressSilent;
import org.junit.jupiter.api.Test;
import splitstree6.data.NetworkBlock;
import splitstree6.data.parts.CharactersType;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static splitstree6.testing.TestUtils.*;

/**
 * median joining (Bandelt, Forster and Röhl 1999) on haplotypes whose network is known
 */
public class MedianJoiningTest {
	private static NetworkBlock run(int epsilon, String... rows) throws Exception {
		var algorithm = new MedianJoining();
		algorithm.setOptionEpsilon(epsilon);
		var network = new NetworkBlock();
		algorithm.compute(new ProgressSilent(), taxa(rows.length), characters(CharactersType.DNA, "acgt", rows), network);
		return network;
	}

	private static String states(NetworkBlock network, Node v) {
		return network.getNodeData(v).get(NetworkBlock.NODE_STATES_KEY);
	}

	private static int differences(String a, String b) {
		var count = 0;
		for (var i = 0; i < a.length(); i++) if (a.charAt(i) != b.charAt(i)) count++;
		return count;
	}

	/**
	 * the length of a shortest path between each two taxa, an edge counting the number of sites at which the sequences
	 * of its two nodes differ
	 */
	private static int[][] pathLengths(NetworkBlock network, int n) {
		var graph = network.getGraph();
		var nodes = new ArrayList<Node>();
		for (var v : graph.nodes()) nodes.add(v);
		var m = nodes.size();
		var d = new int[m][m];
		for (var row : d) Arrays.fill(row, Integer.MAX_VALUE / 4);
		for (var i = 0; i < m; i++) d[i][i] = 0;
		for (var e : graph.edges()) {
			int i = nodes.indexOf(e.getSource()), j = nodes.indexOf(e.getTarget());
			d[i][j] = d[j][i] = differences(states(network, e.getSource()), states(network, e.getTarget()));
		}
		for (var k = 0; k < m; k++) for (var i = 0; i < m; i++) for (var j = 0; j < m; j++) d[i][j] = Math.min(d[i][j], d[i][k] + d[k][j]);
		var result = new int[n][n];
		var nodeOfTaxon = new int[n + 1];
		Arrays.fill(nodeOfTaxon, -1);
		for (var i = 0; i < m; i++) for (var t : graph.getTaxa(nodes.get(i))) nodeOfTaxon[t] = i;
		for (var s = 1; s <= n; s++) {
			assertTrue(nodeOfTaxon[s] >= 0, "taxon " + s + " is on no node");
			for (var t = 1; t <= n; t++) result[s - 1][t - 1] = d[nodeOfTaxon[s]][nodeOfTaxon[t]];
		}
		return result;
	}

	private static void assertPathLengthsAreTheNumbersOfDifferences(NetworkBlock network, String[] rows, String message) {
		var d = pathLengths(network, rows.length);
		for (var s = 0; s < rows.length; s++)
			for (var t = 0; t < rows.length; t++) assertEquals(differences(rows[s], rows[t]), d[s][t], message + ", taxa " + (s + 1) + " and " + (t + 1));
	}

	@Test
	void haplotypesOnALine() throws Exception {
		var rows = new String[]{"aaaa", "caaa", "ccaa", "ccca"};
		var network = run(0, rows);
		assertEquals(4, network.getNumberOfNodes());
		assertEquals(3, network.getNumberOfEdges());
		assertPathLengthsAreTheNumbersOfDifferences(network, rows, "line");
		for (var v : network.getGraph().nodes()) assertEquals(rows[network.getGraph().getTaxon(v) - 1], states(network, v));
		assertEquals(NetworkBlock.Type.HaplotypeNetwork, network.getNetworkType());
	}

	/**
	 * three haplotypes at distance 2 from each other: their median, which is not among them, is added
	 */
	@Test
	void theMedianOfThreeHaplotypesIsAdded() throws Exception {
		var rows = new String[]{"caag", "acag", "aacg"};
		var network = run(0, rows);
		assertEquals(4, network.getNumberOfNodes());
		assertEquals(3, network.getNumberOfEdges());
		var medians = new ArrayList<String>();
		for (var v : network.getGraph().nodes()) if (network.getGraph().getNumberOfTaxa(v) == 0) medians.add(states(network, v));
		assertEquals(List.of("aaag"), medians);
		assertPathLengthsAreTheNumbersOfDifferences(network, rows, "median");
	}

	@Test
	void fourHaplotypesOnASquare() throws Exception {
		var rows = new String[]{"aa", "ac", "ca", "cc"};
		var network = run(0, rows);
		assertEquals(4, network.getNumberOfNodes());
		assertEquals(4, network.getNumberOfEdges());
		assertPathLengthsAreTheNumbersOfDifferences(network, rows, "square");
	}

	/**
	 * a second taxon with the same haplotype is attached to the first by an edge without change
	 */
	@Test
	void identicalHaplotypes() throws Exception {
		var rows = new String[]{"aaaa", "caaa", "aaaa"};
		var network = run(0, rows);
		assertEquals(3, network.getNumberOfNodes());
		assertEquals(2, network.getNumberOfEdges());
		assertPathLengthsAreTheNumbersOfDifferences(network, rows, "identical");
	}

	/**
	 * sites without homoplasy: the network is the tree, and the path between two taxa has as many changes as the two
	 * sequences have differences
	 */
	@Test
	void sitesOfATreeGiveATree() throws Exception {
		var random = new Random(13);
		for (var r = 0; r < 10; r++) {
			var n = 12;
			var truth = new HashMap<BitSet, Double>();
			for (var entry : randomTree(n, random).entrySet()) truth.put(entry.getKey(), (double) (1 + random.nextInt(3)));
			var rows = perfectCharacters(truth, n);
			for (var epsilon : new int[]{0, 2}) {
				var network = run(epsilon, rows);
				assertEquals(network.getNumberOfNodes() - 1, network.getNumberOfEdges(), "tree " + r + ": not a tree");
				assertEquals(n + n - 2, network.getNumberOfNodes(), "tree " + r);
				assertPathLengthsAreTheNumbersOfDifferences(network, rows, "tree " + r + ", epsilon " + epsilon);
			}
		}
	}

	/**
	 * on any data the network connects all taxa, the sequence of a taxon's node is that of the taxon, and no path is
	 * shorter than the number of differences
	 */
	@Test
	void simulatedSequences() throws Exception {
		var random = new Random(14);
		for (var r = 0; r < 10; r++) {
			var rows = simulateSequences(15, 60, 0.03, 0, random);
			var network = run(0, rows);
			var graph = network.getGraph();
			for (var v : graph.nodes()) for (var t : graph.getTaxa(v)) assertEquals(rows[t - 1], states(network, v), "data set " + r + ", taxon " + t);
			var d = pathLengths(network, rows.length);
			for (var s = 0; s < rows.length; s++)
				for (var t = 0; t < rows.length; t++) {
					assertTrue(d[s][t] < Integer.MAX_VALUE / 4, "data set " + r + ": not connected");
					assertTrue(d[s][t] >= differences(rows[s], rows[t]), "data set " + r);
				}
		}
	}

	/**
	 * COMPARED WITH POPART (Leigh and Bryant 2015), by Daniel on 2026-11-21, epsilon 0: the third and the fifth of
	 * five simulated data sets, which are ai/lab/data/median-joining-3.nex and -5.nex. PopART shows the same network
	 * for each, edge by edge: 26 nodes and 27 edges, and 30 nodes and 32 edges; the latter has two triangles, each on
	 * one site with three states, a square, and one median vector with two links. That vector, which lies between
	 * its two neighbors, is not shown as a node here: its two edges of one change each are one edge of two changes
	 * to t5, hence 29 nodes and 31 edges.
	 * For the fifth the program once gave 26 nodes and 29 edges: the first site pattern was counted twice in all
	 * distances, and median vectors with two links were removed, which on this data set went on without end
	 */
	@Test
	void agreesWithPopART() throws Exception {
		var random = new Random(14);
		for (var r = 1; r <= 5; r++) {
			var rows = simulateSequences(15, 60, 0.03, 0, random);
			if (r != 3 && r != 5) continue;
			var network = run(0, rows);
			var graph = network.getGraph();
			assertEquals(r == 3 ? 26 : 29, network.getNumberOfNodes(), "data set " + r + ", nodes");
			assertEquals(r == 3 ? 27 : 31, network.getNumberOfEdges(), "data set " + r + ", edges");
			var changes = new TreeMap<Integer, Integer>(); // number of changes on an edge -> number of such edges
			var twoLinks = 0;
			for (var e : graph.edges()) changes.merge(differences(states(network, e.getSource()), states(network, e.getTarget())), 1, Integer::sum);
			for (var v : graph.nodes()) if (graph.getNumberOfTaxa(v) == 0 && v.getDegree() == 2) twoLinks++;
			if (r == 5) {
				assertEquals(Map.of(1, 17, 2, 10, 3, 3, 5, 1), changes);
				assertEquals(0, twoLinks);
				// the edges at the taxa, as PopART draws them
				var atTaxon = new TreeMap<Integer, List<Integer>>();
				for (var v : graph.nodes())
					for (var t : graph.getTaxa(v)) {
						var list = new ArrayList<Integer>();
						for (var e : v.adjacentEdges()) list.add(differences(states(network, e.getSource()), states(network, e.getTarget())));
						Collections.sort(list);
						atTaxon.put(t, list);
					}
				assertEquals(List.of(1, 5), atTaxon.get(1));
				assertEquals(List.of(1, 1), atTaxon.get(2));
				assertEquals(List.of(1, 1), atTaxon.get(13));
				assertEquals(List.of(2, 2), atTaxon.get(6));
				assertEquals(List.of(3), atTaxon.get(4));
				assertEquals(List.of(2), atTaxon.get(5));
			}
		}
	}

	/**
	 * no node without a taxon has two links, unless its two neighbors are linked or it does not lie between them; and
	 * the lengths of the paths between the taxa are not changed by leaving such nodes out: on sites of a tree they are
	 * the numbers of differences
	 */
	@Test
	void noMedianVectorWithTwoLinks() throws Exception {
		var random = new Random(15);
		for (var r = 0; r < 30; r++) {
			var rows = simulateSequences(12 + r % 8, 60, 0.02 + 0.01 * (r % 3), 0, random);
			for (var epsilon : new int[]{0, 1}) {
				var network = run(epsilon, rows);
				var graph = network.getGraph();
				for (var v : graph.nodes())
					if (graph.getNumberOfTaxa(v) == 0 && v.getDegree() == 2) {
						var u = v.getFirstAdjacentEdge().getOpposite(v);
						var w = v.getLastAdjacentEdge().getOpposite(v);
						var between = differences(states(network, u), states(network, w)) == differences(states(network, u), states(network, v)) + differences(states(network, v), states(network, w));
						assertTrue(u.isAdjacent(w) || !between, "data set " + r + ", epsilon " + epsilon + ": a median vector with two links was left in");
					}
				for (var e : graph.edges()) assertTrue(differences(states(network, e.getSource()), states(network, e.getTarget())) > 0 || graph.getNumberOfTaxa(e.getSource()) > 0 && graph.getNumberOfTaxa(e.getTarget()) > 0, "an edge without change");
				var d = pathLengths(network, rows.length);
				for (var a = 0; a < rows.length; a++)
					for (var b = 0; b < rows.length; b++) assertTrue(d[a][b] >= differences(rows[a], rows[b]) && d[a][b] < Integer.MAX_VALUE / 4);
			}
		}
	}

	@Test
	void degenerateInput() throws Exception {
		assertEquals(1, run(0, "acgt").getNumberOfNodes());
		assertEquals(2, run(0, "acgt", "acgt").getNumberOfNodes());
		assertEquals(2, run(0, "acgt", "aggt").getNumberOfNodes());
		assertEquals(1, run(0, "acgt", "aggt").getNumberOfEdges());
	}
}

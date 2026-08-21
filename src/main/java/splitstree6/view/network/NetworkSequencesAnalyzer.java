/*
 * NetworkSequencesAnalyzer.java Copyright (C) 2026 Daniel H. Huson
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

package splitstree6.view.network;

import jloda.graph.Edge;
import jloda.graph.Node;
import jloda.graph.algorithms.Dijkstra;
import jloda.util.StringUtils;
import splitstree6.data.CharactersBlock;
import splitstree6.data.NetworkBlock;
import splitstree6.data.TaxaBlock;
import splitstree6.data.parts.AmbiguityCodes;

import java.util.HashMap;
import java.util.function.IntFunction;

public record NetworkSequencesAnalyzer(char gapChar, char missingChar, boolean useAmbiguityCodes,
									   boolean ambiguityCharsAreStates) {

	public NetworkSequencesAnalyzer(NetworkBlock networkBlock) {
		this(findCharactersBlock(networkBlock));
	}

	public NetworkSequencesAnalyzer(CharactersBlock charactersBlock) {
		this(charactersBlock.getGapCharacter(), charactersBlock.getMissingCharacter(), charactersBlock.isHasAmbiguityCodes(), false);
	}

	public boolean differ(char a, char b) {
		a = Character.toLowerCase(a);
		b = Character.toLowerCase(b);
		if (ambiguityCharsAreStates) {
			return a != b;
		} else if (a == gapChar || a == missingChar || b == gapChar || b == missingChar) {
			return false;
		} else if (useAmbiguityCodes) {
			// two positions differ iff their possible bases are DISJOINT; overlapping (compatible) codes --
			// including two identical bases, which trivially overlap -- are NOT a difference. (Without the
			// negation, identical bases counted as differences and mismatches as matches: a degenerate,
			// nearly-constant "distance" ~= nchar. Note 'n' is an ambiguity code, so any alignment with n's
			// takes this branch.)
			return !AmbiguityCodes.codesOverlap(a, b);
		} else return a != b;
	}

	public int differences(String sequence1, String sequence2) {
		if (sequence1.length() != sequence2.length())
			throw new IllegalArgumentException("sequences have different lengths");
		var count = 0;
		for (var i = 0; i < sequence1.length(); i++) {
			if (differ(sequence1.charAt(i), sequence2.charAt(i)))
				count++;
		}
		return count;
	}


	public int inputPairwiseDistances(NetworkBlock networkBlock) {
		if (!hasNodeStates(networkBlock))
			throw new IllegalArgumentException("Node sequences required");

		var sum = 0;
		var graph = networkBlock.getGraph();
		for (var v : graph.nodes()) {
			if (graph.hasTaxa(v)) {
				var sequence1 = networkBlock.getNodeData(v).get(NetworkBlock.NODE_STATES_KEY);
				for (var w : graph.nodes(v)) {
					if (graph.hasTaxa(w)) {
						var sequence2 = networkBlock.getNodeData(w).get(NetworkBlock.NODE_STATES_KEY);
						sum += differences(sequence1, sequence2);
					}
				}
			}
		}
		return sum;
	}

	public int totalEdgeDistances(NetworkBlock networkBlock) {
		if (!hasNodeStates(networkBlock))
			throw new IllegalArgumentException("Node sequences required");

		var graph = networkBlock.getGraph();

		var sum = 0;
		for (var e : graph.edges()) {
			var count = mutations(networkBlock, e);
			if (count < 0)
				return -1;
			sum += count;
		}
		return sum;
	}

	public int realizedPairwiseDistances(NetworkBlock networkBlock) {
		if (!hasNodeStates(networkBlock))
			throw new IllegalArgumentException("Node sequences required");

		var graph = networkBlock.getGraph();
		var weights = new HashMap<Edge, Integer>();
		for (var e : graph.edges()) {
			var count = mutations(networkBlock, e);
			if (count < 0)
				return -1;
			weights.put(e, count);
		}

		var sum = 0;
		for (var v : graph.nodes()) {
			if (graph.hasTaxa(v)) {
				for (var w : graph.nodes(v)) {
					if (graph.hasTaxa(w)) {
						// follow the geodesic as drawn (edge weights w(e)) but sum the mutation labels h(e)
						// along it -- consistent with reportAllDifferences(). Summary and per-pair report now
						// use the same geodesic; both diverge from the truth exactly when the labeling's
						// per-edge mutation count h(e) differs from the realization weight w(e).
						var shortestPath = Dijkstra.compute(graph, v, w, graph::getWeight, true);
						Node prev = null;
						var pathLength = 0;
						for (var q : shortestPath) {
							if (prev != null) {
								var e = q.getCommonEdge(prev);
								pathLength += (weights.get(e));
							}
							prev = q;
						}
						sum += pathLength;
					}
				}
			}
		}
		return sum;
	}

	/**
	 * the number of mutations on an edge: the length of its mutation list, or, failing that, the number of sites
	 * at which its two endpoints differ
	 * <p>
	 * The list is the authority because it can outnumber the endpoint differences. An edge that replaces a path
	 * through dissolved degree-2 nodes -- what the Stretch Filter leaves behind -- carries the mutations of the
	 * whole path, and a site that mutated twice along that path appears twice in the list but not at all in the
	 * comparison of its two endpoints. Counting the endpoints there reports fewer mutations than the network
	 * draws and than its edge weights add up to. The fallback keeps unlabeled edges working, and gives the same
	 * answer as the list whenever the labeling itself computed it from the endpoints, which is what every
	 * characters-to-network algorithm here does.
	 *
	 * @return the count, or -1 if the edge has neither a mutation list nor a pair of sequences
	 */
	private int mutations(NetworkBlock networkBlock, Edge e) {
		var sites = networkBlock.getEdgeData(e).get(NetworkBlock.EDGE_SITES_KEY);
		if (sites != null)
			return sites.isBlank() ? 0 : StringUtils.countOccurrences(sites, ',') + 1;
		var sequence1 = networkBlock.getNodeData(e.getSource()).get(NetworkBlock.NODE_STATES_KEY);
		var sequence2 = networkBlock.getNodeData(e.getTarget()).get(NetworkBlock.NODE_STATES_KEY);
		if (sequence1 == null || sequence2 == null)
			return -1;
		return differences(sequence1, sequence2);
	}

	public String computeEdgeLabel(String sequence1, String sequence2, IntFunction<Integer> mapBackIndex) {
		var buf = new StringBuilder();
		for (var i = 0; i < sequence1.length(); i++) {
			if (differ(sequence1.charAt(i), sequence2.charAt(i))) {
				if (!buf.isEmpty())
					buf.append(",");
				buf.append(mapBackIndex.apply(i));
			}
		}
		return buf.toString();
	}

	public void computeHammingDistances(CharactersBlock charactersBlock, double[][] distances) {
		var n = charactersBlock.getNtax();

		for (var i = 0; i < n; i++) {
			var sequence1 = String.valueOf(charactersBlock.getRow0(i));
			for (var j = i + 1; j < n; j++) {
				var sequence2 = String.valueOf(charactersBlock.getRow0(j));
				distances[i][j] = distances[j][i] = differences(sequence1, sequence2);
			}
		}
	}

	public int reportAllDifferences(int s, int t, TaxaBlock taxaBlock, CharactersBlock charactersBlock, NetworkBlock networkBlock) {
		if (!isApplicable(networkBlock))
			throw new IllegalArgumentException("Characters required");

		var diff = 0;

		if (s >= 1 && t >= 1) {

			var inputDifferences = 0;
			{
				var topBuf = new StringBuilder();
				var midBuf = new StringBuilder();
				var botBuf = new StringBuilder();
				for (var pos = 1; pos <= charactersBlock.getNchar(); pos++) {
					var cs = charactersBlock.get(s, pos);
					var ct = charactersBlock.get(t, pos);
					if (differ(cs, ct)) {
						inputDifferences++;
						topBuf.append("%5d".formatted(pos));
						midBuf.append("  %c  ".formatted(cs));
						botBuf.append("  %s  ".formatted(ct));
					}
				}
				System.err.printf("Input differences %s - %s: %,d%n", taxaBlock.getLabel(s), taxaBlock.getLabel(t), inputDifferences);
				System.err.println(topBuf);
				System.err.println(midBuf);
				System.err.println(botBuf);
			}

			var network = networkBlock.getGraph();

			var v = network.nodeStream().filter(u -> network.getTaxon(u) == s).findAny().orElse(null);
			var w = network.nodeStream().filter(u -> network.getTaxon(u) == t).findAny().orElse(null);

			if (v != null && w != null) {
				var shortestPath = Dijkstra.compute(network, v, w, network::getWeight, true);
				Node prev = null;
				var pathDifferences = 0;
				for (var q : shortestPath) {
					if (prev != null) {
						var sp = networkBlock.getNodeData(prev).get(NetworkBlock.NODE_STATES_KEY);
						var sq = networkBlock.getNodeData(q).get(NetworkBlock.NODE_STATES_KEY);
						var qBuff = new StringBuilder("  ");
						for (var i = 0; i < Math.min(sp.length(), sq.length()); i++) {
							if (differ(sq.charAt(i), sp.charAt(i))) {
								pathDifferences++;
								qBuff.append(" %d %c -> %c,".formatted(i + 1, sq.charAt(i), sp.charAt(i)));
							}
						}
						//if(q!=v && q!=w)
						{
							System.err.println(qBuff.toString().toLowerCase());
						}
					}
					prev = q;
				}

				diff = (pathDifferences - inputDifferences);
				if (diff > 0) {
					System.err.println("Path differences larger:  " + pathDifferences + " > " + inputDifferences);
				} else if (diff < 0) {
					System.err.println("Path differences smaller: " + pathDifferences + " < " + inputDifferences);
				}
			}
		}
		return diff;
	}

	/**
	 * can we report on this network in terms of sequences? Requires both the characters the network was
	 * computed from, somewhere up the workflow, and per-node sequences on the network itself.
	 * <p>
	 * This is the test for whether the WORKFLOW can supply what a report needs. The measures themselves only
	 * need the sequences (see {@link #hasNodeStates}), so a caller holding the characters already can compute
	 * them without a workflow at all.
	 */
	public static boolean isApplicable(NetworkBlock networkBlock) {
		return findAncestorCharactersBlock(networkBlock) != null && hasNodeStates(networkBlock);
	}

	/** does the network carry the per-node sequences the measures are computed from? */
	public static boolean hasNodeStates(NetworkBlock networkBlock) {
		var firstNode = networkBlock.getGraph().getFirstNode();
		return firstNode != null && networkBlock.getNodeData(firstNode).containsKey(NetworkBlock.NODE_STATES_KEY);
	}

	public static CharactersBlock findCharactersBlock(NetworkBlock networkBlock) {
		var charactersBlock = findAncestorCharactersBlock(networkBlock);
		if (charactersBlock == null)
			throw new IllegalArgumentException("Characters required");
		return charactersBlock;
	}

	/**
	 * the characters the network was ultimately computed from, or null if there are none
	 * <p>
	 * Walks up the workflow rather than taking exactly two hops, so that a network produced by a
	 * network-to-network algorithm (e.g. the Stretch Filter), whose grandparent block is another network
	 * and not the characters, still reports its total length and excess. The per-node sequences that the
	 * report is computed from survive such a filter, because {@link NetworkBlock#copy} copies node data.
	 */
	private static CharactersBlock findAncestorCharactersBlock(NetworkBlock networkBlock) {
		return networkBlock.findAncestor(CharactersBlock.class);
	}
}


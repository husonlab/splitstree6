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
			return AmbiguityCodes.codesOverlap(a, b);
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
		if (!isApplicable(networkBlock))
			throw new IllegalArgumentException("Characters required");

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
		if (!isApplicable(networkBlock))
			throw new IllegalArgumentException("Characters required");

		var graph = networkBlock.getGraph();

		var sum = 0;
		for (var e : graph.edges()) {
			var sequence1 = networkBlock.getNodeData(e.getSource()).get(NetworkBlock.NODE_STATES_KEY);
			var sequence2 = networkBlock.getNodeData(e.getTarget()).get(NetworkBlock.NODE_STATES_KEY);
			if (sequence1 == null || sequence2 == null)
				return -1;
			sum += differences(sequence1, sequence2);
		}
		return sum;
	}

	public int realizedPairwiseDistances(NetworkBlock networkBlock) {
		if (!isApplicable(networkBlock))
			throw new IllegalArgumentException("Characters required");

		var graph = networkBlock.getGraph();
		var weights = new HashMap<Edge, Integer>();
		for (var e : graph.edges()) {
			var sequence1 = networkBlock.getNodeData(e.getSource()).get(NetworkBlock.NODE_STATES_KEY);
			var sequence2 = networkBlock.getNodeData(e.getTarget()).get(NetworkBlock.NODE_STATES_KEY);
			if (sequence1 == null || sequence2 == null)
				return -1;
			var weight = differences(sequence1, sequence2);
			weights.put(e, weight);
		}

		var sum = 0;
		for (var v : graph.nodes()) {
			if (graph.hasTaxa(v)) {
				for (var w : graph.nodes(v)) {
					if (graph.hasTaxa(w)) {
						var shortestPath = Dijkstra.compute(graph, v, w, weights::get, true);
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
						System.err.println(graph.getLabel(v) + "-" + graph.getLabel(w) + ": " + pathLength);
					}
				}
			}
		}
		return sum;
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

	public static boolean isApplicable(NetworkBlock networkBlock) {
		if (networkBlock.getNode().getPreferredParent().getPreferredParent().getDataBlock() instanceof CharactersBlock charactersBlock)
			return networkBlock.getNodeData(networkBlock.getGraph().getFirstNode()).containsKey(NetworkBlock.NODE_STATES_KEY);
		else return false;
	}

	public static CharactersBlock findCharactersBlock(NetworkBlock networkBlock) {
		if (networkBlock.getNode().getPreferredParent().getPreferredParent().getDataBlock() instanceof CharactersBlock charactersBlock)
			return charactersBlock;
		throw new IllegalArgumentException("Characters required");
	}
}


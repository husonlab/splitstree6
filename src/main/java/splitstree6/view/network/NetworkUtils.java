/*
 * NetworkUtils.java Copyright (C) 2025 Daniel H. Huson
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
import splitstree6.data.DistancesBlock;
import splitstree6.data.NetworkBlock;
import splitstree6.data.parts.AmbiguityCodes;

import java.util.HashMap;


public class NetworkUtils {
	public enum AmbiguousOptions {Wildcard, State}

	public record ComparisonOptions(char gap, char missing, boolean useAmbiguityCodes,
									AmbiguousOptions ambiguousOptions) {
	}

	public static void computeHammingDistances(CharactersBlock charactersBlock, double[][] distances, ComparisonOptions comparisonOptions) {
		var n = charactersBlock.getNtax();

		for (var i = 0; i < n; i++) {
			var a = String.valueOf(charactersBlock.getRow0(i));
			for (var j = i + 1; j < n; j++) {
				var b = String.valueOf(charactersBlock.getRow0(j));
				distances[i][j] = distances[j][i] = compareSequences(a, b, comparisonOptions);
			}
		}
	}

	public static int compareSequences(String a, String b, ComparisonOptions comparisonOptions) {
		var m = a.length();
		var count = 0;

		for (var pos = 0; pos < m; pos++) {
			var ci = Character.toLowerCase(a.charAt(pos));
			var cj = Character.toLowerCase(b.charAt(pos));
			if (differ(ci, cj, comparisonOptions)) {
				count++;
			}
		}
		return count;
	}

	public static boolean differ(char ci, char cj, ComparisonOptions comparisonOptions) {
		if (comparisonOptions.ambiguousOptions == AmbiguousOptions.State) {
			return ci != cj;
		} else if (ci != comparisonOptions.missing && ci != comparisonOptions.gap && cj != comparisonOptions.missing && cj != comparisonOptions.gap) {
			if (comparisonOptions.useAmbiguityCodes) {
				return !AmbiguityCodes.codesOverlap(ci, cj);
			} else {
				return ci != cj;
			}
		} else
			return false;
	}

	public static String computeEdgeLabel(String sequence1, String sequence2, ComparisonOptions comparisonOptions) {
		var buf = new StringBuilder();
		for (var i = 0; i < sequence1.length(); i++) {
			if (differ(Character.toLowerCase(sequence1.charAt(i)), Character.toLowerCase(sequence2.charAt(i)), comparisonOptions)) {
				if (!buf.isEmpty())
					buf.append(",");
				buf.append(i + 1);
			}
		}
		return buf.toString();
	}

	public static double computeTotalLength(NetworkBlock networkBlock) {
		var network = networkBlock.getGraph();
		if (networkBlock.getNode().getPreferredParent().getPreferredParent().getDataBlock() instanceof CharactersBlock charactersBlock) {
			var hammingOptions = new NetworkUtils.ComparisonOptions(charactersBlock.getGapCharacter(), charactersBlock.getMissingCharacter(), true, AmbiguousOptions.Wildcard);
			var length = 0;
			for (var e : network.edges()) {
				var sequence1 = networkBlock.getNodeData(e.getSource()).get(NetworkBlock.NODE_STATES_KEY);
				var sequence2 = networkBlock.getNodeData(e.getTarget()).get(NetworkBlock.NODE_STATES_KEY);
				if (sequence1 == null || sequence2 == null)
					return -1;
				length += compareSequences(sequence1, sequence2, hammingOptions);
			}
			return length;
		} else {
			return network.edgeStream().mapToDouble(network::getWeight).sum();
		}
	}

	public static double computeExcessLength(NetworkBlock networkBlock) {
		var network = networkBlock.getGraph();
		if (networkBlock.getNode().getPreferredParent().getPreferredParent().getDataBlock() instanceof CharactersBlock charactersBlock) {
			var hammingOptions = new NetworkUtils.ComparisonOptions(charactersBlock.getGapCharacter(), charactersBlock.getMissingCharacter(), true, AmbiguousOptions.Wildcard);
			var weights = new HashMap<Edge, Integer>();
			for (var e : network.edges()) {
				var sequence1 = networkBlock.getNodeData(e.getSource()).get(NetworkBlock.NODE_STATES_KEY);
				var sequence2 = networkBlock.getNodeData(e.getTarget()).get(NetworkBlock.NODE_STATES_KEY);
				if (sequence1 == null || sequence2 == null)
					return -1;
				weights.put(e, compareSequences(sequence1, sequence2, hammingOptions));
			}
			var excess = 0;
			for (var v : network.nodes()) {
				if (network.hasTaxa(v)) {
					var sequence1 = networkBlock.getNodeData(v).get(NetworkBlock.NODE_STATES_KEY);

					for (var w : network.nodes(v)) {
						if (network.hasTaxa(w)) {
							var sequence2 = networkBlock.getNodeData(w).get(NetworkBlock.NODE_STATES_KEY);

							var shortestPath = Dijkstra.compute(network, v, w, weights::get, true);
							Node prev = null;
							var pathLength = 0;
							for (var q : shortestPath) {
								if (prev != null) {
									var e = q.getCommonEdge(prev);
									pathLength += (weights.get(e));
								}
								prev = q;
							}
							excess += Math.max(0, pathLength - compareSequences(sequence1, sequence2, hammingOptions));

						}
					}
				}
			}
			return excess;
		} else if (networkBlock.getNode().getPreferredParent().getPreferredParent().getDataBlock() instanceof DistancesBlock distancesBlock) {
			var excessLength = 0.0;
			for (var v : network.nodes()) {
				if (network.hasTaxa(v)) {
					var s = network.getTaxon(v);
					for (var w : network.nodes(v)) {
						if (network.hasTaxa(w)) {
							var t = network.getTaxon(w);
							var shortestPath = Dijkstra.compute(network, v, w, network::getWeight, true);
							Node prev = null;
							var pathLength = 0.0;
							for (var q : shortestPath) {
								if (prev != null) {
									var e = q.getCommonEdge(prev);
									pathLength += network.getWeight(e);
								}
								prev = q;
							}
							excessLength += Math.max(0, pathLength - distancesBlock.get(s, t));
						}
					}
				}
			}
			return excessLength;
		} else return -1;
	}

}

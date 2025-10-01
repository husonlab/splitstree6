/*
 * ReportTanglegramStats.java Copyright (C) 2025 Daniel H. Huson
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

package splitstree6.view.trees.tanglegram;

import jloda.graph.Node;
import jloda.graph.NodeIntArray;
import jloda.phylo.PhyloTree;
import jloda.util.BitSetUtils;
import jloda.util.Counter;
import jloda.util.IteratorUtils;

import java.util.*;
import java.util.concurrent.atomic.DoubleAdder;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * computes tanglegram stats
 * Daniel Huson, 9.2025
 */
public class ReportTanglegramStats {

	public static Stats apply(PhyloTree network1, Function<Node, Double> yFunction1, Function<Node, String> labelFunction1,
							  PhyloTree network2, Function<Node, Double> yFunction2, Function<Node, String> labelFunction2) {
		if (IteratorUtils.size(network1.getTaxa()) == 0 && IteratorUtils.size(network2.getTaxa()) == 0) {
			addTaxa(network1, labelFunction1, network2, labelFunction2);
		}

		var reticulateDisplacement1 = computeReticulateDisplacement(network1, yFunction1);
		var reticulateDisplacement2 = computeReticulateDisplacement(network2, yFunction2);

		var taxonDisplacement2 = computeTaxonDisplacement(network1, network2);
		var crossings = computeNumberOfCrossings(network1, network2);

		var numTaxa = getCommonTaxa(network1, network2).cardinality();
		var r1 = (int) network1.edgeStream().filter(e -> e.getTarget().getInDegree() > 1 && !network1.isTransferAcceptorEdge(e)).count();
		var r2 = (int) network2.edgeStream().filter(e -> e.getTarget().getInDegree() > 1 && !network2.isTransferAcceptorEdge(e)).count();

		return new Stats(numTaxa, r1, r2, reticulateDisplacement1, reticulateDisplacement2, taxonDisplacement2, crossings);
	}

	private static void addTaxa(PhyloTree network1, Function<Node, String> labelFunction1,
								PhyloTree network2, Function<Node, String> labelFunction2) {
		var labelIdMap = new HashMap<String, Integer>();
		var numTaxa = new Counter(0);
		for (var v : network1.nodes()) {
			if (v.isLeaf()) {
				var label = labelFunction1.apply(v);
				if (label != null) {
					var id = labelIdMap.computeIfAbsent(label, k -> (int) numTaxa.incrementAndGet());
					network1.addTaxon(v, id);
				}
			}
		}
		for (var v : network2.nodes()) {
			if (v.isLeaf()) {
				var label = labelFunction2.apply(v);
				if (label != null) {
					var id = labelIdMap.computeIfAbsent(label, k -> (int) numTaxa.incrementAndGet());
					network2.addTaxon(v, id);
				}
			}
		}
	}

	private static double computeReticulateDisplacement(PhyloTree network, Function<Node, Double> yFunction) {
		var yStep = computeLeafYStep(network, yFunction);
		var displacement = 0.0;
		for (var e : network.edges()) {
			if (e.getTarget().getInDegree() > 1 && !network.isTransferAcceptorEdge(e)) {
				displacement += Math.abs(yFunction.apply(e.getSource()) - yFunction.apply(e.getTarget())) / yStep;
			}
		}
		return displacement;
	}

	private static double computeTaxonDisplacement(PhyloTree network1, PhyloTree network2) {
		Function<Node, List<Node>> childrenMap1 = v -> network1.hasLSAChildrenMap() && network1.getLSAChildrenMap().get(v) != null ? network1.getLSAChildrenMap().get(v) : IteratorUtils.asList(v.children());
		Function<Node, List<Node>> childrenMap2 = v -> network2.hasLSAChildrenMap() && network2.getLSAChildrenMap().get(v) != null ? network2.getLSAChildrenMap().get(v) : IteratorUtils.asList(v.children());
		var commonTaxa = getCommonTaxa(network1, network2);

		var taxonRankMap1 = computeTaxonRankMap(network1, childrenMap1, commonTaxa);
		try (var leafRankMap2 = computeLeafRankMap(network2, childrenMap2, commonTaxa)) {

			Function<Node, Double> taxonDisplacementFunction = v -> {
				var displacement = 0.0;
				for (var t : network2.getTaxa(v)) {
					if (commonTaxa.get(t)) {
						displacement += (Math.abs(leafRankMap2.get(v) - taxonRankMap1.get(t)));
					}
				}
				return displacement;
			};
			return computeCostBelow(childrenMap2, network2.getRoot(), taxonDisplacementFunction);
		}
	}

	private static int computeNumberOfCrossings(PhyloTree network1, PhyloTree network2) {
		Function<Node, List<Node>> childrenMap1 = v -> network1.hasLSAChildrenMap() && network1.getLSAChildrenMap().get(v) != null ? network1.getLSAChildrenMap().get(v) : IteratorUtils.asList(v.children());
		Function<Node, List<Node>> childrenMap2 = v -> network2.hasLSAChildrenMap() && network2.getLSAChildrenMap().get(v) != null ? network2.getLSAChildrenMap().get(v) : IteratorUtils.asList(v.children());
		var commonTaxa = getCommonTaxa(network1, network2);

		var taxonRankMap1 = computeTaxonRankMap(network1, childrenMap1, commonTaxa);
		try (var leafRankMap2 = computeLeafRankMap(network2, childrenMap2, commonTaxa)) {
			var list = new ArrayList<Node>();
			preOrderTraversal(network2.getRoot(), childrenMap2, v -> {
				if (v.isLeaf() && leafRankMap2.containsKey(v))
					list.add(v);
			});
			var count = 0;
			for (var i = 0; i < list.size(); i++) {
				var v1 = list.get(i);
				var network = (PhyloTree) v1.getOwner();
				if (network.hasTaxa(v1)) {
					var r1 = taxonRankMap1.get(network.getTaxon(v1));
					for (var j = i + 1; j < list.size(); j++) {
						var v2 = list.get(j);
						if (network.hasTaxa(v2)) {
							var r2 = taxonRankMap1.get(network.getTaxon(v2));
							if (r1 > r2)
								count++;
						}
					}
				}
			}
			return count;
		}
	}

	private static BitSet getCommonTaxa(PhyloTree network1, PhyloTree network2) {
		var taxa1 = new BitSet();
		for (var v : network1.nodes()) {
			if (v.isLeaf() && network1.hasTaxa(v))
				taxa1.set(network1.getTaxon(v));
		}
		var taxa2 = new BitSet();
		for (var v : network2.nodes()) {
			if (v.isLeaf() && network2.hasTaxa(v))
				taxa2.set(network2.getTaxon(v));
		}
		return BitSetUtils.intersection(taxa1, taxa2);
	}


	private static double computeLeafYStep(PhyloTree network, Function<Node, Double> yFunction) {
		var min = Double.MAX_VALUE;
		var max = Double.MIN_VALUE;
		var count = 0;
		for (var v : network.nodes()) {
			if (v.isLeaf()) {
				var value = yFunction.apply(v);
				min = Math.min(min, value);
				max = Math.max(max, value);
				count++;
			}
		}
		return count >= 2 ? (max - min) / (count - 1) : 1;
	}

	private static NodeIntArray computeLeafRankMap(PhyloTree network, Function<Node, List<Node>> childrenMap, BitSet taxa) {
		var counter = new Counter(0);
		var rankMap = network.newNodeIntArray();
		preOrderTraversal(network.getRoot(), childrenMap, v -> {
			if (network.hasTaxa(v) && taxa.get(network.getTaxon(v)))
				rankMap.put(v, (int) counter.incrementAndGet());
		});
		return rankMap;
	}

	private static double computeCostBelow(Function<Node, List<Node>> childrenMap, Node v, Function<Node, Double> costFunction) {
		var cost = new DoubleAdder();
		postOrderTraversal(v, childrenMap, u -> cost.add(costFunction.apply(u)));
		return cost.doubleValue();
	}

	private static Map<Integer, Integer> computeTaxonRankMap(PhyloTree network, Function<Node, List<Node>> childrenMap, BitSet taxa) {
		var counter = new Counter(0);
		var rankMap = new HashMap<Integer, Integer>();
		preOrderTraversal(network.getRoot(), childrenMap, v -> {
			if (network.hasTaxa(v)) {
				var t = network.getTaxon(v);
				if (taxa.get(t)) {
					rankMap.put(t, (int) counter.incrementAndGet());
				}
			}
		});
		return rankMap;
	}

	public static void preOrderTraversal(Node v, Function<Node, List<Node>> children, Consumer<Node> consumer) {
		consumer.accept(v);
		for (var w : children.apply(v)) {
			preOrderTraversal(w, children, consumer);
		}
	}

	public static void postOrderTraversal(Node v, Function<Node, List<Node>> children, Consumer<Node> consumer) {
		for (var w : children.apply(v)) {
			preOrderTraversal(w, children, consumer);
		}
		consumer.accept(v);
	}

	public record Stats(int numTaxa, int r1, int r2, double reticulateDisplacement1, double reticulateDisplacement2,
						double taxonDisplacement2, int crossings) {
		public String toString() {
			return "n=%d\tr1=%d\tr2=%d\tRD1=%.0f\tRD2=%.0f\tTD=%.0f\tCX=%d%n".formatted(numTaxa, r1, r2, reticulateDisplacement1, reticulateDisplacement2, taxonDisplacement2, crossings);
		}

		public double score(boolean useTaxonDisplacement1, boolean useTaxonDisplacement2, boolean useReticulateDisplacement1, boolean useReticulateDisplacement2) {
			var score = 0.0;
			if (useTaxonDisplacement1)
				score += taxonDisplacement2; // yes, is the same as taxonDisplacement2
			if (useTaxonDisplacement2)
				score += taxonDisplacement2;
			if (useReticulateDisplacement1)
				score += reticulateDisplacement1;
			if (useReticulateDisplacement2)
				score += reticulateDisplacement2;
			return score;
		}
	}
}

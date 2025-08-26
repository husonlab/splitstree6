/*
 * TanglegramOptimizer.java Copyright (C) 2025 Daniel H. Huson
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

import javafx.scene.layout.Pane;
import jloda.fx.phylo.embed.Averaging;
import jloda.fx.phylo.embed.HeightAndAngles;
import jloda.fx.util.AService;
import jloda.graph.*;
import jloda.phylo.LSAUtils;
import jloda.phylo.PhyloTree;
import jloda.util.*;
import jloda.util.progress.ProgressListener;

import java.util.*;
import java.util.concurrent.atomic.DoubleAdder;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * optimize the LSAchildren of phylogenies for a tanglegram drawing
 * Daniel Huson, 8.2025
 */
public class TanglegramOptimizer {
	/**
	 * optimize the tanglegram layout by updating the LSA children map
	 *
	 * @param statusPane                   used for progress bar
	 * @param network1                     first phylogeny
	 * @param network2                     second phylogeny
	 * @param optimizeTanglegramCrossings1 optimize tanglegram crossings in first phylogeny
	 * @param optimizeReticulateCrossings1 optimize reticulate crossings in first phylogeny
	 * @param optimizeTanglegramCrossings2 optimize tanglegram crossings in second phylogeny
	 * @param optimizeReticulateCrossings2 optimize reticulate crossings in second phylogeny
	 * @param runningConsumer              this is called in the FX thread when the running state is set to true and again, when set to false
	 * @param successRunnable              this is run in the FX thread once the calculation has successfully completed
	 * @param failedConsumer               this is called in the FX thread if the computation failed
	 */
	public static void apply(Pane statusPane, PhyloTree network1, PhyloTree network2, boolean optimizeTanglegramCrossings1, boolean optimizeReticulateCrossings1, boolean optimizeTanglegramCrossings2,
							 boolean optimizeReticulateCrossings2, Consumer<Boolean> runningConsumer, Runnable successRunnable, Consumer<Throwable> failedConsumer) {
		var childrenMap1 = new HashMap<Node, List<Node>>();
		LSAUtils.computeLSAChildrenMap(network1, childrenMap1);
		var childrenMap2 = new HashMap<Node, List<Node>>();
		LSAUtils.computeLSAChildrenMap(network2, childrenMap2);

		if (optimizeTanglegramCrossings1 || optimizeReticulateCrossings1 || optimizeTanglegramCrossings2 || optimizeReticulateCrossings2) {

			var alpha1 = setAlpha(optimizeReticulateCrossings1, optimizeTanglegramCrossings1);
			var alpha2 = setAlpha(optimizeReticulateCrossings2, optimizeTanglegramCrossings2);
			int rounds;
			if (((optimizeTanglegramCrossings1 || optimizeReticulateCrossings1) && network1.getNumberOfNodes() > 500)
				|| ((optimizeTanglegramCrossings2 || optimizeReticulateCrossings2) && network2.getNumberOfNodes() > 500))
				rounds = 2;
			else rounds = 5;

			var random = new Random(666);
			var service = new AService<Boolean>();
			service.setProgressParentPane(statusPane);
			service.setCallable(() -> {
				var progress = service.getProgressListener();
				progress.setTasks("Tanglegram", "");
				progress.setMaximum(2*rounds);
				for (var i = 0; i < rounds; i++) {
					if (optimizeTanglegramCrossings1 || optimizeReticulateCrossings1) {
						apply(network2, childrenMap2, network1, childrenMap1, alpha1, random, progress);
						progress.setProgress(2 * i);
					}
					if (optimizeTanglegramCrossings2 || optimizeReticulateCrossings2) {
						apply(network1, childrenMap1, network2, childrenMap2, alpha2, random, progress);
						progress.setProgress(2*i+1);
					}

				}
				return true;
			});
			service.setOnRunning(e -> runningConsumer.accept(true));
			service.setOnFailed(e -> {
				runningConsumer.accept(false);
				failedConsumer.accept(service.getException());
			});
			service.setOnSucceeded(e -> {
				network1.getLSAChildrenMap().clear();
				network1.getLSAChildrenMap().putAll(childrenMap1);
				network2.getLSAChildrenMap().clear();
				network2.getLSAChildrenMap().putAll(childrenMap2);
				runningConsumer.accept(false);
				successRunnable.run();
			});
			service.setOnCancelled(e -> {
				runningConsumer.accept(false);
			});
			service.start();
		} else {
			successRunnable.run();
		}
	}

	/**
	 * optimize network2 while keeping network 1 fixed
	 *
	 * @param network1 fixed
	 * @param network2 to be optimized, only its LSA children map will be modified
	 * @param alpha    cost weight, between 0: only optimize tanglegram crossings and 1: only optimize reticulate OD
	 * @param random   random number generator
	 */
	private static void apply(PhyloTree network1, Map<Node, List<Node>> childrenMap1, PhyloTree network2, Map<Node, List<Node>> childrenMap2, double alpha, Random random, ProgressListener progress) {
		System.err.println("One sided tanglegram: fixed: " + network1.getName() + ", optimizing: " + network2.getName());

		var taxaOnLeaves1 = new BitSet();
		network1.nodeStream().filter(v -> v.isLeaf() && network1.hasTaxa(v)).forEach(v -> taxaOnLeaves1.set(network1.getTaxon(v)));
		var taxaOnLeaves2 = new BitSet();
		network2.nodeStream().filter(v -> v.isLeaf() && network2.hasTaxa(v)).forEach(v -> taxaOnLeaves2.set(network2.getTaxon(v)));
		var commonLeafTaxa = BitSetUtils.intersection(taxaOnLeaves1, taxaOnLeaves2);


		if (commonLeafTaxa.cardinality() == 0)
			return; // no taxa in common

		var taxonRankMap1 = computeTaxonRankMap(network1, childrenMap1, commonLeafTaxa);

		try (NodeArray<Height> nodeHeightMap2 = computeNodeFullHeightMap(network2, childrenMap2);
			 var leafRankMap2 = computeLeafRankMap(network2,childrenMap2, commonLeafTaxa)) {

			Function<Node, Double> costFunction = v -> { // evaluate LSA children ordering
				var totalPenalty = 0.0;
				if (alpha > 0) {
					var reticulatePenalty = 0.0;
					for (var e : v.adjacentEdges()) {
						if (((PhyloTree) v.getOwner()).isReticulateEdge(e) && !((PhyloTree) v.getOwner()).isTransferAcceptorEdge(e))
							reticulatePenalty += 0.5 * Math.abs(nodeHeightMap2.get(e.getTarget()).value() - nodeHeightMap2.get(e.getSource()).value());
					}
					totalPenalty += alpha * reticulatePenalty;
				}
				if (alpha < 1) {
					var tanglegramPenalty = 0;
					for (var t : network2.getTaxa(v)) {
						if (commonLeafTaxa.get(t)) {
							tanglegramPenalty += (Math.abs(leafRankMap2.get(v) - taxonRankMap1.get(t)));
							// System.err.printf("costFunction(): taxon t=%d, penalty=%d=abs(%d-%d)%n", t,tanglegramPenalty,leafRankMap2.get(v),taxonRankMap1.get(t));
						}
					}
					totalPenalty += (1 - alpha) * tanglegramPenalty;
				}
				return totalPenalty;
			};

			var originalCost = computeCostBelow(network2, childrenMap2,network2.getRoot(), costFunction);
			System.err.println("Original cost:  " + originalCost);
			/* {
				for (var v : network2.nodes()) {
						System.err.println("node " + v + ": y=" + nodeHeightMap2.get(v).value() + " cost=" + costFunction.apply(v));
						if (network2.hasTaxa(v) && leafRankMap2.get(v)!=null) {
							System.err.println("taxon " + network2.getTaxon(v) + " rank=" + leafRankMap2.get(v) + ": y=" + nodeHeightMap2.get(v).value() + " cost: " + costFunction.apply(v));
						}
					}
			} */

			if (true) {
				DAGTraversals.preOrderTraversal(network2.getRoot(), childrenMap2::get, v -> optimizeOrdering(network2, childrenMap2, v, leafRankMap2, nodeHeightMap2, costFunction, random, progress));
				System.err.println("Optimized cost: " + computeCostBelow(network2, childrenMap2, network2.getRoot(), costFunction));

				/* {
					for (var v : network2.nodes()) {
						System.err.println("node " + v + ": y=" + nodeHeightMap2.get(v).value() + " cost=" + costFunction.apply(v));
					if (network2.hasTaxa(v) && leafRankMap2.get(v)!=null) {
							System.err.println("taxon " + network2.getTaxon(v) + " rank=" + leafRankMap2.get(v) + ": y=" + nodeHeightMap2.get(v).value() + " cost: " + costFunction.apply(v));
						}
					}
				} */
			}
		}
	}

	/**
	 * attempt to optimize ordering of children below v, updating LSA children, nodeRankMap nodeHeightMap appropriately
	 *
	 * @param network       the tree or network
	 * @param v             the node
	 * @param nodeHeightMap maps v to its height, and to the min and max height of v and all descendants
	 * @param costFunction  the cost function
	 * @param random        random number generator used in simulated annealing
	 */
	public static void optimizeOrdering(PhyloTree network, Map<Node, List<Node>> childrenMap, Node v, Map<Node, Integer> nodeRankMap, Map<Node, Height> nodeHeightMap, Function<Node, Double> costFunction, Random random, ProgressListener progress) {
		if(progress.isUserCancelled())
			return;

		var originalOrdering = new ArrayList<>(childrenMap.get(v));

		// System.err.println("original order: "+StringUtils.toString(childrenMap.get(v).stream().filter(network::hasTaxa).mapToInt(network::getTaxon).toArray()," "));
		/* {
			DAGTraversals.preOrderTraversal(v, u -> childrenMap.get(u), u -> {
				if (network.hasTaxa(u))
					System.err.println(network.getTaxon(u) + ": " + nodeHeightMap.get(u));
			});
		} */

		if (originalOrdering.size() <= 8) {
			var bestCost = new Single<>(Double.MAX_VALUE);
			var bestOrdering = new Single<List<Node>>(originalOrdering);
			for (var permuted : Permutations.generateAllPermutations(originalOrdering)) {
				changeOrderOfChildren(network, childrenMap, v, permuted, nodeRankMap, nodeHeightMap);
				var cost = computeCostBelow(network,childrenMap, v, costFunction);
				// System.err.println("cost: "+cost);
				if (cost < bestCost.get()) {
					bestCost.set(cost);
					bestOrdering.set(new ArrayList<>(permuted));
					if (bestCost.get() == 0)
						break;
				}
				if (progress.isUserCancelled())
					return;
			}
			//System.err.println("permutations(v="+v+", list="+originalOrdering.size()+"): "+all.size());
			if (bestCost.get() < Double.MAX_VALUE) {
				// System.err.println("bestCost: "+bestCost.get());

				changeOrderOfChildren(network, childrenMap, v, bestOrdering.get(), nodeRankMap, nodeHeightMap);
				// System.err.println("updated order: "+StringUtils.toString(childrenMap.get(v).stream().filter(network::hasTaxa).mapToInt(network::getTaxon).toArray()," "));
				/* {
					DAGTraversals.preOrderTraversal(v, u -> childrenMap.get(u), u -> {
						if (network.hasTaxa(u))
							System.err.println(network.getTaxon(u) + ": " + nodeHeightMap.get(u));
					});
				} */
				// System.err.println("cost of updated: "+ computeCostBelow(network,v,nodeHeightMap,costFunction));
			}
		} else {
			var simulatedAnnealing = new SimulatedAnnealingMinLA<Node>();
			var pair = simulatedAnnealing.apply(originalOrdering, random, (permuted) -> {
				if (progress.isUserCancelled())
					return 0.0;
				changeOrderOfChildren(network, childrenMap, v, permuted, nodeRankMap, nodeHeightMap);
				return computeCostBelow(network,childrenMap, v, costFunction);
			});
			// System.err.println("simulated annealing on v="+v+": " + pair.getSecond());
			changeOrderOfChildren(network, childrenMap, v, pair.getFirst(), nodeRankMap, nodeHeightMap);
		}
	}


	private static double computeCostBelow(PhyloTree network, Map<Node, List<Node>> childrenMap, Node v, Function<Node, Double> costFunction) {
		var cost = new DoubleAdder();
		DAGTraversals.postOrderTraversal(v, childrenMap::get, u -> cost.add(costFunction.apply(u)));
		return cost.doubleValue();
	}

	/**
	 * change the order of the children of v, both in LSA map and also in the nodeMinHeightMaxMap
	 *
	 * @param network       the network
	 * @param v             the node
	 * @param newOrder      the new order of the children of v in the LSA map
	 * @param nodeHeightMap the node min-height-below, height and max height below values, these are also changed for v and all descendants
	 */
	private static void changeOrderOfChildren(PhyloTree network, Map<Node, List<Node>> childrenMap, Node v, List<Node> newOrder, Map<Node, Integer> nodeRankMap, Map<Node, Height> nodeHeightMap) {
		var oldOrder = childrenMap.get(v);
		if (oldOrder.size() > 1) {

			/* {
				System.err.println("change old nodes="+StringUtils.toString(oldOrder.stream().mapToInt(u->u.getId()).toArray()," "));
				System.err.println("change old taxa= "+StringUtils.toString(oldOrder.stream().filter(network::hasTaxa).mapToInt(network::getTaxon).toArray()," "));
				System.err.println("change old ranks="+StringUtils.toString(oldOrder.stream().filter(network::hasTaxa).mapToInt(nodeRankMap::get).toArray()," "));
				for (var w : oldOrder) {
					System.err.println("old order node " + w + ": " + nodeHeightMap.get(w));
				}
			} */

			if (oldOrder.size() != newOrder.size()) {
				throw new RuntimeException("Order mismatch");
			}
			// ignore leading and trailing children that are in the same place in both orderings
			var start = 0;
			while (start < oldOrder.size() && oldOrder.get(start) == newOrder.get(start)) {
				start++;
			}
			var end = oldOrder.size();
			while (end > 0 && oldOrder.get(end - 1) == newOrder.get(end - 1)) {
				end--;
			}
			if (start + 1 < end) { // update coordinates for all nodes in reordered subtrees
				var minHeight = nodeHeightMap.get(oldOrder.get(start)).minBelow();
				var rank = new Counter(Integer.MAX_VALUE); // set this to smallest rank in subtrees to be shuffled
				{
					for (var i = start; i < end; i++) {
						var w = newOrder.get(i);
						DAGTraversals.postOrderTraversal(w, childrenMap::get, u -> {
							if (nodeRankMap.containsKey(u))
								rank.set(Math.min(rank.get(), nodeRankMap.get(u)));
						});
					}
				}
				// System.err.println("min rank: "+rank.get());
				for (var i = start; i < end; i++) {
					var w = newOrder.get(i);
					var delta = minHeight - nodeHeightMap.get(w).minBelow();
					// System.err.println("node " + w.getId());
					DAGTraversals.postOrderTraversal(w, childrenMap::get, u -> {
						nodeHeightMap.replace(u, nodeHeightMap.get(u).update(delta));
						if (nodeRankMap.containsKey(u)) {
							// System.err.println("rank "+v.getId()+": "+nodeRankMap.get(u)+" -> "+rank.get());
							nodeRankMap.put(u, (int) rank.getAndIncrement());
						}
					});
					minHeight = nodeHeightMap.get(w).maxBelow() + 1.0;
				}
			}
			/* {
				System.err.println("change new node= "+StringUtils.toString(newOrder.stream().mapToInt(u->u.getId()).toArray()," "));
				System.err.println("change new taxa=  "+StringUtils.toString(newOrder.stream().filter(network::hasTaxa).mapToInt(network::getTaxon).toArray()," "));
				System.err.println("change new ranks= "+StringUtils.toString(newOrder.stream().filter(network::hasTaxa).mapToInt(nodeRankMap::get).toArray()," "));
				for (var w : newOrder) {
					System.err.println("new order node " + w + ": " + nodeHeightMap.get(w));
				}
			} */
			childrenMap.put(v, newOrder);
		}
	}

	private static NodeDoubleArray computeNodeHeightMap(PhyloTree network) {
		var nodeHeightMap = network.newNodeDoubleArray();
		HeightAndAngles.apply(network, nodeHeightMap, Averaging.LeafAverage, true);
		return nodeHeightMap;
	}

	private static NodeArray<Height> computeNodeFullHeightMap(PhyloTree network,Map<Node,List<Node>> childrenMap) {
		NodeArray<Height> nodeFullHeightMap = network.newNodeArray();
		try (var nodeHeightMap = computeNodeHeightMap(network)) {
			DAGTraversals.postOrderTraversal(network.getRoot(), childrenMap::get, v -> {
				var height = nodeHeightMap.get(v);
				var min = height;
				var max = height;
				for (var w : childrenMap.get(v)) {
					min = Math.min(min, nodeFullHeightMap.get(w).minBelow());
					max = Math.max(max, nodeFullHeightMap.get(w).maxBelow());
				}
				nodeFullHeightMap.put(v, new Height(min, height, max));
			});
		}
		return nodeFullHeightMap;
	}

	private static Map<Integer, Integer> computeTaxonRankMap(PhyloTree network, Map<Node,List<Node>> childrenMap,BitSet taxa) {
		var counter = new Counter(0);
		var rankMap = new HashMap<Integer, Integer>();
		DAGTraversals.preOrderTraversal(network.getRoot(), childrenMap::get, v -> {
			if (network.hasTaxa(v)) {
				var t = network.getTaxon(v);
				if (taxa.get(t)) {
					rankMap.put(t, (int) counter.incrementAndGet());
				}
			}
		});
		return rankMap;
	}

	private static NodeIntArray computeLeafRankMap(PhyloTree network, Map<Node,List<Node>> childrenMap, BitSet taxa) {
		var counter = new Counter(0);
		var rankMap = network.newNodeIntArray();
		DAGTraversals.preOrderTraversal(network.getRoot(), childrenMap::get, v -> {
			if (network.hasTaxa(v) && taxa.get(network.getTaxon(v)))
				rankMap.put(v, (int) counter.incrementAndGet());
		});
		return rankMap;
	}

	private static double setAlpha(boolean optimizeReticulateCrossings, boolean optimizeTanglegramCrossings) {
		if (optimizeReticulateCrossings && !optimizeTanglegramCrossings) {
			return 1.0;
		} else if (!optimizeReticulateCrossings && optimizeTanglegramCrossings) {
			return 0;
		} else {
			return 0.5;
		}
	}

	public record Height(double minBelow, double value, double maxBelow) {
		public Height update(double dy) {
			return new Height(minBelow + dy, value + dy, maxBelow + dy);
		}
	}
}



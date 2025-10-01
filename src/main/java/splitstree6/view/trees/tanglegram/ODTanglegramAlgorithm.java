/*
 * ODTanglegramAlgorithm.java Copyright (C) 2025 Daniel H. Huson
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
import jloda.graph.algorithms.PQTree;
import jloda.phylo.LSAUtils;
import jloda.phylo.PhyloTree;
import jloda.util.*;
import jloda.util.progress.ProgressListener;
import jloda.util.progress.ProgressPercentage;
import splitstree6.tools.RunWorkflow;
import splitstree6.utils.TreesUtils;

import java.util.*;
import java.util.concurrent.atomic.DoubleAdder;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * optimize the LSAchildren of phylogenies for a tanglegram drawing
 * Daniel Huson, 8.2025
 */
public class ODTanglegramAlgorithm {
	private static boolean verbose = false;

	/**
	 * optimize the tanglegram layout by updating the LSA children map
	 *
	 * @param statusPane                      used for progress bar
	 * @param network1                        first phylogeny
	 * @param network2                        second phylogeny
	 * @param optimizeTaxonDisplacement1      optimize taxon displacement in first phylogeny
	 * @param optimizeReticulateDisplacement1 optimize reticulate displacement in first phylogeny
	 * @param optimizeTaxonDisplacement2      optimize taxon displacement in second phylogeny
	 * @param optimizeReticulateDisplacement2 optimize reticulate displacement in second phylogeny
	 * @param usePQTree                       use PQ tree presorting for two-sided tanglegram
	 * @param runningConsumer                 this is called in the FX thread when the running state is set to true and again, when set to false
	 * @param successRunnable                 this is run in the FX thread once the calculation has successfully completed
	 * @param failedConsumer                  this is called in the FX thread if the computation failed
	 */
	public static void apply(Pane statusPane, PhyloTree network1, PhyloTree network2, boolean optimizeTaxonDisplacement1, boolean optimizeReticulateDisplacement1, boolean optimizeTaxonDisplacement2,
							 boolean optimizeReticulateDisplacement2, boolean usePQTree, Consumer<Boolean> runningConsumer, Runnable successRunnable, Consumer<Throwable> failedConsumer) {
		var childrenMap1 = new HashMap<Node, List<Node>>();
		LSAUtils.computeLSAChildrenMap(network1, childrenMap1);
		var childrenMap2 = new HashMap<Node, List<Node>>();
		LSAUtils.computeLSAChildrenMap(network2, childrenMap2);

		var bestChildrenMap1 = new Single<>(new HashMap<Node, List<Node>>());
		bestChildrenMap1.get().putAll(childrenMap1);
		var bestChildrenMap2 = new Single<>(new HashMap<Node, List<Node>>());
		bestChildrenMap2.get().putAll(childrenMap2);

		var bestScore = new Single<>(Double.MAX_VALUE);

		var finalOptimizeReticulateDisplacement1 = optimizeReticulateDisplacement1 && network1.hasReticulateEdges();
		var finalOptimizeReticulateDisplacement2 = optimizeReticulateDisplacement2 && network2.hasReticulateEdges();

		if (false) {
			runningConsumer.accept(true);
			var random = new Random(666);
			apply(network2, childrenMap2, network1, childrenMap1, optimizeTaxonDisplacement1, optimizeReticulateDisplacement1, random, new ProgressPercentage());
			network1.getLSAChildrenMap().clear();
			network1.getLSAChildrenMap().putAll(childrenMap1);
			network2.getLSAChildrenMap().clear();
			network2.getLSAChildrenMap().putAll(childrenMap2);
			runningConsumer.accept(false);
			successRunnable.run();
			return;
		}

		if (optimizeTaxonDisplacement1 || finalOptimizeReticulateDisplacement1 || optimizeTaxonDisplacement2 || finalOptimizeReticulateDisplacement2) {
			int rounds;

			var optimizeSide1 = (optimizeTaxonDisplacement1 || finalOptimizeReticulateDisplacement1);
			var optimizeSide2 = (optimizeTaxonDisplacement2 || finalOptimizeReticulateDisplacement2);

			if (!optimizeSide1 || !optimizeSide2)
				rounds = 1;
			else if (network1.getNumberOfNodes() > 500 || network2.getNumberOfNodes() > 500)
				rounds = 2;
			else rounds = 5;

			var random = new Random(666);
			var service = new AService<Boolean>();
			service.setProgressParentPane(statusPane);
			service.setCallable(() -> {
				var progress = service.getProgressListener();

				bestScore.set(computeScore(network1, childrenMap1, network2, childrenMap2, optimizeTaxonDisplacement1, finalOptimizeReticulateDisplacement1, optimizeTaxonDisplacement2, finalOptimizeReticulateDisplacement2));

				if (true) {
					System.err.println("original\t" + computeInfoString(network1, network2));
				}

				if (usePQTree && optimizeSide1 && optimizeSide2) {
					progress.setTasks("Tanglegram", "PQ-tree sorting");
					progress.setMaximum(-1);
					if (reorderUsingPQTree(network1, childrenMap1, network2, childrenMap2) && !finalOptimizeReticulateDisplacement1 && !finalOptimizeReticulateDisplacement2)
						return true; // both reordered to accommodate all
					var score = computeScore(network1, childrenMap1, network2, childrenMap2, optimizeTaxonDisplacement1, finalOptimizeReticulateDisplacement1, optimizeTaxonDisplacement2, finalOptimizeReticulateDisplacement2);
					if (score < bestScore.get()) {
						bestChildrenMap1.get().clear();
						bestChildrenMap1.get().putAll(childrenMap1);
						bestChildrenMap2.get().clear();
						bestChildrenMap2.get().putAll(childrenMap2);
					}
				}

				progress.setTasks("Tanglegram", "optimizing");
				progress.setMaximum(2 * rounds);

				for (var i = 0; i < rounds; i++) {
					if (optimizeSide1) {
						progress.setProgress(2 * i);
						apply(network2, childrenMap2, network1, childrenMap1, optimizeTaxonDisplacement1, finalOptimizeReticulateDisplacement1, random, progress);
						var score = computeScore(network1, childrenMap1, network2, childrenMap2, optimizeTaxonDisplacement1, finalOptimizeReticulateDisplacement1, optimizeTaxonDisplacement2, finalOptimizeReticulateDisplacement2);
						if (score < bestScore.get()) {
							bestChildrenMap1.get().clear();
							bestChildrenMap1.get().putAll(childrenMap1);
						}
					}
					if (optimizeSide2) {
						progress.setProgress(2 * i + 1);
						apply(network1, childrenMap1, network2, childrenMap2, optimizeTaxonDisplacement2, finalOptimizeReticulateDisplacement2, random, progress);
						var score = computeScore(network1, childrenMap1, network2, childrenMap2, optimizeTaxonDisplacement1, finalOptimizeReticulateDisplacement1, optimizeTaxonDisplacement2, finalOptimizeReticulateDisplacement2);
						if (score < bestScore.get()) {
							bestChildrenMap2.get().clear();
							bestChildrenMap2.get().putAll(childrenMap2);
						}
					}
				}
				return true;
			});
			service.runningProperty().addListener((v, o, n) -> {
				if (n) {
					if (RunWorkflow.runningJobsInView != null)
						RunWorkflow.runningJobsInView.add(service);
					runningConsumer.accept(true);
				} else {
					if (RunWorkflow.runningJobsInView != null)
						RunWorkflow.runningJobsInView.remove(service);
				}
			});
			service.setOnFailed(e -> {
				runningConsumer.accept(false);
				failedConsumer.accept(service.getException());
			});
			service.setOnSucceeded(e -> {
				if (optimizeSide1) {
					network1.getLSAChildrenMap().clear();
					network1.getLSAChildrenMap().putAll(bestChildrenMap1.get());
				}
				if (optimizeSide1) {
					network2.getLSAChildrenMap().clear();
					network2.getLSAChildrenMap().putAll(bestChildrenMap2.get());
				}
				if (true) {
					System.err.println("DO-Tanglegram\t" + computeInfoString(network1, network2));
				}
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
	 * @param random   random number generator
	 */
	private static boolean apply(PhyloTree network1, Map<Node, List<Node>> childrenMap1, PhyloTree network2, Map<Node, List<Node>> childrenMap2, boolean optimizeTaxonDisplacement, boolean optimizeReticulateDisplacement, Random random, ProgressListener progress) {
		System.err.println("One-sided tanglegram: fixed: " + network1.getName() + ", optimizing: " + network2.getName());

		var taxaOnLeaves1 = new BitSet();
		network1.nodeStream().filter(v -> v.isLeaf() && network1.hasTaxa(v)).forEach(v -> taxaOnLeaves1.set(network1.getTaxon(v)));
		var taxaOnLeaves2 = new BitSet();
		network2.nodeStream().filter(v -> v.isLeaf() && network2.hasTaxa(v)).forEach(v -> taxaOnLeaves2.set(network2.getTaxon(v)));
		var commonLeafTaxa = BitSetUtils.intersection(taxaOnLeaves1, taxaOnLeaves2);

		var taxonRankMap1 = computeTaxonRankMap(network1, childrenMap1, commonLeafTaxa);

		try (NodeArray<Height> nodeHeightMap2 = computeNodeFullHeightMap(network2, childrenMap2);
			 var leafRankMap2 = computeLeafRankMap(network2, childrenMap2, commonLeafTaxa)) {

			Function<Node, Double> reticulateDisplacementFunction = v -> {
				var displacement = 0.0;
				for (var e : v.adjacentEdges()) {
					if (((PhyloTree) v.getOwner()).isReticulateEdge(e) && !((PhyloTree) v.getOwner()).isTransferAcceptorEdge(e))
						displacement += 0.5 * Math.abs(nodeHeightMap2.get(e.getTarget()).value() - nodeHeightMap2.get(e.getSource()).value());
				}
				return displacement;
			};

			Function<Node, Double> taxonDisplacementFunction = v -> {
				var displacement = 0.0;
				for (var t : network2.getTaxa(v)) {
					if (commonLeafTaxa.get(t)) {
						displacement += (Math.abs(leafRankMap2.get(v) - taxonRankMap1.get(t)));
						// System.err.printf("costFunction(): taxon t=%d, penalty=%d=abs(%d-%d)%n", t,tanglegramPenalty,leafRankMap2.get(v),taxonRankMap1.get(t));
					}
				}
				return displacement;
			};

			Function<Node, Double> displacementFunction = v -> {
				var score = 0.0;
				if (optimizeReticulateDisplacement)
					score += reticulateDisplacementFunction.apply(v);
				if (optimizeTaxonDisplacement)
					score += taxonDisplacementFunction.apply(v);
				return score;
			};

			if (verbose) {
				if (optimizeTaxonDisplacement) {
					System.err.println("Original taxon displacement: " + computeCostBelow(childrenMap2, network2.getRoot(), taxonDisplacementFunction));
					System.err.println("Original num taxon crossings:" + computeNumberOfCrossings(network2.getRoot(), childrenMap2, taxonRankMap1, leafRankMap2));
				}
				if (optimizeReticulateDisplacement) {
					System.err.println("Original reticulate displ.:  " + computeCostBelow(childrenMap2, network2.getRoot(), reticulateDisplacementFunction));
				}
			}

			if (true)
				DAGTraversals.preOrderTraversal(network2.getRoot(), childrenMap2::get, v -> optimizeOrdering(childrenMap2, v, leafRankMap2, nodeHeightMap2, displacementFunction, random, progress));
			else
				DAGTraversals.postOrderTraversal(network2.getRoot(), childrenMap2::get, v -> optimizeOrdering(childrenMap2, v, leafRankMap2, nodeHeightMap2, displacementFunction, random, progress));

			if (optimizeTaxonDisplacement) {
				if (verbose) {
					var taxonDisplacement = computeCostBelow(childrenMap2, network2.getRoot(), taxonDisplacementFunction);
					System.err.println("Optimized taxon displacement:" + taxonDisplacement);
					System.err.println("Optimized num taxon crossings:" + computeNumberOfCrossings(network2.getRoot(), childrenMap2, taxonRankMap1, leafRankMap2));
				}
			}

			if (optimizeReticulateDisplacement) {
				var reticulateDisplacement = computeCostBelow(childrenMap2, network2.getRoot(), reticulateDisplacementFunction);
				if (verbose)
					System.err.println("Optimized reticulate displ.: " + reticulateDisplacement);
			}
			var score = network2.edgeStream().filter(e -> e.getTarget().getInDegree() > 1 && !network2.isTransferAcceptorEdge(e)).mapToDouble(e -> Math.abs(nodeHeightMap2.get(e.getSource()).value() - nodeHeightMap2.get(e.getTarget()).value())).sum();
			System.err.printf("Network2 RD: %.1f%n", score);
		}
		return true;
	}

	/**
	 * attempt to optimize ordering of children below v, updating LSA children, nodeRankMap nodeHeightMap appropriately
	 *
	 * @param v             the node
	 * @param nodeHeightMap maps v to its height, and to the min and max height of v and all descendants
	 * @param costFunction  the cost function
	 * @param random        random number generator used in simulated annealing
	 */
	public static void optimizeOrdering(Map<Node, List<Node>> childrenMap, Node v, Map<Node, Integer> leafRankMap, Map<Node, Height> nodeHeightMap, Function<Node, Double> costFunction, Random random, ProgressListener progress) {
		if (progress.isUserCancelled())
			return;

		var originalCost = computeCostBelow(childrenMap, v, costFunction);
		var originalOrdering = new ArrayList<>(childrenMap.get(v));

		if (false) {
			var network = (PhyloTree) v.getOwner();
			System.err.println("original cost: " + originalCost);
			System.err.println("original order: " + StringUtils.toString(childrenMap.get(v).stream().filter(network::hasTaxa).mapToInt(network::getTaxon).toArray(), " "));
			if (false) {
				DAGTraversals.preOrderTraversal(v, childrenMap::get, u -> {
					if (network.hasTaxa(u))
						System.err.println(network.getTaxon(u) + ": " + nodeHeightMap.get(u));
				});
			}
		}

		if (originalOrdering.size() <= 8) {
			var bestCost = new Single<>(originalCost);
			var bestOrdering = new Single<List<Node>>(originalOrdering);
			for (var permuted : Permutations.generateAllPermutations(originalOrdering)) {
				childrenMap.put(v, originalOrdering);
				changeOrderOfChildren(childrenMap, v, permuted, leafRankMap, nodeHeightMap);
				var cost = computeCostBelow(childrenMap, v, costFunction);
				/// System.err.println("cost: "+cost);
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
			if (bestCost.get() < originalCost) {
				if (false) {
					var network = (PhyloTree) v.getOwner();
					System.err.println("best cost: " + bestCost.get());
					System.err.println("best order: " + StringUtils.toString(bestOrdering.get().stream().filter(network::hasTaxa).mapToInt(network::getTaxon).toArray(), " "));
				}

				changeOrderOfChildren(childrenMap, v, bestOrdering.get(), leafRankMap, nodeHeightMap);
				if (false) {
					var network = (PhyloTree) v.getOwner();
					System.err.println("updated order: " + StringUtils.toString(childrenMap.get(v).stream().filter(network::hasTaxa).mapToInt(network::getTaxon).toArray(), " "));
					{
						DAGTraversals.preOrderTraversal(v, childrenMap::get, u -> {
							if (network.hasTaxa(u))
								System.err.println(network.getTaxon(u) + ": " + nodeHeightMap.get(u));
						});
					}
					System.err.println("cost of updated: " + computeCostBelow(childrenMap, v, costFunction));
				}
			}
		} else {
			var simulatedAnnealing = new SimulatedAnnealingMinLA<Node>();
			var pair = simulatedAnnealing.apply(originalOrdering, random, (permuted) -> {
				if (progress.isUserCancelled())
					return 0.0;
				childrenMap.put(v, originalOrdering);
				changeOrderOfChildren(childrenMap, v, permuted, leafRankMap, nodeHeightMap);
				return computeCostBelow(childrenMap, v, costFunction);
			});
			// System.err.println("simulated annealing on v="+v+": " + pair.getSecond());
			if (pair.getSecond() < originalCost) {
				System.err.println("best cost (SA): " + pair.getSecond());
				changeOrderOfChildren(childrenMap, v, pair.getFirst(), leafRankMap, nodeHeightMap);
			}
		}
	}


	private static double computeCostBelow(Map<Node, List<Node>> childrenMap, Node v, Function<Node, Double> costFunction) {
		var cost = new DoubleAdder();
		DAGTraversals.postOrderTraversal(v, childrenMap::get, u -> cost.add(costFunction.apply(u)));
		return cost.doubleValue();
	}

	/**
	 * change the order of the children of v, both in LSA map and also in the nodeMinHeightMaxMap
	 *
	 * @param v             the node
	 * @param newOrder      the new order of the children of v in the LSA map
	 * @param nodeHeightMap the node min-height-below, height and max height below values, these are also changed for v and all descendants
	 */
	private static void changeOrderOfChildren(Map<Node, List<Node>> childrenMap, Node v, List<Node> newOrder, Map<Node, Integer> leafRankMap, Map<Node, Height> nodeHeightMap) {
		var oldOrder = childrenMap.get(v);
		if (oldOrder.size() > 1) {

			if (false) {
				var network = (PhyloTree) v.getOwner();
				System.err.println("change old nodes="+StringUtils.toString(oldOrder.stream().mapToInt(u->u.getId()).toArray()," "));
				System.err.println("change old taxa= "+StringUtils.toString(oldOrder.stream().filter(network::hasTaxa).mapToInt(network::getTaxon).toArray()," "));
				System.err.println("change old ranks=" + StringUtils.toString(oldOrder.stream().filter(u -> network.hasTaxa(u) && leafRankMap.containsKey(u)).mapToInt(leafRankMap::get).toArray(), " "));
				for (var w : oldOrder) {
					System.err.println("old order node " + w + ": " + nodeHeightMap.get(w));
				}
			}

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
				var oldFirstMinBelow = nodeHeightMap.get(oldOrder.get(start)).minBelow();
				var oldLastMaxBelow = nodeHeightMap.get(oldOrder.get(end - 1)).maxBelow();
				if (false) {
					System.err.println("oldFirstMinBelow: " + oldFirstMinBelow);
					System.err.println("oldLastMaxBelow: " + oldLastMaxBelow);
				}

				var minHeight = nodeHeightMap.get(oldOrder.get(start)).minBelow();
				var rank = new Counter(Integer.MAX_VALUE); // set this to smallest rank in subtrees to be shuffled
				{
					for (var i = start; i < end; i++) {
						var w = newOrder.get(i);
						DAGTraversals.postOrderTraversal(w, childrenMap::get, u -> {
							if (leafRankMap.containsKey(u))
								rank.set(Math.min(rank.get(), leafRankMap.get(u)));
						});
					}
				}
				if (false) {
					for (var i = start; i < end; i++) {
						var w = oldOrder.get(i);
						System.err.println("old " + w + ": " + nodeHeightMap.get(w));
					}
				}

				// System.err.println("min rank: "+rank.get());
				for (var i = start; i < end; i++) {
					var w = newOrder.get(i); // new order
					var delta = minHeight - nodeHeightMap.get(w).minBelow();
					// System.err.println("node " + w.getId());
					DAGTraversals.postOrderTraversal(w, childrenMap::get, u -> {
						nodeHeightMap.replace(u, nodeHeightMap.get(u).update(delta));
						if (leafRankMap.containsKey(u)) {
							leafRankMap.put(u, (int) rank.getAndIncrement());
						}
					});
					minHeight = nodeHeightMap.get(w).maxBelow() + 1.0;
				}

				if (false) {
					for (var i = start; i < end; i++) {
						var w = newOrder.get(i);
						System.err.println("new " + w + ": " + nodeHeightMap.get(w));
					}
				}

				var newFirstMinBelow = nodeHeightMap.get(newOrder.get(start)).minBelow();
				var newLastMaxBelow = nodeHeightMap.get(newOrder.get(end - 1)).maxBelow();
				if (false) {
					System.err.println("newFirstMinBelow: " + newFirstMinBelow);
					System.err.println("newLastMaxBelow: " + newLastMaxBelow);


					if (oldLastMaxBelow != newLastMaxBelow) {
						System.err.println("Problem: oldLastMaxBelow=" + oldLastMaxBelow + " != newLastMaxBelow=" + newLastMaxBelow);
					}
				}
			}

			if (false) {
				var network = (PhyloTree) v.getOwner();
				System.err.println("change new node= "+StringUtils.toString(newOrder.stream().mapToInt(u->u.getId()).toArray()," "));
				System.err.println("change new taxa=  "+StringUtils.toString(newOrder.stream().filter(network::hasTaxa).mapToInt(network::getTaxon).toArray()," "));
				System.err.println("change new ranks= " + StringUtils.toString(newOrder.stream().filter(u -> network.hasTaxa(u) && leafRankMap.containsKey(u)).mapToInt(leafRankMap::get).toArray(), " "));
				for (var w : newOrder) {
					System.err.println("new order node " + w + ": " + nodeHeightMap.get(w));
				}
			}
			childrenMap.put(v, newOrder);
		}
	}

	private static NodeDoubleArray computeNodeHeightMap(PhyloTree network) {
		var nodeHeightMap = network.newNodeDoubleArray();
		HeightAndAngles.apply(network, nodeHeightMap, Averaging.LeafAverage, true);
		return nodeHeightMap;
	}

	private static NodeArray<Height> computeNodeFullHeightMap(PhyloTree network, Map<Node, List<Node>> childrenMap) {
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

	private static Map<Integer, Integer> computeTaxonRankMap(PhyloTree network, Map<Node, List<Node>> childrenMap, BitSet taxa) {
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

	private static NodeIntArray computeLeafRankMap(PhyloTree network, Map<Node, List<Node>> childrenMap, BitSet taxa) {
		var counter = new Counter(0);
		var rankMap = network.newNodeIntArray();
		DAGTraversals.preOrderTraversal(network.getRoot(), childrenMap::get, v -> {
			if (network.hasTaxa(v) && taxa.get(network.getTaxon(v)))
				rankMap.put(v, (int) counter.incrementAndGet());
		});
		return rankMap;
	}

	public record Height(double minBelow, double value, double maxBelow) {
		public Height update(double dy) {
			return new Height(minBelow + dy, value + dy, maxBelow + dy);
		}
	}

	public static int computeNumberOfCrossings(Node root2, Map<Node, List<Node>> nodeChildrenMap2, Map<Integer, Integer> taxonRankMap1, Map<Node, Integer> leafRankMap2) {
		var list = new ArrayList<Node>();
		DAGTraversals.preOrderTraversal(root2, nodeChildrenMap2::get, v -> {
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

	public static boolean reorderUsingPQTree(PhyloTree network1, Map<Node, List<Node>> childMap1, PhyloTree network2, Map<Node, List<Node>> childMap2) {
		var taxaOnLeaves1 = new BitSet();
		network1.nodeStream().filter(v -> v.isLeaf() && network1.hasTaxa(v)).forEach(v -> taxaOnLeaves1.set(network1.getTaxon(v)));
		var taxaOnLeaves2 = new BitSet();
		network2.nodeStream().filter(v -> v.isLeaf() && network2.hasTaxa(v)).forEach(v -> taxaOnLeaves2.set(network2.getTaxon(v)));
		var commonTaxa = BitSetUtils.intersection(taxaOnLeaves1, taxaOnLeaves2);

		System.err.println("Common taxa: " + commonTaxa.cardinality());

		var pqtree = new PQTree(commonTaxa);

		var offered = 0;
		var accepted = 0;

		if (false) {
			for (var cluster : TreesUtils.collectAllHardwiredClusters(network1)) {
				cluster.and(commonTaxa);
				offered++;
				if (pqtree.accept(cluster))
					accepted++;
			}
			for (var cluster : TreesUtils.collectAllHardwiredClusters(network2)) {
				cluster.and(commonTaxa);
				offered++;
				if (!pqtree.accept(cluster))
					accepted++;
			}

		} else {
			var clusters1 = TreesUtils.collectAllHardwiredClusters(network1).stream().map(c -> BitSetUtils.intersection(c, commonTaxa)).filter(c -> c.cardinality() >= 2).collect(Collectors.toSet());
			var clusters2 = TreesUtils.collectAllHardwiredClusters(network2).stream().map(c -> BitSetUtils.intersection(c, commonTaxa)).filter(c -> c.cardinality() >= 2).collect(Collectors.toSet());

			var intersection = IteratorUtils.asList(SetUtils.intersection(clusters1, clusters2));
			for (var cluster : intersection) {

				offered++;
				if (pqtree.accept(cluster))
					accepted++;
			}
			var symDiff = IteratorUtils.asList(SetUtils.symmetricDifference(clusters1, clusters2));
			for (var cluster : symDiff) {
				offered++;
				if (pqtree.accept(cluster))
					accepted++;
			}
		}

		var ordering = pqtree.extractAnOrdering();
		var taxonRankMap = new HashMap<Integer, Integer>();
		for (var r = 0; r < ordering.size(); r++) {
			taxonRankMap.put(ordering.get(r), r);
		}

		sortByRank(network1, childMap1, commonTaxa, taxonRankMap);
		sortByRank(network2, childMap2, commonTaxa, taxonRankMap);

		System.err.printf("PQ-tree presort: %d offered, %d accepted%n", offered, accepted);

		return offered == accepted;
	}

	public static void sortByRank(PhyloTree network, Map<Node, List<Node>> childMap, BitSet commonTaxa, Map<Integer, Integer> taxonRankMap) {
		try (var nodeLowestMap = network.newNodeIntArray()) {
			DAGTraversals.postOrderTraversal(network.getRoot(), childMap::get, v -> {
				var children = childMap.get(v);
				if (children.isEmpty() || v.isLeaf()) {
					if (network.hasTaxa(v)) {
						var t = network.getTaxon(v);
						if (commonTaxa.get(t)) {
							nodeLowestMap.put(v, taxonRankMap.get(t));
						}
					}
				} else {
					var av = 0;
					var count = 0;
					var childrenToSort = new ArrayList<Node>();
					for (var child : children) {
						if (nodeLowestMap.get(child) != null) { // ignore unsortable children
							childrenToSort.add(child);
							av += nodeLowestMap.get(child);
							count++;
						}
					}
					if (count > 0)
						nodeLowestMap.put(v, av / count);
					childrenToSort.sort(Comparator.comparingInt(nodeLowestMap::get));
					var pos = 0;
					for (int i = 0; i < children.size(); i++) {
						var child = children.get(i);
						if (nodeLowestMap.get(child) != null) // ignore unsortable children
							children.set(i, childrenToSort.get(pos++));
					}
				}
			});
		}
	}

	public static String computeInfoString(PhyloTree network1, PhyloTree network2) {
		try (var yMap1 = computeNodeHeightMap(network1);
			 var yMap2 = computeNodeHeightMap(network2)) {
			return ReportTanglegramStats.apply(
					network1, yMap1::get, network1::getLabel,
					network2, yMap2::get, network2::getLabel).toString();
		}
	}

	public static double computeScore(PhyloTree network1, Map<Node, List<Node>> childrenMap1, PhyloTree network2, Map<Node, List<Node>> childrenMap2, boolean useTaxonDisplacement1, boolean useTaxonDisplacement2, boolean useReticulateDisplacement1, boolean useReticulateDisplacement2) {
		var old1 = network1.getLSAChildrenMap();
		var old2 = network2.getLSAChildrenMap();
		try {
			network1.getLSAChildrenMap().clear();
			network1.getLSAChildrenMap().putAll(childrenMap1);
			network2.getLSAChildrenMap().clear();
			network2.getLSAChildrenMap().putAll(childrenMap2);

			try (var yMap1 = computeNodeHeightMap(network1);
				 var yMap2 = computeNodeHeightMap(network2)) {
				return ReportTanglegramStats.apply(
						network1, yMap1::get, network1::getLabel,
						network2, yMap2::get, network2::getLabel).score(useTaxonDisplacement1, useTaxonDisplacement2, useReticulateDisplacement1, useReticulateDisplacement2);
			}
		} finally {
			network1.getLSAChildrenMap().clear();
			network1.getLSAChildrenMap().putAll(old1);
			network2.getLSAChildrenMap().clear();
			network2.getLSAChildrenMap().putAll(old2);
		}
	}
}



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

package splitstree6.xtra.layout;


import java.util.*;
import java.util.concurrent.atomic.DoubleAdder;
import java.util.function.BooleanSupplier;
import java.util.function.Function;

import static splitstree6.xtra.layout.ORDNetworkLayoutAlgorithm.computeNodeHeightMap;
import static splitstree6.xtra.layout.ORDNetworkLayoutAlgorithm.computeNodeHeightMapRec;
import static splitstree6.xtra.layout.Utilities.postOrderTraversal;
import static splitstree6.xtra.layout.Utilities.preOrderTraversal;

/**
 * optimize the children of phylogenies for a tanglegram drawing
 * Daniel Huson, 8.2025
 */
public class ODTanglegramAlgorithm {
	/**
	 * optimize tree  2 while keeping tree 1 fixed
	 *
	 * @param name1                 name of first phylogeny
	 * @param root1                 root of first phylogeny
	 * @param nodeTaxonFunction1    node to taxon mapping for first phylogeny
	 * @param nodeChildrenFunction1 node to list of children mapping for first phylogeny
	 * @param name2                 name of second
	 * @param root2                 root of second
	 * @param nodeTaxonFunction2    node to taxon mapping for second
	 * @param nodeChildrenFunction2 node to list-of children for second
	 * @param random                random number generator
	 * @param canceled              returns false if computation is to be canceled
	 * @param <Node>                tree nodes
	 * @return new node-to-list-of children mapping for phylogeny 2
	 */
	public static <Node> ResultPair<Map<Node, List<Node>>> apply(String name1, Node root1, Function<Node, Integer> nodeTaxonFunction1, Function<Node, List<Node>> nodeChildrenFunction1,
																 String name2, Node root2, Function<Node, Integer> nodeTaxonFunction2, Function<Node, List<Node>> nodeChildrenFunction2,
																 Random random, BooleanSupplier canceled) {
		return apply(name1, root1, nodeTaxonFunction1, nodeChildrenFunction1, name2, root2, nodeTaxonFunction2, nodeChildrenFunction2, Collections.emptyMap(), true, false, random, canceled);
	}

	/**
	 * optimize tree (or network) 2 while keeping tree (or network) 1 fixed
	 *
	 * @param name1                          name of first phylogeny
	 * @param root1                          root of first phylogeny
	 * @param nodeTaxonFunction1             node to taxon mapping for first phylogeny
	 * @param nodeChildrenFunction1          node to children mapping for first phylogeny (tree, if tree, otherwise LSA tree of network)
	 * @param name2                          name of second
	 * @param root2                          root of second
	 * @param nodeTaxonFunction2             node to taxon mapping for second
	 * @param nodeChildrenFunction2          node to children for second
	 * @param reticulateEdges2               reticulate edges (not including transfer acceptor edges) sources and targets
	 * @param optimizeTaxonDisplacement      request optimization of taxon displacement
	 * @param optimizeReticulateDisplacement request optimization of reticulate displacement
	 * @param random                         random number generator
	 * @param canceled                       returns false if computation is to be canceled
	 * @param <Node>                         tree or network nodes
	 * @return new node to children mapping
	 */
	public static <Node> ResultPair<Map<Node, List<Node>>> apply(String name1, Node root1, Function<Node, Integer> nodeTaxonFunction1, Function<Node, List<Node>> nodeChildrenFunction1,
																 String name2, Node root2, Function<Node, Integer> nodeTaxonFunction2, Function<Node, List<Node>> nodeChildrenFunction2,
																 Map<Node, List<Node>> reticulateEdges2,
																 boolean optimizeTaxonDisplacement, boolean optimizeReticulateDisplacement, Random random, BooleanSupplier canceled) {
		System.err.println("One-sided tanglegram: fixed: " + name1 + ", optimizing: " + name2);

		var childrenMap1 = new HashMap<Node, List<Node>>();
		postOrderTraversal(root1, nodeChildrenFunction1, u -> {
			childrenMap1.put(u, new ArrayList<>(nodeChildrenFunction1.apply(u)));
		});

		var nodeTaxonMap1 = new HashMap<Node, Integer>();
		var taxaOnLeaves1 = new BitSet();

		postOrderTraversal(root1, childrenMap1::get, u -> {
			var t = nodeTaxonFunction1.apply(u);
			if (t != null) {
				nodeTaxonMap1.put(u, t);
				if (childrenMap1.get(u).isEmpty())
					taxaOnLeaves1.set(t);
			}
		});

		var childrenMap2 = new HashMap<Node, List<Node>>();
		postOrderTraversal(root2, nodeChildrenFunction2, u -> {
			childrenMap2.put(u, new ArrayList<>(nodeChildrenFunction2.apply(u)));
		});

		var nodeTaxonMap2 = new HashMap<Node, Integer>();
		var taxaOnLeaves2 = new BitSet();

		postOrderTraversal(root2, childrenMap2::get, u -> {
			var t = nodeTaxonFunction2.apply(u);
			if (t != null) {
				nodeTaxonMap2.put(u, t);
				if (childrenMap2.get(u).isEmpty())
					taxaOnLeaves2.set(t);
			}
		});

		var commonLeafTaxa = new BitSet();
		commonLeafTaxa.or(taxaOnLeaves1);
		commonLeafTaxa.and(taxaOnLeaves2);

		var taxonRankMap1 = computeTaxonRankMap(root1, nodeTaxonMap1, childrenMap1, commonLeafTaxa);

		var nodeHeightMap2 = computeNodeHeightMap(root2, childrenMap2);
		var leafRankMap2 = computeLeafRankMap(root2, childrenMap2, nodeTaxonMap2, commonLeafTaxa);

		Function<Node, Double> reticulateDisplacementFunction = v -> {
			var displacement = 0.0;
			if (reticulateEdges2.containsKey(v)) {
				for (var w : reticulateEdges2.get(v)) {
					displacement += 0.5 * Math.abs(nodeHeightMap2.get(v) - nodeHeightMap2.get(w));
				}
			}
			return displacement;
		};

		Function<Node, Double> taxonDisplacementFunction = v -> {
			var displacement = 0.0;
			if (nodeTaxonMap2.containsKey(v)) {
				var t = nodeTaxonMap2.get(v);
				if (commonLeafTaxa.get(t)) {
					displacement += (Math.abs(leafRankMap2.get(v) - taxonRankMap1.get(t)));
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

		// pre-or-post order better?
		preOrderTraversal(root2, childrenMap2::get, v -> optimizeOrdering(childrenMap2, v, leafRankMap2, nodeHeightMap2, displacementFunction, random, canceled));

		return new ResultPair<>(childrenMap2, computeCostBelow(childrenMap2, root2, displacementFunction));
	}

	public record ResultPair<R>(R result, double score) {
	}

	/**
	 * attempt to optimize ordering of children below v, updating LSA children, nodeRankMap nodeHeightMap appropriately
	 *
	 * @param v             the node
	 * @param nodeHeightMap maps v to its height, and to the min and max height of v and all descendants
	 * @param costFunction  the cost function
	 * @param random        random number generator used in simulated annealing
	 */
	public static <Node> void optimizeOrdering(Map<Node, List<Node>> childrenMap, Node v, Map<Node, Integer> leafRankMap, Map<Node, Double> nodeHeightMap, Function<Node, Double> costFunction, Random random, BooleanSupplier canceled) {
		if (canceled.getAsBoolean())
			return;

		if (childrenMap.get(v).size() < 2)
			return;

		var originalCost = computeCostBelow(childrenMap, v, costFunction);
		var originalOrdering = new ArrayList<>(childrenMap.get(v));
		var originalHeightBelowMap = copyHeightBelowMap(v, childrenMap, nodeHeightMap);
		var originalLeafRankBelowMap = copyLeafRankBelowMap(v, childrenMap, leafRankMap);

		if (originalOrdering.size() <= 8) {
			var bestCost = new Value<>(originalCost);
			var bestOrdering = new Value<List<Node>>(originalOrdering);

			for (var permuted : Permutations.generateAllPermutations(originalOrdering)) {
				childrenMap.put(v, originalOrdering);
				try {
					changeOrderOfChildren(childrenMap, v, permuted, leafRankMap, nodeHeightMap);
					var cost = computeCostBelow(childrenMap, v, costFunction);
					if (cost < bestCost.get()) {
						bestCost.set(cost);
						bestOrdering.set(new ArrayList<>(permuted));
						if (bestCost.get() == 0)
							break;
					}
					if (canceled.getAsBoolean())
						return;
				} finally {
					childrenMap.put(v, originalOrdering);
					nodeHeightMap.putAll(originalHeightBelowMap);
					leafRankMap.putAll(originalLeafRankBelowMap);
				}
			}
			if (bestCost.get() < originalCost) {
				changeOrderOfChildren(childrenMap, v, bestOrdering.get(), leafRankMap, nodeHeightMap);
			}
		} else {
			var simulatedAnnealing = new SimulatedAnnealingMinLA<Node>();
			var pair = simulatedAnnealing.apply(originalOrdering, random, (permuted) -> {
				if (canceled.getAsBoolean())
					return 0.0;
				try {
					childrenMap.put(v, originalOrdering);
					changeOrderOfChildren(childrenMap, v, permuted, leafRankMap, nodeHeightMap);
					return computeCostBelow(childrenMap, v, costFunction);
				} finally {
					childrenMap.put(v, originalOrdering);
					nodeHeightMap.putAll(originalHeightBelowMap);
					leafRankMap.putAll(originalLeafRankBelowMap);
				}
			});
			if (pair.score() < originalCost) {
				changeOrderOfChildren(childrenMap, v, pair.list(), leafRankMap, nodeHeightMap);
			}
		}
	}

	private static <Node> Map<Node, Integer> copyLeafRankBelowMap(Node v, Map<Node, List<Node>> childrenMap, Map<Node, Integer> leafRankMap) {
		var map = new HashMap<Node, Integer>();
		postOrderTraversal(v, childrenMap::get, u -> {
			if (leafRankMap.containsKey(u))
				map.put(u, leafRankMap.get(u));
		});
		return map;
	}

	public static <Node> Map<Node, Double> copyHeightBelowMap(Node v, Map<Node, List<Node>> childrenMap, Map<Node, Double> heightMap) {
		var heightMapBelow = new HashMap<Node, Double>();
		postOrderTraversal(v, childrenMap::get, u -> heightMapBelow.put(u, heightMap.get(u)));
		return heightMapBelow;
	}


	public static <Node> double computeCostBelow(Map<Node, List<Node>> childrenMap, Node v, Function<Node, Double> costFunction) {
		var cost = new DoubleAdder();
		postOrderTraversal(v, childrenMap::get, u -> cost.add(costFunction.apply(u)));
		return cost.doubleValue();
	}

	/**
	 * change the order of the children of v, both in LSA map and also in the nodeHeightMap
	 *
	 * @param v             the node
	 * @param newOrder      the new order of the children of v in the LSA map
	 * @param nodeHeightMap the node min-height-below, height and max height below values, these are also changed for v and all descendants
	 */
	private static <Node> void changeOrderOfChildren(Map<Node, List<Node>> childrenMap, Node v, List<Node> newOrder, Map<Node, Integer> leafRankMap, Map<Node, Double> nodeHeightMap) {
		var oldOrder = childrenMap.get(v);
		if (oldOrder.size() > 1 && !oldOrder.equals(newOrder)) {
			var next = new Value<>((double) getMinHeight(oldOrder.get(0), childrenMap, nodeHeightMap));
			var rank = new Value<>(getMinRank(oldOrder.get(0), childrenMap, leafRankMap)); // set this to smallest rank in subtrees to be shuffled
			for (var w : newOrder) {
				computeNodeHeightMapRec(w, childrenMap, next, nodeHeightMap);
				postOrderTraversal(w, childrenMap::get, u -> {
					if (leafRankMap.containsKey(u)) {
						leafRankMap.put(u, rank.get());
						rank.set(rank.get() + 1);
					}
				});
			}
			childrenMap.put(v, newOrder);
		}
	}


	public static <Node> int getMinHeight(Node u, Map<Node, List<Node>> childrenMap, Map<Node, Double> nodeHeightMap) {
		while (true) {
			var children = childrenMap.get(u);
			if (children.isEmpty())
				return nodeHeightMap.get(u).intValue();
			else u = children.get(0);
		}
	}

	public static <Node> int getMaxHeight(Node u, Map<Node, List<Node>> childrenMap, Map<Node, Double> nodeHeightMap) {
		while (true) {
			var children = childrenMap.get(u);
			if (children.isEmpty())
				return nodeHeightMap.get(u).intValue();
			else u = children.get(children.size() - 1);
		}
	}

	private static <Node> int getMinRank(Node u, Map<Node, List<Node>> childrenMap, Map<Node, Integer> leafRankMap) {
		var result = new Value<Integer>(-1);
		postOrderTraversal(u, childrenMap::get, v -> {
			if (result.get() == -1 && childrenMap.get(v).isEmpty() && leafRankMap.containsKey(v))
				result.set(leafRankMap.get(v));
		});
		return result.get();
	}

	private static <Node> Collection<Node> computeLeavesBelow(Node u, Map<Node, List<Node>> childrenMap) {
		var list = new ArrayList<Node>();
		postOrderTraversal(u, childrenMap::get, v -> {
			if (childrenMap.get(v).isEmpty())
				list.add(v);
		});
		return list;
	}

	public static <Node> Map<Integer, Integer> computeTaxonRankMap(Node root, Map<Node, Integer> taxonMap, Map<Node, List<Node>> childrenMap, BitSet taxa) {
		var counter = new Value<>(0);
		var rankMap = new HashMap<Integer, Integer>();
		preOrderTraversal(root, childrenMap::get, v -> {
			var t = taxonMap.get(v);
			if (t != null) {
				if (taxa.get(t)) {
					counter.set(counter.get() + 1);
					rankMap.put(t, counter.get());
				}
			}
		});
		return rankMap;
	}

	public static <Node> Map<Node, Integer> computeLeafRankMap(Node root, Map<Node, List<Node>> childrenMap, Map<Node, Integer> nodeTaxonMap, BitSet taxa) {
		var counter = new Value<>(0);
		var rankMap = new HashMap<Node, Integer>();
		preOrderTraversal(root, childrenMap::get, v -> {
			var t = nodeTaxonMap.get(v);
			if (t != null && taxa.get(t)) {
				counter.set(counter.get() + 1);
				rankMap.put(v, counter.get());
			}
		});
		return rankMap;
	}

	public record Height(double minBelow, double value, double maxBelow) {
		public Height update(double dy) {
			return new Height(minBelow + dy, value + dy, maxBelow + dy);
		}
	}


	public static <Node> int computeNumberOfCrossings(Node root2, Map<Node, List<Node>> nodeChildrenMap2, Map<Node, Integer> nodeTaxonMap2, Map<Integer, Integer> taxonRankMap1, Map<Node, Integer> leafRankMap2) {
		var list = new ArrayList<Node>();
		preOrderTraversal(root2, nodeChildrenMap2::get, v -> {
			if (leafRankMap2.containsKey(v))
				list.add(v);
		});
		var count = 0;
		for (var i = 0; i < list.size(); i++) {
			var v1 = list.get(i);
			var t1 = nodeTaxonMap2.get(v1);
			if (t1 != null && taxonRankMap1.containsKey(t1)) {
				var r1 = taxonRankMap1.get(t1);
				for (var j = i + 1; j < list.size(); j++) {
					var v2 = list.get(j);
					var t2 = nodeTaxonMap2.get(v2);
					if (t2 != null) {
						var r2 = taxonRankMap1.get(t2);
						if (r1 > r2)
							count++;
					}
				}
			}
		}
		return count;
	}
}



/*
 * ORDNetworkLayoutAlgorithm.java Copyright (C) 2025 Daniel H. Huson
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
import java.util.function.BooleanSupplier;
import java.util.function.Function;

/**
 * Compute an optimized reticulate displacement network layout
 * Daniel Huson, 10.2025
 */
public class ORDNetworkLayoutAlgorithm {
	/**
	 * Compute an optimized reticulate displacement network layout
	 *
	 * @param root               the root node
	 * @param lsaTreeChildrenMap for each node, the list of its children in the LSA tree
	 * @param reticulateEdgesMap for each node, all nodes that it is connected to via a reticulate edge (excluding transfer-acceptor edges)
	 * @param circular           true, if we are optimizing for a circular layout
	 * @param canceled           true, if calculation has been canceled
	 * @param <Node>             the node of a tree
	 * @return an optimized LSA tree mapping
	 */
	public static <Node> Map<Node, List<Node>> apply(Node root, Map<Node, List<Node>> lsaTreeChildrenMap, Map<Node, List<Node>> reticulateEdgesMap, boolean circular, BooleanSupplier canceled) {
		var childrenMap = new HashMap<>(lsaTreeChildrenMap);

		var random = new Random(666);

		var nodeHeightMap = computeNodeHeightMap(root, childrenMap);
		Function<Node, Double> reticulateDisplacementFunction;

		if (circular) {
			var totalMin = Utilities.getMinHeight(root, childrenMap, nodeHeightMap);
			var totalMax = Utilities.getMaxHeight(root, childrenMap, nodeHeightMap);
			reticulateDisplacementFunction = v -> {
				var displacement = 0.0;
				if (reticulateEdgesMap.containsKey(v)) {
					for (var w : reticulateEdgesMap.get(v)) {
						var min = Math.min(nodeHeightMap.get(v), nodeHeightMap.get(w));
						var max = Math.max(nodeHeightMap.get(v), nodeHeightMap.get(w));
						var diff = Math.min(max - min, (totalMax - max) + (min - totalMin) + 1);
						displacement += 0.5 * diff;
					}
				}
				return displacement;
			};
		} else {
			reticulateDisplacementFunction = v -> {
				var displacement = 0.0;
				if (reticulateEdgesMap.containsKey(v)) {
					for (var w : reticulateEdgesMap.get(v)) {
						var diff = Math.abs(nodeHeightMap.get(v) - nodeHeightMap.get(w));
						displacement += 0.5 * diff;
					}
				}
				return displacement;
			};
		}
		// pre- or post order better?
		Utilities.preOrderTraversal(root, childrenMap::get, v -> optimizeOrdering(childrenMap, v, nodeHeightMap, reticulateDisplacementFunction, random, canceled));
		return childrenMap;
	}

	/**
	 * attempt to optimize ordering of children below v, updating LSA children, nodeRankMap nodeHeightMap appropriately
	 *
	 * @param v             the node
	 * @param nodeHeightMap maps v to its height, and to the min and max height of v and all descendants
	 * @param costFunction  the cost function
	 * @param random        random number generator used in simulated annealing
	 */
	public static <Node> void optimizeOrdering(Map<Node, List<Node>> childrenMap, Node v, Map<Node, Double> nodeHeightMap, Function<Node, Double> costFunction, Random random, BooleanSupplier canceled) {
		if (canceled.getAsBoolean())
			return;

		if (childrenMap.get(v).size() < 2)
			return;

		var originalCost = computeCostBelow(childrenMap, v, costFunction);
		var originalOrdering = new ArrayList<>(childrenMap.get(v));
		var originalHeightBelowMap = copyHeightBelowMap(v, childrenMap, nodeHeightMap);

		if (originalOrdering.size() <= 8) {
			var bestCost = new Value<>(originalCost);
			var bestOrdering = new Value<>(originalOrdering);

			for (var permuted : Permutations.generateAllPermutations(originalOrdering)) {
				childrenMap.put(v, originalOrdering);
				try {
					changeOrderOfChildren(childrenMap, v, permuted, nodeHeightMap);
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
				}
			}
			if (bestCost.get() < originalCost) {
				changeOrderOfChildren(childrenMap, v, bestOrdering.get(), nodeHeightMap);
			}
		} else {
			var simulatedAnnealing = new SimulatedAnnealingMinLA<Node>();
			var pair = simulatedAnnealing.apply(originalOrdering, random, (permuted) -> {
				if (canceled.getAsBoolean())
					return 0.0;
				try {
					childrenMap.put(v, originalOrdering);
					changeOrderOfChildren(childrenMap, v, permuted, nodeHeightMap);
					return computeCostBelow(childrenMap, v, costFunction);
				} finally {
					childrenMap.put(v, originalOrdering);
					nodeHeightMap.putAll(originalHeightBelowMap);
				}
			});
			if (pair.score() < originalCost) {
				changeOrderOfChildren(childrenMap, v, pair.list(), nodeHeightMap);
			}
		}
	}

	public static <Node> Map<Node, Double> copyHeightBelowMap(Node v, Map<Node, List<Node>> childrenMap, Map<Node, Double> heightMap) {
		var heightMapBelow = new HashMap<Node, Double>();
		Utilities.postOrderTraversal(v, childrenMap::get, u -> heightMapBelow.put(u, heightMap.get(u)));
		return heightMapBelow;
	}

	/**
	 * change the order of the children of v, both in LSA map and also in the nodeHeightMap
	 *
	 * @param v             the node
	 * @param newOrder      the new order of the children of v in the LSA map
	 * @param nodeHeightMap the node min-height-below, height and max height below values, these are also changed for v and all descendants
	 */
	private static <Node> void changeOrderOfChildren(Map<Node, List<Node>> childrenMap, Node v, List<Node> newOrder, Map<Node, Double> nodeHeightMap) {
		var oldOrder = childrenMap.get(v);
		if (oldOrder.size() > 1 && !oldOrder.equals(newOrder)) {
			var next = new Value<>(Utilities.getMinHeight(oldOrder.get(0), childrenMap, nodeHeightMap));
			for (var w : newOrder) {
				computeNodeHeightMapRec(w, childrenMap, next, nodeHeightMap);
			}
			childrenMap.put(v, newOrder);
		}
	}

	public static <Node> Map<Node, Double> computeNodeHeightMap(Node root, Map<Node, List<Node>> childrenMap) {
		var nodeHeightMap = new HashMap<Node, Double>();
		var min = new Value<>(0.0);
		var max = computeLeavesBelow(root, childrenMap).size() - 1;
		var bounds = computeNodeHeightMapRec(root, childrenMap, min, nodeHeightMap);
		if (bounds.min() != 0 || bounds.max() != max)
			throw new IllegalStateException("Bad bounds");
		return nodeHeightMap;
	}

	public record Bounds(double min, double max) {
	}

	public static <Node> Bounds computeNodeHeightMapRec(Node v, Map<Node, List<Node>> childrenMap, Value<Double> next, Map<Node, Double> nodeHeightMap) {
		var children = childrenMap.get(v);
		if (children.isEmpty()) {

			var height = (double) next.get();
			next.set(next.get() + 1);
			nodeHeightMap.put(v, height);
			return new Bounds(height, height);
		} else {
			double min = Double.MAX_VALUE;
			double max = Double.MIN_VALUE;
			for (var w : children) {
				var minMax = computeNodeHeightMapRec(w, childrenMap, next, nodeHeightMap);
				min = Math.min(min, minMax.min());
				max = Math.max(max, minMax.max());
			}
			nodeHeightMap.put(v, 0.5 * (max + min));
			return new Bounds(min, max);
		}
	}

	public static <Node> double computeCostBelow(Map<Node, List<Node>> childrenMap, Node v, Function<Node, Double> costFunction) {
		var cost = new double[]{0.0};
		Utilities.postOrderTraversal(v, childrenMap::get, u -> cost[0] += costFunction.apply(u));
		return cost[0];
	}

	private static <Node> Collection<Node> computeLeavesBelow(Node u, Map<Node, List<Node>> childrenMap) {
		var list = new ArrayList<Node>();
		Utilities.postOrderTraversal(u, childrenMap::get, v -> {
			if (childrenMap.get(v).isEmpty())
				list.add(v);
		});
		return list;
	}

}

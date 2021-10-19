/*
 * MedianJoiningCalculator.java Copyright (C) 2021. Daniel H. Huson
 *
 * (Some code written by other authors, as named in code.)
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

package splitstree6.algorithms.characters.characters2network;

import jloda.graph.Edge;
import jloda.graph.EdgeSet;
import jloda.graph.Node;
import jloda.graph.NodeSet;
import jloda.phylo.PhyloGraph;
import jloda.util.CanceledException;
import jloda.util.Pair;
import jloda.util.progress.ProgressListener;

import java.util.*;

/**
 * Median Joining Algorithm
 * Daiel Huson, 2010, 2021
 */
public class MedianJoiningCalculator extends QuasiMedianBase {
	private int optionEpsilon = 0;

	/**
	 * runs the median joining algorithm
	 */
	public void computeGraph(ProgressListener progressListener, Set<String> inputSequences, double[] weights, PhyloGraph graph) throws CanceledException {
		System.err.println("Computing the median joining network for epsilon=" + getOptionEpsilon());
		var outputSequences = new HashSet<String>();
		computeMedianJoiningMainLoop(progressListener, inputSequences, weights, getOptionEpsilon(), outputSequences);

		var feasibleLinks = new EdgeSet(graph);
		boolean changed;
		do {
			graph.clear();
			feasibleLinks.clear();
			computeMinimumSpanningNetwork(outputSequences, weights, 0, graph, feasibleLinks);
			for (var e : graph.edges()) {
				if (!feasibleLinks.contains(e))
					graph.deleteEdge(e);
			}
			changed = removeObsoleteNodes(graph, inputSequences, outputSequences, feasibleLinks);
			progressListener.incrementProgress();
		}
		while (changed);
	}

	/**
	 * Main loop of the median joining algorithm
	 */
	private void computeMedianJoiningMainLoop(ProgressListener progress, Set<String> input, double[] weights, int epsilon, Set<String> outputSequences) throws CanceledException {
		outputSequences.addAll(input);

		boolean changed = true;
		while (changed) {
			System.err.println("Median joining: " + outputSequences.size() + " sequences");
			progress.incrementProgress();
			changed = false;

			var graph = new PhyloGraph();
			var feasibleLinks = new EdgeSet(graph);

			computeMinimumSpanningNetwork(outputSequences, weights, epsilon, graph, feasibleLinks);

			if (removeObsoleteNodes(graph, input, outputSequences, feasibleLinks)) {
				changed = true;   // sequences have been changed, recompute graph
			} else {
				// determine min connection cost:
				double minConnectionCost = Double.POSITIVE_INFINITY;

				for (var u : graph.nodes()) {
					var seqU = (String) u.getInfo();
					for (Edge e = u.getFirstAdjacentEdge(); e != null; e = u.getNextAdjacentEdge(e)) {
						Node v = e.getOpposite(u);
						String seqV = (String) v.getInfo();
						for (Edge f = u.getNextAdjacentEdge(e); f != null; f = u.getNextAdjacentEdge(f)) {
							Node w = f.getOpposite(u);
							String seqW = (String) w.getInfo();
							String[] qm = computeQuasiMedian(seqU, seqV, seqW);
							for (String aQm : qm) {
								if (!outputSequences.contains(aQm)) {
									double cost = computeConnectionCost(seqU, seqV, seqW, aQm, weights);
									if (cost < minConnectionCost)
										minConnectionCost = cost;
								}
							}
						}
						progress.checkForCancel();
					}
				}
				for (var e : feasibleLinks) {
					final var u = e.getSource();
					final var v = e.getTarget();
					final var seqU = (String) u.getInfo();
					final var seqV = (String) v.getInfo();
					for (var f : feasibleLinks.successors(e)) {
						Node w;
						if (f.getSource() == u || f.getSource() == v)
							w = f.getTarget();
						else if (f.getTarget() == u || f.getTarget() == v)
							w = f.getSource();
						else
							continue;
						var seqW = (String) w.getInfo();
						String[] qm = computeQuasiMedian(seqU, seqV, seqW);
						for (var aQm : qm) {
							if (!outputSequences.contains(aQm)) {
								double cost = computeConnectionCost(seqU, seqV, seqW, aQm, weights);
								if (cost <= minConnectionCost + epsilon) {
									outputSequences.add(aQm);
									changed = true;
								}
							}
						}
					}
					progress.checkForCancel();
				}
			}
		}
	}

	/**
	 * computes the minimum spanning network upto a tolerance of epsilon
	 */
	private void computeMinimumSpanningNetwork(Set<String> sequences, double[] weights, int epsilon, PhyloGraph graph, EdgeSet feasibleLinks) {
		var array = sequences.toArray(new String[0]);
		// compute a distance matrix between all sequences:
		var matrix = new double[array.length][array.length];

		var value2pairs = new TreeMap<Double, List<Pair<Integer, Integer>>>();

		for (var i = 0; i < array.length; i++) {
			for (var j = i + 1; j < array.length; j++) {
				matrix[i][j] = computeDistance(array[i], array[j], weights);
				var value = matrix[i][j];
				var pairs = value2pairs.computeIfAbsent(value, k -> new LinkedList<>());
				pairs.add(new Pair<>(i, j));
			}
		}

		var nodes = new Node[array.length];
		var componentsOfMSN = new int[array.length];
		var componentsOfThresholdGraph = new int[array.length];

		for (var i = 0; i < array.length; i++) {
			nodes[i] = graph.newNode(array[i]);
			graph.setLabel(nodes[i], array[i]);
			componentsOfMSN[i] = i;
			componentsOfThresholdGraph[i] = i;
		}

		var numComponentsMSN = array.length;

		// TODO: This implementation of the minimum spanning network is wrong, add only edges between different connected components

		var maxValue = Double.POSITIVE_INFINITY;
		// all sets of edges in ascending order of lengths
		for (var value : value2pairs.keySet()) {
			if (value > maxValue)
				break;
			var ijPairs = value2pairs.get(value);

			// update threshold graph components:
			for (int i = 0; i < array.length; i++) {
				for (int j = i + 1; j < array.length; j++) {
					if (componentsOfThresholdGraph[i] != componentsOfThresholdGraph[j] && matrix[i][j] < value - epsilon) {
						int oldComponent = componentsOfThresholdGraph[i];
						int newComponent = componentsOfThresholdGraph[j];
						for (int k = 0; k < array.length; k++) {
							if (componentsOfThresholdGraph[k] == oldComponent)
								componentsOfThresholdGraph[k] = newComponent;
						}
					}
				}
			}

			// determine new edges for minimum spanning network and determine feasible links
			var newPairs = new ArrayList<Pair<Integer, Integer>>();
			for (var ijPair : ijPairs) {
				var i = ijPair.getFirst();
				var j = ijPair.getSecond();

				var e = graph.newEdge(nodes[i], nodes[j]);
				graph.setWeight(e, matrix[i][j]);

				if (feasibleLinks != null && componentsOfThresholdGraph[i] != componentsOfThresholdGraph[j]) {
					feasibleLinks.add(e);
					if (false)
						System.err.println("ERROR nodes are connected: " + i + ", " + j);
				}
				newPairs.add(new Pair<>(i, j));
			}

			// update MSN components
			for (var pair : newPairs) {
				var i = pair.getFirst();
				var j = pair.getSecond();
				if (componentsOfMSN[i] != componentsOfMSN[j]) {
					numComponentsMSN--;
					var oldComponent = componentsOfMSN[i];
					var newComponent = componentsOfMSN[j];
					for (var k = 0; k < array.length; k++)
						if (componentsOfMSN[k] == oldComponent)
							componentsOfMSN[k] = newComponent;
				}
			}
			if (numComponentsMSN == 1 && maxValue == Double.POSITIVE_INFINITY)
				maxValue = value + epsilon; // once network is connected, add all edges upto threshold+epsilon
		}
	}

	/**
	 * determine whether v and target are connected by a chain of edges all of weight-threshold. Use for debugging
	 *
	 * @return true, if connected
	 */
	private boolean areConnected(PhyloGraph graph, Node v, Node target, NodeSet visited, double threshold) {
		if (v == target)
			return true;

		if (!visited.contains(v)) {
			visited.add(v);

			for (var e : v.adjacentEdges()) {
				if (graph.getWeight(e) < threshold) {
					var w = e.getOpposite(v);
					if (areConnected(graph, w, target, visited, threshold))
						return true;
				}
			}
		}
		return false;
	}

	/**
	 * iteratively removes all nodes that are connected to only two other and are not part of the original input
	 *
	 * @return true, if anything was removed
	 */
	private boolean removeObsoleteNodes(PhyloGraph graph, Set<String> input, Set<String> sequences, EdgeSet feasibleLinks) {
		var removed = 0;
		var changed = true;
		while (changed) {
			changed = false;
			var toDelete = new ArrayList<Node>();

			for (var v : graph.nodes()) {
				var seqV = (String) v.getInfo();
				if (!input.contains(seqV)) {
					int count = 0;
					for (Edge e = v.getFirstAdjacentEdge(); count <= 2 && e != null; e = v.getNextAdjacentEdge(e)) {
						if (feasibleLinks.contains(e))
							count++;
					}
					if (count <= 2)
						toDelete.add(v);
				}
			}
			if (toDelete.size() > 0) {
				changed = true;
				removed += toDelete.size();
				for (var v : toDelete) {
					sequences.remove((String) v.getInfo());
					graph.deleteNode(v);
				}
			}
		}
		return removed > 0;
	}


	/**
	 * compute the cost of connecting seqM to the other three sequences
	 *
	 * @return cost
	 */
	private double computeConnectionCost(String seqU, String seqV, String seqW, String seqM, double[] weights) {
		return computeDistance(seqU, seqM, weights) + computeDistance(seqV, seqM, weights) + computeDistance(seqW, seqM, weights);
	}

	/**
	 * compute weighted distance between two sequences
	 *
	 * @return distance
	 */
	private double computeDistance(String seqA, String seqB, double[] weights) {
		double cost = 0;
		for (int i = 0; i < seqA.length(); i++) {
			if (seqA.charAt(i) != seqB.charAt(i))
				if (weights != null)
					cost += weights[i];
				else
					cost++;
		}
		return cost;
	}


	/**
	 * computes the quasi median for three sequences
	 *
	 * @return quasi median
	 */
	private String[] computeQuasiMedian(String seqA, String seqB, String seqC) {
		var buf = new StringBuilder();
		var hasStar = false;
		for (var i = 0; i < seqA.length(); i++) {
			if (seqA.charAt(i) == seqB.charAt(i) || seqA.charAt(i) == seqC.charAt(i))
				buf.append(seqA.charAt(i));
			else if (seqB.charAt(i) == seqC.charAt(i))
				buf.append(seqB.charAt(i));
			else {
				buf.append("*");
				hasStar = true;
			}
		}
		if (!hasStar)
			return new String[]{buf.toString()};

		var median = new HashSet<String>();
		var stack = new Stack<String>();
		stack.add(buf.toString());
		while (!stack.empty()) {
			var seq = stack.pop();
			var pos = seq.indexOf('*');
			var pos2 = seq.indexOf('*', pos + 1);
			var first = seq.substring(0, pos) + seqA.charAt(pos) + seq.substring(pos + 1);
			var second = seq.substring(0, pos) + seqB.charAt(pos) + seq.substring(pos + 1);
			var third = seq.substring(0, pos) + seqC.charAt(pos) + seq.substring(pos + 1);
			if (pos2 == -1) {
				median.add(first);
				median.add(second);
				median.add(third);
			} else {
				stack.add(first);
				stack.add(second);
				stack.add(third);
			}
		}
		return median.toArray(new String[0]);
	}

	public int getOptionEpsilon() {
		return optionEpsilon;
	}

	public void setOptionEpsilon(int optionEpsilon) {
		this.optionEpsilon = optionEpsilon;
	}
}
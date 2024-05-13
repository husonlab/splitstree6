/*
 *  ClusterNetwork.java Copyright (C) 2024 Daniel H. Huson
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

package splitstree6.algorithms.trees.trees2trees;

import javafx.beans.property.DoubleProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleObjectProperty;
import jloda.fx.util.ProgramProperties;
import jloda.graph.Edge;
import jloda.graph.Node;
import jloda.graph.NodeDoubleArray;
import jloda.graph.NodeSet;
import jloda.phylo.PhyloTree;
import jloda.util.BitSetUtils;
import jloda.util.Pair;
import jloda.util.progress.ProgressListener;
import splitstree6.compute.autumn.HasseDiagram;
import splitstree6.data.TaxaBlock;
import splitstree6.data.TreesBlock;
import splitstree6.utils.TreesUtils;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Tree selector
 * Daniel Huson, 1/2018
 */
public class ClusterNetwork extends Trees2Trees {
	public enum EdgeWeights {Mean, Count, Sum, Uniform}

	private final DoubleProperty optionThresholdPercent = new SimpleDoubleProperty(this, "optionThresholdPercent", 10.0);
	protected final ObjectProperty<EdgeWeights> optionEdgeWeights = new SimpleObjectProperty<>(this, "optionEdgeWeights");


	@Override
	public List<String> listOptions() {
		return List.of(optionEdgeWeights.getName(), optionThresholdPercent.getName());
	}

	public ClusterNetwork() {
		ProgramProperties.track(optionEdgeWeightsProperty(), EdgeWeights::valueOf, EdgeWeights.Count);
	}

	@Override
	public String getToolTip(String optionName) {
		if (!optionName.startsWith("option")) {
			optionName = "option" + optionName;
		}
		return switch (optionName) {
			case "optionEdgeWeights" -> "compute edge weights";
			case "optionThresholdPercent" -> "minimum percentage of trees that a cluster must appear in";
			default -> super.getToolTip(optionName);
		};
	}

	@Override
	public String getCitation() {
		return "Huson & Rupp 2008; DH Huson and R. Rupp. Summarizing multiple gene trees using cluster networks. In: KA Crandall and J Lagergren (eds) Algorithms in Bioinformatics. WABI. LNCS, 5251, 2008";
	}

	@Override
	public String getShortDescription() {
		return "Computes the cluster network that contains all input trees (in the hardwired sense).";
	}

	@Override
	public void compute(ProgressListener progress, TaxaBlock taxaBlock, TreesBlock parent, TreesBlock child) {
		var clusterCountWeightMap = new HashMap<BitSet, Pair<Integer, Double>>();

		for (var tree : parent.getTrees()) {
			try (var nodeClusterMap = TreesUtils.extractClusters(tree)) {
				for (var v : nodeClusterMap.keySet()) {
					var cluster = nodeClusterMap.get(v);
					var weight = (v.getInDegree() == 1 ? tree.getWeight(v.getFirstInEdge()) : 0);
					var prevPair = clusterCountWeightMap.get(cluster);
					var newPair = (prevPair == null ? new Pair<>(1, weight) : new Pair<>(prevPair.getFirst() + 1, prevPair.getSecond() + weight));
					clusterCountWeightMap.put(cluster, newPair);
				}
			}
		}
		var threshold = (getOptionThresholdPercent() / 100.0) * parent.getNTrees() - 0.000001;

		var clusters = new ArrayList<BitSet>();
		for (var entry : clusterCountWeightMap.entrySet()) {
			if (entry.getValue().getFirst() >= threshold)
				clusters.add(entry.getKey());
		}

		Function<Integer, String> taxonIdLabelFunction = taxaBlock::getLabel;

		Function<BitSet, Double> clusterWeightFunction = cluster -> {
			var pair = clusterCountWeightMap.get(cluster);
			if (pair != null && pair.getFirst() > 0) {
				return switch (getOptionEdgeWeights()) {
					case Count -> (double) pair.getFirst();
					case Mean -> // average length
							pair.getSecond() / pair.getFirst();
					case Sum -> pair.getSecond();
					default -> 1.0;
				};
			} else return 1.0;
		};

		var network = computeNetwork(clusters, taxonIdLabelFunction, clusterWeightFunction);

		network.setName("Cluster network");
		child.getTrees().setAll(network);
		child.setReticulated(network.nodeStream().anyMatch(v -> v.getInDegree() > 1));
		child.setRooted(true);
	}

	/**
	 * compute the network for a set of clusters
	 *
	 * @param clusters           clusters
	 * @param taxonLabelFunction optional leaf labeling function
	 * @param weightFunction     optional edge weight function
	 * @return network
	 */
	public static PhyloTree computeNetwork(Collection<BitSet> clusters, Function<Integer, String> taxonLabelFunction, Function<BitSet, Double> weightFunction) {
		var network = HasseDiagram.constructHasse(clusters.toArray(new BitSet[0]));
		convertHasseToClusterNetwork(network, null);
		var additionalLeaves = new ArrayList<Pair<Node, Integer>>();
		for (var v : network.nodes()) {
			network.clearTaxa(v);
			network.setLabel(v, null);
			if (v.isLeaf()) {
				if (v.getInfo() instanceof BitSet bitSet) {
					if (bitSet.cardinality() > 1) {
						for (var t : BitSetUtils.members(bitSet)) {
							additionalLeaves.add(new Pair<>(v, t));
						}
					} else {
						for (var t : BitSetUtils.members(bitSet)) {
							network.addTaxon(v, t);
						}
					}
				}
			}
			v.setInfo(null);
		}
		for (var pair : additionalLeaves) {
			var v = network.newNode();
			network.addTaxon(v, pair.getSecond());
			network.newEdge(pair.getFirst(), v);
		}
		if (taxonLabelFunction != null) {
			for (var v : network.leaves()) {
				network.setLabel(v, taxonLabelFunction.apply(network.getTaxon(v)));
			}
		}
		if (weightFunction != null) {
			try (var nodeClusterMap = TreesUtils.extractClusters(network)) {
				for (var v : nodeClusterMap.keySet()) {
					if (v.getInDegree() == 1) {
						var cluster = nodeClusterMap.get(v);
						var weight = weightFunction.apply(cluster);
						if (weight != null)
							network.setWeight(v.getFirstInEdge(), weight);
					}
				}
			}
		}
		return network;
	}

	public static void convertHasseToClusterNetwork(PhyloTree network, NodeDoubleArray node2weight) {
		// split every node that has indegree>1 and outdegree!=1
		var nodes = network.nodeStream().collect(Collectors.toCollection(ArrayList::new));

		for (var v : nodes) {
			if (v.getInDegree() > 1) {
				var w = network.newNode();
				var toDelete = new ArrayList<Edge>();
				for (var e : v.inEdges()) {
					var u = e.getSource();
					var f = network.newEdge(u, w);
					network.setWeight(f, 0); // special edges have zero weight
					network.setReticulate(f, true);
					toDelete.add(e);
				}
				var f = network.newEdge(w, v);
				if (node2weight != null) {
					if (node2weight.getDouble(v) == -1)  // todo: fix code so that is ok for node to have only special edges
					{
						network.setWeight(f, 0);
					} else {
						network.setWeight(f, node2weight.getDouble(v));
					}
				}
				for (var aToDelete : toDelete) {
					network.deleteEdge(aToDelete);
				}
				//treeView.setLocation(w, treeView.getLocation(v).getX(), treeView.getLocation(v).getY() + 0.1);
			} else if (v.getInDegree() == 1) {
				var e = v.getFirstInEdge();
				if (node2weight != null)
					network.setWeight(e, node2weight.getDouble(v));
			}
		}
		var root = network.getRoot();
		if (root.getDegree() == 2 && network.getLabel(root) == null)  // is midway root, must divide both weights by two
		{
			var e = root.getFirstAdjacentEdge();
			var f = root.getLastAdjacentEdge();
			var weight = 0.5 * (network.getWeight(e) + network.getWeight(f));
			var a = computeAverageDistanceToALeaf(network, e.getOpposite(root));
			var b = computeAverageDistanceToALeaf(network, f.getOpposite(root));
			var na = 0.5 * (b - a + weight);
			if (na >= weight)
				na = 0.95 * weight;
			else if (na <= 0)
				na = 0.05 * weight;
			var nb = weight - na;
			network.setWeight(e, na);
			network.setWeight(f, nb);
		}

		// todo:    the algorithm sometimes leaves an additional edge at the root
		if (root.getOutDegree() == 1) {
			root = network.getRoot().getFirstOutEdge().getTarget();
			network.deleteNode(network.getRoot());
			network.setRoot(root);
		}
	}


	@Override
	public boolean isApplicable(TaxaBlock taxa, TreesBlock treesBlock) {
		return !treesBlock.isReticulated() && !treesBlock.isPartial();
	}

	public double getOptionThresholdPercent() {
		return optionThresholdPercent.get();
	}

	public DoubleProperty optionThresholdPercentProperty() {
		return optionThresholdPercent;
	}

	public void setOptionThresholdPercent(double optionThresholdPercent) {
		this.optionThresholdPercent.set(optionThresholdPercent);
	}

	public EdgeWeights getOptionEdgeWeights() {
		return optionEdgeWeights.get();
	}

	public ObjectProperty<EdgeWeights> optionEdgeWeightsProperty() {
		return optionEdgeWeights;
	}

	public void setOptionEdgeWeights(EdgeWeights optionEdgeWeights) {
		this.optionEdgeWeights.set(optionEdgeWeights);
	}

	/**
	 * gets the average editDistance from this node to a leaf.
	 *
	 * @param v node
	 * @return average editDistance to a leaf
	 */
	public static double computeAverageDistanceToALeaf(PhyloTree tree, Node v) {
		// assumes that all edges are oriented away from the root
		try (var seen = tree.newNodeSet()) {
			var pair = new Pair<>(0.0, 0);
			computeAverageDistanceToLeafRec(tree, v, null, 0, seen, pair);
			var sum = pair.getFirst();
			var leaves = pair.getSecond();
			if (leaves > 0)
				return sum / leaves;
			else
				return 0;
		}
	}

	/**
	 * recursively does the work
	 */
	private static void computeAverageDistanceToLeafRec(PhyloTree tree, Node v, Edge e, double distance, NodeSet seen, Pair<Double, Integer> pair) {
		if (!seen.contains(v)) {
			seen.add(v);
			if (v.getOutDegree() > 0) {
				for (var f : v.adjacentEdges()) {
					if (f != e) {
						computeAverageDistanceToLeafRec(tree, f.getOpposite(v), f, distance + tree.getWeight(f), seen, pair);
					}
				}
			} else {
				pair.setFirst(pair.getFirst() + distance);
				pair.setSecond(pair.getSecond() + 1);
			}
		}
	}

}

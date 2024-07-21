/*
 *  PhyloFusion.java Copyright (C) 2024 Daniel H. Huson
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

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import jloda.fx.util.ProgramProperties;
import jloda.graph.Graph;
import jloda.graph.Node;
import jloda.graph.NodeArray;
import jloda.phylo.NewickIO;
import jloda.phylo.PhyloTree;
import jloda.phylo.algorithms.ClusterPoppingAlgorithm;
import jloda.util.BitSetUtils;
import jloda.util.IteratorUtils;
import jloda.util.Pair;
import jloda.util.StringUtils;
import jloda.util.progress.ProgressListener;
import splitstree6.algorithms.utils.MutualRefinement;
import splitstree6.compute.phylofusion.NetworkUtils;
import splitstree6.compute.phylofusion.PhyloFusionAlgorithm;
import splitstree6.data.TaxaBlock;
import splitstree6.data.TreesBlock;
import splitstree6.utils.ClusterUtils;
import splitstree6.utils.PathMultiplicityDistance;
import splitstree6.utils.TreesUtils;

import java.io.IOException;
import java.util.*;

/**
 * recursive version of the PhyloFusion algorithm
 * Developed together with Louxin Zhang and Banu Cetinkaya
 * Daniel Huson, 5.2024
 */
public class PhyloFusion extends Trees2Trees {
	private boolean verbose = false;

	public enum Search {SuperThorough, Thorough, Medium, Fast}

	private final BooleanProperty optionMutualRefinement = new SimpleBooleanProperty(this, "optionMutualRefinement", true);

	private final BooleanProperty optionNormalizeEdgeWeights = new SimpleBooleanProperty(this, "optionNormalizeEdgeWeights", true);

	private final BooleanProperty optionCalculateWeights = new SimpleBooleanProperty(this, "optionCalculateWeights", true);

	private final ObjectProperty<Search> optionSearchHeuristic = new SimpleObjectProperty<>(this, "optionSearchHeuristic");

	private final BooleanProperty optionCladeReduction = new SimpleBooleanProperty(this, "optionCladeReduction");

	private final BooleanProperty optionExperimental = new SimpleBooleanProperty(this, "optionExperimental");

	private final BooleanProperty optionOnlyOneNetwork = new SimpleBooleanProperty(this, "optionOnlyOneNetwork");

	{
		ProgramProperties.track(optionMutualRefinement, true);
		ProgramProperties.track(optionSearchHeuristic, Search::valueOf, Search.Thorough);
		ProgramProperties.track(optionCladeReduction, true);
		ProgramProperties.track(optionOnlyOneNetwork, true);
		ProgramProperties.track(optionExperimental, true);
	}

	@Override
	public String getCitation() {
		return "Zhang et al 2023; L. Zhang, N. Abhari, C. Colijn and Y Wu." +
			   " A fast and scalable method for inferring phylogenetic networks from trees by aligning lineage taxon strings. Genome Res. 2023.;"
			   + "Zhang et al 2024; L. Zhang, B. Cetinkaya and D.H. Huson. PhyloFusion- Fast and easy fusion of rooted phylogenetic trees into a network, in preparation.";
	}

	@Override
	public String getShortDescription() {
		return "Combines multiple rooted phylogenetic trees into a rooted network.";
	}

	@Override
	public List<String> listOptions() {
		return List.of(optionOnlyOneNetwork.getName(), optionMutualRefinement.getName(), optionNormalizeEdgeWeights.getName(), optionSearchHeuristic.getName(), optionExperimental.getName()); //, optionCladeReduction.getName()); //, optionCalculateWeights.getName());
	}
	// cladeReduction is not optional, there is a bug when it is not set ;-(

	@Override
	public String getToolTip(String optionName) {
		if (!optionName.startsWith("option")) {
			optionName = "option" + optionName;
		}
		return switch (optionName) {
			case "optionOnlyOneNetwork" -> "Report only one network";
			case "optionSearchHeuristic" ->
					"Fast, Medium, Thorough or SuperThorough search: 10, 150, 300 or 1000 random orderings per taxon, respectively";
			case "optionCalculateWeights" -> "Calculate edge weights using brute-force algorithm";
			case "optionMutualRefinement" -> "mutually refine input trees";
			case "optionNormalizeEdgeWeights" -> "normalize input edge weights";
			case "optionCladeReduction" -> "allow clade reduction as well as subtree reduction";
			case "optionExperimental" -> "run latest modifications";
			default -> super.getToolTip(optionName);
		};
	}

	/**
	 * run the algorithm
	 */
	@Override
	public void compute(ProgressListener progress, TaxaBlock taxaBlock, TreesBlock treesBlock, TreesBlock outputBlock) throws IOException {
		progress.setTasks("PhyloFusion", "init");

		TreesUtils.checkTaxonIntersection(treesBlock.getTrees(), 0.25);

		List<PhyloTree> inputTrees;
		if (isOptionMutualRefinement()) {
			inputTrees = MutualRefinement.apply(treesBlock.getTrees(), MutualRefinement.Strategy.All, false);
			if (verbose)
				System.err.println("Refined:\n" + NewickIO.toString(inputTrees, false));
		} else {
			inputTrees = treesBlock.getTrees().stream().map(PhyloTree::new).toList();
		}

		var result = computeRec(progress, inputTrees);
		var hybridizationNumber = result.get(0).nodeStream().filter(v -> v.getInDegree() > 0).mapToInt(v -> v.getInDegree() - 1).sum();
		System.err.println("Hybridization number: " + hybridizationNumber);

		outputBlock.setPartial(false);
		outputBlock.setRooted(true);

		if (isOptionOnlyOneNetwork() && result.size() > 1) {
			var one = result.get(0);
			result.clear();
			result.add(one);
		}

		var count = 0;
		for (var network : result) {
			for (var e : network.edges()) {
				network.setReticulate(e, e.getTarget().getInDegree() > 1);
			}
			network.setName("N" + (++count));
			TreesUtils.addLabels(network, taxaBlock::getLabel);
			outputBlock.getTrees().add(network);
			if (!outputBlock.isReticulated() && network.nodeStream().anyMatch(v -> v.getInDegree() > 1)) {
				outputBlock.setReticulated(true);
			}
			NetworkUtils.check(network);
			if (true) {
				for (var t = 1; t <= treesBlock.getNTrees(); t++) {
					var tree = treesBlock.getTree(t);
					if (!PathMultiplicityDistance.contains(taxaBlock.getTaxaSet(), network, tree)) {
						System.err.println("Internal error: Network does not appear to contain tree: " + t);
					}
				}
			}
			for (var v : network.nodeStream().filter(v -> v.getInDegree() == 1 && v.getOutDegree() == 1).toList()) {
				network.delDivertex(v);
			}

			if (count <= 10 && isOptionCalculateWeights()) {
				NetworkUtils.setEdgeWeights(treesBlock.getTrees(), network, isOptionNormalizeEdgeWeights(), 3000);
			}
			if (network.getRoot().getOutDegree() == 1)
				network.setWeight(network.getRoot().getFirstOutEdge(), 0.000001);
			if (optionSearchHeuristic.get() == Search.Fast)
				break; // only copy one
		}
	}

	private List<PhyloTree> computeRec(ProgressListener progress, List<PhyloTree> trees) throws IOException {
		if (trees.size() == 1) {
			if (verbose)
				System.err.println("Single tree");
			return List.of(new PhyloTree(trees.get(0)));
		}

		final var taxa = new BitSet();
		for (var tree : trees) {
			taxa.or(BitSetUtils.asBitSet(tree.getTaxa()));
		}
		var taxLabelMap = new HashMap<Integer, String>();
		trees.forEach(tree -> tree.nodeStream().filter(Node::isLeaf).forEach(v -> taxLabelMap.putIfAbsent(tree.getTaxon(v), tree.getLabel(v))));

		if (verbose)
			System.err.println("computeRec----");

		var repOthersMap = new HashMap<Integer, BitSet>();

		if (isOptionExperimental()) { // find all sets of un-separated taxa and replace each set by a single representative taxon
			var graph = new Graph();
			var taxonNodeMap = new TreeMap<Integer, Node>();
			for (var t : BitSetUtils.members(taxa)) {
				var v = graph.newNode();
				taxonNodeMap.put(t, v);
				v.setInfo(t);
			}

			for (var tree : trees) {
				if (verbose)
					System.err.println("Top Tree: " + tree.toBracketString(false));

				var leaves = tree.nodeStream().filter(Node::isLeaf).toList();
				for (var la : leaves) {
					var ta = tree.getTaxon(la);
					var v = taxonNodeMap.get(ta);
					for (var lb : leaves) {
						if (la.getParent() != lb.getParent()) {
							var tb = tree.getTaxon(lb);
							var w = taxonNodeMap.get(tb);
							if (!v.isAdjacent(w)) {
								graph.newEdge(v, w);
								if (verbose)
									System.err.println("Separated: " + taxLabelMap.get(ta) + " " + taxLabelMap.get(tb));
							}
						}
					}
				}
			}
			computeComplement(graph);

			if (verbose) {
				var nodes = IteratorUtils.asList(graph.nodes());
				for (var i = 0; i < nodes.size(); i++) {
					var v = nodes.get(i);
					var ta = (Integer) v.getInfo();
					for (var j = i + 1; j < nodes.size(); j++) {
						var w = nodes.get(j);
						var tb = (Integer) w.getInfo();
						if (v.isAdjacent(w)) {
							System.err.println("Not separated: " + taxLabelMap.get(ta) + " and " + taxLabelMap.get(tb));
						}
					}
				}
			}
			var treeTaxaSets = trees.stream().map(tree -> BitSetUtils.asBitSet(tree.getTaxa())).toList();

			try (var visited = graph.newNodeSet()) {
				for (var v : graph.nodes()) {
					if (!visited.contains(v)) {
						var component = new ArrayList<Integer>();
						var stack = new Stack<Node>();
						stack.push(v);
						visited.add(v);
						while (!stack.isEmpty()) {
							v = stack.pop();
							if (!component.contains((Integer) v.getInfo())) {
								component.add((Integer) v.getInfo());
							}
							for (var w : v.adjacentNodes()) {
								if (!visited.contains(w)) {
									stack.push(w);
									visited.add(v);
								}
							}
						}

						{ // representative has to appear in every tree that a node that it is representing appears
							if (component.size() > 1) {
								var bestRep = 0;
								var bestOthers = new BitSet();
								for (var i = 0; i < component.size(); i++) {
									var rep = (int) component.get(i);
									var others = new BitSet();
									for (var taxon : component) {
										if (taxon != rep && treeTaxaSets.stream().noneMatch(s -> s.get(taxon) && !s.get(rep))) {
											others.set(taxon);
										}
										if (others.cardinality() > bestOthers.cardinality()) {
											bestRep = rep;
											bestOthers = others;
										}
									}
								}
								if (bestOthers.cardinality() > 0) {
									repOthersMap.put(bestRep, bestOthers);
								}
							}
						}
					}
				}
			}
			if (!repOthersMap.isEmpty()) {
				// make copy of trees:
				{
					var tmp = new ArrayList<PhyloTree>();
					for (var tree : trees) {
						tmp.add(new PhyloTree(tree));
					}
					trees = tmp;
				}

				if (verbose)
					System.err.println("Before: " + StringUtils.toString(BitSetUtils.asList(taxa).stream().map(taxLabelMap::get).toList(), " "));

				for (var t : repOthersMap.keySet()) {
					var bitSet = repOthersMap.get(t);
					if (verbose)
						System.err.println("t=" + taxLabelMap.get(t) + " set=" + StringUtils.toString(BitSetUtils.asList(bitSet).stream().map(taxLabelMap::get).toList(), " "));

					for (var tree : trees) {
						var treeTaxa = BitSetUtils.asBitSet(tree.getTaxa());
						if (treeTaxa.intersects(bitSet)) {
							if (verbose)
								System.err.println("Before: " + tree.toBracketString(false));
							var parents = new HashSet<Node>();
							tree.nodeStream().filter(Node::isLeaf).filter(v -> bitSet.get(tree.getTaxon(v)))
									.forEach(v -> {
										parents.add(v.getParent());
										tree.deleteNode(v);
									});
							for (var p : parents) {
								if (p.getInDegree() == 1 && p.getOutDegree() == 1 && p.getParent() != tree.getRoot())
									tree.delDivertex(p);
							}
							if (verbose)
								System.err.println("After: " + tree.toBracketString(false));
						}
					}
				}
				if (verbose)
					System.err.println("After: " + StringUtils.toString(BitSetUtils.asList(taxa).stream().map(taxLabelMap::get).toList(), " "));

			}
			taxa.clear();
			for (var tree : trees) {
				taxa.or(BitSetUtils.asBitSet(tree.getTaxa()));
			}
		}

		Graph incompatibityGraph;
		var clusters = new ArrayList<BitSet>();
		var clusterIGMap = new HashMap<BitSet, Node>();
		{
			{
				var clusterSet = new HashSet<BitSet>();
				for (var tree : trees) {
					clusterSet.addAll(TreesUtils.collectAllHardwiredClusters(tree));
				}
				clusters.addAll(clusterSet);
				// sort by decreasing cardinality
				clusters.sort((a, b) -> -Integer.compare(a.cardinality(), b.cardinality()));
			}
			incompatibityGraph = new Graph();
			for (var cluster : clusters) {
				clusterIGMap.put(cluster, incompatibityGraph.newNode(cluster));
			}
			var nodes = IteratorUtils.asList(incompatibityGraph.nodes());
			for (var i = 0; i < nodes.size(); i++) {
				var a = nodes.get(i);
				for (var j = i + 1; j < nodes.size(); j++) {
					var b = nodes.get(j);
					if (!ClusterUtils.compatible((BitSet) a.getInfo(), (BitSet) b.getInfo()))
						incompatibityGraph.newEdge(a, b);
				}
			}
		}
		if (incompatibityGraph.nodeStream().noneMatch(v -> v.getDegree() > 0)) {
			var tree = new PhyloTree();
			ClusterPoppingAlgorithm.apply(clusters, tree);
			return List.of(tree);

		}
		// find a cluster that is compatible with all, if one exists
		BitSet separator = null;
		for (int i = 0; i < clusters.size(); i++) {
			var cluster = clusters.get(i);
			if (!cluster.equals(taxa)) {
				if (cluster.cardinality() == 1) {
					break;
				} else if (clusterIGMap.get(cluster).getDegree() == 0) {
					var ok = true;
					if (!isOptionCladeReduction()) {
						// need to check that set of contained clusters is compatible
						for (int j = i + 1; j < clusters.size(); j++) {
							var other = clusters.get(j);
							if (clusters.contains(other) && clusterIGMap.get(other).getDegree() != 0) {
								ok = false;
								break;
							}
						}
					}
					if (ok) {
						separator = cluster;
						break;
					}
				}
			}
		}
		if (separator == null) {
			if (trees.size() == 1) {
				if (verbose)
					System.err.println("Single tree");
				return List.of(new PhyloTree(trees.get(0)));
			} else {
				// run the algorithm
				if (verbose)
					System.err.println("Running on " + taxa.cardinality() + " taxa");
				var numberOfRandomOrderings = computeNumberOfRandomOrderings(taxa.cardinality(), getOptionSearchHeuristic());
				return PhyloFusionAlgorithm.apply(numberOfRandomOrderings, trees, isOptionOnlyOneNetwork(), progress);
			}

		} else {
			var rep = BitSetUtils.min(separator);
			var networksBelow = computeRec(progress, computeTreesBelow(trees, taxLabelMap, separator));

			var networksAbove = computeRec(progress, computeTreesAbove(trees, taxLabelMap, separator, rep));

			var result = new ArrayList<PhyloTree>();
			for (var networkAbove : networksAbove) {
				for (var networkBelow : networksBelow) {
					var networkMerged = new PhyloTree(networkAbove);
					var v = networkMerged.nodeStream().filter(w -> networkMerged.getTaxon(w) == rep).findAny().orElse(null);
					assert v != null; // null can't happen
					networkMerged.clearTaxa(v);
					if (verbose) {
						System.err.println("MERGING:");
						networkAbove.nodeStream().filter(networkAbove::hasTaxa).forEach(w -> networkAbove.setLabel(w, "t" + networkAbove.getTaxon(w)));
						System.err.println("Above " + (result.size() + 1) + ": " + networkAbove.toBracketString(false) + ";");
						{
							var reached = new HashSet<Node>();
							networkAbove.postorderTraversal(reached::add);
							System.err.println("networkAbove: nodes=" + networkAbove.getNumberOfNodes() + ", reachable: " + reached.size());
						}

						networkBelow.nodeStream().filter(networkBelow::hasTaxa).forEach(w -> networkBelow.setLabel(w, "t" + networkBelow.getTaxon(w)));
						System.err.println("Below " + (result.size() + 1) + ": " + networkBelow.toBracketString(false) + ";");
						{
							var reached = new HashSet<Node>();
							networkBelow.postorderTraversal(reached::add);
							System.err.println("networkBelow: nodes=" + networkBelow.getNumberOfNodes() + ", reachable: " + reached.size());
						}
					}
					networkMerged.clearTaxa(v);
					networkMerged.setLabel(v, null);
					copySubNetwork(networkBelow.getRoot(), v);
					if (verbose) {
						networkMerged.nodeStream().filter(networkMerged::hasTaxa).forEach(w -> networkMerged.setLabel(w, "t" + networkMerged.getTaxon(w)));
						for (var e : networkMerged.edges()) {
							networkMerged.setReticulate(e, e.getTarget().getInDegree() > 1);
						}
						System.err.println("Merged: " + networkMerged.toBracketString(false) + ";");
						{
							var reached = new HashSet<Node>();
							networkMerged.postorderTraversal(reached::add);
							System.err.println("networkMerged: nodes=" + networkMerged.getNumberOfNodes() + ", reachable: " + reached.size());
						}
					}
					result.add(networkMerged);
					if (isOptionOnlyOneNetwork())
						break;
				}
			}
			if (!repOthersMap.isEmpty()) { // add all the represented taxa to the networks
				for (var network : result) {
					network.nodeStream().filter(Node::isLeaf).filter(v -> repOthersMap.containsKey(network.getTaxon(v)))
							.forEach(v -> {
								var p = v.getParent();
								network.deleteEdge(p.getEdgeTo(v));
								var q = network.newNode();
								network.newEdge(p, q);
								network.newEdge(q, v);
								for (var t : BitSetUtils.members(repOthersMap.get(network.getTaxon(v)))) {
									var w = network.newNode();
									network.addTaxon(w, t);
									network.setLabel(w, taxLabelMap.get(t));
									network.newEdge(q, w);
								}
							});
				}
			}
			return result;
		}
	}

	public static void computeComplement(Graph graph) {
		var newPairs = new ArrayList<Pair<Node, Node>>();
		var nodes = IteratorUtils.asList(graph.nodes());
		for (var i = 0; i < nodes.size(); i++) {
			var v = nodes.get(i);
			for (var j = i + 1; j < nodes.size(); j++) {
				var w = nodes.get(j);
				if (!v.isAdjacent(w))
					newPairs.add(new Pair<>(v, w));
			}
		}
		graph.deleteAllEdges();
		for (var pair : newPairs) {
			graph.newEdge(pair.getFirst(), pair.getSecond());
		}
	}

	private List<PhyloTree> computeTreesBelow(List<PhyloTree> trees, Map<Integer, String> taxonLabelMap, BitSet taxa) {
		var clusterSets = new HashSet<HashSet<BitSet>>();
		for (var tree : trees) {
			var clusters = new HashSet<BitSet>();
			for (var cluster : TreesUtils.collectAllHardwiredClusters(tree)) {
				cluster.and(taxa);
				if (cluster.cardinality() > 0)
					clusters.add(cluster);
			}
			if (!clusters.isEmpty())
				clusterSets.add(clusters);
		}
		var result = new ArrayList<PhyloTree>();
		for (var clusters : clusterSets) {
			var tree = new PhyloTree();
			ClusterPoppingAlgorithm.apply(clusters, tree);
			tree.nodeStream().filter(Node::isLeaf).forEach(v -> tree.setLabel(v, taxonLabelMap.get(tree.getTaxon(v))));
			result.add(tree);
		}
		return result;
	}

	private List<PhyloTree> computeTreesAbove(List<PhyloTree> trees, Map<Integer, String> taxonLabelMap, BitSet taxa, int rep) {
		var clusterSets = new HashSet<HashSet<BitSet>>();
		for (var tree : trees) {
			var clusters = new HashSet<BitSet>();
			for (var cluster : TreesUtils.collectAllHardwiredClusters(tree)) {
				if (cluster.intersects(taxa)) {
					cluster.andNot(taxa);
					cluster.set(rep);
				}
				clusters.add(cluster);
			}
			if (!clusters.isEmpty())
				clusterSets.add(clusters);
		}
		var result = new ArrayList<PhyloTree>();
		for (var clusters : clusterSets) {
			var tree = new PhyloTree();
			ClusterPoppingAlgorithm.apply(clusters, tree);
			tree.nodeStream().filter(Node::isLeaf).forEach(v -> tree.setLabel(v, taxonLabelMap.get(tree.getTaxon(v))));
			result.add(tree);
		}
		return result;
	}

	public static void copySubNetwork(Node sourceRoot, Node targetNode) {
		var sourceTree = (PhyloTree) sourceRoot.getOwner();
		var targetTree = (PhyloTree) targetNode.getOwner();
		try (NodeArray<Node> old2new = sourceTree.newNodeArray()) {
			old2new.put(sourceRoot, targetNode);
			var allBelow = new HashSet<Node>();
			sourceTree.postorderTraversal(sourceRoot, v -> !allBelow.contains(v), allBelow::add);
			for (var v : allBelow) {
				if (v != sourceRoot) {
					var w = targetTree.newNode();
					old2new.put(v, w);
					for (var t : sourceTree.getTaxa(v)) {
						targetTree.addTaxon(w, t);
					}
				}
			}
			for (var e : sourceTree.edges()) {
				if (old2new.containsKey(e.getSource()) && old2new.containsKey(e.getTarget())) {
					var f = targetTree.newEdge(old2new.get(e.getSource()), old2new.get(e.getTarget()));
					targetTree.setWeight(f, sourceTree.getWeight(e));
				}
			}
		}
	}

	public static long computeNumberOfRandomOrderings(int ntax, Search optionSearch) {
		return switch (optionSearch) {
			case Fast -> Math.max(100, 10L * ntax);
			case Medium -> 150L * ntax;
			case Thorough -> 300L * ntax;
			case SuperThorough -> 1000L * ntax;
		};
	}

	public boolean isOptionMutualRefinement() {
		return optionMutualRefinement.get();
	}

	public BooleanProperty optionMutualRefinementProperty() {
		return optionMutualRefinement;
	}

	public void setOptionMutualRefinement(boolean optionMutualRefinement) {
		this.optionMutualRefinement.set(optionMutualRefinement);
	}

	public boolean isOptionNormalizeEdgeWeights() {
		return optionNormalizeEdgeWeights.get();
	}

	public BooleanProperty optionNormalizeEdgeWeightsProperty() {
		return optionNormalizeEdgeWeights;
	}

	public void setOptionNormalizeEdgeWeights(boolean optionNormalizeEdgeWeights) {
		this.optionNormalizeEdgeWeights.set(optionNormalizeEdgeWeights);
	}

	@Override
	public boolean isApplicable(TaxaBlock taxa, TreesBlock datablock) {
		return !datablock.isReticulated() && datablock.getNTrees() > 1;
	}

	public boolean isOptionCalculateWeights() {
		return optionCalculateWeights.get();
	}

	public BooleanProperty optionCalculateWeightsProperty() {
		return optionCalculateWeights;
	}

	public void setOptionCalculateWeights(boolean optionCalculateWeights) {
		this.optionCalculateWeights.set(optionCalculateWeights);
	}

	public void setOptionSearchHeuristic(Search optionSearchHeuristic) {
		this.optionSearchHeuristic.set(optionSearchHeuristic);
	}

	public Search getOptionSearchHeuristic() {
		return optionSearchHeuristic.get();
	}

	public ObjectProperty<Search> optionSearchHeuristicProperty() {
		return optionSearchHeuristic;
	}

	public boolean isOptionCladeReduction() {
		return optionCladeReduction.get();
	}

	public BooleanProperty optionCladeReductionProperty() {
		return optionCladeReduction;
	}

	public void setOptionCladeReduction(boolean cladeReduction) {
		this.optionCladeReduction.set(cladeReduction);
	}

	public boolean isOptionOnlyOneNetwork() {
		return optionOnlyOneNetwork.get();
	}

	public BooleanProperty optionOnlyOneNetworkProperty() {
		return optionOnlyOneNetwork;
	}

	public void setOptionOnlyOneNetwork(boolean onlyOneNetwork) {
		optionOnlyOneNetwork.set(onlyOneNetwork);
	}

	public boolean isOptionExperimental() {
		return optionExperimental.get();
	}

	public BooleanProperty optionExperimentalProperty() {
		return optionExperimental;
	}
}

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
import jloda.phylo.PhyloTree;
import jloda.phylo.algorithms.ClusterPoppingAlgorithm;
import jloda.util.BitSetUtils;
import jloda.util.IteratorUtils;
import jloda.util.StringUtils;
import jloda.util.progress.ProgressListener;
import splitstree6.algorithms.utils.MutualRefinement;
import splitstree6.compute.phylofusion.NetworkUtils;
import splitstree6.compute.phylofusion.PhyloFusionAlgorithmOct2024;
import splitstree6.data.TaxaBlock;
import splitstree6.data.TreesBlock;
import splitstree6.splits.GraphUtils;
import splitstree6.utils.ClusterUtils;
import splitstree6.utils.MaxCliqueUtilities;
import splitstree6.utils.PathMultiplicityDistance;
import splitstree6.utils.TreesUtils;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * recursive version of the PhyloFusion algorithm
 * Developed together with Louxin Zhang and Banu Cetinkaya
 * Daniel Huson, 5.2024
 */
public class PhyloFusion extends Trees2Trees {
	private boolean verbose = false; // for debugging
	private boolean checkAllPartialResults = false; // for debugging

	public enum Search {
		Thorough, Medium, Fast;

		/**
		 * get the number of random orderings for the given search
		 *
		 * @param ntax ntaxa
		 * @return number of random orderings
		 */
		public int numberOfRandomOrderings(int ntax) {
			return switch (this) {
				case Fast -> 10 * ntax;
				case Medium -> 150 * ntax;
				case Thorough -> 300 * ntax;
			};
		}
	}

	private final BooleanProperty optionMutualRefinement = new SimpleBooleanProperty(this, "optionMutualRefinement", true);

	private final BooleanProperty optionNormalizeEdgeWeights = new SimpleBooleanProperty(this, "optionNormalizeEdgeWeights", true);

	private final BooleanProperty optionCalculateWeights = new SimpleBooleanProperty(this, "optionCalculateWeights", true);

	private final ObjectProperty<Search> optionSearchHeuristic = new SimpleObjectProperty<>(this, "optionSearchHeuristic");

	private final BooleanProperty optionCladeReduction = new SimpleBooleanProperty(this, "optionCladeReduction");

	private final BooleanProperty optionGroupNonSeparated = new SimpleBooleanProperty(this, "optionGroupNonSeparated");

	private final BooleanProperty optionOnlyOneNetwork = new SimpleBooleanProperty(this, "optionOnlyOneNetwork");

	{
		ProgramProperties.track(optionMutualRefinement, true);
		ProgramProperties.track(optionSearchHeuristic, Search::valueOf, Search.Thorough);
		ProgramProperties.track(optionCladeReduction, true);
		ProgramProperties.track(optionOnlyOneNetwork, true);
		ProgramProperties.track(optionGroupNonSeparated, true);
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
		return List.of(optionOnlyOneNetwork.getName(), optionMutualRefinement.getName(), optionNormalizeEdgeWeights.getName(), optionSearchHeuristic.getName(), optionGroupNonSeparated.getName(), optionCladeReduction.getName()); //, optionCalculateWeights.getName());
	}
	// cladeReduction is not optional, there is a bug when it is not set ;-(

	@Override
	public String getToolTip(String optionName) {
		if (!optionName.startsWith("option")) {
			optionName = "option" + optionName;
		}
		return switch (optionName) {
			case "optionOnlyOneNetwork" -> "Report only one network";
			case "optionSearchHeuristic" -> "Fast, Medium, or Thorough search";
			case "optionCalculateWeights" -> "Calculate edge weights using brute-force algorithm";
			case "optionMutualRefinement" -> "mutually refine input trees";
			case "optionNormalizeEdgeWeights" -> "normalize input edge weights";
			case "optionCladeReduction" -> "improve performance using clade reduction";
			case "optionGroupNonSeparated" ->
					"improve performance by grouping taxa that are not separated by a non-trivial edge";
			default -> super.getToolTip(optionName);
		};
	}

	/**
	 * run the algorithm
	 */
	@Override
	public void compute(ProgressListener progress, TaxaBlock taxaBlock, TreesBlock treesBlock, TreesBlock outputBlock) throws IOException {
		progress.setTasks("PhyloFusion", "init");

		if (false)
			TreesUtils.checkTaxonIntersection(treesBlock.getTrees(), 0.10);

		var inputTrees = new ArrayList<>(treesBlock.getTrees().stream().map(PhyloTree::new).toList());

		var start = System.currentTimeMillis();
		var result = computeRec(progress, isOptionMutualRefinement(), inputTrees);

		var hybridizationNumber = result.get(0).nodeStream().filter(v -> v.getInDegree() > 0).mapToInt(v -> v.getInDegree() - 1).sum();
		System.err.println("Hybridization number: " + hybridizationNumber);

		if (false)
			System.err.println("Time: " + ((System.currentTimeMillis() - start) / 1000) + "s");

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
						System.err.println("Warning: Network might not contain tree: " + t);
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

	/**
	 * recursively compute the networks
	 *
	 * @param progress progress listener
	 * @param trees    current input trees
	 * @return networks
	 * @throws IOException something went wrong
	 */
	private List<PhyloTree> computeRec(ProgressListener progress, boolean mutualRefinement, List<PhyloTree> trees) throws IOException {
		removeContainedAndRefine(trees, mutualRefinement);

		if (trees.size() == 1) {
			if (verbose)
				System.err.println("Single tree");
			var tree = trees.get(0);
			if (checkAllPartialResults) {
				NetworkUtils.check(tree);
			}
			return List.of(tree);
		}

		final var taxa = new BitSet();
		for (var tree : trees) {
			taxa.or(BitSetUtils.asBitSet(tree.getTaxa()));
		}
		var taxLabelMap = new HashMap<Integer, String>();
		trees.forEach(tree -> tree.nodeStream().filter(Node::isLeaf).forEach(v -> taxLabelMap.putIfAbsent(tree.getTaxon(v), tree.getLabel(v))));

		if (verbose)
			System.err.println("computeRec----");

		var repGroupMap = new HashMap<Integer, BitSet>();
		if (getOptionGroupNonSeparated()) {
			repGroupMap.putAll(groupNonSeparatedTaxa(taxa, trees, taxLabelMap));
		}

		Graph incompatibityGraph;
		var clusters = new ArrayList<BitSet>();
		var clusterIGMap = new HashMap<BitSet, Node>();
		{
			if (checkAllPartialResults) {
				for (var tree : trees)
					NetworkUtils.check(tree);
			}
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
			return restoreGroupedTaxa(repGroupMap, taxLabelMap, List.of(tree));
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
				return restoreGroupedTaxa(repGroupMap, taxLabelMap, List.of(new PhyloTree(trees.get(0))));
			} else {
				// run the algorithm
				if (verbose)
					System.err.println("Running on " + taxa.cardinality() + " taxa");
				var numberOfRandomOrderings = getOptionSearchHeuristic().numberOfRandomOrderings(taxa.cardinality());

				var resultList = PhyloFusionAlgorithmOct2024.apply(getOptionSearchHeuristic(), trees, isOptionOnlyOneNetwork(), progress);

				// var resultList = PhyloFusionAlgorithmMay2024.apply(numberOfRandomOrderings, trees, isOptionOnlyOneNetwork(), progress);

				restoreGroupedTaxa(repGroupMap, taxLabelMap, resultList);

				if (checkAllPartialResults) {
					for (var network : resultList) {
						if (!NetworkUtils.check(network)) {
							for (var tree : trees) {
								System.err.println("tree was: " + tree.toBracketString(false));
							}
						}
					}
				}
				return resultList;
			}

		} else {
			var rep = BitSetUtils.min(separator);
			var networksBelow = computeRec(progress, isOptionMutualRefinement(), computeTreesBelow(trees, taxLabelMap, separator));
			if (checkAllPartialResults) {
				for (var network : networksBelow) {
					NetworkUtils.check(network);
				}
			}

			var networksAbove = computeRec(progress, isOptionMutualRefinement(), computeTreesAbove(trees, taxLabelMap, separator, rep));

			if (checkAllPartialResults) {
				for (var networkA : networksAbove) {
					for (var v : networkA.nodes()) {
						if (networkA.hasTaxa(v))
							networkA.setLabel(v, taxLabelMap.get(networkA.getTaxon(v)));
					}
					System.err.println("Network above: " + networkA.toBracketString(false));


					NetworkUtils.check(networkA);

					if (!IteratorUtils.asSet(networkA.getTaxa()).contains(rep))
						System.err.println("Network don't contain rep=" + rep);
				}
			}

			var resultList = new ArrayList<PhyloTree>();
			for (var networkAbove : networksAbove) {
				if (checkAllPartialResults) {
					NetworkUtils.check(networkAbove);
				}
				for (var networkBelow : networksBelow) {
					if (checkAllPartialResults)
						NetworkUtils.check(networkBelow);
					var networkMerged = new PhyloTree(networkAbove);
					final var v = networkMerged.nodeStream().filter(w -> networkMerged.getTaxon(w) == rep).findAny().orElse(null);

					if (v == null) {
						System.err.println("rep: " + rep + " " + taxLabelMap.get(rep));
						System.err.println("Not found in: " + networkAbove.toBracketString(false));
						System.err.println("Not found in: " + StringUtils.toString(BitSetUtils.asBitSet(networkAbove.getTaxa())));
					}
					if (v == null) {
						throw new RuntimeException("Internal error, rep not found");
					}

					networkMerged.clearTaxa(v);
					if (verbose) {
						System.err.println("MERGING:");
						networkAbove.nodeStream().filter(networkAbove::hasTaxa).forEach(w -> networkAbove.setLabel(w, "t" + networkAbove.getTaxon(w)));
						System.err.println("Above " + (resultList.size() + 1) + ": " + networkAbove.toBracketString(false) + ";");
						{
							var reached = new HashSet<Node>();
							networkAbove.postorderTraversal(reached::add);
							System.err.println("networkAbove: nodes=" + networkAbove.getNumberOfNodes() + ", reachable: " + reached.size());
						}

						networkBelow.nodeStream().filter(networkBelow::hasTaxa).forEach(w -> networkBelow.setLabel(w, "t" + networkBelow.getTaxon(w)));
						System.err.println("Below " + (resultList.size() + 1) + ": " + networkBelow.toBracketString(false) + ";");
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

					if (checkAllPartialResults)
						NetworkUtils.check(networkMerged);
					resultList.add(networkMerged);
					if (isOptionOnlyOneNetwork())
						break;
				}
			}

			restoreGroupedTaxa(repGroupMap, taxLabelMap, resultList);

			if (checkAllPartialResults) {
				for (var network : resultList) {
					NetworkUtils.check(network);
				}
			}

			return resultList;
		}
	}

	/**
	 * remove contained trees.
	 *
	 * @param trees trees
	 */
	private void removeContainedAndRefine(List<PhyloTree> trees, boolean refine) {

		if (refine) {
			var result = MutualRefinement.apply(trees, MutualRefinement.Strategy.All, false);
			trees.clear();
			trees.addAll(result);
		}

		var dataList = new ArrayList<>(trees.stream().map(DataItem::new)
				.sorted(Comparator.comparingInt(a -> a.taxa().cardinality())).toList());
		trees.clear();

		var keep = new BitSet();

		for (var i = 0; i < dataList.size(); i++) {
			var iTaxa = dataList.get(i).taxa();
			var iClusters = dataList.get(i).clusters();

			var ok = true;
			if (false)
			for (var j = i + 1; ok && j < dataList.size(); j++) {
				var jTaxa = dataList.get(j).taxa();
				var jClusters = dataList.get(j).clusters();
				if (BitSetUtils.contains(jTaxa, iTaxa)) {
					if (iTaxa.cardinality() == jTaxa.cardinality()) {
						if (jClusters.containsAll(iClusters)) {
							ok = false;
						}
					} else { // iTaxa is subset
						var jInduced = jClusters.stream().map(s -> BitSetUtils.intersection(s, iTaxa)).filter(s -> s.cardinality() > 0).collect(Collectors.toSet());
						if (jInduced.containsAll(iClusters)) {
							ok = false;
						}
					}
				}
			}
			if (ok)
				keep.set(i);
		}

		trees.addAll(BitSetUtils.asList(keep).stream().map(i -> dataList.get(i).tree()).toList());
	}

	public record DataItem(PhyloTree tree, BitSet taxa, Set<BitSet> clusters) {
		public DataItem(PhyloTree tree) {
			this(new PhyloTree(tree), BitSetUtils.asBitSet(tree.getTaxa()), TreesUtils.collectAllHardwiredClusters(tree));
		}
	}

	/**
	 * remove all taxa that are covered by some other taxon
	 *
	 * @param taxa        input taxa
	 * @param trees       input trees
	 * @param taxLabelMap tax-label map
	 * @return representative to covered taxa mapp
	 */
	private Map<Integer, BitSet> groupNonSeparatedTaxa(final BitSet taxa, final List<PhyloTree> trees, final Map<Integer, String> taxLabelMap) {
		final var repOthersMap = new HashMap<Integer, BitSet>();
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
							if (verbose && false)
								System.err.println("Separated: " + taxLabelMap.get(ta) + " " + taxLabelMap.get(tb));
						}
					}
				}
			}
		}
		GraphUtils.convertToComplement(graph);

		var treeTaxaSets = trees.stream().map(tree -> BitSetUtils.asBitSet(tree.getTaxa())).toList();

		for (var e : IteratorUtils.asList(graph.edges())) {
			var s = (Integer) e.getSource().getInfo();
			var t = (Integer) e.getTarget().getInfo();
			if (treeTaxaSets.stream().noneMatch(set -> set.get(s) && set.get(t)) ||
				treeTaxaSets.stream().filter(set -> set.get(s)).count() == 1 || treeTaxaSets.stream().filter(set -> set.get(t)).count() == 1)
				graph.deleteEdge(e);
		}

		MaxCliqueUtilities.greedilyReduceToDisjointMaxCliques(graph);

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

		try (var visited = graph.newNodeSet()) {
			for (var v : graph.nodes()) {
				if (!visited.contains(v)) {
					var component = new TreeSet<Integer>();
					var stack = new Stack<Node>();
					stack.push(v);
					visited.add(v);
					while (!stack.isEmpty()) {
						v = stack.pop();
						component.add((Integer) v.getInfo());
						for (var w : v.adjacentNodes()) {
							if (!visited.contains(w)) {
								stack.push(w);
								visited.add(v);
							}
						}
					}

					{  // representative taxon has to appear in every tree that a node that it is representing appears
						// and must not already be represented by some other taxon
						if (component.size() > 1) {
							var bestRep = 0;
							var bestOthers = new BitSet();
							for (var rep : component) {
								var others = new BitSet();
								for (var taxon : component) {
									if (!taxon.equals(rep) && treeTaxaSets.stream().noneMatch(s -> s.get(taxon) && !s.get(rep))) {
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
			trees.replaceAll(PhyloTree::new);

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

						for (var v : tree.nodes()) {
							if (v.isLeaf() && tree.getTaxon(v) == -1) {
								System.err.println("Unlabeled leaf: " + v + " in " + tree.toBracketString(false));
							}
						}

						tree.nodeStream().filter(Node::isLeaf).filter(v -> bitSet.get(tree.getTaxon(v)))
								.forEach(v -> {
									parents.add(v.getParent());
									tree.getTaxonNodeMap().remove(tree.getTaxon(v), v);
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
		return repOthersMap;
	}

	/**
	 * add all group taxa into the networks
	 *
	 * @param repGroupMap map of representatives to grouped taxa
	 * @param taxLabelMap taxon to label map
	 * @param networks    the networks
	 * @return the networks
	 */
	private List<PhyloTree> restoreGroupedTaxa(Map<Integer, BitSet> repGroupMap, Map<Integer, String> taxLabelMap, List<PhyloTree> networks) {
		if (!repGroupMap.isEmpty()) {
			for (var network : networks) {
				IteratorUtils.asStream(network.nodes()).filter(Node::isLeaf).filter(v -> repGroupMap.containsKey(network.getTaxon(v)))
						.forEach(v -> {
							var p = v.getParent();
							network.deleteEdge(p.getEdgeTo(v));
							var q = network.newNode();
							network.newEdge(p, q);
							network.newEdge(q, v);
							for (var t : BitSetUtils.members(repGroupMap.get(network.getTaxon(v)))) {
								var w = network.newNode();
								network.addTaxon(w, t);
								network.setLabel(w, taxLabelMap.get(t));
								network.newEdge(q, w);
							}
						});
				if (checkAllPartialResults)
					NetworkUtils.check(network);
			}
		}
		return networks;
	}

	/**
	 * compute all trees below a cluster
	 *
	 * @param trees         trees
	 * @param taxonLabelMap taxon label map
	 * @param taxa          the taxa
	 * @return trees below cluster
	 */
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

	/**
	 * computes all the trees above a cluster
	 *
	 * @param trees         the trees
	 * @param taxonLabelMap taxon-label map
	 * @param taxa          taxa
	 * @param rep           the representative to be used for the cluster
	 * @return trees above the cluster
	 */
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

		if (checkAllPartialResults) {
			var found = false;
			for (var tree : trees) {
				if (IteratorUtils.asSet(tree.getTaxa()).contains(rep))
					found = true;
			}
			if (!found)
				System.err.println("Trees don't contain rep=" + rep);
		}

		return result;
	}

	/**
	 * copy a source network into a target network
	 *
	 * @param sourceRoot root of the source network
	 * @param targetNode the node in the target network where to copy the source root to
	 */
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

	public boolean getOptionGroupNonSeparated() {
		return optionGroupNonSeparated.get();
	}

	public BooleanProperty optionGroupNonSeparatedProperty() {
		return optionGroupNonSeparated;
	}
}

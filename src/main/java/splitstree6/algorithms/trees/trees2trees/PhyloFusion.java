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
import jloda.graph.Node;
import jloda.graph.NodeArray;
import jloda.phylo.NewickIO;
import jloda.phylo.PhyloTree;
import jloda.util.BitSetUtils;
import jloda.util.IteratorUtils;
import jloda.util.progress.ProgressListener;
import splitstree6.algorithms.utils.MutualRefinement;
import splitstree6.compute.autumn.HasseDiagram;
import splitstree6.compute.phylofusion.NetworkUtils;
import splitstree6.compute.phylofusion.PhyloFusionAlgorithm;
import splitstree6.data.TaxaBlock;
import splitstree6.data.TreesBlock;
import splitstree6.utils.PathMultiplicityDistance;
import splitstree6.utils.TreesUtils;

import java.io.IOException;
import java.util.*;

public class PhyloFusion extends Trees2Trees {
	private boolean verboseSubtreeReduction = false;

	public enum Search {Thorough, Medium, Fast}
	private final BooleanProperty optionMutualRefinement = new SimpleBooleanProperty(this, "optionMutualRefinement", true);

	private final BooleanProperty optionNormalizeEdgeWeights = new SimpleBooleanProperty(this, "optionNormalizeEdgeWeights", true);

	private final BooleanProperty optionCalculateWeights = new SimpleBooleanProperty(this, "optionCalculateWeights", true);

	private final ObjectProperty<Search> optionSearchHeuristic = new SimpleObjectProperty<>(this, "optionSearchHeuristic");

	private final BooleanProperty optionSubtreeReduction = new SimpleBooleanProperty(this, "optionSubtreeReduction");

	{
		ProgramProperties.track(optionMutualRefinement, true);
		ProgramProperties.track(optionSearchHeuristic, Search::valueOf, Search.Thorough);
		ProgramProperties.track(optionSubtreeReduction, true);
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
		return List.of(optionMutualRefinement.getName(), optionNormalizeEdgeWeights.getName(), optionSearchHeuristic.getName(), optionSubtreeReduction.getName()); //, optionCalculateWeights.getName());
	}

	@Override
	public String getToolTip(String optionName) {
		if (!optionName.startsWith("option")) {
			optionName = "option" + optionName;
		}
		return switch (optionName) {
			case "optionSearch" ->
					"Fast, Medium or Thorough search: 10, 150 or 300 random orderings per taxon, respectively";
			case "optionCalculateWeights" -> "Calculate edge weights using brute-force algorithm";
			case "optionMutualRefinement" -> "mutually refine input trees";
			case "optionNormalizeEdgeWeights" -> "normalize input edge weights";
			case "optionSubtreeReduction" -> "apply subtree reduction to speed up calculation";
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
				if (false)
					System.err.println("Refined:\n" + NewickIO.toString(inputTrees, false));
			} else {
				inputTrees = treesBlock.getTrees().stream().map(PhyloTree::new).toList();
			}

		var representativeSubtreeMap = new HashMap<Integer, PhyloTree>();

		var ntax = taxaBlock.getNtax();

		if (isOptionSubtreeReduction()) { // subtree reduction
			if (verboseSubtreeReduction) {
				System.err.println("Input:");
				System.err.println(NewickIO.toString(inputTrees, false));
			}

			var clusters = new HashSet<BitSet>();
			for (var tree : inputTrees) {
				clusters.addAll(TreesUtils.collectAllHardwiredClusters(tree));
			}
			var hasseDiagram = HasseDiagram.constructHasse(clusters.toArray(new BitSet[0]));
			try (var visited = hasseDiagram.newNodeSet(); var goodNodes = hasseDiagram.newNodeSet()) {
				hasseDiagram.postorderTraversal(hasseDiagram.getRoot(), v -> !visited.contains(v), v -> {
					visited.add(v);
					if (v.getInDegree() <= 1 && v.childrenStream().allMatch(goodNodes::contains)) {
						goodNodes.add(v); // is the highest node that is compatible will everything and is above a tree
					}
					if (v.isLeaf()) {
						var taxa = (BitSet) v.getInfo();
						for (var t : BitSetUtils.members(taxa)) {
							hasseDiagram.addTaxon(v, t);
						}
					}
				});
				for (var v : goodNodes) {
					if (!v.isLeaf() && !(v.getInDegree() == 1 && goodNodes.contains(v.getParent()))) {
						var taxa = (BitSet) v.getInfo();
						var rep = BitSetUtils.min(taxa);
						var subtree = new PhyloTree();
						var root = subtree.newNode(taxa);
						subtree.setRoot(root);
						copySubTree(v, root);
						if (verboseSubtreeReduction) {
							System.err.println("Subtree:");
							TreesUtils.addLabels(subtree, taxaBlock::getLabel);
							System.err.println(subtree.toBracketString(false));
						}

						root.setInfo(taxa);
						representativeSubtreeMap.put(rep, subtree);
					}
				}
				if (!representativeSubtreeMap.isEmpty()) {
					for (var tree : inputTrees) {
						var taxa = BitSetUtils.asBitSet(tree.getTaxa());
						try (var nodeClusterMap = TreesUtils.extractClusters(tree)) {
							for (var rep : representativeSubtreeMap.keySet()) {
								var set = (BitSet) representativeSubtreeMap.get(rep).getRoot().getInfo();
								var induced = BitSetUtils.intersection(set, taxa);
								if (induced.cardinality() > 0) {
									var v = tree.nodeStream().filter(w -> nodeClusterMap.get(w).equals(induced)).findAny().orElse(null);
									var toDelete = new ArrayList<Node>();
									if (v != null && v.getInDegree() == 1) {
										tree.postorderTraversal(v, toDelete::add);
										toDelete.stream().filter(w -> w != v).forEach(w -> {
											tree.clearTaxa(w);
											tree.deleteNode(w);
										});
										tree.addTaxon(v, rep);
										tree.setLabel(v, "T" + rep);
									}
								}
							}
						}
					}
				}

				if (verboseSubtreeReduction) {
					System.err.println("Reduced:");
					System.err.println(NewickIO.toString(inputTrees, false));
				}

				var taxa = new BitSet();
				for (var tree : inputTrees) {
					taxa.or(BitSetUtils.asBitSet(tree.getTaxa()));
				}
				ntax = taxa.cardinality();
			}
			if (!representativeSubtreeMap.isEmpty()) {
				System.err.println("Subtree reduction: " + taxaBlock.getNtax() + " -> " + ntax);
			} else {
				System.err.println("Subtree reduction: no reduction");
			}

		}

			List<PhyloTree> result;
			if (inputTrees.size() <= 1) {
				result = inputTrees;
			} else {
				var numberOfRandomOrderings = computeNumberOfRandomOrderings(ntax, getOptionSearchHeuristic());
				result = PhyloFusionAlgorithm.apply(numberOfRandomOrderings, inputTrees, progress);
			}

		// undo subtree reduction:
			for (var network : result) {
				if (!representativeSubtreeMap.isEmpty()) {
					if (verboseSubtreeReduction) {
						System.err.println("Network:");
						TreesUtils.addLabels(network, taxaBlock::getLabel);
						System.err.println(NewickIO.toString(network, false));
					}

					for (var rep : representativeSubtreeMap.keySet()) {
						for (var v : network.nodes()) {
							if (network.getTaxon(v) == rep) {
								network.clearTaxa(v);
								network.setLabel(v, null);
								copySubTree(representativeSubtreeMap.get(rep).getRoot(), v);
								break;
							}
						}
					}
					if (verboseSubtreeReduction) {
						TreesUtils.addLabels(network, taxaBlock::getLabel);
						System.err.println("Network:");
						System.err.println(NewickIO.toString(network, false));
					}
				}

				for (var e : network.edges()) {
					network.setReticulate(e, e.getTarget().getInDegree() > 1);
				}
				if (isOptionCalculateWeights())
					if (!NetworkUtils.setEdgeWeights(treesBlock.getTrees(), network, isOptionNormalizeEdgeWeights(), 1500))
						break;
			}

			outputBlock.setPartial(false);
			outputBlock.setRooted(true);

			var count = 0;
			for (var network : result) {
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
			}
	}

	private void copySubTree(Node sourceRoot, Node targetRoot) {
		var sourceTree = (PhyloTree) sourceRoot.getOwner();
		var targetTree = (PhyloTree) targetRoot.getOwner();
		try (NodeArray<Node> old2new = sourceTree.newNodeArray()) {
			old2new.put(sourceRoot, targetRoot);
			var queue = new LinkedList<>(IteratorUtils.asList(sourceRoot.children()));
			while (!queue.isEmpty()) {
				var v = queue.pop();
				var w = targetTree.newNode();
				old2new.put(v, w);
				for (var t : sourceTree.getTaxa(v)) {
					targetTree.addTaxon(w, t);
				}
				if (v != sourceRoot) {
					var e = v.getFirstInEdge();
					var f = targetTree.newEdge(old2new.get(e.getSource()), w);
					targetTree.setWeight(f, sourceTree.getWeight(e));
				}
				queue.addAll(IteratorUtils.asList(v.children()));
			}
		}
	}

	private long computeNumberOfRandomOrderings(int ntax, Search optionSearch) {
		return switch (optionSearch) {
			case Fast -> Math.max(100, 10L * ntax);
			case Medium -> 150L * ntax;
			case Thorough -> 300L * ntax;
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

	public boolean isOptionSubtreeReduction() {
		return optionSubtreeReduction.get();
	}

	public BooleanProperty optionSubtreeReductionProperty() {
		return optionSubtreeReduction;
	}

	public void setOptionSubtreeReduction(boolean optionSubtreeReduction) {
		this.optionSubtreeReduction.set(optionSubtreeReduction);
	}
}

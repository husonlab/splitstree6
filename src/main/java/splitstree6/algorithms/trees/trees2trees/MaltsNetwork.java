/*
 *  MaltsNetwork.java Copyright (C) 2024 Daniel H. Huson
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
import javafx.beans.property.SimpleBooleanProperty;
import jloda.graph.Node;
import jloda.graph.NodeArray;
import jloda.phylo.NewickIO;
import jloda.phylo.PhyloTree;
import jloda.util.*;
import jloda.util.progress.ProgressListener;
import splitstree6.algorithms.utils.MutualRefinement;
import splitstree6.data.TaxaBlock;
import splitstree6.data.TreesBlock;
import splitstree6.splits.TreesUtils;
import splitstree6.xtra.hyperstrings.HyperSequence;
import splitstree6.xtra.hyperstrings.ProgressiveSCS;
import splitstree6.xtra.kernelize.Kernelize;

import java.io.IOException;
import java.util.*;

public class MaltsNetwork extends Trees2Trees {
	private final BooleanProperty optionMutualRefinement = new SimpleBooleanProperty(this, "optionMutualRefinement", false);

	private final BooleanProperty optionKernelization = new SimpleBooleanProperty(this, "optionKernelization", false);

	private final BooleanProperty optionRemoveDuplicates = new SimpleBooleanProperty(this, "optionRemoveDuplicates", false);

	@Override
	public String getCitation() {
		return "Zhang et al 2023; L. Zhang, N. Abhari, C. Colijn and Y Wu." +
			   " A fast and scalable method for inferring phylogenetic networks from trees by aligning lineage taxon strings. Genome Res. 2023.;"
			   + "Zhang et al 2024; L. Zhang, B. Cetinkaya and DH Huson. Hybridization networks from phylogenetic trees, in preparation.";
	}

	@Override
	public String getShortDescription() {
		return "Computes one or more rooted networks that contain all input trees using the M-ALTS algorithm.";
	}

	@Override
	public List<String> listOptions() {
		return List.of(optionMutualRefinement.getName(), optionRemoveDuplicates.getName()); //, optionKernelization.getName());
	}

	@Override
	public String getToolTip(String optionName) {
		if (!optionName.startsWith("option")) {
			optionName = "option" + optionName;
		}
		return switch (optionName) {
			case "optionMutualRefinement" -> "mutually refine trees during preprocessing";
			case "optionKernelization" -> "perform kernelization during preprocessing";
			case "optionRemoveDuplicates" -> "remove duplicate networks in output";
			default -> super.getToolTip(optionName);
		};
	}

	/**
	 * run the MALTS algorithm
	 */
	@Override
	public void compute(ProgressListener progress, TaxaBlock taxaBlock, TreesBlock treesBlock, TreesBlock outputBlock) throws IOException {
		progress.setTasks("Computing hybridization result", "(Unknown how long this will really take)");

		Collection<PhyloTree> inputTrees;
		if (isOptionMutualRefinement()) {
			inputTrees = MutualRefinement.apply(treesBlock.getTrees(), MutualRefinement.Strategy.All, true);
			if (false)
				System.err.println("Refined:\n" + NewickIO.toString(inputTrees, false));
		} else {
			inputTrees = new ArrayList<>();
			for (var tree : treesBlock.getTrees()) {
				inputTrees.add(new PhyloTree(tree));
			}
		}
		Collection<PhyloTree> result;
		if (inputTrees.size() <= 1) {
			result = inputTrees;
		} else if (!isOptionKernelization()) {
			result = apply(inputTrees, progress); // kernelization is broken
		} else {
			result = Kernelize.apply(progress, taxaBlock, inputTrees, MaltsNetwork::apply, 100000);
		}

		// todo: use the input weights...
		for (var network : result) {
			for (var e : network.edges()) {
				if (e.getTarget().getInDegree() > 1) {
					network.setReticulate(e, true);
					network.setWeight(e, 0.1);
				} else {
					network.setReticulate(e, false);
					network.setWeight(e, 1.0);
				}
			}
		}

		outputBlock.setPartial(false);
		outputBlock.setRooted(true);

		for (var network : result) {
			TreesUtils.addLabels(network, taxaBlock::getLabel);
			outputBlock.getTrees().add(network);
			if (network.nodeStream().anyMatch(v -> v.getInDegree() > 1)) {
				outputBlock.setReticulated(true);
			}
			if (false) { // this takes too long when number of reticulations is large
				var networkClusters = TreesUtils.collectAllSoftwiredClusters(network);
				for (var t = 1; t <= treesBlock.getNTrees(); t++) {
					var tree = treesBlock.getTree(t);
					if (!networkClusters.containsAll(TreesUtils.collectAllHardwiredClusters(tree))) {
						System.err.println("ERROR: Network does not contain tree: " + t);
						for (var cluster : TreesUtils.collectAllHardwiredClusters(tree)) {
							if (!networkClusters.contains(cluster))
								System.err.println("ERROR: missing cluster: " + StringUtils.toString(cluster));
						}
					}
				}
			}
		}
	}

	/**
	 * run the algorithm
	 *
	 * @param inputTrees
	 * @param progress
	 * @return
	 * @throws IOException
	 */
	public static List<PhyloTree> apply(Collection<PhyloTree> inputTrees, ProgressListener progress) throws IOException {
		var taxa = new BitSet();
		var trees = new ArrayList<PhyloTree>();
		for (var tree : inputTrees) {
			tree = new PhyloTree(tree);
			if (tree.getRoot().getOutDegree() > 1) {
				var root = tree.newNode();
				var e = tree.newEdge(root, tree.getRoot());
				tree.setWeight(e, 0);
				tree.setRoot(root);
				taxa.or(BitSetUtils.asBitSet(tree.getTaxa()));
			}
			trees.add(tree);
		}

		var rankings = computeTaxonRankings(taxa, trees);

		var bestHybridizationNumber = new Single<>(Integer.MAX_VALUE);
		var best = new ArrayList<Pair<int[], Map<Integer, HyperSequence>>>();

		try {
			ExecuteInParallel.apply(rankings, taxonRank -> {
				var taxonHyperSequencesMap = computeHyperSequences(taxa, taxonRank, trees);
				var taxonHyperSequenceMap = new HashMap<Integer, HyperSequence>();
				// todo: take different optimal SCS into account
				for (var t : taxonHyperSequencesMap.keySet()) {
					taxonHyperSequenceMap.put(t, ProgressiveSCS.apply(new ArrayList<>(taxonHyperSequencesMap.get(t))));
				}
				var hybridizationNumber = computeHybridizationNumber(taxa.cardinality(), taxonHyperSequenceMap);
				synchronized (bestHybridizationNumber) {
					if (hybridizationNumber < bestHybridizationNumber.get()) {
						bestHybridizationNumber.set(hybridizationNumber);
						best.clear();
					}
					if (hybridizationNumber == bestHybridizationNumber.get()) {
						best.add(new Pair<>(taxonRank, taxonHyperSequenceMap));
					}
				}
			}, ProgramExecutorService.getNumberOfCoresToUse(), progress);
		} catch (Exception e) {
			throw new IOException(e);
		}

		return best.stream().map(pair -> computeNetwork(pair.getFirst(), pair.getSecond())).toList();
	}

	private static Collection<? extends int[]> computeTaxonRankings(BitSet taxa, ArrayList<PhyloTree> trees) {
		var order = new int[taxa.cardinality()];
		var orderings = new ArrayList<int[]>();
		var hybridizationNumber = new Single<>(Integer.MAX_VALUE);
		computeTaxonRankingsRec(0, order, taxa, BitSetUtils.copy(taxa), trees, hybridizationNumber, orderings);
		return orderings;
	}

	/**
	 * recursively use the "best next" heuristic to compute best order
	 *
	 * @param pos           position in orde
	 * @param order         current order
	 * @param remainingTaxa remaining taxa
	 * @param trees         all trees
	 * @param globalScore   best hybridization number so far
	 * @param rankings      best orderings
	 */
	private static void computeTaxonRankingsRec(int pos, int[] order, BitSet allTaxa, BitSet remainingTaxa, ArrayList<PhyloTree> trees, Single<Integer> globalScore, ArrayList<int[]> rankings) {
		var bestScore = Integer.MAX_VALUE;
		var bestTaxa = new BitSet();

		for (var t : BitSetUtils.members(remainingTaxa)) {
			order[pos] = t;
			// fill up with remaining:
			var nextPos = pos + 1;
			for (var s : BitSetUtils.members(remainingTaxa)) {
				if (!s.equals(t)) {
					order[nextPos++] = s;
				}
			}
			if (true) {
				var seen = new BitSet();
				for (var taxon : order) {
					if (seen.get(taxon))
						System.err.println("ERROR: Already seen: " + taxon);
					else seen.set(taxon);
				}
				if (BitSetUtils.compare(allTaxa, seen) != 0) {
					System.err.println("ERROR: taxa!=seen: " + SetUtils.symmetricDifference(BitSetUtils.asSet(allTaxa), BitSetUtils.asSet(seen)));
				}
			}

			var taxonHyperSequencesMap = computeHyperSequences(allTaxa, ranking(order), trees);
			var taxonHyperSequenceMap = new HashMap<Integer, HyperSequence>();
			// todo: take different optimal SCS into account
			for (var t1 : taxonHyperSequencesMap.keySet()) {
				taxonHyperSequenceMap.put(t1, ProgressiveSCS.apply(new ArrayList<>(taxonHyperSequencesMap.get(t1))));
			}
			var h = computeHybridizationNumber(allTaxa.cardinality(), taxonHyperSequenceMap);

			// compute score
			if (h < bestScore) {
				bestTaxa.clear();
				bestScore = h;
			}
			if (h == bestScore && bestTaxa.cardinality() < 1) {
				bestTaxa.set(t);
			}
		}

		for (var t : BitSetUtils.members(bestTaxa)) {
			remainingTaxa.clear(t);
			try {
				order[pos] = t;
				for (var p = pos + 1; p < order.length; p++) {
					order[p] = 0;
				}

				if (remainingTaxa.cardinality() > 0) {
					if (bestScore <= globalScore.get()) {
						computeTaxonRankingsRec(pos + 1, order, allTaxa, remainingTaxa, trees, globalScore, rankings);
					}
				} else { // completed ordering,
					if (bestScore < globalScore.get()) {
						rankings.clear();
						globalScore.set(bestScore);
					}
					if (bestScore == globalScore.get()) {
						rankings.add(ranking(order));
					}
				}
			} finally {
				remainingTaxa.set(t);
			}
		}
	}

	/**
	 * compute the hybridization number for this map
	 *
	 * @param taxonHyperSequenceMap taxon to scs map
	 * @return hybridization number
	 */
	private static int computeHybridizationNumber(int nTaxa, HashMap<Integer, HyperSequence> taxonHyperSequenceMap) {
		var total = 0;
		for (var hyperSequence : taxonHyperSequenceMap.values()) {
			for (var component : hyperSequence.array()) {
				total += component.cardinality();
			}
		}
		return total - (nTaxa - 1);
	}

	/**
	 * compute the network for a given taxon-to-hyper-sequence map
	 *
	 * @param taxonHyperSequenceMap the taxon to hyper-sequence map
	 * @return the corresponding network
	 */
	private static PhyloTree computeNetwork(int[] taxonRank, Map<Integer, HyperSequence> taxonHyperSequenceMap) {
		var ordering = inorder(taxonRank);

		var network = new PhyloTree();
		network.setName("MALTS");
		try (NodeArray<BitSet> label = network.newNodeArray()) {
			var taxonStartMap = new HashMap<Integer, Node>();
			var taxonChainMap = new HashMap<Integer, ArrayList<Node>>();
			for (var t : ordering) {
				var start = network.newNode();
				label.put(start, BitSetUtils.asBitSet(t));
				if (network.getRoot() == null)
					network.setRoot(start);
				taxonStartMap.put(t, start);
				var prev = start;
				taxonChainMap.put(t, new ArrayList<>());
				if (taxonHyperSequenceMap.containsKey(t)) {
					var hyperSequence = taxonHyperSequenceMap.get(t);
					for (var component : hyperSequence.array()) {
						var v = network.newNode();
						label.put(v, component);
						network.newEdge(prev, v);
						taxonChainMap.get(t).add(v);
						prev = v;
					}
				}
				var end = network.newNode();
				label.put(end, BitSetUtils.asBitSet(t));
				network.addTaxon(end, t);
				network.newEdge(prev, end);
			}
			for (var p : ordering) {
				for (var v : taxonChainMap.get(p)) {
					for (var q : BitSetUtils.members(label.get(v))) {
						var w = taxonStartMap.get(q);
						network.newEdge(v, w);
					}
				}
			}
		}

		for (var v : network.nodeStream().filter(v -> v.getInDegree() == 1 && v.getOutDegree() == 1).toList()) {
			network.delDivertex(v);
		}

		network.edgeStream().filter(e -> e.getTarget().getInDegree() > 1).forEach(e -> network.setReticulate(e, true));

		return network;
	}

	/**
	 * list taxa in order of their ranking
	 *
	 * @param taxonRank taxon ranking
	 * @return ordered taxa
	 */
	private static ArrayList<Integer> inorder(int[] taxonRank) {
		var ordering = new int[taxonRank.length];
		for (var t = 1; t < taxonRank.length; t++) {
			ordering[taxonRank[t]] = t;
		}
		var list = new ArrayList<Integer>();
		for (var t = 1; t < ordering.length; t++) {
			if (ordering[t] != 0)
				list.add(ordering[t]);
		}
		return list;
	}

	private static int[] ranking(int[] order) {
		var taxonRank = new int[Arrays.stream(order).max().orElse(0) + 1];
		var rank = 0;
		for (int taxon : order) {
			if (taxon > 0)
				taxonRank[taxon] = ++rank;
		}
		return taxonRank;
	}

	/**
	 * extract all hyper sequences
	 *
	 * @param taxa      all taxa
	 * @param taxonRank the taxon ranking
	 * @param trees     all trees
	 * @return mapping from taxa to hyper sequences
	 */
	private static Map<Integer, Set<HyperSequence>> computeHyperSequences(BitSet taxa, int[] taxonRank, List<PhyloTree> trees) {
		var minTaxon = findMin(taxa, taxonRank);
		var taxonHyperSequencesMap = new HashMap<Integer, Set<HyperSequence>>();
		for (var tree : trees) {
			try (NodeArray<BitSet> nodeLabels = tree.newNodeArray(); NodeArray<BitSet> taxaBelow = tree.newNodeArray()) {
				// compute labeling:
				tree.postorderTraversal(v -> {
					if (v.isLeaf()) {
						taxaBelow.computeIfAbsent(v, k -> new BitSet()).set(tree.getTaxon(v));
						nodeLabels.computeIfAbsent(v, k -> new BitSet()).set(tree.getTaxon(v));
					} else {
						Node smallestChild = null;
						var childSmallestLeafRank = Integer.MAX_VALUE;
						for (var w : v.children()) {
							var minLeafRankBelow = findMin(taxaBelow.get(w), taxonRank);
							var leafRank = taxonRank[minLeafRankBelow];
							if (leafRank < childSmallestLeafRank) {
								smallestChild = w;
								childSmallestLeafRank = leafRank;
							}
						}
						nodeLabels.put(v, new BitSet());
						for (var w : v.children()) {
							if (w != smallestChild) {
								nodeLabels.get(v).set(findMin(taxaBelow.get(w), taxonRank));
							}
						}
						taxaBelow.put(v, new BitSet());
						for (var w : v.children()) {
							taxaBelow.get(v).or(taxaBelow.get(w));
						}
					}
				});
				nodeLabels.computeIfAbsent(tree.getRoot(), k -> new BitSet()).set(minTaxon);


				if (true) { // check that each taxon appears exactly twice in a label:
					var taxonCount = new HashMap<Integer, Integer>();
					for (var v : tree.nodes()) {
						for (var t : BitSetUtils.members(nodeLabels.get(v))) {
							taxonCount.put(t, taxonCount.getOrDefault(t, 0) + 1);
						}
					}
					for (var t : BitSetUtils.members(taxa)) {
						if (taxonCount.get(t) == null || taxonCount.get(t) != 2)
							System.err.println("Error: taxon " + t + ": count=" + taxonCount.get(t));
					}
				}

				// extract hyper sequences
				var taxonReverseSequenceMap = new HashMap<Integer, ArrayList<BitSet>>();
				tree.postorderTraversal(v -> {
					if (v.isLeaf()) {
						taxonReverseSequenceMap.put(tree.getTaxon(v), new ArrayList<>());
					} else {
						for (var t : BitSetUtils.members(taxaBelow.get(v))) {
							var labels = nodeLabels.get(v);
							if (labels.get(t)) { // end of sequence for this taxon
								var sequence = taxonReverseSequenceMap.get(t);
								if (!sequence.isEmpty()) {
									CollectionUtils.reverseInPlace(sequence);
									var hyperSequence = new HyperSequence(sequence);
									taxonHyperSequencesMap.computeIfAbsent(t, k -> new HashSet<>()).add(hyperSequence);
								}
								taxonReverseSequenceMap.remove(t);
							} else {
								if (taxonReverseSequenceMap.containsKey(t)) {
									var sequence = taxonReverseSequenceMap.get(t);
									sequence.add(labels);
								}
							}
						}
					}
				});
				if (!taxonReverseSequenceMap.isEmpty()) {
					throw new RuntimeException("taxonReverseSequenceMap: " + taxonReverseSequenceMap.size());
				}
			}
		}
		return taxonHyperSequencesMap;
	}

	/**
	 * find taxon of minimum rank
	 *
	 * @param taxa      the taxa to consider
	 * @param taxonRank taxon ranks
	 * @return taxon of smallest rank
	 */
	private static int findMin(BitSet taxa, int[] taxonRank) {
		var result = -1;
		for (var t : BitSetUtils.members(taxa)) {
			if (result == -1)
				result = t;
			else if (taxonRank[t] < taxonRank[result])
				result = t;
		}
		return result;
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

	public boolean isOptionKernelization() {
		return optionKernelization.get();
	}

	public BooleanProperty optionKernelizationProperty() {
		return optionKernelization;
	}

	public void setOptionKernelization(boolean optionKernelization) {
		this.optionKernelization.set(optionKernelization);
	}

	public boolean isOptionRemoveDuplicates() {
		return optionRemoveDuplicates.get();
	}

	public BooleanProperty optionRemoveDuplicatesProperty() {
		return optionRemoveDuplicates;
	}

	public void setOptionRemoveDuplicates(boolean optionRemoveDuplicates) {
		this.optionRemoveDuplicates.set(optionRemoveDuplicates);
	}
}

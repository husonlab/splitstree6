/*
 *  PhyloFusionAlgorithmMay2024.java Copyright (C) 2024 Daniel H. Huson
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

package splitstree6.compute.phylofusion;

import jloda.graph.Node;
import jloda.graph.NodeArray;
import jloda.phylo.PhyloGraph;
import jloda.phylo.PhyloTree;
import jloda.util.*;
import jloda.util.progress.ProgressListener;
import splitstree6.algorithms.trees.trees2trees.PhyloFusion;
import splitstree6.utils.PathMultiplicityDistance;
import splitstree6.xtra.hyperstrings.HyperSequence;
import splitstree6.xtra.hyperstrings.ProgressiveSCS;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * the PhyloFusion algorithm
 * Daniel Huson, 5.2024
 */
public class PhyloFusionAlgorithmOct2024 {
	/**
	 * run the algorithm
	 *
	 * @param inputTrees input rooted trees
	 * @param progress   progress listener
	 * @return the computed networks
	 * @throws IOException user canceled
	 */
	public static List<PhyloTree> apply(PhyloFusion.Search search, List<PhyloTree> inputTrees, boolean onlyOneNetwork, ProgressListener progress) throws IOException {
		if (inputTrees.size() == 1) {
			return List.of(new PhyloTree(inputTrees.get(0)));
		}

		var taxa = union(inputTrees.stream().map(PhyloGraph::getTaxa).toList());

		var trees = new ArrayList<PhyloTree>();
		for (var tree : inputTrees) {
			tree = new PhyloTree(tree);
			if (tree.getRoot().getOutDegree() > 1) {
				var root = tree.newNode();
				var e = tree.newEdge(root, tree.getRoot());
				tree.setWeight(e, 0);
				tree.setRoot(root);
			}
			trees.add(tree);
		}

		var bestHybridizationNumber = new Single<>(Integer.MAX_VALUE);
		var best = new ArrayList<Pair<int[], Map<Integer, HyperSequence>>>();


		for (var ranking : computeTaxonRankings(progress, taxa, trees, search)) {
			var taxonHyperSequencesMap = computeHyperSequences(progress, taxa, ranking, trees);
			var taxonHyperSequenceMap = new HashMap<Integer, HyperSequence>();
			// todo: take different optimal SCS into account
			for (var t : taxonHyperSequencesMap.keySet()) {
				var hyperSequence = ProgressiveSCS.apply(new ArrayList<>(taxonHyperSequencesMap.get(t)));

				// simplification
				if (hyperSequence != null) {
					var prev = new BitSet();
					for (var i = 0; i + 1 < hyperSequence.size(); i++) {
						var item = hyperSequence.get(i);
						var next = hyperSequence.get(i + 1);
						prev = BitSetUtils.minus(BitSetUtils.intersection(item, next), prev);
						item.andNot(prev);
					}
					hyperSequence.removeEmptyElements();
				}
				taxonHyperSequenceMap.put(t, hyperSequence);
			}

			var hybridizationNumber = computeHybridizationNumber(taxa.cardinality(), taxonHyperSequenceMap);
			if (hybridizationNumber < bestHybridizationNumber.get()) {
				bestHybridizationNumber.set(hybridizationNumber);
				best.clear();
			}
			if (hybridizationNumber == bestHybridizationNumber.get()) {
				best.add(new Pair<>(ranking, taxonHyperSequenceMap));
			}
		}

		if (onlyOneNetwork && best.size() > 1) {
			var one = best.get(0);
			best.clear();
			best.add(one);
		}

		progress.setSubtask("creating networks");
		progress.setMaximum(best.size());
		progress.setProgress(0);

		var result = new ArrayList<PhyloTree>();
		for (var network : best.stream().map(pair -> computeNetwork(pair.getFirst(), pair.getSecond())).toList()) {
			if (result.stream().noneMatch(other -> PathMultiplicityDistance.compute(network, other) == 0)) {
				result.add(network);
			}
			progress.incrementProgress();
		}
		return result;
	}

	private static List<int[]> computeTaxonRankings(ProgressListener progress, BitSet taxa, ArrayList<PhyloTree> trees, PhyloFusion.Search search) throws CanceledException {
		final var nTax = taxa.cardinality();
		final var scoredOrderings1 = new FixedCapacitySortedSet<>(nTax, ScoredOrdering::compare);
		final var scoredOrderings2 = new FixedCapacitySortedSet<>(nTax, ScoredOrdering::compare);

		var startKeepSize = switch (search) {
			case Fast -> nTax;
			case Medium -> Math.max(500, nTax);
			case Thorough -> Math.max(4000, nTax);
		};
		var endKeepSize = switch (search) {
			case Fast -> 5;
			case Medium -> Math.min(100, nTax);
			case Thorough -> Math.min(400, nTax);
		};

		// seed elements
		{
			var array = new int[nTax];
			var count = 0;
			for (var t : BitSetUtils.members(taxa)) {
				array[count++] = t;
			}
			for (var other = 0; other < nTax; other++) {
				scoredOrderings1.add(new ScoredOrdering(Integer.MAX_VALUE, 0, other, swapAndCopy(array, 0, other)));
			}
		}

		progress.setTasks("Searching orderings", "taxa=" + nTax + " trees=" + trees.size());
		progress.setMaximum(nTax);
		progress.setProgress(1);

		for (var pos = 1; pos < nTax; pos++) {
			if (pos > 1) {
				scoredOrderings1.clear();
				scoredOrderings1.addAll(scoredOrderings2);
				scoredOrderings2.clear();
				progress.incrementProgress();

				var keep = (int) Math.ceil(((double) (nTax - pos) / nTax) * startKeepSize + ((double) pos / nTax) * endKeepSize);
				scoredOrderings1.changeCapacity(keep);
				scoredOrderings2.changeCapacity(keep);
			}

			var finalPos = pos;
			try {
				ExecuteInParallel.apply(scoredOrderings1, order -> {
					for (var other = finalPos; other < nTax; other++) {
						var ordering = swapAndCopy(order.ordering, finalPos, other);
						var score = evaluate(progress, taxa, trees, ordering, finalPos);
						var scoredOrdering = new ScoredOrdering(score, finalPos, other, ordering);
						scoredOrderings2.add(scoredOrdering);
					}
				}, ProgramExecutorService.getNumberOfCoresToUse());
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		}
		return scoredOrderings2.stream().map(ScoredOrdering::ordering).map(PhyloFusionAlgorithmOct2024::ranking).toList();
	}

	public record ScoredOrdering(int score, int pos, int other, int[] ordering) {
		public static int compare(ScoredOrdering a, ScoredOrdering b) {
			int result = Integer.compare(a.score, b.score);
			if (result != 0) return result;
			result = Integer.compare(a.pos, b.pos);
			if (result != 0) return result;
			result = Integer.compare(a.other, b.other);
			if (result != 0) return result;
			return Arrays.compare(a.ordering, b.ordering);
		}
	}

	public static int evaluate(ProgressListener progress, BitSet taxa, List<PhyloTree> trees, int[] ordering, int pos) throws CanceledException {
		if (true) {
			var taxonHyperSequencesMap = computeHyperSequences(progress, taxa, ranking(ordering), trees);
			var taxonHyperSequenceMap = new HashMap<Integer, HyperSequence>();
			for (var t : taxonHyperSequencesMap.keySet()) {
				taxonHyperSequenceMap.put(t, ProgressiveSCS.apply(new ArrayList<>(taxonHyperSequencesMap.get(t))));
			}
			return computeHybridizationNumber(taxa.cardinality(), taxonHyperSequenceMap);
		} else {
			var taxonHyperSequencesMap = computeHyperSequences(progress, taxa, ranking(ordering), trees);
			var taxonHyperSequenceMap = new HashMap<Integer, HyperSequence>();
			var activeTaxa = BitSetUtils.asBitSet(Arrays.copyOf(ordering, pos + 1));
			for (var t : BitSetUtils.members(activeTaxa)) {
				if (taxonHyperSequencesMap.containsKey(t)) {
					var sequences = taxonHyperSequencesMap.get(t).stream().map(h -> h.induce(activeTaxa))
							.distinct().collect(Collectors.toCollection(ArrayList::new));
					taxonHyperSequenceMap.put(t, ProgressiveSCS.apply(sequences));
				}
			}
			return computeHybridizationNumber(activeTaxa.cardinality(), taxonHyperSequenceMap);
	}
	}

	public static int[] swapAndCopy(int[] array, int i, int j) {
		array = Arrays.copyOf(array, array.length);
		if (i != j) {
			var tmp = array[i];
			array[i] = array[j];
			array[j] = tmp;
		}
		return array;
	}

	/**
	 * compute the hybridization number for this map
	 *
	 * @param taxonHyperSequenceMap taxon-to-hypersequence map
	 * @return hybridization number
	 */
	public static int computeHybridizationNumber(int nTaxa, HashMap<Integer, HyperSequence> taxonHyperSequenceMap) {
		var total = 0;
		for (var hyperSequence : taxonHyperSequenceMap.values()) {
			for (var component : hyperSequence.members()) {
				total += component.cardinality();
			}
		}
		return total - (nTaxa - 1);
	}

	/**
	 * compute the network for a given taxon-to-hypersequence map
	 *
	 * @param taxonHyperSequenceMap the taxon to hypersequence map
	 * @return the corresponding network
	 */
	public static PhyloTree computeNetwork(int[] taxonRank, Map<Integer, HyperSequence> taxonHyperSequenceMap) {
		var ordering = inorder(taxonRank);

		var network = new PhyloTree();
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
					for (var component : hyperSequence.members()) {
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
		network.edgeStream().forEach(e -> network.setReticulate(e, e.getTarget().getInDegree() > 1));

		return network;
	}

	/**
	 * for each taxon, extract all hyper sequences
	 *
	 * @param taxa      all taxa
	 * @param taxonRank the taxon ranking
	 * @param trees     all trees
	 * @return mapping from taxa to hypersequences
	 */
	public static Map<Integer, Set<HyperSequence>> computeHyperSequences(ProgressListener progress, BitSet taxa, int[] taxonRank, List<PhyloTree> trees) throws CanceledException {
		var taxonHyperSequencesMap = new HashMap<Integer, Set<HyperSequence>>();
		for (var tree : trees) {
			var minTaxon = findMin(BitSetUtils.asBitSet(tree.getTaxa()), taxonRank);

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

				if (false) {
					for (var v : tree.nodes()) {
						if (!v.isLeaf()) {
							tree.setLabel(v, "t" + StringUtils.toString(BitSetUtils.asArrayWith0s(BitSetUtils.max(taxa), nodeLabels.get(v)), ""));
						}
					}
					System.err.println("ranks: " + StringUtils.toString(taxonRank, " "));
					System.err.println(tree.toBracketString(false) + ";");
					for (var v : tree.nodes()) {
						if (!v.isLeaf()) {
							tree.setLabel(v, null);
						}
					}
					System.err.println();
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

				if (true) { // check that each taxon appears exactly twice in a label:
					var taxonCount = new HashMap<Integer, Integer>();
					var treeTaxa = new BitSet();
					for (var v : tree.nodes()) {
						for (var t : BitSetUtils.members(nodeLabels.get(v))) {
							taxonCount.put(t, taxonCount.getOrDefault(t, 0) + 1);
							treeTaxa.set(t);
						}
					}
					for (var t : BitSetUtils.members(treeTaxa)) {
						if (taxonCount.get(t) == null || taxonCount.get(t) != 2)
							System.err.println("Error: taxon " + t + ": count=" + taxonCount.get(t));
					}
				}

				if (!taxonReverseSequenceMap.isEmpty()) {
					throw new RuntimeException("taxonReverseSequenceMap: " + taxonReverseSequenceMap.size());
				}
			}
			progress.checkForCancel();
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
	public static int findMin(BitSet taxa, int[] taxonRank) {
		var result = -1;
		for (var t : BitSetUtils.members(taxa)) {
			if (result == -1)
				result = t;
			else if (taxonRank[t] < taxonRank[result])
				result = t;
		}
		return result;
	}

	/**
	 * list taxa in order of their ranking
	 *
	 * @param taxonRank taxon ranking
	 * @return ordered taxa
	 */
	public static ArrayList<Integer> inorder(int[] taxonRank) {
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

	/**
	 * compute taxon ranking from taxon ordering
	 *
	 * @param order ordering
	 * @return ranking
	 */
	public static int[] ranking(int[] order) {
		var taxonRank = new int[Arrays.stream(order).max().orElse(0) + 1];
		var rank = 0;
		for (int taxon : order) {
			if (taxon > 0)
				taxonRank[taxon] = ++rank;
		}
		return taxonRank;
	}

	/**
	 * union of integers
	 *
	 * @param sets integer iterables
	 * @return union
	 */
	public static BitSet union(Collection<Iterable<Integer>> sets) {
		var result = new BitSet();
		for (var set : sets) {
			for (var t : set) {
				result.set(t);
			}
		}
		return result;
	}
}

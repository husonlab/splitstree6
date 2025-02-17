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
import jloda.phylo.PhyloTree;
import jloda.util.*;
import jloda.util.progress.ProgressListener;
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
public class PhyloFusionAlgorithm {
	/**
	 * run the algorithm
	 *
	 * @param inputTrees input rooted trees
	 * @param progress   progress listener
	 * @return the computed networks
	 * @throws IOException user canceled
	 */
	public static List<PhyloTree> apply(List<PhyloTree> inputTrees, boolean onlyOneNetwork,
										boolean useRefinementHeuristic, boolean useMissingTaxaHeuristic,
										ProgressListener progress) throws IOException {
		if (inputTrees.size() == 1) {
			return List.of(new PhyloTree(inputTrees.get(0)));
		}

		var treeTaxa = new ArrayList<BitSet>();

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
			treeTaxa.add(BitSetUtils.asBitSet(tree.getTaxa()));
		}
		var allTaxa = BitSetUtils.union(treeTaxa);

		var multifurcating = trees.stream().anyMatch(tree -> tree.nodeStream().anyMatch(v -> v.getOutDegree() > 2));
		var missingTaxa = treeTaxa.stream().anyMatch(set -> !allTaxa.equals(set));

		if (false)
			System.err.println("multifurcating: " + multifurcating + ", missing taxa: " + missingTaxa);

		var bestHybridizationNumber = new Single<>(Integer.MAX_VALUE);
		var best = new ArrayList<Pair<int[], Map<Integer, HyperSequence>>>();

		for (var ranking : computeTaxonRankings(progress, multifurcating, missingTaxa, allTaxa, treeTaxa, trees)) {
			var taxonHyperSequencesMap = computeHyperSequenceTable(progress,
					multifurcating && useRefinementHeuristic, missingTaxa && useMissingTaxaHeuristic,
					allTaxa, ranking, treeTaxa, trees);
			var taxonHyperSequenceMap = new HashMap<Integer, HyperSequence>();
			// todo: take different optimal SCS into account
			for (var t : taxonHyperSequencesMap.rowKeySet()) {
				var hyperSequence = ProgressiveSCS.apply(new ArrayList<>(taxonHyperSequencesMap.row(t).values()));

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

			var hybridizationNumber = computeHybridizationNumber(allTaxa.cardinality(), taxonHyperSequenceMap);
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

	private static List<int[]> computeTaxonRankings(ProgressListener progress, boolean multifurcating, boolean missingTaxa,
													BitSet taxa, List<BitSet> treeTaxa, ArrayList<PhyloTree> trees) throws CanceledException {
		final var nTax = taxa.cardinality();
		final var scoredOrderings1 = new FixedCapacitySortedSet<>(1, ScoredOrdering::compare);
		final var scoredOrderings2 = new FixedCapacitySortedSet<>(1, ScoredOrdering::compare);

		final int startKeepSize = 10;
		final int endKeepSize = 10;
		final int iterations = 10;
		final int maxIterationsWithoutImprovement = 4;


		progress.setTasks("Searching orderings", "taxa=" + nTax + " trees=" + trees.size());
		progress.setMaximum((long) nTax * iterations);
		progress.setProgress(1);

		// seed ordering:
		scoredOrderings2.add(new ScoredOrdering(Integer.MAX_VALUE, 0, BitSetUtils.asArray(taxa)));

		var bestScores = new int[iterations];

		var verbose = false;

		for (var iteration = 0; iteration < iterations; iteration++) { // iterate over results
			if (verbose)
				System.err.println("Iteration: " + iteration);
			for (var pos = 0; pos < nTax; pos++) {
				scoredOrderings1.clear();
				scoredOrderings1.addAll(scoredOrderings2);
				scoredOrderings2.clear();
				progress.incrementProgress();

				{
					var keep = (int) Math.ceil(((double) (nTax - pos) / nTax) * startKeepSize + ((double) pos / nTax) * endKeepSize);
					scoredOrderings1.changeCapacity(keep);
					scoredOrderings2.changeCapacity(keep);
					// System.err.println("keep: " + keep + " best score (in): " + scoredOrderings1.first().score);
				}

				var finalIteration = iteration;
				var finalPos = pos;
				try {
					ExecuteInParallel.apply(scoredOrderings1, order -> {
						for (var other = finalPos; other < nTax; other++) {

							var ordering = copyAndSwap(order.ordering, finalPos, other);
							var score = evaluate(progress, multifurcating, missingTaxa, taxa, treeTaxa, trees, ordering, finalPos);
							var scoredOrdering = new ScoredOrdering(score, finalIteration, ordering);
							scoredOrderings2.add(scoredOrdering);
						}
					}, ProgramExecutorService.getNumberOfCoresToUse());
				} catch (Exception e) {
					throw new RuntimeException(e);
				}
				if (verbose)
					System.err.println("best score (out): " + scoredOrderings2.first().score);

			}
			bestScores[iteration] = scoredOrderings2.first().score;
			if (iteration >= maxIterationsWithoutImprovement && bestScores[iteration] == bestScores[iteration - maxIterationsWithoutImprovement]) {
				if (verbose)
					System.err.println("Break in iteration: " + iteration);
				break; // haven't seen an improvement in last 3 iterations
			}
		}

		return scoredOrderings2.stream().map(ScoredOrdering::ordering).map(PhyloFusionAlgorithm::ranking).toList();
	}

	public record ScoredOrdering(int score, int iteration, int[] ordering) {

		public static int compare(ScoredOrdering a, ScoredOrdering b) {
			int result = Integer.compare(a.score, b.score);
			if (result != 0) return result; // lower score is better
			result = Integer.compare(a.iteration, b.iteration);
			if (result != 0) return -result; // newer is better
			return Arrays.compare(a.ordering, b.ordering);
		}
	}

	public static int evaluate(ProgressListener progress, boolean multifurcating, boolean missingTaxa, BitSet taxa, List<BitSet> treeTaxa, List<PhyloTree> trees, int[] ordering, int pos) throws CanceledException {
		if (true) {
			var taxonHyperSequencesMap = computeHyperSequenceTable(progress, multifurcating, missingTaxa, taxa, ranking(ordering), treeTaxa, trees);
			var taxonHyperSequenceMap = new HashMap<Integer, HyperSequence>();
			for (var t : taxonHyperSequencesMap.rowKeySet()) {
				taxonHyperSequenceMap.put(t, ProgressiveSCS.apply(new ArrayList<>(taxonHyperSequencesMap.row(t).values())));
			}
			return computeHybridizationNumber(taxa.cardinality(), taxonHyperSequenceMap);
		} else { // todo: can't get this to work
			var taxonHyperSequencesMap = computeHyperSequenceTable(progress, multifurcating, missingTaxa, taxa, ranking(ordering), treeTaxa, trees);
			var taxonHyperSequenceMap = new HashMap<Integer, HyperSequence>();
			var activeTaxa = BitSetUtils.asBitSet(Arrays.copyOf(ordering, pos + 1));
			for (var t : BitSetUtils.members(activeTaxa)) {
				if (taxonHyperSequencesMap.containsRow(t)) {
					var sequences = taxonHyperSequencesMap.row(t).values().stream().map(h -> h.induce(activeTaxa))
							.distinct().collect(Collectors.toCollection(ArrayList::new));
					taxonHyperSequenceMap.put(t, ProgressiveSCS.apply(sequences));
				}
			}
			return computeHybridizationNumber(activeTaxa.cardinality(), taxonHyperSequenceMap);
		}
	}

	/**
	 * make a copy of the array and then swap entries at positions i and j
	 *
	 * @param array array to copy
	 * @param i     pos to swap
	 * @param j     pos to swap
	 * @return copy with swapped items
	 */
	public static int[] copyAndSwap(int[] array, int i, int j) {
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
			for (var component : hyperSequence.elements()) {
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
					for (var component : hyperSequence.elements()) {
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
	 * for each taxon and tree, extracts the hyper sequence
	 *
	 * @param allTaxa   all taxa
	 * @param taxonRank the taxon ranking
	 * @param trees     all trees
	 * @return table  of hyper sequences indexed by taxon and tree
	 */
	public static Table<Integer, Integer, HyperSequence> computeHyperSequenceTable(ProgressListener progress, boolean useRefinementHeuristic, boolean useMissingTaxaHeuristic, BitSet allTaxa, int[] taxonRank, List<BitSet> treeTaxa, List<PhyloTree> trees) throws CanceledException {
		var hyperSequenceTable = new Table<Integer, Integer, HyperSequence>();
		for (var treeId = 0; treeId < trees.size(); treeId++) {
			var tree = trees.get(treeId);
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
							tree.setLabel(v, "t" + StringUtils.toString(BitSetUtils.asArrayWith0s(BitSetUtils.max(allTaxa), nodeLabels.get(v)), ""));
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
				var finalTreeId = treeId;
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
									hyperSequenceTable.put(t, finalTreeId, hyperSequence);
									//System.err.println("put: t="+t+" tree="+finalTreeId+" seq: "+StringUtils.toString(hyperSequence.elements()," "));
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
					var theTaxa = new BitSet();
					for (var v : tree.nodes()) {
						for (var t : BitSetUtils.members(nodeLabels.get(v))) {
							taxonCount.put(t, taxonCount.getOrDefault(t, 0) + 1);
							theTaxa.set(t);
						}
					}
					for (var t : BitSetUtils.members(theTaxa)) {
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


		if (useRefinementHeuristic) { // apply simplification rules in the case of multifurcations
			applyRefinementRule1(hyperSequenceTable, taxonRank);
		}
		if (useMissingTaxaHeuristic) { // apply simplification rules in the case of missing taxa
				if (false) {
					System.err.println("order: " + StringUtils.toString(inorder(taxonRank), "<"));
					System.err.println("trees:");
					for (var tree : trees) {
						var copy = new PhyloTree(tree);
						for (var v : copy.nodes()) {
							if (v.isLeaf())
								copy.setLabel(v, copy.getLabel(v) + "/" + copy.getTaxon(v));
						}
						System.err.println(copy.toBracketString(false) + ";");
					}
					System.err.println("table:");
					for (var tax : hyperSequenceTable.rowKeySet()) {
						for (var tree : hyperSequenceTable.columnKeySet()) {
							var seq = hyperSequenceTable.get(tax, tree);
							System.err.printf(" (tax=%d,tree=%d)= %s%n", tax, tree, seq != null ? StringUtils.toString(seq.elements(), " ") : "null");
						}
						System.err.println();
					}
					System.err.println();
				}

				applyMissingTaxaRule3(hyperSequenceTable, allTaxa, treeTaxa);
		}
		return hyperSequenceTable;
	}

	/**
	 * this implements refinement rule 1: Consider the HTS seq1s of taxon s in tree T1 has an element E of size >1.
	 * Let z be the largest taxon in E and let R be the set of other elements in E.
	 * If no elements of R are contained anywhere else in seq1s,
	 * then look for a later taxon t and tree T2 such that the HTS seq2t contains the elements of R.
	 * If found, we remove R from E and insert R at the beginning of seq1t, the sequence for t in T. This corresponds to refining the node corresponding
	 * to E along the edge toward taxon t.
	 *
	 * @param hyperSequenceTable the (taxon,tree) to hyper sequence table
	 * @param taxonRank          the taxon ranking
	 */
	private static void applyRefinementRule1(Table<Integer, Integer, HyperSequence> hyperSequenceTable, int[] taxonRank) {
		var ordering = inorder(taxonRank);
		var treeOrder = new TreeSet<>(hyperSequenceTable.columnKeySet());
		for (var i = 0; i < ordering.size() - 1; i++) {
			var taxonS = ordering.get(i);
			for (var tree1 : treeOrder) {
				var seq1s = hyperSequenceTable.get(taxonS, tree1);
				if (seq1s != null) {
					for (var elementE : seq1s.elements()) {
						if (elementE.cardinality() > 1) { // multifurcation
							var taxonZ = getLargest(taxonRank, elementE);
							var remainingR = BitSetUtils.minus(elementE, BitSetUtils.asBitSet(taxonZ));
							if (seq1s.elements().stream().filter(e -> e != elementE).noneMatch(e -> e.intersects(remainingR))) {
								loop:
								for (var j = i + 1; j < ordering.size(); j++) {
									var taxonT = ordering.get(j);
									for (var tree2 : treeOrder) {
										if (!tree2.equals(tree1)) {
											var seq2t = hyperSequenceTable.get(taxonT, tree2);
											if (seq2t != null) {
												if (BitSetUtils.contains(BitSetUtils.union(seq2t.elements()), remainingR)) {
													elementE.andNot(remainingR);
													var seq1t = hyperSequenceTable.get(taxonT, tree1);
													if (seq1t != null) {
														seq1t.elements().add(0, remainingR);
														break loop;
													}
												}
											}
										}
									}
								}
							}
						}
					}
				}
			}
		}
	}

	/**
	 * this rule addresses the problem of missing taxa.
	 * If tree_j contains taxon a - with a in Sij (the hyper sequence for taxon i in tree j) - and another tree_p doesn't,
	 * and if there is a taxon b in Sip (the h.s. for i in tree_p) that is contained in Saj (the h.s. for a in tree_j),
	 * then
	 * - in Sip, replace b by a, and
	 * - set Sap= b.
	 * <p>
	 * Note that this is partially redundant, because in the case that one tree is contained in the other, then we remove
	 * the contained tree in preprocessing in the method PhyloFusion.removeContainedAndRefine()
	 *
	 * @param hyperSequenceTable hyper sequences
	 */
	private static void applyMissingTaxaRule3(Table<Integer, Integer, HyperSequence> hyperSequenceTable, BitSet allTaxa,
											  List<BitSet> treeTaxa) {
		var verbose = false;

		var treeOrder = new TreeSet<>(hyperSequenceTable.columnKeySet());

		for (var tax_a : BitSetUtils.members(allTaxa)) {
			var aSet = BitSetUtils.asBitSet(tax_a);
			if (aSet.cardinality() == 1) {
				if (verbose)
					System.err.println("a=" + tax_a);
				loop:
				for (var tree_j : treeOrder) {
					if (treeTaxa.get(tree_j).get(tax_a)) {
						if (hyperSequenceTable.contains(tax_a, tree_j)) {
							var seq_saj = hyperSequenceTable.get(tax_a, tree_j);
							if (verbose)
								System.err.println("tree_j:" + tree_j + " (contains " + tax_a + ")");
							for (var tree_p : treeOrder) {
								if (!tree_j.equals(tree_p)) {
									if (!treeTaxa.get(tree_p).get(tax_a)) {
										if (verbose)
											System.err.println("	tree_p:" + tree_p + " (doesn't contain " + tax_a + ")");
										for (var tax_i : hyperSequenceTable.rowKeySet()) {
											if (verbose)
												System.err.println("	i=" + tax_i);
											if (hyperSequenceTable.contains(tax_i, tree_j)) {
												var seq_sij = hyperSequenceTable.get(tax_i, tree_j);
												for (var element_of_sij : seq_sij.elements()) {
													if (element_of_sij.equals(aSet) && !BitSetUtils.contains(treeTaxa.get(tree_p), aSet)) {
														if (hyperSequenceTable.contains(tax_i, tree_p)) {
															var seq_sip = hyperSequenceTable.get(tax_i, tree_p);
															for (int pos_in_seq_sip = 0; pos_in_seq_sip < seq_sip.elements().size(); pos_in_seq_sip++) {
																var bSet = seq_sip.elements().get(pos_in_seq_sip);
																if (bSet.cardinality() >= 1 && seq_saj.elements().contains(bSet)) {
																	seq_sip.elements().set(pos_in_seq_sip, aSet);
																	var hyperSequence = new HyperSequence();
																	hyperSequence.add(bSet);
																	hyperSequenceTable.put(tax_a, tree_p, hyperSequence);
																	if (verbose)
																		System.err.println("modified");
																	break loop;
																}
															}
														}
													}
												}
											}
										}
									}
								}
							}
						}
					}
				}
			}
		}
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

	private static int getLargest(int[] taxonRank, BitSet set) {
		var largestRank = 0;
		var largestTaxon = 0;
		for (var t : BitSetUtils.members(set)) {
			if (taxonRank[t] > largestRank) {
				largestRank = taxonRank[t];
				largestTaxon = t;
			}
		}
		return largestTaxon;
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

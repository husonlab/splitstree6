/*
 *  TracedNetwork.java Copyright (C) 2024 Daniel H. Huson
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

import jloda.graph.Edge;
import jloda.graph.Node;
import jloda.graph.NodeArray;
import jloda.phylo.PhyloTree;
import jloda.util.BitSetUtils;
import jloda.util.CollectionUtils;
import jloda.util.Table;
import jloda.util.progress.ProgressListener;

import java.util.*;

/**
 * builds a single PhyloFusion network for a given taxon ranking, tagging each reticulate edge with the input trees
 * that route a lineage through it. This is the metadata-carrying counterpart of {@link PhyloFusionAlgorithm#computeNetwork}:
 * per-tree hyper-sequences are stamped with their tree of origin, the metadata rides through the shortest-common-hyper-
 * sequence alignment ({@link TracedSCS}), and the network builder reads it off to label the reticulate edges.
 * <p>
 * Banu Cetinkaya, 2026
 */
public class TracedNetwork {
	private TracedNetwork() {
	}

	/**
	 * build the traced network for one ranking. Reticulate edges (and nodes) carry the ids of the ORIGINAL input trees.
	 *
	 * @param representedTreeIds original input-tree ids represented by each local tree (indexed as the table's tree keys)
	 */
	public static PhyloTree build(ProgressListener progress, boolean useRefinement, boolean useMissingTaxa,
								  BitSet allTaxa, int[] taxonRank, List<BitSet> treeTaxa, List<PhyloTree> trees,
								  List<BitSet> representedTreeIds) throws jloda.util.CanceledException {
		var table = computeHyperSequenceTable(progress, useRefinement, useMissingTaxa, allTaxa, taxonRank, treeTaxa, trees);
		var map = new HashMap<Integer, TracedHyperSequence>();
		for (var t : table.rowKeySet()) {
			var hyperSequence = TracedSCS.progressive(new ArrayList<>(table.row(t).values()));
			if (hyperSequence != null)
				hyperSequence = simplifyTraceSequence(hyperSequence);
			map.put(t, hyperSequence);
		}
		var network = computeNetwork(taxonRank, map);
		expandActiveToOriginalTreeIds(network, representedTreeIds);
		return network;
	}

	/**
	 * for each taxon and tree, extract the hyper sequence, stamping each element with its source tree
	 */
	private static Table<Integer, Integer, TracedHyperSequence> computeHyperSequenceTable(ProgressListener progress, boolean useRefinement, boolean useMissingTaxa,
																						  BitSet allTaxa, int[] taxonRank, List<BitSet> treeTaxa, List<PhyloTree> trees) throws jloda.util.CanceledException {
		var hyperSequenceTable = new Table<Integer, Integer, TracedHyperSequence>();
		for (var treeId = 0; treeId < trees.size(); treeId++) {
			var tree = trees.get(treeId);
			var minTaxon = findMin(BitSetUtils.asBitSet(tree.getTaxa()), taxonRank);
			try (NodeArray<BitSet> nodeLabels = tree.newNodeArray(); NodeArray<BitSet> taxaBelow = tree.newNodeArray()) {
				tree.postorderTraversal(v -> {
					if (v.isLeaf()) {
						taxaBelow.computeIfAbsent(v, k -> new BitSet()).set(tree.getTaxon(v));
						nodeLabels.computeIfAbsent(v, k -> new BitSet()).set(tree.getTaxon(v));
					} else {
						Node smallestChild = null;
						var childSmallestLeafRank = Integer.MAX_VALUE;
						for (var w : v.children()) {
							var leafRank = taxonRank[findMin(taxaBelow.get(w), taxonRank)];
							if (leafRank < childSmallestLeafRank) {
								smallestChild = w;
								childSmallestLeafRank = leafRank;
							}
						}
						nodeLabels.put(v, new BitSet());
						for (var w : v.children()) {
							if (w != smallestChild)
								nodeLabels.get(v).set(findMin(taxaBelow.get(w), taxonRank));
						}
						taxaBelow.put(v, new BitSet());
						for (var w : v.children())
							taxaBelow.get(v).or(taxaBelow.get(w));
					}
				});
				nodeLabels.computeIfAbsent(tree.getRoot(), k -> new BitSet()).set(minTaxon);

				var taxonReverseSequenceMap = new HashMap<Integer, ArrayList<TracedHyperSequence.Element>>();
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
									hyperSequenceTable.put(t, finalTreeId, new TracedHyperSequence(sequence));
								}
								taxonReverseSequenceMap.remove(t);
							} else {
								if (taxonReverseSequenceMap.containsKey(t))
									taxonReverseSequenceMap.get(t).add(TracedHyperSequence.Element.fromTaxaAndTree(labels, finalTreeId));
							}
						}
					}
				});

				if (!taxonReverseSequenceMap.isEmpty())
					throw new RuntimeException("taxonReverseSequenceMap: " + taxonReverseSequenceMap.size());
			}
			progress.checkForCancel();
		}

		if (useRefinement)
			applyRefinementRule1(hyperSequenceTable, taxonRank);
		if (useMissingTaxa)
			applyMissingTaxaRule3(hyperSequenceTable, allTaxa, treeTaxa);
		return hyperSequenceTable;
	}

	/**
	 * build the network, reading each reticulate edge's tree ids off the source element's metadata
	 */
	private static PhyloTree computeNetwork(int[] taxonRank, Map<Integer, TracedHyperSequence> taxonHyperSequenceMap) {
		var ordering = inorder(taxonRank);
		var network = new PhyloTree();
		try (NodeArray<BitSet> label = network.newNodeArray()) {
			var taxonStartMap = new HashMap<Integer, Node>();
			var taxonChainMap = new HashMap<Integer, ArrayList<Node>>();
			var chainNodeToElement = new HashMap<Node, TracedHyperSequence.Element>();

			for (var t : ordering) {
				var start = network.newNode();
				label.put(start, BitSetUtils.asBitSet(t));
				if (network.getRoot() == null)
					network.setRoot(start);
				taxonStartMap.put(t, start);
				var prev = start;
				taxonChainMap.put(t, new ArrayList<>());
				var hyperSequence = taxonHyperSequenceMap.get(t);
				if (hyperSequence != null) {
					for (var component : hyperSequence.elements()) {
						var v = network.newNode();
						label.put(v, (BitSet) component.taxa().clone());
						chainNodeToElement.put(v, component);
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
					var sourceElement = chainNodeToElement.get(v);
					for (var q : BitSetUtils.members(label.get(v))) {
						var w = taxonStartMap.get(q);
						var e = network.newEdge(v, w);
						var edgeIds = new BitSet();
						if (sourceElement != null)
							edgeIds.or(sourceElement.getTreeIds(q));
						setEdgeTreeIds(e, edgeIds);
						mergeNodeTreeIds(w, edgeIds);
					}
				}
			}
		}

		for (var v : network.nodeStream().filter(v -> v.getInDegree() == 1 && v.getOutDegree() == 1).toList())
			network.delDivertex(v);
		network.edgeStream().forEach(e -> network.setReticulate(e, e.getTarget().getInDegree() > 1));
		return network;
	}

	/**
	 * map the local (active) tree ids stored on nodes and reticulate edges to the original input-tree ids
	 */
	private static void expandActiveToOriginalTreeIds(PhyloTree network, List<BitSet> representedTreeIds) {
		for (var v : network.nodes()) {
			var local = getNodeTreeIds(v);
			if (!local.isEmpty())
				setNodeTreeIds(v, expand(local, representedTreeIds));
		}
		for (var e : network.edges()) {
			var local = getEdgeTreeIds(e);
			if (!local.isEmpty())
				setEdgeTreeIds(e, expand(local, representedTreeIds));
		}
	}

	private static BitSet expand(BitSet localIds, List<BitSet> representedTreeIds) {
		var expanded = new BitSet();
		for (var i = localIds.nextSetBit(0); i >= 0; i = localIds.nextSetBit(i + 1)) {
			if (i < representedTreeIds.size())
				expanded.or(representedTreeIds.get(i));
		}
		return expanded;
	}

	/**
	 * refinement rule 1 (multifurcations): move a peeled-off element of a multifurcation to the sequence of a later taxon
	 */
	private static void applyRefinementRule1(Table<Integer, Integer, TracedHyperSequence> hyperSequenceTable, int[] taxonRank) {
		var ordering = inorder(taxonRank);
		var treeOrder = new TreeSet<>(hyperSequenceTable.columnKeySet());
		for (var i = 0; i < ordering.size() - 1; i++) {
			var taxonS = ordering.get(i);
			for (var tree1 : treeOrder) {
				var seq1s = hyperSequenceTable.get(taxonS, tree1);
				if (seq1s == null)
					continue;
				for (var elementE : seq1s.elements()) {
					if (elementE.taxa().cardinality() <= 1)
						continue;
					var taxonZ = getLargest(taxonRank, elementE.taxa());
					var remainingR = BitSetUtils.minus(elementE.taxa(), BitSetUtils.asBitSet(taxonZ));
					if (seq1s.elements().stream().filter(e -> e != elementE).noneMatch(e -> e.taxa().intersects(remainingR))) {
						loop:
						for (var j = i + 1; j < ordering.size(); j++) {
							var taxonT = ordering.get(j);
							for (var tree2 : treeOrder) {
								if (tree2.equals(tree1))
									continue;
								var seq2t = hyperSequenceTable.get(taxonT, tree2);
								if (seq2t == null)
									continue;
								if (BitSetUtils.contains(unionTaxa(seq2t.elements()), remainingR)) {
									var moved = elementE.inducedBy(remainingR);
									elementE.removeTaxa(remainingR);
									var seq1t = hyperSequenceTable.get(taxonT, tree1);
									if (seq1t != null && !moved.isEmpty()) {
										seq1t.elements().add(0, moved);
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

	/**
	 * missing-taxa rule 3: when a tree lacks a taxon that a witness taxon reaches, borrow the ordering from a donor tree
	 */
	private static void applyMissingTaxaRule3(Table<Integer, Integer, TracedHyperSequence> hyperSequenceTable, BitSet allTaxa, List<BitSet> treeTaxa) {
		var treeOrder = new TreeSet<>(hyperSequenceTable.columnKeySet());
		for (var taxA : BitSetUtils.members(allTaxa)) {
			var aSet = BitSetUtils.asBitSet(taxA);
			loop:
			for (var treeJ : treeOrder) {
				if (!treeTaxa.get(treeJ).get(taxA) || !hyperSequenceTable.contains(taxA, treeJ))
					continue;
				var seqSaj = hyperSequenceTable.get(taxA, treeJ);
				for (var treeP : treeOrder) {
					if (treeJ.equals(treeP) || treeTaxa.get(treeP).get(taxA))
						continue;
					for (var taxI : hyperSequenceTable.rowKeySet()) {
						if (!hyperSequenceTable.contains(taxI, treeJ) || !hyperSequenceTable.contains(taxI, treeP))
							continue;
						var seqSij = hyperSequenceTable.get(taxI, treeJ);
						var seqSip = hyperSequenceTable.get(taxI, treeP);
						for (var sourceAInSij : seqSij.elements()) {
							if (!sourceAInSij.taxa().equals(aSet))
								continue;
							if (BitSetUtils.contains(treeTaxa.get(treeP), aSet))
								continue;
							for (var pos = 0; pos < seqSip.elements().size(); pos++) {
								var bSet = seqSip.elements().get(pos).taxa();
								if (bSet.cardinality() < 1)
									continue;
								var matchingInSaj = firstElementWithSameTaxa(seqSaj, bSet);
								if (matchingInSaj == null)
									continue;
								seqSip.elements().set(pos, TracedHyperSequence.Element.fromTaxaAndTree(aSet, treeP));
								var newSap = new TracedHyperSequence();
								newSap.add(TracedHyperSequence.Element.fromTaxaAndTree(bSet, treeP));
								hyperSequenceTable.put(taxA, treeP, newSap);
								break loop;
							}
						}
					}
				}
			}
		}
	}

	/**
	 * drop from each element the taxa carried by the next, transferring their metadata forward first
	 */
	private static TracedHyperSequence simplifyTraceSequence(TracedHyperSequence hyperSequence) {
		var simplified = new TracedHyperSequence();
		if (hyperSequence == null || hyperSequence.isEmpty())
			return simplified;
		var working = hyperSequence.copy().elements();
		for (var i = 0; i < working.size() - 1; i++) {
			var current = working.get(i);
			var next = working.get(i + 1);
			transferOverlapTreeInfo(current, next);
			var keep = BitSetUtils.minus(current.taxa(), next.taxa());
			if (keep.cardinality() > 0)
				simplified.add(current.inducedBy(keep));
		}
		simplified.add(working.get(working.size() - 1).copy());
		simplified.removeEmptyElements();
		return simplified;
	}

	private static void transferOverlapTreeInfo(TracedHyperSequence.Element current, TracedHyperSequence.Element next) {
		var overlap = (BitSet) current.taxa().clone();
		overlap.and(next.taxa());
		for (int taxon = overlap.nextSetBit(0); taxon >= 0; taxon = overlap.nextSetBit(taxon + 1)) {
			var currentTrees = current.treeIdsPerTaxon().get(taxon);
			if (currentTrees != null)
				next.treeIdsPerTaxon().computeIfAbsent(taxon, k -> new BitSet()).or((BitSet) currentTrees.clone());
		}
	}

	private static TracedHyperSequence.Element firstElementWithSameTaxa(TracedHyperSequence sequence, BitSet taxa) {
		for (var element : sequence.elements())
			if (element.taxa().equals(taxa))
				return element;
		return null;
	}

	private static BitSet unionTaxa(Collection<TracedHyperSequence.Element> elements) {
		var result = new BitSet();
		for (var element : elements)
			result.or(element.taxa());
		return result;
	}

	private static int findMin(BitSet taxa, int[] taxonRank) {
		var result = -1;
		for (var t : BitSetUtils.members(taxa)) {
			if (result == -1 || taxonRank[t] < taxonRank[result])
				result = t;
		}
		return result;
	}

	private static ArrayList<Integer> inorder(int[] taxonRank) {
		var ordering = new int[taxonRank.length];
		for (var t = 1; t < taxonRank.length; t++)
			ordering[taxonRank[t]] = t;
		var list = new ArrayList<Integer>();
		for (var t = 1; t < ordering.length; t++)
			if (ordering[t] != 0)
				list.add(ordering[t]);
		return list;
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

	// tree ids ride along in the generic Node/Edge info slot, as a BitSet (matching TreeTracing):

	private static void setNodeTreeIds(Node v, BitSet ids) {
		v.setInfo((BitSet) ids.clone());
	}

	private static BitSet getNodeTreeIds(Node v) {
		return v.getInfo() instanceof BitSet ids ? (BitSet) ids.clone() : new BitSet();
	}

	private static void mergeNodeTreeIds(Node v, BitSet ids) {
		var current = getNodeTreeIds(v);
		current.or(ids);
		setNodeTreeIds(v, current);
	}

	private static void setEdgeTreeIds(Edge e, BitSet ids) {
		e.setInfo((BitSet) ids.clone());
	}

	private static BitSet getEdgeTreeIds(Edge e) {
		return e.getInfo() instanceof BitSet ids ? (BitSet) ids.clone() : new BitSet();
	}
}

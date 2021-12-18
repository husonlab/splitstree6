/*
 *  CircularOrdering.java Copyright (C) 2021 Daniel H. Huson
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

package splitstree6.view.trees.ordering;

import jloda.graph.Node;
import jloda.graph.NodeArray;
import jloda.phylo.LSAUtils;
import jloda.phylo.PhyloTree;
import jloda.util.BitSetUtils;
import jloda.util.IteratorUtils;
import jloda.util.Pair;
import jloda.util.Single;
import splitstree6.algorithms.distances.distances2splits.neighbornet.NeighborNetCycle;
import splitstree6.data.TaxaBlock;

import java.util.*;

/**
 * provides methods for computing the ordering used to layout rooted trees or networks
 * Daniel Huson, 12.2021
 */
public class CircularOrdering {
	/**
	 * compute ordering for a list of trees or rooted networks
	 *
	 * @param taxaBlock
	 * @param trees
	 * @return circular orderings for both trees
	 */
	public static int[] apply(TaxaBlock taxaBlock, PhyloTree tree, PhyloTree... trees) {
		if (!tree.isReticulated() && trees.length == 0) {
			var taxCycle = new int[taxaBlock.getNtax() + 1];
			var counter = new Single<>(0);
			tree.postorderTraversal(tree.getRoot(), v -> {
				if (v.isLeaf()) {
					for (var t : tree.getTaxa(v)) {
						taxCycle[counter.set(counter.get() + 1)] = t;
					}
				}
			});
			return taxCycle;
		} else { // at least two trees, or at least one rooted network
			// trees might be partial so need to map to indices and back
			var tax2index = new HashMap<Integer, Integer>();
			var index2tax = new HashMap<Integer, Integer>();
			var allTaxa = BitSetUtils.asBitSet(tree.getTaxa());
			Arrays.stream(trees).map(t -> BitSetUtils.asBitSet(t.getTaxa())).forEach(allTaxa::or);

			var ntax = 0;
			for (var t : BitSetUtils.members(allTaxa)) {
				tax2index.put(t, ntax);
				index2tax.put(ntax, t);
				ntax++;
			}

			// we need a formal outgroup taxon:
			var formalOutgroupTaxon = allTaxa.stream().max().orElse(0) + 1;
			tax2index.put(formalOutgroupTaxon, ntax);
			index2tax.put(ntax, formalOutgroupTaxon);
			ntax++;

			final var distances = new double[ntax][ntax];

			for (var phyloTree : IteratorUtils.asList(List.of(tree), List.of(trees))) {
				if (phyloTree.isReticulated() && phyloTree.getLSAChildrenMap().size() == 0)
					SetupLSAChildrenMap.apply(phyloTree);

				var taxa = BitSetUtils.asBitSet(phyloTree.getTaxa());
				taxa.set(formalOutgroupTaxon);
				for (var split : HardWired.computeSplits(taxa, phyloTree)) {
					for (var a : BitSetUtils.members(split.getA())) {
						for (var b : BitSetUtils.members(split.getB())) {
							var ia = tax2index.get(a);
							var ib = tax2index.get(b);
							distances[ia][ib] += 1;
							distances[ib][ia] += 1;
						}
					}
				}
			}

			final var indexCycle = NeighborNetCycle.computeNeighborNetCycle(ntax, distances);
			var taxCycle = new int[indexCycle.length];
			for (var i = 1; i < indexCycle.length; i++) {
				taxCycle[i] = index2tax.get(indexCycle[i] - 1);
			}

			{
				// rotate formal outgroup taxon to end and remove it
				var rotatedTaxCycle = new int[taxCycle.length - 1]; // shorter because we will drop the formal outgroup
				var outgroupPos = 0;
				for (int pos = 0; pos < taxCycle.length; pos++) {
					if (taxCycle[pos] == formalOutgroupTaxon) {
						outgroupPos = pos;
						break;
					}
				}
				var pos = (outgroupPos + 1 < taxCycle.length ? outgroupPos + 1 : 1);
				for (int i = 1; i < rotatedTaxCycle.length; i++) {
					rotatedTaxCycle[i] = taxCycle[pos];
					pos = (pos + 1 < taxCycle.length ? pos + 1 : 1);
				}
				taxCycle = rotatedTaxCycle;
			}
			return taxCycle;
		}
	}

	/**
	 * determine a realizable circular ordering that is similar to the target ordering
	 *
	 * @param tree
	 * @param targetCycle
	 * @return target cycle that will produce a crossing-free embedding
	 */
	public static int[] computeRealizableCycle(PhyloTree tree, int[] targetCycle) {
		var taxa = BitSetUtils.asBitSet(tree.getTaxa());

		var taxPosMap = new HashMap<Integer, Integer>();
		for (var pos = 1; pos < targetCycle.length; pos++) {
			var t = targetCycle[pos];
			if (taxa.get(t)) {
				taxPosMap.put(t, pos);
			}
		}

		// map each node to the taxa positions that lie below it:

		try (NodeArray<BitSet> positionsBelow = tree.newNodeArray()) {
			LSAUtils.postorderTraversalLSA(tree, tree.getRoot(), v -> {
				if (v.isLeaf()) {
					positionsBelow.put(v, BitSetUtils.asBitSet(IteratorUtils.asStream(tree.getTaxa(v)).mapToInt(taxPosMap::get).toArray()));
				} else {
					var below = new BitSet();
					IteratorUtils.asStream(tree.lsaChildren(v)).map(positionsBelow::get).forEach(below::or);
					positionsBelow.put(v, below);
				}
			});

			var cycle = new int[targetCycle.length];
			computeBestOrderingRec(tree, tree.getRoot(), positionsBelow, new Single<>(0), cycle);
			return cycle;
		}
	}

	/**
	 * recursively determines a realizable ordering.
	 * During a traversal of the LSA tree, we visit the children of each node in the order of the average position below each child,
	 * and record cycle in the order that we thus visit the leaves
	 */
	private static void computeBestOrderingRec(PhyloTree tree, Node v, NodeArray<BitSet> positionsBelow, Single<Integer> pos, int[] cycle) {
		if (v.isLeaf()) {
			for (var t : tree.getTaxa(v)) {
				cycle[pos.set(pos.get() + 1)] = t;
			}
		} else {
			var list = new ArrayList<Pair<Double, Node>>();
			for (var w : tree.lsaChildren(v)) {
				var average = (double) BitSetUtils.asStream(positionsBelow.get(w)).mapToInt(t -> t).sum() / (double) positionsBelow.get(w).cardinality();
				list.add(new Pair<>(average, w));
				list.sort(Comparator.comparingDouble(Pair::getFirst));
			}
			for (var pair : list) {
				computeBestOrderingRec(tree, pair.getSecond(), positionsBelow, pos, cycle);
			}
		}
	}
}

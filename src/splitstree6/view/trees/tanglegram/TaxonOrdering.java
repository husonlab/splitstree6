/*
 *  Copyright (C) 2018. Daniel H. Huson
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

/*
 *  TaxonOrdering.java Copyright (C) 2021 Daniel H. Huson
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

package splitstree6.view.trees.tanglegram;

import jloda.graph.Node;
import jloda.graph.NodeArray;
import jloda.phylo.PhyloTree;
import jloda.util.BitSetUtils;
import jloda.util.Counter;
import jloda.util.IteratorUtils;
import jloda.util.Pair;
import splitstree6.algorithms.distances.distances2splits.neighbornet.NeighborNetCycle;
import splitstree6.algorithms.utils.TreesUtilities;
import splitstree6.data.TaxaBlock;
import splitstree6.data.parts.ASplit;

import java.util.*;

/**
 * computes the taxon ordering to be used in the tanglegram
 * Daniel Huson, 12.2021
 */
@Deprecated
public class TaxonOrdering {
	/**
	 * apply the computation
	 *
	 * @param tree1 first tree
	 * @param tree2 second tree
	 * @return cyclic ordering to use for first and second tree
	 */
	public static Pair<int[], int[]> apply(TaxaBlock taxaBlock, PhyloTree tree1, PhyloTree tree2) {
		if (tree1 != null && tree1.getNumberOfNodes() > 0 && tree2 != null && tree2.getNumberOfNodes() > 0) {
			if (tree1 == tree2) { // the same tree object, need to handle this case separately
				var nTaxa = TreesUtilities.getTaxa(tree1).cardinality();
				var cycle = new int[nTaxa + 1];
				var pos = new Counter();
				tree1.preorderTraversal(v -> {
					if (v.isLeaf()) {
						IteratorUtils.asStream(tree1.getTaxa(v)).forEach(t -> cycle[(int) pos.incrementAndGet()] = t);
					}
				});
				return new Pair<>(cycle, cycle);
			}

			var taxa1 = TreesUtilities.getTaxa(tree1);
			var taxa2 = TreesUtilities.getTaxa(tree2);

			var taxa = BitSetUtils.union(taxa1, taxa2);
			var formalOutgroupTaxon = taxa.nextClearBit(1);
			taxa1.set(formalOutgroupTaxon);
			taxa2.set(formalOutgroupTaxon);
			taxa.set(formalOutgroupTaxon);

			var outgroupNode1 = tree1.newNode();
			var outgroupNode2 = tree2.newNode();

			try {
				{
					tree1.setLabel(outgroupNode1, "FormalOutGroup");
					tree1.addTaxon(outgroupNode1, formalOutgroupTaxon);
					tree1.newEdge(tree1.getRoot(), outgroupNode1);
				}
				{
					tree2.setLabel(outgroupNode2, "FormalOutGroup");
					tree2.addTaxon(outgroupNode2, formalOutgroupTaxon);
					tree2.newEdge(tree2.getRoot(), outgroupNode2);
				}

				// trees might be partial so need to map to indices and back
				var tax2index = new HashMap<Integer, Integer>();
				var index2tax = new HashMap<Integer, Integer>();
				var n = 0;
				{
					for (var t : BitSetUtils.members(taxa)) {
						tax2index.put(t, n);
						index2tax.put(n, t);
						n++;
					}
				}

				final var distances = new double[n][n];

				for (var pair : List.of(new Pair<>(taxa1, tree1), new Pair<>(taxa2, tree2))) {
					final var splits = new ArrayList<ASplit>();
					TreesUtilities.computeSplits(pair.getFirst(), pair.getSecond(), splits);
					for (var split : splits) {
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

				final var indexCycle = NeighborNetCycle.computeNeighborNetCycle(n, distances);
				var taxCycle = new int[Objects.requireNonNull(indexCycle).length];
				for (var i = 1; i < indexCycle.length; i++) {
					taxCycle[i] = index2tax.get(indexCycle[i] - 1);
				}

				var result = new Pair<int[], int[]>();

				{
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

					tree1.deleteNode(outgroupNode1);
					tree1.deleteNode(outgroupNode2);


					var tax2pos = new int[taxCycle.length];
					for (var pos = 1; pos < taxCycle.length; pos++)
						tax2pos[taxCycle[pos]] = pos;
					// map each node to the taxa positions
					try (NodeArray<BitSet> positions1Below = tree1.newNodeArray()) {
						tree1.postorderTraversal(v -> {
							if (v.isLeaf()) {
								positions1Below.put(v, BitSetUtils.asBitSet(IteratorUtils.asStream(tree1.getTaxa(v)).mapToInt(t -> tax2pos[t]).toArray()));
							} else {
								var below = new BitSet();
								v.childrenStream().map(positions1Below::get).forEach(below::or);
								positions1Below.put(v, below);
							}
						});

						var cycle1 = new int[taxCycle.length];
						computeBestOrderingRec(tree1, tree1.getRoot(), positions1Below, new Counter(0), cycle1);
						result.setFirst(cycle1);
					}
				}
				{
					var tax2pos = new int[taxCycle.length];
					for (var pos = 1; pos < taxCycle.length; pos++)
						tax2pos[taxCycle[pos]] = pos;
					// map each node to the taxa positions
					try (NodeArray<BitSet> positions2Below = tree2.newNodeArray()) {
						tree2.postorderTraversal(v -> {
							if (v.isLeaf()) {
								positions2Below.put(v, BitSetUtils.asBitSet(IteratorUtils.asStream(tree2.getTaxa(v)).mapToInt(t -> tax2pos[t]).toArray()));
							} else {
								var below = new BitSet();
								v.childrenStream().map(positions2Below::get).forEach(below::or);
								positions2Below.put(v, below);
							}
						});

						var cycle2 = new int[taxCycle.length];
						computeBestOrderingRec(tree2, tree2.getRoot(), positions2Below, new Counter(0), cycle2);
						result.setSecond(cycle2);
					}

					return result;
				}
			} finally {
				if (outgroupNode1.getOwner() != null) // in case we didn't remove the node above
					tree1.deleteNode(outgroupNode1);
				if (outgroupNode2.getOwner() != null) // in case we didn't remove the node above
					tree2.deleteNode(outgroupNode2);
			}
		}
		return new Pair<>(new int[0], new int[0]);
	}


	private static void computeBestOrderingRec(PhyloTree tree, Node v, NodeArray<BitSet> taxaBelow, Counter pos, int[] cycle) {
		if (v.isLeaf()) {
			for (var t : tree.getTaxa(v)) {
				cycle[(int) pos.incrementAndGet()] = t;
			}
		} else {
			var list = new ArrayList<Pair<Double, Node>>();
			for (var w : v.children()) {
				var average = (double) BitSetUtils.asStream(taxaBelow.get(w)).mapToInt(t -> t).sum() / (double) taxaBelow.get(w).cardinality();
				list.add(new Pair<>(average, w));
				list.sort(Comparator.comparingDouble(Pair::getFirst));
			}
			for (var pair : list) {
				computeBestOrderingRec(tree, pair.getSecond(), taxaBelow, pos, cycle);
			}
		}
	}
}

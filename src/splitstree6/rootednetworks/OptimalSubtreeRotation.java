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
 *  OptimalSubtreeRotation.java Copyright (C) 2021 Daniel H. Huson
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

package splitstree6.rootednetworks;

import jloda.graph.Edge;
import jloda.graph.Node;
import jloda.graph.NodeArray;
import jloda.phylo.PhyloTree;
import jloda.util.IteratorUtils;
import splitstree6.data.TaxaBlock;
import splitstree6.view.trees.ordering.SetupLSAChildrenMap;

import java.util.BitSet;
import java.util.HashMap;
import java.util.Map;

/**
 * attempts to optimize layout of rooted networks
 * Daniel Huson, 12.2021
 */
public class OptimalSubtreeRotation {
	/**
	 * apply to given trees
	 *
	 * @param taxaBlock
	 * @param trees
	 */
	public static void apply(TaxaBlock taxaBlock, PhyloTree... trees) {
		final var taxon2Id = new HashMap<String, Integer>();

		final var rhoId = taxaBlock.getNtax() + 1;
		for (int tax = 1; tax <= taxaBlock.getNtax(); tax++) {
			taxon2Id.put(taxaBlock.get(tax).getName(), tax);
		}
		taxon2Id.put("rho****", rhoId);

		// add formal root nodes
		var root = new Node[trees.length];
		var newRoot = new Node[trees.length];
		var rho = new Node[trees.length];

		for (var t = 0; t < trees.length; t++) {
			var tree = trees[t];
			root[t] = tree.getRoot();
			rho[t] = tree.newNode();
			newRoot[t] = tree.newNode();
			tree.newEdge(newRoot[t], tree.getRoot());
			tree.newEdge(newRoot[t], rho[t]);
			tree.setRoot(newRoot[t]);
			tree.setLabel(rho[t], "rho****");
		}

		final int[] linearOrdering;
		try {
			var circularOrdering = HardwiredClusters.computeCircularOrdering(trees, taxon2Id);

			System.err.println("+++++++++ Circular ordering:");
			for (var t : circularOrdering) {
				if (t > 0 && t <= taxaBlock.getNtax())
					System.err.println(t + " " + taxaBlock.get(t).getName());
			}
			linearOrdering = getLinearOrderingId(circularOrdering, rhoId);

			System.err.println("+++++++++ Linear ordering:");
			for (var t : linearOrdering) {
				if (t > 0)
					System.err.println(t + " " + taxaBlock.get(t).getName());
			}
		} finally {
			for (var t = 0; t < trees.length; t++) {
				var tree = trees[t];
				tree.deleteNode(newRoot[t]);
				tree.deleteNode(rho[t]);
				tree.setRoot(root[t]);
			}
		}
		for (var tree : trees) {
			layoutNodesToMatchOrderHeuristic(tree, linearOrdering, taxon2Id);
		}

	}

	/**
	 * fast heuristic that tries to rotate tree/network so that it matches the given order
	 *
	 * @param tree
	 * @param linearOrdering
	 * @param taxon2Id
	 */
	private static void layoutNodesToMatchOrderHeuristic(PhyloTree tree, int[] linearOrdering, Map<String, Integer> taxon2Id) {
		try (var taxaBelow = new NodeArray<BitSet>(tree)) {
			for (Node v : tree.nodes()) {
				if (v.getOutDegree() == 0) {
					var id = taxon2Id.get(tree.getLabel(v));
					for (var z = 1; z <= linearOrdering.length; z++)
						if (linearOrdering[z] == id) {
							var below = new BitSet();
							below.set(z);
							taxaBelow.put(v, below);
							break;
						}
				}
			}
			computeTaxaBelowRec(tree.getRoot(), taxaBelow);
			rotateTreeByTaxaBelow(tree, taxaBelow);
		}
		SetupLSAChildrenMap.apply(tree);
	}

	/**
	 * gets the linear ordering starting at id idRho and excluding idRho
	 *
	 * @param circularOrdering
	 * @param idRho
	 * @return linear ordering
	 */
	private static int[] getLinearOrderingId(int[] circularOrdering, int idRho) {
		var start = 0;
		for (var src = 1; src < circularOrdering.length; src++) {
			if (circularOrdering[src] == idRho)
				start = src;
		}

		var ordering = new int[circularOrdering.length - 1];
		var tar = 1;
		for (var src = start + 1; src < circularOrdering.length; src++) {
			ordering[tar++] = circularOrdering[src];
		}
		for (var src = 1; src < start; src++) {
			ordering[tar++] = circularOrdering[src];
		}
		return ordering;
	}

	/**
	 * recursively extends the taxa below map from leaves to all nodes
	 *
	 * @param v
	 * @param taxaBelow
	 */
	public static void computeTaxaBelowRec(Node v, NodeArray<BitSet> taxaBelow) {
		if (v.getOutDegree() > 0 && taxaBelow.get(v) == null) {
			var below = new BitSet();

			for (Edge e : v.outEdges()) {
				var w = e.getTarget();
				computeTaxaBelowRec(w, taxaBelow);
				below.or(taxaBelow.get(w));
			}
			taxaBelow.put(v, below);
		}
	}

	/**
	 * rotates all out edges to sort by the taxa-below sets
	 *
	 * @param tree
	 * @param taxaBelow
	 */
	public static void rotateTreeByTaxaBelow(PhyloTree tree, final NodeArray<BitSet> taxaBelow) {
		for (var v0 : tree.nodes()) {
			if (v0.getOutDegree() > 0) {
				final var sourceNode = v0;

                /*
                System.err.println("Source node: " +sourceNode+" "+tree.getLabel(v0));

                System.err.println("original order:");
                for (Edge e = v0.getFirstOutEdge(); e != null; e = v0.getNextOutEdge(e)) {
                    Node w=e.getOpposite(v0) ;
                    System.err.println(w +" "+tree.getLabel(w)+ " value: " + (Basic.toString(taxaBelow.get(w))));
                }
                */

				var adjacentEdges = IteratorUtils.asList(v0.adjacentEdges());
				adjacentEdges.sort((e, f) -> {
					if (e.getSource() == sourceNode && f.getSource() == sourceNode) // two out edges
					{
						var v = e.getTarget();
						var w = f.getTarget();

						// lexicographically smaller is smaller
						var taxaBelowV = taxaBelow.get(v);
						var taxaBelowW = taxaBelow.get(w);

						var i = taxaBelowV.nextSetBit(0);
						var j = taxaBelowW.nextSetBit(0);
						while (i != -1 && j != -1) {
							if (i < j)
								return -1;
							else if (i > j)
								return 1;
							i = taxaBelowV.nextSetBit(i + 1);
							j = taxaBelowW.nextSetBit(j + 1);
						}
						if (i == -1 && j != -1)
							return -1;
						else if (i != -1 && j == -1)
							return 1;

					} else if (e.getTarget() == sourceNode && f.getSource() == sourceNode)
						return -1;
					else if (e.getSource() == sourceNode && f.getTarget() == sourceNode)
						return 1;
					// no else here!
					if (e.getId() < f.getId())
						return -1;
					else if (e.getId() > f.getId())
						return 1;
					else
						return 0;
				});
				v0.rearrangeAdjacentEdges(adjacentEdges);
			}
		}
	}

}

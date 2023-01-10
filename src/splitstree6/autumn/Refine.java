/*
 * Refine.java Copyright (C) 2023 Daniel H. Huson
 *
 * (Some files contain contributions from other authors, who are then mentioned separately.)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package splitstree6.autumn;


import jloda.graph.Node;
import jloda.phylo.PhyloTree;
import jloda.util.Pair;
import jloda.util.StringUtils;
import splitstree6.data.TaxaBlock;

import java.io.IOException;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashSet;
import java.util.Set;

/**
 * refine two trees
 * Daniel Huson, 4.2011
 */
public class Refine {
	/**
	 * refine two trees
	 *
	 * @return refined trees
	 */
	public static PhyloTree[] apply(PhyloTree tree1, PhyloTree tree2) throws IOException {
		// create rooted trees with nodes labeled by taxa ids
		var allTaxa = new TaxaBlock();
		var roots = PreProcess.apply(tree1, tree2, allTaxa);
		var v1 = roots.getFirst();
		var v2 = roots.getSecond();

		apply(v1, v2);

		v1.reorderSubTree();
		v2.reorderSubTree();

		// convert data-structures to final trees
		var result = PostProcess.apply(new Root[]{v1, v2}, allTaxa, false);
		return result.toArray(new PhyloTree[0]);

	}

	/**
	 * recursively does the work
	 *
	 */
	public static void apply(Root root1, Root root2) {
        applyRec(root1, root2, new HashSet<>());
		if (!root1.getTaxa().equals(root2.getTaxa()))
			throw new RuntimeException("Unequal taxon sets: " + StringUtils.toString(root1.getTaxa()) + " vs " + StringUtils.toString(root2.getTaxa()));
	}

	/**
	 * refines two rooted trees with respect to each other
	 *
	 */
	private static void applyRec(Root v1, Root v2, Set<Pair<Root, Root>> compared) {
		var pair = new Pair<>(v1, v2);

		if (compared.contains(pair))
			return;
		else
			compared.add(pair);

		var X = v1.getTaxa();
		var Y = v2.getTaxa();

		if (X.cardinality() == 1 || Y.cardinality() == 1 || !X.intersects(Y))
			return; // doesn't update

		// System.err.println("Refining with v1=" + Basic.toString(X) + "  v2=" + Basic.toString(Y));

		if (Cluster.contains(X, Y) && !X.equals(Y))  // X contains Y
		{
			// System.err.println("X contains Y");

			var taxa1 = new BitSet();
			var removedTaxa1 = new BitSet();
			var toPushDown = new ArrayList<Root>();
			int count = 0;
			for (var e1 : v1.outEdges()) {
				var w1 = (Root) e1.getTarget();
				if (Y.intersects(w1.getTaxa())) {
					taxa1.or(w1.getTaxa());
					removedTaxa1.or(w1.getRemovedTaxa());
					toPushDown.add(w1);
					count++;
				}
			}
			if (count > 1 && taxa1.equals(Y)) {
				var needsReordering1 = new HashSet<Root>();
				// push down nodes
				var u = v1.newNode();
				u.setTaxa(taxa1);
				u.setRemovedTaxa(removedTaxa1);
				var f = v1.newEdge(v1, u);
				f.setInfo(1);
				needsReordering1.add(v1);
				needsReordering1.add(u);
				for (var w : toPushDown) {
					needsReordering1.add((Root) w.getFirstInEdge().getSource());
					w.deleteEdge(w.getFirstInEdge());
					f = v1.newEdge(u, w);
					f.setInfo(1);
				}
				//   System.err.println("Refined " + Basic.toString(Y));
				v1 = u;
				for (var v : needsReordering1) {
					v.reorderChildren();
				}
			}
		}
		if (Cluster.contains(Y, X) && !X.equals(Y))  // Y contains X
		{
			//   System.err.println("Y contains X");

			var taxa2 = new BitSet();
			var removedTaxa2 = new BitSet();
			var toPushDown = new ArrayList<Node>();
			var count = 0;
			for (var e2 : v2.outEdges()) {
				var w2 = (Root) e2.getTarget();
				if (X.intersects(w2.getTaxa())) {
					taxa2.or(w2.getTaxa());
					removedTaxa2.or(w2.getRemovedTaxa());
					toPushDown.add(w2);
					count++;
				}
			}
			if (count > 1 && taxa2.equals(X)) {
				var needsReordering2 = new HashSet<Root>();
				// push down nodes
				var u = v2.newNode();
				u.setTaxa(taxa2);
				u.setRemovedTaxa(removedTaxa2);
				var f = v2.newEdge(v2, u);
				f.setInfo(2);
				needsReordering2.add(v2);
				needsReordering2.add(u);
				for (var w : toPushDown) {
					needsReordering2.add((Root) w.getFirstInEdge().getSource());
					v2.deleteEdge(w.getFirstInEdge());
					f = v2.newEdge(u, w);
					f.setInfo(2);
				}
				//    System.err.println("Refined " + Basic.toString(X));
				v2 = u;
				for (var v : needsReordering2) {
					v.reorderChildren();
				}
			}
		}

		for (var e1 : v1.outEdges()) {
			var w1 = (Root) e1.getTarget();
			if (w1.getTaxa().intersects(Y)) {
				applyRec(w1, v2, compared);
			}
		}
		for (var e2 : v2.outEdges()) {
			var w2 = (Root) e2.getTarget();
			if (w2.getTaxa().intersects(X)) {
				applyRec(v1, w2, compared);
			}
		}
	}
}

/*
 *  TreeTracing.java Copyright (C) 2024 Daniel H. Huson
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
import jloda.phylo.CommentData;
import jloda.phylo.NewickIO;
import jloda.phylo.PhyloTree;

import java.io.IOException;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Map;

/**
 * tree tracing for PhyloFusion networks: records, for every reticulate edge, the set of original input trees that
 * route a lineage through it, so that each input tree can be recovered as it is embedded in the network (used by
 * PhyloParallelograms and for edge-weight fitting).
 * <p>
 * The reticulate-edge ids are set while the network is built (see {@link PhyloFusionAlgorithm#computeNetwork}); this
 * class then fills in all node ids by upward closure and writes the ids out as {@code TT} Newick comments.
 * <p>
 * Tree tracing is the contribution of Banu Cetinkaya; adapted into the main PhyloFusion by D. Huson, 7.2026
 */
public class TreeTracing {
	private TreeTracing() {
	}

	// tree ids ride along in the generic Node/Edge info slot, as a BitSet of zero-based input-tree ids:
	public static BitSet getTreeIds(Node v) {
		return v.getInfo() instanceof BitSet ids ? (BitSet) ids.clone() : new BitSet();
	}

	public static void setTreeIds(Node v, BitSet ids) {
		v.setInfo(ids.isEmpty() ? null : (BitSet) ids.clone());
	}

	public static BitSet getTreeIds(Edge e) {
		return e.getInfo() instanceof BitSet ids ? (BitSet) ids.clone() : new BitSet();
	}

	public static void setTreeIds(Edge e, BitSet ids) {
		e.setInfo(ids.isEmpty() ? null : (BitSet) ids.clone());
	}

	/**
	 * complete the traces on a finished network (Banu Cetinkaya): leaves take the exact ids of the input trees that
	 * contain their taxon; each internal node takes the union over its out-branches, where a reticulation branch
	 * contributes only its edge's ids but a tree branch contributes its whole subtree; anything left empty falls back.
	 *
	 * @param taxonToTreeIds taxon to ids of the input trees containing it
	 * @param fallbackIds    ids to use for any node/reticulate-edge that is otherwise still empty
	 */
	public static void complete(PhyloTree network, Map<Integer, BitSet> taxonToTreeIds, BitSet fallbackIds) {
		for (var v : network.nodes()) { // leaves: exact ids from the input trees
			if (v.isLeaf() && network.hasTaxa(v)) {
				var ids = taxonToTreeIds.get(network.getTaxon(v));
				if (ids != null && !ids.isEmpty())
					setTreeIds(v, ids);
			}
		}

		var postorder = new ArrayList<Node>(); // internal nodes: union over out-branches, children before parents
		network.postorderTraversal(postorder::add);
		for (var v : postorder) {
			if (!v.isLeaf()) {
				var ids = getTreeIds(v); // keep any ids already present
				for (var e : v.outEdges()) {
					var child = e.getTarget();
					ids.or(child.getInDegree() > 1 ? getTreeIds(e) : getTreeIds(child));
				}
				setTreeIds(v, ids);
			}
		}

		for (var e : network.edges()) { // reticulate edge still without ids: ids common to its two ends
			if (e.getTarget().getInDegree() > 1 && getTreeIds(e).isEmpty()) {
				var ids = getTreeIds(e.getSource());
				ids.and(getTreeIds(e.getTarget()));
				setTreeIds(e, ids);
			}
		}

		for (var v : network.nodes()) // fallback for anything still empty
			if (getTreeIds(v).isEmpty())
				setTreeIds(v, fallbackIds);
		for (var e : network.edges())
			if (e.getTarget().getInDegree() > 1 && getTreeIds(e).isEmpty())
				setTreeIds(e, fallbackIds);
	}

	/**
	 * extended Newick string in which every node, and every reticulate edge, carries a {@code TT} comment listing the
	 * one-based ids of the input trees that use it (Banu Cetinkaya)
	 */
	public static String toExtendedNewick(PhyloTree network) throws IOException {
		for (var v : network.nodes()) {
			var ids = getTreeIds(v);
			v.setData(ids.isEmpty() ? null : new CommentData().put("TT", toOneBased(ids)));
		}
		for (var e : network.edges()) {
			var ids = e.getTarget().getInDegree() > 1 ? getTreeIds(e) : new BitSet(); // only reticulate edges carry TT
			e.setData(ids.isEmpty() ? null : new CommentData().put("TT", toOneBased(ids)));
		}

		var newickIO = new NewickIO();
		newickIO.allowMultiLabeledNodes = false;
		newickIO.setNewickNodeCommentSupplier(CommentData.createDataNodeSupplier());
		newickIO.setNewickEdgeCommentSupplier(CommentData.createDataEdgeSupplier());
		return newickIO.toBracketString(network, true) + ";";
	}

	/**
	 * discard all trace information (raw ids and comments) from a network
	 */
	public static void clear(PhyloTree network) {
		for (var v : network.nodes()) {
			v.setInfo(null);
			v.setData(null);
		}
		for (var e : network.edges()) {
			e.setInfo(null);
			e.setData(null);
		}
	}

	private static BitSet toOneBased(BitSet zeroBased) {
		var result = new BitSet();
		for (var i = zeroBased.nextSetBit(0); i >= 0; i = zeroBased.nextSetBit(i + 1))
			result.set(i + 1);
		return result;
	}
}

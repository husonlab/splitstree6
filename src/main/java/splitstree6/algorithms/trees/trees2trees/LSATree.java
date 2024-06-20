/*
 *  TreeSelector.java Copyright (C) 2024 Daniel H. Huson
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

import jloda.graph.*;
import jloda.phylo.PhyloTree;
import jloda.util.progress.ProgressListener;
import splitstree6.algorithms.IFilter;
import splitstree6.data.TaxaBlock;
import splitstree6.data.TreesBlock;

import java.util.*;

/**
 * LSA trees
 * Daniel Huson, 4/2024
 */
public class LSATree extends Trees2Trees implements IFilter {
	@Override
	public String getShortDescription() {
		return "Extract the LSA tree from a rooted network.";
	}

	@Override
	public String getCitation() {
		return "Huson et al 2012;DH Huson, R. Rupp and C. Scornavacca, Phylogenetic Networks, Cambridge, 2012.";
	}

	@Override
	public void compute(ProgressListener progress, TaxaBlock ignored, TreesBlock inputData, TreesBlock outputData) {
		outputData.clear();
		outputData.setPartial(inputData.isPartial());
		outputData.setRooted(true);
		outputData.setReticulated(false);
		for (var network : inputData.getTrees()) {
			if (network.isReticulated()) {
				var tree = new PhyloTree();
				try (NodeArray<Node> srcTarMap = network.newNodeArray()) {
					computeLSA(network, tree, srcTarMap);
				}
				outputData.getTrees().add(tree);
			} else
				outputData.getTrees().add(network);
		}
	}

	@Override
	public boolean isApplicable(TaxaBlock taxa, TreesBlock datablock) {
		return datablock.isReticulated() && datablock.isRooted();
	}

	@Override
	public boolean isActive() {
		return true;
	}

	/**
	 * given a reticulate network, returns the LSA tree
	 *
	 * @return LSA tree
	 */
	public static void computeLSA(PhyloTree network, PhyloTree tree, NodeArray<Node> srcTarNodeMap) {
		tree.copy(network, srcTarNodeMap, null);
		tree.setRoot(srcTarNodeMap.get(network.getRoot()));

		for (var v : network.nodes()) {
			if (network.getLabel(v) != null)
				tree.setLabel(srcTarNodeMap.get(v), network.getLabel(v));
		}

		if (tree.getRoot() != null) {
			// first we compute the reticulate node to lsa node mapping:
			try (
					NodeArray<Node> reticulation2LSA = new NodeArray<>(tree);
					NodeArray<Set<Node>> node2below = tree.newNodeArray()) {
				computeReticulation2LSA(tree, reticulation2LSA, node2below);

				var reticulation2LSAEdgeLength = computeReticulation2LSAEdgeLength(tree, reticulation2LSA, node2below);

				// check that all reticulation nodes have a LSA:
				for (Node v = tree.getFirstNode(); v != null; v = v.getNext()) {
					if (v.getInDegree() >= 2) {
						Node lsa = reticulation2LSA.get(v);
						if (lsa == null)
							System.err.println("WARNING: no LSA found for node: " + v);
					}
				}

				var toDelete = new ArrayList<Edge>();
				for (Node v = tree.getFirstNode(); v != null; v = v.getNext()) {
					Node lsa = reticulation2LSA.get(v);

					if (lsa != null) {
						for (Edge e = v.getFirstInEdge(); e != null; e = v.getNextInEdge(e))
							toDelete.add(e);
						Edge e = tree.newEdge(lsa, v);
						tree.setWeight(e, reticulation2LSAEdgeLength.getDouble(v));
						// System.err.println("WEIGHT: " + (float) reticulation2LSAEdgeLength.get(v));
						// tree.setLabel(v,tree.getLabel(v)!=null?tree.getLabel(v)+"/"+(float)tree.getWeight(e):""+(float)tree.getWeight(e));
					}
				}
				for (Edge e : toDelete)
					tree.deleteEdge(e);

				boolean changed = true;
				while (changed) {
					changed = false;
					List<Node> falseLeaves = new LinkedList<>();
					for (Node v = tree.getFirstNode(); v != null; v = v.getNext()) {
						if (v.getInDegree() == 1 && v.getOutDegree() == 0 && (tree.getLabel(v) == null || tree.getLabel(v).isEmpty()))
							falseLeaves.add(v);
					}
					if (!falseLeaves.isEmpty()) {
						for (Node u : falseLeaves)
							tree.deleteNode(u);
						changed = true;
					}

					List<Node> divertices = new LinkedList<>();
					for (Node v = tree.getFirstNode(); v != null; v = v.getNext()) {
						if (v.getInDegree() == 1 && v.getOutDegree() == 1 && v != tree.getRoot() && (tree.getLabel(v) == null || tree.getLabel(v).isEmpty()))
							divertices.add(v);
					}
					if (!divertices.isEmpty()) {
						for (Node u : divertices)
							tree.delDivertex(u);
						changed = true;
					}
				}
			}
		}

		// make sure special attribute is set correctly:
		for (Edge e = tree.getFirstEdge(); e != null; e = e.getNext()) {
			boolean shouldBe = e.getTarget().getInDegree() > 1;
			if (shouldBe != tree.isReticulateEdge(e)) {
				System.err.println("WARNING: bad special state, fixing (to: " + shouldBe + ") for e=" + e);
				tree.setReticulate(e, shouldBe);
			}
		}
		// making sure leaves have labels:

		for (Node v = tree.getFirstNode(); v != null; v = v.getNext()) {
			if (v.getOutDegree() == 0 && (tree.getLabel(v) == null || tree.getLabel(v).trim().isEmpty())) {
				System.err.println("WARNING: adding label to naked leaf: " + v);
				tree.setLabel(v, "V" + v.getId());
			}
		}
	}


	/**
	 * compute the reticulate node to lsa node mapping
	 */
	private static void computeReticulation2LSA(PhyloTree network, NodeArray<Node> reticulation2LSA, NodeArray<Set<Node>> node2below) {
		reticulation2LSA.clear();
		try (NodeArray<BitSet> ret2PathSet = network.newNodeArray();
			 NodeArray<EdgeArray<BitSet>> ret2Edge2PathSet = network.newNodeArray();
		) {
			computeReticulation2LSARec(network, network.getRoot(), ret2PathSet, ret2Edge2PathSet, reticulation2LSA, node2below);
		}
	}

	/**
	 * recursively compute the mapping of reticulate nodes to their lsa nodes
	 */
	private static void computeReticulation2LSARec(PhyloTree tree, Node v, NodeArray<BitSet> ret2PathSet,
												   NodeArray<EdgeArray<BitSet>> ret2Edge2PathSet, NodeArray<Node> reticulation2LSA,
												   NodeArray<Set<Node>> node2below) {
		if (v.getInDegree() > 1) // this is a reticulate node, add paths to node and incoming edges
		{
			// setup new paths for this node:
			EdgeArray<BitSet> edge2PathSet = new EdgeArray<>(tree);
			ret2Edge2PathSet.put(v, edge2PathSet);
			BitSet pathsForR = new BitSet();
			ret2PathSet.put(v, pathsForR);
			//  assign a different path number to each in-edge:
			int pathNum = 0;
			for (Edge e = v.getFirstInEdge(); e != null; e = v.getNextInEdge(e)) {
				pathNum++;
				pathsForR.set(pathNum);
				BitSet pathsForEdge = new BitSet();
				pathsForEdge.set(pathNum);
				edge2PathSet.put(e, pathsForEdge);
			}
		}

		Set<Node> reticulationsBelow = new HashSet<>(); // set of all reticulate nodes below v
		node2below.put(v, reticulationsBelow);

		// visit all children and determine all reticulations below this node
		for (Edge f = v.getFirstOutEdge(); f != null; f = v.getNextOutEdge(f)) {
			Node w = f.getTarget();
			if (node2below.get(w) == null) // if haven't processed child yet, do it:
				computeReticulation2LSARec(tree, w, ret2PathSet, ret2Edge2PathSet, reticulation2LSA, node2below);
			reticulationsBelow.addAll(node2below.get(w));
			if (w.getInDegree() > 1)
				reticulationsBelow.add(w);
		}

		// check whether this is the lsa for any of the reticulations below v
		// look at all reticulations below v:
		List<Node> toDelete = new LinkedList<>();
		for (Node r : reticulationsBelow) {
			// determine which paths from the reticulation lead to this node
			var edge2PathSet = ret2Edge2PathSet.get(r);
			var paths = new BitSet();
			for (var f = v.getFirstOutEdge(); f != null; f = v.getNextOutEdge(f)) {
				BitSet eSet = edge2PathSet.get(f);
				if (eSet != null)
					paths.or(eSet);

			}
			BitSet alive = ret2PathSet.get(r);
			if (paths.equals(alive)) // if the set of paths equals all alive paths, v is lsa of r
			{
				reticulation2LSA.put(r, v);
				toDelete.add(r); // don't need to consider this reticulation any more
			}
		}
		// don't need to consider reticulations for which lsa has been found:
		for (Node u : toDelete)
			reticulationsBelow.remove(u);

		// all paths are pulled up the first in-edge"
		if (v.getInDegree() >= 1) {
			for (Node r : reticulationsBelow) {
				// determine which paths from the reticulation lead to this node
				EdgeArray<BitSet> edge2PathSet = ret2Edge2PathSet.get(r);

				BitSet newSet = new BitSet();

				for (Edge e = v.getFirstOutEdge(); e != null; e = v.getNextOutEdge(e)) {
					BitSet pathSet = edge2PathSet.get(e);
					if (pathSet != null)
						newSet.or(pathSet);
				}
				edge2PathSet.put(v.getFirstInEdge(), newSet);
			}
		}
		// open new paths on all additional in-edges:
		if (v.getInDegree() >= 2) {
			for (Node r : reticulationsBelow) {
				BitSet existingPathsForR = ret2PathSet.get(r);

				EdgeArray<BitSet> edge2PathSet = ret2Edge2PathSet.get(r);
				// start with the second in edge:
				for (Edge e = v.getNextInEdge(v.getFirstInEdge()); e != null; e = v.getNextInEdge(e)) {
					BitSet pathsForEdge = new BitSet();
					int pathNum = existingPathsForR.nextClearBit(1);
					existingPathsForR.set(pathNum);
					pathsForEdge.set(pathNum);
					edge2PathSet.put(e, pathsForEdge);
				}
			}
		}
	}

	/**
	 * computes the reticulation 2 lsa edge length map, after running the lsa computation
	 *
	 * @return mapping from reticulation nodes to the edge lengths
	 */
	private static NodeDoubleArray computeReticulation2LSAEdgeLength(PhyloTree tree, NodeArray<Node> reticulation2LSA, NodeArray<Set<Node>> node2below) {
		try (NodeArray<NodeDoubleArray> ret2Node2Length = tree.newNodeArray();
			 var ret2length = tree.newNodeDoubleArray()) {
			for (Node v = tree.getFirstNode(); v != null; v = v.getNext()) {
				if (v.getInDegree() > 1)
					ret2Node2Length.put(v, new NodeDoubleArray(tree));
				// if(v.getOutDegree()>0) tree.setLabel(v,""+v.getId());
			}

			try (var visited = tree.newNodeSet()) {
				computeReticulation2LSAEdgeLengthRec(tree, tree.getRoot(), visited, reticulation2LSA,
						node2below,
						ret2Node2Length, ret2length);
			}
			return ret2length;
		}
	}

	/**
	 * recursively does the work
	 */
	private static void computeReticulation2LSAEdgeLengthRec(PhyloTree tree, Node v, NodeSet visited,
															 NodeArray<Node> reticulation2LSA,
															 NodeArray<Set<Node>> node2below,
															 NodeArray<NodeDoubleArray> ret2Node2Length, NodeDoubleArray ret2length) {
		if (!visited.contains(v)) {
			visited.add(v);

			Set<Node> reticulationsBelow = new HashSet<>();

			for (Edge f = v.getFirstOutEdge(); f != null; f = v.getNextOutEdge(f)) {
				computeReticulation2LSAEdgeLengthRec(tree, f.getTarget(), visited, reticulation2LSA, node2below, ret2Node2Length, ret2length);
				reticulationsBelow.addAll(node2below.get(f.getTarget()));
			}

			reticulationsBelow.removeAll(node2below.get(v)); // because reticulations mentioned here don't hve v as LSA

			for (Node r : reticulationsBelow) {
				NodeDoubleArray node2Dist = ret2Node2Length.get(r);
				double length = 0;
				for (Edge f = v.getFirstOutEdge(); f != null; f = v.getNextOutEdge(f)) {
					Node w = f.getTarget();
					length += node2Dist.getDouble(w);
					if (!tree.isReticulateEdge(f))
						length += tree.getWeight(f);
				}
				if (v.getOutDegree() > 0)
					length /= v.getOutDegree();
				node2Dist.put(v, length);
				if (reticulation2LSA.get(r) == v)
					ret2length.put(r, length);
			}
		}
	}
}

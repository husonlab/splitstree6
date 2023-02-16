/*
 *  EnumerateTrees.java Copyright (C) 2023 Daniel H. Huson
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
import jloda.graph.Edge;
import jloda.graph.EdgeArray;
import jloda.graph.EdgeSet;
import jloda.graph.Node;
import jloda.phylo.PhyloTree;
import jloda.util.BitSetUtils;
import jloda.util.IteratorUtils;
import jloda.util.SetUtils;
import jloda.util.progress.ProgressListener;
import splitstree6.algorithms.utils.TreesUtilities;
import splitstree6.data.TaxaBlock;
import splitstree6.data.TreesBlock;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * enumerate all trees contained in a rooted network
 * Daniel Huson, 2.2023
 */
public class EnumerateTrees extends Trees2Trees {
	private final BooleanProperty optionRemoveDuplicates = new SimpleBooleanProperty(this, "optionRemoveDuplicates", true);

	@Override
	public List<String> listOptions() {
		return List.of(optionRemoveDuplicates.getName());
	}

	@Override
	public void compute(ProgressListener progress, TaxaBlock taxaBlock, TreesBlock parent, TreesBlock child) throws IOException {
		child.setPartial(parent.isPartial());
		child.setReticulated(false);
		for (var t = 1; t <= parent.getNTrees(); t++) {
			var network = parent.getTree(t);
			var containedTrees = extractContainedTrees(network);
			if (isOptionRemoveDuplicates())
				containedTrees = removeDuplicates(containedTrees);
			for (var i = 0; i < containedTrees.size(); i++) {
				var tree = containedTrees.get(i);
				tree.setName(network.getName() + "-" + (i + 1));
			}
			child.getTrees().addAll(containedTrees);
		}
		System.err.printf("Total number of trees enumerated: %,d%n", child.getNTrees());

		if (true)
			for (var s = 1; s <= child.getNTrees(); s++) {
				var tree1 = child.getTree(s);
				for (var t = s + 1; t <= child.getNTrees(); t++) {
					var tree2 = child.getTree(t);
					if (tree1.getNumberOfTaxa() == tree2.getNumberOfTaxa() && haveRootedSprDistanceOne(taxaBlock, tree1, tree2))
						System.err.printf("Trees %s and %s have rSPR distance 1%n", tree1.getName(), tree2.getName());
				}
			}
	}

	/**
	 * extract all contained trees
	 *
	 * @param network the network
	 * @return the contained trees
	 */
	public ArrayList<PhyloTree> extractContainedTrees(PhyloTree network) {
		var competingEdgeSets = new ArrayList<Edge[]>();

		for (var v : network.nodes()) {
			if (v.getInDegree() > 1) {
				var set = IteratorUtils.asList(v.inEdges()).toArray(new Edge[0]);
				competingEdgeSets.add(set);
			}
		}

		var trees = new ArrayList<PhyloTree>();
		try (var inactive = network.newEdgeSet()) {
			extractContainedTreesRec(0, competingEdgeSets, network, network.getRoot(), inactive, trees);
		}
		return trees;
	}

	private void extractContainedTreesRec(int which, ArrayList<Edge[]> competingEdgeSets, PhyloTree network, Node root, EdgeSet inactive, ArrayList<PhyloTree> trees) {
		if (which < competingEdgeSets.size()) {
			var edges = competingEdgeSets.get(which);
			inactive.addAll(List.of(edges));
			for (Edge edge : edges) {
				inactive.remove(edge);
				extractContainedTreesRec(which + 1, competingEdgeSets, network, root, inactive, trees);
				inactive.add(edge);
			}
		} else {
			var tree = new PhyloTree();
			try (EdgeArray<Edge> oldEdge2NewEdgeMap = network.newEdgeArray()) {
				tree.copy(network, null, oldEdge2NewEdgeMap);
				inactive.stream().map(oldEdge2NewEdgeMap::get).forEach(tree::deleteEdge);
				tree.clearTransferAcceptorEdges();
				tree.clearRetciulateEdges();
				tree.clearLsaChildrenMap();

				while (true) {
					var nakedLeaves = tree.nodeStream().filter(v -> v.isLeaf() && tree.getNumberOfTaxa(v) == 0).collect(Collectors.toList());
					if (nakedLeaves.size() > 0) {
						nakedLeaves.forEach(tree::deleteNode);
					} else
						break;
				}

				var diVertices = tree.nodeStream().filter(v -> v.getInDegree() == 1 && v.getOutDegree() == 1).collect(Collectors.toList());
				for (var v : diVertices) {
					tree.delDivertex(v);
				}

			}
			trees.add(tree);
		}
	}


	/**
	 * remove duplicate trees by comparing contained clusters
	 *
	 * @param trees input trees
	 * @return reduced trees
	 */
	private ArrayList<PhyloTree> removeDuplicates(ArrayList<PhyloTree> trees) {
		var result = new ArrayList<PhyloTree>();
		var clusterSets = new ArrayList<Set<BitSet>>();

		for (var tree : trees) {
			var clusters = new HashSet<BitSet>(TreesUtilities.extractClusters(tree).values());
			if (!clusterSets.contains(clusters)) {
				result.add(tree);
				clusterSets.add(clusters);
			}
		}
		return result;
	}

	public static boolean haveRootedSprDistanceOne(TaxaBlock taxa, PhyloTree tree1, PhyloTree tree2) {
		if (true) {
			var clusters1 = TreesUtilities.extractClusters(tree1).values();
			var clusters2 = new HashSet<>(TreesUtilities.extractClusters(tree2).values());

			if (allCompatible(clusters1, clusters2))
				return false; // rSPR distance is 0

			var clusters1sorted = new ArrayList<>(clusters1);
			clusters1sorted.sort(Comparator.comparingInt(BitSet::cardinality));

			for (var cluster : SetUtils.intersection(clusters1sorted, clusters2)) {
				var inC1 = clusters1.stream().filter(c -> BitSetUtils.contains(cluster, c)).toList();
				var inC2 = clusters2.stream().filter(c -> BitSetUtils.contains(cluster, c)).toList();
				if (!allCompatible(inC1, inC2))
					return false; // found two incompatible subtrees that we can fix, because going through clusters in order of increasing size
				var withoutC1 = clusters1.stream().map(c -> BitSetUtils.minus(c, cluster)).filter(c -> c.cardinality() > 0).toList();
				var withoutC2 = clusters2.stream().map(c -> BitSetUtils.minus(c, cluster)).filter(c -> c.cardinality() > 0).toList();
				if (allCompatible(withoutC1, withoutC2))
					return true;
			}
			return false;
		} else {
			var n2c1 = TreesUtilities.extractClusters(tree1);
			var n2c2 = TreesUtilities.extractClusters(tree2);

			{
				if (allCompatible(n2c1.values(), n2c2.values()))
					return false; // rSPR distance is 0
			}

			var c2n2 = new HashMap<BitSet, Node>();
			for (var entry : n2c2.entrySet()) {
				c2n2.put(entry.getValue(), entry.getKey());
			}

			for (var v : tree1.nodeStream().filter(v -> v.getInDegree() == 1).collect(Collectors.toList())) {
				var c = n2c1.get(v);
				var vp = v.getParent();
				var cvp = n2c1.get(vp);

				var w = c2n2.get(c);
				if (w != null && w.getInDegree() == 1) {
					var wp = w.getParent();
					var cwp = n2c2.get(wp);
					if (cvp != cwp) {
						{
							var inC1 = n2c1.values().stream().filter(cluster -> BitSetUtils.contains(c, cluster)).toList();
							var inC2 = n2c2.values().stream().filter(cluster -> BitSetUtils.contains(c, cluster)).toList();
							if (!allCompatible(inC1, inC2))
								continue;
						}
						{
							var withoutC1 = n2c1.values().stream().map(cluster -> BitSetUtils.minus(cluster, c))
									.filter(cluster -> cluster.cardinality() > 0).toList();
							var withoutC2 = n2c2.values().stream().map(cluster -> BitSetUtils.minus(cluster, c))
									.filter(cluster -> cluster.cardinality() > 0).toList();
							if (allCompatible(withoutC1, withoutC2))
								return true;
						}
					}
				}
			}
			return false;
		}
	}

	private static boolean allCompatible(Collection<BitSet> clusters1, Collection<BitSet> clusters2) {
		for (var c1 : clusters1) {
			for (var c2 : clusters2) {
				var intersectionSize = BitSetUtils.intersection(c1, c2).cardinality();
				if (intersectionSize != 0 && intersectionSize != c1.cardinality() && intersectionSize != c2.cardinality())
					return false;
			}
		}
		return true;
	}


	@Override
	public boolean isApplicable(TaxaBlock taxa, TreesBlock datablock) {
		return datablock.isReticulated();
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

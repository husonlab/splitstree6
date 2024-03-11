/*
 *  EnumerateContainedTrees.java Copyright (C) 2024 Daniel H. Huson
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
import jloda.util.IteratorUtils;
import jloda.util.progress.ProgressListener;
import splitstree6.data.TaxaBlock;
import splitstree6.data.TreesBlock;
import splitstree6.splits.TreesUtils;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * enumerate all trees contained in a rooted network
 * Daniel Huson, 2.2023
 */
public class EnumerateContainedTrees extends Trees2Trees {
	private final BooleanProperty optionRemoveDuplicates = new SimpleBooleanProperty(this, "optionRemoveDuplicates", true);

	@Override
	public List<String> listOptions() {
		return List.of(optionRemoveDuplicates.getName());
	}

	@Override
	public String getShortDescription() {
		return "Enumerates all contained trees.";
	}

	@Override
	public void compute(ProgressListener progress, TaxaBlock taxaBlock, TreesBlock inputTreesBlock, TreesBlock outputTreesBlock) throws IOException {
		outputTreesBlock.setPartial(inputTreesBlock.isPartial());
		outputTreesBlock.setReticulated(false);
		for (var t = 1; t <= inputTreesBlock.getNTrees(); t++) {
			var network = inputTreesBlock.getTree(t);
			var containedTrees = extractContainedTrees(network);
			if (isOptionRemoveDuplicates())
				containedTrees = removeDuplicates(containedTrees);
			for (var i = 0; i < containedTrees.size(); i++) {
				var tree = containedTrees.get(i);
				tree.setName(network.getName() + "-" + (i + 1));
			}
			outputTreesBlock.getTrees().addAll(containedTrees);
		}
		System.err.printf("Total number of trees enumerated: %,d%n", outputTreesBlock.getNTrees());
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
				tree.clearReticulateEdges();
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
			var clusters = new HashSet<BitSet>(TreesUtils.extractClusters(tree).values());
			if (!clusterSets.contains(clusters)) {
				result.add(tree);
				clusterSets.add(clusters);
			}
		}
		return result;
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

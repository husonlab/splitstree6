/*
 *  TreesFilterMore.java Copyright (C) 2024 Daniel H. Huson
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

import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import jloda.graph.Graph;
import jloda.graph.Node;
import jloda.phylo.PhyloTree;
import jloda.phylo.algorithms.RootedNetworkProperties;
import jloda.util.ExecuteInParallel;
import jloda.util.ProgramExecutorService;
import jloda.util.progress.ProgressListener;
import splitstree6.algorithms.IFilter;
import splitstree6.data.TaxaBlock;
import splitstree6.data.TreesBlock;
import splitstree6.utils.ClusterUtils;
import splitstree6.utils.TreesUtils;

import java.util.BitSet;
import java.util.HashMap;
import java.util.List;

/**
 * filters tree edges by concordance (that is, number of input trees that contain them)
 * Daniel Huson, 5.2026
 */
public class TreesConcordanceFilter extends Trees2Trees implements IFilter {
	private final DoubleProperty optionMinConcordance = new SimpleDoubleProperty(this, "optionMinConcordance", 0);

	public List<String> listOptions() {
		return List.of(optionMinConcordance.getName());
	}

	@Override
	public String getToolTip(String optionName) {
		return switch (optionName) {
			case "optionMinConcordance" -> "Minimum percentage of trees that any must be compatible with to remain";
			default -> optionName;
		};
	}

	@Override
	public String getShortDescription() {
		return "Filter edges in trees by concordance (minimum percentage of trees that any must be compatible with to remain)";
	}

	@Override
	public void compute(ProgressListener progress, TaxaBlock taxaBlock, TreesBlock inputData, TreesBlock outputData) {
		if (!isActive() || inputData.isReticulated()) {
			outputData.getTrees().addAll(inputData.getTrees());
			outputData.setRooted(inputData.isRooted());
			outputData.setPartial(inputData.isPartial());
			outputData.setReticulated(inputData.isReticulated());
		} else {
			var trees = outputData.getTrees();
			trees.clear();
			for (var tree : inputData.getTrees()) {
				var newTree = new PhyloTree(tree);
				newTree.clearLsaChildrenMap();
				trees.add(newTree);
			}
			outputData.setRooted(inputData.isRooted());
			outputData.setPartial(inputData.isPartial());
			outputData.setReticulated(inputData.isReticulated());

			var clusterTreesMaps = new HashMap<BitSet, BitSet>();
			for (var t = 1; t <= inputData.getNTrees(); t++) {
				var tree = inputData.getTree(t);
				for (var cluster : TreesUtils.collectAllHardwiredClusters(tree)) {
					clusterTreesMaps.computeIfAbsent(cluster, k -> new BitSet()).set(t);
				}
			}

			try {
				var threshold = (int) (inputData.getNTrees() * Math.min(1, Math.max(0, 1.0 - (optionMinConcordance.get() / 100.0))));
				System.err.printf("Keeping all edges that are compatible with at least %d input trees%n", (inputData.getNTrees() - threshold));

				var clusterGraph = new Graph();
				var clusterNodeClusterGraphMap = new HashMap<BitSet, Node>();
				for (var entry : clusterTreesMaps.entrySet()) {
					var v = clusterGraph.newNode();
					v.setInfo(entry.getKey());
					v.setData(entry.getValue());
					clusterNodeClusterGraphMap.put(entry.getKey(), v);
				}
				for (var v : clusterGraph.nodes()) {
					if (v.getInfo() instanceof BitSet clusterV) {
						for (var w : clusterGraph.nodes(v)) {
							if (w.getInfo() instanceof BitSet clusterW) {
								if (!ClusterUtils.compatible(clusterV, clusterW)) {
									clusterGraph.newEdge(v, w);
								}
							}
						}
					}
				}
				ExecuteInParallel.apply(outputData.getTrees(), tree -> {
					try (var nodeClusterMap = TreesUtils.extractClusters(tree);
						 var toContract = tree.newEdgeSet()) {
						tree.edgeStream().filter(e -> !e.getTarget().isLeaf())
								.forEach(e -> {
									var w = e.getTarget();
									var cluster = nodeClusterMap.get(w);
									var clustGraphNode = clusterNodeClusterGraphMap.get(cluster);

									var treeSet = new BitSet();
									clusterNodeClusterGraphMap.get(cluster).adjacentEdgesStream(false).
											map(f -> (BitSet) f.getOpposite(clustGraphNode).getData()).forEach(treeSet::or);
									if (treeSet.cardinality() >= threshold)
										toContract.add(e);
								});
						RootedNetworkProperties.contractEdges(tree, toContract, null);
					}
				}, ProgramExecutorService.getNumberOfCoresToUse(), progress);
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		}
		if (outputData.getNTrees() == inputData.getNTrees())
			setShortDescription("using all " + inputData.size() + " trees");
		else
			setShortDescription("using " + outputData.size() + " of " + inputData.size() + " trees");
	}

	public double getOptionMinConcordance() {
		return optionMinConcordance.get();
	}

	public DoubleProperty optionMinConcordanceProperty() {
		return optionMinConcordance;
	}

	public void setOptionMinConcordance(double optionMinConcordance) {
		this.optionMinConcordance.set(optionMinConcordance);
	}

	@Override
	public boolean isApplicable(TaxaBlock taxa, TreesBlock treesBlock) {
		return treesBlock.size() > 1 && !treesBlock.isReticulated();
	}

	@Override
	public boolean isActive() {
		return getOptionMinConcordance() > 0;
	}
}

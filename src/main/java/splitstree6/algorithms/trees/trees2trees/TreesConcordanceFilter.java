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
import jloda.phylo.PhyloTree;
import jloda.phylo.algorithms.RootedNetworkProperties;
import jloda.util.ExecuteInParallel;
import jloda.util.ProgramExecutorService;
import jloda.util.progress.ProgressListener;
import splitstree6.algorithms.IFilter;
import splitstree6.data.TaxaBlock;
import splitstree6.data.TreesBlock;
import splitstree6.utils.TreesUtils;

import java.util.List;
import java.util.stream.Collectors;

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
			case "optionMinConcordance" -> "Minimum percentage of trees that must contain an edge for it remain";
			default -> optionName;
		};
	}

	@Override
	public String getShortDescription() {
		return "Filter edges in trees by concordance (percentage of trees that must contain an edge for it remain)";
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

			var clusterCountMap = trees.parallelStream()
					.flatMap(tree -> TreesUtils.collectAllHardwiredClusters(tree).stream())
					.filter(c -> c.cardinality() > 1 && c.cardinality() < taxaBlock.getNtax())
					.collect(Collectors.toConcurrentMap(c -> c, c -> 1, Integer::sum));

			try {
				var threshold = outputData.getNTrees() * Math.min(1.0, optionMinConcordance.get() / 100.0);
				ExecuteInParallel.apply(outputData.getTrees(), tree -> {
					try (var nodeClusterMap = TreesUtils.extractClusters(tree);
						 var toContract = tree.newEdgeSet()) {
						tree.edgeStream().filter(e -> !e.getTarget().isLeaf())
								.forEach(e -> {
									var w = e.getTarget();
									if (nodeClusterMap.containsKey(w)) {
										var cluster = nodeClusterMap.get(w);
										if (clusterCountMap.containsKey(cluster) && clusterCountMap.get(cluster) < threshold) {
											toContract.add(e);
										}
									}
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
		return treesBlock.size() > 1 && !treesBlock.isPartial() && !treesBlock.isReticulated();
	}

	@Override
	public boolean isActive() {
		return getOptionMinConcordance() > 0;
	}
}

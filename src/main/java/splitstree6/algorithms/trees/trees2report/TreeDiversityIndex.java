/*
 *  TreeDiversityIndex.java Copyright (C) 2024 Daniel H. Huson
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

package splitstree6.algorithms.trees.trees2report;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import jloda.graph.NodeDoubleArray;
import jloda.phylo.PhyloTree;
import jloda.util.BitSetUtils;
import jloda.util.CanceledException;
import jloda.util.NumberUtils;
import jloda.util.StringUtils;
import jloda.util.progress.ProgressListener;
import splitstree6.data.TaxaBlock;
import splitstree6.data.TreesBlock;
import splitstree6.data.parts.Taxon;
import splitstree6.utils.TreesUtils;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collection;
import java.util.List;

/**
 * compute fair proportion and/or equal splits values on trees
 * Daniel Huson, 2.2023
 */
public class TreeDiversityIndex extends Trees2ReportBase {
	public enum Method {FairProportions, EqualSplits}

	private final ObjectProperty<Method> optionMethod = new SimpleObjectProperty<>(this, "optionMethod", Method.FairProportions);

	@Override
	public List<String> listOptions() {
		var list = new ArrayList<String>();
		list.add(optionMethod.getName());
		list.addAll(super.listOptions());
		return list;
	}

	@Override
	public String getToolTip(String optionName) {
		if (!optionName.startsWith("option"))
			optionName = "option" + optionName;
		if (optionName.equals(optionMethod.getName()))
			return "choose the type of index calculation";
		else return super.getToolTip(optionName);
	}

	@Override
	public String getCitation() {
		return "Redding 2003;D. Redding, Incorporating genetic distinctness and reserve occupancy into a conservation priorisation approach. Master’s thesis. University of East Anglia, 2003.;" +
			   "Redding & Mooers 2006;DW Redding and AO Mooers. Incorporating evolutionary measures into conservation prioritization. Conservation Biology 20, 1670–1678, 2006.";
	}

	@Override
	public String getShortDescription() {
		return "Calculates the fair-proportion and equal-splits values on trees.";
	}

	@Override
	String runAnalysis(ProgressListener progress, TaxaBlock taxaBlock, TreesBlock treesBlock, Collection<Taxon> selectedTaxa) throws CanceledException {
		var selectedTaxonSet = BitSetUtils.asBitSet(selectedTaxa.stream().mapToInt(taxaBlock::indexOf).toArray());
		return switch (getOptionMethod()) {
			case FairProportions -> reportFairProportions(progress, taxaBlock, selectedTaxonSet, treesBlock);
			case EqualSplits -> reportEqualSplits(progress, taxaBlock, selectedTaxonSet, treesBlock);
		};
	}

	@Override
	public boolean isApplicable(TaxaBlock taxa, TreesBlock treesBlock) {
		return super.isApplicable(taxa, treesBlock) && !treesBlock.isReticulated();
	}

	public Method getOptionMethod() {
		return optionMethod.get();
	}

	public ObjectProperty<Method> optionMethodProperty() {
		return optionMethod;
	}

	public void setOptionMethod(Method optionMethod) {
		this.optionMethod.set(optionMethod);
	}

	public static String reportFairProportions(ProgressListener progress, TaxaBlock taxaBlock, BitSet selectedTaxa, TreesBlock treesBlock) throws CanceledException {
		var buf = new StringBuilder();
		buf.append("Fair Proportions Diversity Index:\n");
		progress.setTasks("Computing", "fair proportions diversity index");
		progress.setMaximum(treesBlock.getNTrees());
		progress.setProgress(0);
		for (var tree : treesBlock.getTrees()) {
			var total = tree.edgeStream().mapToDouble(e -> Math.max(0, tree.getWeight(e))).sum();
			var totalRounded = NumberUtils.roundSigFig(total, 5);

			buf.append("%nTree %s (total: %s):%n".formatted(tree.getName(), StringUtils.removeTrailingZerosAfterDot(totalRounded)));

			try (var map = computeFairProportions(tree)) {
				var entries = new ArrayList<>(map.entrySet());
				entries.sort((a, b) -> Double.compare(b.getValue(), a.getValue())); // by decreasing value
				for (var entry : entries) {
					var t = tree.getTaxon(entry.getKey());
					if (selectedTaxa.cardinality() == 0 || selectedTaxa.get(t)) {
						var valueRounded = NumberUtils.roundSigFig(entry.getValue(), 5);
						buf.append(String.format("%s: %s (%.2f%%)%n", taxaBlock.get(t).getName(), StringUtils.removeTrailingZerosAfterDot(valueRounded), 100.0 * entry.getValue() / total));
					}
				}
			}
			progress.incrementProgress();
		}
		progress.reportTaskCompleted();
		return buf.toString();
	}

	public static NodeDoubleArray computeFairProportions(PhyloTree tree) {
		try (var node2clusters = TreesUtils.extractClusters(tree)) {
			var diversityIndex = tree.newNodeDoubleArray();
			diversityIndex.put(tree.getRoot(), 0.0);
			tree.preorderTraversal(tree.getRoot(), v -> v.parentsStream(false).allMatch(diversityIndex::containsKey),
					v -> {
						var weight = 0.0;
						if (v.getInDegree() > 0) {
							var numberBelow = node2clusters.get(v).cardinality();
							for (var e : v.inEdges()) {
								weight += diversityIndex.get(e.getSource()) + (tree.getWeight(e) / numberBelow);
							}
						}
						diversityIndex.put(v, weight);
					});

			// remove values for unlabeled nodes
			for (var v : tree.nodes()) {
				if (tree.getNumberOfTaxa(v) == 0) {
					diversityIndex.remove(v);
				} else {
					diversityIndex.put(v, diversityIndex.get(v) / (tree.getNumberOfTaxa(v)));
				}
			}
			return diversityIndex;
		}
	}

	public static String reportEqualSplits(ProgressListener progress, TaxaBlock taxaBlock, BitSet selectedTaxa, TreesBlock treesBlock) throws CanceledException {
		var buf = new StringBuilder();
		buf.append("Equal Splits Diversity Index:\n");
		progress.setTasks("Computing", "equal splits diversity index");
		progress.setMaximum(treesBlock.getNTrees());
		progress.setProgress(0);

		for (var tree : treesBlock.getTrees()) {
			var total = tree.edgeStream().mapToDouble(e -> Math.max(0, tree.getWeight(e))).sum();
			var totalRounded = NumberUtils.roundSigFig(total, 5);

			buf.append("%nTree %s (total: %s):%n".formatted(tree.getName(), StringUtils.removeTrailingZerosAfterDot(totalRounded)));
			var map = computeEqualSplits(tree);
			var entries = new ArrayList<>(map.entrySet());
			entries.sort((a, b) -> Double.compare(b.getValue(), a.getValue())); // by decreasing value

			for (var entry : entries) {
				var t = tree.getTaxon(entry.getKey());
				if (selectedTaxa.cardinality() == 0 || selectedTaxa.get(t)) {
					var valueRounded = NumberUtils.roundSigFig(entry.getValue(), 5);
					buf.append(String.format("%s: %s (%.2f%%)%n", taxaBlock.get(t).getName(), StringUtils.removeTrailingZerosAfterDot(valueRounded), 100 * entry.getValue() / total));
				}
			}
			progress.incrementProgress();
		}
		progress.reportTaskCompleted();
		return buf.toString();
	}

	public static NodeDoubleArray computeEqualSplits(PhyloTree tree) {
		var diversityIndex = tree.newNodeDoubleArray();
		diversityIndex.put(tree.getRoot(), 0d);
		tree.preorderTraversal(v -> {
			for (var e : v.outEdges()) {
				diversityIndex.put(e.getTarget(), (diversityIndex.get(v) / v.getOutDegree()) + tree.getWeight(e));
			}
		});
		for (var v : tree.nodes()) {
			if (tree.getNumberOfTaxa(v) == 0) {
				diversityIndex.remove(v);
			}
		}
		return diversityIndex;
	}
}

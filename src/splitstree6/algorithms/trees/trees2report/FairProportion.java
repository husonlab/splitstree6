/*
 * ShowSplits.java Copyright (C) 2023 Daniel H. Huson
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

package splitstree6.algorithms.trees.trees2report;

import jloda.graph.NodeDoubleArray;
import jloda.phylo.PhyloTree;
import jloda.util.BitSetUtils;
import jloda.util.CanceledException;
import jloda.util.progress.ProgressListener;
import splitstree6.algorithms.utils.TreesUtilities;
import splitstree6.data.TaxaBlock;
import splitstree6.data.TreesBlock;
import splitstree6.data.parts.Taxon;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collection;

/**
 * compute fair proporition values on trees
 * Daniel Huson, 2.2023
 */
public class FairProportion extends Trees2ReportBase {
	@Override
	String runAnalysis(ProgressListener progress, TaxaBlock taxaBlock, TreesBlock treesBlock, Collection<Taxon> selectedTaxa) throws CanceledException {
		var taxa = BitSetUtils.asBitSet(selectedTaxa.stream().mapToInt(taxaBlock::indexOf).toArray());
		return report(progress, taxaBlock, treesBlock, taxa);
	}

	@Override
	public String getCitation() {
		return "D Redding 2004;Redding, D. Incorporating genetic distinctness and reserve occupancy into a conservation priorisation approach. Master’s thesis. University of East Anglia (2003)";
	}

	public static String report(ProgressListener progress, TaxaBlock taxaBlock, TreesBlock treesBlock, BitSet selectedTaxa) throws CanceledException {
		var buf = new StringBuilder();
		buf.append("Fair Proportions:\n");
		progress.setTasks("Computing", "fair proporitions");
		progress.setMaximum(treesBlock.getNTrees());
		progress.setProgress(0);
		for (var tree : treesBlock.getTrees()) {
			buf.append("%nTree %s:%n".formatted(tree.getName()));
			var map = compute(tree);
			var entries = new ArrayList<>(map.entrySet());
			entries.sort((a, b) -> Double.compare(b.getValue(), a.getValue())); // by decreasing value
			for (var entry : entries) {
				for (var t : tree.getTaxa(entry.getKey())) {
					buf.append(String.format("%s: %.2f%%%n", taxaBlock.get(t).getName(), 100 * entry.getValue()));
				}
			}
			progress.incrementProgress();
		}
		progress.reportTaskCompleted();
		return buf.toString();
	}

	public static NodeDoubleArray compute(PhyloTree tree) {
		try (var node2clusters = TreesUtilities.extractClusters(tree)) {
			var fairProportion = tree.newNodeDoubleArray();
			fairProportion.put(tree.getRoot(), 0.0);
			tree.preorderTraversal(tree.getRoot(), v -> v.parentsStream(false).allMatch(fairProportion::containsKey),
					v -> {
						var weight = 0.0;
						if (v.getInDegree() > 0) {
							var numberBelow = node2clusters.get(v).cardinality();
							for (var e : v.inEdges()) {
								weight += fairProportion.get(e.getSource()) + (tree.getWeight(e) / numberBelow);
							}
						}
						fairProportion.put(v, weight);
					});

			// remove values for unlabeled trees
			var total = tree.edgeStream().mapToDouble(e -> Math.max(0, tree.getWeight(e))).sum();
			for (var v : tree.nodes()) {
				if (tree.getNumberOfTaxa(v) == 0) {
					fairProportion.remove(v);
				} else {
					fairProportion.put(v, fairProportion.get(v) / (tree.getNumberOfTaxa(v) * total));
				}
			}
			if (true) {
				var sum = fairProportion.values().stream().mapToDouble(d -> d).sum();
				if (Math.abs(sum - 1.0) > 0.01)
					System.err.println("Fair proportion calculation wrong? sum=" + sum);
			}
			return fairProportion;
		}
	}
}

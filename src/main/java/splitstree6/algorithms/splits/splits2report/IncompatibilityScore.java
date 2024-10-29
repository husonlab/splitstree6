/*
 *  ShapleyValues.java Copyright (C) 2024 Daniel H. Huson
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

package splitstree6.algorithms.splits.splits2report;

import jloda.graph.Graph;
import jloda.graph.Node;
import jloda.util.StringUtils;
import jloda.util.progress.ProgressListener;
import splitstree6.algorithms.utils.GreedyCompatible;
import splitstree6.data.SplitsBlock;
import splitstree6.data.TaxaBlock;
import splitstree6.data.parts.Taxon;
import splitstree6.splits.ASplit;
import splitstree6.splits.BiPartition;

import java.util.Collection;
import java.util.List;

/**
 * compute incompatibility score
 * Daniel Huson, 10.2024
 */
public class IncompatibilityScore extends Splits2ReportBase {
	@Override
	String runAnalysis(ProgressListener progress, TaxaBlock taxaBlock, SplitsBlock splitsBlock, Collection<Taxon> selectedTaxa) {
		return report(taxaBlock, splitsBlock.getSplits());
	}

	@Override
	public String getCitation() {
		return super.getCitation();
	}

	@Override
	public String getShortDescription() {
		return "Calculates an incompatibility score on splits.";
	}

	public static String report(TaxaBlock taxaBlock, List<ASplit> splits) {
		if (true) {
			var compatible = GreedyCompatible.apply(splits, ASplit::getWeight);
			var totalWeight = splits.stream().filter(s -> !s.isTrivial()).mapToDouble(ASplit::getWeight).sum();
			var compatibleWeight = compatible.stream().filter(s -> !s.isTrivial()).mapToDouble(ASplit::getWeight).sum();

			var result = "# Total weight of splits not in a greedy tree, as a percent of the sum of weights of all non-trivial splits:\n";

			result += "%.8f of %.8f (%.1f%%)".formatted(totalWeight - compatibleWeight, totalWeight,
					100 * (totalWeight - compatibleWeight) / totalWeight);
			return result;

		} else {
			var incompatilityGraph = buildIncompatibilityGraph(splits);

			var area = 0.0;
			for (var e : incompatilityGraph.edges()) {
				var s = (ASplit) e.getSource().getInfo();
				var t = (ASplit) e.getTarget().getInfo();
				area += s.getWeight() * t.getWeight();
			}
			var total = 0.0;
			for (var i = 0; i < splits.size(); i++) {
				var s = splits.get(i);
				if (!s.isTrivial()) {
					for (var j = i + 1; j < splits.size(); j++) {
						var t = splits.get(j);
						if (!t.isTrivial()) {
							total += s.getWeight() * t.getWeight();
						}
					}
				}
			}
			return String.format("%s of %s (%.2f%%)", StringUtils.removeTrailingZerosAfterDot(area),
					StringUtils.removeTrailingZerosAfterDot(total), area * 100.0 / total);
		}
	}


	public static Graph buildIncompatibilityGraph(List<ASplit> splits) {
		var graph = new Graph();

		var split2node = new Node[splits.size()];
		for (var s = 0; s < splits.size(); s++) {
			split2node[s] = graph.newNode(splits.get(s));
		}
		for (int s = 0; s < splits.size(); s++) {
			for (int t = s + 1; t < splits.size(); t++)
				if (!BiPartition.areCompatible(splits.get(s), splits.get(t))) {
					graph.newEdge(split2node[s], split2node[t]);
				}
		}
		return graph;
	}
}

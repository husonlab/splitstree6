/*
 * ShowSplits.java Copyright (C) 2024 Daniel H. Huson
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

package splitstree6.algorithms.splits.splits2report;

import jloda.util.BitSetUtils;
import jloda.util.ExecuteInParallel;
import jloda.util.NumberUtils;
import jloda.util.ProgramExecutorService;
import jloda.util.progress.ProgressListener;
import splitstree6.data.SplitsBlock;
import splitstree6.data.TaxaBlock;
import splitstree6.data.parts.Taxon;
import splitstree6.splits.ASplit;

import java.util.*;

/**
 * compute shapley values
 * Daniel Huson, 2.2023
 */
public class ShapleyValues extends Splits2ReportBase {
	@Override
	String runAnalysis(ProgressListener progress, TaxaBlock taxaBlock, SplitsBlock splitsBlock, Collection<Taxon> selectedTaxa) {
		return report(taxaBlock, splitsBlock.getSplits());
	}

	@Override
	public String getCitation() {
		return "Volkmann et al 2014;L. Volkmann, I. Martyn, V. Moulton, A. Spillner, and AO Mooers." +
			   " Prioritizing populations for conservation using phylogenetic networks. PLoS ONE 9(2):e88945, 2014";
	}

	@Override
	public String getShortDescription() {
		return "Calculates Shapley values on splits.";
	}

	public static String report(TaxaBlock taxaBlock, Collection<ASplit> splits) {
		var total = splits.stream().mapToDouble(ASplit::getWeight).sum();

		var map = compute(taxaBlock.getTaxaSet(), splits);
		var entries = new ArrayList<>(map.entrySet());
		entries.sort((a, b) -> Double.compare(b.getValue(), a.getValue())); // by decreasing value
		var buf = new StringBuilder("Unrooted Shapley values:\n");
		for (var entry : entries) {
			var valueRounded = NumberUtils.roundSigFig(entry.getValue(), 5);
			buf.append(String.format("%s: %s (%.2f%%)%n", taxaBlock.get(entry.getKey()).getName(), valueRounded, 100 * entry.getValue() / total));
		}
		return buf.toString();
	}

	public static Map<Integer, Double> compute(BitSet taxa, Collection<ASplit> splits) {
		var ntax = taxa.cardinality();
		var taxonShapleyMap = new HashMap<Integer, Double>();

		try {
			ExecuteInParallel.apply(BitSetUtils.asList(taxa), t -> {
				taxonShapleyMap.put(t, splits.stream().mapToDouble(s -> (s.getPartNotContaining(t).cardinality() * s.getWeight()) / (ntax * s.getPartContaining(t).cardinality())).sum());
			}, ProgramExecutorService.getNumberOfCoresToUse());
		} catch (Exception ignored) {
		}
		return taxonShapleyMap;
	}
}

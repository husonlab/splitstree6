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
import jloda.util.NumberUtils;
import jloda.util.StringUtils;
import jloda.util.progress.ProgressListener;
import splitstree6.data.SplitsBlock;
import splitstree6.data.TaxaBlock;
import splitstree6.data.parts.Taxon;
import splitstree6.splits.ASplit;

import java.util.BitSet;
import java.util.Collection;

/**
 * compute phylogenetic diversity
 * Daniel Huson, 2.2023
 */
public class PhylogeneticDiversity extends Splits2ReportBase {
	@Override
	String runAnalysis(ProgressListener progress, TaxaBlock taxaBlock, SplitsBlock splitsBlock, Collection<Taxon> selectedTaxa) {
		var taxa = BitSetUtils.asBitSet(selectedTaxa.stream().mapToInt(taxaBlock::indexOf).toArray());
		return report(taxaBlock, splitsBlock, taxa);
	}

	@Override
	public String getCitation() {
		return "Volkmann et al 2014;Volkmann L, Martyn I, Moulton V, Spillner A, Mooers AO." +
			   " Prioritizing populations for conservation using phylogenetic networks. PLoS ONE 9(2):e88945 (2014)";
	}

	@Override
	public String getShortDescription() {
		return "Calculates the phylogenetic diversity for selected taxa.";
	}

	/**
	 * compute the phylogenetic diversity of a set of taxa for a given set of splits
	 *
	 * @param splits the splits
	 * @param taxa   the taxa
	 * @return phylogenetic diversity
	 */
	public static double compute(SplitsBlock splits, BitSet taxa) {
		return splits.getSplits().stream().filter(s -> s.getA().intersects(taxa) && s.getB().intersects(taxa))
				.mapToDouble(ASplit::getWeight).sum();
	}

	public static String report(TaxaBlock taxaBlock, SplitsBlock splitsBlock, BitSet selectedTaxa) {
		var total = splitsBlock.getSplits().stream().mapToDouble(ASplit::getWeight).sum();
		var totalRounded = NumberUtils.roundSigFig(total, 5);

		var buf = new StringBuilder();
		buf.append("%nSplits (total: %s):%n".formatted(StringUtils.removeTrailingZerosAfterDot(totalRounded)));

		var diversity = compute(splitsBlock, selectedTaxa);
		var diversityRounded = NumberUtils.roundSigFig(diversity, 5);
		buf.append("Phylogenetic Diversity = %s (%.1f%%)%n".formatted(StringUtils.removeTrailingZerosAfterDot(diversityRounded), 100.0 * (diversity / total)));
		buf.append("Computed on %d (of %d) selected taxa:%n".formatted(selectedTaxa.cardinality(), taxaBlock.getNtax()));
		if (selectedTaxa.cardinality() > 0) {
			for (var t = 1; t <= taxaBlock.getNtax(); t++) {
				if (selectedTaxa.get(t)) {
					buf.append(taxaBlock.get(t).getName()).append("\n");
				}
			}
		}
		return buf.toString();
	}
}

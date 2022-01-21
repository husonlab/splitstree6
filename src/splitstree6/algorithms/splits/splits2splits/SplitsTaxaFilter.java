/*
 * SplitsTaxaFilter.java Copyright (C) 2022 Daniel H. Huson
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

package splitstree6.algorithms.splits.splits2splits;

import jloda.util.progress.ProgressListener;
import splitstree6.data.SplitsBlock;
import splitstree6.data.TaxaBlock;
import splitstree6.data.parts.ASplit;
import splitstree6.data.parts.Compatibility;
import splitstree6.workflow.DataTaxaFilter;

import java.io.IOException;
import java.util.BitSet;
import java.util.Map;

public class SplitsTaxaFilter extends DataTaxaFilter<SplitsBlock, SplitsBlock> {

	public SplitsTaxaFilter() {
		super(SplitsBlock.class, SplitsBlock.class);
	}

	@Override
	public void filter(ProgressListener progress, TaxaBlock originalTaxaBlock, TaxaBlock modifiedTaxaBlock, SplitsBlock inputData, SplitsBlock outputData) throws IOException {
		if (originalTaxaBlock.getTaxa().equals(modifiedTaxaBlock.getTaxa())) {
			outputData.copy(inputData);
			outputData.setCycle(inputData.getCycle());
			outputData.setCompatibility(inputData.getCompatibility());
			setShortDescription("using all " + modifiedTaxaBlock.size() + " taxa");
		} else {
			progress.setMaximum(inputData.getNsplits());
			final Map<Integer, Integer> originalIndex2ModifiedIndex = originalTaxaBlock.computeIndexMap(modifiedTaxaBlock);
			for (ASplit split : inputData.getSplits()) {
				ASplit induced = computeInducedSplit(split, originalIndex2ModifiedIndex, modifiedTaxaBlock.getNtax());
				if (induced != null)
					outputData.getSplits().add(induced);
				progress.incrementProgress();
			}
			outputData.setCycle(computeInducedCycle(inputData.getCycle(), originalIndex2ModifiedIndex, modifiedTaxaBlock.getNtax()));
			outputData.setCompatibility(Compatibility.compute(modifiedTaxaBlock.getNtax(), outputData.getSplits(), outputData.getCycle()));
			setShortDescription("using " + modifiedTaxaBlock.size() + " of " + originalTaxaBlock.size() + " taxa");
		}
		outputData.setFit(inputData.getFit());
		outputData.setThreshold(inputData.getThreshold());
		outputData.setPartial(inputData.isPartial());
	}

	/**
	 * compute an induced split
	 *
	 * @return induced split or null
	 */
	private static ASplit computeInducedSplit(ASplit originalSplit, Map<Integer, Integer> originalIndex2ModifiedIndex, int inducedNtax) {
		final BitSet originalA = originalSplit.getA();

		final BitSet inducedA = new BitSet();
		for (int t = originalA.nextSetBit(0); t != -1; t = originalA.nextSetBit(t + 1)) {
			if (originalIndex2ModifiedIndex.containsKey(t))
				inducedA.set(originalIndex2ModifiedIndex.get(t));
		}
		if (inducedA.cardinality() < inducedNtax) {
			return new ASplit(inducedA, inducedNtax, originalSplit.getWeight());
		} else
			return null;
	}

	private static int[] computeInducedCycle(int[] originalCycle, Map<Integer, Integer> originalIndex2ModifiedIndex, int inducedNtax) {
		final int[] cycle = new int[inducedNtax + 1];

		int i = 1;
		for (int originalI : originalCycle) {
			if (originalIndex2ModifiedIndex.containsKey(originalI)) {
				cycle[i++] = originalIndex2ModifiedIndex.get(originalI);
			}
		}
		return cycle;
	}
}

/*
 * CredibilityNetwork.java Copyright (C) 2022 Daniel H. Huson
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

package splitstree6.algorithms.trees.trees2splits;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import jloda.util.progress.ProgressListener;
import splitstree6.algorithms.splits.splits2splits.DimensionFilter;
import splitstree6.algorithms.utils.SplitMatrix;
import splitstree6.algorithms.utils.SplitsUtilities;
import splitstree6.data.SplitsBlock;
import splitstree6.data.TaxaBlock;
import splitstree6.data.TreesBlock;
import splitstree6.data.parts.ASplit;
import splitstree6.data.parts.Compatibility;

import java.io.IOException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * Implements a credibility network networks using Beran's algorithm
 * <p>
 * Created on 07.06.2017
 *
 * @author Daniel Huson and David Bryant
 */

public class CredibilityNetwork extends Trees2Splits {

	private final DoubleProperty optionLevel = new SimpleDoubleProperty(this, "optionLevel", .95);
	private final BooleanProperty optionHighDimensionFilter = new SimpleBooleanProperty(this, "optionHighDimensionFilter", true);

	@Override
	public String getCitation() {
		return "Huson and Bryant 2006; " +
			   "Daniel H. Huson and David Bryant. Application of Phylogenetic Networks in Evolutionary Studies. " +
			   "Mol. Biol. Evol. 23(2):254–267. 2006";
	}

	@Override
	public List<String> listOptions() {
		return List.of(optionLevel.getName(), optionHighDimensionFilter.getName());
	}

	@Override
	public String getToolTip(String optionName) {
		if (optionName.equals(optionLevel.getName())) {
			return "Set the level";
		}
		return optionName;
	}

	@Override
	public void compute(ProgressListener progress, TaxaBlock taxaBlock, TreesBlock treesBlock, SplitsBlock splitsBlock) throws IOException {
		final var nTax = taxaBlock.getNtax();
		final var splitMatrix = new SplitMatrix(progress, taxaBlock, treesBlock);
		final var nSplits = splitMatrix.getNsplits();   //Number of splits.... |U| in Beran 88
		final var nBlocks = splitMatrix.getNblocks();  //the value jn in Beran 88

		final var row = new DoubleInt[nBlocks + 1];  //Row vector, indexed 1...nblocks
		final var maxH = new int[nBlocks + 1];
		final var medians = new double[nSplits + 1];

		for (var i = 1; i <= nSplits; i++) {
			for (var j = 1; j <= nBlocks; j++) {
				var x = splitMatrix.get(i, j);
				row[j] = new DoubleInt(x, j);
			}

			//Find the median value.
			Arrays.sort(row, 1, row.length, Comparator.comparingDouble(x -> x.Rij));

			var mid = (int) Math.floor(((double) nBlocks - 1.0) / 2.0) + 1;
			double median;
			if (nBlocks % 2 == 0)
				median = (row[mid].Rij + row[mid + 1].Rij) / 2.0;
			else
				median = row[mid].Rij;
			//Save the median for later.
			medians[i] = median;

			//The "root" value is the abs diff between value and median.
			for (int j = 1; j <= nBlocks; j++) {
				row[j] = new DoubleInt(Math.abs(row[j].Rij - median), j);
			}
			//Now for each entry j, we count the number of entries k such that
			//Rik <= Rij. For this, we first sort R.
			Arrays.sort(row, 1, row.length, Comparator.comparingDouble(x -> x.Rij));

			var count = nBlocks;
			var val = row[nBlocks].Rij;
			for (var k = nBlocks; k >= 1; k--) {
				var x = row[k];
				if (k < nBlocks && x.Rij < val)
					count = k + 1;
				val = x.Rij;
				//int Hij = count;
				maxH[x.j] = Math.max(maxH[x.j], count);
			}
		}

		//We now have, for each j, that choosing a cut off of maxH[j] or more
		//means that all the splits in that column will get included.
		//We'd like to find as small a value K as possible so that maxH[j] <= K
		//for at least level * nblocks of the j's.
		Arrays.sort(maxH);
		var n = (int) Math.ceil(getOptionLevel() * nBlocks);
		var cutoffH = maxH[n];

		//Now go through the splits again, this time computing the values
		//NOTE: in this version we do extra calculations (sorting) here in order
		//to reduce memory usage.

		var computedSplits = new SplitsBlock();

		for (var i = 1; i <= nSplits; i++) {
			var median = medians[i];
			for (int j = 1; j <= nBlocks; j++) {
				double x = splitMatrix.get(i, j);
				row[j] = new DoubleInt(Math.abs(x - median), j);
			}

			//Find the cutoff value.
			Arrays.sort(row, 1, row.length, Comparator.comparingDouble(x -> x.Rij));
			var cutoffRij = row[cutoffH].Rij;
			var low = median - cutoffRij;
			var high = median + cutoffRij;
			var sp = splitMatrix.getSplit(i - 1);
			if (high > 0.0) {
				var split = new ASplit(sp, nTax, (float) median, 0);
				//newSplits.getSplits().add(sp, (float) median, 0, new Interval(low, high), "");
				computedSplits.getSplits().add(split);
			}
		}

		splitsBlock.clear();
		if (isOptionHighDimensionFilter()) {
			var dimensionsFilter = new DimensionFilter();
			dimensionsFilter.apply(progress, 4, computedSplits.getSplits(), splitsBlock.getSplits());
		} else
			splitsBlock.copy(computedSplits);

		SplitsUtilities.verifySplits(splitsBlock.getSplits(), taxaBlock);

		splitsBlock.setCycle(SplitsUtilities.computeCycle(taxaBlock.getNtax(), splitsBlock.getSplits()));
		splitsBlock.setFit(-1);
		splitsBlock.setCompatibility(Compatibility.compute(taxaBlock.getNtax(), splitsBlock.getSplits(), splitsBlock.getCycle()));
	}

	@Override
	public boolean isApplicable(TaxaBlock taxaBlock, TreesBlock parent) {
		return !parent.isPartial() && !parent.isReticulated();
	}

	public double getOptionLevel() {
		return optionLevel.get();
	}

	public DoubleProperty optionLevelProperty() {
		return optionLevel;
	}

	public void setOptionLevel(double optionLevel) {
		this.optionLevel.set(optionLevel);
	}

	public boolean isOptionHighDimensionFilter() {
		return optionHighDimensionFilter.get();
	}

	public BooleanProperty optionHighDimensionFilterProperty() {
		return optionHighDimensionFilter;
	}

	public void setOptionHighDimensionFilter(boolean optionHighDimensionFilter) {
		this.optionHighDimensionFilter.set(optionHighDimensionFilter);
	}

	static private record DoubleInt(double Rij, int j) {
	}
}

/*
 * BalancedConfidenceNetwork.java Copyright (C) 2021. Daniel H. Huson
 *
 * (Some code written by other authors, as named in code.)
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
 *
 */

package splitstree6.algorithms.trees.trees2splits;

import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import jloda.util.ProgressListener;
import splitstree6.algorithms.utils.ConfidenceNetwork;
import splitstree6.data.SplitsBlock;
import splitstree6.data.TaxaBlock;
import splitstree6.data.TreesBlock;
import splitstree6.utils.SplitMatrix;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

/**
 * Implements confidence networks using Beran's algorithm
 * <p>
 * Created on 07.06.2017
 *
 * @author Daniel Huson and David Bryant
 */

public class BalancedConfidenceNetwork extends Trees2Splits {

	private final DoubleProperty optionLevel = new SimpleDoubleProperty(this, "Level", .95);

	@Override
	public String getCitation() {
		return "Huson and Bryant 2006; " +
				"Daniel H. Huson and David Bryant. Application of Phylogenetic Networks in Evolutionary Studies. " +
				"Mol. Biol. Evol. 23(2):254–267. 2006";
	}

	@Override
	public List<String> listOptions() {
		return Collections.singletonList("Level");
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
		progress.setMaximum(100);
		final SplitMatrix M = new SplitMatrix(treesBlock, taxaBlock);
		M.print();
		splitsBlock.copy(ConfidenceNetwork.getConfidenceNetwork(M, getOptionLevel(), taxaBlock.getNtax(), progress));
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

	@Override
	public boolean isApplicable(TaxaBlock taxaBlock, TreesBlock parent) {
		return !parent.isPartial();
	}
}

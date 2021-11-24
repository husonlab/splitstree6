/*
 * TreeSelectorSplits.java Copyright (C) 2021. Daniel H. Huson
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

import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;
import jloda.phylo.PhyloTree;
import jloda.util.progress.ProgressListener;
import splitstree6.algorithms.utils.SplitsUtilities;
import splitstree6.algorithms.utils.TreesUtilities;
import splitstree6.data.SplitsBlock;
import splitstree6.data.TaxaBlock;
import splitstree6.data.TreesBlock;
import splitstree6.data.parts.Compatibility;

import java.io.IOException;
import java.util.BitSet;
import java.util.Collections;
import java.util.List;

/**
 * Obtains splits from a selected tree
 * <p>
 * Created on 20.01.2017, original version: 2005
 *
 * @author Daniel Huson
 */
public class TreeSelectorSplits extends Trees2Splits {
	private final IntegerProperty optionWhich = new SimpleIntegerProperty(this, "Which", 1); // which tree is selected?

	@Override
	public List<String> listOptions() {
		return Collections.singletonList("Which");
	}

	@Override
	public String getToolTip(String optionName) {
		if (optionName.equals(optionWhich.getName())) {
			return "Which tree to use";
		}
		return optionName;
	}

	@Override
	public void compute(ProgressListener progress, TaxaBlock taxaBlock, TreesBlock trees, SplitsBlock splits) throws IOException {
		progress.setTasks("Tree selector", "Init.");

		if (getOptionWhich() < 1)
			setOptionWhich(1);
		if (getOptionWhich() > trees.getNTrees())
			setOptionWhich(trees.getNTrees());

		if (trees.getNTrees() == 0)
			return;

		final PhyloTree tree = trees.getTrees().get(getOptionWhich() - 1);

		if (tree.getNumberOfNodes() == 0)
			return;

		progress.setTasks("TreeSelectorSplits", "Extracting splits");
		progress.incrementProgress();

		final BitSet taxaInTree = TreesUtilities.computeSplits(null, tree, splits.getSplits());

		splits.setPartial(taxaInTree.cardinality() < taxaBlock.getNtax());
		splits.setCompatibility(Compatibility.compatible);
		splits.setCycle(SplitsUtilities.computeCycle(taxaBlock.size(), splits.getSplits()));

		SplitsUtilities.verifySplits(splits.getSplits(), taxaBlock);
		progress.close();
	}

	@Override
	public boolean isApplicable(TaxaBlock taxaBlock, TreesBlock parent) {
		return 1 <= getOptionWhich() && getOptionWhich() <= parent.getTrees().size() && !parent.isPartial();
	}

	public int getOptionWhich() {
		return optionWhich.get();
	}

	public IntegerProperty optionWhichProperty() {
		return optionWhich;
	}

	public void setOptionWhich(int optionWhich) {
		this.optionWhich.set(optionWhich);
	}

	@Override
	public String getShortDescription() {
		return "which=" + getOptionWhich();
	}
}
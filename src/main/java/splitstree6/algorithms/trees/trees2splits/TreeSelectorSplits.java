/*
 * TreeSelectorSplits.java Copyright (C) 2024 Daniel H. Huson
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

import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;
import jloda.phylo.PhyloTree;
import jloda.util.progress.ProgressListener;
import splitstree6.algorithms.utils.SplitsBlockUtilities;
import splitstree6.data.SplitsBlock;
import splitstree6.data.TaxaBlock;
import splitstree6.data.TreesBlock;
import splitstree6.splits.Compatibility;
import splitstree6.splits.SplitUtils;

import java.io.IOException;
import java.util.BitSet;
import java.util.List;

/**
 * Obtains splits from a selected tree
 * <p>
 * Created on 20.01.2017, original version: 2005
 *
 * @author Daniel Huson
 */
public class TreeSelectorSplits extends Trees2Splits {
	private final IntegerProperty optionWhich = new SimpleIntegerProperty(this, "optionWhich", 1); // which tree is selected?

	@Override
	public List<String> listOptions() {
		return List.of(optionWhich.getName());
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
		if (getOptionWhich() < 1)
			setOptionWhich(1);
		if (getOptionWhich() > trees.getNTrees())
			setOptionWhich(trees.getNTrees());

		if (trees.getNTrees() == 0)
			return;

		final PhyloTree tree = trees.getTrees().get(getOptionWhich() - 1);

		if (tree.getNumberOfNodes() == 0)
			return;

		setShortDescription("using tree " + getOptionWhich() + " of " + trees.size() + " trees");

		progress.setTasks("TreeSelectorSplits", "Extracting splits");
		progress.incrementProgress();

		final BitSet taxaInTree = SplitUtils.computeSplits(null, tree, splits.getSplits());

		splits.setPartial(taxaInTree.cardinality() < taxaBlock.getNtax());
		splits.setCompatibility(Compatibility.compatible);
		splits.setCycle(SplitsBlockUtilities.computeCycle(taxaBlock.size(), splits.getSplits()));

		SplitsBlockUtilities.verifySplits(splits.getSplits(), taxaBlock);
		progress.close();
	}

	@Override
	public boolean isApplicable(TaxaBlock taxaBlock, TreesBlock parent) {
		return 1 <= getOptionWhich() && getOptionWhich() <= parent.getTrees().size() && !parent.isPartial();
	}

	public int getOptionWhich() {
		return optionWhich.get();
	}

	public void setOptionWhich(int optionWhich) {
		this.optionWhich.set(optionWhich);
	}

	public IntegerProperty optionWhichProperty() {
		return optionWhich;
	}
}

/*
 * TreesFilter.java Copyright (C) 2021. Daniel H. Huson
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

package splitstree6.algorithms.trees.trees2trees;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import jloda.phylo.PhyloTree;
import jloda.util.ProgressListener;
import splitstree6.algorithms.IFilter;
import splitstree6.data.TaxaBlock;
import splitstree6.data.TreesBlock;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Trees filter
 * Daniel Huson, 12/31/16.
 */
public class TreesFilter extends Trees2Trees implements IFilter {
	private final ObservableList<String> optionEnabledTrees = FXCollections.observableArrayList();
	private final ObservableList<String> OptionDisabledTrees = FXCollections.observableArrayList();

	@Override
	public String getToolTip(String optionName) {
		switch (optionName) {
			case "EnabledTrees":
				return "List of trees currently enabled";
			case "DisabledTrees":
				return "List of trees currently disabled";
			default:
				return optionName;
		}
	}

	@Override
	public void compute(ProgressListener progress, TaxaBlock taxaBlock, TreesBlock parent, TreesBlock child) throws IOException {
		if (optionEnabledTrees.size() == 0 && OptionDisabledTrees.size() == 0) // nothing has been explicitly set, copy everything
		{
			progress.setMaximum(1);
			child.getTrees().setAll(parent.getTrees());
			child.setPartial(parent.isPartial()); // if trees subselected, recompute this!
			progress.incrementProgress();
		} else {
			final int totalTaxa = taxaBlock.getNtax();
			boolean partial = false;

			progress.setMaximum(optionEnabledTrees.size());
			final Map<String, PhyloTree> name2tree = new HashMap<>();
			for (PhyloTree tree : parent.getTrees()) {
				name2tree.put(tree.getName(), tree);
			}
			for (String name : optionEnabledTrees) {
				if (!OptionDisabledTrees.contains(name)) {
					final PhyloTree tree = name2tree.get(name);
					child.getTrees().add(tree);
					if (tree.getNumberOfTaxa() != totalTaxa)
						partial = true;
					progress.incrementProgress();
				}
			}
			child.setPartial(partial);
		}
		if (OptionDisabledTrees.size() == 0)
			setShortDescription("using all " + parent.size() + " trees");
		else
			setShortDescription("using " + optionEnabledTrees.size() + " of " + parent.size() + " trees");

		child.setRooted(parent.isRooted());
	}

	@Override
	public void clear() {
		optionEnabledTrees.clear();
		OptionDisabledTrees.clear();
	}

	public ObservableList<String> getOptionEnabledTrees() {
		return optionEnabledTrees;
	}

	public ObservableList<String> getOptionDisabledTrees() {
		return OptionDisabledTrees;
	}

	@Override
	public boolean isActive() {
		return OptionDisabledTrees.size() > 0;
	}
}

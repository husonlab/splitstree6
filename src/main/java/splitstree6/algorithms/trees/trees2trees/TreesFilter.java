/*
 * TreesFilter.java Copyright (C) 2024 Daniel H. Huson
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

package splitstree6.algorithms.trees.trees2trees;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import jloda.util.progress.ProgressListener;
import splitstree6.algorithms.IFilter;
import splitstree6.data.TaxaBlock;
import splitstree6.data.TreesBlock;

import java.io.IOException;
import java.util.HashSet;

/**
 * Trees filter
 * Daniel Huson, 12/31/16, 2/2/2022
 */
public class TreesFilter extends Trees2Trees implements IFilter {
	private final ObservableList<String> OptionDisabledTrees = FXCollections.observableArrayList();

	@Override
	public String getToolTip(String optionName) {
		return switch (optionName) {
			case "optionDisabledTrees" -> "List of trees currently disabled";
			default -> optionName;
		};
	}

	@Override
	public void compute(ProgressListener progress, TaxaBlock taxaBlock, TreesBlock parent, TreesBlock child) throws IOException {
		final var totalTaxa = taxaBlock.getNtax();
		var partial = false;

		progress.setMaximum(parent.getNTrees() - getOptionDisabledTrees().size());

		var disabledSet = new HashSet<>(getOptionDisabledTrees());

		for (var tree : parent.getTrees()) {
			if (!disabledSet.contains(tree.getName())) {
				child.getTrees().add(tree);
				if (tree.getNumberOfTaxa() != totalTaxa)
					partial = true;
				progress.incrementProgress();
			}
		}
		child.setPartial(partial);

		if (OptionDisabledTrees.size() == 0)
			setShortDescription("using all " + parent.size() + " trees");
		else
			setShortDescription("using " + child.size() + " of " + parent.size() + " trees");

		child.setRooted(parent.isRooted());
	}

	@Override
	public void clear() {
		OptionDisabledTrees.clear();
	}

	public ObservableList<String> getOptionDisabledTrees() {
		return OptionDisabledTrees;
	}

	@Override
	public boolean isActive() {
		return OptionDisabledTrees.size() > 0;
	}
}

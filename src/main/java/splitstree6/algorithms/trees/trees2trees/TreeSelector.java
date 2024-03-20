/*
 *  TreeSelector.java Copyright (C) 2024 Daniel H. Huson
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

package splitstree6.algorithms.trees.trees2trees;

import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;
import jloda.util.progress.ProgressListener;
import splitstree6.algorithms.IFilter;
import splitstree6.data.TaxaBlock;
import splitstree6.data.TreesBlock;

import java.util.List;

/**
 * Tree selector
 * Daniel Huson, 1/2018
 */
public class TreeSelector extends Trees2Trees implements IFilter {
	private final IntegerProperty optionWhich = new SimpleIntegerProperty(this, "optionWhich", 1); // 1-based

	@Override
	public List<String> listOptions() {
		return List.of(optionWhich.getName());
	}

	@Override
	public String getToolTip(String optionName) {
		if (!optionName.startsWith("option"))
			optionName = "option" + optionName;
		if (optionName.equals(optionWhich.getName())) {
			return "Which tree to use";
		} else
			return super.getToolTip(optionName);
	}

	@Override
	public String getShortDescription() {
		return "Allows the user to select one from a list of trees.";
	}

	@Override
	public void compute(ProgressListener progress, TaxaBlock ignored, TreesBlock parent, TreesBlock child) {
		setOptionWhich(Math.max(1, Math.min(parent.size(), optionWhich.get())));
		child.getTrees().add(parent.getTree(getOptionWhich()));
		child.setRooted(parent.isRooted());
		child.setPartial(parent.isPartial());
		child.setReticulated(parent.getTree(getOptionWhich()).isReticulated());
		setShortDescription("using tree " + getOptionWhich() + " of " + parent.size() + " trees");
	}

	@Override
	public void clear() {
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
	public boolean isActive() {
		return optionWhich.get() > 0;
	}
}

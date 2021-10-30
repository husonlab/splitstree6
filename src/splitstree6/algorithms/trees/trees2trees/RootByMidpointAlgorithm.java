/*
 * RootByMidpointAlgorithm.java Copyright (C) 2021. Daniel H. Huson
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


import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import jloda.phylo.PhyloTree;
import jloda.util.progress.ProgressListener;
import splitstree6.algorithms.IFilter;
import splitstree6.algorithms.utils.RerootingUtils;
import splitstree6.data.TaxaBlock;
import splitstree6.data.TreesBlock;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

/**
 * tree rerooting by midpoint
 * Daniel Huson, 5.2018
 */
public class RootByMidpointAlgorithm extends Trees2Trees implements IFilter {
	private final BooleanProperty optionUseMidpoint = new SimpleBooleanProperty(true);

	@Override
	public List<String> listOptions() {
		return Collections.singletonList("UseMidpoint");
	}

	@Override
	public String getToolTip(String optionName) {
		if ("UseMidpoint".equals(optionName)) {
			return "Determine whether to computeCycle midpoint rooting";
		}
		return optionName;
	}

	@Override
	public void compute(ProgressListener progress, TaxaBlock taxa, TreesBlock parent, TreesBlock child) throws IOException {
		if (!isOptionUseMidpoint()) // nothing has been explicitly set, copy everything
		{
			child.getTrees().setAll(parent.getTrees());
		} else { // reroot using outgroup
			child.getTrees().clear();

			for (PhyloTree orig : parent.getTrees()) {
				final PhyloTree tree = new PhyloTree();
				tree.copy(orig);
				if (tree.getRoot() == null) {
					tree.setRoot(tree.getFirstNode());
					tree.redirectEdgesAwayFromRoot();
				}
				// todo: ask about internal node labels
				RerootingUtils.rerootByMidpoint(false, tree);
				child.getTrees().add(tree);
			}
		}

		setShortDescription(isOptionUseMidpoint() ? "using midpoint rooting" : "not using midpoint rooting");
	}


	@Override
	public boolean isActive() {
		return isOptionUseMidpoint();
	}

	public boolean isOptionUseMidpoint() {
		return optionUseMidpoint.get();
	}

	public BooleanProperty optionUseMidpointProperty() {
		return optionUseMidpoint;
	}

	public void setOptionUseMidpoint(boolean optionUseMidpoint) {
		this.optionUseMidpoint.set(optionUseMidpoint);
	}
}

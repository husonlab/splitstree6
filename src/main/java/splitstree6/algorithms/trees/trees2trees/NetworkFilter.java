/*
 *  NetworkFilter.java Copyright (C) 2024 Daniel H. Huson
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

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import jloda.phylo.PhyloTree;
import jloda.phylo.algorithms.RootedNetworkProperties;
import jloda.util.progress.ProgressListener;
import splitstree6.algorithms.IFilter;
import splitstree6.data.TaxaBlock;
import splitstree6.data.TreesBlock;

import java.io.IOException;
import java.util.List;

/**
 * filters rooted networks by their topological class
 * Daniel Huson, 8.2026
 */
public class NetworkFilter extends Trees2Trees implements IFilter {
	/**
	 * the network classes to keep. The classes are nested: every Tree is Normal, every Normal network is
	 * Tree-Child, and every Tree-Child network is Tree-Based, so e.g. TreeChildNetwork also keeps trees and
	 * normal networks.
	 */
	public enum FilterBy {Tree, NormalNetwork, TreeChildNetwork, TreeBasedNetwork, None}

	private final ObjectProperty<FilterBy> optionFilterBy = new SimpleObjectProperty<>(this, "optionFilterBy", FilterBy.None);

	@Override
	public List<String> listOptions() {
		return List.of(optionFilterBy.getName());
	}

	@Override
	public String getToolTip(String optionName) {
		return switch (optionName) {
			case "optionFilterBy" ->
					"Keep only phylogenies of the selected class: Tree (no reticulations), normal network, tree-child Network, tree-based-network or None (unfiltered)";
			default -> super.getToolTip(optionName);
		};
	}

	@Override
	public String getShortDescription() {
		return "Filters rooted networks by their topological class (tree, normal, tree-child, tree-based).";
	}

	@Override
	public boolean isApplicable(TaxaBlock taxa, TreesBlock datablock) {
		return datablock.isReticulated();
	}

	@Override
	public void compute(ProgressListener progress, TaxaBlock taxaBlock, TreesBlock parent, TreesBlock child) throws IOException {
		child.setPartial(parent.isPartial());
		child.setRooted(parent.isRooted());

		if (!isActive()) {
			child.getTrees().addAll(parent.getTrees());
		} else {
			final var filterBy = getOptionFilterBy();
			progress.setTasks("Network Filter", "Selecting " + filterBy + " networks");
			progress.setMaximum(parent.getNTrees());
			progress.setProgress(0);
			for (var t = 1; t <= parent.getNTrees(); t++) {
				var network = parent.getTree(t);
				if (accepts(filterBy, network))
					child.getTrees().add(network);
				progress.incrementProgress();
			}
		}
		// the kept networks may or may not still contain reticulations (e.g. FilterBy=Tree removes them all)
		child.setReticulated(child.getTrees().stream().anyMatch(PhyloTree::isReticulated));

		if (child.getNTrees() == parent.getNTrees())
			setShortDescription("using all " + parent.size() + " networks");
		else
			setShortDescription("using " + child.size() + " of " + parent.size() + " networks");
	}

	/**
	 * does the given network belong to the selected class?
	 */
	private static boolean accepts(FilterBy filterBy, PhyloTree network) {
		return switch (filterBy) {
			case None -> true;
			case Tree -> !network.isReticulated();
			case NormalNetwork -> RootedNetworkProperties.isNormal(network);
			case TreeChildNetwork -> RootedNetworkProperties.isTreeChild(network);
			case TreeBasedNetwork -> RootedNetworkProperties.isTreeBased(network);
		};
	}

	@Override
	public void clear() {
	}

	@Override
	public boolean isActive() {
		return getOptionFilterBy() != FilterBy.None;
	}

	public FilterBy getOptionFilterBy() {
		return optionFilterBy.get();
	}

	public ObjectProperty<FilterBy> optionFilterByProperty() {
		return optionFilterBy;
	}

	public void setOptionFilterBy(FilterBy optionFilterBy) {
		this.optionFilterBy.set(optionFilterBy);
	}
}

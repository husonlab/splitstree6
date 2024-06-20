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

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import jloda.phylo.PhyloTree;
import jloda.phylo.algorithms.ClusterPoppingAlgorithm;
import jloda.util.BitSetUtils;
import jloda.util.progress.ProgressListener;
import splitstree6.algorithms.IFilter;
import splitstree6.data.TaxaBlock;
import splitstree6.data.TreesBlock;
import splitstree6.utils.TreesUtils;

import java.util.*;

/**
 * Filter trees and rooted networks by unique topologies (hardwired clusters)
 * Daniel Huson, 4/2024
 */
public class UniqueTopologies extends Trees2Trees implements IFilter {

	private final BooleanProperty optionUnrooted = new SimpleBooleanProperty(this, "optionUnrooted", false); // 1-based

	@Override
	public List<String> listOptions() {
		return List.of(optionUnrooted.getName());
	}

	@Override
	public String getToolTip(String optionName) {
		if (!optionName.startsWith("option"))
			optionName = "option" + optionName;
		if (optionName.equals(optionUnrooted.getName())) {
			return "Ignore location of root";
		} else
			return super.getToolTip(optionName);
	}

	@Override
	public String getShortDescription() {
		return "Filters trees or rooted networks returning all unique topologies (using hardwired clusters).";
	}

	@Override
	public String getCitation() {
		return super.getCitation();
	}

	@Override
	public void compute(ProgressListener progress, TaxaBlock taxaBlock, TreesBlock parent, TreesBlock child) {
		child.clear();
		child.setPartial(parent.isPartial());
		child.setRooted(parent.isRooted());
		child.setReticulated(parent.isReticulated());

		var clustersList = new ArrayList<Set<BitSet>>();
		for (var tree : parent.getTrees()) {
			var taxa = BitSetUtils.asBitSet(tree.getTaxa());
			var clusters = TreesUtils.collectAllHardwiredClusters(tree);
			if (isOptionUnrooted()) {
				var min = taxa.stream().min().orElse(1);
				var unrootedClusters = new HashSet<BitSet>();
				for (var cluster : clusters) {
					if (!cluster.get(min)) {
						clusters.add(cluster);
					} else {
						unrootedClusters.add(BitSetUtils.minus(taxa, cluster));
					}
				}
				clusters = unrootedClusters;
			}
			var found = false;
			for (var other : clustersList) {
				if (other.equals(clusters)) {
					found = true;
					break;
				}
			}
			if (!found)
				clustersList.add(clusters);
		}
		for (var clusters : clustersList) {
			var tree = new PhyloTree();
			ClusterPoppingAlgorithm.apply(clusters, tree);
			for (var v : tree.nodes()) {
				if (tree.hasTaxa(v)) {
					var t = tree.getTaxon(v);
					tree.setLabel(v, taxaBlock.getLabel(t));
				}
			}
			child.getTrees().add(tree);
		}
		setShortDescription("Filter by unique topology, from %,d to %,d trees".formatted(parent.getNTrees(), child.getNTrees()));
	}

	@Override
	public boolean isApplicable(TaxaBlock taxa, TreesBlock datablock) {
		return datablock.isRooted();
	}

	@Override
	public boolean isActive() {
		return true;
	}

	public boolean isOptionUnrooted() {
		return optionUnrooted.get();
	}

	public BooleanProperty optionUnrootedProperty() {
		return optionUnrooted;
	}

	public void setOptionUnrooted(boolean optionUnrooted) {
		this.optionUnrooted.set(optionUnrooted);
	}
}

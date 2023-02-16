/*
 *  PhylogeneticDiversity.java Copyright (C) 2023 Daniel H. Huson
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

package splitstree6.analysis;

import jloda.phylo.PhyloTree;
import splitstree6.algorithms.utils.TreesUtilities;
import splitstree6.data.SplitsBlock;
import splitstree6.data.parts.ASplit;

import java.util.BitSet;

/**
 * compute the phylogenetic diversity for a set of selected taxa in a rooted tree or network
 * Daniel Huson, 2.2023
 */
public class PhylogeneticDiversity {
	/**
	 * computes the phylogenetic diversity
	 *
	 * @param tree a rooted tree or network
	 * @param taxa the selected taxa
	 * @return phylogenetic diversity
	 */
	public static double apply(PhyloTree tree, BitSet taxa) {
		if (taxa.cardinality() == 0) {
			return 0.0;
		} else {
			try (var nodeClusterMap = TreesUtilities.extractClusters(tree)) {
				return tree.edgeStream().filter(e -> nodeClusterMap.get(e.getTarget()).intersects(taxa)).mapToDouble(tree::getWeight).sum();
			}
		}
	}

	/**
	 * compute the phylogenetic diversity of a set of taxa for a given set of splits
	 *
	 * @param splits the splits
	 * @param taxa   the taxa
	 * @return phylogenetic diversity
	 */
	public static double apply(SplitsBlock splits, BitSet taxa) {
		return splits.getSplits().stream().filter(s -> s.getA().intersects(taxa) && s.getB().intersects(taxa))
				.mapToDouble(ASplit::getWeight).sum();
	}
}

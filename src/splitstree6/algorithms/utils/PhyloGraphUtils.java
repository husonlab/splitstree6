/*
 * PhyloGraphUtils.java Copyright (C) 2023 Daniel H. Huson
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

package splitstree6.algorithms.utils;

import jloda.graph.Node;
import jloda.phylo.PhyloGraph;
import splitstree6.data.TaxaBlock;

import java.util.function.Function;

/**
 * some phylograph utilities
 * Daniel Huson, 2.2019
 */
public class PhyloGraphUtils {
	public static void addLabels(TaxaBlock taxaBlock, PhyloGraph graph) {
		addLabels(taxaBlock::getLabel, graph);
	}

	/**
	 * add labels to graph
	 */
	public static void addLabels(Function<Integer, String> taxonLabel, PhyloGraph graph) {
		// remove labels for taxon nodes, in case some algorithm has already been applied
		for (int t = 1; t <= graph.getNumberOfTaxa(); t++) {
			final Node v = graph.getTaxon2Node(t);
			graph.setLabel(v, null);
		}

		for (int t = 1; t <= graph.getNumberOfTaxa(); t++) {
			final Node v = graph.getTaxon2Node(t);
			if (graph.getLabel(v) == null)
				graph.setLabel(v, taxonLabel.apply(t));
			else
				graph.setLabel(v, graph.getLabel(v) + ", " + taxonLabel.apply(t));
		}
	}
}

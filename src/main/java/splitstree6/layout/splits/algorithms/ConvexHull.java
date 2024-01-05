/*
 * ConvexHull.java Copyright (C) 2024 Daniel H. Huson
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

package splitstree6.layout.splits.algorithms;

import jloda.phylo.PhyloSplitsGraph;
import jloda.util.CanceledException;
import jloda.util.progress.ProgressListener;
import splitstree6.data.SplitsBlock;
import splitstree6.data.TaxaBlock;

import java.util.BitSet;

/**
 * applies the convex hull algorithm to build a split network from splits
 * Daniel Huson, 11.2017
 */
public class ConvexHull {
	/**
	 * update the algorithm to build a new graph
	 */
	public static void apply(ProgressListener progress, TaxaBlock taxaBlock, SplitsBlock splitsBlock, PhyloSplitsGraph graph) throws CanceledException {
		graph.clear();
		apply(progress, taxaBlock, splitsBlock, graph, new BitSet());
	}

	/**
	 * assume that some splits have already been processed and applies convex hull algorithm to remaining splits
	 */
	public static void apply(ProgressListener progress, TaxaBlock taxaBlock, SplitsBlock splits, PhyloSplitsGraph graph, BitSet usedSplits) throws CanceledException {
		splitstree6.splits.ConvexHull.apply(progress, taxaBlock.getNtax(), taxaBlock::getLabel, splits.getSplits(), graph, usedSplits);
	}
}

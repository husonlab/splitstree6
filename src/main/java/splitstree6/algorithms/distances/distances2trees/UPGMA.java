/*
 *  UPGMA.java Copyright (C) 2026 Daniel H. Huson
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

package splitstree6.algorithms.distances.distances2trees;

import jloda.graph.Node;
import jloda.phylo.PhyloTree;
import jloda.util.BitSetUtils;
import jloda.util.CanceledException;
import jloda.util.progress.ProgressListener;
import splitstree6.algorithms.trees.IToSingleTree;
import splitstree6.data.DistancesBlock;
import splitstree6.data.TaxaBlock;
import splitstree6.data.TreesBlock;

import java.io.IOException;
import java.util.BitSet;

/**
 * UPGMA, the unweighted pair group method with arithmetic mean (Sokal and Michener 1958): average-linkage
 * clustering that repeatedly merges the two closest clusters, producing a rooted ultrametric tree
 * Daniel Huson, 11.2026
 */
public class UPGMA extends Distances2Trees implements IToSingleTree {
	@Override
	public String getCitation() {
		return "Sokal & Michener 1958; " +
			   "R.R. Sokal and C.D.Michener. A statistical method for evaluating systematic relationships. " +
			   "University of Kansas Scientific Bulletin, 28:1409-1438, 1958.";
	}

	@Override
	public String getShortDescription() {
		return "Computes a rooted phylogenetic tree using the UPGMA method.";
	}

	@Override
	public void compute(ProgressListener progress, TaxaBlock taxaBlock, DistancesBlock distances, TreesBlock trees) throws IOException {
		trees.setPartial(false);
		trees.setRooted(true);
		trees.getTrees().setAll(computeUPGMATree(progress, taxaBlock, distances));
	}

	/**
	 * computes the UPGMA tree
	 *
	 * @param progress  progress listener
	 * @param taxaBlock taxa
	 * @param distances distances
	 * @return rooted ultrametric tree
	 */
	public static PhyloTree computeUPGMATree(ProgressListener progress, TaxaBlock taxaBlock, DistancesBlock distances) throws CanceledException {
		var ntax = distances.getNtax();
		var tree = new PhyloTree();
		tree.setName("UPGMA-tree");

		// each cluster is represented by the root of its subtree, the number of taxa in it and its height above the leaves
		var roots = new Node[ntax + 1];
		var sizes = new int[ntax + 1];
		var heights = new double[ntax + 1];
		var clusters = new BitSet();
		for (var t = 1; t <= ntax; t++) {
			var v = tree.newNode();
			tree.setLabel(v, taxaBlock.getLabel(t));
			tree.addTaxon(v, t);
			roots[t] = v;
			sizes[t] = 1;
			clusters.set(t);
		}

		// average distances between clusters, initially the given distances
		var d = new double[ntax + 1][ntax + 1];
		for (var i = 1; i <= ntax; i++) {
			for (var j = i + 1; j <= ntax; j++) {
				d[i][j] = d[j][i] = symmetrize(distances.get(i, j), distances.get(j, i));
			}
		}

		progress.setMaximum(ntax);
		progress.setProgress(0);

		while (clusters.cardinality() > 1) {
			// find the two closest clusters, taking the first pair in index order if there are several
			var bestI = -1;
			var bestJ = -1;
			var best = Double.POSITIVE_INFINITY;
			for (var i : BitSetUtils.members(clusters)) {
				for (var j : BitSetUtils.members(clusters, i + 1)) {
					if (bestI == -1 || d[i][j] < best) {
						bestI = i;
						bestJ = j;
						best = d[i][j];
					}
				}
			}

			// the new cluster sits at half the distance between its two parts and each part hangs below it by the
			// difference in height, which is never negative because average linkage produces no inversions
			var height = 0.5 * best;
			var u = tree.newNode();
			tree.setWeight(tree.newEdge(u, roots[bestI]), Math.max(0.0, height - heights[bestI]));
			tree.setWeight(tree.newEdge(u, roots[bestJ]), Math.max(0.0, height - heights[bestJ]));

			// the distance from any other cluster to the merged one is the size-weighted mean of its distances to the two parts
			for (var k : BitSetUtils.members(clusters)) {
				if (k != bestI && k != bestJ) {
					d[bestI][k] = d[k][bestI] = (sizes[bestI] * d[bestI][k] + sizes[bestJ] * d[bestJ][k]) / (sizes[bestI] + sizes[bestJ]);
				}
			}
			roots[bestI] = u;
			sizes[bestI] += sizes[bestJ];
			heights[bestI] = height;
			clusters.clear(bestJ);

			progress.incrementProgress();
		}

		if (ntax > 0)
			tree.setRoot(roots[clusters.nextSetBit(1)]);
		progress.setProgress(ntax);
		return tree;
	}

	/**
	 * the input distance between two taxa, averaged over both orientations; a matrix in which only one
	 * triangle has been filled in is accepted
	 */
	private static double symmetrize(double dij, double dji) {
		if (dij == 0.0)
			return dji;
		else if (dji == 0.0)
			return dij;
		else
			return 0.5 * (dij + dji);
	}

	@Override
	public boolean isApplicable(TaxaBlock taxaBlock, DistancesBlock parent) {
		return parent.getNtax() > 0;
	}
}

/*
 * UPGMA.java Copyright (C) 2021. Daniel H. Huson
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

package splitstree6.algorithms.distances.distances2trees;

import jloda.graph.Node;
import jloda.phylo.PhyloTree;
import jloda.util.CanceledException;
import jloda.util.ProgressListener;
import splitstree6.data.DistancesBlock;
import splitstree6.data.TaxaBlock;
import splitstree6.data.TreesBlock;

import java.io.IOException;

/**
 * UPGMA classic n³ version
 * <p>
 * Created on 2010-02-04
 *
 * @author Christian Rausch, David Bryant, Daniel Huson
 */

public class UPGMA extends Distances2Trees {

	@Override
	public String getCitation() {
		return "Sokal and Michener 1958; " +
				"R.R. Sokal and C.D.Michener. A statistical method for evaluating systematic relationships. " +
				"University of Kansas Scientific Bulletin, 28:1409-1438, 1958.";
	}

	@Override
	public void compute(ProgressListener progress, TaxaBlock taxaBlock, DistancesBlock distances, TreesBlock trees) throws IOException {
		trees.setPartial(false);
		trees.setRooted(true);
		trees.getTrees().setAll(computeUPGMATree(progress, taxaBlock, distances));
	}

	/**
	 * compute the UPGMA tree
	 */
	public static PhyloTree computeUPGMATree(ProgressListener progress, TaxaBlock taxaBlock, DistancesBlock distances) throws CanceledException {
		final var tree = new PhyloTree();
		final var ntax = distances.getNtax();

		final var subtrees = new Node[ntax + 1];
		final var sizes = new int[ntax + 1];
		var heights = new double[ntax + 1];

		for (var t = 1; t <= ntax; t++) {
			final var v = tree.newNode();
			subtrees[t] = v;
			tree.setLabel(v, taxaBlock.getLabel(t));
			tree.addTaxon(v, t);
			sizes[t] = 1;
		}

		final var d = new double[ntax + 1][ntax + 1];// distance matix

		//Initialise d
		//Compute the closest values for each taxa.
		for (var i = 1; i <= ntax; i++) {
			for (var j = i + 1; j <= ntax; j++) {
				//d[i][j] = d[j][i] = (distances.get(i, j) + distances.get(j, i)) / 2.0;
				final var sum = distances.get(i, j) + distances.get(j, i);
				if (sum == distances.get(i, j) || sum == distances.get(j, i)) {
					d[i][j] = d[j][i] = sum;
				} else {
					d[i][j] = d[j][i] = sum / 2.0;
				}
			}
		}

		progress.setMaximum(ntax);
		for (var clusters = ntax; clusters > 2; clusters--) {
			var i_min = 0;
			var j_min = 0;
			//Find closest pair.
			double d_min = Double.POSITIVE_INFINITY;
			for (int i = 1; i <= clusters; i++) {
				for (int j = i + 1; j <= clusters; j++) {
					double dij = d[i][j];
					if (i_min == 0 || dij < d_min) {
						i_min = i;
						j_min = j;
						d_min = dij;
					}
				}
			}

			final var height = d_min / 2.0;

			final var v = tree.newNode();
			final var e = tree.newEdge(v, subtrees[i_min]);
			tree.setWeight(e, Math.max(height - heights[i_min], 0.0));
			final var f = tree.newEdge(v, subtrees[j_min]);
			tree.setWeight(f, Math.max(height - heights[j_min], 0.0));

			subtrees[i_min] = v;
			subtrees[j_min] = null;
			heights[i_min] = height;


			final var size_i = sizes[i_min];
			final var size_j = sizes[j_min];
			sizes[i_min] = size_i + size_j;

			for (var k = 1; k <= ntax; k++) {
				if ((k == i_min) || k == j_min) continue;
				final var dki = (d[k][i_min] * size_i + d[k][j_min] * size_j) / ((double) (size_i + size_j));
				d[k][i_min] = d[i_min][k] = dki;
			}

			//Copy the top row of the matrix and arrays into the empty j_min row/column.
			if (j_min < clusters) {
				for (var k = 1; k <= clusters; k++) {
					d[j_min][k] = d[k][j_min] = d[clusters][k];
				}
				d[j_min][j_min] = 0.0;
				subtrees[j_min] = subtrees[clusters];
				sizes[j_min] = sizes[clusters];
				heights[j_min] = heights[clusters];
			}

			progress.incrementProgress();
		}

		final var brother = 1;

		var sister = brother + 1;
		while (subtrees[sister] == null)
			sister++;

		final var root = tree.newNode();
		final var left = tree.newEdge(root, subtrees[brother]);
		final var right = tree.newEdge(root, subtrees[sister]);

		final var halfTotal = 0.5 * (d[brother][sister] + heights[brother] + heights[sister]);
		tree.setWeight(left, halfTotal - heights[brother]);
		tree.setWeight(right, halfTotal - heights[sister]);

		tree.setRoot(root);

		return tree;
	}


	@Override
	public boolean isApplicable(TaxaBlock taxaBlock, DistancesBlock parent) {
		return parent.getNtax() > 0;
	}
}

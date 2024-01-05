/*
 * BioNJ.java Copyright (C) 2024 Daniel H. Huson
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

package splitstree6.algorithms.distances.distances2trees;

import jloda.graph.Node;
import jloda.phylo.PhyloTree;
import jloda.util.CanceledException;
import jloda.util.progress.ProgressListener;
import splitstree6.algorithms.trees.IToSingleTree;
import splitstree6.data.DistancesBlock;
import splitstree6.data.TaxaBlock;
import splitstree6.data.TreesBlock;

import java.io.IOException;
import java.util.BitSet;
import java.util.HashMap;


/**
 * Implementation of the Bio-Neighbor-Joining algorithm (Gascuel 1997)
 * <p>
 * Created on 2008-02-26
 *
 * @author David Bryant and Daniel Huson
 */

public class BioNJ extends Distances2Trees implements IToSingleTree {

	@Override
	public String getCitation() {
		return "Gascuel 1997; " +
			   "O. Gascuel, BIONJ: an improved version of the NJ algorithm based on a simple model of sequence data. " +
			   "Molecular Biology and Evolution. 1997 14:685-695.";
	}

	@Override
	public void compute(ProgressListener progress, TaxaBlock taxaBlock, DistancesBlock distances, TreesBlock trees) throws IOException {
		trees.setPartial(false);
		trees.setRooted(true);
		trees.getTrees().setAll(computeBioNJTree(progress, taxaBlock, distances));
	}

	/**
	 * compute the BIO nj tree
	 */
	public static PhyloTree computeBioNJTree(ProgressListener progress, TaxaBlock taxaBlock, DistancesBlock distances) throws CanceledException {
		final var tree = new PhyloTree();
		tree.setName("BioNJ-tree");
		final var taxaHashMap = new HashMap<String, Node>();
		final var nTax = distances.getNtax();
		final var tax = new StringBuffer[nTax + 1];

		for (var t = 1; t <= nTax; t++) {
			tax[t] = new StringBuffer();
			tax[t].append(t);
			final var v = tree.newNode(); // create newNode for each Taxon
			tree.setLabel(v, taxaBlock.getLabel(t));
			tree.addTaxon(v, t);
			taxaHashMap.put(tax[t].toString(), v);
		}

		if (nTax <= 1)
			return tree;

		final var h = new double[nTax + 1][nTax + 1];// distance matrix

		final var active = new BitSet();

		final var var = new double[nTax + 1][nTax + 1]; // variances matrix. This really should be upper diag of h.
		final var b = new double[nTax + 1];// the b variable in Neighbor Joining

		StringBuilder tax_old_i; //labels of taxa that are being merged
		StringBuilder tax_old_j;
		StringBuilder tax_old_k;

		active.set(1, nTax + 1);

		for (int i = 1; i <= nTax; i++) {
			h[i][i] = 0.0;
			for (int j = 1; j <= nTax; j++) { //fill up the distance matix h
				if (i < j)
					h[i][j] = distances.get(i, j);//
				else
					h[i][j] = distances.get(j, i);
				var[i][j] = h[i][j];
			}
		}

		// calculate b:
		for (int i = 1; i <= nTax; i++) {
			for (int j = 1; j <= nTax; j++) {
				b[i] += h[i][j];
			}
		}
		// recall: int i_min=0, j_min=0;

		// actual for (finding all nearest Neighbors)
		var i_min = 0;
		var j_min = 0;

		progress.setMaximum(nTax);
		for (var actual = nTax; actual > 3; actual--) {
			// find: min D (h, b, b)
			var d_min = Double.POSITIVE_INFINITY;
			for (var i = 1; i < nTax; i++) {
				if (!active.get(i)) continue;
				for (var j = i + 1; j <= nTax; j++) {
					if (!active.get(j))
						continue;
					var d_ij = ((double) actual - 2.0) * h[i][j] - b[i] - b[j];
					if (d_ij < d_min) {
						d_min = d_ij;
						i_min = i;
						j_min = j;
					}
				}
			}
			var dist_e = 0.5 * (h[i_min][j_min] + b[i_min] / ((double) actual - 2.0)
								- b[j_min] / ((double) actual - 2.0));
			var dist_f = h[i_min][j_min] - dist_e;
			//dist_f=0.5*(h[i_min][j_min] + b[j_min]/((double)actual-2.0)
			//	- b[i_min]/((double)actual-2.0) );

			active.set(j_min, false);

			// tax taxa update:
			tax_old_i = new StringBuilder(tax[i_min].toString());
			tax_old_j = new StringBuilder(tax[j_min].toString());
			tax[i_min].insert(0, "(");
			tax[i_min].append(",");
			tax[i_min].append(tax[j_min]);
			tax[i_min].append(")");
			tax[j_min].delete(0, tax[j_min].length());

			// b update:

			b[i_min] = 0.0;
			b[j_min] = 0.0;

			// fusion of h
			// double h_min = h[i_min][j_min];
			double var_min = var[i_min][j_min]; //Variance of the distance between i_min and j_min

			//compute lambda to minimize the variances of the new distances
			double lambda;
			if (var_min == 0.0)
				lambda = 0.5;
			else {
				lambda = 0.0;
				for (var i = 1; i <= nTax; i++) {
					if ((i_min != i) && (j_min != i) && (h[0][i] != 0.0))
						lambda += var[i_min][i] - var[j_min][i];
				}
				lambda = 0.5 + lambda / (2.0 * (actual - 2) * var_min);
				if (lambda < 0.0)
					lambda = 0.0;
				if (lambda > 1.0)
					lambda = 1.0;
			}

			for (var i = 1; i <= nTax; i++) {
				if ((i == i_min) || (!active.get(i)))
					continue;
				//temp=(h[i][i_min] + h[i][j_min] - h_min)/2; NJ                                        //temp=(h[i][i_min] + h[i][j_min] - dist_e - dist_f)/2; NJ
				var temp = (1.0 - lambda) * (h[i][i_min] - dist_e) + (lambda) * (h[i][j_min] - dist_f); //BioNJ

				//  if (i != i_min) // always true
				{
					b[i] = b[i] - h[i][i_min] - h[i][j_min] + temp;
				}
				b[i_min] += temp;
				h[i_min][i] = h[i][i_min] = temp; //WARNING... this can affect updating of b[i]
				//Update variances
				var[i_min][i] = (1.0 - lambda) * var[i_min][i] + (lambda) * var[j_min][i] - lambda * (1.0 - lambda) * var_min;
				var[i][i_min] = var[i_min][i];
			}

			for (var i = 1; i <= nTax; i++) {
				h[i_min][i] = h[i][i_min];
				h[i][j_min] = 0.0;
				h[j_min][i] = 0.0;
			}

			// generate new Node for merged Taxa:
			var v = tree.newNode();
			taxaHashMap.put(tax[i_min].toString(), v);

			// generate Edges from two Taxa that are merged to one:
			var e = tree.newEdge(v, taxaHashMap.get(tax_old_i.toString()));
			tree.setWeight(e, dist_e);
			var f = tree.newEdge(v, taxaHashMap.get(tax_old_j.toString()));
			tree.setWeight(f, dist_f);
			progress.incrementProgress();
		}

		// evaluating last three nodes:
		i_min = active.nextSetBit(1);
		j_min = active.nextSetBit(i_min + 1);
		var k_min = active.nextSetBit(j_min + 1);

		tax_old_i = new StringBuilder(tax[i_min].toString());
		tax_old_j = new StringBuilder(tax[j_min].toString());
		tax_old_k = new StringBuilder(tax[k_min].toString());

		tax[i_min].insert(0, "(");
		tax[i_min].append(",");
		tax[i_min].append(tax[j_min]);
		tax[i_min].append(",");
		tax[i_min].append(tax[k_min]);
		tax[i_min].append(")");

		// System.err.println(tax[i_min].toString());

		// generate new Node for the root of the tree.
		var v = tree.newNode();
		taxaHashMap.put(tax[i_min].toString(), v);
		var e = tree.newEdge(v, taxaHashMap.get(tax_old_i.toString()));
		tree.setWeight(e, 0.5 * (h[i_min][j_min] + h[i_min][k_min] - h[j_min][k_min]));
		e = tree.newEdge(v, taxaHashMap.get(tax_old_j.toString()));
		tree.setWeight(e, 0.5 * (h[i_min][j_min] + h[j_min][k_min] - h[i_min][k_min]));
		e = tree.newEdge(v, taxaHashMap.get(tax_old_k.toString()));
		tree.setWeight(e, 0.5 * (h[i_min][k_min] + h[j_min][k_min] - h[i_min][j_min]));

		tree.setRoot(v);

		return tree;
	}
}

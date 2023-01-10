/*
 * NeighborJoining.java Copyright (C) 2023 Daniel H. Huson
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
import jloda.util.BitSetUtils;
import jloda.util.CanceledException;
import jloda.util.progress.ProgressListener;
import splitstree6.algorithms.trees.IToSingleTree;
import splitstree6.data.DistancesBlock;
import splitstree6.data.TaxaBlock;
import splitstree6.data.TreesBlock;

import java.util.BitSet;

/**
 * Neighbor joining algorithm
 *
 * @author Daniel Huson, 12.2020
 */
public class NeighborJoining extends Distances2Trees implements IToSingleTree {
	@Override
	public String getCitation() {
		return "Saitou and Nei 1987; " +
			   "N. Saitou and M. Nei. The Neighbor-Joining method: a new method for reconstructing phylogenetic trees. " +
			   "Molecular Biology and Evolution, 4:406-425, 1987.";
	}

	/**
	 * compute the neighbor joining tree
	 */
	public void compute(ProgressListener progress, TaxaBlock taxaBlock, DistancesBlock distances, TreesBlock trees) throws CanceledException {
		trees.setPartial(false);
		trees.setRooted(true);
		trees.getTrees().setAll(computeNJTree(progress, taxaBlock, distances));
	}

	private PhyloTree computeNJTree(ProgressListener progressListener, TaxaBlock taxaBlock, DistancesBlock distances) throws CanceledException {
		final var ntax = distances.getNtax();
		final var tree = new PhyloTree();
		tree.setName("NJ-tree");

		final var alive = new BitSet(); // o-based

		final var nodes = new Node[ntax]; // 0-based
		for (var t = 1; t <= ntax; t++) {
			var v = tree.newNode();
			tree.addTaxon(v, t);
			tree.setLabel(v, taxaBlock.getLabel(t));
			nodes[t - 1] = v;
			alive.set(t - 1);
		}

		if (ntax <= 1)
			return tree;

		progressListener.setMaximum(ntax);
		progressListener.setProgress(0);

		final var matrix = new float[ntax][ntax]; // 0-based

		final var rowSum = new float[ntax]; // 0-based

		for (var i : BitSetUtils.members(alive)) {
			for (var j : BitSetUtils.members(alive, i + 1)) {
				matrix[i][j] = matrix[j][i] = (float) distances.get(i + 1, j + 1);
			}
		}

		for (var i : BitSetUtils.members(alive)) {
			rowSum[i] = computeRowSum(alive, i, matrix);
		}

		var verbose = false;

		while (alive.cardinality() > 2) {
			var minI = -1;
			var minJ = -1;
			var minQ = Float.MAX_VALUE;

			if (verbose) {
				System.err.println("\nTaxa: " + alive.cardinality());
				System.err.println("Distances:");
				for (var i : BitSetUtils.members(alive)) {
					System.err.print(i + 1 + ":");
					for (var j : BitSetUtils.members(alive, i + 1)) {
						System.err.printf(" %d", (int) matrix[i][j]);
					}
					System.err.println();
				}
			}

			if (verbose)
				System.err.println("Q:");

			for (var i : BitSetUtils.members(alive)) {
				if (verbose) System.err.print(i + 1 + ":");
				for (var j : BitSetUtils.members(alive, i + 1)) {
					final var q = (alive.cardinality() - 2) * matrix[i][j] - rowSum[i] - rowSum[j];

					if (verbose) System.err.printf(" %f%n", q);

					if (q < minQ) {
						minQ = q;
						minI = i;
						minJ = j;
					}
				}
				if (verbose) System.err.println();
			}
			if (verbose) System.err.println("minI=" + (minI + 1) + " minJ=" + (minJ + 1) + " minQ=" + (int) minQ);


			final var u = tree.newNode();
			final var weightIU = 0.5f * matrix[minI][minJ] + 0.5f * (rowSum[minI] - rowSum[minJ]) / (alive.cardinality() - 2);
			tree.setWeight(tree.newEdge(u, nodes[minI]), weightIU);
			final var weightJU = matrix[minI][minJ] - weightIU;
			tree.setWeight(tree.newEdge(u, nodes[minJ]), weightJU);

			nodes[minI] = u;

			alive.clear(minI);
			alive.clear(minJ);

			for (var k : BitSetUtils.members(alive)) {
				rowSum[k] -= matrix[k][minI];
				rowSum[k] -= matrix[k][minJ];
			}

			for (var k : BitSetUtils.members(alive)) {
				matrix[minI][k] = matrix[k][minI] = (float) (0.5 * (matrix[minI][k] + matrix[minJ][k] - matrix[minI][minJ]));
			}

			for (var k : BitSetUtils.members(alive)) {
				rowSum[k] += matrix[k][minI];
			}

			rowSum[minI] = computeRowSum(alive, minI, matrix);

			alive.set(minI); // replaces both old taxa

			progressListener.incrementProgress();
		}

		if (alive.cardinality() == 2) {
			var i = BitSetUtils.min(alive);
			var j = BitSetUtils.max(alive);

			tree.setWeight(tree.newEdge(nodes[i], nodes[j]), matrix[i][j]);
			tree.setRoot(nodes[i]);
		}
		progressListener.setProgress(ntax);

		// System.err.println(tree.toBracketString());

		return tree;
	}

	private float computeRowSum(BitSet alive, int i, float[][] matrix) {
		var r = 0f;
		for (var j : BitSetUtils.members(alive)) {
			r += matrix[i][j];
		}
		return r;
	}
}


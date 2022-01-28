/*
 * MaxWeightClique.java Copyright (C) 2022 Daniel H. Huson
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

import java.util.Arrays;

/**
 * User: bryant
 * Date: Jun 13, 2005
 * Time: 9:36:04 PM
 * <p>
 * This is an implementation of the exhaustive algorithm of  Bron and Kerbosch  for
 * the max weight clique problem. It assumes no structure on the graph, and does not
 * use branch and bound.
 * <p>
 * The algorithm was converted from the fortran pseudo-code in
 * Communications of the ACM archive
 * Volume 16 ,  Issue 9  (September 1973)
 * It should only be used for small graphs.
 */
public class MaxWeightClique {

	private final int n;
	private final int[] sortedToUnsorted;
	private final boolean[][] AdjMatrix;
	private final double[] vertexWeights;

	/* These are used by Bron and Kerbosch's algorithm */

	private int c;
	private final int[] compsub;
	private final int[] maxClique;
	private int maxCliqueSize;
	private double maxCliqueWeight;

	private double currWeight;

	/**
	 * Takes an adjaceny matrix (1..n by 1..n) and a vector of vertex weights
	 * (1..n).
	 * Computes a maximum weight clique using an exhaustive algorithm - potentially
	 * very slow!!!
	 */
	public MaxWeightClique(boolean[][] Adj, double[] weights) {

		/* First we need to sort the vertices by decreasing weight */
		n = weights.length - 1;
		Pair[] v = new Pair[n + 1];
		for (int i = 1; i <= n; i++)
			v[i] = new Pair(i, weights[i]);
		Arrays.sort(v, 1, n + 1);

		sortedToUnsorted = new int[n + 1];

		for (int i = 1; i <= n; i++) {
			int oldId = v[i].id;
			sortedToUnsorted[i] = oldId;
		}

		AdjMatrix = new boolean[n + 1][n + 1];
		vertexWeights = new double[n + 1];
		for (int i = 1; i <= n; i++) {
			for (int j = 1; j <= i; j++) {
				AdjMatrix[i][j] = Adj[sortedToUnsorted[i]][sortedToUnsorted[j]];
				AdjMatrix[j][i] = AdjMatrix[i][j];
			}
			vertexWeights[i] = weights[sortedToUnsorted[i]];
		}

		/* Initialisation for the Bron and Kerbosch algorithm  */
		int[] all = new int[n + 1];
		compsub = new int[n + 1];
		maxClique = new int[n + 1];
		maxCliqueSize = 0;
		currWeight = 0.0;
		maxCliqueWeight = 0.0;
		for (int i = 1; i <= n; i++)
			all[i] = i;
		c = 0;
		extend(all, 0, n);
	}

	/**
	 * return the maximum clique weight found by the method
	 */
	public double getMaxCliqueWeight() {
		return maxCliqueWeight;
	}

	/**
	 * return the maximum clique found by the algorithm
	 */
	public boolean[] getMaxClique() {
		boolean[] clique = new boolean[n + 1];
		for (int i = 1; i <= n; i++)
			clique[i] = false;
		for (int i = 1; i <= maxCliqueSize; i++)
			clique[sortedToUnsorted[maxClique[i]]] = true;
		return clique;
	}


	private void extend(int[] old, int ne, int ce) {

        /* old is a list of length ce of vertices in the graph.
        old[1]...old[ne] will not be  added to any clique extending this one. */
		//ToDo: add bounding.

		int[] newv = new int[n + 1];
		int nod, fixp = -1;
		int newne, newce, i, j, count, pos, p, s, sel, minnod;

		minnod = ce;
		boolean fixedPointIsCandidate = false;

		s = pos = -1; //Assignment needed to avoid compilation errors.

		//Determine each counter value and look for minimum
		for (i = 1; i <= ce && minnod != 0; i++) {
			p = old[i];
			count = 0;

			//Count disconnections
			for (j = ne + 1; j <= ce && count < minnod; j++) {
				if (!AdjMatrix[p][old[j]]) {
					count = count + 1;
					//save position of potential candidate
					pos = j;
				}
			}
			//test new minimum
			if (count < minnod) {
				fixp = p;
				minnod = count;
				if (i <= ne)
					s = pos;
				else {
					s = i;
					fixedPointIsCandidate = true;
				}
			}
		}

        /* If fixed point initially chosen from candidates then number of disconntections
        will be preincreased by one. */

		if (fixedPointIsCandidate)
			nod = minnod + 1;
		else
			nod = minnod;


		for (; nod >= 1; nod--) {
			//Interchange
			p = old[s];
			old[s] = old[ne + 1];
			sel = old[ne + 1] = p;
			//Fill new set of excluded. Only relevant if connected to the vertex just added ?!?
			newne = 0;
			for (i = 1; i <= ne; i++) {
				if (AdjMatrix[sel][old[i]]) {
					newne = newne + 1;
					newv[newne] = old[i];
				}
			}
			//Fill new set of candidates. This does not include the vertex being added
			newce = newne;
			for (i = ne + 2; i <= ce; i++) {
				if (AdjMatrix[sel][old[i]]) {
					newce = newce + 1;
					newv[newce] = old[i];
				}
			}
			//Add to compsub
			c = c + 1;
			compsub[c] = sel;
			this.currWeight += this.vertexWeights[sel];

			//System.err.println("Added vertex "+p+" with weight "+vertexWeights[sel]+ " to give current weight of "+ this.currWeight);

			if (newce == 0) {
				if (this.currWeight > this.maxCliqueWeight) {
					//System.err.println("\t Found clique with weight "+this.currWeight+":");
					this.maxCliqueWeight = this.currWeight;
					System.arraycopy(compsub, 1, this.maxClique, 1, c);
					//System.err.println();
					this.maxCliqueSize = c;
				}
			} else {
				if (newne < newce)
					extend(newv, newne, newce);
			}
			//Remove from compsub
			//System.err.println("Removed vertex "+compsub[c]+ "with weight "+ this.vertexWeights[compsub[c]]);

			this.currWeight -= this.vertexWeights[compsub[c]];

			c = c - 1;

			//Add to not
            ne = ne + 1;
            if (nod > 1) {
                for (s = ne + 1; AdjMatrix[fixp][old[s]]; s++) {
                }
            }


        }
    }

    static class Pair implements Comparable {
        public final int id;
        public final double x;

        public Pair(int id, double x) {
            this.id = id;
            this.x = x;
        }

        public int compareTo(Object o) {
            Pair p = (Pair) o;

            //Note - we want to sort in decreasing size!!
			if (this.x < p.x)
				return 1;
			else
				return -1;
		}
	}
}

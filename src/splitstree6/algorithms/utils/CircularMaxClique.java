/*
 *  CircularMaxClique.java Copyright (C) 2021 Daniel H. Huson
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

/*
 * CircularMaxClique.java Copyright (C) 2021. Daniel H. Huson
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

package splitstree6.algorithms.utils;

import splitstree6.data.parts.ASplit;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.List;

/**
 * gets a max clique
 * User: bryant
 * Date: Jun 20, 2005
 */
public class CircularMaxClique {

	static public ArrayList<ASplit> getMaxClique(int ntax, List<ASplit> splits, double[] weights, int[] ordering) {
		/* First step - read the splits back into an array */
		//Save the splits onto a hashmap
		final HashMap<BitSet, Integer> map = new HashMap<>();
		int outgroup = ordering[1];

		for (int i = 1; i <= splits.size(); i++) {
			BitSet sp = splits.get(i - 1).getA();
			if (sp.get(outgroup))
				sp = splits.get(i - 1).getB();
			map.put(sp, i);
		}

		double[][] w = new double[ntax][ntax];
		int[][] splitIds = new int[ntax][ntax];

		for (int i = 0; i < ntax; i++) {
			BitSet set = new BitSet();
			for (int j = i + 1; j < ntax; j++) {
				set.set(ordering[j + 1]);
				if (map.containsKey(set)) {
					int id = map.get(set);
					double weight = weights[id];
					splitIds[i][j] = id;
					w[j][i] = weight;
				}

			}
		}

		//We can now pretend that the taxa are labelled 0... ntax-1, and that w[j][i] is the
		// weight for the cluster  i,i+1,....,j-1.
		// We now let w[i][j] be the max weight tree with given ordering an max cluster i,i+1,...,j-1

		int[][] M = new int[ntax][ntax];

		for (int i = 0; i + 1 < ntax; i++)
			w[i][i + 1] = w[i + 1][i];      //Trivial clusters - get weight automatically.


		for (int k = 2; k < ntax; k++) {
			for (int i = 0; i + k < ntax; i++) {
				double maxweight = -1.0;
				for (int j = i + 1; j < i + k; j++) {
					double x = w[i][j] + w[j][i + k];
					if (x > maxweight) {
						M[i][i + k] = j;
						maxweight = x;
					}
				}
				w[i][i + k] = w[i + k][i] + maxweight;
			}
		}

		//we now extract a max weight clique

		boolean[][] clique = new boolean[ntax][ntax];
		for (int i = 0; i < ntax; i++)
			for (int j = 0; j < ntax; j++)
				clique[i][j] = false;

		extractClique(M, clique, 0, ntax - 1);

		final ArrayList<ASplit> result = new ArrayList<>(splits.size());

		//Now zero all splits not in the clique
		for (int i = 0; i < ntax; i++) {
			for (int j = i + 1; j < ntax; j++) {
				final int id = splitIds[i][j];
				if (id > 0 && clique[i][j]) {
					result.add(splits.get(id - 1).clone());
				}
			}
		}
		return result;

	}

	static private void extractClique(int[][] M, boolean[][] clique, int i, int j) {
		clique[i][j] = true;
		if (j > i + 1) {
			int k = M[i][j];
			extractClique(M, clique, i, k);
			extractClique(M, clique, k, j);
		}
	}
}


/*
 * RazorSlack.java Copyright (C) 2025 Daniel H. Huson
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
 *
 */

package splitstree6.algorithms.distances.distances2network.razor2;


import java.util.Set;

public class RazorSlack {
	/**
	 * Compute c(x) (slack) and an attaining pair (y, z) over the given subset.
	 */
	static Slack slackWithArgmin(int[][] D, Set<Integer> subset, int x) {
		var others = subset.stream().filter(i -> i != x).sorted().toList();
		if (others.size() < 2) return new Slack(0, -1, -1);

		var largestSlack = Integer.MAX_VALUE;
		var second = -1;
		var by = -1;
		var bz = -1;
		for (var i = 0; i < others.size(); i++) {
			var y = others.get(i);
			for (var j = i + 1; j < others.size(); j++) {
				var z = others.get(j);
				var val = (D[x][y] + D[x][z] - D[y][z]) / 2;
				if (val < largestSlack || (val == largestSlack && D[y][z] > second)) {
					largestSlack = val;
					by = y;
					bz = z;
					second = D[y][z];
				}
				if (false) {
					if (val <= 0) {
						System.err.println("x,y=" + D[x][y]);
						System.err.println("x,z=" + D[x][z]);
						System.err.println("y,z=" + D[y][z]);
						System.err.println("x=" + x + ", y=" + y + ", z=" + z + " value=" + val);
					}
				}
			}
		}
		if (false) {
			System.err.println("x,y=" + D[x][by]);
			System.err.println("x,z=" + D[x][bz]);
			System.err.println("y,z=" + D[by][bz]);
			System.err.println("x=" + x + ", y=" + by + ", z=" + bz + " value=" + largestSlack);
		}
		return new Slack(Math.max(0, largestSlack), by, bz);
	}

	public record Slack(int s, int y, int z) {
	}
}

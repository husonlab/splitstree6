/*
 *  ShortestCommonHyperSequence.java Copyright (C) 2024 Daniel H. Huson
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

package splitstree6.xtra.hyperstrings;

import jloda.util.BitSetUtils;
import jloda.util.CollectionUtils;
import jloda.util.StringUtils;

import java.util.ArrayList;
import java.util.BitSet;

public class ShortestCommonHyperSequence {
	public static HyperSequence align(HyperSequence a, HyperSequence b) {
		var m = a.size();
		var n = b.size();

		var matrix = new int[m + 1][n + 1];

		for (var i = 0; i <= m; i++) {
			matrix[i][0] = i;
		}
		for (var j = 0; j <= n; j++) {
			matrix[0][j] = j;
		}

		for (var i = 1; i <= m; i++) {
			for (var j = 1; j <= n; j++) {
				var value = Math.min(matrix[i - 1][j] + 1, matrix[i][j - 1] + 1);
				if (BitSetUtils.contains(a.get(i - 1), b.get(j - 1)) || BitSetUtils.contains(b.get(j - 1), a.get(i - 1)))
					value = Math.min(value, matrix[i - 1][j - 1]);
				matrix[i][j] = value;
			}
		}

		var aTrace = new ArrayList<Integer>();
		var bTrace = new ArrayList<Integer>();
		// compute the trace:
		{
			var i = m;
			var j = n;
			// trace back
			var value = matrix[i][j];
			while (i > 0 || j > 0) {
				if (i > 0 && value == matrix[i - 1][j] + 1) {
					aTrace.add(i - 1);
					bTrace.add(-1);
					value = matrix[i - 1][j];
					i--;
				} else if (j > 0 && value == matrix[i][j - 1] + 1) {
					aTrace.add(-1);
					bTrace.add(j - 1);
					value = matrix[i][j - 1];
					j--;
				} else {
					aTrace.add(i - 1);
					bTrace.add(j - 1);
					i--;
					j--;
				}
			}
			CollectionUtils.reverseInPlace(aTrace);
			CollectionUtils.reverseInPlace(bTrace);
		}


		if (false) { // write out the trace:
			var bufA = new StringBuilder();
			var bufB = new StringBuilder();

			for (var p = 0; p < aTrace.size(); p++) {
				String wordA;
				String wordB;
				if (aTrace.get(p) == -1) {
					wordB = StringUtils.toString(b.get(bTrace.get(p)));
					wordA = " ".repeat(wordB.length());
				} else if (bTrace.get(p) == -1) {
					wordA = StringUtils.toString(a.get(aTrace.get(p)));
					wordB = " ".repeat(wordA.length());
				} else {
					wordA = StringUtils.toString(a.get(aTrace.get(p)));
					wordB = StringUtils.toString(b.get(bTrace.get(p)));
					if (wordA.length() < wordB.length()) {
						wordA += " ".repeat(wordB.length() - wordA.length());
					} else if (wordB.length() < wordA.length()) {
						wordB += " ".repeat(wordA.length() - wordB.length());
					}
				}
				bufA.append(wordA).append(" : ");
				bufB.append(wordB).append(" : ");
			}
			System.err.println("\nAlignment:");
			System.err.println(bufA);
			System.err.println(bufB);
			System.err.println();
		}

		var sequence = new HyperSequence();
		for (var p = 0; p < aTrace.size(); p++) {
			var set = new BitSet();
			if (aTrace.get(p) != -1) {
				set.or(a.get(aTrace.get(p)));
			}
			if (bTrace.get(p) != -1) {
				set.or(b.get(bTrace.get(p)));
			}
			sequence.add(set);
		}

		return sequence;
	}


	public static void main(String[] args) {
		var a = HyperSequence.parse("1 : 2 : 3 4 5 : 6 : 7");
		var b = HyperSequence.parse("1 2 : 3 : 4 5 :  7");

		System.err.println("Input:");
		System.err.println("a= " + a);
		System.err.println("b= " + b);

		var aligned = align(a, b);
		System.err.println("SCS= " + aligned);
	}
}

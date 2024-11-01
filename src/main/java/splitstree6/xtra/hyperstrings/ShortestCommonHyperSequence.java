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

import jloda.util.*;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashSet;
import java.util.function.BiConsumer;

/**
 * determines the shortest common hypersequence using dynamic programming
 * Daniel Huson, 8.2024
 */
public class ShortestCommonHyperSequence {
	private static final byte TRACEBACK_INSERT_A = 1;
	private static final byte TRACEBACK_INSERT_B = 2;
	private static final byte TRACEBACK_MATCH = 4;

	/**
	 * determines the shortest common hypersequence using dynamic programming
	 *
	 * @param a one hypersequence
	 * @param b the other
	 * @return super sequence
	 */
	public static HyperSequence align(HyperSequence a, HyperSequence b) {
		// System.err.println("Aligning "+a+" vs "+b);

		var m = a.size();
		var n = b.size();

		var matrix = new int[m + 1][n + 1];
		var traceback = new byte[m + 1][n + 1]; // |: 1, \ : 2 - : 4

		for (var i = 1; i <= m; i++) {
			matrix[i][0] = i;
			traceback[i][0] = TRACEBACK_INSERT_A;
		}
		for (var j = 1; j <= n; j++) {
			matrix[0][j] = j;
			traceback[0][j] = TRACEBACK_INSERT_B;
		}

		for (var i = 1; i <= m; i++) {
			var i1 = i - 1;
			for (var j = 1; j <= n; j++) {
				var j1 = j - 1;

				var insertionInA = matrix[i1][j] + 1;
				var insertionInB = matrix[i][j1] + 1;

				if (BitSetUtils.contains(a.get(i1), b.get(j1)) || BitSetUtils.contains(b.get(j1), a.get(i1))) {
					var match = matrix[i1][j1];
					var best = NumberUtils.min(insertionInA, insertionInB, match);
					if (insertionInA == best)
						traceback[i][j] |= TRACEBACK_INSERT_A;
					if (insertionInB == best)
						traceback[i][j] |= TRACEBACK_INSERT_B;
					if (match == best)
						traceback[i][j] |= TRACEBACK_MATCH;
					matrix[i][j] = best;
				} else {
					var best = Math.min(insertionInA, insertionInB);
					if (insertionInA == best)
						traceback[i][j] |= TRACEBACK_INSERT_A;
					if (insertionInB == best)
						traceback[i][j] |= TRACEBACK_INSERT_B;
					matrix[i][j] = best;
				}
			}
		}

		if (false) {
			System.err.println("Matrix:");
			System.err.print("     ");
			for (var j = 0; j <= n; j++) {
				System.err.printf(" %02d  ", j);
			}
			System.err.println();

			for (var i = 0; i <= m; i++) {
				System.err.printf("%03d:", i);

				for (var j = 0; j <= n; j++) {
					var value = matrix[i][j];
					System.err.printf(" %2dt%d", value, (traceback[i][j]));
				}
				System.err.println();
			}
		}

		if (true) {
			var best = new Single<>(Integer.MAX_VALUE);
			var result = new Single<HyperSequence>();
			var seen = new HashSet<Pair<ArrayList<Integer>, ArrayList<Integer>>>();

			traceback(m, n, matrix, traceback, 100, (aTrace, bTrace) -> {
				var pair = new Pair<>(aTrace, bTrace);
				if (!seen.add(pair))
					return;

				aTrace = CollectionUtils.reverse(aTrace);
				bTrace = CollectionUtils.reverse(bTrace);
				var hyperSequence = new HyperSequence();
				for (var p = 0; p < aTrace.size(); p++) {
					var set = new BitSet();
					if (aTrace.get(p) != -1) {
						set.or(a.get(aTrace.get(p)));
					}
					if (bTrace.get(p) != -1) {
						set.or(b.get(bTrace.get(p)));
					}
					hyperSequence.add(set);
				}

				// System.err.println("Got: " + hyperSequence);

				var simplified = new HyperSequence();
				var count = 0;

				for (var i = 0; i < hyperSequence.size() - 1; i++) {
					var set = BitSetUtils.minus(hyperSequence.get(i), hyperSequence.get(i + 1));
					if (set.cardinality() > 0) {
						count += set.cardinality();
						simplified.add(set);
					}
				}
				{
					var last = hyperSequence.get(hyperSequence.size() - 1);
					count += last.cardinality();
					simplified.add(last);
				}

				// System.err.println("Sim: " + simplified);

				if (count < best.get()) {
					best.set(count);
					result.set(simplified);
				}
			});

			//System.err.println("Done "+a+" vs "+b+": "+result.get());

			return result.get();
		} else {
			var aTrace = new ArrayList<Integer>();
			var bTrace = new ArrayList<Integer>();
			// compute the trace:
			{
				var i = m;
				var j = n;
				// trace back
				var value = matrix[m][n];
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
				reportTrace(a, b, aTrace, bTrace);
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
	}

	/**
	 * perform trace back
	 *
	 * @param m                 starting row
	 * @param n                 starting column
	 * @param matrix            DP matrix
	 * @param maxResults        the maximum number of results to consider
	 * @param tracebackConsumer consume the resulting traceback
	 */
	private static void traceback(int m, int n, int[][] matrix, byte[][] traceback, int maxResults, BiConsumer<ArrayList<Integer>, ArrayList<Integer>> tracebackConsumer) {
		traceBackRec(m, n, matrix[m][n], matrix, traceback, new ArrayList<>(), new ArrayList<>(), new Counter(maxResults), tracebackConsumer);
	}

	private static void traceBackRec(final int i, final int j, final int value, int[][] matrix, byte[][] traceback, ArrayList<Integer> aTrace, ArrayList<Integer> bTrace, Counter resultsToConsume, BiConsumer<ArrayList<Integer>, ArrayList<Integer>> tracebackConsumer) {
		// System.err.println("-------- rec: i "+i+" j "+j);

		if ((traceback[i][j] & TRACEBACK_INSERT_A) != 0) {
			// System.err.printf(" i: %d -> %d, j: %d%n", i, i-1,j);
			// System.err.println("value: "+value);
			// System.err.println("matrix["+i+"-1]["+j+"]="+matrix[i-1][j]+"+1");

			aTrace.add(i - 1);
			bTrace.add(-1);
			traceBackRec(i - 1, j, matrix[i - 1][j], matrix, traceback, aTrace, bTrace, resultsToConsume, tracebackConsumer);
			aTrace.remove(aTrace.size() - 1);
			bTrace.remove(bTrace.size() - 1);
			if (resultsToConsume.get() == 0)
				return;
		}
		if ((traceback[i][j] & TRACEBACK_INSERT_B) != 0) {
			// System.err.printf(" i: %d, j: %d -> %d%n", i, j,j-1);
			// System.err.println("value: "+value);
			// System.err.println("matrix["+i+"]["+j+"-1]="+matrix[i][j-1]+"+1");

			aTrace.add(-1);
			bTrace.add(j - 1);
			traceBackRec(i, j - 1, matrix[i][j - 1], matrix, traceback, aTrace, bTrace, resultsToConsume, tracebackConsumer);
			aTrace.remove(aTrace.size() - 1);
			bTrace.remove(bTrace.size() - 1);
			if (resultsToConsume.get() == 0)
				return;
		}
		if ((traceback[i][j] & TRACEBACK_MATCH) != 0) {
			// System.err.printf(" i: %d -> %d, j: %d -> %d%n", i,i-1, j,j-1);
			// System.err.println("value: "+value);
			//System.err.println("matrix["+i+"]["+j+"]="+matrix[i][j]);
			aTrace.add(i - 1);
			bTrace.add(j - 1);
			traceBackRec(i - 1, j - 1, matrix[i - 1][j - 1], matrix, traceback, aTrace, bTrace, resultsToConsume, tracebackConsumer);
			aTrace.remove(aTrace.size() - 1);
			bTrace.remove(bTrace.size() - 1);
			if (resultsToConsume.get() == 0)
				return;
		}
		if (i == 0 && j == 0) {
			tracebackConsumer.accept(aTrace, bTrace);
			resultsToConsume.decrement();
		}
	}

	private static void reportTrace(HyperSequence a, HyperSequence b, ArrayList<Integer> aTrace, ArrayList<Integer> bTrace) {
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

	public static Pair<HyperSequence, HyperSequence> preProcessExpansion(HyperSequence a, HyperSequence b) {
		var aExpanded = new HyperSequence();
		var bExpanded = new HyperSequence();

		for (var i = 0; i < 2; i++) { // i==0: expand a using b, i==1: expand b using a
			var first = (i == 0 ? a : b);
			var second = (i == 0 ? b : a);
			var expanded = (i == 0 ? aExpanded : bExpanded);

			for (var set : first.elements()) { // for each member
				if (set.cardinality() == 1)
					expanded.add(set); // singleton, no expansion
				else {
					var remaining = BitSetUtils.copy(set);
					var list = new ArrayList<BitSet>();
					for (var other : second.elements()) { // loop over all members of other sequence
						if (set.intersects(other)) {
							var intersection = BitSetUtils.intersection(set, other);
							remaining.andNot(intersection);
							list.add(intersection);
						}
					}
					if (remaining.cardinality() > 0) {
						expanded.add(remaining); //
					}
					list.forEach(expanded::add);
				}
			}
		}
		return new Pair<>(aExpanded, bExpanded);
	}

	public static HyperSequence postProcessExpansion(HyperSequence a, HyperSequence b, HyperSequence superseq) {
		// todo: implement
		return superseq;
	}

	public static void main(String[] args) {
		//var a = HyperSequence.parse("6:3:2:8:4 5:7");
		// var b = HyperSequence.parse("6:2 4 8:5:7:3");

		var a = HyperSequence.parse("2 3 5 : 4 : 8 : 9");
		var b = HyperSequence.parse("6 : 2 : 3 : 4 5 : 8");


		System.err.println("Input:");
		System.err.println("a= " + a);
		System.err.println("b= " + b);

		var aligned = align(a, b);
		System.err.println("SCS= " + aligned);

	}
}

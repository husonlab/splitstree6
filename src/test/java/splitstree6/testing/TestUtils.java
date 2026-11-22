/*
 *  TestUtils.java Copyright (C) 2026 Daniel H. Huson
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

package splitstree6.testing;

import jloda.graph.Node;
import jloda.phylo.PhyloTree;
import jloda.util.progress.ProgressSilent;
import splitstree6.algorithms.characters.characters2distances.Characters2Distances;
import splitstree6.algorithms.distances.distances2splits.Distances2Splits;
import splitstree6.algorithms.distances.distances2trees.Distances2Trees;
import splitstree6.data.*;
import splitstree6.data.parts.CharactersType;
import splitstree6.models.nucleotideModels.NucleotideModel;
import splitstree6.splits.ASplit;

import java.util.*;

/**
 * input data whose answer is known, and independent reference computations, for the unit tests of the algorithms.
 * A set of weighted splits is held as a map from the side of the split that contains taxon 1 to the weight.
 * Taxa are numbered from 1, arrays of distances from 0.
 */
public class TestUtils {
	// ---------- blocks ----------

	public static TaxaBlock taxa(int n) {
		var taxa = new TaxaBlock();
		var names = new ArrayList<String>();
		for (var t = 1; t <= n; t++) names.add("t" + t);
		taxa.addTaxaByNames(names);
		return taxa;
	}

	public static DistancesBlock distances(double[][] d) {
		var block = new DistancesBlock();
		block.setNtax(d.length);
		for (var i = 0; i < d.length; i++) for (var j = 0; j < d.length; j++) block.set(i + 1, j + 1, d[i][j]);
		return block;
	}

	public static CharactersBlock characters(CharactersType type, String symbols, String... rows) {
		var chars = new CharactersBlock();
		chars.setDimension(rows.length, rows[0].length());
		chars.setGapCharacter('-');
		chars.setMissingCharacter('?');
		for (var t = 1; t <= rows.length; t++)
			for (var i = 1; i <= rows[0].length(); i++)
				chars.set(t, i, rows[t - 1].charAt(i - 1));
		chars.setSymbols(symbols);
		chars.setDataType(type);
		return chars;
	}

	// ---------- running an algorithm ----------

	public static DistancesBlock run(Characters2Distances algorithm, CharactersBlock chars) throws Exception {
		var distances = new DistancesBlock();
		algorithm.compute(new ProgressSilent(), taxa(chars.getNtax()), chars, distances);
		return distances;
	}

	public static PhyloTree tree(Distances2Trees algorithm, double[][] d) throws Exception {
		var trees = new TreesBlock();
		algorithm.compute(new ProgressSilent(), taxa(d.length), distances(d), trees);
		return trees.getTree(1);
	}

	public static SplitsBlock splits(Distances2Splits algorithm, double[][] d) throws Exception {
		var splits = new SplitsBlock();
		algorithm.compute(new ProgressSilent(), taxa(d.length), distances(d), splits);
		return splits;
	}

	// ---------- weighted splits ----------

	/**
	 * the side of a split that contains taxon 1
	 */
	public static BitSet canonical(BitSet side, int n) {
		if (side.get(1)) return (BitSet) side.clone();
		var other = new BitSet();
		for (var t = 1; t <= n; t++) if (!side.get(t)) other.set(t);
		return other;
	}

	/**
	 * the splits of a block whose weight exceeds the threshold
	 */
	public static Map<BitSet, Double> weightedSplits(SplitsBlock splits, int n, double threshold) {
		var result = new HashMap<BitSet, Double>();
		for (ASplit s : splits.getSplits())
			if (s.getWeight() > threshold) result.merge(canonical(s.getA(), n), s.getWeight(), Double::sum);
		return result;
	}

	/**
	 * the splits of a tree, by the taxa below each edge; edges that give the same split are added up
	 */
	public static Map<BitSet, Double> weightedSplits(PhyloTree tree, int n) {
		var result = new HashMap<BitSet, Double>();
		below(tree, tree.getRoot(), n, result);
		return result;
	}

	private static BitSet below(PhyloTree tree, Node v, int n, Map<BitSet, Double> result) {
		var set = new BitSet();
		for (var t : tree.getTaxa(v)) set.set(t);
		for (var e : v.outEdges()) {
			var sub = below(tree, e.getTarget(), n, result);
			if (sub.cardinality() > 0 && sub.cardinality() < n) result.merge(canonical(sub, n), tree.getWeight(e), Double::sum);
			set.or(sub);
		}
		return set;
	}

	/**
	 * the largest difference between the weights of two sets of splits, an absent split counting as weight 0
	 */
	public static double maxDifference(Map<BitSet, Double> a, Map<BitSet, Double> b) {
		var max = 0.0;
		var keys = new HashSet<>(a.keySet());
		keys.addAll(b.keySet());
		for (var key : keys) max = Math.max(max, Math.abs(a.getOrDefault(key, 0.0) - b.getOrDefault(key, 0.0)));
		return max;
	}

	public static boolean isTrivial(BitSet side, int n) {
		return side.cardinality() <= 1 || side.cardinality() >= n - 1;
	}

	// ---------- distances with a known answer ----------

	/**
	 * a random binary tree on taxa 1..n as weighted splits, all trivial splits included; weights in [0.1, 1.1)
	 */
	public static Map<BitSet, Double> randomTree(int n, Random random) {
		var result = new HashMap<BitSet, Double>();
		var clusters = new ArrayList<BitSet>();
		for (var t = 1; t <= n; t++) {
			var c = new BitSet();
			c.set(t);
			clusters.add(c);
		}
		while (clusters.size() > 3) {
			var a = clusters.remove(random.nextInt(clusters.size()));
			var b = clusters.remove(random.nextInt(clusters.size()));
			a.or(b);
			clusters.add(a);
			result.put(canonical(a, n), 0.1 + random.nextDouble());
		}
		for (var t = 1; t <= n; t++) {
			var c = new BitSet();
			c.set(t);
			result.put(canonical(c, n), 0.1 + random.nextDouble());
		}
		return result;
	}

	/**
	 * random splits that are intervals of a random circular ordering, each present with probability 1/2
	 */
	public static Map<BitSet, Double> randomCircularSplits(int n, Random random) {
		var order = new ArrayList<Integer>();
		for (var t = 1; t <= n; t++) order.add(t);
		Collections.shuffle(order, random);
		var result = new HashMap<BitSet, Double>();
		for (var p = 1; p < n; p++)
			for (var q = p; q < n; q++)
				if (random.nextBoolean()) {
					var side = new BitSet();
					for (var i = p; i <= q; i++) side.set(order.get(i));
					result.put(canonical(side, n), 0.1 + random.nextDouble());
				}
		return result;
	}

	/**
	 * the distances of a set of weighted splits: the sum of the weights of the splits that separate two taxa
	 */
	public static double[][] metric(Map<BitSet, Double> splits, int n) {
		var d = new double[n][n];
		for (var entry : splits.entrySet())
			for (var i = 1; i <= n; i++)
				for (var j = 1; j <= n; j++)
					if (entry.getKey().get(i) != entry.getKey().get(j)) d[i - 1][j - 1] += entry.getValue();
		return d;
	}

	/**
	 * each distance multiplied by 1 + sigma * a standard normal deviate, not less than 0.05
	 */
	public static double[][] noisy(double[][] d, double sigma, Random random) {
		var n = d.length;
		var result = new double[n][n];
		for (var i = 0; i < n; i++)
			for (var j = i + 1; j < n; j++)
				result[i][j] = result[j][i] = d[i][j] * Math.max(0.05, 1 + sigma * random.nextGaussian());
		return result;
	}

	/**
	 * a circular split system (all trivial splits, others with probability 1/4) with noise
	 */
	public static double[][] noisyCircular(int n, double sigma, Random random) {
		var order = new ArrayList<Integer>();
		for (var t = 1; t <= n; t++) order.add(t);
		Collections.shuffle(order, random);
		var truth = new HashMap<BitSet, Double>();
		for (var p = 0; p < n - 1; p++)
			for (var q = p; q < n - 1; q++)
				if (p == q || random.nextInt(4) == 0) {
					var side = new BitSet();
					for (var i = p; i <= q; i++) side.set(order.get(i));
					truth.put(canonical(side, n), 0.1 + random.nextDouble());
				}
		return noisy(metric(truth, n), sigma, random);
	}

	public static double[][] scaled(double[][] d, double scale) {
		var n = d.length;
		var result = new double[n][n];
		for (var i = 0; i < n; i++) for (var j = 0; j < n; j++) result[i][j] = scale * d[i][j];
		return result;
	}

	/**
	 * a random ultrametric; fills d and returns the splits of the rooted tree, as an unrooted one
	 */
	public static Map<BitSet, Double> randomUltrametric(int n, Random random, double[][] d) {
		var result = new HashMap<BitSet, Double>();
		var clusters = new ArrayList<BitSet>();
		var heights = new ArrayList<Double>();
		for (var t = 1; t <= n; t++) {
			var c = new BitSet();
			c.set(t);
			clusters.add(c);
			heights.add(0.0);
		}
		var height = 0.0;
		while (clusters.size() > 1) {
			height += 0.1 + random.nextDouble();
			var ia = random.nextInt(clusters.size());
			var a = clusters.remove(ia);
			var ha = heights.remove(ia);
			var ib = random.nextInt(clusters.size());
			var b = clusters.remove(ib);
			var hb = heights.remove(ib);
			for (var i = a.nextSetBit(0); i >= 0; i = a.nextSetBit(i + 1))
				for (var j = b.nextSetBit(0); j >= 0; j = b.nextSetBit(j + 1))
					d[i - 1][j - 1] = d[j - 1][i - 1] = 2 * height;
			if (a.cardinality() < n) result.merge(canonical(a, n), height - ha, Double::sum);
			if (b.cardinality() < n) result.merge(canonical(b, n), height - hb, Double::sum);
			var c = (BitSet) a.clone();
			c.or(b);
			clusters.add(c);
			heights.add(height);
		}
		return result;
	}

	/**
	 * distances for which several methods have a special case: one, two and three taxa, all distances zero, and two
	 * identical taxa
	 */
	public static Map<String, double[][]> degenerateDistances() {
		var cases = new LinkedHashMap<String, double[][]>();
		cases.put("one taxon", new double[1][1]);
		cases.put("two taxa", new double[][]{{0, 1}, {1, 0}});
		cases.put("three taxa", new double[][]{{0, 1, 2}, {1, 0, 2}, {2, 2, 0}});
		cases.put("four taxa", metric(randomTree(4, new Random(7)), 4));
		cases.put("six taxa, all distances zero", new double[6][6]);
		var d = metric(randomTree(6, new Random(18)), 6);
		for (var k = 0; k < 6; k++) d[1][k] = d[k][1] = d[0][k];
		d[0][1] = d[1][0] = 0;
		cases.put("six taxa, taxa 1 and 2 identical", d);
		return cases;
	}

	// ---------- reference computations ----------

	/**
	 * neighbor joining (Saitou and Nei 1987, Studier and Keppler 1988) or BioNJ (Gascuel 1997), written from the papers,
	 * returning the splits of the tree.
	 * With four clusters left, a pair and the complementary pair have the same score and give the same tree, but BioNJ
	 * gives different branch lengths depending on which is joined; otherPairAtFour selects the complementary pair
	 */
	public static Map<BitSet, Double> referenceNeighborJoining(double[][] input, boolean bio, boolean otherPairAtFour) {
		var n = input.length;
		var d = new double[n][n];
		var v = new double[n][n];
		for (var i = 0; i < n; i++) for (var j = 0; j < n; j++) d[i][j] = v[i][j] = input[i][j];
		var cluster = new BitSet[n];
		var alive = new ArrayList<Integer>();
		for (var i = 0; i < n; i++) {
			cluster[i] = new BitSet();
			cluster[i].set(i + 1);
			alive.add(i);
		}
		var result = new HashMap<BitSet, Double>();
		while (alive.size() > 3) {
			var r = alive.size();
			var sum = new double[n];
			for (var i : alive) for (var j : alive) sum[i] += d[i][j];
			int bi = -1, bj = -1;
			var best = Double.POSITIVE_INFINITY;
			for (var i : alive)
				for (var j : alive)
					if (i < j) {
						var q = (r - 2) * d[i][j] - sum[i] - sum[j];
						if (q < best) {
							best = q;
							bi = i;
							bj = j;
						}
					}
			if (r == 4 && otherPairAtFour) {
				var others = new ArrayList<Integer>();
				for (var k : alive) if (k != bi && k != bj) others.add(k);
				bi = others.get(0);
				bj = others.get(1);
			}
			var di = 0.5 * d[bi][bj] + (sum[bi] - sum[bj]) / (2.0 * (r - 2));
			var dj = d[bi][bj] - di;
			result.merge(canonical(cluster[bi], n), di, Double::sum);
			result.merge(canonical(cluster[bj], n), dj, Double::sum);
			var lambda = 0.5;
			if (bio && v[bi][bj] > 0) {
				var s = 0.0;
				for (var k : alive) if (k != bi && k != bj) s += v[bj][k] - v[bi][k];
				lambda = Math.max(0, Math.min(1, 0.5 + s / (2.0 * (r - 2) * v[bi][bj])));
			}
			for (var k : alive)
				if (k != bi && k != bj) {
					var dk = lambda * (d[bi][k] - di) + (1 - lambda) * (d[bj][k] - dj);
					var vk = lambda * v[bi][k] + (1 - lambda) * v[bj][k] - lambda * (1 - lambda) * v[bi][bj];
					d[bi][k] = d[k][bi] = dk;
					v[bi][k] = v[k][bi] = vk;
				}
			cluster[bi].or(cluster[bj]);
			alive.remove((Integer) bj);
		}
		int a = alive.get(0), b = alive.get(1), c = alive.get(2);
		result.merge(canonical(cluster[a], n), 0.5 * (d[a][b] + d[a][c] - d[b][c]), Double::sum);
		result.merge(canonical(cluster[b], n), 0.5 * (d[a][b] + d[b][c] - d[a][c]), Double::sum);
		result.merge(canonical(cluster[c], n), 0.5 * (d[a][c] + d[b][c] - d[a][b]), Double::sum);
		return result;
	}

	/**
	 * all splits with positive index, by enumeration of all splits: the isolation index of split decomposition
	 * (Bandelt and Dress 1992) or the Buneman index
	 */
	public static Map<BitSet, Double> splitsByEnumeration(double[][] d, boolean isolationIndex) {
		var n = d.length;
		var result = new HashMap<BitSet, Double>();
		for (var mask = 1; mask < (1 << (n - 1)); mask++) { // the side that does not contain taxon n
			var a = new ArrayList<Integer>();
			var b = new ArrayList<Integer>();
			for (var i = 0; i < n; i++)
				if ((mask & (1 << i)) != 0) a.add(i);
				else b.add(i);
			var min = Double.POSITIVE_INFINITY;
			for (var a1 : a)
				for (var a2 : a)
					for (var b1 : b)
						for (var b2 : b) {
							double x = d[a1][b1] + d[a2][b2], y = d[a1][b2] + d[a2][b1];
							var crossing = isolationIndex ? Math.max(x, y) : Math.min(x, y);
							min = Math.min(min, 0.5 * (crossing - d[a1][a2] - d[b1][b2]));
						}
			if (min > 1e-6) {
				var side = new BitSet();
				for (var i : a) side.set(i + 1);
				result.put(canonical(side, n), min);
			}
		}
		return result;
	}

	/**
	 * the sum of the squared differences between the distances and those of the weighted splits, over all pairs
	 */
	public static double leastSquaresObjective(double[][] d, Map<BitSet, Double> weights) {
		var n = d.length;
		var s = metric(weights, n);
		var sum = 0.0;
		for (var i = 0; i < n; i++) for (var j = i + 1; j < n; j++) sum += (d[i][j] - s[i][j]) * (d[i][j] - s[i][j]);
		return sum;
	}

	/**
	 * the non-negative least-squares weights of all splits of the given circular ordering (1-based, position 0 unused),
	 * by cyclic coordinate descent on the normal equations, until the optimality conditions hold to 1e-10 relative
	 * to the largest right-hand side. Returns null if that is not reached
	 */
	public static Map<BitSet, Double> referenceCircularLeastSquares(double[][] d, int[] cycle) {
		var n = d.length;
		var m = n * (n - 1) / 2;
		var sides = new BitSet[m];
		var k = 0;
		for (var i = 1; i <= n; i++)
			for (var j = i + 1; j <= n; j++) {
				sides[k] = new BitSet();
				for (var p = i; p < j; p++) sides[k].set(cycle[p]);
				k++;
			}
		// normal equations: g[c1][c2] = number of pairs separated by both splits, b[c] = sum of the distances across split c
		var b = new double[m];
		var size = new int[m];
		for (var c = 0; c < m; c++) {
			size[c] = sides[c].cardinality();
			for (var s = sides[c].nextSetBit(0); s >= 0; s = sides[c].nextSetBit(s + 1))
				for (var t = 1; t <= n; t++) if (!sides[c].get(t)) b[c] += d[s - 1][t - 1];
		}
		var g = new double[m][m];
		for (var c1 = 0; c1 < m; c1++)
			for (var c2 = c1; c2 < m; c2++) {
				var both = (BitSet) sides[c1].clone();
				both.and(sides[c2]);
				var ab = both.cardinality();
				var aOnly = size[c1] - ab;
				var bOnly = size[c2] - ab;
				var neither = n - ab - aOnly - bOnly;
				g[c1][c2] = g[c2][c1] = (double) ab * neither + (double) aOnly * bOnly;
			}
		var x = new double[m];
		var grad = new double[m];
		for (var c = 0; c < m; c++) grad[c] = -b[c];
		var bound = 0.0;
		for (var c = 0; c < m; c++) bound = Math.max(bound, Math.abs(b[c]));
		var converged = bound == 0;
		for (var sweep = 0; sweep < 200000 && !converged; sweep++) {
			for (var c = 0; c < m; c++) {
				var updated = Math.max(0, x[c] - grad[c] / g[c][c]);
				var delta = updated - x[c];
				if (delta != 0) {
					x[c] = updated;
					var row = g[c];
					for (var c2 = 0; c2 < m; c2++) grad[c2] += delta * row[c2];
				}
			}
			if (sweep % 50 == 0) {
				if (sweep % 1000 == 0) { // recompute the gradient now and then, so that rounding does not accumulate
					for (var c = 0; c < m; c++) grad[c] = -b[c];
					for (var c = 0; c < m; c++)
						if (x[c] != 0) {
							var row = g[c];
							for (var c2 = 0; c2 < m; c2++) grad[c2] += x[c] * row[c2];
						}
				}
				var violation = 0.0;
				for (var c = 0; c < m; c++) violation = Math.max(violation, x[c] > 0 ? Math.abs(grad[c]) : Math.max(0, -grad[c]));
				converged = violation / bound < 1e-10;
			}
		}
		if (!converged) return null;
		var result = new HashMap<BitSet, Double>();
		for (var c = 0; c < m; c++) if (x[c] > 0) result.merge(canonical(sides[c], n), x[c], Double::sum);
		return result;
	}

	// ---------- sequences with a known distance ----------

	/**
	 * the divergence matrix of a nucleotide model at time t: entry (a,b) is the probability of state a in one sequence
	 * and b in the other
	 */
	public static double[][] divergence(NucleotideModel model, double t) {
		var F = new double[4][4];
		for (var a = 0; a < 4; a++) for (var b = 0; b < 4; b++) F[a][b] = model.getX(a, b, t);
		return F;
	}

	/**
	 * the divergence matrix of the Jukes-Cantor model, given the probability that the two states at a site are equal
	 */
	public static double[][] divergenceJukesCantor(double same) {
		var F = new double[4][4];
		for (var a = 0; a < 4; a++) for (var b = 0; b < 4; b++) F[a][b] = 0.25 * (a == b ? same : (1 - same) / 3.0);
		return F;
	}

	/**
	 * two sequences of the given number of sites whose divergence matrix is F, by largest-remainder rounding, and a copy of
	 * the first
	 */
	public static String[] sequences(double[][] F, int sites) {
		var counts = new int[16];
		var remainder = new double[16];
		var assigned = 0;
		for (var k = 0; k < 16; k++) {
			var x = F[k / 4][k % 4] * sites;
			counts[k] = (int) Math.floor(x);
			remainder[k] = x - counts[k];
			assigned += counts[k];
		}
		while (assigned < sites) {
			var best = 0;
			for (var k = 1; k < 16; k++) if (remainder[k] > remainder[best]) best = k;
			counts[best]++;
			remainder[best] = -1;
			assigned++;
		}
		var first = new StringBuilder();
		var second = new StringBuilder();
		for (var k = 0; k < 16; k++) {
			first.append(String.valueOf("acgt".charAt(k / 4)).repeat(counts[k]));
			second.append(String.valueOf("acgt".charAt(k % 4)).repeat(counts[k]));
		}
		return new String[]{first.toString(), second.toString(), first.toString()};
	}

	// ---------- reports, sets and simulated sequences ----------

	public static String text(ReportBlock report) {
		return String.join("\n", report.getLines());
	}

	public static BitSet set(int... members) {
		var set = new BitSet();
		for (var t : members) set.set(t);
		return set;
	}

	/**
	 * the first number that follows the given text
	 */
	public static double numberAfter(String text, String prefix) {
		var start = text.indexOf(prefix);
		if (start < 0) throw new IllegalArgumentException("'" + prefix + "' not found in: " + text);
		var matcher = java.util.regex.Pattern.compile("-?[0-9][0-9,]*\\.?[0-9]*([eE]-?[0-9]+)?").matcher(text.substring(start + prefix.length()));
		if (!matcher.find()) throw new IllegalArgumentException("no number after '" + prefix + "' in: " + text);
		return Double.parseDouble(matcher.group().replace(",", ""));
	}

	/**
	 * nucleotide sequences evolved under the Jukes-Cantor model along a random rooted binary tree whose edges all have
	 * the given length (expected substitutions per site); the first invariable sites never change
	 */
	public static String[] simulateSequences(int n, int sites, double edgeLength, int invariable, Random random) {
		var root = new char[sites];
		for (var i = 0; i < sites; i++) root[i] = "acgt".charAt(random.nextInt(4));
		var rows = new String[n];
		var taxa = new ArrayList<Integer>();
		for (var t = 0; t < n; t++) taxa.add(t);
		evolve(root, taxa, edgeLength, invariable, random, rows);
		return rows;
	}

	private static void evolve(char[] sequence, List<Integer> taxa, double edgeLength, int invariable, Random random, String[] rows) {
		if (taxa.size() == 1) {
			rows[taxa.get(0)] = new String(sequence);
			return;
		}
		var cut = 1 + random.nextInt(taxa.size() - 1);
		for (var part : List.of(taxa.subList(0, cut), taxa.subList(cut, taxa.size()))) {
			var child = sequence.clone();
			var change = 0.75 * (1 - Math.exp(-4.0 * edgeLength / 3.0));
			for (var i = invariable; i < child.length; i++)
				if (random.nextDouble() < change) {
					char other;
					do other = "acgt".charAt(random.nextInt(4)); while (other == child[i]);
					child[i] = other;
				}
			evolve(child, part, edgeLength, invariable, random, rows);
		}
	}

	/**
	 * binary characters without homoplasy: for each split, as many sites as its weight rounded up, with state 'a' on the
	 * side of taxon 1 and 'c' on the other
	 */
	public static String[] perfectCharacters(Map<BitSet, Double> splits, int n) {
		var rows = new StringBuilder[n];
		for (var t = 0; t < n; t++) rows[t] = new StringBuilder();
		var sides = new ArrayList<>(splits.keySet());
		sides.sort(Comparator.comparing(BitSet::toString));
		for (var side : sides)
			for (var k = 0; k < Math.ceil(splits.get(side)); k++)
				for (var t = 1; t <= n; t++) rows[t - 1].append(side.get(t) ? 'a' : 'c');
		var result = new String[n];
		for (var t = 0; t < n; t++) result[t] = rows[t].toString();
		return result;
	}

	// ---------- splits blocks and trees ----------

	/**
	 * a splits block with the given weighted splits (sides containing taxon 1), in a fixed order, with its cycle and
	 * compatibility computed
	 */
	public static SplitsBlock splitsBlock(Map<BitSet, Double> splits, int n) {
		var block = new SplitsBlock();
		var sides = new ArrayList<>(splits.keySet());
		sides.sort(Comparator.comparing(BitSet::toString));
		for (var side : sides) block.getSplits().add(new ASplit(side, n, splits.get(side)));
		block.setCycle(splitstree6.algorithms.utils.SplitsBlockUtilities.computeCycle(n, block.getSplits()));
		block.setCompatibility(splitstree6.splits.Compatibility.compute(n, block.getSplits(), block.getCycle()));
		return block;
	}

	/**
	 * a tree from a Newick string whose leaves are named t1, t2, ...: the leaf named tK is taxon K
	 */
	public static PhyloTree tree(String newick) {
		try {
			var tree = new PhyloTree();
			new jloda.phylo.NewickIO().parseBracketNotation(tree, newick, true);
			for (var v : tree.nodes()) {
				var label = tree.getLabel(v);
				if (label != null && label.matches("t[0-9]+")) tree.addTaxon(v, Integer.parseInt(label.substring(1)));
			}
			return tree;
		} catch (java.io.IOException ex) {
			throw new IllegalArgumentException(ex);
		}
	}

	public static TreesBlock trees(String... newick) {
		var block = new TreesBlock();
		for (var line : newick) {
			var tree = tree(line);
			tree.setName("tree-" + (block.getNTrees() + 1));
			block.getTrees().add(tree);
		}
		block.setRooted(true);
		return block;
	}

	/**
	 * the sets of taxa below the nodes of a rooted tree, other than the set of all taxa
	 */
	public static Set<BitSet> clusters(PhyloTree tree) {
		var result = new HashSet<BitSet>();
		var all = clustersBelow(tree, tree.getRoot(), result);
		result.remove(all);
		return result;
	}

	private static BitSet clustersBelow(PhyloTree tree, Node v, Set<BitSet> result) {
		var set = new BitSet();
		for (var t : tree.getTaxa(v)) set.set(t);
		for (var e : v.outEdges()) set.or(clustersBelow(tree, e.getTarget(), result));
		result.add(set);
		return set;
	}

	/**
	 * a random rooted binary tree on taxa 1..n as a Newick string, with edge lengths in [0.1, 1.1) or without
	 */
	public static String randomNewick(int n, Random random, boolean lengths) {
		var parts = new ArrayList<String>();
		for (var t = 1; t <= n; t++) parts.add("t" + t);
		while (parts.size() > 1) {
			var a = parts.remove(random.nextInt(parts.size()));
			var b = parts.remove(random.nextInt(parts.size()));
			if (lengths)
				parts.add("(" + a + ":" + (0.1 + random.nextDouble()) + "," + b + ":" + (0.1 + random.nextDouble()) + ")");
			else parts.add("(" + a + "," + b + ")");
		}
		return parts.get(0) + ";";
	}

	/**
	 * the distances between the taxa along the edges of a tree
	 */
	public static double[][] pathDistances(PhyloTree tree, int n) {
		return metric(weightedSplits(tree, n), n);
	}

	/**
	 * the Newick string of the rooted tree on taxa 1..n that has exactly the given clusters (and the leaves)
	 */
	public static String newickFromClusters(Set<BitSet> clusters, int n) {
		var all = new BitSet();
		all.set(1, n + 1);
		return newickFromClusters(all, clusters) + ";";
	}

	private static String newickFromClusters(BitSet taxa, Set<BitSet> clusters) {
		if (taxa.cardinality() == 1) return "t" + taxa.nextSetBit(0);
		// the children are the largest clusters properly inside, and the taxa that are in none of them
		var inside = new ArrayList<BitSet>();
		for (var c : clusters) {
			var rest = (BitSet) c.clone();
			rest.andNot(taxa);
			if (rest.isEmpty() && !c.equals(taxa) && !c.isEmpty()) inside.add(c);
		}
		inside.sort(Comparator.comparing(BitSet::toString));
		var parts = new ArrayList<String>();
		var covered = new BitSet();
		for (var c : inside) {
			var maximal = true;
			for (var d : inside) {
				var rest = (BitSet) c.clone();
				rest.andNot(d);
				if (rest.isEmpty() && !c.equals(d)) maximal = false;
			}
			if (maximal) {
				parts.add(newickFromClusters(c, clusters));
				covered.or(c);
			}
		}
		for (var t = taxa.nextSetBit(0); t >= 0; t = taxa.nextSetBit(t + 1)) if (!covered.get(t)) parts.add("t" + t);
		return "(" + String.join(",", parts) + ")";
	}

	/**
	 * rooted trees on taxa 1..n that are related and not fully resolved: each is a common random binary tree, with two
	 * taxa exchanged in some of them, in which each cluster that is not a leaf is left out with the given probability
	 */
	public static List<String> relatedUnresolvedTrees(int n, int count, double leaveOut, Random random) {
		var base = clusters(tree(randomNewick(n, random, false)));
		var result = new ArrayList<String>();
		for (var i = 0; i < count; i++) {
			int a = 1 + random.nextInt(n), b = (i % 2 == 0 ? a : 1 + random.nextInt(n));
			var kept = new HashSet<BitSet>();
			for (var c : base) {
				var d = (BitSet) c.clone();
				if (c.get(a) != c.get(b)) {
					d.flip(a);
					d.flip(b);
				}
				if (d.cardinality() == 1 || random.nextDouble() >= leaveOut) kept.add(d);
			}
			result.add(newickFromClusters(kept, n));
		}
		return result;
	}

	/**
	 * the values of all options of an algorithm, by the names of their property methods
	 */
	public static Map<String, String> optionValues(Object algorithm) throws Exception {
		var result = new TreeMap<String, String>();
		for (var method : algorithm.getClass().getMethods())
			if (method.getName().startsWith("option") && method.getName().endsWith("Property") && method.getParameterCount() == 0) {
				var value = ((javafx.beans.value.ObservableValue<?>) method.invoke(algorithm)).getValue();
				result.put(method.getName(), value instanceof Object[] array ? Arrays.toString(array) : value instanceof int[] ints ? Arrays.toString(ints) : String.valueOf(value));
			}
		return result;
	}
}

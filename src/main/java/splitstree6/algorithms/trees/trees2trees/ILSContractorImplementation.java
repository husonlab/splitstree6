/*
 * ILSContractorImplementation.java Copyright (C) 2026 Daniel H. Huson
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

package splitstree6.algorithms.trees.trees2trees;

import jloda.graph.Edge;
import jloda.graph.Node;
import jloda.graph.NodeArray;
import jloda.phylo.PhyloTree;
import jloda.util.BitSetUtils;
import jloda.util.progress.ProgressListener;
import jloda.util.progress.ProgressSilent;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ILS-aware contraction of edges in a set of rooted gene trees, over complete or partial trees.
 *
 * <p>Rationale. Under the multi-species coalescent, for a species-level rooted triple ((a,b),c)
 * with internal branch length t (coalescent units):
 *
 * <pre>
 *     P(ab|c) = 1 - (2/3) e^-t ,     P(ac|b) = P(bc|a) = (1/3) e^-t
 * </pre>
 * <p>
 * i.e. the two DISCORDANT triples are exchangeable. Reticulation breaks that exchangeability.
 * The observed count vector (n1,n2,n3) of a triple across the input gene trees therefore
 * supports two orthogonal one-df tests:
 *
 * <pre>
 *   (R) resolution: n_max ~ Bin(N, 1/3), one-sided     -> rejecting means a real branch exists
 *   (S) symmetry:   n_2   ~ Bin(n2+n3, 1/2), two-sided -> rejecting means reticulation
 *                   (exactly the ABBA/BABA D-statistic test)
 * </pre>
 * <p>
 * A gene tree's resolution i of a triple is classified:
 *
 * <pre>
 *   (R) not rejected, and CF demonstrably &lt; minConcordance -> POLYTOMY (nobody's resolution is real)
 *   (R) not rejected, but CF not demonstrably low          -> UNTESTED (abstain: too little data)
 *   i is the majority                                      -> SIGNAL
 *   (S) rejected and i is the larger minority              -> SIGNAL   (introgression: keep it)
 *   otherwise                                              -> ILS_NOISE
 * </pre>
 * <p>
 * For edge e=(u,v), the triples that contracting e ALONE would unresolve are
 *
 * <pre>
 *   R(e) = { xy|z : x in C(v_i), y in C(v_j), i&lt;j children of v, z in C(u)\C(v) }
 * </pre>
 * <p>
 * (the rooted analogue of ASTRAL's quadripartition quartets). The edge score is
 * s(e) = |SIGNAL| / |tested|, and e is contracted iff s(e) &lt; tau. Everything is
 * species-tree-free: the triple frequencies of the input set supply the concordance factors.
 *
 * <p><b>Partial trees.</b> Trees need not share a taxon set. A triple contributes only to the
 * trees that contain all three of its taxa, so N is a per-triple co-occurrence count rather than
 * the number of input trees. That makes low power a per-triple property, so the resolution test
 * must not contract on failure to reject: it requires positive evidence that the concordance
 * factor lies below optionMinConcordance, and otherwise abstains (UNTESTED). Abstentions leave
 * both the numerator and denominator of s(e); an edge with no testable triple gets no score and
 * is never contracted. Taxon ids are compacted to those actually occurring in the input, so the
 * triple table is sized by the realised taxon union, not by the TaxaBlock.
 *
 * <p>Usage: {@link #scoreEdges} or {@link #analyze} once (this is the expensive part), keep the
 * result, then let the user move tau and call {@link #contract} per tree. Scores are keyed by the
 * LOWER node of the edge; absent (null) means not testable, and such edges are never contracted.
 *
 * <p>Conventions / assumptions:
 * <ul>
 * <li>trees only: a node with in-degree &gt; 1 raises an IllegalArgumentException</li>
 * <li>taxa sit on leaves; a labelled internal node is never contracted away, but the triple
 * counts do not enumerate cherries formed by a node's own taxon and a child subtree</li>
 * <li>{@link #contract} modifies the tree IN PLACE -- copy first if you need the originals</li>
 * </ul>
 */
public class ILSContractorImplementation {

	public enum Verdict {SIGNAL, ILS_NOISE, POLYTOMY, UNTESTED}

	/**
	 * per-edge diagnostics: how many of the triples resolved by the edge carried signal, how many
	 * were testable at all, and how many trees resolved a testable triple on average. An edge with
	 * a low signalFraction and a high untestedFraction was contracted for lack of co-sampling,
	 * not for lack of signal.
	 */
	public record EdgeStats(int signal, int tested, int untested, double meanTreesPerTriple) {
		public double signalFraction() {
			return tested == 0 ? Double.NaN : (double) signal / (double) tested;
		}

		public double untestedFraction() {
			var all = tested + untested;
			return all == 0 ? Double.NaN : (double) untested / (double) all;
		}
	}

	// ---------------------------------------------------------------- options
	private double optionAlphaResolution = 0.05;   // reject "species-level polytomy"
	private double optionAlphaSymmetry = 0.05;     // reject "discordance is symmetric"
	private double optionMinConcordance = 0.5;     // CF below which a branch is not worth keeping
	private double optionMinSignalFraction = 0.5;  // tau
	private int optionMinTreesPerTriple = 10;      // min N to run a test at all
	private int optionMaxTriplesPerEdge = 1000;    // 0 = exhaustive per edge
	private long optionSeed = 42;

	public ILSContractorImplementation setAlphaResolution(double v) {
		optionAlphaResolution = v;
		return this;
	}

	public ILSContractorImplementation setAlphaSymmetry(double v) {
		optionAlphaSymmetry = v;
		return this;
	}

	public ILSContractorImplementation setMinConcordance(double v) {
		optionMinConcordance = v;
		return this;
	}

	public ILSContractorImplementation setMinSignalFraction(double v) {
		optionMinSignalFraction = v;
		return this;
	}

	public ILSContractorImplementation setMinTreesPerTriple(int v) {
		optionMinTreesPerTriple = v;
		return this;
	}

	public ILSContractorImplementation setMaxTriplesPerEdge(int v) {
		optionMaxTriplesPerEdge = v;
		return this;
	}

	public ILSContractorImplementation setSeed(long v) {
		optionSeed = v;
		return this;
	}

	public double getMinSignalFraction() {
		return optionMinSignalFraction;
	}

	// ---------------------------------------------------------------- entry points

	/**
	 * Count all rooted triples over all trees and produce full per-edge diagnostics.
	 *
	 * @param nTaxa upper bound on taxon ids; only ids actually used are given a slot
	 * @return one NodeArray per input tree, keyed by the lower node of each candidate edge
	 */
	public List<NodeArray<EdgeStats>> analyze(List<PhyloTree> trees, int nTaxa, ProgressListener progress) throws IOException {
		var taxa = compactTaxonIds(trees, nTaxa);
		var counts = new TripleCounts(taxa.size());
		var data = new ArrayList<TreeData>(trees.size());
		try {
			progress.setSubtask("counting triples");
			progress.setMaximum(2L * trees.size());
			progress.setProgress(0);
			for (var tree : trees) {
				var d = new TreeData(tree, taxa.toCompact());
				data.add(d);
				counts.add(d);
				progress.incrementProgress();
			}
			progress.setSubtask("scoring edges");
			var result = new ArrayList<NodeArray<EdgeStats>>(trees.size());
			for (var i = 0; i < data.size(); i++) {
				result.add(analyzeTree(data.get(i), counts, new Random(optionSeed + 31L * i)));
				progress.incrementProgress();
			}
			return result;
		} finally {
			for (var d : data)
				d.close();
		}
	}

	/**
	 * @return one NodeArray per input tree; score.get(v) = s(e) for the edge above v, or null if
	 * not testable (root, leaf, no sibling, labelled node, or no triple with enough co-sampling)
	 */
	public List<NodeArray<Double>> scoreEdges(List<PhyloTree> trees, int nTaxa, ProgressListener progress) throws IOException {
		var stats = analyze(trees, nTaxa, progress);
		var result = new ArrayList<NodeArray<Double>>(trees.size());
		for (var i = 0; i < trees.size(); i++) {
			NodeArray<Double> score = trees.get(i).newNodeArray();
			try (var s = stats.get(i)) {
				s.forEach((v, es) -> {
					var f = es.signalFraction();
					if (!Double.isNaN(f))
						score.put(v, f);
				});
			}
			result.add(score);
		}
		return result;
	}

	public List<NodeArray<Double>> scoreEdges(List<PhyloTree> trees, int nTaxa) {
		try {
			return scoreEdges(trees, nTaxa, new ProgressSilent());
		} catch (IOException ex) {
			throw new RuntimeException(ex);   // cannot happen: ProgressSilent never cancels
		}
	}

	/**
	 * Score and then contract at the current threshold. Trees are modified in place.
	 *
	 * @return the number of edges contracted
	 */
	public int apply(List<PhyloTree> trees, int nTaxa) {
		var scores = scoreEdges(trees, nTaxa);
		var total = 0;
		for (var i = 0; i < trees.size(); i++)
			total += contract(trees.get(i), scores.get(i), optionMinSignalFraction);
		return total;
	}

	// ---------------------------------------------------------------- scoring

	private NodeArray<EdgeStats> analyzeTree(TreeData d, TripleCounts tc, Random rnd) {
		NodeArray<EdgeStats> stats = d.tree.newNodeArray();
		var root = d.tree.getRoot();
		for (var v : d.postOrder) {
			if (v == root || d.children.get(v).length < 2 || d.tree.hasTaxa(v))
				continue;
			var es = edgeStats(d, v, tc, rnd);
			if (es != null)
				stats.put(v, es);
		}
		return stats;
	}

	private EdgeStats edgeStats(TreeData d, Node v, TripleCounts tc, Random rnd) {
		var u = v.getParent();
		if (u == null)
			return null;
		var sib = siblingTaxa(d, u, v);
		if (sib.length == 0)
			return null;
		var kids = d.children.get(v);

		// child-subtree pairs with cumulative weights, for uniform sampling over R(e)
		var np = kids.length * (kids.length - 1) / 2;
		var pi = new Node[np];
		var pj = new Node[np];
		var cum = new long[np];
		var tot = 0L;
		var p = 0;
		for (var i = 0; i < kids.length; i++)
			for (var j = i + 1; j < kids.length; j++) {
				pi[p] = kids[i];
				pj[p] = kids[j];
				tot += (long) d.below.get(kids[i]).length * d.below.get(kids[j]).length;
				cum[p++] = tot;
			}
		var total = tot * sib.length;

		var acc = new Accumulator();
		if (optionMaxTriplesPerEdge > 0 && total > optionMaxTriplesPerEdge) {
			for (var s = 0; s < optionMaxTriplesPerEdge; s++) {
				var k = upperBound(cum, (long) (rnd.nextDouble() * tot));
				var bx = d.below.get(pi[k]);
				var by = d.below.get(pj[k]);
				acc.add(tc.get(bx[rnd.nextInt(bx.length)], by[rnd.nextInt(by.length)], sib[rnd.nextInt(sib.length)]));
			}
		} else {
			for (var k = 0; k < np; k++)
				for (var x : d.below.get(pi[k]))
					for (var y : d.below.get(pj[k]))
						for (var z : sib)
							acc.add(tc.get(x, y, z));
		}
		return acc.toStats();
	}

	/**
	 * folds triple verdicts into per-edge counts
	 */
	private final class Accumulator {
		private int signal, tested, untested;
		private long sumN;

		void add(int[] c) {
			var verdict = classify(c);
			if (verdict == Verdict.UNTESTED)
				untested++;
			else {
				tested++;
				sumN += c[0] + c[1] + c[2];
				if (verdict == Verdict.SIGNAL)
					signal++;
			}
		}

		EdgeStats toStats() {
			return new EdgeStats(signal, tested, untested, tested == 0 ? 0 : (double) sumN / (double) tested);
		}
	}

	/**
	 * classify the resolution xy|z (compacted taxon ids) against the counts of {x,y,z} over all trees
	 */
	public Verdict classify(TripleCounts tc, int x, int y, int z) {
		return classify(tc.get(x, y, z));
	}

	/**
	 * @param c {count of this tree's resolution, count of one alternative, count of the other}
	 */
	public Verdict classify(int[] c) {
		int self = c[0], o1 = c[1], o2 = c[2];
		var n = self + o1 + o2;
		if (n < optionMinTreesPerTriple)
			return Verdict.UNTESTED;

		var max = Math.max(self, Math.max(o1, o2));
		// (R) is there a real branch? x3 Bonferroni because max was selected among the three
		if (Math.min(1.0, 3.0 * Stats.binomTailGE(max, n, 1.0 / 3.0)) > optionAlphaResolution) {
			// no evidence FOR a branch -- but is there evidence AGAINST one, or just no power?
			// (max is inflated by the selection, which makes this lower-tail test conservative)
			if (Stats.binomTailLE(max, n, optionMinConcordance) <= optionAlphaResolution)
				return Verdict.POLYTOMY;   // concordance demonstrably below optionMinConcordance
			return Verdict.UNTESTED;       // abstain: too few trees resolve this triple to say
		}
		if (self >= max)
			return Verdict.SIGNAL;         // concordant with the dominant signal

		var otherMinor = Math.min(o1, o2); // valid because self is not the max
		if (self > otherMinor && Stats.binomSymmetryP(self, otherMinor) <= optionAlphaSymmetry)
			return Verdict.SIGNAL;         // elevated minority -> reticulation, keep
		return Verdict.ILS_NOISE;
	}

	// ---------------------------------------------------------------- contraction

	/**
	 * Contract, in place, every edge whose lower node v has score.get(v) &lt; tau. The children of v
	 * are re-attached to the parent of v and the contracted weight is pushed down, so all
	 * root-to-leaf path lengths are preserved.
	 *
	 * @return number of edges contracted
	 */
	public static int contract(PhyloTree tree, NodeArray<Double> score, double tau) {
		var doomed = new ArrayList<Node>();
		for (var v : tree.nodes()) {
			var s = score.get(v);
			if (s != null && !Double.isNaN(s) && s < tau && v != tree.getRoot() && !tree.hasTaxa(v))
				doomed.add(v);
		}
		// order is irrelevant: the live parent is re-read at each step
		for (var v : doomed)
			contractEdgeAbove(tree, v);
		return doomed.size();
	}

	private static void contractEdgeAbove(PhyloTree tree, Node v) {
		var u = v.getParent();
		if (u == null)
			return;
		var w = tree.getWeight(v.getFirstInEdge());
		var out = new ArrayList<Edge>();
		for (var f : v.outEdges())
			out.add(f);
		for (var f : out) {
			var g = tree.newEdge(u, f.getTarget());
			tree.setWeight(g, tree.getWeight(f) + w);
			// tree.setConfidence(g, tree.getConfidence(f));  // if you carry support values
		}
		tree.deleteNode(v);   // also removes the incident edges
	}

	// ---------------------------------------------------------------- helpers

	/**
	 * compacted taxon ids: only taxa actually occurring in the input trees get a slot
	 */
	private record TaxonMap(int[] toCompact, int size) {
	}

	private static TaxonMap compactTaxonIds(List<PhyloTree> trees, int nTaxa) {
		var used = new BitSet();
		for (var tree : trees)
			for (var v : tree.nodes())
				if (tree.hasTaxa(v))
					used.set(tree.getTaxon(v));
		var map = new int[Math.max(nTaxa + 1, used.length())];
		Arrays.fill(map, -1);
		var m = 0;
		for (var t : BitSetUtils.members(used))
			map[t] = m++;
		return new TaxonMap(map, m);
	}

	/**
	 * taxa below the other children of u, plus u's own taxon if it has one
	 */
	private static int[] siblingTaxa(TreeData d, Node u, Node v) {
		var uHasTaxon = d.tree.hasTaxa(u);
		var len = uHasTaxon ? 1 : 0;
		for (var w : d.children.get(u))
			if (w != v) len += d.below.get(w).length;
		var r = new int[len];
		var p = 0;
		if (uHasTaxon)
			r[p++] = d.toCompact[d.tree.getTaxon(u)];
		for (var w : d.children.get(u))
			if (w != v) {
				var bw = d.below.get(w);
				System.arraycopy(bw, 0, r, p, bw.length);
				p += bw.length;
			}
		return r;
	}

	/**
	 * first index i with cum[i] > r
	 */
	private static int upperBound(long[] cum, long r) {
		var lo = 0;
		var hi = cum.length - 1;
		while (lo < hi) {
			var mid = (lo + hi) >>> 1;
			if (cum[mid] > r) hi = mid;
			else lo = mid + 1;
		}
		return lo;
	}

	// ================================================================ per-tree scratch

	/**
	 * children as arrays (stable under later graph surgery), sorted compacted taxon sets, post-order
	 */
	public static final class TreeData implements AutoCloseable {
		public final PhyloTree tree;
		public final int[] toCompact;
		public final NodeArray<Node[]> children;
		public final NodeArray<int[]> below;
		public final List<Node> postOrder;

		public TreeData(PhyloTree tree, int[] toCompact) {
			this.tree = tree;
			this.toCompact = toCompact;
			children = tree.newNodeArray();
			below = tree.newNodeArray();
			postOrder = new ArrayList<>(tree.getNumberOfNodes());

			var stack = new ArrayDeque<Node>();
			stack.push(tree.getRoot());
			while (!stack.isEmpty()) {
				var v = stack.pop();
				postOrder.add(v);
				var list = new ArrayList<Node>();
				for (var w : v.children()) {
					if (w.getInDegree() > 1)
						throw new IllegalArgumentException("reticulate node in '" + tree.getName() + "': trees required");
					list.add(w);
					stack.push(w);
				}
				children.put(v, list.toArray(new Node[0]));
			}
			Collections.reverse(postOrder);   // pre-order reversed is a valid post-order

			for (var v : postOrder) {
				var hasTaxon = tree.hasTaxa(v);
				var len = hasTaxon ? 1 : 0;
				for (var w : children.get(v))
					len += below.get(w).length;
				var b = new int[len];
				var p = 0;
				if (hasTaxon)
					b[p++] = toCompact[tree.getTaxon(v)];
				for (var w : children.get(v)) {
					var bw = below.get(w);
					System.arraycopy(bw, 0, b, p, bw.length);
					p += bw.length;
				}
				Arrays.sort(b);
				below.put(v, b);
			}
		}

		@Override
		public void close() {
			children.close();
			below.close();
		}
	}

	// ================================================================ triple counts

	/**
	 * Flat table of rooted-triple counts over all input trees. Slot s at rank r of the sorted
	 * triple (p&lt;q&lt;s3) holds the count of the topology whose OUTGROUP is the taxon at
	 * position s: 0 -&gt; qs3|p, 1 -&gt; ps3|q, 2 -&gt; pq|s3. Ids are compacted, so n is the
	 * realised taxon union.
	 * Memory: 12 * C(n,3) bytes -&gt; n=129: 4 MB, n=250: 31 MB, n=500: 250 MB.
	 */
	public static final class TripleCounts {
		private final int n;
		private final int[] c;
		private final int[] ch2, ch3;

		public TripleCounts(int nTaxa) {
			n = nTaxa;
			var m = (long) nTaxa * (nTaxa - 1) * (nTaxa - 2) / 6;
			if (3L * m > Integer.MAX_VALUE - 8)
				throw new IllegalArgumentException("too many taxa for exhaustive triples: %,d (needs %,d GB)"
						.formatted(nTaxa, Math.round(12.0 * m / (1 << 30))));
			c = new int[(int) (3 * m)];
			ch2 = new int[nTaxa];
			ch3 = new int[nTaxa];
			for (var x = 0; x < nTaxa; x++) {
				ch2[x] = x * (x - 1) / 2;
				ch3[x] = x * (x - 1) * (x - 2) / 6;
			}
		}

		private int rank(int a, int b, int cc) {   // a < b < cc, combinadic
			return ch3[cc] + ch2[b] + a;
		}

		public void merge(TripleCounts other) {
			for (var i = 0; i < c.length; i++)
				c[i] += other.c[i];
		}

		/**
		 * Add one tree. Every resolved triple xy|z has a unique cherry node v = MRCA(x,y), so this
		 * visits each resolved triple exactly once: O(#resolved triples), no LCA queries. For a
		 * partial tree, z ranges only over the taxa present in that tree, so a triple is counted
		 * only in the trees that contain all three of its taxa.
		 */
		public void add(TreeData d) {
			var present = d.below.get(d.tree.getRoot());
			var in = new boolean[n];
			var out = new int[present.length];
			for (var v : d.postOrder) {
				var kids = d.children.get(v);
				if (kids.length < 2)
					continue;
				var bv = d.below.get(v);
				for (var x : bv)
					in[x] = true;
				var outN = 0;
				for (var z : present)
					if (!in[z]) out[outN++] = z;
				if (outN > 0)
					for (var i = 0; i < kids.length; i++)
						for (var j = i + 1; j < kids.length; j++)
							for (var x : d.below.get(kids[i]))
								for (var y : d.below.get(kids[j]))
									for (var q = 0; q < outN; q++)
										bump(x, y, out[q]);
				for (var x : bv)
					in[x] = false;
			}
		}

		private void bump(int x, int y, int z) {          // topology xy|z
			int a = x, b = y;
			if (a > b) {
				int s = a;
				a = b;
				b = s;
			}
			if (z < a) c[3 * rank(z, a, b)]++;
			else if (z < b) c[3 * rank(a, z, b) + 1]++;
			else c[3 * rank(a, b, z) + 2]++;
		}

		/**
		 * @return {count(xy|z), count(other1), count(other2)}, compacted taxon ids
		 */
		public int[] get(int x, int y, int z) {
			int a = x, b = y;
			if (a > b) {
				int s = a;
				a = b;
				b = s;
			}
			int r, slot;
			if (z < a) {
				r = rank(z, a, b);
				slot = 0;
			} else if (z < b) {
				r = rank(a, z, b);
				slot = 1;
			} else {
				r = rank(a, b, z);
				slot = 2;
			}
			var base = 3 * r;
			return new int[]{c[base + slot], c[base + (slot + 1) % 3], c[base + (slot + 2) % 3]};
		}
	}

	// ================================================================ statistics

	public static final class Stats {
		private static final ConcurrentHashMap<Double, ConcurrentHashMap<Long, Double>> CACHES = new ConcurrentHashMap<>();

		/**
		 * P(X &gt;= k) for X ~ Bin(n,p); exact for n &lt;= 2000, else normal approx with continuity correction
		 */
		public static double binomTailGE(int k, int n, double p) {
			if (k <= 0) return 1.0;
			if (k > n) return 0.0;
			var cache = CACHES.computeIfAbsent(p, x -> new ConcurrentHashMap<>());
			var key = ((long) n << 32) | (k & 0xffffffffL);
			var hit = cache.get(key);
			if (hit != null)
				return hit;
			double r;
			if (n <= 2000) {
				double s = 0, lp = Math.log(p), lq = Math.log1p(-p);
				for (var i = k; i <= n; i++)
					s += Math.exp(logChoose(n, i) + i * lp + (n - i) * lq);
				r = Math.min(1.0, s);
			} else {
				var mu = n * p;
				var sd = Math.sqrt(n * p * (1 - p));
				r = 0.5 * erfc(((k - 0.5) - mu) / (sd * Math.sqrt(2.0)));
			}
			cache.put(key, r);
			return r;
		}

		/**
		 * P(X &lt;= k) for X ~ Bin(n,p). Computed as P(Y &gt;= n-k) with Y ~ Bin(n,1-p) rather than
		 * as 1 - binomTailGE(k+1,n,p), which would lose the small tail to cancellation.
		 */
		public static double binomTailLE(int k, int n, double p) {
			if (k < 0) return 0.0;
			if (k >= n) return 1.0;
			return binomTailGE(n - k, n, 1.0 - p);
		}

		/**
		 * two-sided exact binomial test, p = 1/2, on (a,b) -- the D-statistic test
		 */
		public static double binomSymmetryP(int a, int b) {
			var n = a + b;
			if (n == 0) return 1.0;
			return Math.min(1.0, 2.0 * binomTailGE(Math.max(a, b), n, 0.5));
		}

		public static double logChoose(int n, int k) {
			return lgamma(n + 1) - lgamma(k + 1) - lgamma(n - k + 1);
		}

		private static final double[] LANCZOS = {676.5203681218851, -1259.1392167224028,
				771.32342877765313, -176.61502916214059, 12.507343278686905,
				-0.13857109526572012, 9.9843695780195716e-6, 1.5056327351493116e-7};

		public static double lgamma(double x) {
			if (x < 0.5)
				return Math.log(Math.PI / Math.abs(Math.sin(Math.PI * x))) - lgamma(1 - x);
			x -= 1;
			var a = 0.99999999999980993;
			var t = x + 7.5;
			for (var i = 0; i < LANCZOS.length; i++)
				a += LANCZOS[i] / (x + i + 1);
			return 0.5 * Math.log(2 * Math.PI) + (x + 0.5) * Math.log(t) - t + Math.log(a);
		}

		public static double erfc(double x) {
			var z = Math.abs(x);
			var t = 1.0 / (1.0 + 0.5 * z);
			var ans = t * Math.exp(-z * z - 1.26551223 + t * (1.00002368 + t * (0.37409196
																				+ t * (0.09678418 + t * (-0.18628806 + t * (0.27886807 + t * (-1.13520398
																																			  + t * (1.48851587 + t * (-0.82215223 + t * 0.17087277)))))))));
			return x >= 0 ? ans : 2.0 - ans;
		}
	}
}

/*
 * ParsimonyLabeler.java Copyright (C) 2025 Daniel H. Huson
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
package razornetaccess;

import java.util.*;
import java.util.function.Function;

/**
 * Small-parsimony labeling on a general connected, undirected graph with some nodes pre-labeled by DNA sequences.
 *
 * Standard interpretation with IUPAC ambiguity codes:
 * - At each site, each labeled character constrains the node to a set of allowed bases (A,C,G,T).
 *   Example: 'R' => {A,G}, 'N' => {A,C,G,T}.
 * - We still assign a single concrete base (A/C/G/T) to every node per site.
 * - Objective (per site): minimize number of edges whose endpoints differ (unit Potts/Hamming).
 *
 * Solvers:
 * - If the feasible alphabet size at a site is <= 2: exact s-t min-cut.
 * - Else: α-expansion (move-making) using graph cuts (standard for Potts metric).
 *
 * Host graph API assumptions (adapt if needed):
 * - nodes: Iterable<Node>
 * - edges: Iterable<Edge>
 * - u(edge), v(edge): return the two endpoints (undirected)
 */
public class ParsimonyLabeler<Node, Edge> {
	private final Iterable<Node> nodes;
	private final Iterable<Edge> edges;
	private final Function<Edge, Node> u;
	private final Function<Edge, Node> v;

	public ParsimonyLabeler(Iterable<Node> nodes, Iterable<Edge> edges,
							Function<Edge, Node> u, Function<Edge, Node> v) {
		this.nodes = nodes;
		this.edges = edges;
		this.u = u;
		this.v = v;
	}

	/**
	 * Main entry: label all nodes for all sites.
	 *
	 * @param knownSeqs map of pre-labeled nodes to their DNA sequences (all equal length).
	 *                  Sequences may contain IUPAC ambiguity codes (R,Y,S,W,K,M,B,D,H,V,N),
	 *                  also '?' and '-' which are treated as unconstrained (N).
	 * @return map of every node to its deduced DNA sequence (A/C/G/T per site)
	 */
	public Map<Node, String> labelAllSites(Map<Node, String> knownSeqs) {
		if (knownSeqs.isEmpty())
			throw new IllegalArgumentException("At least one labeled node required");

		int L = knownSeqs.values().iterator().next().length();
		for (String s : knownSeqs.values())
			if (s.length() != L)
				throw new IllegalArgumentException("All sequences must have equal length");

		// Build per-site allowed masks for labeled nodes (unlabeled nodes default to ALL)
		List<Map<Node, Integer>> siteAllowed = new ArrayList<>(L);
		for (int j = 0; j < L; j++) {
			Map<Node, Integer> m = new HashMap<>();
			for (Map.Entry<Node, String> e : knownSeqs.entrySet()) {
				char c = e.getValue().charAt(j);
				m.put(e.getKey(), iupacMask(c));
			}
			siteAllowed.add(m);
		}

		// Solve each site independently
		List<Map<Node, Character>> siteLabels = new ArrayList<>(L);
		for (int j = 0; j < L; j++) {
			Map<Node, Integer> allowedMasks = siteAllowed.get(j);

			// Determine feasible alphabet (restricting to bases that appear in any constraint)
			int union = 0;
			for (int m : allowedMasks.values()) union |= m;
			if (union == 0) throw new IllegalArgumentException("No allowed bases at site " + j);

			Set<Character> sigma = new LinkedHashSet<>();
			for (int i = 0; i < 4; i++) if ((union & BASE_MASKS[i]) != 0) sigma.add(BASES[i]);

			Map<Node, Character> labels;
			if (sigma.size() <= 1) {
				char a = sigma.iterator().next();
				labels = new HashMap<>();
				for (Node x : nodes) labels.put(x, a);
			} else if (sigma.size() == 2) {
				labels = solveBinarySiteWithAmbiguity(allowedMasks, sigma);
			} else {
				labels = solveSiteAlphaExpansionWithAmbiguity(allowedMasks, sigma);
			}

			// Final feasibility check: ensure labels respect allowed masks (defensive)
			for (Node x : nodes) {
				int mask = allowedMasks.getOrDefault(x, ALL_MASK);
				char bx = labels.get(x);
				if (!allowed(mask, bx)) labels.put(x, firstAllowedBase(mask));
			}

			siteLabels.add(labels);
		}

		// Concatenate per-site
		Map<Node, StringBuilder> agg = new HashMap<>();
		for (Node x : nodes) agg.put(x, new StringBuilder(L));
		for (int j = 0; j < L; j++) {
			Map<Node, Character> lab = siteLabels.get(j);
			for (Node x : nodes) agg.get(x).append(lab.get(x));
		}
		Map<Node, String> out = new HashMap<>();
		for (Map.Entry<Node, StringBuilder> e : agg.entrySet())
			out.put(e.getKey(), e.getValue().toString());
		return out;
	}

	// ------------------------ IUPAC ambiguity handling ------------------------

	// 4-bit mask over A,C,G,T in that order
	private static final int A_MASK = 1 << 0;
	private static final int C_MASK = 1 << 1;
	private static final int G_MASK = 1 << 2;
	private static final int T_MASK = 1 << 3;
	private static final int ALL_MASK = A_MASK | C_MASK | G_MASK | T_MASK;

	private static final char[] BASES = {'A', 'C', 'G', 'T'};
	private static final int[] BASE_MASKS = {A_MASK, C_MASK, G_MASK, T_MASK};

	private static int iupacMask(char c) {
		c = Character.toUpperCase(c);
		return switch (c) {
			// unambiguous
			case 'A' -> A_MASK;
			case 'C' -> C_MASK;
			case 'G' -> G_MASK;
			case 'T', 'U' -> T_MASK;

			// IUPAC ambiguity codes
			case 'R' -> A_MASK | G_MASK;                 // A or G
			case 'Y' -> C_MASK | T_MASK;                 // C or T
			case 'S' -> G_MASK | C_MASK;                 // G or C
			case 'W' -> A_MASK | T_MASK;                 // A or T
			case 'K' -> G_MASK | T_MASK;                 // G or T
			case 'M' -> A_MASK | C_MASK;                 // A or C

			case 'B' -> C_MASK | G_MASK | T_MASK;        // not A
			case 'D' -> A_MASK | G_MASK | T_MASK;        // not C
			case 'H' -> A_MASK | C_MASK | T_MASK;        // not G
			case 'V' -> A_MASK | C_MASK | G_MASK;        // not T

			// unknown/gap treated as unconstrained (adjust if you want gaps handled differently)
			case 'N', '?', '-' -> ALL_MASK;

			default -> throw new IllegalArgumentException("Unsupported IUPAC base: '" + c + "'");
		};
	}

	private static boolean allowed(int mask, char base) {
		int bm = switch (base) {
			case 'A' -> A_MASK;
			case 'C' -> C_MASK;
			case 'G' -> G_MASK;
			case 'T' -> T_MASK;
			default -> 0;
		};
		return (mask & bm) != 0;
	}

	private static char firstAllowedBase(int mask) {
		for (int i = 0; i < 4; i++)
			if ((mask & BASE_MASKS[i]) != 0) return BASES[i];
		throw new IllegalArgumentException("Empty allowed mask");
	}

	// ------------------------ Binary (|Σ|=2) exact via min-cut ------------------------

	/**
	 * Binary site with domain restrictions encoded as infinite unary penalties.
	 * Convention: nodes on S-side are labeled sA; on T-side labeled sB.
	 * <p>
	 * Cut unary semantics in this implementation:
	 * - If node is placed on S-side, cost includes cap(node -> T).
	 * - If node is placed on T-side, cost includes cap(S -> node).
	 * <p>
	 * Therefore:
	 * - To forbid S-side (forbid sA), set cap(node -> T) = INF.
	 * - To forbid T-side (forbid sB), set cap(S -> node) = INF.
	 */
	private Map<Node, Character> solveBinarySiteWithAmbiguity(Map<Node, Integer> allowedMasks, Set<Character> sigma) {
		Iterator<Character> it = sigma.iterator();
		char sA = it.next();
		char sB = it.next();

		FlowNetwork<Node> FN = new FlowNetwork<>();
		for (Node x : nodes) FN.addNode(x);
		FN.addSourceSink();

		final double INF = 1e12;

		for (Node x : nodes) {
			int mask = allowedMasks.getOrDefault(x, ALL_MASK);
			boolean allowA = allowed(mask, sA);
			boolean allowB = allowed(mask, sB);
			if (!allowA && !allowB) {
				throw new IllegalArgumentException("Node has no allowed base among {" + sA + "," + sB + "}");
			}
			if (!allowA) FN.addCapacity(x, FN.T, INF); // forbid S-side (sA)
			if (!allowB) FN.addCapacity(FN.S, x, INF); // forbid T-side (sB)
		}

		for (Edge e : edges) {
			FN.addUndirectedCapacity(u.apply(e), v.apply(e), 1.0);
		}

		FN.maxFlow();
		Set<Node> Sside = FN.minCutSourceSide();

		Map<Node, Character> lab = new HashMap<>();
		for (Node x : nodes) lab.put(x, Sside.contains(x) ? sA : sB);
		return lab;
	}

	// ------------------------ Multi-label (|Σ|>=3) via α-expansion ------------------------

	/**
	 * α-expansion with per-node domain restrictions (IUPAC masks) via unary INF penalties.
	 * <p>
	 * Convention for each α-move:
	 * - Binary variable y_v: 0 = keep current label; 1 = switch to α.
	 * - We interpret "v in S-side" as y_v = 1 (switch to α), matching the update rule below.
	 * <p>
	 * Unary term encoding (standard s-t cut):
	 * - cost(y=0) is paid when node is on T-side, i.e. cap(S -> v).
	 * - cost(y=1) is paid when node is on S-side, i.e. cap(v -> T).
	 */
	private Map<Node, Character> solveSiteAlphaExpansionWithAmbiguity(Map<Node, Integer> allowedMasks, Set<Character> sigma) {
		final double INF = 1e12;

		// Initialize with any feasible label per node (choose first allowed base; unconstrained choose first sigma)
		Map<Node, Character> label = new HashMap<>();
		char defaultBase = sigma.iterator().next();
		for (Node x : nodes) {
			int mask = allowedMasks.getOrDefault(x, ALL_MASK);
			label.put(x, (mask == ALL_MASK) ? defaultBase : firstAllowedBase(mask));
		}

		boolean improved;
		int iter = 0;
		do {
			improved = false;

			for (char alpha : sigma) {
				FlowNetwork<Node> FN = new FlowNetwork<>();
				for (Node x : nodes) FN.addNode(x);
				FN.addSourceSink();

				// Unary feasibility constraints for this move
				for (Node x : nodes) {
					int mask = allowedMasks.getOrDefault(x, ALL_MASK);
					char cur = label.get(x);

					double D0 = allowed(mask, cur) ? 0.0 : INF;      // keep current must be allowed
					double D1 = allowed(mask, alpha) ? 0.0 : INF;    // switching to alpha must be allowed

					if (D0 > 0) FN.addCapacity(FN.S, x, D0); // cost if y=0 (T-side)
					if (D1 > 0) FN.addCapacity(x, FN.T, D1); // cost if y=1 (S-side)
				}

				// Pairwise Potts terms for α-expansion
				for (Edge e : edges) {
					Node p = u.apply(e), q = v.apply(e);
					char Lp = label.get(p);
					char Lq = label.get(q);

					// y=0 keep, y=1 switch to alpha
					double a = (Lp != Lq) ? 1 : 0;        // V(0,0)
					double b = (Lp != alpha) ? 1 : 0;     // V(0,1)
					double c = (Lq != alpha) ? 1 : 0;     // V(1,0)
					double d = 0;                         // V(1,1)

					addSubmodularPairwise(FN, p, q, a, b, c, d);
				}

				double before = energyPotts(label);
				FN.maxFlow();
				Set<Node> Sside = FN.minCutSourceSide();

				// Apply move: nodes on S-side take alpha (y=1), others keep (y=0)
				Map<Node, Character> candidate = new HashMap<>(label);
				for (Node x : nodes) {
					if (Sside.contains(x)) candidate.put(x, alpha);
				}

				double after = energyPotts(candidate);
				if (after + 1e-9 < before) {
					label = candidate;
					improved = true;
				}
			}

			iter++;
		} while (improved && iter < 50);

		// Defensive feasibility cleanup
		for (Node x : nodes) {
			int mask = allowedMasks.getOrDefault(x, ALL_MASK);
			if (!allowed(mask, label.get(x))) label.put(x, firstAllowedBase(mask));
		}
		return label;
	}

	private double energyPotts(Map<Node, Character> lab) {
		double E = 0;
		for (Edge e : edges) {
			Node a = u.apply(e), b = v.apply(e);
			if (!Objects.equals(lab.get(a), lab.get(b))) E += 1.0;
		}
		return E;
	}

	// Add a submodular binary pairwise term V with values a=V(0,0), b=V(0,1), c=V(1,0), d=V(1,1)
	private void addSubmodularPairwise(FlowNetwork<Node> FN, Node p, Node q,
									   double a, double b, double c, double d) {
		if (a + d > b + c + 1e-9) throw new IllegalArgumentException("Pairwise term not submodular");

		double w = b + c - a - d; // >= 0
		if (w < -1e-12) throw new IllegalStateException("Negative edge capacity computed");
		if (w > 1e-12) FN.addUndirectedCapacity(p, q, w);

		double sp = c - d;
		double tp = a - b;
		double sq = b - d;
		double tq = a - c;

		if (sp > 0) FN.addCapacity(FN.S, p, sp);
		else if (sp < 0) FN.addCapacity(p, FN.T, -sp);

		if (tp > 0) FN.addCapacity(p, FN.T, tp);
		else if (tp < 0) FN.addCapacity(FN.S, p, -tp);

		if (sq > 0) FN.addCapacity(FN.S, q, sq);
		else if (sq < 0) FN.addCapacity(q, FN.T, -sq);

		if (tq > 0) FN.addCapacity(q, FN.T, tq);
		else if (tq < 0) FN.addCapacity(FN.S, q, -tq);
	}

	// ------------------------ Simple Dinic maxflow for dense-ish graphs ------------------------

	private static class FlowNetwork<N> {
		static class EdgeRec<N> {
			N to;
			int rev;
			double cap;

			EdgeRec(N to, int rev, double cap) {
				this.to = to;
				this.rev = rev;
				this.cap = cap;
			}
		}

		private final Map<N, List<EdgeRec<N>>> adj = new HashMap<>();
		N S, T;

		void addNode(N x) {
			adj.computeIfAbsent(x, k -> new ArrayList<>());
		}

		@SuppressWarnings("unchecked")
		void addSourceSink() {
			this.S = (N) new Object();
			this.T = (N) new Object();
			addNode(S);
			addNode(T);
		}

		void addCapacity(N a, N b, double cap) {
			addNode(a);
			addNode(b);
			List<EdgeRec<N>> A = adj.get(a), B = adj.get(b);
			A.add(new EdgeRec<>(b, B.size(), cap));
			B.add(new EdgeRec<>(a, A.size() - 1, 0));
		}

		void addUndirectedCapacity(N a, N b, double cap) {
			addCapacity(a, b, cap);
			addCapacity(b, a, cap);
		}

		double maxFlow() {
			double flow = 0;
			Map<N, Integer> level = new HashMap<>();
			Map<N, Integer> it = new HashMap<>();

			while (bfs(level)) {
				it.clear();
				for (N x : adj.keySet()) it.put(x, 0);
				double f;
				while ((f = dfs(S, T, Double.POSITIVE_INFINITY, level, it)) > 1e-12) flow += f;
			}
			return flow;
		}

		private boolean bfs(Map<N, Integer> level) {
			level.clear();
			Deque<N> dq = new ArrayDeque<>();
			level.put(S, 0);
			dq.add(S);

			while (!dq.isEmpty()) {
				N x = dq.removeFirst();
				for (EdgeRec<N> e : adj.get(x)) {
					if (e.cap > 1e-12 && !level.containsKey(e.to)) {
						level.put(e.to, level.get(x) + 1);
						dq.addLast(e.to);
					}
				}
			}
			return level.containsKey(T);
		}

		private double dfs(N x, N t, double f, Map<N, Integer> level, Map<N, Integer> it) {
			if (x.equals(t)) return f;

			List<EdgeRec<N>> L = adj.get(x);
			for (int i = it.get(x); i < L.size(); i++, it.put(x, i)) {
				EdgeRec<N> e = L.get(i);
				if (e.cap <= 1e-12) continue;
				if (!level.containsKey(e.to) || level.get(e.to) != level.get(x) + 1) continue;

				double pushed = dfs(e.to, t, Math.min(f, e.cap), level, it);
				if (pushed > 1e-12) {
					e.cap -= pushed;
					EdgeRec<N> r = adj.get(e.to).get(e.rev);
					r.cap += pushed;
					return pushed;
				}
			}
			return 0;
		}

		Set<N> minCutSourceSide() {
			Set<N> vis = new HashSet<>();
			Deque<N> dq = new ArrayDeque<>();
			dq.add(S);
			vis.add(S);

			while (!dq.isEmpty()) {
				N x = dq.removeFirst();
				for (EdgeRec<N> e : adj.get(x)) {
					if (e.cap > 1e-12 && !vis.contains(e.to)) {
						vis.add(e.to);
						dq.addLast(e.to);
					}
				}
			}
			return vis;
		}
	}
}
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
 * <p>
 * Objective (per site): assign one symbol to each node to minimize the number of edges whose endpoints differ.
 * For |Σ| <= 2 (binary site), we solve exactly via a single s-t min-cut.
 * For |Σ| >= 3, we use α-expansion (Boykov-Kolmogorov style move-making) with graph cuts (2-approx for Potts/Hamming).
 * <p>
 * Assumptions about host PhyloGraph API (adapt or shim to your classes):
 * - PhyloGraph#nodes: Collection<Node>
 * - PhyloGraph#edges: Collection<Edge>
 * - PhyloGraph#getAdjacentNodes(Node v): Collection<Node>
 * - PhyloGraph#getAdjacentEdges(Node v): Collection<Edge>
 * - Edge#u(), Edge#v(): Node endpoints (order arbitrary)
 * <p>
 * Integration notes:
 * - Replace Node with your concrete node type, or make this class generic <N> and pass lambdas to accessors.
 * - Known sequences provided for a subset of nodes must all have identical length L.
 * - Terminals are pinned to their known symbols at each site.
 * - Per-edge cost is 1 if symbols differ, 0 otherwise (Hamming / Potts with unit weights).
 */
public class ParsimonyLabeler<Node, Edge> {
	private final Iterable<Node> nodes;
	private final Iterable<Edge> edges;
	private final Function<Edge, Node> u;
	private final Function<Edge, Node> v;


	public ParsimonyLabeler(Iterable<Node> nodes, Iterable<Edge> edges, Function<Edge, Node> u, Function<Edge, Node> v) {
		this.nodes = nodes;
		this.edges = edges;
		this.u = u;
		this.v = v;
	}

	/**
	 * Main entry: label all nodes for all sites.
	 *
	 * @param knownSeqs map of pre-labeled nodes to their DNA sequences (all equal length)
	 * @return map of every node to its deduced DNA sequence
	 */
	public Map<Node, String> labelAllSites(Map<Node, String> knownSeqs) {
		if (knownSeqs.isEmpty()) throw new IllegalArgumentException("At least one labeled node required");
		int L = knownSeqs.values().iterator().next().length();
		for (String s : knownSeqs.values())
			if (s.length() != L) throw new IllegalArgumentException("All sequences must have equal length");

		// Prepare per-site terminal symbols
		List<Map<Node, Character>> siteTerminals = new ArrayList<>(L);
		for (int j = 0; j < L; j++) {
			Map<Node, Character> m = new HashMap<>();
			for (Map.Entry<Node, String> e : knownSeqs.entrySet()) m.put(e.getKey(), e.getValue().charAt(j));
			siteTerminals.add(m);
		}

		// Solve each site independently
		List<Map<Node, Character>> siteLabels = new ArrayList<>(L);
		for (int j = 0; j < L; j++) {
			Map<Node, Character> terminals = siteTerminals.get(j);
			Set<Character> sigma = new HashSet<>(terminals.values());
			Map<Node, Character> labels;
			if (sigma.size() <= 1) {
				// Trivial: everyone takes the single symbol
				char a = terminals.values().iterator().next();
				labels = new HashMap<>();
				for (Node v : nodes) labels.put(v, a);
			} else if (sigma.size() == 2) {
				labels = solveBinarySite(terminals);
			} else {
				labels = solveSiteAlphaExpansion(terminals, sigma);
			}
			siteLabels.add(labels);
		}

		// Concatenate per-site
		Map<Node, StringBuilder> agg = new HashMap<>();
		for (Node v : nodes) agg.put(v, new StringBuilder(L));
		for (int j = 0; j < L; j++) {
			Map<Node, Character> lab = siteLabels.get(j);
			for (Node v : nodes) agg.get(v).append(lab.get(v));
		}
		Map<Node, String> out = new HashMap<>();
		for (Map.Entry<Node, StringBuilder> e : agg.entrySet()) out.put(e.getKey(), e.getValue().toString());
		return out;
	}

	// ------------------------ Binary (|Σ|=2) exact via min-cut ------------------------

	private Map<Node, Character> solveBinarySite(Map<Node, Character> terminals) {
		// Identify the two symbols
		Iterator<Character> it = new HashSet<>(terminals.values()).iterator();
		char sA = it.next();
		char sB = it.next();

		// Build flow network: nodes + S + T
		FlowNetwork<Node> FN = new FlowNetwork<>();
		for (Node v : nodes) FN.addNode(v);
		FN.addSourceSink();

		final double INF = 1e12;

		// Pin terminals
		for (Map.Entry<Node, Character> e : terminals.entrySet()) {
			Node v = e.getKey();
			if (e.getValue() == sA) FN.addCapacity(FN.S, v, INF); // S->v infinite: v must be on S side (label sA)
			else FN.addCapacity(v, FN.T, INF);                    // v->T infinite: v must be on T side (label sB)
		}

		// Original edges cost 1 if endpoints differ -> capacity 1 on undirected edge
		for (var e : edges) {
			FN.addUndirectedCapacity(u.apply(e), v.apply(e), 1.0);
		}

		FN.maxFlow();
		Set<Node> Sside = FN.minCutSourceSide();
		Map<Node, Character> lab = new HashMap<>();
		for (Node v : nodes) lab.put(v, Sside.contains(v) ? sA : sB);
		return lab;
	}

	// ------------------------ Multi-label (|Σ|>=3) via α-expansion ------------------------

	private Map<Node, Character> solveSiteAlphaExpansion(Map<Node, Character> terminals, Set<Character> sigma) {
		// Initialize labeling: multi-source BFS from terminals by symbol
		Map<Node, Character> label = initByNearestTerminal(terminals);

		// Fix terminals permanently
		Set<Node> terminalSet = terminals.keySet();

		boolean improved;
		int iter = 0;
		do {
			improved = false;
			for (char alpha : sigma) {
				// Build binary energy for the α-expansion move and solve by min-cut
				FlowNetwork<Node> FN = new FlowNetwork<>();
				for (Node v : nodes) FN.addNode(v);
				FN.addSourceSink();

				// Pin terminals
				final double INF = 1e12;
				for (Node v : terminalSet) {
					char tv = terminals.get(v);
					// Terminals cannot change from tv
					if (tv == alpha) {
						// force v to choose "switch to α" (y=1) by INF to Source
						FN.addCapacity(FN.S, v, INF);
					} else {
						// force v to keep current (y=0) by INF to Sink
						FN.addCapacity(v, FN.T, INF);
					}
				}

				// Add pairwise Potts terms converted to s-t graph for submodular binary energy
				for (Edge e : edges) {
					Node p = u.apply(e), q = v.apply(e);
					// current labels
					char Lp = label.getOrDefault(p, terminals.getOrDefault(p, alpha));
					char Lq = label.getOrDefault(q, terminals.getOrDefault(q, alpha));

					// Define binary pairwise costs V(y_p, y_q) as per Potts α-expansion move
					// y=0: keep current label; y=1: switch to α
					double a = (Lp != Lq) ? 1 : 0;              // V(0,0)
					double b = (Lp != alpha) ? 1 : 0;           // V(0,1)
					double c = (Lq != alpha) ? 1 : 0;           // V(1,0)
					double d = 0;                               // V(1,1)

					addSubmodularPairwise(FN, p, q, a, b, c, d);
				}

				// Solve move
				double before = energyPotts(label);
				FN.maxFlow();
				Set<Node> Sside = FN.minCutSourceSide();

				// Apply move: nodes on S side take y=1 (switch to α), T side keep y=0
				Map<Node, Character> candidate = new HashMap<>(label);
				for (Node v : nodes) {
					if (terminalSet.contains(v)) continue; // already accounted by pins; keep fixed
					if (Sside.contains(v)) candidate.put(v, alpha);
				}

				double after = energyPotts(candidate);
				if (after + 1e-9 < before) {
					label = candidate;
					improved = true;
				}
			}
			iter++;
		} while (improved && iter < 50);

		// Ensure terminals are exact
		for (Node v : terminals.keySet()) label.put(v, terminals.get(v));
		return label;
	}

	private Map<Node, Character> initByNearestTerminal(Map<Node, Character> terminals) {
		// Multi-source BFS by symbol: assign each node the symbol of the nearest terminal (ties broken by order)
		Map<Node, Character> lab = new HashMap<>();
		Map<Node, Integer> dist = new HashMap<>();
		Deque<Node> dq = new ArrayDeque<>();
		for (Map.Entry<Node, Character> e : terminals.entrySet()) {
			Node v = e.getKey();
			lab.put(v, e.getValue());
			dist.put(v, 0);
			dq.add(v);
		}
		while (!dq.isEmpty()) {
			Node z = dq.removeFirst();
			int dz = dist.get(z);
			// Need adjacency; we approximate via edges
			for (Edge e : edges) {
				Node u = this.u.apply(e), v = this.v.apply(e);
				if (u.equals(z)) {
					if (!dist.containsKey(v)) {
						dist.put(v, dz + 1);
						lab.put(v, lab.get(z));
						dq.addLast(v);
					}
				} else if (v.equals(z)) {
					if (!dist.containsKey(u)) {
						dist.put(u, dz + 1);
						lab.put(u, lab.get(z));
						dq.addLast(u);
					}
				}
			}
		}
		return lab;
	}

	private double energyPotts(Map<Node, Character> lab) {
		double E = 0;
		for (Edge e : edges) {
			Node u = this.u.apply(e), v = this.v.apply(e);
			if (!Objects.equals(lab.get(u), lab.get(v))) E += 1.0;
		}
		return E;
	}

	// Add a submodular binary pairwise term V with values a=V(0,0), b=V(0,1), c=V(1,0), d=V(1,1)
	private void addSubmodularPairwise(FlowNetwork<Node> FN, Node p, Node q, double a, double b, double c, double d) {
		// Submodularity check (allow small epsilon)
		if (a + d > b + c + 1e-9) throw new IllegalArgumentException("Pairwise term not submodular");
		// Edge capacity
		double w = b + c - a - d; // >= 0
		if (w < -1e-12) throw new IllegalStateException("Negative edge capacity computed");
		if (w > 1e-12) FN.addUndirectedCapacity(p, q, w);
		// T-link adjustments
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

		void addNode(N v) {
			adj.computeIfAbsent(v, k -> new ArrayList<>());
		}

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
				for (N v : adj.keySet()) it.put(v, 0);
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
				N u = dq.removeFirst();
				for (EdgeRec<N> e : adj.get(u))
					if (e.cap > 1e-12 && !level.containsKey(e.to)) {
						level.put(e.to, level.get(u) + 1);
						dq.addLast(e.to);
					}
			}
			return level.containsKey(T);
		}

		private double dfs(N u, N t, double f, Map<N, Integer> level, Map<N, Integer> it) {
			if (u.equals(t)) return f;
			List<EdgeRec<N>> L = adj.get(u);
			for (int i = it.merge(u, 0, Integer::sum); i < L.size(); i = it.merge(u, 1, Integer::sum)) {
				EdgeRec<N> e = L.get(i);
				if (e.cap <= 1e-12) continue;
				if (!level.containsKey(e.to) || level.get(e.to) != level.get(u) + 1) continue;
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
			// After maxflow, BFS from S in residual graph
			Set<N> vis = new HashSet<>();
			Deque<N> dq = new ArrayDeque<>();
			dq.add(S);
			vis.add(S);
			while (!dq.isEmpty()) {
				N u = dq.removeFirst();
				for (EdgeRec<N> e : adj.get(u))
					if (e.cap > 1e-12 && !vis.contains(e.to)) {
						vis.add(e.to);
						dq.addLast(e.to);
					}
			}
			return vis;
		}
	}
}

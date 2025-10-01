package splitstree6.algorithms.distances.distances2network;


import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

/**
 * CactusRealizer (single-file, source-mode runnable)
 * <p>
 * High-level picture
 * ------------------
 * Given a distance matrix D (metric on X), we want a weighted undirected
 * graph (G_output) whose shortest-path distances exactly realize D.
 * <p>
 * We do this in two stages:
 * (A1) Build the complete weighted graph on D and prune any edge (i,j)
 * that is dominated by a 2-hop path i-k-j (triangle inequality tight).
 * -> This produces G_output directly when D already needs no extra
 * "auxiliary" (Steiner-like) vertices.
 * <p>
 * (A2) Optionally (and by default in run()), we first extend the distance
 * matrix by introducing auxiliary vertices that "tighten" distances
 * (the compactification/auxiliary phase). This mirrors your Python
 * pipeline: auxiliary points may be added; "shadow" vertices merged;
 * local graph structure (scaffold) is updated along the way.
 * Then we execute (A1) on the extended D.
 * <p>
 * Key data structures
 * -------------------
 * - WeightedGraph: adjacency implemented as Map<UEdge, Double>.
 * - UEdge: undirected edge key storing endpoints in (min,max) order.
 * <p>
 * Terminology
 * -----------
 * - K_minus: local graph that keeps only edges not dominated by a 2-hop path
 * inside the currently processed vertex set (V ∪ V_aux).
 * - graphPiece: edges incident to vertices of degree ≤ 2 in K_minus; these are
 * "compacted" (finalized) locally and moved into the scaffold.
 * - scaffold: global graph that accumulates local decisions from all pieces.
 * <p>
 * Files I/O
 * ---------
 * - Input CSV: first line is 'n', followed by 'n' lines, each with 'n' comma-
 * separated numbers representing the symmetric distance matrix with zeros
 * on the diagonal.
 * <p>
 * Usage:
 * java CactusRealizer.java input.csv [output.txt]
 * <p>
 * Authors: Momoko Hayamizu and ???
 */
public class CactusRealizer {
	// Tolerance used for “<=” comparisons on distances (triangle tightness, equality checks, etc.)
	public static double EPSILON = 1e-12;
	public static double MIN_DISTANCE = 1e-12;

	static {
		var epsProp = System.getProperty("cactus.epsilon");
		if (epsProp != null) {
			try {
				EPSILON = Double.parseDouble(epsProp);
			} catch (NumberFormatException ignored) {
			}
		}
		var minDistance = System.getProperty("cactus.min.distance");
		if (minDistance != null) {
			try {
				MIN_DISTANCE = Double.parseDouble(minDistance);
			} catch (NumberFormatException ignored) {
			}
		}
	}

	/**
	 * Entry point for the algorithm if you already have the distance matrix in memory.
	 * The default pipeline is: (A2) extend/compactify -> (A1) build+prune.
	 *
	 * @param distances input distance matrix (D_0)
	 * @return the realized graph (after pruning) built on the (possibly) extended D
	 */
	public static WeightedGraph run(double[][] distances) {
		// Step A2: extend/compactify distances (may add auxiliary vertices; may merge shadows).
		var ext = calculate_extended_distance_matrix(distances);
		var D = ext.D;

		// Step A1: start from the complete graph on the (possibly extended) metric D.
		var graph = buildComplete(D);

		var before = graph.numberOfEdges();

		// Remove edges (i,j) with a strictly shorter or equal 2-hop alternative i-k-j.
		pruneRedundantEdges(D, graph);

		var after = graph.numberOfEdges();
		System.out.printf("Pruned %d of %d edges (kept %d)%n", (before - after), before, after);
		return graph;
	}


	/* ===================== Algorithm 1: complete -> prune ===================== */

	/**
	 * Build the weighted complete graph on D (edge (i,j) has weight D[i][j]).
	 * We keep 5-digit rounding to mirror your Python output formatting.
	 */
	public static WeightedGraph buildComplete(double[][] D) {
		var n = D.length;
		var graph = new WeightedGraph(n);
		for (int i = 0; i < n; i++) {
			for (int j = i + 1; j < n; j++) {
				graph.putEdge(i, j, round5(D[i][j]));
			}
		}
		return graph;
	}

	/**
	 * Remove every edge (i,j) that is "redundant" given D:
	 * If there exists k with k!=i,j such that D[i,k] > 0 and D[k,j] > 0 and
	 * D[i,k] + D[k,j] <= D[i,j] (+ tiny epsilon), then the edge is unnecessary:
	 * a shortest path i->k->j is no worse than the direct edge.
	 * <p>
	 * NOTE: epsilon 1e-12 keeps the condition faithful to exact checks; if
	 * your CSVs are noisy, consider 1e-6 to avoid floating-point pitfalls.
	 */
	public static void pruneRedundantEdges(double[][] D, WeightedGraph g) {
		var n = D.length;
		var rm = new ArrayList<UEdge>();
		for (var e : g.listEdges()) {
			var i = e.u();
			var j = e.v();
			var redundant = false;
			for (var k = 0; k < n; k++) {
				if (k == i || k == j) continue;
				var dik = D[i][k];
				var dkj = D[k][j];
				var dij = D[i][j];
				// standard domination test (triangle inequality tight via k)
				if (dik > 0 && dkj > 0 && dik + dkj <= dij + EPSILON) {
					redundant = true;
					break;
				}
			}
			if (redundant) rm.add(e);
		}
		for (var e : rm) g.removeEdge(e.u, e.v);
	}

	/**
	 * Rounds to 5 decimal places to match the Python's edge-weight pretty-printing.
	 */
	private static double round5(double x) {
		return Math.round(x * 1e5) / 1e5;
	}


	/* ===================== Helpers equivalent to cactus_utils bits ===================== */

	/**
	 * Utility: returns a new (n+1)x(n+1) matrix with D in the top-left block (zeros elsewhere).
	 */
	static double[][] padMatrixByOne(double[][] D) {
		var n = D.length;
		var R = new double[n + 1][n + 1];
		for (var i = 0; i < n; i++) {
			System.arraycopy(D[i], 0, R[i], 0, n);
		}
		return R;
	}

	/**
	 * Connected connectedComponents of size ≥ 3 (used to continue compactification only on non-trivial pieces).
	 */
	static List<Set<Integer>> nontrivialCc(WeightedGraph g) {
		return g.connectedComponents().stream().filter(cc -> cc.size() >= 3).collect(Collectors.toList());
	}

	/**
	 * Identify "shadow" vertices.
	 * j is a shadow of some previous i if:
	 * - D[i][j] == 0  (coincident in the metric)
	 * - the entire rows are equal: D[i][*] == D[j][*]
	 * <p>
	 * We then remove such rows/columns later and reindex vertices accordingly.
	 */
	static List<Integer> getShadowVertices(double[][] D) {
		var n = D.length;
		var removed = new boolean[n];
		var shadows = new ArrayList<Integer>();
		outer:
		for (var j = 0; j < n; j++) {
			if (removed[j]) continue;
			for (var i = 0; i < j; i++) {
				if (removed[i]) continue;
				if (Math.abs(D[i][j]) > MIN_DISTANCE) continue;
				var same = true;
				for (var k = 0; k < n; k++) {
					if (Math.abs(D[i][k] - D[j][k]) > MIN_DISTANCE) {
						same = false;
						break;
					}
				}
				if (same) {
					removed[j] = true;
					shadows.add(j);
					continue outer;
				}
			}
		}
		return shadows;
	}

	/**
	 * Remove rows/cols whose indices are in 'shadows' (ascending), returning a smaller matrix.
	 * This logically merges shadow vertices with their "parents".
	 */
	static double[][] shrinkDistanceMatrix(double[][] D, List<Integer> shadows) {
		if (shadows.isEmpty()) return D;
		var n = D.length;
		var gone = new boolean[n];
		for (var s : shadows) {
			gone[s] = true;
		}
		var m = n - shadows.size();
		var R = new double[m][m];
		var ri = 0;
		for (var i = 0; i < n; i++) {
			if (gone[i]) continue;
			var rj = 0;
			for (var j = 0; j < n; j++) {
				if (gone[j])
					continue;
				R[ri][rj] = D[i][j];
				rj++;
			}
			ri++;
		}
		return R;
	}

	/**
	 * Remap graph vertex indices after removing shadows.
	 * Build a mapping oldIndex -> newIndex, then rebuild all edges with the new indices.
	 */
	static WeightedGraph shrinkGraph(double[][] D_before, WeightedGraph g, List<Integer> shadows) {
		if (shadows.isEmpty())
			return g;
		var n = D_before.length;
		var m = n - shadows.size();

		// build old->new mapping
		var map = new int[n];
		Arrays.fill(map, -1);
		var gone = new boolean[n];
		for (int s : shadows) {
			gone[s] = true;
		}
		var nxt = 0;
		for (int i = 0; i < n; i++) {
			if (!gone[i]) map[i] = nxt++;
		}

		// construct remapped graph
		var ng = new WeightedGraph(m);
		for (var e : g.listEdges()) {
			var u = map[e.u];
			var v = map[e.v];
			if (u >= 0 && v >= 0) ng.putEdge(u, v, g.getWeight(e));
		}
		return ng;
	}

	/**
	 * Reindex a set of integers after removing indices in 'shadows'.
	 * This keeps any book-keeping sets (existing, compacted, V) consistent with the shrink.
	 */
	static Set<Integer> reindexSetMinusList(Set<Integer> V, List<Integer> shadows) {
		if (shadows.isEmpty())
			return new HashSet<>(V);
		var n = (V.isEmpty() ? 0 : (Collections.max(V) + 1));
		// gone size must dominate max(V) and max(shadows) to compute shifts safely
		var gone = new boolean[Math.max(n, (Collections.max(shadows) + 1)) + 1];
		for (var s : shadows) {
			if (s >= 0 && s < gone.length) {
				gone[s] = true;
			}
		}
		// shift[i] = #removed indices ≤ i
		var shift = new int[gone.length];
		var c = 0;
		for (var i = 0; i < gone.length; i++) {
			if (gone[i]) {
				c++;
			}
			shift[i] = c;
		}
		var R = new HashSet<Integer>();
		for (int x : V) {
			int lost = (x < shift.length ? shift[x] : shift[shift.length - 1]); // safe for x≥len
			R.add(x - lost);
		}
		return R;
	}


	/**
	 * Compute the compactification (slack) index of a vertex x within V and
	 * return the minimizing pair (y, z).
	 * <p>
	 * For a metric D, the slack at x with respect to (y, z) is:
	 * s(x; y, z) = (D[x,y] + D[x,z] - D[y,z]) / 2
	 * <p>
	 * The method chooses the pair (y, z) that minimizes s and returns
	 * max(s, 0) (i.e., truncated to non-negative).
	 * <p>
	 * Intuition:
	 * - If s(x; y, z) > 0 for the minimizing pair, an auxiliary (Steiner-like)
	 * point can represent x more tightly with respect to (y, z).
	 *
	 * @param V set of candidate vertices containing x
	 * @param x the vertex whose slack is computed
	 * @param D distance matrix
	 * @return SlackResult with non-negative slack and the argmin pair (y, z)
	 */
	static SlackResult getSlackIndexAndPair(Set<Integer> V, int x, double[][] D) {
		var others = V.stream().filter(v -> v != x).sorted().toList();
		if (others.size() < 2) {
			return new SlackResult(0.0, -1, -1);
		}
		var best = Double.POSITIVE_INFINITY;
		var bestY = -1;
		var bestZ = -1;
		for (var i = 0; i < others.size(); i++) {
			var y = others.get(i);
			for (var j = i + 1; j < others.size(); j++) {
				var z = others.get(j);
				var val = (D[x][y] + D[x][z] - D[y][z]) / 2.0;
				if (val < best) {
					best = val;
					bestY = y;
					bestZ = z;
				}
			}
		}
		return new SlackResult(Math.max(0, best), bestY, bestZ);
	}

	/**
	 * Container for slack and argmin pair (y,z).
	 */
	record SlackResult(double slack, int y, int z) {
	}

	/**
	 * Part 1: Identify and add auxiliary vertices to tighten D over V.
	 * <p>
	 * For each x in V:
	 * 1) Find minimizing pair (y, z) and slack s = s(x; y, z).
	 * 2) If s > 0 and basic feasibility checks pass, add a new auxiliary
	 * vertex "aux" with distances:
	 * D[x,aux] = s,
	 * D[y,aux] = max(D[y,x] - s, 0),
	 * D[z,aux] = max(D[z,x] - s, 0),
	 * D[a,aux] = max(D[a,x] - s, D[a,y] - D[y,aux], D[a,z] - D[z,aux]) for all a.
	 * (This mirrors the Python construction ensuring metric consistency.)
	 * 3) Reject the new aux if it would collapse onto any vertex already in
	 * V ∪ V_aux (distance zero to an existing vertex).
	 * <p>
	 * Only if an aux passes checks do we actually reallocate a (n+1)x(n+1) matrix.
	 */
	static AuxResult find_auxiliary_vertices(double[][] D, Set<Integer> V, Set<Integer> V_aux) {
		var curD = D;
		var curAux = new HashSet<Integer>(V_aux);

		for (int x : V) {
			SlackResult sr = getSlackIndexAndPair(V, x, curD);
			if (sr.slack > 0) {
				double dist_x_aux = sr.slack;
				double dist_y_aux = Math.max(0, curD[sr.y][x] - dist_x_aux);
				double dist_z_aux = Math.max(0, curD[sr.z][x] - dist_x_aux);
				// if aux would coincide with y or z, skip
				if (dist_y_aux == 0.0 || dist_z_aux == 0.0) continue;

				boolean skipAux = false;
				Map<Integer, Double> distToAux = new HashMap<>();
				Set<Integer> allCurrent = new HashSet<>();
				for (int a = 0; a < curD.length; a++) allCurrent.add(a);
				allCurrent.addAll(curAux);

				// Compute distance from every a ≠ x,y,z to aux so that
				// the metric conditions remain consistent.
				for (int a : diff(allCurrent, setOf(x, sr.y, sr.z))) {
					double dist_a_aux = Math.max(0, Math.max(
							Math.max(curD[a][x] - dist_x_aux, curD[a][sr.y] - dist_y_aux),
							curD[a][sr.z] - dist_z_aux));
					distToAux.put(a, dist_a_aux);
					// If aux would land exactly on an existing vertex in V ∪ V_aux, reject it.
					if ((V.contains(a) || curAux.contains(a)) && dist_a_aux == 0.0) {
						skipAux = true;
						break;
					}
				}
				if (skipAux) continue;

				// Accept aux: reallocate D and write distances.
				int aux = curD.length;
				double[][] Dp = padMatrixByOne(curD);

				Dp[x][aux] = Dp[aux][x] = dist_x_aux;
				Dp[sr.y][aux] = Dp[aux][sr.y] = dist_y_aux;
				Dp[sr.z][aux] = Dp[aux][sr.z] = dist_z_aux;
				for (var e : distToAux.entrySet()) {
					int a = e.getKey();
					double d = e.getValue();
					Dp[a][aux] = Dp[aux][a] = d;
				}

				curD = Dp;
				curAux.add(aux);
			}
		}
		return new AuxResult(curD, curAux);
	}

	/**
	 * Container for the possibly expanded matrix and the set of auxiliary vertices.
	 */
	record AuxResult(double[][] D, Set<Integer> V_aux) {
	}

	/**
	 * Part 2: Build a local graph piece and the K_minus graph on V ∪ V_aux.
	 * <p>
	 * K_minus keeps exactly those edges (x,y) that cannot be realized strictly better
	 * as x—z—y within V_processed (no dominated edges by a 2-hop path).
	 * <p>
	 * graphPiece aggregates edges adjacent to low-degree (≤2) vertices in K_minus.
	 * These are "compacted" and moved to the scaffold, shrinking the local problem.
	 */
	static BuildPieceResult build_graph_piece(double[][] D, Set<Integer> V, Set<Integer> V_aux, Set<Integer> compacted) {
		var V_processed = new HashSet<>(V);
		V_processed.addAll(V_aux);

		var graphPiece = new WeightedGraph(D.length);
		var K_minus = new WeightedGraph(D.length);

		// Evaluate every pair (x,y) in V_processed:
		// keep (x,y) in K_minus only if no z supplies a shorter/equal 2-hop.
		var list = V_processed.stream().sorted().toList();
		for (var i = 0; i < list.size(); i++) {
			int x = list.get(i);
			for (var j = i + 1; j < list.size(); j++) {
				int y = list.get(j);
				var dominated = false;
				for (var z : V_processed) {
					if (z == x || z == y) continue;
					if (D[x][z] != 0.0 && D[z][y] != 0.0 && D[x][z] + D[z][y] <= D[x][y] + EPSILON) {
						dominated = true;
						break;
					}
				}
				if (!dominated) K_minus.addEdge(x, y);
			}
		}

		int prevCount = compacted.size();

		// Any vertex with degree ≤ 2 is "final" locally. Move its incident edges
		// into graphPiece, and mark the vertex as compacted.
		for (var x : V_processed) {
			if (K_minus.degree(x) <= 2) {
				compacted.add(x);
				if (K_minus.degree(x) >= 1) {
					for (var nb : K_minus.neighbors(x)) {
						graphPiece.addEdge(x, nb);
					}
				}
			}
		}

		// Remove compacted vertices from K_minus entirely (they're finalized).
		for (var v : new HashSet<>(compacted)) {
			for (var nb : new HashSet<>(K_minus.neighbors(v))) K_minus.removeEdge(v, nb);
		}

		return new BuildPieceResult(graphPiece, K_minus, V_processed, prevCount);
	}

	/**
	 * Container for the products of the local K_minus/graphPiece build.
	 */
	record BuildPieceResult(WeightedGraph graphPiece, WeightedGraph K_minus, Set<Integer> V_processed,
							int prevCompactedCount) {
	}

	/**
	 * Part 2.5: Post-processing after building K_minus and graphPiece.
	 * <p>
	 * If 'compacted' did NOT grow:
	 * - Let T = V_processed \ compacted. If T non-empty:
	 * * mark all of V_processed compacted;
	 * * move any K_minus edges internal to T into the scaffold;
	 * else:
	 * * signal 'shouldContinue' to bail out early for this V_active.
	 * <p>
	 * This prevents local stalling: if nothing changed, we finalize and move on.
	 */
	static PostProcessResult handle_post_build_processing(Set<Integer> V_processed, Set<Integer> compacted, int prevCompactedCount, WeightedGraph K_minus, WeightedGraph scaffold) {
		var shouldContinue = false;
		if (compacted.size() == prevCompactedCount) {
			var tmp = diff(V_processed, compacted);
			if (!tmp.isEmpty()) {
				compacted.addAll(V_processed);
				var list = tmp.stream().sorted().toList();
				for (var i = 0; i < list.size(); i++) {
					var x = list.get(i);
					for (var j = i + 1; j < list.size(); j++) {
						var y = list.get(j);
						if (K_minus.hasEdge(x, y)) {
							K_minus.removeEdge(x, y);
							scaffold.addEdge(x, y);
						}
					}
				}
			} else {
				shouldContinue = true; // early stop for this V_active
			}
		}
		return new PostProcessResult(compacted, shouldContinue);
	}

	/**
	 * Container for compacted set and a continue/bail flag.
	 */
	record PostProcessResult(Set<Integer> compacted, boolean shouldContinue) {
	}

	/**
	 * Part 0: Identify high-degree neighborhoods around V and clear local edges.
	 * <p>
	 * For each x in V with degree ≥ 3 in 'graph':
	 * - Neighborhood N = {x} ∪ neighbors(x) is enqueued to be compactified.
	 * - In a working copy (prunedGraph), remove all edges among pairs of N,
	 * creating a "fresh local slate" for the compactification process.
	 * <p>
	 * We do NOT modify 'graph' in place; we return a pruned copy.
	 */
	static PrepResult prepare_subsets_for_compactification(Set<Integer> V, WeightedGraph graph) {
		Set<Set<Integer>> neighborSets = new HashSet<>();
		var given = graph.copy();
		var pruned = graph.copy();

		for (var x : V) {
			if (given.degree(x) <= 2) continue;
			var xs = given.neighbors(x);
			var xN = new HashSet<>(xs);
			xN.add(x);
			neighborSets.add(Collections.unmodifiableSet(xN));
			var arr = xN.stream().sorted().toList();
			// nuke all internal edges among the local neighborhood
			for (var i = 0; i < arr.size(); i++) {
				var y = arr.get(i);
				for (var j = i + 1; j < arr.size(); j++) {
					var z = arr.get(j);
					if (pruned.hasEdge(y, z)) pruned.removeEdge(y, z);
				}
			}
		}
		return new PrepResult(neighborSets, pruned);
	}

	/**
	 * Container for neighborhood sets and a pruned working graph.
	 */
	record PrepResult(Set<Set<Integer>> neighborSets, WeightedGraph prunedGraph) {
	}

	/**
	 * Part 3: Iteratively compactify one neighborhood (and its descendants).
	 * <p>
	 * Queue-based process:
	 * For each dequeued V_active:
	 * (1) find_auxiliary_vertices: possibly grow D by adding aux nodes.
	 * (2) build_graph_piece: compute local K_minus and graphPiece, add the
	 * graphPiece edges to the global scaffold.
	 * (3) handle_post_build_processing: if nothing changed, finalize locally
	 * or queue non-trivial connected connectedComponents of K_minus (size ≥ 3).
	 * (4) Move any 2-vertex connected-connectedComponents (single edges) to scaffold.
	 * (5) Mark leftover degree ≤ 2 vertices as compacted/done.
	 * <p>
	 * Stops when there are no more local subsets to process.
	 */
	static CompactResult repetitive_compactification(double[][] D, Set<Integer> V_initial, Set<Integer> compacted, WeightedGraph scaffold) {
		var queue = new ArrayDeque<Set<Integer>>();
		queue.add(new HashSet<>(V_initial));
		var curD = D;
		var curComp = (Set<Integer>) new HashSet<>(compacted);
		var curScaffold = scaffold;

		while (!queue.isEmpty()) {
			var V_active = queue.pollFirst();

			// Part 1: auxiliary vertices (may expand D)
			var ar = find_auxiliary_vertices(curD, V_active, new HashSet<>());
			curD = ar.D;
			var V_aux = ar.V_aux;

			// Part 2: local K_minus + graphPiece on V_active ∪ V_aux
			var br = build_graph_piece(curD, V_active, V_aux, curComp);
			curScaffold = ensureNodes(curScaffold, curD.length); // grow scaffold capacity if D grew
			for (var e : br.graphPiece.listEdges()) curScaffold.addEdge(e.u, e.v);

			// Part 2.5: finalize or continue
			var pr = handle_post_build_processing(br.V_processed, curComp, br.prevCompactedCount, br.K_minus, curScaffold);
			curComp = pr.compacted;
			if (pr.shouldContinue) continue;

			// Move 2-vertex CCs of K_minus to scaffold (they are just stand-alone edges).
			for (var cc : br.K_minus.connectedComponents()) {
				if (cc.size() == 2) {
					var it = cc.iterator();
					var x = it.next();
					var y = it.next();
					if (br.K_minus.hasEdge(x, y)) {
						br.K_minus.removeEdge(x, y);
						curScaffold.addEdge(x, y);
					}
				}
			}
			// Enqueue nontrivial CCs (size ≥ 3) for further compactification.
			queue.addAll(nontrivialCc(br.K_minus));

			// Any vertex not in a nontrivial CC and with deg ≤ 2 is done locally.
			var nontrivVertices = nontrivialCc(br.K_minus).stream().flatMap(Set::stream).collect(Collectors.toSet());
			for (var v = 0; v < curD.length; v++) {
				if (!nontrivVertices.contains(v) && br.K_minus.degree(v) <= 2) curComp.add(v);
			}
		}
		return new CompactResult(curD, curComp, curScaffold);
	}

	/**
	 * Container for updated D, compacted set, and the scaffold.
	 */
	record CompactResult(double[][] D, Set<Integer> compacted, WeightedGraph scaffold) {
	}

	/**
	 * Perform one round of distance-matrix extension and scaffold update
	 * on a given vertex subset V.
	 * <p>
	 * Steps:
	 * 1. Ensure bookkeeping sets are initialized; mark V as processed.
	 * 2. Identify high-degree neighborhoods around V and reset local edges
	 * in the scaffold (prepare_subsets_for_compactification).
	 * 3. For each such neighborhood, run the iterative compactification
	 * process (repetitive_compactification) which may:
	 * - Add auxiliary vertices to the distance matrix,
	 * - Move edges from temporary graphs into the scaffold,
	 * - Mark vertices as compacted (finalized).
	 * 4. Detect and remove "shadow" vertices (rows/columns of D that are
	 * identical and mutually distance 0), shrinking both the matrix and
	 * the scaffold graph.
	 * 5. Reindex all sets (V, existing, compacted) to match the shrunken
	 * matrix/graph.
	 */
	static ExtendResult extend_distance_matrix(double[][] D, Set<Integer> V, Set<Integer> existingVertices, Set<Integer> compactedVertices, WeightedGraph scaffold) {
		if (existingVertices == null) existingVertices = new HashSet<>();
		if (compactedVertices == null) compactedVertices = new HashSet<>();
		existingVertices.addAll(V);

		// Part 0: prep local neighborhoods & wipe internal edges locally in a pruned copy.
		var prep = prepare_subsets_for_compactification(V, scaffold);
		scaffold = prep.prunedGraph;

		var curD = D;
		var curComp = compactedVertices;
		var curScaffold = scaffold;

		// Process each high-degree neighborhood in turn.
		for (var xNeighbors : prep.neighborSets) {
			var cr = repetitive_compactification(curD, xNeighbors, curComp, curScaffold);
			curD = cr.D;
			curComp = cr.compacted;
			curScaffold = cr.scaffold;
		}

		// Merge "shadow" vertices (rows identical; D[i,j]==0).
		var D_before = copyOf(curD);
		var shadows = getShadowVertices(D_before);
		curD = shrinkDistanceMatrix(D_before, shadows);
		curScaffold = shrinkGraph(D_before, curScaffold, shadows);

		// Reindex the book-keeping sets to match the shrunken D.
		var V2 = reindexSetMinusList(V, shadows);
		var comp2 = reindexSetMinusList(curComp, shadows);
		var exist2 = reindexSetMinusList(existingVertices, shadows);

		return new ExtendResult(curD, exist2, comp2, curScaffold, V2);
	}

	/**
	 * Container for the results of one extension round.
	 */
	record ExtendResult(double[][] D, Set<Integer> existing, Set<Integer> compacted, WeightedGraph scaffold,
						Set<Integer> V_reindexed) {
	}

	/**
	 * Calculate the extended distance matrix:
	 * - Run one extension pass on the original vertex set X.
	 * - Run a second pass on all current vertices (in case aux were added).
	 * - Keep looping on newly appearing vertices until no change.
	 * <p>
	 * Returns the final D plus the final scaffold and bookkeeping sets.
	 */
	static CalcExtResult calculate_extended_distance_matrix(double[][] D_initial) {
		var D = copyOf(D_initial);
		var n = D.length;
		var X = rangeSet(0, n);
		var scaffold = completeGraph(n);
		var compacted = (Set<Integer>) new HashSet<Integer>();
		var existing = (Set<Integer>) new HashSet<Integer>();

		// Step 1: process original X
		ExtendResult er1 = extend_distance_matrix(D, X, existing, compacted, scaffold);
		D = er1.D;
		existing = er1.existing;
		compacted = er1.compacted;
		scaffold = er1.scaffold;

		// Step 2: process all current vertices
		var all = rangeSet(0, D.length);
		var er2 = extend_distance_matrix(D, all, existing, compacted, scaffold);
		D = er2.D;
		existing = er2.existing;
		compacted = er2.compacted;
		scaffold = er2.scaffold;

		// Step 3: keep going if new vertices were added by aux
		while (true) {
			var currentAll = rangeSet(0, D.length);
			var newVertices = diff(currentAll, existing);
			if (newVertices.isEmpty()) break;
			ExtendResult er = extend_distance_matrix(D, newVertices, existing, compacted, scaffold);
			D = er.D;
			existing = er.existing;
			compacted = er.compacted;
			scaffold = er.scaffold;
		}
		return new CalcExtResult(D, scaffold, X, existing, compacted);
	}

	/**
	 * Container for the final extended D and associated structures.
	 */
	record CalcExtResult(double[][] D, WeightedGraph scaffold, Set<Integer> X, Set<Integer> existing,
						 Set<Integer> compacted) {
	}

	/* ===================== Basic graph (undirected; weights optional) ===================== */

	/**
	 * Minimal undirected weighted graph.
	 * - Vertices are implicit: 0..n-1.
	 * - Edges are stored as keys UEdge(u,v) with u<v in a HashMap.
	 * - Provides degree/neighbor queries and a stable edge listing.
	 */
	public static final class WeightedGraph {
		private final int n;
		private final Map<UEdge, Double> w = new HashMap<>();// edges i<j

		public WeightedGraph(int n) {
			this.n = n;
		}

		public int size() {
			return n;
		}

		/**
		 * Insert/replace an edge weight; self-loops are forbidden by UEdge constructor.
		 */
		public void putEdge(int i, int j, double weight) {
			w.put(new UEdge(i, j), weight);
		}

		public void putEdge(UEdge e, double weight) {
			w.put(e, weight);
		}

		/**
		 * Unweighted add is just weight=1.0 (used when carrying structure only).
		 */
		public void addEdge(int i, int j) {
			putEdge(i, j, 1.0);
		}

		public void addEdge(UEdge e) {
			w.put(e, 1.0);
		}

		/**
		 * Membership check for an edge, ignoring orientation.
		 */
		public boolean hasEdge(int i, int j) {
			return w.containsKey(new UEdge(i, j));
		}

		public Double getWeight(int i, int j) {
			return w.get(new UEdge(i, j));
		}

		public double getWeight(UEdge e) {
			return w.get(e);
		}

		public void setWeight(UEdge e, double weight) {
			w.put(e, weight);
		}

		public void removeEdge(int i, int j) {
			w.remove(new UEdge(i, j));
		}

		/**
		 * Number of stored edges.
		 */
		public int numberOfEdges() {
			return w.size();
		}

		/**
		 * Return a stable, sorted list of edges.
		 * Sorting is important for deterministic output.
		 */
		public Collection<UEdge> listEdges() {
			var edges = new ArrayList<>(w.keySet());
			edges.sort(UEdge::compareTo);
			return edges;
		}

		/**
		 * Gather the open neighborhood of v.
		 */
		public Set<Integer> neighbors(int v) {
			var nb = new HashSet<Integer>();
			for (var p : w.keySet()) {
				if (p.u == v) nb.add(p.v);
				else if (p.v == v) nb.add(p.u);
			}
			return nb;
		}

		/**
		 * Degree equals the size of the neighbor set.
		 */
		public int degree(int v) {
			return neighbors(v).size();
		}

		/**
		 * Shallow copy (shares no map entries).
		 */
		public WeightedGraph copy() {
			var g = new WeightedGraph(n);
			g.w.putAll(this.w);
			return g;
		}

		/**
		 * Connected connectedComponents under current edges.
		 * We include isolated vertices (with degree 0) as singleton connectedComponents.
		 */
		public ArrayList<Set<Integer>> connectedComponents() {
			var seen = new boolean[n];
			var comps = new ArrayList<Set<Integer>>();
			for (var s = 0; s < n; s++) {
				if (seen[s]) continue;
				var comp = new HashSet<Integer>();
				var dq = new ArrayDeque<Integer>();
				dq.add(s);
				seen[s] = true;
				while (!dq.isEmpty()) {
					var u = dq.poll();
					comp.add(u);
					for (var nb : neighbors(u)) {
						if (!seen[nb]) {
							seen[nb] = true;
							dq.add(nb);
						}
					}
				}
				comps.add(comp);
			}
			//System.err.println("Connected connectedComponents: " + comps.size());
			return comps;
		}

		/**
		 * Write edges as "u, v, weight" in increasing lexicographic order.
		 */
		public void save(String outputFile) throws IOException {
			try (PrintWriter pw = new PrintWriter(outputFile == null ? new OutputStreamWriter(System.out) : new FileWriter(outputFile))) {
				pw.println("# u, v, weight");
				for (var e : listEdges()) pw.printf("%d, %d, %s%n", e.u(), e.v(), trimZeros(getWeight(e)));
			}
		}

		public ArrayList<Integer> nodeList() {
			var set = new TreeSet<Integer>();
			for (var e : listEdges()) {
				set.add(e.u());
				set.add(e.v());
			}
			return new ArrayList<>(set);
		}
	}

	/**
	 * Undirected edge key: stores (min(u,v), max(u,v)) to ensure symmetry.
	 * Comparable for stable ordering (u primary, v secondary).
	 * <p>
	 * NOTE: The current compareTo compares this.u to that.v in the '>' case.
	 * That looks like a typo (probably should be 'that.u'). Edge listing
	 * remains mostly sorted because of the first branch, but for perfect
	 * lexicographic ordering you may want:
	 * <p>
	 * if (this.u < that.u) return -1;
	 * else if (this.u > that.u) return 1;
	 * else return Integer.compare(this.v, that.v);
	 */
	public record UEdge(int u, int v) implements Comparable<UEdge> {
		public UEdge(int u, int v) {
			if (u == v) throw new IllegalArgumentException("self-loop not allowed");
			this.u = Math.min(u, v);
			this.v = Math.max(u, v);
		}

		@Override
		public int compareTo(UEdge that) {
			if (this.u < that.u) return -1;
			if (this.u > that.u) return 1;
			return Integer.compare(this.v, that.v);
		}
	}


	/* ===================== Small utilities ===================== */

	/**
	 * {a, a+1, ..., b-1}
	 */
	static Set<Integer> rangeSet(int a, int b) {
		Set<Integer> s = new HashSet<>();
		for (int i = a; i < b; i++) s.add(i);
		return s;
	}

	/**
	 * A \ B (set difference).
	 */
	static Set<Integer> diff(Set<Integer> A, Set<Integer> B) {
		Set<Integer> r = new HashSet<>(A);
		r.removeAll(B);
		return r;
	}

	/**
	 * Build a set from varargs.
	 */
	static Set<Integer> setOf(int... xs) {
		Set<Integer> s = new HashSet<>();
		for (int x : xs) s.add(x);
		return s;
	}

	/**
	 * Unweighted complete graph on n vertices (edge weight defaults to 1.0).
	 */
	static WeightedGraph completeGraph(int n) {
		WeightedGraph g = new WeightedGraph(n);
		for (int i = 0; i < n; i++) for (int j = i + 1; j < n; j++) g.addEdge(i, j);
		return g;
	}

	/**
	 * Deep copy of a square matrix.
	 */
	static double[][] copyOf(double[][] D) {
		int n = D.length;
		double[][] R = new double[n][n];
		for (int i = 0; i < n; i++) System.arraycopy(D[i], 0, R[i], 0, n);
		return R;
	}

	/**
	 * Minimal printer that trims trailing zeros (for nicer text output).
	 */
	private static String trimZeros(double x) {
		String s = String.format(Locale.US, "%.10f", x);
		int i = s.length() - 1;
		while (i >= 0 && s.charAt(i) == '0') i--;
		if (i >= 0 && s.charAt(i) == '.') i--;
		return s.substring(0, i + 1);
	}

	/**
	 * Ensure the scaffold graph has capacity for at least requiredN vertices.
	 * If 'D' grew (due to aux vertices), we create a larger graph and copy edges.
	 * Otherwise we return the same instance unchanged.
	 */
	static CactusRealizer.WeightedGraph ensureNodes(CactusRealizer.WeightedGraph g, int requiredN) {
		if (g.size() >= requiredN) return g;
		CactusRealizer.WeightedGraph ng = new CactusRealizer.WeightedGraph(requiredN);
		for (var e : g.listEdges()) {
			ng.putEdge(e, g.getWeight(e));
		}
		return ng;
	}

	/**
	 * Read an n x n distance matrix from CSV with the following format:
	 * line 1: n
	 * next n lines: row i contains n comma-separated numbers (row i of D)
	 * Lines starting with '#' are ignored.
	 */
	public static double[][] readCSV(String filename) throws IOException {
		var lines = Files.readAllLines(Paths.get(filename)).stream().map(String::trim).filter(line -> !line.startsWith("#")).toList();
		if (lines.isEmpty())
			throw new IOException("Input file is empty");
		var n = Integer.parseInt(lines.get(0));
		var d = new double[n][];
		if (lines.size() != n + 1)
			throw new IOException("Input file has wrong number of lines");
		for (var i = 0; i < n; i++) {
			var tokens = lines.get(i + 1).split(",");
			if (tokens.length != n)
				throw new IOException("Input line has wrong number of tokens");
			d[i] = Arrays.stream(tokens).mapToDouble(Double::valueOf).toArray();
		}
		return d;
	}

	/* ===================== Main ===================== */

	/**
	 * Command-line front-end.
	 * Usage: java CactusRealizer.java input.csv [output.txt]
	 * - If output is omitted, the graph is printed to stdout.
	 */
	public static void main(String[] args) {
		if (args.length < 1 || args.length > 2) {
			System.err.println("Usage: java -Deps=1e-6 CactusRealizer.java input.csv [output.txt]");
			System.exit(2);
		}
		var inFile = args[0];
		var outFile = (args.length == 2 ? args[1] : null);

		try {
			System.out.println("🔶🔶🔶 Start processing: " + inFile + " 🔶🔶🔶");
			var distanceMatrix = readCSV(inFile);

			var graph = run(distanceMatrix);
			graph.save(outFile);

		} catch (Exception e) {
			System.err.println("Error: " + e.getMessage());
			e.printStackTrace(System.err);
			System.exit(1);
		}
	}
}
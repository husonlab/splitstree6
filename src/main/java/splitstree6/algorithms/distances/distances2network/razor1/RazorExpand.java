/*
 * RazorExpand.java Copyright (C) 2025 Daniel H. Huson
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

package splitstree6.algorithms.distances.distances2network.razor1;

import jloda.util.CanceledException;
import jloda.util.progress.ProgressListener;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

import static splitstree6.algorithms.distances.distances2network.razor1.RazorMath.buildAuxColumn;
import static splitstree6.algorithms.distances.distances2network.razor1.RazorMath.slackWithArgmin;

/**
 * RazorNet: distance-matrix → expanded matrix via compactification (“Razor”) and recursive confirmation.
 * <p>
 * Summary of the algorithm (your outline):
 * Recursively call expand(D, subset). At top level, subset = all indices {0..n-1}.
 * <p>
 * Step 1 (Compactification & Matrix Expansion):
 * For each x in subset:
 * c(x) = min_{y<z in subset\{x}} (D[x,y] + D[x,z] - D[y,z]) / 2, truncated to >= 0.
 * If all c(x) == 0: terminate on this subset (no expansion).
 * Else for each x with c(x) > 0:
 * add auxiliary x' by the “max3” rule (unless x' coincides with an existing vertex, then skip).
 * <p>
 * Step 2 (Temporary graph G):
 * Build an unweighted graph on (non-slack vertices in subset) ∪ (newly-added vertices),
 * but do *not* include the pendant edges
 * .
 * <p>
 * Step 3 (Remove redundant edges):
 * For each edge (u,v) in G:
 * if ∃z in current X with D[u,z] + D[z,v] <= D[u,v] + EPS    → remove (u,v).
 * <p>
 * Step 4 (Confirm & Recurse):
 * Confirm edges incident to any vertex of degree ≤ 2.
 * H := subgraph induced by the unconfirmed edges.
 * If H empty → terminate on this subset.
 * Else recurse for each connected component C of H on its vertex set.
 * <p>
 * Notes:
 * - We mutate a shared distance matrix (wrapped in MutableD) and recurse on subsets of indices.
 * - If you strictly want to “extract submatrices”, this subset version is equivalent but faster.
 * - Coincidence test: skip adding xp if its “row” equals an existing vertex row within EPS and
 * the distance to that vertex is ~0.
 */
public final class RazorExpand {
	public static double EPS = 1e-12;

	/**
	 * Public driver: expands the given matrix until all unconfirmed edges are resolved.
	 */
	public static double[][] expand(double[][] D0, ProgressListener progress) throws CanceledException {
		var M = new MutableD(D0);                       // working, growable matrix

		var all = RazorMath.fullIndexSet(M.size());
		expandSubset(M, all, progress);
		// in-place expansion
		return M.toArray();                // return a fresh array snapshot
	}

	/* ----------------------------- CORE RECURSION ----------------------------- */

	/**
	 * Recursive expansion on a given subset of indices.
	 */
	private static void expandSubset(MutableD M, Set<Integer> subset, ProgressListener progress) throws CanceledException {
		// ----- Step 1: compute slacks and expand with aux vertices (max3) -----

		progress.setMaximum(5);
		progress.setProgress(0);
		var slack = new HashMap<Integer, RazorMath.Slack>(); // x -> (s, y, z)
		boolean anySlack = false;
		for (var x : subset) {
			var sx = slackWithArgmin(M, subset, x);
			if (sx.s() > EPS) {
				anySlack = true;
			}
			slack.put(x, sx);
		}

		if (!anySlack) {
			return; // No vertex is slack in this subset → nothing to expand here.
		}

		progress.setProgress(1);

		// For each x with positive slack, add xp unless it coincides with an existing vertex.
		var newVertices = new ArrayList<Integer>();    // indices of all aux added this round

		for (int x : subset) {
			var sx = slack.get(x);
			if (sx.s() <= EPS) continue;                 // x not slack → skip

			// Build candidate distances for xp using the “max3” rule.
			var cand = buildAuxColumn(M, x, sx);

			// Coincidence check: if xp would duplicate an existing vertex row, skip.
			var coincide = RazorMath.coincidesWithExisting(M, cand);
			if (coincide != null) continue;

			// Append xp to the matrix.
			var xp = M.appendVertex(cand);
			newVertices.add(xp);
		}

		progress.setProgress(2);


		// ----- Step 2: build temporary unweighted graph G on “non-slack ∪ new” + pendants -----

		var verticesG = new HashSet<Integer>();
		for (int x : subset) {
			if (slack.get(x).s() <= EPS) {
				verticesG.add(x); // non-slack in this subset
			}
		}
		verticesG.addAll(newVertices);

		var G = new UG(verticesG);  // simple undirected graph
		var list = new ArrayList<>(verticesG);
		for (int i = 0; i < list.size(); i++) {
			for (int j = i + 1; j < list.size(); j++) {
				G.addEdge(list.get(i), list.get(j));
			}
		}

		progress.setProgress(3);

		//System.err.println("Graph before pruning: "+G.vertices().size()+ " "+ G.edges().size());
		// ----- Step 3: prune redundant edges wrt distances in M -----

		var toRemove = new ArrayList<UG.Edge>();
		for (var e : G.edges()) {
			if (RazorMath.isRedundantEdge(M, e.u(), e.v())) {
				toRemove.add(e);
			}
		}
		for (var e : toRemove) {
			G.removeEdge(e.u(), e.v());
		}
		//System.err.println("Graph after pruning: "+G.vertices().size()+ " "+ G.edges().size());

		// ----- Step 4: confirm edges (degree ≤ 2) and recurse on unconfirmed components -----
		progress.setProgress(4);

		var unconfirmed = new HashSet<UG.Edge>();
		for (var e : G.edges()) {
			boolean confirmed = (G.degree(e.u()) <= 2) || (G.degree(e.v()) <= 2);
			if (!confirmed) unconfirmed.add(e);
		}

		if (unconfirmed.isEmpty()) {
			// All edges in this local region are confirmed → stop here.
			return;
		}

		progress.setProgress(5);

		var H = G.inducedByEdges(unconfirmed);// subgraph on unconfirmed edges
		for (var comp : H.components()) { // Recurse on this connected set of vertices (indices into current M)
			expandSubset(M, comp, progress);
		}
	}
}
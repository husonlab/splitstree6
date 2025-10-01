/*
 * WeightedLayout.java Copyright (C) 2025 Daniel H. Huson
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

package splitstree6.layout.network;

import javafx.geometry.Point2D;

import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.ToDoubleFunction;

/**
 * Length-aware 2D layout using shortest-path distances + stress minimization.
 * Generic over node type N and edge type E.
 * <p>
 * Assumptions:
 * - Graph is undirected (or interpreted as such).
 * - You can iterate nodes via graph.nodes().
 * - For a node u, you can iterate incident edges via graph.getAdjacentEdges(u).
 * - Given (u, e), you can obtain the opposite endpoint v via otherEndpoint.apply(u, e).
 * - Edge lengths come from edgeLength.applyAsDouble(e) and must be > 0.
 * <p>
 * The algorithm:
 * 1) Compute all-pairs target distances dij as sum of edge lengths along shortest paths.
 * 2) Minimize stress:  sum_{i<j} w_ij (||xi - xj|| - dij)^2  with w_ij = 1 / (dij^2 + eps)
 * using batch gradient descent with backoff when stress increases.
 */
public class WeightedLayout<N, E> {
	public static class Params {
		public int maxIterations = 2000;
		public double initialStep = 0.05;
		public double minStep = 1e-6;
		public double stressTolerance = 1e-6;   // relative improvement threshold
		public long randomSeed = 42L;
		public boolean centerAndScale = true;   // normalize final layout to unit box
	}

	public void layout(
			List<N> nodes,
			Function<N, Iterable<E>> getAdjacent,
			BiFunction<N, E, N> getOtherEnd,
			ToDoubleFunction<E> getLength,
			BiConsumer<N, Point2D> setPoint,
			Params params
	) {
		int n = nodes.size();
		if (n == 0) return;
		if (n == 1) {
			setPoint.accept(nodes.get(0), new Point2D(0, 0));
		}

		// 1) Index nodes
		Map<N, Integer> indexOf = new HashMap<>();
		for (int i = 0; i < n; i++) indexOf.put(nodes.get(i), i);

		// 2) Build adjacency for Dijkstra
		List<List<Adj<N, E>>> adj = new ArrayList<>(n);
		for (int i = 0; i < n; i++) adj.add(new ArrayList<>());
		for (int i = 0; i < n; i++) {
			N u = nodes.get(i);
			for (E e : getAdjacent.apply(u)) {
				N v = getOtherEnd.apply(u, e);
				Integer j = indexOf.get(v);
				if (j == null) continue; // ignore edges to nodes not in nodes()
				double w = getLength.applyAsDouble(e);
				if (!(w > 0.0) || Double.isInfinite(w)) continue;
				adj.get(i).add(new Adj<>(i, j, w));
				adj.get(j).add(new Adj<>(j, i, w)); // undirected
			}
		}

		// 3) All-pairs shortest-path distances (Dijkstra from each node)
		double[][] D = new double[n][n];
		for (int s = 0; s < n; s++) {
			Arrays.fill(D[s], Double.POSITIVE_INFINITY);
			D[s][s] = 0.0;
			dijkstra(s, adj, D[s]);
		}

		// Handle disconnected pairs by skipping them in the stress (weight = 0)
		final double EPS = 1e-9;
		double[][] W = new double[n][n];
		int connectedPairs = 0;
		for (int i = 0; i < n; i++) {
			for (int j = i + 1; j < n; j++) {
				double dij = D[i][j];
				if (Double.isFinite(dij) && dij > 0) {
					W[i][j] = W[j][i] = 1.0 / (dij * dij + EPS);
					connectedPairs++;
				} else {
					W[i][j] = W[j][i] = 0.0;
				}
			}
		}
		if (connectedPairs == 0) {
			// Nothing is connected by finite-length paths -> place on a circle
			circleFallback(nodes, setPoint);
		}

		// 4) Initialize positions (circle)
		double[][] X = initCircle(n, params.randomSeed);

		// 5) Optimize stress with gradient descent + step backoff
		double step = params.initialStep;
		double prevStress = stress(X, D, W);
		for (int iter = 0; iter < params.maxIterations; iter++) {
			double[][] G = gradient(X, D, W, EPS);
			// Take a trial step
			double[][] Xtrial = addScaled(X, G, -step);

			double trialStress = stress(Xtrial, D, W);
			if (trialStress <= prevStress) {
				// Accept
				X = Xtrial;
				double relImprovement = (prevStress - trialStress) / Math.max(prevStress, 1e-12);
				prevStress = trialStress;

				// Small acceleration
				step *= 1.05;
				if (relImprovement < params.stressTolerance) {
					break; // converged
				}
			} else {
				// Backoff
				step *= 0.5;
				if (step < params.minStep) {
					break; // stuck
				}
			}
		}

		if (params.centerAndScale) normalize(X);

		// 6) Return map
		for (int i = 0; i < n; i++) {
			setPoint.accept(nodes.get(i), new Point2D(X[i][0], X[i][1]));
		}
	}

	// ----------------- Helpers -----------------

	private static final class Adj<N, E> {
		final int u, v;
		final double w;

		Adj(int u, int v, double w) {
			this.u = u;
			this.v = v;
			this.w = w;
		}
	}

	private void dijkstra(int s, List<List<Adj<N, E>>> adj, double[] dist) {
		int n = adj.size();
		boolean[] vis = new boolean[n];
		PriorityQueue<int[]> pq = new PriorityQueue<>(Comparator.comparingDouble(a -> dist[a[0]]));
		pq.add(new int[]{s});
		while (!pq.isEmpty()) {
			int u = pq.poll()[0];
			if (vis[u]) continue;
			vis[u] = true;
			for (Adj<?, ?> a : adj.get(u)) {
				int v = a.v;
				double nd = dist[u] + a.w;
				if (nd < dist[v]) {
					dist[v] = nd;
					pq.add(new int[]{v});
				}
			}
		}
	}

	private static double[][] initCircle(int n, long seed) {
		double[][] X = new double[n][2];
		Random rnd = new Random(seed);
		double R = 0.5;
		for (int i = 0; i < n; i++) {
			double t = 2 * Math.PI * i / Math.max(1, n);
			// small jitter to break symmetry
			double jitter = 0.01 * (rnd.nextDouble() - 0.5);
			X[i][0] = R * Math.cos(t) + jitter;
			X[i][1] = R * Math.sin(t) + jitter;
		}
		return X;
	}

	private static double[][] addScaled(double[][] X, double[][] G, double scale) {
		int n = X.length;
		double[][] Y = new double[n][2];
		for (int i = 0; i < n; i++) {
			Y[i][0] = X[i][0] + scale * G[i][0];
			Y[i][1] = X[i][1] + scale * G[i][1];
		}
		return Y;
	}

	private static double stress(double[][] X, double[][] D, double[][] W) {
		int n = X.length;
		double s = 0.0;
		for (int i = 0; i < n; i++) {
			for (int j = i + 1; j < n; j++) {
				if (W[i][j] == 0.0) continue;
				double dx = X[i][0] - X[j][0];
				double dy = X[i][1] - X[j][1];
				double dijHat = Math.hypot(dx, dy);
				double diff = dijHat - D[i][j];
				s += W[i][j] * diff * diff;
			}
		}
		return s;
	}

	private static double[][] gradient(double[][] X, double[][] D, double[][] W, double eps) {
		int n = X.length;
		double[][] G = new double[n][2];
		for (int i = 0; i < n; i++) {
			double gx = 0.0, gy = 0.0;
			double xi = X[i][0], yi = X[i][1];
			for (int j = 0; j < n; j++) {
				if (i == j || W[i][j] == 0.0) continue;
				double dx = xi - X[j][0];
				double dy = yi - X[j][1];
				double rij = Math.hypot(dx, dy);
				// derivative of (||xi-xj|| - dij)^2 is 2*(1 - dij/||...||)*(xi-xj)
				double scale = 2.0 * W[i][j] * (1.0 - (D[i][j] / Math.max(rij, eps)));
				gx += scale * dx;
				gy += scale * dy;
			}
			G[i][0] = gx;
			G[i][1] = gy;
		}
		return G;
	}

	private static void normalize(double[][] X) {
		int n = X.length;
		// center
		double cx = 0, cy = 0;
		for (double[] p : X) {
			cx += p[0];
			cy += p[1];
		}
		cx /= n;
		cy /= n;
		for (double[] p : X) {
			p[0] -= cx;
			p[1] -= cy;
		}

		// scale to unit box
		double minx = Double.POSITIVE_INFINITY, maxx = Double.NEGATIVE_INFINITY;
		double miny = Double.POSITIVE_INFINITY, maxy = Double.NEGATIVE_INFINITY;
		for (double[] p : X) {
			minx = Math.min(minx, p[0]);
			maxx = Math.max(maxx, p[0]);
			miny = Math.min(miny, p[1]);
			maxy = Math.max(maxy, p[1]);
		}
		double sx = maxx - minx, sy = maxy - miny;
		double s = Math.max(Math.max(sx, sy), 1e-12);
		for (double[] p : X) {
			p[0] /= s;
			p[1] /= s;
		}
	}

	private void circleFallback(List<N> nodes, BiConsumer<N, Point2D> setPoint) {
		var n = nodes.size();
		for (var i = 0; i < n; i++) {
			var t = 2 * Math.PI * i / n;
			setPoint.accept(nodes.get(i), new Point2D(Math.cos(t), Math.sin(t)));
		}
	}
}
/*
 * SpanningTree.java Copyright (C) 2025 Daniel H. Huson
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

package splitstree6.view.network;

import java.util.*;
import java.util.function.Function;
import java.util.function.ToDoubleFunction;

public final class SpanningTree {

	/**
	 * Kruskal: returns a set of |V|-1 edges forming a spanning tree (or a forest if graph not connected).
	 */
	public static <N, E> List<E> kruskal(
			Collection<N> nodes,
			Collection<E> edges,
			Function<E, N> uOf,                 // how to get edge's first endpoint
			Function<E, N> vOf,                 // how to get edge's second endpoint
			ToDoubleFunction<E> weight,         // e -> weight
			boolean maximum                     // false = min tree, true = max tree
	) {
		// Map nodes to compact ids for Union-Find:
		Map<N, Integer> id = new HashMap<>(nodes.size() * 2);
		int idx = 0;
		for (N n : nodes) id.put(n, idx++);

		UnionFind uf = new UnionFind(nodes.size());

		// Sort edges by weight (asc for MST, desc for max-ST):
		List<E> sorted = new ArrayList<>(edges);
		Comparator<E> cmp = Comparator.comparingDouble(weight);
		if (maximum) cmp = cmp.reversed();
		sorted.sort(cmp);

		List<E> tree = new ArrayList<>(Math.max(0, nodes.size() - 1));
		for (E e : sorted) {
			N u = uOf.apply(e), v = vOf.apply(e);
			int iu = id.get(u), iv = id.get(v);
			if (uf.union(iu, iv)) {   // merged two connectedComponents => no cycle formed
				tree.add(e);
				if (tree.size() == nodes.size() - 1) break; // done
			}
		}
		return tree; // if graph is connected, size == |V|-1
	}

	// --------- Union-Find (Disjoint Set Union) ---------
	private static final class UnionFind {
		private final int[] parent;
		private final byte[] rank;

		UnionFind(int n) {
			parent = new int[n];
			rank = new byte[n];
			for (int i = 0; i < n; i++) parent[i] = i;
		}

		int find(int x) {
			int p = parent[x];
			if (p != x) parent[x] = find(p);
			return parent[x];
		}

		boolean union(int a, int b) {
			int ra = find(a), rb = find(b);
			if (ra == rb) return false;
			if (rank[ra] < rank[rb]) {
				parent[ra] = rb;
			} else if (rank[ra] > rank[rb]) {
				parent[rb] = ra;
			} else {
				parent[rb] = ra;
				rank[ra]++;
			}
			return true;
		}
	}
}
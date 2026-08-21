/*
 *  NetworkSimplification.java Copyright (C) 2026 Daniel H. Huson
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

package splitstree6.algorithms.network.network2network;

import jloda.graph.Edge;
import jloda.graph.Node;
import jloda.phylo.PhyloGraph;
import jloda.util.StringUtils;
import jloda.util.progress.ProgressListener;
import splitstree6.data.NetworkBlock;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

/**
 * What every lossy network filter needs: shortest paths over a subset of the edges, and the tidy-up that turns
 * a network with edges removed back into a drawable one.
 * <p>
 * Shared by {@link StretchFilter} and {@link SimplifyFilter} so that a correction made for one -- such as the
 * merged-edge mutation labels of 2026-08-19 -- does not have to be made twice.
 * <p>
 * Daniel Huson, 2026
 */
class NetworkSimplification {
	private record NodeDist(Node node, double dist) {
	}

	/**
	 * Dijkstra single-source shortest paths over the {@code alive} edge set only (undirected, edge weight =
	 * {@code graph.getWeight}). Returns node -&gt; distance; absent = unreachable.
	 */
	static Map<Node, Double> singleSource(PhyloGraph graph, Node source, Set<Edge> alive) {
		var dist = new HashMap<Node, Double>();
		dist.put(source, 0.0);
		var queue = new PriorityQueue<NodeDist>(Comparator.comparingDouble(NodeDist::dist));
		queue.add(new NodeDist(source, 0.0));
		while (!queue.isEmpty()) {
			var cur = queue.poll();
			if (cur.dist() > dist.getOrDefault(cur.node(), Double.MAX_VALUE))
				continue; // stale
			for (var e : cur.node().adjacentEdges()) {
				if (!alive.contains(e))
					continue;
				var nbr = e.getOpposite(cur.node());
				var nd = cur.dist() + graph.getWeight(e);
				if (nd < dist.getOrDefault(nbr, Double.MAX_VALUE)) {
					dist.put(nbr, nd);
					queue.add(new NodeDist(nbr, nd));
				}
			}
		}
		return dist;
	}

	/**
	 * Remove superfluous unlabeled (Steiner) nodes: drop degree-&le;1 danglers and dissolve degree-2 nodes into a
	 * single edge (summed weight and, on a haplotype network, concatenated mutation list). Taxon nodes are never
	 * touched. Iterates to convergence.
	 *
	 * @param block    the node/edge data to keep in step, or null when the graph is a throwaway copy being
	 *                 measured rather than the network that will be drawn
	 * @param progress checked for cancellation, or null
	 */
	static void cleanAndSmooth(PhyloGraph graph, NetworkBlock block, ProgressListener progress) throws Exception {
		var changed = true;
		while (changed) {
			changed = false;
			for (var v : graph.nodeStream().toList()) {
				if (graph.hasTaxa(v))
					continue;
				var degree = v.getDegree();
				if (degree <= 1) {
					graph.deleteNode(v);
					changed = true;
				} else if (degree == 2) {
					var edges = new ArrayList<Edge>();
					v.adjacentEdges().forEach(edges::add);
					var v1 = edges.get(0).getOpposite(v);
					var v2 = edges.get(1).getOpposite(v);
					var weight = graph.getWeight(edges.get(0)) + graph.getWeight(edges.get(1));
					// read the two mutation lists before deleting the node takes their edges with them
					var sites = (block == null ? null : mergeSites(block.getEdgeData(edges.get(0)).get(NetworkBlock.EDGE_SITES_KEY),
							block.getEdgeData(edges.get(1)).get(NetworkBlock.EDGE_SITES_KEY)));
					graph.deleteNode(v); // also removes its two edges
					if (v1 != v2) {
						var f = graph.newEdge(v1, v2);
						graph.setWeight(f, weight);
						if (block != null) {
							block.getEdgeData(f).put("weight", StringUtils.trim((float) weight));
							if (sites != null)
								block.getEdgeData(f).put(NetworkBlock.EDGE_SITES_KEY, sites);
						}
					}
					changed = true;
				}
				if (progress != null)
					progress.checkForCancel();
			}
		}
	}

	/**
	 * The mutation list for an edge that replaces a two-edge path: the two lists, concatenated in path order.
	 * Returns null if either edge has no list, because then there is nothing faithful to write -- a network that
	 * is not a haplotype network carries no mutation lists at all, and half a list would understate the merged
	 * edge. (Left unset, the merged edge used to claim no mutations at all while carrying the summed weight.)
	 * <p>
	 * Deliberately <em>not</em> de-duplicated. The merged edge's weight has to be the sum of the two weights,
	 * since that is what preserves the shortest-path distances these filters bound, so a site that mutates on
	 * both edges -- a reversal, or two steps such as a &rarr; c &rarr; g -- must appear twice, or the mutations
	 * drawn would no longer add up to the length drawn. Deliberately not sorted either: a site is not necessarily
	 * a number, {@code QuasiMedianBase} writes the alignment's character labels.
	 */
	static String mergeSites(String sites1, String sites2) {
		if (sites1 == null || sites2 == null)
			return null;
		if (sites1.isBlank())
			return sites2;
		if (sites2.isBlank())
			return sites1;
		return sites1 + "," + sites2;
	}
}

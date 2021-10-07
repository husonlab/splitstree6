/*
 *  NeighborNetCycle.java Copyright (C) 2021 Daniel H. Huson
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

package splitstree6.algorithms.distances.distances2splits.neighbornet;

import jloda.util.CanceledException;
import jloda.util.ProgressListener;
import jloda.util.ProgressSilent;

import java.util.Arrays;
import java.util.Stack;

/**
 * compute the neighbor-net cycle
 * David Bryant and Daniel Huson, 2005
 */
public class NeighborNetCycle {
	/**
	 * Run the neighbor net algorithm to compute the circular ordering of the taxa
	 */
	public static int[] compute(ProgressListener progress, int nTax, double[][] dist) throws CanceledException {
		//Special cases. When nTax<=3, the default circular ordering will work.
		if (nTax <= 3) {
			int[] cycle = new int[nTax + 1];
			for (int i = 1; i <= nTax; i++)
				cycle[i] = i;
			return cycle;
		}

		final double[][] mat = setupMatrix(nTax, dist);

		final NetNode nodesHeader = new NetNode(0);

		/* Nodes are stored in a doubly linked list that we set up here */
		for (int i = nTax; i >= 1; i--) /* Initially, all singleton nodes are active */ {
			final NetNode node = new NetNode(i);
			node.next = nodesHeader.next;
			nodesHeader.next = node;
		}

		/* Set up links in other direction */
		for (NetNode taxNode = nodesHeader; taxNode.next != null; taxNode = taxNode.next)
			taxNode.next.prev = taxNode;

		/* Perform the agglomeration step */
		progress.setTasks("NNet", "agglomeration");
		final Stack<NetNode> joins = joinNodes(progress, mat, nodesHeader, nTax);
		progress.setTasks("NNet", "expansion");
		// System.err.println("Ordering: "+ Basic.toString(ordering));

		return expandNodes(progress, nTax, joins, nodesHeader);
	}

	/**
	 * compute the cycle
	 */
	static public int[] computeNeighborNetCycle(int nTax, double[][] dist) {
		try {
			return compute(new ProgressSilent(), nTax, dist);
		} catch (CanceledException e) {
			return null;
		}
	}

	/**
	 * Sets up the working matrix. The original distance matrix is enlarged to
	 * handle the maximum number of nodes
	 *
	 * @param dist Distance block 0-based
	 * @return a working matrix of appropriate cardinality 1-based
	 */
	private static double[][] setupMatrix(int nTax, double[][] dist) {
		int max_num_nodes = 3 * nTax - 5;
		double[][] mat = new double[max_num_nodes][max_num_nodes];
		/* Copy the distance matrix into a larger, scratch distance matrix */
		for (int i = 1; i <= nTax; i++) {
			System.arraycopy(dist[i - 1], 0, mat[i], 1, nTax);
			Arrays.fill(mat[i], nTax + 1, max_num_nodes, 0.0);
		}
		for (int i = nTax + 1; i < max_num_nodes; i++)
			Arrays.fill(mat[i], 0, max_num_nodes, 0.0);
		return mat;
	}

	/**
	 * Agglomerates the nodes
	 */
	static private Stack<NetNode> joinNodes(ProgressListener progress, double[][] D, NetNode nodesHead, int num_nodes) throws CanceledException {
		final Stack<NetNode> joins = new Stack<>();

		//System.err.println("joinNodes");

		double Qpq;
		double best;
		int num_active = num_nodes;
		int num_clusters = num_nodes;
		int m;
		double Dpq;

		while (num_active > 3) {

            /* Special case
            If we let this one go then we get a divide by zero when computing Qpq */
			if (num_active == 4 && num_clusters == 2) {
				NetNode p = nodesHead.next;
				NetNode q;
				if (p.next != p.nbr)
					q = p.next;
				else
					q = p.next.next;
				if (D[p.id][q.id] + D[p.nbr.id][q.nbr.id] < D[p.id][q.nbr.id] + D[p.nbr.id][q.id]) {
					join3way(p, q, q.nbr, joins, D, nodesHead, num_nodes);
				} else {
					join3way(p, q.nbr, q, joins, D, nodesHead, num_nodes);
				}
				break;
			}

            /* Compute the "averaged" sums s_i from each cluster to every other cluster.

            todo: 2x speedup by using symmetry */

			for (NetNode p = nodesHead.next; p != null; p = p.next)
				p.Sx = 0.0;
			for (NetNode p = nodesHead.next; p != null; p = p.next) {
				if (p.nbr == null || p.nbr.id > p.id) {
					for (NetNode q = p.next; q != null; q = q.next) {
						if (q.nbr == null || (q.nbr.id > q.id) && (q.nbr != p)) {
							if ((p.nbr == null) && (q.nbr == null))
								Dpq = D[p.id][q.id];
							else if ((p.nbr != null) && (q.nbr == null))
								Dpq = (D[p.id][q.id] + D[p.nbr.id][q.id]) / 2.0;
							else if ((p.nbr == null)) //  && (q.nbr != null))
								Dpq = (D[p.id][q.id] + D[p.id][q.nbr.id]) / 2.0;
							else
								Dpq = (D[p.id][q.id] + D[p.id][q.nbr.id] + D[p.nbr.id][q.id] + D[p.nbr.id][q.nbr.id]) / 4.0;

							p.Sx += Dpq;
							if (p.nbr != null)
								p.nbr.Sx += Dpq;
							q.Sx += Dpq;
							if (q.nbr != null)
								q.nbr.Sx += Dpq;
						}
					}
					progress.checkForCancel();
				}
			}

			NetNode Cx = null;
			NetNode Cy = null;

			/* Now minimize (m-2) D[C_i,C_k] - Sx - Sy */
			best = 0;
			for (NetNode p = nodesHead.next; p != null; p = p.next) {
				if ((p.nbr != null) && (p.nbr.id < p.id)) /* We only evaluate one node per cluster */
					continue;
				for (NetNode q = nodesHead.next; q != p; q = q.next) {
					if ((q.nbr != null) && (q.nbr.id < q.id)) /* We only evaluate one node per cluster */
						continue;
					if (q.nbr == p) /* We only evaluate nodes in different clusters */
						continue;
					if ((p.nbr == null) && (q.nbr == null))
						Dpq = D[p.id][q.id];
					else if ((p.nbr != null) && (q.nbr == null))
						Dpq = (D[p.id][q.id] + D[p.nbr.id][q.id]) / 2.0;
					else if ((p.nbr == null)) // && (q.nbr != null))
						Dpq = (D[p.id][q.id] + D[p.id][q.nbr.id]) / 2.0;
					else
						Dpq = (D[p.id][q.id] + D[p.id][q.nbr.id] + D[p.nbr.id][q.id] + D[p.nbr.id][q.nbr.id]) / 4.0;
					Qpq = ((double) num_clusters - 2.0) * Dpq - p.Sx - q.Sx;

					/* Check if this is the best so far */
					if ((Cx == null || (Qpq < best)) && (p.nbr != q)) {
						Cx = p;
						Cy = q;
						best = Qpq;
					}
				}
			}
			if (Cx == null || Cy == null)
				throw new RuntimeException("Internal error");

			/* Find the node in each cluster */
			NetNode x = Cx;
			NetNode y = Cy;

			if (Cx.nbr != null || Cy.nbr != null) {
				Cx.Rx = ComputeRx(Cx, Cx, Cy, D, nodesHead);
				if (Cx.nbr != null)
					Cx.nbr.Rx = ComputeRx(Cx.nbr, Cx, Cy, D, nodesHead);
				Cy.Rx = ComputeRx(Cy, Cx, Cy, D, nodesHead);
				if (Cy.nbr != null)
					Cy.nbr.Rx = ComputeRx(Cy.nbr, Cx, Cy, D, nodesHead);
			}

			m = num_clusters;
			if (Cx.nbr != null)
				m++;
			if (Cy.nbr != null)
				m++;

			best = ((double) m - 2.0) * D[Cx.id][Cy.id] - Cx.Rx - Cy.Rx;
			if (Cx.nbr != null) {
				Qpq = ((double) m - 2.0) * D[Cx.nbr.id][Cy.id] - Cx.nbr.Rx - Cy.Rx;
				if (Qpq < best) {
					x = Cx.nbr;
					y = Cy;
					best = Qpq;
				}
			}
			if (Cy.nbr != null) {
				Qpq = ((double) m - 2.0) * D[Cx.id][Cy.nbr.id] - Cx.Rx - Cy.nbr.Rx;
				if (Qpq < best) {
					x = Cx;
					y = Cy.nbr;
					best = Qpq;
				}
			}
			if ((Cx.nbr != null) && (Cy.nbr != null)) {
				Qpq = ((double) m - 2.0) * D[Cx.nbr.id][Cy.nbr.id] - Cx.nbr.Rx - Cy.nbr.Rx;
				if (Qpq < best) {
					x = Cx.nbr;
					y = Cy.nbr;
				}
			}

			/* We perform an agglomeration... one of three types */
			if ((null == x.nbr) && (null == y.nbr)) {   /* Both vertices are isolated...add edge {x,y} */
				join2way(x, y);
				num_clusters--;
			} else if (null == x.nbr) {     /* X is isolated,  Y  is not isolated*/
				join3way(x, y, y.nbr, joins, D, nodesHead, num_nodes);
				num_nodes += 2;
				num_active--;
				num_clusters--;
			} else if ((null == y.nbr) || (num_active == 4)) { /* Y is isolated,  X is not isolated
                                                        OR theres only four active nodes and none are isolated */
				//noinspection SuspiciousNameCombination
				join3way(y, x, x.nbr, joins, D, nodesHead, num_nodes);
				num_nodes += 2;
				num_active--;
				num_clusters--;
			} else {  /* Both nodes are connected to others and there are more than 4 active nodes */
				num_nodes = join4way(x.nbr, x, y, y.nbr, joins, D, nodesHead, num_nodes);
				num_active -= 2;
				num_clusters--;
			}
		}
		// for(NetNode n:joins) System.err.println(n);

		return joins;
	}

	/**
	 * agglomerate 2 nodes
	 *
	 * @param x one node
	 * @param y other node
	 */
	static private void join2way(NetNode x, NetNode y) {
		x.nbr = y;
		y.nbr = x;
	}

	/**
	 * agglomerate 3 nodes.
	 * Note that this version doesn't update num_nodes, you need to
	 * num_nodes+=2 after calling this!
	 *
	 * @param x one node
	 * @param y other node
	 * @param z other node
	 * @return one of the new nodes
	 */
	static private NetNode join3way(NetNode x, NetNode y, NetNode z, Stack<NetNode> joins, double[][] mat, NetNode nodesHead, int num_nodes) {
		/* Agglomerate x,y, and z to give TWO new nodes, u and v */
/* In terms of the linked list: we replace x and z
       by u and v and remove y from the linked list.
  	 and replace y with the new node z
    Returns a pointer to the node u */
//printf("Three way: %d, %d, and %d\n",x.id,y.id,z.id);

		NetNode u = new NetNode(num_nodes + 1);
		u.ch1 = x;
		u.ch2 = y;

		NetNode v = new NetNode(num_nodes + 2);
		v.ch1 = y;
		v.ch2 = z;

		/* Replace x by u in the linked list */
		u.next = x.next;
		u.prev = x.prev;
		if (u.next != null)
			u.next.prev = u;
		if (u.prev != null)
			u.prev.next = u;

		/* Replace z by v in the linked list */
		v.next = z.next;
		v.prev = z.prev;
		if (v.next != null)
			v.next.prev = v;
		if (v.prev != null)
			v.prev.next = v;

		/* Remove y from the linked list */
		if (y.next != null)
			y.next.prev = y.prev;
		if (y.prev != null)
			y.prev.next = y.next;

		/* Add an edge between u and v, and add u into the list of amalgamations */
		u.nbr = v;
		v.nbr = u;

		/* Update distance matrix */

		for (NetNode p = nodesHead.next; p != null; p = p.next) {
			mat[u.id][p.id] = mat[p.id][u.id] = (2.0 / 3.0) * mat[x.id][p.id] + mat[y.id][p.id] / 3.0;
			mat[v.id][p.id] = mat[p.id][v.id] = (2.0 / 3.0) * mat[z.id][p.id] + mat[y.id][p.id] / 3.0;
		}
		mat[u.id][u.id] = mat[v.id][v.id] = 0.0;

		joins.push(u);

		return u;
	}

	/**
	 * Agglomerate four nodes
	 *
	 * @param x2 a node
	 * @param x  a node
	 * @param y  a node
	 * @param y2 a node
	 * @return the new number of nodes
	 */
	static private int join4way(NetNode x2, NetNode x, NetNode y, NetNode y2, Stack<NetNode> joins, double[][] mat, NetNode nodesHead, int num_nodes) {
/* Replace x2,x,y,y2 by with two vertices... performed using two
       3 way amalgamations */

		//noinspection SuspiciousNameCombination
		final NetNode u = join3way(x2, x, y, joins, mat, nodesHead, num_nodes); /* Replace x2,x,y by two nodes, equals to x2_prev.next and y_prev.next. */
		num_nodes += 2;
		join3way(u, u.nbr, y2, joins, mat, nodesHead, num_nodes); /* z = y_prev . next */
		num_nodes += 2;
		return num_nodes;
	}

	/**
	 * Computes the Rx
	 *
	 * @param z         a node
	 * @param Cx        a node
	 * @param Cy        a node
	 * @param mat       the distances
	 * @param nodesHead the net nodes
	 * @return the Rx value
	 */
	static private double ComputeRx(NetNode z, NetNode Cx, NetNode Cy, double[][] mat, NetNode nodesHead) {
		double Rx = 0.0;

		for (NetNode p = nodesHead.next; p != null; p = p.next) {
			if (p == Cx || p == Cx.nbr || p == Cy || p == Cy.nbr || p.nbr == null)
				Rx += mat[z.id][p.id];
			else /* p.nbr != null */
				Rx += mat[z.id][p.id] / 2.0; /* We take the average of the distances */
		}
		return Rx;
	}

	/**
	 * Expands the net nodes to obtain the ordering, quickly
	 *
	 * @param nTax      number of taxa
	 * @param joins     stack of joins
	 * @param nodesHead the net nodes
	 */
	static private int[] expandNodes(ProgressListener progress, int nTax, Stack<NetNode> joins, NetNode nodesHead) throws CanceledException {
		//System.err.println("expandNodes");

		/* Set up the circular order for the first three nodes */
		NetNode x = nodesHead.next;
		NetNode y = x.next;
		NetNode z = y.next;

		z.next = x;
		x.prev = z;

		/* Now do the rest of the expansions */
		while (!joins.empty()) {
            /* Find the three elements replacing u and v. Swap u and v around if v comes before u in the
            circular ordering being built up */
			NetNode u = joins.pop();
			// System.err.println("POP: u="+u);
			NetNode v = u.nbr;
			x = u.ch1;
			y = u.ch2;
			z = v.ch2;
			if (v != u.next) {
				NetNode tmp = u;
				u = v;
				v = tmp;
				tmp = x;
				x = z;
				z = tmp;
			}

			/* Insert x,y,z into the circular order */
			x.prev = u.prev;
			x.prev.next = x;
			x.next = y;
			y.prev = x;
			y.next = z;
			z.prev = y;
			z.next = v.next;
			z.next.prev = z;
			progress.checkForCancel();
		}

		/* When we exit, we know that the point x points to a node in the circular order */
		/* We loop through until we find the node after taxa zero */
		while (x.id != 1) {
			x = x.next;
		}

		/* extract the ordering */
		final int[] cycle = new int[nTax + 1];
		{
			NetNode a = x;
			int t = 0;
			do {
				cycle[++t] = a.id;
				a = a.next;
			} while (a != x);
		}
		return cycle;
	}
}

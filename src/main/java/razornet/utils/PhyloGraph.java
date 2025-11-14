/*
 * PhyloGraph.java Copyright (C) 2025 Daniel H. Huson
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

package razornet.utils;

import java.util.Set;

public interface PhyloGraph<N, E> {
	int getNumberOfNodes();

	int getNumberOfEdges();

	Iterable<N> nodes();

	int getDegree(N node);

	Iterable<E> edges();

	Set<E> edgesAsSet();

	double getWeight(E edge);

	void setWeight(E edge, double weight);

	Iterable<N> getAdjacentNodes(N node);

	Iterable<E> getAdjacentEdges(N node);

	N getOpposite(E e, N v);

	void setTaxon(N node, int taxon);

	int getTaxon(N node);

	void setLabel(N node, String label);

	String getLabel(N node);

	N newNode();

	E newEdge(N s, N t);

	boolean hasEdge(N s, N t);

	E getEdge(N s, N t);

	N getSource(E e);

	N getTarget(E e);

	void deleteEdge(E e);

	void deleteNode(N node);
}

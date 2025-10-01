/*
 * InterfaceUtils.java Copyright (C) 2025 Daniel H. Huson
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

import jloda.graph.Edge;
import jloda.graph.Graph;
import jloda.graph.Node;

public class InterfaceUtils {
	public static ParsimonyLabeler.GraphAccessor<Node, Edge> getGraphAdapter(Graph graph) {
		return new ParsimonyLabeler.GraphAccessor<Node, Edge>() {
			public Iterable<Node> nodes() {
				return graph.nodes();
			}

			public Iterable<Edge> edges() {
				return graph.edges();
			}

			public Node u(Edge e) {
				return e.getSource();
			}

			public Node v(Edge e) {
				return e.getTarget();
			}
		};
	}
}

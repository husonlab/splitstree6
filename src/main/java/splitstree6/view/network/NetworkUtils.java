/*
 * NetworkUtils.java Copyright (C) 2025 Daniel H. Huson
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

import jloda.graph.Edge;
import jloda.graph.GraphTraversals;
import jloda.graph.Node;

import java.util.Collection;
import java.util.HashSet;

public class NetworkUtils {
	public static Collection<Node> reachableFrom(Node v, Edge forbidded) {
		var nodes = new HashSet<Node>();
		GraphTraversals.traverseReachable(v, e -> e != forbidded, nodes::add);
		return nodes;
	}
}

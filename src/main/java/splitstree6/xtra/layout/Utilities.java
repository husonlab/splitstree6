/*
 * Utilities.java Copyright (C) 2025 Daniel H. Huson
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

package splitstree6.xtra.layout;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

public class Utilities {
	public static <Node> void postOrderTraversal(Node v, Function<Node, List<Node>> children, Consumer<Node> consumer) {
		var below = children.apply(v);
		if (below != null) {
			for (var u : below) {
				postOrderTraversal(u, children, consumer);
			}
		}
		consumer.accept(v);
	}

	public static <Node> void preOrderTraversal(Node v, Function<Node, List<Node>> children, Consumer<Node> consumer) {
		consumer.accept(v);
		var below = children.apply(v);
		if (below != null) {
			for (var u : below) {
				preOrderTraversal(u, children, consumer);
			}
		}
	}

	public static <Node> double getMinHeight(Node u, Map<Node, List<Node>> childrenMap, Map<Node, Double> nodeHeightMap) {
		while (true) {
			var children = childrenMap.get(u);
			if (children.isEmpty())
				return nodeHeightMap.get(u);
			else u = children.get(0);
		}
	}

	public static <Node> double getMaxHeight(Node u, Map<Node, List<Node>> childrenMap, Map<Node, Double> nodeHeightMap) {
		while (true) {
			var children = childrenMap.get(u);
			if (children.isEmpty())
				return nodeHeightMap.get(u);
			else u = children.get(children.size() - 1);
		}
	}
}

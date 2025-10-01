/*
 * InitialTreeLayout.java Copyright (C) 2025 Daniel H. Huson
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
import jloda.graph.Node;
import jloda.graph.fmm.FastMultiLayerMethodLayout;
import jloda.phylo.PhyloGraph;

import java.util.Map;

public class InitialTreeLayout {
	public static Map<Node, FastMultiLayerMethodLayout.Point> apply(PhyloGraph graph) {
		var edges = SpanningTree.kruskal(graph.getNodesAsList(), graph.getEdgesAsList(), Edge::getSource, Edge::getTarget, graph::getWeight, false);
		return null;
	}
}

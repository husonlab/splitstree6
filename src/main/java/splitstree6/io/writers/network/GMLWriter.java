/*
 *  GMLWriter.java Copyright (C) 2026 Daniel H. Huson
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

package splitstree6.io.writers.network;

import jloda.graph.Edge;
import jloda.graph.Node;
import jloda.graph.io.GraphGML;
import jloda.util.ProgramProperties;
import jloda.util.StringUtils;
import splitstree6.data.NetworkBlock;
import splitstree6.data.TaxaBlock;

import java.io.IOException;
import java.io.Writer;
import java.util.TreeSet;
import java.util.function.BiFunction;

/**
 * write a network in GML format, for import into Cytoscape, igraph, Gephi and the like
 * <p>
 * Unlike {@link splitstree6.io.writers.view.GMLWriter}, which exports what the network <i>viewer</i>
 * shows and therefore has node coordinates, this writes the {@link NetworkBlock} itself: node labels,
 * edge weights, and whatever key-value pairs the algorithm attached to nodes and edges. So it also
 * works headless, where no view exists.
 * Daniel Huson, 8.2026
 */
public class GMLWriter extends NetworkWriterBase {
	public GMLWriter() {
		setFileExtensions("gml");
	}

	@Override
	public void write(Writer w, TaxaBlock taxaBlock, NetworkBlock networkBlock) throws IOException {
		var graph = networkBlock.getGraph();
		if (graph == null)
			return;

		// the attributes are the union of all keys present, so an algorithm that labels only some nodes
		// (mutations on some edges, say) still gets those keys written
		var nodeLabelNames = new TreeSet<String>();
		for (var v : graph.nodes()) {
			nodeLabelNames.addAll(networkBlock.getNodeData(v).keySet());
		}
		nodeLabelNames.add("label");

		BiFunction<String, Node, String> labelNodeValue = (label, v) ->
				switch (label) {
					case "label" -> graph.hasTaxa(v) ? taxaBlock.getLabel(graph.getTaxon(v)) : graph.getLabel(v);
					default -> networkBlock.getNodeData(v).get(label);
				};

		var edgeLabelNames = new TreeSet<String>();
		for (var e : graph.edges()) {
			edgeLabelNames.addAll(networkBlock.getEdgeData(e).keySet());
		}
		edgeLabelNames.add("weight");

		BiFunction<String, Edge, String> labelEdgeValue = (label, e) ->
				switch (label) {
					case "weight" -> StringUtils.trim("%.8f", graph.getWeight(e));
					default -> networkBlock.getEdgeData(e).get(label);
				};

		var comment = "Exported from %s: %,d nodes, %,d edges".formatted(ProgramProperties.getProgramName(),
				graph.getNumberOfNodes(), graph.getNumberOfEdges());
		var graphLabel = (graph.getName() != null ? graph.getName() : "Network");
		GraphGML.writeGML(graph, comment, graphLabel, false, 1, w,
				nodeLabelNames, labelNodeValue, edgeLabelNames, labelEdgeValue);
	}
}

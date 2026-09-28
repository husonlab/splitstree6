/*
 *  NetworkNexusOutput.java Copyright (C) 2024 Daniel H. Huson
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

package splitstree6.io.nexus;

import jloda.graph.Edge;
import jloda.graph.Node;
import jloda.phylo.PhyloGraph;
import jloda.util.StringUtils;
import splitstree6.data.NetworkBlock;
import splitstree6.data.TaxaBlock;

import java.io.IOException;
import java.io.Writer;

/**
 * network nexus output
 * Daniel Huson, 2.2018
 */
public class NetworkNexusOutput extends NexusIOBase implements INexusOutput<NetworkBlock> {
	/**
	 * write the block in nexus format
	 */
	@Override
	public void write(Writer w, TaxaBlock taxaBlock, NetworkBlock networkBlock) throws IOException {
		w.write("\nBEGIN NETWORK;\n");
		writeTitleAndLink(w);
		w.write("DIMENSIONS nVertices=" + networkBlock.getNumberOfNodes() + " nEdges=" + networkBlock.getNumberOfEdges() + ";\n");

		w.write("TYPE " + networkBlock.getNetworkType() + ";\n");

		final PhyloGraph graph = networkBlock.getGraph();
		// format?

		// properties?
		if (networkBlock.getInfoString() != null && !networkBlock.getInfoString().isBlank()) {
			w.write("PROPERTIES info ='" + StringUtils.fold(networkBlock.getInfoString().trim(), 256) + "';\n");
		}

		{
			w.write("VERTICES\n");
			boolean first = true;
			for (Node v : graph.nodes()) {
				if (first)
					first = false;
				else
					w.write(",\n");
				w.write("\tid=" + v.getId());
				// one label per vertex: the graph label, else a label held in the node data (the SplitsTree4 and GML
				// readers put one there). Writing both made every load and save of a network repeat the label
				var nodeData = networkBlock.getNodeData(v);
				var label = graph.getLabel(v);
				if (label == null || label.isBlank())
					label = nodeData.get(NetworkBlock.NodeData.BasicKey.label.name());
				if (label != null && !label.isBlank()) {
					w.write(" label='" + label.trim() + "'");
				}
				for (String key : nodeData.keySet()) {
					if (!key.equals(NetworkBlock.NodeData.BasicKey.label.name()))
						w.write(" " + key + "='" + nodeData.get(key) + "'");
				}
			}
			w.write("\n;\n");
		}

		{
			w.write("EDGES\n");
			boolean first = true;
			for (Edge e : graph.edges()) {
				if (first)
					first = false;
				else
					w.write(",\n");
				w.write("\tid=" + e.getId());
				w.write(" sid=" + e.getSource().getId());
				w.write(" tid=" + e.getTarget().getId());

				var hasLabel = graph.getLabel(e) != null && !graph.getLabel(e).isBlank();
				if (hasLabel) {
					w.write(" label='" + graph.getLabel(e).trim() + "'");
				}
				if (graph.hasEdgeWeights() && !networkBlock.getEdgeData(e).containsKey("weight")) {
					w.write(" weight='" + graph.getWeight(e) + "'");
				}
				if (graph.hasEdgeConfidences() && !networkBlock.getEdgeData(e).containsKey("confidence")) {
					w.write(" confidence='" + graph.getConfidence(e) + "'");
				}
				if (graph.hasEdgeProbabilities() && !networkBlock.getEdgeData(e).containsKey("probability")) {
					w.write(" probability='" + graph.getProbability(e) + "'");
				}
				for (var key : networkBlock.getEdgeData(e).keySet()) {
					// as for vertices, an edge has at most one label
					if (!(hasLabel && key.equals(NetworkBlock.EdgeData.BasicKey.label.name())))
						w.write(" " + key + "='" + networkBlock.getEdgeData(e).get(key) + "'");
				}
			}
			w.write("\n;\n");
		}
		w.write("END; [NETWORK]\n");
	}
}

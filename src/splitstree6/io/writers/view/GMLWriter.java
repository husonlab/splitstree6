/*
 * PlainTextWriter.java Copyright (C) 2023 Daniel H. Huson
 *
 * (Some files contain contributions from other authors, who are then mentioned separately.)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package splitstree6.io.writers.view;

import jloda.graph.EdgeArray;
import jloda.graph.NodeArray;
import jloda.graph.io.GraphGML;
import jloda.util.StringUtils;
import splitstree6.data.TaxaBlock;
import splitstree6.data.ViewBlock;
import splitstree6.view.splits.viewer.SplitsView;

import java.io.IOException;
import java.io.Writer;
import java.util.BitSet;
import java.util.HashMap;

/**
 * write as text
 * Daniel Huson, 11.2021
 */
public class GMLWriter extends ViewWriterBase {
	public GMLWriter() {
		setFileExtensions("gml");
	}

	@Override
	public void write(Writer w, TaxaBlock taxaBlock, ViewBlock viewBlock) throws IOException {
		var view = viewBlock.getView();
		if (view == null)
			w.write("VIEW: not set");
		else if (view instanceof SplitsView splitsView) {
			var graph = splitsView.getPresenter().getSplitNetworkPane().getSplitNetworkLayout().getGraph();
			var nodePointMap = splitsView.getPresenter().getSplitNetworkPane().getSplitNetworkLayout().getNodePointMap();
			if (graph != null) {
				try (NodeArray<String> nodeLabelMap = graph.newNodeArray();
					 NodeArray<String> nodeXMap = graph.newNodeArray();
					 NodeArray<String> nodeYMap = graph.newNodeArray();
					 EdgeArray<String> edgeSplitMap = graph.newEdgeArray();
					 EdgeArray<String> edgeWeightMap = graph.newEdgeArray()) {

					for (var v : graph.nodes()) {
						if (graph.hasTaxa(v))
							nodeLabelMap.put(v, taxaBlock.getLabel(graph.getTaxon(v)));
						nodeXMap.put(v, StringUtils.removeTrailingZerosAfterDot("%.4f", nodePointMap.get(v).getX()));
						nodeYMap.put(v, StringUtils.removeTrailingZerosAfterDot("%.4f", nodePointMap.get(v).getY()));
					}
					var labelNodeValueMap = new HashMap<String, NodeArray<String>>();
					labelNodeValueMap.put("label", nodeLabelMap);
					labelNodeValueMap.put("x", nodeXMap);
					labelNodeValueMap.put("y", nodeYMap);

					var splits = new BitSet();
					for (var e : graph.edges()) {
						edgeSplitMap.put(e, String.valueOf(graph.getSplit(e)));
						splits.set(graph.getSplit(e));
						edgeWeightMap.put(e, StringUtils.removeTrailingZerosAfterDot("%.8f", graph.getWeight(e)));
					}
					var labelEdgeValueMap = new HashMap<String, EdgeArray<String>>();
					labelEdgeValueMap.put("split", edgeSplitMap);
					labelEdgeValueMap.put("weight", edgeWeightMap);
					var comment = "Exported from SplitsTreeCE: %,d nodes, %,d edges, %,d splits".formatted(graph.getNumberOfNodes(), graph.getNumberOfEdges(), splits.cardinality());
					var graphLabel = (graph.getName() != null ? graph.getName() : splitsView.getName());
					GraphGML.writeGML(graph, comment, graphLabel, false, 1, w,
							labelNodeValueMap, labelEdgeValueMap);
				}
			}
		} else {
			w.write("GML not implemented for view '" + view.getName() + "'");

		}
		w.write("\n");
		w.flush();
	}
}

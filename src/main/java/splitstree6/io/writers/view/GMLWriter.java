/*
 *  GMLWriter.java Copyright (C) 2024 Daniel H. Huson
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

package splitstree6.io.writers.view;

import jloda.graph.Edge;
import jloda.graph.Node;
import jloda.graph.io.GraphGML;
import jloda.util.StringUtils;
import splitstree6.data.TaxaBlock;
import splitstree6.data.ViewBlock;
import splitstree6.view.network.NetworkView;
import splitstree6.view.splits.viewer.SplitsView;

import java.io.IOException;
import java.io.Writer;
import java.util.List;
import java.util.TreeSet;
import java.util.function.BiFunction;

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
			if (graph != null) {
				var nodePointMap = splitsView.getPresenter().getSplitNetworkPane().getSplitNetworkLayout().getNodePointMap();
				var labelNodes = List.of("label", "x", "y");
				BiFunction<String, Node, String> labelNodeValue = (label, v) ->
						switch (label) {
							case "label" -> (graph.hasTaxa(v) ? taxaBlock.getLabel(graph.getTaxon(v)) : null);
							case "x" -> StringUtils.removeTrailingZerosAfterDot("%.4f", nodePointMap.get(v).getX());
							case "y" -> StringUtils.removeTrailingZerosAfterDot("%.4f", nodePointMap.get(v).getY());
							default -> null;
						};
				var labelEdges = List.of("split", "weight");
				BiFunction<String, Edge, String> labelEdgeValue = (label, e) -> switch (label) {
					case "split" -> String.valueOf(graph.getSplit(e));
					case "weight" -> StringUtils.removeTrailingZerosAfterDot("%.8f", graph.getWeight(e));
					default -> null;
				};
				var comment = "Exported from SplitsTree: %,d nodes, %,d edges, %,d splits".formatted(graph.getNumberOfNodes(), graph.getNumberOfEdges(), splitsView.getSplitsBlock().getNsplits());
				var graphLabel = (graph.getName() != null ? graph.getName() : splitsView.getName());
				GraphGML.writeGML(graph, comment, graphLabel, false, 1, w,
						labelNodes, labelNodeValue, labelEdges, labelEdgeValue);
			}
		} else if (view instanceof NetworkView networkView) {
			var networkBlock = networkView.getNetworkBlock();
			var graph = networkBlock.getGraph();
			if (graph != null) {
				var labelNodes = new TreeSet<String>();
				for (var v : graph.nodes()) {
					var data = networkBlock.getNodeData(v);
					labelNodes.addAll(data.keySet());
				}
				labelNodes.addAll(List.of("label", "x", "y"));

				var nodeShapeMap = networkView.getNodeShapeMap();

				BiFunction<String, Node, String> labelNodeValue = (label, v) ->
						switch (label) {
							case "label" ->
									nodeShapeMap.get(v).getLabel() != null ? nodeShapeMap.get(v).getLabel().getRawText() : null;
							case "x" ->
									StringUtils.removeTrailingZerosAfterDot("%.4f", nodeShapeMap.get(v).getTranslateX());
							case "y" ->
									StringUtils.removeTrailingZerosAfterDot("%.4f", nodeShapeMap.get(v).getTranslateY());
							default -> networkBlock.getNodeData(v).get(label);
						};
				var labelEdges = new TreeSet<String>();
				for (var e : graph.edges()) {
					var data = networkBlock.getEdgeData(e);
					labelEdges.addAll(data.keySet());
				}
				labelEdges.add("weight");
				BiFunction<String, Edge, String> labelEdgeValue = (label, e) ->
						switch (label) {
							case "weight" -> StringUtils.removeTrailingZerosAfterDot("%.8f", graph.getWeight(e));
							default -> networkView.getNetworkBlock().getEdgeData(e).get(label);
						};
				var comment = "Exported from SplitsTree: %,d nodes, %,d edges,".formatted(graph.getNumberOfNodes(), graph.getNumberOfEdges());
				var graphLabel = (graph.getName() != null ? graph.getName() : networkView.getName());
				GraphGML.writeGML(graph, comment, graphLabel, false, 1, w,
						labelNodes, labelNodeValue, labelEdges, labelEdgeValue);
			}
		} else {
			w.write("GML not implemented for view '" + view.getName() + "'");
		}
		w.write("\n");
		w.flush();
	}
}

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

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.geometry.Point2D;
import jloda.graph.Edge;
import jloda.graph.Node;
import jloda.graph.io.GraphGML;
import jloda.util.NumberUtils;
import jloda.util.ProgramProperties;
import jloda.util.StringUtils;
import splitstree6.data.NetworkBlock;
import splitstree6.data.TaxaBlock;
import splitstree6.layout.network.WeightedLayout;

import java.io.IOException;
import java.io.Writer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.function.BiFunction;

/**
 * write a network in GML format, for import into Cytoscape, igraph, Gephi and the like
 * <p>
 * Unlike {@link splitstree6.io.writers.view.GMLWriter}, which exports what the network <i>viewer</i>
 * shows and therefore reads coordinates off the node shapes, this writes the {@link NetworkBlock}
 * itself: node labels, edge weights, and whatever key-value pairs the algorithm attached to nodes and
 * edges. So it also works headless, where no view exists.
 * <p>
 * To make the output usable without further work, node coordinates are computed here (see
 * {@code optionLayout}), so the receiving program has a drawing rather than a bare edge list.
 * Daniel Huson, 8.2026
 */
public class GMLWriter extends NetworkWriterBase {
	/**
	 * The layout is deterministic: same network in, same drawing out. The viewer deliberately varies its
	 * layout (it alternates engines on the parity of its random seed), which is the wrong behavior for a
	 * file format, so the seed is fixed here instead of being exposed.
	 */
	private static final long LAYOUT_SEED = 42L;
	private static final int LAYOUT_MAX_ITERATIONS = 5000;

	/**
	 * Zero-length edges occur (coincident nodes in haplotype networks). {@link WeightedLayout} drops
	 * non-positive lengths from its adjacency, which would cut those nodes loose from the graph and let
	 * them drift apart, so lengths are floored just above zero rather than dropped.
	 */
	private static final double MIN_EDGE_LENGTH = 0.00001;

	private final BooleanProperty optionLayout = new SimpleBooleanProperty(this, "optionLayout", true);

	public GMLWriter() {
		setFileExtensions("gml");
	}

	@Override
	public void write(Writer w, TaxaBlock taxaBlock, NetworkBlock networkBlock) throws IOException {
		var graph = networkBlock.getGraph();
		if (graph == null)
			return;

		var nodePointMap = computeNodePoints(networkBlock);

		// the attributes are the union of all keys present, so an algorithm that labels only some nodes
		// (mutations on some edges, say) still gets those keys written
		var nodeLabelNames = new TreeSet<String>();
		for (var v : graph.nodes()) {
			nodeLabelNames.addAll(networkBlock.getNodeData(v).keySet());
		}
		nodeLabelNames.add("label");
		if (!nodePointMap.isEmpty())
			nodeLabelNames.addAll(List.of("x", "y"));

		BiFunction<String, Node, String> labelNodeValue = (label, v) ->
				switch (label) {
					case "label" -> graph.hasTaxa(v) ? taxaBlock.getLabel(graph.getTaxon(v)) : graph.getLabel(v);
					case "x" -> nodePointMap.containsKey(v) ? StringUtils.trim("%.4f", nodePointMap.get(v).getX())
							: networkBlock.getNodeData(v).get("x");
					case "y" -> nodePointMap.containsKey(v) ? StringUtils.trim("%.4f", nodePointMap.get(v).getY())
							: networkBlock.getNodeData(v).get("y");
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

	/**
	 * Determines the node coordinates to write, if any.
	 * <p>
	 * Coordinates already present in the block (a network of type {@code Points}, say) are used as they
	 * are. Otherwise, and only when {@code optionLayout} is set, the network is laid out with
	 * {@link WeightedLayout}, which places nodes so that Euclidean distance approximates distance in the
	 * graph. That is the layout to use here: a network produced by a metric realization <i>is</i> its
	 * distances, so a drawing that reproduces them carries the content, whereas a purely topological
	 * layout (the FMMM engine the viewer uses for its Topology diagram) would discard it.
	 * <p>
	 * Two deliberate differences from the viewer, both to serve a file rather than a screen:
	 * <ul>
	 *     <li>the true edge weights are used, not the compressed ones from
	 *     {@code NetworkLayout.setupScaling}, which exist to keep long edges on screen;</li>
	 *     <li>{@code centerAndScale} is off, so coordinates come out in the same units as the edge
	 *     weights instead of in a unit box &mdash; a distance of 146 in the input is about 146 units in
	 *     the drawing.</li>
	 * </ul>
	 * Note that the result therefore does not reproduce what the viewer happens to be showing; for that,
	 * export the view instead.
	 * <p>
	 * The coordinates are a <i>drawing</i>, not data: most metrics do not embed in the plane, so distance
	 * measured in the drawing only approximates the real distance, and the more complex the metric the
	 * looser that approximation gets (on our test instances, mean error over the taxon pairs runs from
	 * about 8% on a 5-taxon tree to about 27% on a 20-taxon generic metric). The distances live in the
	 * {@code weight} attribute of the edges, which is exact.
	 */
	private Map<Node, Point2D> computeNodePoints(NetworkBlock networkBlock) {
		var graph = networkBlock.getGraph();
		var nodePointMap = new HashMap<Node, Point2D>();

		if (graph.getNumberOfNodes() == 0)
			return nodePointMap;

		// coordinates the algorithm already determined win over anything we would compute
		var haveAllPoints = true;
		for (var v : graph.nodes()) {
			var data = networkBlock.getNodeData(v);
			var x = data.get(NetworkBlock.NodeData.BasicKey.x.name());
			var y = data.get(NetworkBlock.NodeData.BasicKey.y.name());
			if (!NumberUtils.isDouble(x) || !NumberUtils.isDouble(y)) {
				haveAllPoints = false;
				break;
			}
			nodePointMap.put(v, new Point2D(NumberUtils.parseDouble(x), NumberUtils.parseDouble(y)));
		}
		if (haveAllPoints)
			return nodePointMap;
		nodePointMap.clear();

		if (!isOptionLayout())
			return nodePointMap;

		var params = new WeightedLayout.Params();
		params.maxIterations = LAYOUT_MAX_ITERATIONS;
		params.randomSeed = LAYOUT_SEED;
		params.centerAndScale = false; // keep the drawing in the units of the edge weights
		new WeightedLayout<Node, Edge>().layout(graph.getNodesAsList(), Node::adjacentEdges, Node::getOpposite,
				e -> Math.max(MIN_EDGE_LENGTH, graph.getWeight(e)), nodePointMap::put, params);
		return nodePointMap;
	}

	public boolean isOptionLayout() {
		return optionLayout.get();
	}

	public BooleanProperty optionLayoutProperty() {
		return optionLayout;
	}

	public void setOptionLayout(boolean optionLayout) {
		this.optionLayout.set(optionLayout);
	}
}

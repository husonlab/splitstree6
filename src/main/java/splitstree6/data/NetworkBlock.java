/*
 *  NetworkBlock.java Copyright (C) 2024 Daniel H. Huson
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

package splitstree6.data;

import jloda.graph.Edge;
import jloda.graph.EdgeArray;
import jloda.graph.Node;
import jloda.graph.NodeArray;
import jloda.phylo.PhyloGraph;
import splitstree6.algorithms.network.network2network.NetworkTaxaFilter;
import splitstree6.workflow.DataBlock;
import splitstree6.workflow.DataTaxaFilter;

import java.util.HashMap;

public class NetworkBlock extends DataBlock {
	public static final String NODE_STATES_KEY = "states";
	public static final String EDGE_SITES_KEY = "sites";

	/**
	 * what the edge weights of this network mean, and so how it should be drawn and reported on
	 * <p>
	 * A closed set of KINDS of object, not a cross of provenance and rendering: the question is what the network
	 * realizes, not what it happens to be labelled with. A minimum spanning network is computed from distances
	 * and is a {@code DistanceNetwork} even though it labels its nodes with sequences when it can find them --
	 * its weights are lengths, so distortion is what means something for it.
	 * <ul>
	 * <li>{@code HaplotypeNetwork} -- weights are numbers of mutations; report length and excess
	 * <li>{@code DistanceNetwork} -- weights are lengths realizing a metric; report length and distortion
	 * <li>{@code Points} -- the edges carry no meaning and the nodes carry coordinates (PCoA): do not lay it out
	 * and do not filter its edges
	 * <li>{@code Other} -- unknown, the default; consumers fall back to inspecting the workflow
	 * </ul>
	 */
	public enum Type {HaplotypeNetwork, DistanceNetwork, Points, Other}

	private final PhyloGraph graph;
	private final NodeArray<NodeData> node2data;
	private final EdgeArray<EdgeData> edge2data;

	private String infoString = "";

	// never null: the initializer, clear() and the null-coercing setter between them see to that, because
	// NetworkLayout dereferences it and a block built outside the workflow has never been cleared
	private Type networkType = Type.Other;

	public NetworkBlock() {
		graph = new PhyloGraph();
		node2data = new NodeArray<>(graph);
		edge2data = new EdgeArray<>(graph);
		//getNetworkNodes().addListener((InvalidationListener) observable -> setShortDescription(getInfo()));
	}

	public void clear() {
		graph.clear();
		node2data.clear();
		edge2data.clear();
		networkType = Type.Other;
		infoString = "";
	}

	public void copy(NetworkBlock that) {
		clear();
		NodeArray<Node> oldNode2new = that.getGraph().newNodeArray();
		EdgeArray<Edge> oldEdge2new = that.getGraph().newEdgeArray();
		graph.copy(that.getGraph(), oldNode2new, oldEdge2new);
		setNetworkType(that.getNetworkType()); // a filter keeps the kind of network it was given
		for (var v : oldNode2new.keys()) {
			getNodeData(oldNode2new.get(v)).putAll((that.getNodeData(v)));
		}
		for (var e : oldEdge2new.keys()) {
			getEdgeData(oldEdge2new.get(e)).putAll((that.getEdgeData(e)));
		}
		// Deliberately do NOT copy infoString: it is a derived display cache (total length / distortion / excess,
		// computed lazily by the viewer). A copy is a new, possibly-modified network -- e.g. the output of a
		// network-to-network filter such as StretchFilter, which removes edges -- so its info must be recomputed
		// from its own content, not inherited from the source (clear() above already left it blank).
	}

	public PhyloGraph getGraph() {
		return graph;
	}

	public NodeArray<NodeData> getNode2data() {
		return node2data;
	}

	public EdgeArray<EdgeData> getEdge2data() {
		return edge2data;
	}

	public Type getNetworkType() {
		return networkType;
	}

	/** null is taken to mean {@link Type#Other} -- an unknown kind, not a missing field */
	public void setNetworkType(Type networkType) {
		this.networkType = (networkType == null ? Type.Other : networkType);
	}

	public NodeData getNodeData(Node v) {
		NodeData nodeData = node2data.get(v);
		if (nodeData == null) {
			nodeData = new NodeData();
			node2data.put(v, nodeData);
		}
		return nodeData;
	}

	public void removeNodeData(Node v) {
		node2data.remove(v);
	}

	public EdgeData getEdgeData(Edge e) {
		EdgeData edgeData = edge2data.get(e);
		if (edgeData == null) {
			edgeData = new EdgeData();
			edge2data.put(e, edgeData);
		}
		return edgeData;
	}


	@Override
	public int size() {
		return getNumberOfNodes();
	}

	public int getNumberOfNodes() {
		return graph.getNumberOfNodes();
	}

	public int getNumberOfEdges() {
		return graph.getNumberOfEdges();
	}

	@Override
	public DataTaxaFilter<? extends DataBlock, ? extends DataBlock> createTaxaDataFilter() {
		return new NetworkTaxaFilter(NetworkBlock.class, NetworkBlock.class);
	}

	public static class NodeData extends HashMap<String, String> {
		public enum BasicKey {x, y, h, w, label}
	}

	public static class EdgeData extends HashMap<String, String> {
		public enum BasicKey {type, c1, c2, label}
	}

	@Override
	public NetworkBlock newInstance() {
		return (NetworkBlock) super.newInstance();
	}

	public static final String BLOCK_NAME = "NETWORK";

	@Override
	public void updateShortDescription() {
		setShortDescription(String.format("%,d nodes and %,d edges", graph.getNumberOfNodes(), graph.getNumberOfEdges()));
	}

	@Override
	public String getBlockName() {
		return BLOCK_NAME;
	}

	public String getInfoString() {
		return infoString;
	}

	public void setInfoString(String infoString) {
		this.infoString = infoString;
	}
}

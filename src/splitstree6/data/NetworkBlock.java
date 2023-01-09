/*
 * NetworkBlock.java Copyright (C) 2023 Daniel H. Huson
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

	public enum Type {HaplotypeNetwork, Points, Other}

	private final PhyloGraph graph;
	private final NodeArray<NodeData> node2data;
	private final EdgeArray<EdgeData> edge2data;

	private String infoString = "";

	private Type networkType;

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
		this.networkType = that.getNetworkType();
		for (var v : oldNode2new.keys()) {
			getNodeData(oldNode2new.get(v)).putAll((that.getNodeData(v)));
		}
		for (var e : oldEdge2new.keys()) {
			getEdgeData(oldEdge2new.get(e)).putAll((that.getEdgeData(e)));
		}
		this.infoString = that.infoString;
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

	public void setNetworkType(Type networkType) {
		this.networkType = networkType;
	}

	public NodeData getNodeData(Node v) {
		NodeData nodeData = node2data.get(v);
		if (nodeData == null) {
			nodeData = new NodeData();
			node2data.put(v, nodeData);
		}
		return nodeData;
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

/*
 * PhyloGraphAdapter.java Copyright (C) 2025 Daniel H. Huson
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

package razornetaccess;

import jloda.graph.Edge;
import jloda.graph.Node;
import jloda.util.IteratorUtils;
import razornet.utils.PhyloGraph;

import java.util.Set;

public class PhyloGraphAdapter implements PhyloGraph<Node, Edge> {
	private final jloda.phylo.PhyloGraph graph;

	public PhyloGraphAdapter(jloda.phylo.PhyloGraph graph) {
		this.graph = graph;
	}

	public jloda.phylo.PhyloGraph getGraph() {
		return graph;
	}

	@Override
	public int getNumberOfNodes() {
		return graph.getNumberOfNodes();
	}

	@Override
	public int getNumberOfEdges() {
		return graph.getNumberOfEdges();
	}

	@Override
	public Iterable<Node> nodes() {
		return graph.nodes();
	}

	@Override
	public int getDegree(Node node) {
		return node.getDegree();
	}

	@Override
	public Iterable<Edge> edges() {
		return graph.edges();
	}

	@Override
	public Set<Edge> edgesAsSet() {
		return IteratorUtils.asSet(graph.edges());
	}

	@Override
	public double getWeight(Edge edge) {
		return graph.getWeight(edge);
	}

	@Override
	public void setWeight(Edge edge, double weight) {
		graph.setWeight(edge, weight);
	}

	@Override
	public Iterable<Node> getAdjacentNodes(Node node) {
		return node.adjacentNodes();
	}

	@Override
	public Iterable<Edge> getAdjacentEdges(Node node) {
		return node.adjacentEdges();
	}

	@Override
	public Node getOpposite(Edge edge, Node v) {
		return edge.getOpposite(v);
	}

	@Override
	public void setTaxon(Node node, int taxon) {
		graph.addTaxon(node, taxon);
	}

	@Override
	public int getTaxon(Node node) {
		return graph.hasTaxa(node) ? graph.getTaxon(node) : -1;
	}

	@Override
	public void setLabel(Node node, String label) {
		graph.setLabel(node, label);
	}

	@Override
	public String getLabel(Node node) {
		return graph.getLabel(node);
	}

	@Override
	public Node newNode() {
		return graph.newNode();
	}

	@Override
	public Edge newEdge(Node s, Node t) {
		return graph.newEdge(s, t);
	}

	@Override
	public boolean hasEdge(Node s, Node t) {
		return s.isAdjacent(t);
	}

	@Override
	public Edge getEdge(Node s, Node t) {
		return s.getCommonEdge(t);
	}

	@Override
	public Node getSource(Edge edge) {
		return edge.getSource();
	}

	@Override
	public Node getTarget(Edge edge) {
		return edge.getTarget();
	}

	@Override
	public void deleteEdge(Edge edge) {

	}

	@Override
	public void deleteNode(Node node) {
		graph.deleteNode(node);
	}
}

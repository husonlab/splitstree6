/*
 *  DimensionFilter.java Copyright (C) 2022 Daniel H. Huson
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

package splitstree6.algorithms.splits.splits2splits;

import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;
import jloda.fx.window.NotificationManager;
import jloda.graph.Edge;
import jloda.graph.Graph;
import jloda.graph.Node;
import jloda.graph.NodeSet;
import jloda.util.Basic;
import jloda.util.CanceledException;
import jloda.util.Pair;
import jloda.util.progress.ProgressListener;
import splitstree6.algorithms.IFilter;
import splitstree6.algorithms.utils.SplitsUtilities;
import splitstree6.data.SplitsBlock;
import splitstree6.data.TaxaBlock;
import splitstree6.data.parts.ASplit;
import splitstree6.data.parts.Compatibility;

import java.io.IOException;
import java.util.BitSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * heuristic dimension filter
 * Daniel Huson, 5.2004
 */
public class DimensionFilter extends Splits2Splits implements IFilter {
	private final IntegerProperty optionMaxDimension = new SimpleIntegerProperty(this, "optionMaxDimension", 4);
	private boolean active;

	private final static int COMPUTE_DSUBGRAPH_MAXDIMENSION = 5;

	@Override
	public List<String> listOptions() {
		return List.of(optionMaxDimension.getName());
	}

	@Override
	public String getToolTip(String optionName) {
		if (optionName.equals(optionMaxDimension.getName())) {
			return "Heuristically remove splits that create configurations of a higher dimension than this threshold";
		}
		return optionName;
	}

	/**
	 * heuristically remove high-dimension configurations in split graph
	 */
	public void compute(ProgressListener progress, TaxaBlock taxaBlock, SplitsBlock parent, SplitsBlock child) throws IOException {
		active = false;

		if (parent.getCompatibility() != Compatibility.compatible && parent.getCompatibility() != Compatibility.cyclic && parent.getCompatibility() != Compatibility.weaklyCompatible) {
			apply(progress, getOptionMaxDimension(), parent.getSplits(), child.getSplits());
			if (parent.getNsplits() == child.getNsplits()) {
				child.setCycle(parent.getCycle().clone());
				child.setCompatibility(parent.getCompatibility());
				setShortDescription("using all " + parent.getNsplits() + " splits");
			} else {
				child.setCycle(SplitsUtilities.computeCycle(taxaBlock.getNtax(), child.getSplits()));
				child.setFit(-1);
				child.setCompatibility(Compatibility.compute(taxaBlock.getNtax(), child.getSplits(), child.getCycle()));
				child.setThreshold(parent.getThreshold());
				setShortDescription("using " + child.getNsplits() + " of " + parent.getNsplits() + " splits");
				active = true;
			}
		}
	}

	/**
	 * does the work
	 */
	public void apply(ProgressListener progress, int maxDimension, List<ASplit> srcSplits, List<ASplit> targetSplits) {
		final BitSet toDelete = new BitSet(); // set of splits to be removed from split set

		try {
			progress.setTasks("Dimension filter", "optionMaxDimension=" + optionMaxDimension.get());
			// build initial incompatibility graph:
			Graph graph = buildIncompatibilityGraph(srcSplits);

			//System.err.println("Init: "+graph);
			int origNumberOfNodes = graph.getNumberOfNodes();
			progress.setMaximum(origNumberOfNodes);    //initialize maximum progress
			progress.setProgress(0);

			if (maxDimension <= COMPUTE_DSUBGRAPH_MAXDIMENSION) {
				//System.err.println("(Small D: using D-subgraph)");
				computeDSubgraph(progress, graph, maxDimension + 1);
			} else {
				//System.err.println("(Large D: using maxDegree heuristic)");
				relaxGraph(progress, graph, maxDimension - 1);
			}
			//System.err.println("relaxed: "+graph);

			while (graph.getNumberOfNodes() > 0) {
				Node worstNode = getWorstNode(graph);
				int s = ((Pair<Integer, Integer>) worstNode.getInfo()).getFirst();
				toDelete.set(s);
				graph.deleteNode(worstNode);
				//System.err.println("deleted: "+graph);

				if (maxDimension <= COMPUTE_DSUBGRAPH_MAXDIMENSION)
					computeDSubgraph(progress, graph, maxDimension + 1);
				else
					relaxGraph(progress, graph, maxDimension - 1);
				//System.err.println("relaxed: "+graph);
				progress.setProgress(origNumberOfNodes - graph.getNumberOfNodes());
			}
		} catch (Exception ex) {
			Basic.caught(ex);
		}

		for (int i = toDelete.nextClearBit(0); i != -1 && i < srcSplits.size(); i = toDelete.nextClearBit(i + 1))
			targetSplits.add(srcSplits.get(i));

		//System.err.println("Splits removed: " + toDelete.cardinality());
		if (toDelete.cardinality() > 0)
			NotificationManager.showInformation("Dimension filter removed " + toDelete.cardinality() + " splits");
	}

	/**
	 * build the incompatibility graph
	 *
	 * @param splits
	 * @return incompatibility graph
	 */
	private Graph buildIncompatibilityGraph(List<ASplit> splits) {
		final Graph graph = new Graph();

		final Node[] split2node = new Node[splits.size()];

		for (int s = 0; s < splits.size(); s++) {
			final Pair<Integer, Integer> pair = new Pair<>(s, (int) (10000 * splits.get(s).getWeight()));
			split2node[s] = graph.newNode(pair);
		}
		for (int s = 0; s < splits.size(); s++) {

			for (int t = s + 1; t < splits.size(); t++)
				if (!Compatibility.areCompatible(splits.get(s), splits.get(t))) {
					graph.newEdge(split2node[s], split2node[t]);
				}
		}
		return graph;
	}

	/**
	 * computes the subgraph in which every node is contained in a d-clique
	 *
	 * @param graph
	 * @param d     clique size
	 */
	private void computeDSubgraph(ProgressListener progress, Graph graph, int d) throws CanceledException {
		//System.err.print("Compute D-subgraph: ");
		NodeSet keep = new NodeSet(graph);
		NodeSet discard = new NodeSet(graph);
		NodeSet clique = new NodeSet(graph);
		for (Node v : graph.nodes()) {
			if (!keep.contains(v)) {
				clique.clear();
				clique.add(v);
				if (findClique(graph, v, v.getFirstAdjacentEdge(), 1, d, clique, discard))
					keep.addAll(clique);
				else
					discard.add(v);
			}
			progress.checkForCancel();
		}

		// remove all nodes not contained in a d-clique
		for (Node v : discard) {
			graph.deleteNode(v);
		}
		//System.err.println(" "+graph.getNumberOfNodes());
	}

	/**
	 * recursively determine whether v is contained in a d-clique.
	 *
	 * @return true, if v contained in a d-clique
	 */
	private boolean findClique(Graph graph, Node v, Edge e, int i, int d, NodeSet clique, NodeSet discard) {
		if (i == d)
			return true;  // found clique, retreat
		else {
			while (e != null) {
				Node w = graph.getOpposite(v, e);
				e = v.getNextAdjacentEdge(e);

				if (isConnectedTo(w, clique) && !discard.contains(w)) {
					clique.add(w);
					if (findClique(graph, v, e, i + 1, d, clique, discard))
						return true;
					clique.remove(w);
				}
			}
			return false; // didn't work out, try different combination
		}
	}

	/**
	 * determines whether node w is connected to all nodes in U
	 *
	 * @return true, if w is connected to all nodes in U
	 */
	private boolean isConnectedTo(Node w, NodeSet U) {
		int count = 0;
		for (Node u : w.adjacentNodes()) {
			if (U.contains(u)) {
				count++;
				if (count == U.size())
					return true;
			}
		}
		return false;
	}

	/**
	 * Modify graph to become the maximal induced graph in which all nodes have degree >maxDegree
	 * If maxDegree==1, then we additionally require that all remaining nodes are contained in a triangle
	 */
	private void relaxGraph(ProgressListener progress, Graph graph, int maxDegree) throws CanceledException {
		System.err.print("Relax graph: ");

		int maxDegreeHeuristicThreshold = 6; // use heuristic for max degrees above this threshold
		Set<Node> active = new HashSet<>();
		for (Node v : graph.nodes()) {
			if (v.getDegree() < maxDegree
				|| (maxDegree <= maxDegreeHeuristicThreshold && hasDegreeDButNotInClique(maxDegree + 1, graph, v)))
				active.add(v);
		}

		while (!active.isEmpty()) {
			Node v = active.iterator().next();
			if (v.getDegree() < maxDegree || (maxDegree <= maxDegreeHeuristicThreshold && hasDegreeDButNotInClique(maxDegree + 1, graph, v))) {
				for (Node w : v.adjacentNodes()) {
					active.add(w);
				}
				active.remove(v);
				graph.deleteNode(v);
			} else
				active.remove(v);
			progress.checkForCancel();
		}
		System.err.println("" + graph.getNumberOfNodes());
	}


	/**
	 * gets the node will the lowest compatability score
	 *
	 * @return worst node
	 */
	private Node getWorstNode(Graph graph) {
		float worstCompatibility = 0;
		Node worstNode = null;
		for (Node v : graph.nodes()) {
			float compatibility = getCompatibilityScore(v);
			if (worstNode == null || compatibility < worstCompatibility) {
				worstNode = v;
				worstCompatibility = compatibility;
			}
		}
		return worstNode;
	}

	/**
	 * gets the compatibility score of a node.
	 * This is the weight of the splits minus the weight of all contradicting splits
	 *
	 * @return compatibility score
	 */
	private int getCompatibilityScore(Node v) {
		int score = ((Pair<Integer, Integer>) v.getInfo()).getSecond();
		for (Node w : v.adjacentNodes()) {
			score -= ((Pair<Integer, Integer>) w.getInfo()).getSecond();
		}
		return score;
	}


	/**
	 * determines whether the node v has degree==d but  is not contained in a clique of size d+1
	 *
	 * @param d* @param graph
	 * @return false, if the node v has degree!=d or is contained in a d+1 clique
	 */
	private boolean hasDegreeDButNotInClique(int d, Graph graph, Node v) {
		if (v.getDegree() != d)
			return false;
		for (Edge e = v.getFirstAdjacentEdge(); e != null; e = v.getNextAdjacentEdge(e)) {
			Node a = graph.getOpposite(v, e);
			for (Edge f = v.getNextAdjacentEdge(e); f != null; f = v.getNextAdjacentEdge(f)) {
				Node b = graph.getOpposite(v, f);
				if (!a.isAdjacent(b))
					return true;
			}
		}
		return false;
	}

	public int getOptionMaxDimension() {
		return optionMaxDimension.get();
	}

	public IntegerProperty optionMaxDimensionProperty() {
		return optionMaxDimension;
	}

	public void setOptionMaxDimension(int optionMaxDimension) {
		this.optionMaxDimension.set(optionMaxDimension);
	}

	@Override
	public boolean isActive() {
		return active;
	}
}


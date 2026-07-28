/*
 *  TracedEdgeWeights.java Copyright (C) 2024 Daniel H. Huson
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

package splitstree6.compute.phylofusion;

import jloda.graph.Edge;
import jloda.graph.Node;
import jloda.graph.NodeArray;
import jloda.phylo.PhyloTree;
import org.ojalgo.optimisation.ExpressionsBasedModel;
import org.ojalgo.optimisation.Variable;

import java.util.*;

/**
 * fits branch lengths of a PhyloFusion network from the input-tree branch lengths, using the tree tracing: each tree
 * edge maps (via {@link TreeTracing}) to the network edges along its displayed path, giving one equation
 * "sum of the covered network-edge lengths = tree-edge length". The equations are solved by averaging, or as an
 * L1 (LP) / L2 (NNLS) fit with non-negative lengths (optionally forcing reticulate edges to zero).
 * <p>
 * Requires the network's nodes and
 * reticulate edges to carry trace ids (run {@link TreeTracing#complete} first).
 * Banu Cetinkaya, 2026
 */
public class TracedEdgeWeights {
	public enum Method {AVERAGE, LP, NNLS, LP_RETICULATES_ZERO}

	private TracedEdgeWeights() {
	}

	/**
	 * fit the network edge weights with the requested method
	 */
	public static void apply(Method method, List<PhyloTree> inputTrees, PhyloTree network) {
		switch (method) {
			case AVERAGE -> mean(inputTrees, network);
			case LP -> lp(inputTrees, network);
			case NNLS -> nnls(inputTrees, network);
			case LP_RETICULATES_ZERO -> lpReticulatesZero(inputTrees, network);
		}
	}

	// ---- the three fitting objectives -------------------------------------------------------------------------------

	/**
	 * each tree edge distributes its length equally over the network edges it covers; every network edge takes the mean
	 */
	public static void mean(List<PhyloTree> inputTrees, PhyloTree network) {
		var treeEdgeToNetworkEdges = computeTreeEdgeToNetworkEdgeMap(inputTrees, network);
		var sum = new HashMap<Edge, Double>();
		var count = new HashMap<Edge, Integer>();
		for (var f : network.edges()) {
			sum.put(f, 0.0);
			count.put(f, 0);
		}
		for (var treeId = 0; treeId < inputTrees.size(); treeId++) {
			var tree = inputTrees.get(treeId);
			var treeMapping = treeEdgeToNetworkEdges.get(treeId);
			for (var e : tree.edges()) {
				var covered = treeMapping.get(e);
				if (covered == null || covered.isEmpty())
					continue;
				var contribution = tree.getWeight(e) / covered.size();
				for (var f : covered) {
					sum.put(f, sum.get(f) + contribution);
					count.put(f, count.get(f) + 1);
				}
			}
		}
		for (var f : network.edges())
			network.setWeight(f, count.get(f) == 0 ? 0.0 : sum.get(f) / count.get(f));
	}

	/**
	 * L1 fit (linear program) with non-negative edge lengths
	 */
	public static void lp(List<PhyloTree> inputTrees, PhyloTree network) {
		var treeEdgeToNetworkEdges = computeTreeEdgeToNetworkEdgeMap(inputTrees, network);
		var model = new ExpressionsBasedModel();
		var x = new HashMap<Edge, Variable>();
		var edgeId = 0;
		for (var f : network.edges())
			x.put(f, model.addVariable("x_" + edgeId++).lower(0.0));

		addAbsoluteErrorEquations(model, inputTrees, treeEdgeToNetworkEdges, x);

		var solution = model.minimise();
		if (!solution.getState().isFeasible()) {
			System.err.println("LP edge-weight fit failed (" + solution.getState() + "), falling back to Average");
			mean(inputTrees, network);
			return;
		}
		for (var f : network.edges())
			network.setWeight(f, round(x.get(f).getValue().doubleValue()));
	}

	/**
	 * L1 fit as {@link #lp}, but every reticulate edge is forced to length zero
	 */
	public static void lpReticulatesZero(List<PhyloTree> inputTrees, PhyloTree network) {
		var treeEdgeToNetworkEdges = computeTreeEdgeToNetworkEdgeMap(inputTrees, network);
		var model = new ExpressionsBasedModel();
		var x = new HashMap<Edge, Variable>();
		var edgeId = 0;
		for (var f : network.edges()) {
			var variable = model.addVariable("x_" + edgeId++);
			if (f.getTarget().getInDegree() > 1)
				variable.level(0.0); // reticulate edge: fixed to zero
			else
				variable.lower(0.0);
			x.put(f, variable);
		}

		addAbsoluteErrorEquations(model, inputTrees, treeEdgeToNetworkEdges, x);

		var solution = model.minimise();
		if (!solution.getState().isFeasible()) {
			System.err.println("LP edge-weight fit failed (" + solution.getState() + "), falling back to Average");
			mean(inputTrees, network);
			return;
		}
		for (var f : network.edges())
			network.setWeight(f, round(x.get(f).getValue().doubleValue()));
	}

	/**
	 * L2 (least squares) fit with non-negative edge lengths
	 */
	public static void nnls(List<PhyloTree> inputTrees, PhyloTree network) {
		var treeEdgeToNetworkEdges = computeTreeEdgeToNetworkEdgeMap(inputTrees, network);
		var model = new ExpressionsBasedModel();
		var x = new HashMap<Edge, Variable>();
		var edgeId = 0;
		for (var f : network.edges())
			x.put(f, model.addVariable("x_" + edgeId++).lower(0.0));

		var objective = model.addExpression("sum_squared_residuals").weight(1.0);
		var rowId = 0;
		for (var treeId = 0; treeId < inputTrees.size(); treeId++) {
			var tree = inputTrees.get(treeId);
			var treeMapping = treeEdgeToNetworkEdges.get(treeId);
			for (var treeEdge : tree.edges()) {
				var covered = treeMapping.get(treeEdge);
				if (covered == null || covered.isEmpty())
					continue;
				var residual = model.addVariable("r_" + rowId);
				objective.set(residual, residual, 1.0); // minimise residual^2
				var equation = model.addExpression("eq_" + rowId).level(tree.getWeight(treeEdge));
				for (var networkEdge : covered)
					equation.set(x.get(networkEdge), 1.0);
				equation.set(residual, -1.0);
				rowId++;
			}
		}

		var solution = model.minimise();
		if (!solution.getState().isFeasible()) {
			System.err.println("NNLS edge-weight fit failed (" + solution.getState() + "), falling back to Average");
			mean(inputTrees, network);
			return;
		}
		for (var f : network.edges())
			network.setWeight(f, round(x.get(f).getValue().doubleValue()));
	}

	/**
	 * one L1 equation per mapped tree edge: (sum of covered edge lengths) + errMinus - errPlus = treeEdgeLength
	 */
	private static void addAbsoluteErrorEquations(ExpressionsBasedModel model, List<PhyloTree> inputTrees,
												  Map<Integer, Map<Edge, ArrayList<Edge>>> treeEdgeToNetworkEdges, Map<Edge, Variable> x) {
		var constraintId = 0;
		for (var treeId = 0; treeId < inputTrees.size(); treeId++) {
			var tree = inputTrees.get(treeId);
			var treeMapping = treeEdgeToNetworkEdges.get(treeId);
			for (var treeEdge : tree.edges()) {
				var covered = treeMapping.get(treeEdge);
				if (covered == null || covered.isEmpty())
					continue;
				var errPlus = model.addVariable("p_" + constraintId).lower(0.0).weight(1.0);
				var errMinus = model.addVariable("m_" + constraintId).lower(0.0).weight(1.0);
				var constraint = model.addExpression("tree_edge_" + constraintId).level(tree.getWeight(treeEdge));
				for (var networkEdge : covered)
					constraint.set(x.get(networkEdge), 1.0);
				constraint.set(errMinus, 1.0);
				constraint.set(errPlus, -1.0);
				constraintId++;
			}
		}
	}

	private static double round(double weight) {
		return Math.abs(weight) < 1e-10 ? 0.0 : weight;
	}

	/**
	 * report goodness of fit (sum of covered network-edge lengths vs. tree-edge length) to stderr
	 */
	public static void printFitStatistics(Method method, List<PhyloTree> inputTrees, PhyloTree network) {
		var map = computeTreeEdgeToNetworkEdgeMap(inputTrees, network);
		var sse = 0.0;
		var sae = 0.0;
		var maxAbs = 0.0;
		var count = 0;
		for (var treeId = 0; treeId < inputTrees.size(); treeId++) {
			var tree = inputTrees.get(treeId);
			var treeMapping = map.get(treeId);
			for (var treeEdge : tree.edges()) {
				var covered = treeMapping.get(treeEdge);
				if (covered == null || covered.isEmpty())
					continue;
				var predicted = 0.0;
				for (var networkEdge : covered)
					predicted += network.getWeight(networkEdge);
				var residual = predicted - tree.getWeight(treeEdge);
				sse += residual * residual;
				sae += Math.abs(residual);
				maxAbs = Math.max(maxAbs, Math.abs(residual));
				count++;
			}
		}
		System.err.printf("%s fit: equations=%d SSE=%.8f RMSE=%.8f MAE=%.8f MaxAbs=%.8f%n",
				method, count, sse, count == 0 ? 0.0 : Math.sqrt(sse / count), count == 0 ? 0.0 : sae / count, maxAbs);
	}

	// ---- tracing-driven mapping of tree edges to the network edges they cover ---------------------------------------

	/**
	 * for each input tree, map every tree edge to the network edges along the path that displays it (found by
	 * following only the edges the tree uses according to the trace)
	 */
	public static Map<Integer, Map<Edge, ArrayList<Edge>>> computeTreeEdgeToNetworkEdgeMap(List<PhyloTree> inputTrees, PhyloTree network) {
		var result = new HashMap<Integer, Map<Edge, ArrayList<Edge>>>();
		for (var treeId = 0; treeId < inputTrees.size(); treeId++) {
			var tree = inputTrees.get(treeId);

			var displayedClusterToPath = new HashMap<BitSet, ArrayList<Edge>>();
			for (var path : extractDisplayedEdgePaths(network, treeId)) {
				var cluster = taxaReachableBelow(network, path.target(), treeId);
				if (!cluster.isEmpty())
					displayedClusterToPath.put(cluster, path.networkEdges());
			}

			var treeEdgeToNetworkEdges = new HashMap<Edge, ArrayList<Edge>>();
			var treeEdgeClusters = computeEdgeClusters(tree);
			for (var e : tree.edges()) {
				var covered = displayedClusterToPath.get(treeEdgeClusters.get(e));
				treeEdgeToNetworkEdges.put(e, covered != null ? covered : new ArrayList<>());
			}
			result.put(treeId, treeEdgeToNetworkEdges);
		}
		return result;
	}

	private record DisplayedPath(Node source, Node target, ArrayList<Edge> networkEdges) {
	}

	/**
	 * the maximal paths through the network that tree treeId displays, each running between two "essential" nodes
	 */
	private static ArrayList<DisplayedPath> extractDisplayedEdgePaths(PhyloTree network, int treeId) {
		var result = new ArrayList<DisplayedPath>();
		for (var start : network.nodes()) {
			if (!isEssentialNode(network, start, treeId))
				continue;
			for (var firstEdge : start.outEdges()) {
				if (!isAllowedForTree(firstEdge, treeId))
					continue;
				var path = new ArrayList<Edge>();
				path.add(firstEdge);
				var current = firstEdge.getTarget();
				while (!isEssentialNode(network, current, treeId)) {
					var next = getSingleAllowedOutEdge(current, treeId);
					if (next == null)
						break;
					path.add(next);
					current = next.getTarget();
				}
				result.add(new DisplayedPath(start, current, path));
			}
		}
		return result;
	}

	private static boolean isEssentialNode(PhyloTree network, Node v, int treeId) {
		if (v == network.getRoot() || network.hasTaxa(v))
			return true;
		return allowedInDegree(v, treeId) != 1 || allowedOutDegree(v, treeId) != 1;
	}

	private static int allowedInDegree(Node v, int treeId) {
		var count = 0;
		for (var e : v.inEdges())
			if (isAllowedForTree(e, treeId))
				count++;
		return count;
	}

	private static int allowedOutDegree(Node v, int treeId) {
		var count = 0;
		for (var e : v.outEdges())
			if (isAllowedForTree(e, treeId))
				count++;
		return count;
	}

	private static Edge getSingleAllowedOutEdge(Node v, int treeId) {
		Edge result = null;
		for (var e : v.outEdges()) {
			if (isAllowedForTree(e, treeId)) {
				if (result != null)
					return null;
				result = e;
			}
		}
		return result;
	}

	/**
	 * does tree treeId use this network edge? A reticulate edge is used iff it is tagged with treeId; a tree edge is
	 * used iff both its endpoints are on treeId's embedding.
	 */
	private static boolean isAllowedForTree(Edge e, int treeId) {
		if (e.getTarget().getInDegree() > 1)
			return TreeTracing.getTreeIds(e).get(treeId);
		else
			return TreeTracing.getTreeIds(e.getSource()).get(treeId) && TreeTracing.getTreeIds(e.getTarget()).get(treeId);
	}

	private static BitSet taxaReachableBelow(PhyloTree network, Node start, int treeId) {
		var result = new BitSet();
		var visited = new HashSet<Node>();
		var stack = new Stack<Node>();
		stack.push(start);
		visited.add(start);
		while (!stack.isEmpty()) {
			var v = stack.pop();
			if (network.hasTaxa(v)) {
				for (var t : network.getTaxa(v))
					result.set(t);
			}
			for (var e : v.outEdges()) {
				if (isAllowedForTree(e, treeId) && visited.add(e.getTarget()))
					stack.push(e.getTarget());
			}
		}
		return result;
	}

	private static Map<Edge, BitSet> computeEdgeClusters(PhyloTree tree) {
		var result = new HashMap<Edge, BitSet>();
		try (NodeArray<BitSet> taxaBelow = tree.newNodeArray()) {
			tree.postorderTraversal(v -> {
				var set = new BitSet();
				if (tree.hasTaxa(v)) {
					for (var t : tree.getTaxa(v))
						set.set(t);
				}
				for (var e : v.outEdges()) {
					var childSet = taxaBelow.get(e.getTarget());
					if (childSet != null)
						set.or(childSet);
				}
				taxaBelow.put(v, set);
			});
			for (var e : tree.edges())
				result.put(e, (BitSet) taxaBelow.get(e.getTarget()).clone());
		}
		return result;
	}
}

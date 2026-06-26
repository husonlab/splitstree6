package splitstree6.xtra.phyloFusionTreeTrace;

import jloda.graph.Edge;
import jloda.phylo.PhyloTree;
import org.ojalgo.optimisation.Expression;
import org.ojalgo.optimisation.ExpressionsBasedModel;
import org.ojalgo.optimisation.Optimisation;
import org.ojalgo.optimisation.Variable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static splitstree6.xtra.phyloFusionTreeTrace.NetworkEdgesWeightHelpers.edgeString;

public class NetworkEdgeWeightsComputation {

    public record EdgeWeightInfo(double sum, int count) {}
    private static class MutableEdgeWeightInfo {
        double sum = 0.0;
        int count = 0;
        void add(double value) {
            sum += value;
            count++;
        }
        EdgeWeightInfo freeze() {
            return new EdgeWeightInfo(sum, count);
        }
    }

    public static void NNLS(List<PhyloTree> inputTrees, PhyloTree network) {
        var treeEdgeToNetworkEdgeMap = NetworkEdgesWeightHelpers.computeTreeEdgeToNetworkEdgeMap(inputTrees, network);

        var networkEdges = new ArrayList<Edge>();
        for (var e : network.edges()) {
            networkEdges.add(e);
        }

        var networkEdgeIndex = new HashMap<Edge, Integer>();
        for (int i = 0; i < networkEdges.size(); i++) {
            networkEdgeIndex.put(networkEdges.get(i), i);
        }

        var model = new ExpressionsBasedModel();

        var x = new ArrayList<Variable>();
        for (int i = 0; i < networkEdges.size(); i++) {
            x.add(model.addVariable("x_" + i).lower(0.0));
        }

        Expression objective = model.addExpression("sum_squared_residuals").weight(1.0);

        int rowId = 0;

        for (int treeId = 0; treeId < inputTrees.size(); treeId++) {
            var tree = inputTrees.get(treeId);
            var treeMapping = treeEdgeToNetworkEdgeMap.get(treeId);

            for (var treeEdge : tree.edges()) {
                var covered = treeMapping.get(treeEdge);

                if (covered == null || covered.isEmpty()) {
                    System.err.println("WARNING: tree edge not mapped: "
                            + edgeString(tree, treeEdge));
                    continue;
                }

                double treeEdgeWeight = tree.getWeight(treeEdge);

                var residual = model.addVariable("r_" + rowId);

                objective.set(residual, residual, 1.0);

                Expression equation = model.addExpression("eq_" + rowId)
                        .level(treeEdgeWeight);

                for (var networkEdge : covered) {
                    Integer index = networkEdgeIndex.get(networkEdge);
                    if (index != null) {
                        equation.set(x.get(index), 1.0);
                    }
                }

                equation.set(residual, -1.0);

                rowId++;
            }
        }

        Optimisation.Result solution = model.minimise();

        if (!solution.getState().isFeasible()) {
            System.err.println("NNLS failed: " + solution.getState());
            return;
        }

        //System.err.println("NNLS equations: " + rowId);
        //System.err.println("NNLS network edge weights:");

        for (int i = 0; i < networkEdges.size(); i++) {
            var edge = networkEdges.get(i);
            double weight = x.get(i).getValue().doubleValue();

            if (Math.abs(weight) < 1e-10) {
                weight = 0.0;
            }

            network.setWeight(edge, weight);

//            System.err.printf(
//                    "  network edge %s weight=%.6f%n",
//                    edgeString(network, edge),
//                    weight
//            );
        }
    }

    public static void LP(List<PhyloTree> inputTrees, PhyloTree network) {
        var treeEdgeToNetworkEdgeMap = NetworkEdgesWeightHelpers.computeTreeEdgeToNetworkEdgeMap(inputTrees, network);

        var networkEdges = new ArrayList<Edge>();
        for (var e : network.edges()) {
            networkEdges.add(e);
        }

        var networkEdgeIndex = new HashMap<Edge, Integer>();
        for (int i = 0; i < networkEdges.size(); i++) {
            networkEdgeIndex.put(networkEdges.get(i), i);
        }

        var model = new ExpressionsBasedModel();

        var x = new ArrayList<Variable>();
        for (int i = 0; i < networkEdges.size(); i++) {
            var variable = model.addVariable("x_" + i).lower(0.0);
            x.add(variable);
        }

        int constraintId = 0;

        for (int treeId = 0; treeId < inputTrees.size(); treeId++) {
            var tree = inputTrees.get(treeId);
            var treeMapping = treeEdgeToNetworkEdgeMap.get(treeId);

            for (var treeEdge : tree.edges()) {
                var covered = treeMapping.get(treeEdge);

                if (covered == null || covered.isEmpty()) {
                    continue;
                }

                double treeEdgeWeight = tree.getWeight(treeEdge);

                var errPlus = model.addVariable("p_" + constraintId).lower(0.0).weight(1.0);
                var errMinus = model.addVariable("m_" + constraintId).lower(0.0).weight(1.0);

                Expression constraint = model.addExpression("tree_edge_" + constraintId)
                        .level(treeEdgeWeight);

                for (var networkEdge : covered) {
                    var index = networkEdgeIndex.get(networkEdge);
                    if (index != null) {
                        constraint.set(x.get(index), 1.0);
                    }
                }

                constraint.set(errMinus, 1.0);
                constraint.set(errPlus, -1.0);

                constraintId++;
            }
        }

        Optimisation.Result solution = model.minimise();

        if (!solution.getState().isFeasible()) {
            System.err.println("LP failed: " + solution.getState());
            return;
        }

        //System.err.println("LP network edge weights:");

        for (int i = 0; i < networkEdges.size(); i++) {
            var edge = networkEdges.get(i);
            double weight = x.get(i).getValue().doubleValue();

            network.setWeight(edge, weight);

//            System.err.printf(
//                    "  network edge %s weight=%.6f%n",
//                    edgeString(network, edge),
//                    weight
//            );
        }
    }

    public static void mean(List<PhyloTree> inputTrees, PhyloTree network)
    {
        var result = computeSumAndCount(inputTrees, network);

        //System.err.println("MEAN network edge weights:");

        for (var f : network.edges()) {
            var info = result.get(f);

            double weight;
            if (info == null || info.count() == 0) {
                weight = 0.0;
            } else {
                weight = info.sum() / info.count();
            }

            network.setWeight(f, weight);

//            System.err.printf(
//                    "  network edge %s weight=%.6f%n",
//                    edgeString(network, f),
//                    weight
//            );
        }

    }

    public static Map<Edge, EdgeWeightInfo> computeSumAndCount(List<PhyloTree> inputTrees, PhyloTree network) {
        var treeEdgeToNetworkEdgesMap = NetworkEdgesWeightHelpers.computeTreeEdgeToNetworkEdgeMap(inputTrees, network);

        var weights = new HashMap<Edge, MutableEdgeWeightInfo>();

        for (var f : network.edges()) {
            weights.put(f, new MutableEdgeWeightInfo());
        }

        for (int treeId = 0; treeId < inputTrees.size(); treeId++) {
            var tree = inputTrees.get(treeId);
            var treeMapping = treeEdgeToNetworkEdgesMap.get(treeId);

            for (var e : tree.edges()) {
                var covered = treeMapping.get(e);

                if (covered == null || covered.isEmpty())
                    continue;

                var contribution = tree.getWeight(e) / covered.size();

                for (var f : covered) {
                    weights.get(f).add(contribution);
                }
            }
        }

        var result = new HashMap<Edge, EdgeWeightInfo>();

        //System.err.println("Network edge weight sums and counts:");

        for (var f : network.edges()) {
            var info = weights.get(f).freeze();
            result.put(f, info);

            //System.err.println("  network edge " + edgeString(network, f) + " sum=" + info.sum() + " count=" + info.count());

        }

        return result;
    }

    public static void LPReticulatesZero(List<PhyloTree> inputTrees, PhyloTree network) {
        var treeEdgeToNetworkEdgeMap = NetworkEdgesWeightHelpers.computeTreeEdgeToNetworkEdgeMap(inputTrees, network);

        var networkEdges = new ArrayList<Edge>();
        for (var e : network.edges()) {
            networkEdges.add(e);
        }

        var model = new ExpressionsBasedModel();

        var x = new HashMap<Edge, Variable>();

        int edgeId = 0;
        for (var f : networkEdges) {
            Variable variable;

            if (f.getTarget().getInDegree() > 1) {
                variable = model.addVariable("x_" + edgeId).level(0.0);
            } else {
                variable = model.addVariable("x_" + edgeId).lower(0.0);
            }

            x.put(f, variable);
            edgeId++;
        }

        int constraintId = 0;

        for (int treeId = 0; treeId < inputTrees.size(); treeId++) {
            var tree = inputTrees.get(treeId);
            var treeMapping = treeEdgeToNetworkEdgeMap.get(treeId);

            for (var treeEdge : tree.edges()) {
                var covered = treeMapping.get(treeEdge);
                if (covered == null || covered.isEmpty())
                    continue;

                double treeEdgeWeight = tree.getWeight(treeEdge);

                var errPlus = model.addVariable("p_" + constraintId).lower(0.0).weight(1.0);
                var errMinus = model.addVariable("m_" + constraintId).lower(0.0).weight(1.0);

                Expression constraint = model.addExpression("tree_edge_" + constraintId)
                        .level(treeEdgeWeight);

                for (var networkEdge : covered) {
                    var variable = x.get(networkEdge);
                    if (variable != null) {
                        constraint.set(variable, 1.0);
                    }
                }

                constraint.set(errMinus, 1.0);
                constraint.set(errPlus, -1.0);

                constraintId++;
            }
        }

        Optimisation.Result solution = model.minimise();

        if (!solution.getState().isFeasible()) {
            System.err.println("LP edge-weight calculation failed: " + solution.getState());
            return;
        }

        //System.err.println("LP network edge weights:");

        for (var f : networkEdges) {
            double weight = x.get(f).getValue().doubleValue();

            if (Math.abs(weight) < 1e-10)
                weight = 0.0;

            network.setWeight(f, weight);

//            System.err.printf(
//                    "  network edge %s weight=%.6f%n",
//                    edgeString(network, f),
//                    weight
//            );
        }
    }
}

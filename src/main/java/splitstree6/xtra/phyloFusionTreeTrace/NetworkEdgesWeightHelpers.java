package splitstree6.xtra.phyloFusionTreeTrace;

import jloda.graph.Edge;
import jloda.graph.Node;
import jloda.graph.NodeArray;
import jloda.phylo.PhyloTree;

import java.util.*;

public class NetworkEdgesWeightHelpers {

    public static Map<Integer, Map<Edge, ArrayList<Edge>>> computeTreeEdgeToNetworkEdgeMap(List<PhyloTree> inputTrees, PhyloTree network) {
        var result = new HashMap<Integer, Map<Edge, ArrayList<Edge>>>();

        for (int treeId = 0; treeId < inputTrees.size(); treeId++) {
            var tree = inputTrees.get(treeId);

            var displayedPaths = extractDisplayedEdgePaths(network, treeId);
            var displayedClusterToPath = new HashMap<BitSet, ArrayList<Edge>>();

            for (var path : displayedPaths) {
                var cluster = taxaReachableBelow(network, path.target(), treeId);
                if (!cluster.isEmpty())
                    displayedClusterToPath.put(cluster, path.networkEdges());
            }

            var treeEdgeToNetworkEdges = new HashMap<Edge, ArrayList<Edge>>();
            var treeEdgeClusters = computeEdgeClusters(tree);

            //System.err.println("Tree " + (treeId + 1));

            for (var e : tree.edges()) {
                var cluster = treeEdgeClusters.get(e);
                var covered = findByCluster(displayedClusterToPath, cluster);

                if (covered == null)
                    covered = new ArrayList<>();

                treeEdgeToNetworkEdges.put(e, covered);

                /**System.err.println("  tree edge " + edgeString(tree, e)
                        + " cluster=" + cluster
                        + " covers network edges: " + networkEdgeListString(network, covered));**/
            }

            result.put(treeId, treeEdgeToNetworkEdges);
        }

        return result;
    }

    private record DisplayedPath(Node source, Node target, ArrayList<Edge> networkEdges) {
    }

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
        if (v == network.getRoot())
            return true;

        if (network.hasTaxa(v))
            return true;

        return allowedInDegree(v, treeId) != 1 || allowedOutDegree(v, treeId) != 1;
    }

    private static int allowedInDegree(Node v, int treeId) {
        var count = 0;
        for (var e : v.inEdges()) {
            if (isAllowedForTree(e, treeId))
                count++;
        }
        return count;
    }

    private static int allowedOutDegree(Node v, int treeId) {
        var count = 0;
        for (var e : v.outEdges()) {
            if (isAllowedForTree(e, treeId))
                count++;
        }
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

    private static boolean isAllowedForTree(Edge e, int treeId) {
        var sourceIds = getNodeTreeIds(e.getSource());
        var targetIds = getNodeTreeIds(e.getTarget());

        if (e.getTarget().getInDegree() > 1) {
            return getEdgeTreeIds(e).get(treeId);
        } else {
            return sourceIds.get(treeId) && targetIds.get(treeId);
        }
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
                if (!isAllowedForTree(e, treeId))
                    continue;

                var w = e.getTarget();
                if (visited.add(w))
                    stack.push(w);
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

            for (var e : tree.edges()) {
                result.put(e, (BitSet) taxaBelow.get(e.getTarget()).clone());
            }
        }

        return result;
    }

    private static ArrayList<Edge> findByCluster(Map<BitSet, ArrayList<Edge>> clusterToPath, BitSet query) {
        for (var entry : clusterToPath.entrySet()) {
            if (entry.getKey().equals(query))
                return entry.getValue();
        }
        return null;
    }

    private static BitSet getNodeTreeIds(Node v) {
        return v.getInfo() instanceof BitSet bs ? (BitSet) bs.clone() : new BitSet();
    }

    private static BitSet getEdgeTreeIds(Edge e) {
        return e.getInfo() instanceof BitSet bs ? (BitSet) bs.clone() : new BitSet();
    }

    static String edgeString(PhyloTree tree, Edge e) {
        return e.getId() + " (" + nodeString(tree, e.getSource()) + " -> " + nodeString(tree, e.getTarget()) + ")";
    }

    private static String nodeString(PhyloTree tree, Node v) {
        if (tree.hasTaxa(v))
            return tree.getLabel(v) != null ? tree.getLabel(v) : "taxon=" + tree.getTaxon(v);
        else
            return "v" + v.getId();
    }

    private static String networkEdgeListString(PhyloTree network, ArrayList<Edge> edges) {
        var parts = new ArrayList<String>();
        for (var e : edges) {
            parts.add(e.getId() + " (" + nodeString(network, e.getSource()) + " -> " + nodeString(network, e.getTarget()) + ")");
        }
        return parts.toString();
    }

    public static void printFitStatistics(PhyloFusionTreeTrace.EdgeWeightMethod method, List<PhyloTree> inputTrees, PhyloTree network) {
        var map = NetworkEdgesWeightHelpers.computeTreeEdgeToNetworkEdgeMap(inputTrees, network);

        double sse = 0.0;
        double sae = 0.0;
        double maxAbs = 0.0;
        int count = 0;

        for (int treeId = 0; treeId < inputTrees.size(); treeId++) {
            var tree = inputTrees.get(treeId);
            var treeMapping = map.get(treeId);

            for (var treeEdge : tree.edges()) {
                var covered = treeMapping.get(treeEdge);
                if (covered == null || covered.isEmpty())
                    continue;

                double observed = tree.getWeight(treeEdge);

                double predicted = 0.0;
                for (var networkEdge : covered)
                    predicted += network.getWeight(networkEdge);

                double residual = predicted - observed;

                sse += residual * residual;
                sae += Math.abs(residual);
                maxAbs = Math.max(maxAbs, Math.abs(residual));
                count++;
            }
        }

        System.err.printf(
                "%s fit: equations=%d SSE=%.8f RMSE=%.8f MAE=%.8f MaxAbs=%.8f%n",
                method,
                count,
                sse,
                Math.sqrt(sse / count),
                sae / count,
                maxAbs
        );
    }
}
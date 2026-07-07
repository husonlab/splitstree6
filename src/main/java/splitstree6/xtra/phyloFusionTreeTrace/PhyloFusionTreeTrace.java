package splitstree6.xtra.phyloFusionTreeTrace;

import jloda.graph.Edge;
import jloda.graph.Graph;
import jloda.graph.Node;
import jloda.graph.NodeArray;
import jloda.phylo.CommentData;
import jloda.phylo.NewickIO;
import jloda.phylo.PhyloTree;
import jloda.phylo.algorithms.ClusterPoppingAlgorithm;
import jloda.util.BitSetUtils;
import jloda.util.IteratorUtils;
import jloda.util.StringUtils;
import jloda.util.progress.ProgressListener;
import splitstree6.algorithms.trees.trees2trees.PhyloFusion;
import splitstree6.algorithms.utils.TreeMutualRefinement;
import splitstree6.compute.phylofusion.NetworkUtils;
import splitstree6.data.TaxaBlock;
import splitstree6.data.TreesBlock;
import splitstree6.splits.GraphUtils;
import splitstree6.utils.ClusterUtils;
import splitstree6.utils.MaxCliqueUtilities;
import splitstree6.utils.PathMultiplicityDistance;
import splitstree6.utils.TreesUtils;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * recursive version of the PhyloFusion algorithm
 * same recursive logic as original PhyloFusion,
 * but uses PhyloFusionAlgorithmTreeTrace for the SCS-based core step
 */
public class PhyloFusionTreeTrace extends PhyloFusion {
    /**
     * one active tree/network plus the original input tree ids that it represents
     */
    private record TraceTree(PhyloTree tree, BitSet representedTreeIds) {
    }

    public record DataItem(PhyloTree tree, BitSet taxa, Set<BitSet> clusters) {
        public DataItem(PhyloTree tree) {
            this(new PhyloTree(tree), BitSetUtils.asBitSet(tree.getTaxa()), TreesUtils.collectAllHardwiredClusters(tree));
        }
    }

    public enum EdgeWeightMethod {
        AVERAGE,
        LP,
        LP_RETICULATES_ZERO,
        NNLS
    }

    private EdgeWeightMethod edgeWeightMethod = EdgeWeightMethod.AVERAGE;

    public void setEdgeWeightMethod(EdgeWeightMethod edgeWeightMethod) {
        this.edgeWeightMethod = edgeWeightMethod;
    }

    private boolean trace = false;
    public boolean isTrace() {return trace;}
    public void setTrace(boolean trace) {this.trace = trace;}


    @Override
    public void compute(ProgressListener progress, TaxaBlock taxaBlock, TreesBlock treesBlock, TreesBlock outputBlock) throws IOException {
        progress.setTasks("PhyloFusionTreeTrace", "init");

        var inputTrees = new ArrayList<TraceTree>();
        var taxonToTreeIds = new HashMap<Integer, BitSet>();

        for (int i = 0; i < treesBlock.getTrees().size(); i++) {
            var tree = new PhyloTree(treesBlock.getTrees().get(i));

            var ids = new BitSet();
            ids.set(i); // internal zero-based original tree id
            inputTrees.add(new TraceTree(tree, ids));

            for (var taxon : tree.getTaxa()) {
                taxonToTreeIds.computeIfAbsent(taxon, k -> new BitSet()).set(i);
            }
        }
        var start = System.currentTimeMillis();
        var result = computeRec(progress, isOptionMutualRefinement(), inputTrees);

        var hybridizationNumber = result.get(0).tree().nodeStream().filter(v -> v.getInDegree() > 1).mapToInt(v -> v.getInDegree() - 1).sum();
        System.err.println("Hybridization number: " + hybridizationNumber);

        if (false)
            System.err.println("Time: " + ((System.currentTimeMillis() - start) / 1000) + "s");

        outputBlock.setPartial(false);
        outputBlock.setRooted(true);

        if (isOptionOnlyOneNetwork() && result.size() > 1) {
            var one = result.get(0);
            result.clear();
            result.add(one);
        }

        var count = 0;
        for (var traced : result) {
            var network = traced.tree();

            // postprocessing: assign leaves from original input trees, then propagate upward
            completeMissingNodeTreeIds(network, traced.representedTreeIds(), taxonToTreeIds);

            for (var e : network.edges()) {
                network.setReticulate(e, e.getTarget().getInDegree() > 1);
            }

            network.setName("N" + (++count));
            TreesUtils.addLabels(network, taxaBlock::getLabel);
            outputBlock.getTrees().add(network);

            if (!outputBlock.isReticulated() && network.nodeStream().anyMatch(v -> v.getInDegree() > 1)) {
                outputBlock.setReticulated(true);
            }

            NetworkUtils.check(network);

            for (var t = 1; t <= treesBlock.getNTrees(); t++) {
                var tree = treesBlock.getTree(t);
                if (!PathMultiplicityDistance.contains(taxaBlock.getTaxaSet(), network, tree)) {
                    System.err.println("Warning: Network might not contain tree: " + t);
                }
            }

            for (var v : network.nodeStream().filter(v -> v.getInDegree() == 1 && v.getOutDegree() == 1).toList()) {
                network.delDivertex(v);
            }

            /**if (count <= 10 && isOptionCalculateWeights()) {
                NetworkUtils.setEdgeWeights(treesBlock.getTrees(), network, isOptionNormalizeEdgeWeights(), 3000);
            }**/
            if (network.getRoot().getOutDegree() == 1)
                network.setWeight(network.getRoot().getFirstOutEdge(), 0.000001);

            switch (edgeWeightMethod) {
                case AVERAGE ->
                    NetworkEdgeWeightsComputation.mean(treesBlock.getTrees(), network);
                case LP ->
                        NetworkEdgeWeightsComputation.LP(treesBlock.getTrees(), network);
                case NNLS ->
                        NetworkEdgeWeightsComputation.NNLS(treesBlock.getTrees(), network);
                case LP_RETICULATES_ZERO ->
                        NetworkEdgeWeightsComputation.LPReticulatesZero(treesBlock.getTrees(), network);
            }

            NetworkEdgesWeightHelpers.printFitStatistics(edgeWeightMethod, treesBlock.getTrees(), network);

            //debugPrintNetworkTrace(network);
            if (isTrace()) {
                System.err.println(toExtendedNewickWithTT(network));
            } else {
                for (var v : network.nodes()) {
                    v.setData(null);
                }
                for (var e : network.edges()) {
                    e.setData(null);
                }
                System.err.println(network.toBracketString() + ";");
            }
            break;
        }
    }

    private void debugPrintNetworkTrace(PhyloTree network) {
        for (var v : network.nodes()) {
            System.err.println("node " + v.getId() + " taxa= " + v.getLabel() + " treeIds= " + bitSetToTreeString(getNodeTreeIds(v)));
            if (v.getParent() != null){
                System.err.println(v.getParent().getId() + " --> " + v.getId());
            }
        }
        for (var e : network.edges()) {
            if (e.getTarget().getInDegree() > 1) {
                System.err.println("reticulate edge " + e.getId()
                        + " (" + e.getSource().getId() + "->" + e.getTarget().getId() + ")"
                        + " treeIds=" + bitSetToTreeString(getEdgeTreeIds(e)));
            }
        }
        System.err.println();
    }

    private static BitSet toOneBased(BitSet zeroBased) {
        var result = new BitSet();
        for (int i = zeroBased.nextSetBit(0); i >= 0; i = zeroBased.nextSetBit(i + 1)) {
            result.set(i + 1);
        }
        return result;
    }

    private static void transferTraceInfoToCommentData(PhyloTree network) {
        for (var v : network.nodes()) {
            var ids = getNodeTreeIds(v);
            if (!ids.isEmpty()) {
                v.setData(new CommentData().put("TT", toOneBased(ids)));
            } else {
                v.setData(null);
            }
        }

        for (var e : network.edges()) {
            if (e.getTarget().getInDegree() > 1) {
                var ids = getEdgeTreeIds(e);
                if (!ids.isEmpty()) {
                    e.setData(new CommentData().put("TT", toOneBased(ids)));
                } else {
                    e.setData(null);
                }
            } else {
                e.setData(null); // ordinary tree edges must not get TT comments
            }
        }
    }

    private static String toExtendedNewickWithTT(PhyloTree network) throws IOException {
        //PhyloTree.SUPPORT_RICH_NEWICK = true;

        transferTraceInfoToCommentData(network);

        var newickIO = new NewickIO();
        newickIO.allowMultiLabeledNodes = false;
        newickIO.setNewickNodeCommentSupplier(CommentData.createDataNodeSupplier());
        newickIO.setNewickEdgeCommentSupplier(CommentData.createDataEdgeSupplier());

        return newickIO.toBracketString(network, true) + ";";
    }

    /**
     * recursively compute the networks
     */
    private List<TraceTree> computeRec(ProgressListener progress, boolean mutualRefinement, List<TraceTree> tracedTrees) throws IOException {
        removeContainedAndRefine(tracedTrees, mutualRefinement);

        if (tracedTrees.size() == 1) {
            if (verbose)
                System.err.println("Single tree");
            var tree = tracedTrees.get(0).tree();
            if (checkAllPartialResults) {
                NetworkUtils.check(tree);
            }
            return List.of(tracedTrees.get(0));
        }

        final var taxa = new BitSet();
        for (var traced : tracedTrees) {
            taxa.or(BitSetUtils.asBitSet(traced.tree().getTaxa()));
        }

        var taxLabelMap = new HashMap<Integer, String>();
        tracedTrees.forEach(traced -> traced.tree().nodeStream().filter(Node::isLeaf).forEach(v -> taxLabelMap.putIfAbsent(traced.tree().getTaxon(v), traced.tree().getLabel(v))));

        if (verbose)
            System.err.println("computeRec----");

        var plainTrees = new ArrayList<>(tracedTrees.stream().map(TraceTree::tree).toList());

        var repGroupMap = new HashMap<Integer, BitSet>();
        if (getOptionGroupNonSeparated()) {
            repGroupMap.putAll(groupNonSeparatedTaxa(taxa, plainTrees, taxLabelMap));
        }

        // after grouping, keep same represented ids, only topology changed
        tracedTrees = rebuildTraceTrees(plainTrees, tracedTrees);
        Graph incompatibityGraph;
        var clusters = new ArrayList<BitSet>();
        var clusterIGMap = new HashMap<BitSet, Node>();
        {
            if (checkAllPartialResults) {
                for (var traced : tracedTrees)
                    NetworkUtils.check(traced.tree());
            }
            {
                var clusterSet = new HashSet<BitSet>();
                for (var traced : tracedTrees) {
                    clusterSet.addAll(TreesUtils.collectAllHardwiredClusters(traced.tree()));
                }
                clusters.addAll(clusterSet);
                clusters.sort((a, b) -> -Integer.compare(a.cardinality(), b.cardinality()));
            }
            incompatibityGraph = new Graph();
            for (var cluster : clusters) {
                clusterIGMap.put(cluster, incompatibityGraph.newNode(cluster));
            }
            var nodes = IteratorUtils.asList(incompatibityGraph.nodes());
            for (var i = 0; i < nodes.size(); i++) {
                var a = nodes.get(i);
                for (var j = i + 1; j < nodes.size(); j++) {
                    var b = nodes.get(j);
                    if (!ClusterUtils.compatible((BitSet) a.getInfo(), (BitSet) b.getInfo()))
                        incompatibityGraph.newEdge(a, b);
                }
            }
        }

        if (incompatibityGraph.nodeStream().noneMatch(v -> v.getDegree() > 0)) {
            var tree = new PhyloTree();
            ClusterPoppingAlgorithm.apply(clusters, tree);
            var list = restoreGroupedTaxa(repGroupMap, taxLabelMap, List.of(tree));

            var representedIds = unionRepresentedTreeIds(tracedTrees);
            var result = new ArrayList<TraceTree>();
            for (var t : list) {
                result.add(new TraceTree(t, (BitSet) representedIds.clone()));
            }
            return result;
        }

        BitSet separator = null;
        for (int i = 0; i < clusters.size(); i++) {
            var cluster = clusters.get(i);
            if (!cluster.equals(taxa)) {
                if (cluster.cardinality() == 1) {
                    break;
                } else if (clusterIGMap.get(cluster).getDegree() == 0) {
                    var ok = true;
                    if (!isOptionCladeReduction()) {
                        for (int j = i + 1; j < clusters.size(); j++) {
                            var other = clusters.get(j);
                            if (clusters.contains(other) && clusterIGMap.get(other).getDegree() != 0) {
                                ok = false;
                                break;
                            }
                        }
                    }
                    if (ok) {
                        separator = cluster;
                        break;
                    }
                }
            }
        }
        if (separator == null) {
            if (tracedTrees.size() == 1) {
                if (verbose)
                    System.err.println("Single tree");

                var restored = restoreGroupedTaxa(repGroupMap, taxLabelMap, List.of(new PhyloTree(tracedTrees.get(0).tree())));
                var representedIds = unionRepresentedTreeIds(tracedTrees);

                var result = new ArrayList<TraceTree>();
                for (var t : restored) {
                    result.add(new TraceTree(t, (BitSet) representedIds.clone()));
                }
                return result;
            } else {
                if (verbose)
                    System.err.println("Running on " + taxa.cardinality() + " taxa");

                //for (var traced : tracedTrees) {System.err.println("tree: " + traced.tree().toBracketString(false));}
                var coreInputTrees = new ArrayList<PhyloTree>();
                for (var traced : tracedTrees) {
                    coreInputTrees.add(copyTreeTopologyOnly(traced.tree()));
                }

                var resultList = PhyloFusionAlgorithmTreeTrace.apply(coreInputTrees, isOptionOnlyOneNetwork(),
                        isOptionRefinementHeuristic(), isOptionMissingTaxaHeuristic(), progress);

                // map local active-tree ids used by PhyloFusionAlgorithmTreeTrace back to original ids
                for (var network : resultList) {
                    expandActiveToOriginalTreeIds(network, tracedTrees);
                }

                restoreGroupedTaxa(repGroupMap, taxLabelMap, resultList);

                if (checkAllPartialResults) {
                    for (var network : resultList) {
                        if (!NetworkUtils.check(network)) {
                            for (var traced : tracedTrees) {
                                System.err.println("tree was: " + traced.tree().toBracketString(false));
                            }
                        }
                    }
                }

                var representedIds = unionRepresentedTreeIds(tracedTrees);
                var tracedResultList = new ArrayList<TraceTree>();
                for (var t : resultList) {
                    tracedResultList.add(new TraceTree(t, (BitSet) representedIds.clone()));
                }
                return tracedResultList;
            }
        } else {
            var rep = BitSetUtils.min(separator);

            var networksBelow = computeRec(progress, isOptionMutualRefinement(), computeTreesBelow(tracedTrees, taxLabelMap, separator));
            if (checkAllPartialResults) {
                for (var network : networksBelow) {
                    NetworkUtils.check(network.tree());
                }
            }

            var networksAbove = computeRec(progress, isOptionMutualRefinement(), computeTreesAbove(tracedTrees, taxLabelMap, separator, rep));

            if (checkAllPartialResults) {
                for (var networkA : networksAbove) {
                    for (var v : networkA.tree().nodes()) {
                        if (networkA.tree().hasTaxa(v))
                            networkA.tree().setLabel(v, taxLabelMap.get(networkA.tree().getTaxon(v)));
                    }
                    System.err.println("Network above: " + networkA.tree().toBracketString(false));
                    NetworkUtils.check(networkA.tree());

                    if (!IteratorUtils.asSet(networkA.tree().getTaxa()).contains(rep))
                        System.err.println("Network don't contain rep=" + rep);
                }
            }

            var resultList = new ArrayList<TraceTree>();
            for (var networkAbove : networksAbove) {
                if (checkAllPartialResults) {
                    NetworkUtils.check(networkAbove.tree());
                }
                for (var networkBelow : networksBelow) {
                    if (checkAllPartialResults)
                        NetworkUtils.check(networkBelow.tree());

                    var networkMerged = copyTreePreservingInfo(networkAbove.tree());
                    final var v = networkMerged.nodeStream().filter(w -> networkMerged.getTaxon(w) == rep).findAny().orElse(null);

                    if (v == null) {
                        System.err.println("rep: " + rep + " " + taxLabelMap.get(rep));
                        System.err.println("Not found in: " + networkAbove.tree().toBracketString(false));
                        System.err.println("Not found in: " + StringUtils.toString(BitSetUtils.asBitSet(networkAbove.tree().getTaxa())));
                    }
                    if (v == null) {
                        throw new RuntimeException("Internal error, rep not found");
                    }

                    networkMerged.clearTaxa(v);
                    if (verbose) {
                        System.err.println("MERGING:");
                        networkAbove.tree().nodeStream().filter(networkAbove.tree()::hasTaxa).forEach(w -> networkAbove.tree().setLabel(w, "t" + networkAbove.tree().getTaxon(w)));
                        System.err.println("Above " + (resultList.size() + 1) + ": " + networkAbove.tree().toBracketString(false) + ";");
                        {
                            var reached = new HashSet<Node>();
                            networkAbove.tree().postorderTraversal(reached::add);
                            System.err.println("networkAbove: nodes=" + networkAbove.tree().getNumberOfNodes() + ", reachable: " + reached.size());
                        }

                        networkBelow.tree().nodeStream().filter(networkBelow.tree()::hasTaxa).forEach(w -> networkBelow.tree().setLabel(w, "t" + networkBelow.tree().getTaxon(w)));
                        System.err.println("Below " + (resultList.size() + 1) + ": " + networkBelow.tree().toBracketString(false) + ";");
                        {
                            var reached = new HashSet<Node>();
                            networkBelow.tree().postorderTraversal(reached::add);
                            System.err.println("networkBelow: nodes=" + networkBelow.tree().getNumberOfNodes() + ", reachable: " + reached.size());
                        }
                    }
                    networkMerged.clearTaxa(v);
                    networkMerged.setLabel(v, null);
                    copySubNetwork(networkBelow.tree().getRoot(), v);

                    if (verbose) {
                        networkMerged.nodeStream().filter(networkMerged::hasTaxa).forEach(w -> networkMerged.setLabel(w, "t" + networkMerged.getTaxon(w)));
                        for (var e : networkMerged.edges()) {
                            networkMerged.setReticulate(e, e.getTarget().getInDegree() > 1);
                        }
                        System.err.println("Merged: " + networkMerged.toBracketString(false) + ";");
                        {
                            var reached = new HashSet<Node>();
                            networkMerged.postorderTraversal(reached::add);
                            System.err.println("networkMerged: nodes=" + networkMerged.getNumberOfNodes() + ", reachable: " + reached.size());
                        }
                    }

                    if (checkAllPartialResults)
                        NetworkUtils.check(networkMerged);

                    var mergedIds = union(networkAbove.representedTreeIds(), networkBelow.representedTreeIds());
                    resultList.add(new TraceTree(networkMerged, mergedIds));
                    if (isOptionOnlyOneNetwork())
                        break;
                }
            }

            var restored = restoreGroupedTaxa(repGroupMap, taxLabelMap, resultList.stream().map(TraceTree::tree).collect(Collectors.toCollection(ArrayList::new)));

            var finalList = new ArrayList<TraceTree>();
            for (int i = 0; i < restored.size(); i++) {
                finalList.add(new TraceTree(restored.get(i), resultList.get(i).representedTreeIds()));
            }

            if (checkAllPartialResults) {
                for (var network : finalList) {
                    NetworkUtils.check(network.tree());
                }
            }

            return finalList;
        }
    }

    private static PhyloTree copyTreeTopologyOnly(PhyloTree source) {
        var target = new PhyloTree();

        try (NodeArray<Node> old2new = source.newNodeArray()) {
            for (var v : source.nodes()) {
                var w = target.newNode();
                old2new.put(v, w);

                for (var t : source.getTaxa(v)) {
                    target.addTaxon(w, t);
                }

                var label = source.getLabel(v);
                if (label != null) {
                    target.setLabel(w, label);
                }

                if (v == source.getRoot()) {
                    target.setRoot(w);
                }
            }

            for (var e : source.edges()) {
                var a = old2new.get(e.getSource());
                var b = old2new.get(e.getTarget());
                if (a != null && b != null) {
                    var f = target.newEdge(a, b);
                    target.setWeight(f, source.getWeight(e));
                }
            }
        }

        return target;
    }

    /**
     * remove contained trees
     */
    private void removeContainedAndRefine(List<TraceTree> trees, boolean refine) {
        if (refine) {
            var refinedPlain = TreeMutualRefinement.apply(trees.stream().map(t -> new PhyloTree(t.tree())).toList());
            var refined = new ArrayList<TraceTree>();
            for (int i = 0; i < refinedPlain.size(); i++) {
                var ids = i < trees.size() ? (BitSet) trees.get(i).representedTreeIds().clone() : new BitSet();
                refined.add(new TraceTree(refinedPlain.get(i), ids));
            }
            trees.clear();
            trees.addAll(refined);
        }

        var dataList = new ArrayList<>(trees.stream().map(t -> new AbstractMap.SimpleEntry<>(t, new DataItem(t.tree())))
                .sorted(Comparator.comparingInt(a -> a.getValue().taxa().cardinality())).toList());
        trees.clear();

        var keep = new BitSet();

        for (var i = 0; i < dataList.size(); i++) {
            var iTaxa = dataList.get(i).getValue().taxa();
            var iClusters = dataList.get(i).getValue().clusters();

            var ok = true;
            for (var j = i + 1; ok && j < dataList.size(); j++) {
                var jTaxa = dataList.get(j).getValue().taxa();
                var jClusters = dataList.get(j).getValue().clusters();
                if (BitSetUtils.contains(jTaxa, iTaxa)) {
                    if (iTaxa.cardinality() == jTaxa.cardinality()) {
                        if (jClusters.containsAll(iClusters)) {
                            ok = false;
                            dataList.get(j).getKey().representedTreeIds().or(dataList.get(i).getKey().representedTreeIds());
                        }
                    } else {
                        var jInduced = jClusters.stream().map(s -> BitSetUtils.intersection(s, iTaxa)).filter(s -> s.cardinality() > 0).collect(Collectors.toSet());
                        if (jInduced.containsAll(iClusters)) {
                            ok = false;
                            dataList.get(j).getKey().representedTreeIds().or(dataList.get(i).getKey().representedTreeIds());
                        }
                    }
                }
            }
            if (ok)
                keep.set(i);
        }
        trees.addAll(BitSetUtils.asList(keep).stream().map(i -> dataList.get(i).getKey()).toList());
    }

    /**
     * original grouping logic, unchanged
     */
    private Map<Integer, BitSet> groupNonSeparatedTaxa(final BitSet taxa, final List<PhyloTree> trees, final Map<Integer, String> taxLabelMap) {
        final var repOthersMap = new HashMap<Integer, BitSet>();
        var graph = new Graph();
        var taxonNodeMap = new TreeMap<Integer, Node>();
        for (var t : BitSetUtils.members(taxa)) {
            var v = graph.newNode();
            taxonNodeMap.put(t, v);
            v.setInfo(t);
        }

        for (var tree : trees) {
            if (verbose)
                System.err.println("Top Tree: " + tree.toBracketString(false));

            var leaves = tree.nodeStream().filter(Node::isLeaf).toList();
            for (var la : leaves) {
                var ta = tree.getTaxon(la);
                var v = taxonNodeMap.get(ta);
                for (var lb : leaves) {
                    if (la.getParent() != lb.getParent()) {
                        var tb = tree.getTaxon(lb);
                        var w = taxonNodeMap.get(tb);
                        if (!v.isAdjacent(w)) {
                            graph.newEdge(v, w);
                            if (verbose && false)
                                System.err.println("Separated: " + taxLabelMap.get(ta) + " " + taxLabelMap.get(tb));
                        }
                    }
                }
            }
        }
        GraphUtils.convertToComplement(graph);

        var treeTaxaSets = trees.stream().map(tree -> BitSetUtils.asBitSet(tree.getTaxa())).toList();

        for (var e : IteratorUtils.asList(graph.edges())) {
            var s = (Integer) e.getSource().getInfo();
            var t = (Integer) e.getTarget().getInfo();
            if (treeTaxaSets.stream().noneMatch(set -> set.get(s) && set.get(t)) ||
                    treeTaxaSets.stream().filter(set -> set.get(s)).count() == 1 || treeTaxaSets.stream().filter(set -> set.get(t)).count() == 1)
                graph.deleteEdge(e);
        }

        MaxCliqueUtilities.greedilyReduceToDisjointMaxCliques(graph);

        if (verbose) {
            var nodes = IteratorUtils.asList(graph.nodes());
            for (var i = 0; i < nodes.size(); i++) {
                var v = nodes.get(i);
                var ta = (Integer) v.getInfo();
                for (var j = i + 1; j < nodes.size(); j++) {
                    var w = nodes.get(j);
                    var tb = (Integer) w.getInfo();
                    if (v.isAdjacent(w)) {
                        System.err.println("Not separated: " + taxLabelMap.get(ta) + " and " + taxLabelMap.get(tb));
                    }
                }
            }
        }

        try (var visited = graph.newNodeSet()) {
            for (var v : graph.nodes()) {
                if (!visited.contains(v)) {
                    var component = new TreeSet<Integer>();
                    var stack = new Stack<Node>();
                    stack.push(v);
                    visited.add(v);
                    while (!stack.isEmpty()) {
                        v = stack.pop();
                        component.add((Integer) v.getInfo());
                        for (var w : v.adjacentNodes()) {
                            if (!visited.contains(w)) {
                                stack.push(w);
                                visited.add(v);
                            }
                        }
                    }

                    if (component.size() > 1) {
                        var bestRep = 0;
                        var bestOthers = new BitSet();
                        for (var rep : component) {
                            var others = new BitSet();
                            for (var taxon : component) {
                                if (!taxon.equals(rep) && treeTaxaSets.stream().noneMatch(s -> s.get(taxon) && !s.get(rep))) {
                                    others.set(taxon);
                                }
                                if (others.cardinality() > bestOthers.cardinality()) {
                                    bestRep = rep;
                                    bestOthers = others;
                                }
                            }
                        }
                        if (bestOthers.cardinality() > 0) {
                            repOthersMap.put(bestRep, bestOthers);
                        }
                    }
                }
            }
        }
        if (!repOthersMap.isEmpty()) {
            trees.replaceAll(PhyloTree::new);

            if (verbose)
                System.err.println("Before: " + StringUtils.toString(BitSetUtils.asList(taxa).stream().map(taxLabelMap::get).toList(), " "));

            for (var t : repOthersMap.keySet()) {
                var bitSet = repOthersMap.get(t);
                if (verbose)
                    System.err.println("t=" + taxLabelMap.get(t) + " set=" + StringUtils.toString(BitSetUtils.asList(bitSet).stream().map(taxLabelMap::get).toList(), " "));

                for (var tree : trees) {
                    var treeTaxa = BitSetUtils.asBitSet(tree.getTaxa());
                    if (treeTaxa.intersects(bitSet)) {
                        if (verbose)
                            System.err.println("Before: " + tree.toBracketString(false));
                        var parents = new HashSet<Node>();

                        for (var v : tree.nodes()) {
                            if (v.isLeaf() && tree.getTaxon(v) == -1) {
                                System.err.println("Unlabeled leaf: " + v + " in " + tree.toBracketString(false));
                            }
                        }

                        tree.nodeStream().filter(Node::isLeaf).filter(v -> bitSet.get(tree.getTaxon(v)))
                                .forEach(v -> {
                                    parents.add(v.getParent());
                                    tree.getTaxonNodeMap().remove(tree.getTaxon(v), v);
                                    tree.deleteNode(v);
                                });
                        for (var p : parents) {
                            if (p.getInDegree() == 1 && p.getOutDegree() == 1 && p.getParent() != tree.getRoot())
                                tree.delDivertex(p);
                        }
                        if (verbose)
                            System.err.println("After: " + tree.toBracketString(false));
                    }
                }
            }
            if (verbose)
                System.err.println("After: " + StringUtils.toString(BitSetUtils.asList(taxa).stream().map(taxLabelMap::get).toList(), " "));
        }
        taxa.clear();

        for (var tree : trees) {
            taxa.or(BitSetUtils.asBitSet(tree.getTaxa()));
        }
        return repOthersMap;
    }

    /**
     * original restore logic, unchanged
     */
    private List<PhyloTree> restoreGroupedTaxa(Map<Integer, BitSet> repGroupMap, Map<Integer, String> taxLabelMap, List<PhyloTree> networks) {
        if (!repGroupMap.isEmpty()) {
            for (var network : networks) {
                IteratorUtils.asStream(network.nodes()).filter(Node::isLeaf).filter(v -> repGroupMap.containsKey(network.getTaxon(v)))
                        .forEach(v -> {
                            var p = v.getParent();
                            network.deleteEdge(p.getEdgeTo(v));
                            var q = network.newNode();
                            network.newEdge(p, q);
                            network.newEdge(q, v);
                            for (var t : BitSetUtils.members(repGroupMap.get(network.getTaxon(v)))) {
                                var w = network.newNode();
                                network.addTaxon(w, t);
                                network.setLabel(w, taxLabelMap.get(t));
                                network.newEdge(q, w);
                            }
                        });
                if (checkAllPartialResults)
                    NetworkUtils.check(network);
            }
        }
        return networks;
    }

    /**
     * compute all trees below a cluster
     */
    private List<TraceTree> computeTreesBelow(List<TraceTree> trees, Map<Integer, String> taxonLabelMap, BitSet taxa) {
        var clusterSetToIds = new HashMap<HashSet<BitSet>, BitSet>();

        for (var traced : trees) {
            var tree = traced.tree();
            var clusters = new HashSet<BitSet>();
            for (var cluster : TreesUtils.collectAllHardwiredClusters(tree)) {
                cluster = BitSetUtils.intersection(cluster, taxa);
                if (cluster.cardinality() > 0)
                    clusters.add(cluster);
            }
            if (!clusters.isEmpty()) {
                clusterSetToIds.computeIfAbsent(clusters, k -> new BitSet()).or(traced.representedTreeIds());
            }
        }

        var result = new ArrayList<TraceTree>();
        for (var entry : clusterSetToIds.entrySet()) {
            var tree = new PhyloTree();
            ClusterPoppingAlgorithm.apply(entry.getKey(), tree);
            tree.nodeStream().filter(Node::isLeaf).forEach(v -> tree.setLabel(v, taxonLabelMap.get(tree.getTaxon(v))));
            result.add(new TraceTree(tree, entry.getValue()));
        }
        return result;
    }

    /**
     * computes all the trees above a cluster
     */
    private List<TraceTree> computeTreesAbove(List<TraceTree> trees, Map<Integer, String> taxonLabelMap, BitSet taxa, int rep) {
        var clusterSetToIds = new HashMap<HashSet<BitSet>, BitSet>();

        for (var traced : trees) {
            var tree = traced.tree();
            var clusters = new HashSet<BitSet>();
            for (var cluster : TreesUtils.collectAllHardwiredClusters(tree)) {
                cluster = (BitSet) cluster.clone();
                if (cluster.intersects(taxa)) {
                    cluster.andNot(taxa);
                    cluster.set(rep);
                }
                clusters.add(cluster);
            }
            if (!clusters.isEmpty()) {
                clusterSetToIds.computeIfAbsent(clusters, k -> new BitSet()).or(traced.representedTreeIds());
            }
        }

        var result = new ArrayList<TraceTree>();
        for (var entry : clusterSetToIds.entrySet()) {
            var tree = new PhyloTree();
            ClusterPoppingAlgorithm.apply(entry.getKey(), tree);
            tree.nodeStream().filter(Node::isLeaf).forEach(v -> tree.setLabel(v, taxonLabelMap.get(tree.getTaxon(v))));
            result.add(new TraceTree(tree, entry.getValue()));
        }

        if (checkAllPartialResults) {
            var found = false;
            for (var traced : trees) {
                if (IteratorUtils.asSet(traced.tree().getTaxa()).contains(rep))
                    found = true;
            }
            if (!found)
                System.err.println("Trees don't contain rep=" + rep);
        }

        return result;
    }

    /**
     * copy a source network into a target network, preserving trace info if present
     */
    public static void copySubNetwork(Node sourceRoot, Node targetNode) {
        var sourceTree = (PhyloTree) sourceRoot.getOwner();
        var targetTree = (PhyloTree) targetNode.getOwner();
        try (NodeArray<Node> old2new = sourceTree.newNodeArray()) {
            old2new.put(sourceRoot, targetNode);
            copyNodeInfo(sourceRoot, targetNode);

            var allBelow = new HashSet<Node>();
            sourceTree.postorderTraversal(sourceRoot, v -> !allBelow.contains(v), allBelow::add);
            for (var v : allBelow) {
                if (v != sourceRoot) {
                    var w = targetTree.newNode();
                    old2new.put(v, w);
                    for (var t : sourceTree.getTaxa(v)) {
                        targetTree.addTaxon(w, t);
                    }
                    copyNodeInfo(v, w);
                }
            }
            for (var e : sourceTree.edges()) {
                if (old2new.containsKey(e.getSource()) && old2new.containsKey(e.getTarget())) {
                    var f = targetTree.newEdge(old2new.get(e.getSource()), old2new.get(e.getTarget()));
                    targetTree.setWeight(f, sourceTree.getWeight(e));
                    copyEdgeInfo(e, f);
                }
            }
        }
    }

    /**
     * post-processing:
     * 1. assign exact tree ids to leaves from the original input trees
     * 2. propagate upward: every internal node gets union of all child-subtree tree ids
     *    and existing reticulation-edge ids below it
     * 3. preserve reticulation-edge ids already assigned during SCS/network construction
     * 4. only fill missing reticulation-edge ids if still empty
     * 5. fallback only for still-empty nodes / reticulation edges
     */
    private static void completeMissingNodeTreeIds(PhyloTree network, BitSet fallbackIds, Map<Integer, BitSet> taxonToTreeIds) {
        // Step 1: leaves get exact ids from original input trees
        for (var v : network.nodes()) {
            if (v.isLeaf() && network.hasTaxa(v)) {
                var taxon = network.getTaxon(v);
                var ids = taxonToTreeIds.get(taxon);
                if (ids != null && !ids.isEmpty()) {
                    setNodeTreeIds(v, ids);
                }
            }
        }

        // Step 2: upward closure
        // Internal node TT should be determined by what passes through its outgoing branches.
        var postorder = new ArrayList<Node>();
        network.postorderTraversal(postorder::add);

        for (var v : postorder) {
            if (!v.isLeaf()) {
                var ids = getNodeTreeIds(v); // keep existing SCS ids if any

                for (var e : v.outEdges()) {
                    var child = e.getTarget();

                    if (child.getInDegree() > 1) {
                        // reticulation child: only trees on this specific branch count
                        ids.or(getEdgeTreeIds(e));
                    } else {
                        // ordinary tree child: whole child subtree counts
                        ids.or(getNodeTreeIds(child));
                    }
                }

                if (!ids.isEmpty()) {
                    setNodeTreeIds(v, ids);
                }
            }
        }

        // Step 3: fill missing reticulation-edge ids only if still empty
        for (var e : network.edges()) {
            if (e.getTarget().getInDegree() > 1 && getEdgeTreeIds(e).isEmpty()) {
                var ids = new BitSet();
                ids.or(getNodeTreeIds(e.getSource()));
                ids.and(getNodeTreeIds(e.getTarget()));
                if (!ids.isEmpty()) {
                    setEdgeTreeIds(e, ids);
                }
            }
        }

        // Step 4: fallback only if something is still empty
        for (var v : network.nodes()) {
            if (getNodeTreeIds(v).isEmpty()) {
                setNodeTreeIds(v, fallbackIds);
            }
        }

        for (var e : network.edges()) {
            if (e.getTarget().getInDegree() > 1 && getEdgeTreeIds(e).isEmpty()) {
                setEdgeTreeIds(e, fallbackIds);
            }
        }
    }

    private static BitSet collectDescendantNodeTreeIds(PhyloTree network, Node start) {
        var result = new BitSet();
        var stack = new Stack<Node>();
        var visited = new HashSet<Node>();

        stack.push(start);
        visited.add(start);

        while (!stack.isEmpty()) {
            var v = stack.pop();

            result.or(getNodeTreeIds(v));

            for (var e : v.outEdges()) {
                var w = e.getTarget();
                if (!visited.contains(w)) {
                    visited.add(w);
                    stack.push(w);
                }
            }
        }
        return result;
    }

    private static List<TraceTree> rebuildTraceTrees(List<PhyloTree> plainTrees, List<TraceTree> oldTraceTrees) {
        var result = new ArrayList<TraceTree>();
        for (int i = 0; i < plainTrees.size(); i++) {
            result.add(new TraceTree(plainTrees.get(i), (BitSet) oldTraceTrees.get(i).representedTreeIds().clone()));
        }
        return result;
    }

    private static BitSet unionRepresentedTreeIds(List<TraceTree> trees) {
        var result = new BitSet();
        for (var traced : trees) {
            result.or(traced.representedTreeIds());
        }
        return result;
    }

    private static BitSet union(BitSet a, BitSet b) {
        var result = new BitSet();
        result.or(a);
        result.or(b);
        return result;
    }

    /**
     * after each core run, convert local active-tree ids to original input-tree ids
     */
    private static void expandActiveToOriginalTreeIds(PhyloTree network, List<TraceTree> activeTrees) {
        for (var v : network.nodes()) {
            var local = getNodeTreeIds(v);
            if (!local.isEmpty()) {
                var expanded = new BitSet();
                for (int i = local.nextSetBit(0); i >= 0; i = local.nextSetBit(i + 1)) {
                    if (i >= 0 && i < activeTrees.size()) {
                        expanded.or(activeTrees.get(i).representedTreeIds());
                    }
                }
                setNodeTreeIds(v, expanded);
            }
        }

        for (var e : network.edges()) {
            var local = getEdgeTreeIds(e);
            if (!local.isEmpty()) {
                var expanded = new BitSet();
                for (int i = local.nextSetBit(0); i >= 0; i = local.nextSetBit(i + 1)) {
                    if (i >= 0 && i < activeTrees.size()) {
                        expanded.or(activeTrees.get(i).representedTreeIds());
                    }
                }
                setEdgeTreeIds(e, expanded);
            }
        }
    }

    private static PhyloTree copyTreePreservingInfo(PhyloTree source) {
        var target = new PhyloTree();
        try (NodeArray<Node> old2new = source.newNodeArray()) {
            var nodes = IteratorUtils.asList(source.nodes());
            for (var v : nodes) {
                var w = target.newNode();
                old2new.put(v, w);
                copyNodeInfo(v, w);
                for (var t : source.getTaxa(v)) {
                    target.addTaxon(w, t);
                }
                if (source.getLabel(v) != null) {
                    target.setLabel(w, source.getLabel(v));
                }
                if (v == source.getRoot()) {
                    target.setRoot(w);
                }
            }
            for (var e : source.edges()) {
                var f = target.newEdge(old2new.get(e.getSource()), old2new.get(e.getTarget()));
                target.setWeight(f, source.getWeight(e));
                copyEdgeInfo(e, f);
            }
        }
        return target;
    }

    private static void copyNodeInfo(Node source, Node target) {
        if (source.getInfo() instanceof BitSet bs) {
            target.setInfo((BitSet) bs.clone());
        } else if (source.getInfo() != null) {
            target.setInfo(source.getInfo());
        }
    }

    private static void copyEdgeInfo(Edge source, Edge target) {
        if (source.getInfo() instanceof BitSet bs) {
            target.setInfo((BitSet) bs.clone());
        } else if (source.getInfo() != null) {
            target.setInfo(source.getInfo());
        }
    }

    private static void setNodeTreeIds(Node v, BitSet ids) {
        v.setInfo((BitSet) ids.clone());
    }

    private static BitSet getNodeTreeIds(Node v) {
        return v.getInfo() instanceof BitSet bs ? (BitSet) bs.clone() : new BitSet();
    }

    private static void setEdgeTreeIds(Edge e, BitSet ids) {
        e.setInfo((BitSet) ids.clone());
    }

    private static BitSet getEdgeTreeIds(Edge e) {
        return e.getInfo() instanceof BitSet bs ? (BitSet) bs.clone() : new BitSet();
    }

    private static String bitSetToTreeString(BitSet bs) {
        var list = new ArrayList<Integer>();
        for (int i = bs.nextSetBit(0); i >= 0; i = bs.nextSetBit(i + 1)) {
            list.add(i + 1);
        }
        return list.toString();
    }
}
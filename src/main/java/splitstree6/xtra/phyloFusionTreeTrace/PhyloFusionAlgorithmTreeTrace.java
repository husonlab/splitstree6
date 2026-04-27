package splitstree6.xtra.phyloFusionTreeTrace;

import jloda.graph.Node;
import jloda.graph.NodeArray;
import jloda.phylo.PhyloTree;
import jloda.util.*;
import jloda.util.progress.ProgressListener;
import splitstree6.compute.phylofusion.FixedCapacitySortedSet;
import splitstree6.utils.PathMultiplicityDistance;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;
import jloda.graph.Edge;

/**
 * the PhyloFusion algorithm in a metadata-aware environment
 * Taxa drive all combinatorial logic; tree ids are traced as metadata
 */
public class PhyloFusionAlgorithmTreeTrace {
    /**
     * run the algorithm
     *
     * @param inputTrees input rooted trees
     * @param progress   progress listener
     * @return the computed networks
     * @throws IOException user canceled
     */
    public static List<PhyloTree> apply(List<PhyloTree> inputTrees, boolean onlyOneNetwork,
                                        boolean useRefinementHeuristic, boolean useMissingTaxaHeuristic,
                                        ProgressListener progress) throws IOException {
        if (inputTrees.size() == 1) {
            return List.of(new PhyloTree(inputTrees.get(0)));
        }

        var treeTaxa = new ArrayList<BitSet>();

        var trees = new ArrayList<PhyloTree>();
        for (var tree : inputTrees) {
            tree = new PhyloTree(tree);
            if (tree.getRoot().getOutDegree() > 1) {
                var root = tree.newNode();
                var e = tree.newEdge(root, tree.getRoot());
                tree.setWeight(e, 0);
                tree.setRoot(root);
            }
            trees.add(tree);
            treeTaxa.add(BitSetUtils.asBitSet(tree.getTaxa()));
        }
        //printTaxonLabelIdMap(trees);

        var allTaxa = BitSetUtils.union(treeTaxa);

        var multifurcating = trees.stream().anyMatch(tree -> tree.nodeStream().anyMatch(v -> v.getOutDegree() > 2));
        var missingTaxa = treeTaxa.stream().anyMatch(set -> !allTaxa.equals(set));

        var bestHybridizationNumber = new Single<>(Integer.MAX_VALUE);
        var best = new ArrayList<Pair<int[], Map<Integer, HyperSequenceTreeTrace>>>();

        for (var ranking : computeTaxonRankings(progress, multifurcating, missingTaxa, allTaxa, treeTaxa, trees)) {
            var taxonHyperSequencesMap = computeHyperSequenceTable(progress,
                    multifurcating && useRefinementHeuristic, missingTaxa && useMissingTaxaHeuristic,
                    allTaxa, ranking, treeTaxa, trees);

            var taxonHyperSequenceMap = new HashMap<Integer, HyperSequenceTreeTrace>();

            for (var t : taxonHyperSequencesMap.rowKeySet()) {
                //System.err.println("taxon " + t + " has " + taxonHyperSequencesMap.row(t).values());
                var hyperSequence = ProgressiveSCSTreeTrace.apply(new ArrayList<>(taxonHyperSequencesMap.row(t).values()));

                if (hyperSequence != null) {
                    hyperSequence = simplifyTraceSequence(hyperSequence);
                }
                taxonHyperSequenceMap.put(t, hyperSequence);
            }

            var hybridizationNumber = computeHybridizationNumber(allTaxa.cardinality(), taxonHyperSequenceMap);
            if (hybridizationNumber < bestHybridizationNumber.get()) {
                bestHybridizationNumber.set(hybridizationNumber);
                best.clear();
            }
            if (hybridizationNumber == bestHybridizationNumber.get()) {
                best.add(new Pair<>(ranking, taxonHyperSequenceMap));
            }
        }

        if (onlyOneNetwork && best.size() > 1) {
            var one = best.get(0);
            best.clear();
            best.add(one);
            System.err.println("Tree Trace Rank: " + inorder(one.getFirst()));
        }

        progress.setSubtask("creating networks");
        progress.setMaximum(best.size());
        progress.setProgress(0);

        var result = new ArrayList<PhyloTree>();
        for (var network : best.stream().map(pair -> computeNetwork(pair.getFirst(), pair.getSecond())).toList()) {
            if (result.stream().noneMatch(other -> PathMultiplicityDistance.compute(network, other) == 0)) {
                result.add(network);
            }
            progress.incrementProgress();
        }
        return result;
    }

    private static void printTaxonLabelIdMap(List<PhyloTree> trees) {
        var labelToId = new TreeMap<String, Integer>();
        for (var tree : trees) {
            for (var v : tree.nodes()) {
                if (v.isLeaf()) {
                    var taxonId = tree.getTaxon(v);
                    var label = tree.getLabel(v);
                    if (label != null) {
                        labelToId.putIfAbsent(label, taxonId);
                    }
                }
            }
        }
        System.err.println("=== taxon label -> taxon id ===");
        for (var entry : labelToId.entrySet()) {
            System.err.println(entry.getKey() + " -> " + entry.getValue());
        }
        System.err.println();
    }

    private static List<int[]> computeTaxonRankings(ProgressListener progress, boolean multifurcating, boolean missingTaxa,
                                                    BitSet taxa, List<BitSet> treeTaxa, ArrayList<PhyloTree> trees) throws CanceledException {
        final var nTax = taxa.cardinality();
        final var scoredOrderings1 = new FixedCapacitySortedSet<>(1, ScoredOrdering::compare);
        final var scoredOrderings2 = new FixedCapacitySortedSet<>(1, ScoredOrdering::compare);

        final int startKeepSize = 10;
        final int endKeepSize = 10;
        final int iterations = 10;
        final int maxIterationsWithoutImprovement = 4;

        progress.setTasks("Searching orderings", "taxa=" + nTax + " trees=" + trees.size());
        progress.setMaximum((long) nTax * iterations);
        progress.setProgress(1);

        scoredOrderings2.add(new ScoredOrdering(Integer.MAX_VALUE, 0, BitSetUtils.asArray(taxa)));

        var bestScores = new int[iterations];
        var verbose = false;

        for (var iteration = 0; iteration < iterations; iteration++) {
            if (verbose)
                System.err.println("Iteration: " + iteration);

            for (var pos = 0; pos < nTax; pos++) {
                scoredOrderings1.clear();
                scoredOrderings1.addAll(scoredOrderings2);
                scoredOrderings2.clear();
                progress.incrementProgress();

                var keep = (int) Math.ceil(((double) (nTax - pos) / nTax) * startKeepSize + ((double) pos / nTax) * endKeepSize);
                scoredOrderings1.changeCapacity(keep);
                scoredOrderings2.changeCapacity(keep);

                var finalIteration = iteration;
                var finalPos = pos;
                try {
                    ExecuteInParallel.apply(scoredOrderings1, order -> {
                        for (var other = finalPos; other < nTax; other++) {
                            var ordering = copyAndSwap(order.ordering, finalPos, other);
                            var score = evaluate(progress, multifurcating, missingTaxa, taxa, treeTaxa, trees, ordering, finalPos);
                            var scoredOrdering = new ScoredOrdering(score, finalIteration, ordering);
                            scoredOrderings2.add(scoredOrdering);
                        }
                    }, ProgramExecutorService.getNumberOfCoresToUse());
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }

                if (verbose)
                    System.err.println("best score (out): " + scoredOrderings2.first().score);
            }

            bestScores[iteration] = scoredOrderings2.first().score;
            if (iteration >= maxIterationsWithoutImprovement &&
                    bestScores[iteration] == bestScores[iteration - maxIterationsWithoutImprovement]) {
                if (verbose)
                    System.err.println("Break in iteration: " + iteration);
                break;
            }
        }

        return scoredOrderings2.stream().map(ScoredOrdering::ordering).map(PhyloFusionAlgorithmTreeTrace::ranking).toList();
    }

    public record ScoredOrdering(int score, int iteration, int[] ordering) {
        public static int compare(ScoredOrdering a, ScoredOrdering b) {
            int result = Integer.compare(a.score, b.score);
            if (result != 0) return result;
            result = Integer.compare(a.iteration, b.iteration);
            if (result != 0) return -result;
            return Arrays.compare(a.ordering, b.ordering);
        }
    }

    public static int evaluate(ProgressListener progress, boolean multifurcating, boolean missingTaxa,
                               BitSet taxa, List<BitSet> treeTaxa, List<PhyloTree> trees, int[] ordering, int pos) throws CanceledException {
        if (true) {
            var taxonHyperSequencesMap = computeHyperSequenceTable(progress, multifurcating, missingTaxa, taxa, ranking(ordering), treeTaxa, trees);
            var taxonHyperSequenceMap = new HashMap<Integer, HyperSequenceTreeTrace>();
            for (var t : taxonHyperSequencesMap.rowKeySet()) {
                var aligned = ProgressiveSCSTreeTrace.apply(new ArrayList<>(taxonHyperSequencesMap.row(t).values()));
                if (aligned != null) {
                    aligned = simplifyTraceSequence(aligned);
                }
                taxonHyperSequenceMap.put(t, aligned);
            }
            return computeHybridizationNumber(taxa.cardinality(), taxonHyperSequenceMap);
        } else { // partial evaluation branch, still metadata-aware
            var taxonHyperSequencesMap = computeHyperSequenceTable(progress, multifurcating, missingTaxa, taxa, ranking(ordering), treeTaxa, trees);
            var taxonHyperSequenceMap = new HashMap<Integer, HyperSequenceTreeTrace>();
            var activeTaxa = BitSetUtils.asBitSet(Arrays.copyOf(ordering, pos + 1));
            for (var t : BitSetUtils.members(activeTaxa)) {
                if (taxonHyperSequencesMap.containsRow(t)) {
                    var sequences = taxonHyperSequencesMap.row(t).values().stream()
                            .map(h -> h.induce(activeTaxa))
                            .distinct()
                            .collect(Collectors.toCollection(ArrayList::new));
                    var aligned = ProgressiveSCSTreeTrace.apply(sequences);
                    if (aligned != null) {
                        aligned = simplifyTraceSequence(aligned);
                    }
                    taxonHyperSequenceMap.put(t, aligned);
                }
            }
            return computeHybridizationNumber(activeTaxa.cardinality(), taxonHyperSequenceMap);
        }
    }

    public static int[] copyAndSwap(int[] array, int i, int j) {
        array = Arrays.copyOf(array, array.length);
        if (i != j) {
            var tmp = array[i];
            array[i] = array[j];
            array[j] = tmp;
        }
        return array;
    }

    public static int computeHybridizationNumber(int nTaxa, HashMap<Integer, HyperSequenceTreeTrace> taxonHyperSequenceMap) {
        var total = 0;
        for (var hyperSequence : taxonHyperSequenceMap.values()) {
            if (hyperSequence != null) {
                for (var component : hyperSequence.elements()) {
                    total += component.taxa().cardinality();
                }
            }
        }
        return total - (nTaxa - 1);
    }

    public static PhyloTree computeNetwork(int[] taxonRank, Map<Integer, HyperSequenceTreeTrace> taxonHyperSequenceMap) {
        var ordering = inorder(taxonRank);

        var network = new PhyloTree();
        try (NodeArray<BitSet> label = network.newNodeArray()) {
            var taxonStartMap = new HashMap<Integer, Node>();
            var taxonChainMap = new HashMap<Integer, ArrayList<Node>>();
            var chainNodeToElement = new HashMap<Node, HyperSequenceTreeTrace.Element>();

            for (var t : ordering) {
                var start = network.newNode();
                label.put(start, BitSetUtils.asBitSet(t));
                setNodeTreeIds(start, new BitSet()); // start node itself has no direct hypersequence provenance
                if (network.getRoot() == null)
                    network.setRoot(start);
                taxonStartMap.put(t, start);

                var prev = start;
                taxonChainMap.put(t, new ArrayList<>());

                if (taxonHyperSequenceMap.containsKey(t)) {
                    var hyperSequence = taxonHyperSequenceMap.get(t);
                    if (hyperSequence != null) {
                        for (var component : hyperSequence.elements()) {
                            var v = network.newNode();
                            label.put(v, (BitSet) component.taxa().clone());
                            chainNodeToElement.put(v, component);

                            // node provenance = union of all tree ids attached to taxa in this element
                            var nodeTreeIds = new BitSet();
                            for (int x = component.taxa().nextSetBit(0); x >= 0; x = component.taxa().nextSetBit(x + 1)) {
                                nodeTreeIds.or(component.getTreeIds(x));
                            }
                            setNodeTreeIds(v, nodeTreeIds);

                            var e = network.newEdge(prev, v);
                            // ordinary chain edges should stay unlabeled for final TT Newick output
                            setEdgeTreeIds(e, new BitSet());

                            taxonChainMap.get(t).add(v);
                            prev = v;
                        }
                    }
                }

                var end = network.newNode();
                label.put(end, BitSetUtils.asBitSet(t));

                // leaf provenance = union of all tree ids represented in this taxon's final aligned sequence
                var leafTreeIds = new BitSet();
                var hyperSequence = taxonHyperSequenceMap.get(t);
                if (hyperSequence != null) {
                    for (var component : hyperSequence.elements()) {
                        leafTreeIds.or(component.getTreeIds(t));
                    }
                }
                setNodeTreeIds(end, leafTreeIds);

                network.addTaxon(end, t);
                var e = network.newEdge(prev, end);
                // ordinary leaf edge should stay unlabeled
                setEdgeTreeIds(e, new BitSet());
            }

            // reticulation edges:
            // for each taxon q referenced by a chain node v, assign edge TT from the exact source element of v
            for (var p : ordering) {
                for (var v : taxonChainMap.get(p)) {
                    var sourceElement = chainNodeToElement.get(v);

                    for (var q : BitSetUtils.members(label.get(v))) {
                        var w = taxonStartMap.get(q);
                        var e = network.newEdge(v, w);

                        var edgeIds = new BitSet();
                        if (sourceElement != null) {
                            edgeIds.or(sourceElement.getTreeIds(q));
                        }
                        if (edgeIds.isEmpty()) {
                            edgeIds.or(getNodeTreeIds(v));
                        }
                        setEdgeTreeIds(e, edgeIds);

                        // the start node of q should also know this taxon can be reached by these trees
                        mergeNodeTreeIds(w, edgeIds);
                    }
                }
            }
        }

        for (var v : network.nodeStream().filter(v -> v.getInDegree() == 1 && v.getOutDegree() == 1).toList()) {
            network.delDivertex(v);
        }
        network.edgeStream().forEach(e -> network.setReticulate(e, e.getTarget().getInDegree() > 1));

        return network;
    }

    /**
     * for each taxon and tree, extracts the hyper sequence with source-tree metadata
     */
    public static Table<Integer, Integer, HyperSequenceTreeTrace> computeHyperSequenceTable(
            ProgressListener progress, boolean useRefinementHeuristic, boolean useMissingTaxaHeuristic,
            BitSet allTaxa, int[] taxonRank, List<BitSet> treeTaxa, List<PhyloTree> trees) throws CanceledException {

        var hyperSequenceTable = new Table<Integer, Integer, HyperSequenceTreeTrace>();

        for (var treeId = 0; treeId < trees.size(); treeId++) {
            var tree = trees.get(treeId);
            var minTaxon = findMin(BitSetUtils.asBitSet(tree.getTaxa()), taxonRank);

            try (NodeArray<BitSet> nodeLabels = tree.newNodeArray(); NodeArray<BitSet> taxaBelow = tree.newNodeArray()) {
                tree.postorderTraversal(v -> {
                    if (v.isLeaf()) {
                        taxaBelow.computeIfAbsent(v, k -> new BitSet()).set(tree.getTaxon(v));
                        nodeLabels.computeIfAbsent(v, k -> new BitSet()).set(tree.getTaxon(v));
                    } else {
                        Node smallestChild = null;
                        var childSmallestLeafRank = Integer.MAX_VALUE;
                        for (var w : v.children()) {
                            var minLeafRankBelow = findMin(taxaBelow.get(w), taxonRank);
                            var leafRank = taxonRank[minLeafRankBelow];
                            if (leafRank < childSmallestLeafRank) {
                                smallestChild = w;
                                childSmallestLeafRank = leafRank;
                            }
                        }
                        nodeLabels.put(v, new BitSet());
                        for (var w : v.children()) {
                            if (w != smallestChild) {
                                nodeLabels.get(v).set(findMin(taxaBelow.get(w), taxonRank));
                            }
                        }
                        taxaBelow.put(v, new BitSet());
                        for (var w : v.children()) {
                            taxaBelow.get(v).or(taxaBelow.get(w));
                        }
                    }
                });
                nodeLabels.computeIfAbsent(tree.getRoot(), k -> new BitSet()).set(minTaxon);

                var taxonReverseSequenceMap = new HashMap<Integer, ArrayList<HyperSequenceTreeTrace.Element>>();
                var finalTreeId = treeId;

                tree.postorderTraversal(v -> {
                    if (v.isLeaf()) {
                        taxonReverseSequenceMap.put(tree.getTaxon(v), new ArrayList<>());
                    } else {
                        for (var t : BitSetUtils.members(taxaBelow.get(v))) {
                            var labels = nodeLabels.get(v);
                            if (labels.get(t)) {
                                var sequence = taxonReverseSequenceMap.get(t);
                                if (!sequence.isEmpty()) {
                                    CollectionUtils.reverseInPlace(sequence);
                                    hyperSequenceTable.put(t, finalTreeId, new HyperSequenceTreeTrace(sequence));
                                }
                                taxonReverseSequenceMap.remove(t);
                            } else {
                                if (taxonReverseSequenceMap.containsKey(t)) {
                                    taxonReverseSequenceMap.get(t).add(HyperSequenceTreeTrace.Element.fromTaxaAndTree(labels, finalTreeId));
                                }
                            }
                        }
                    }
                });

                if (true) {
                    var taxonCount = new HashMap<Integer, Integer>();
                    var theTaxa = new BitSet();
                    for (var v : tree.nodes()) {
                        for (var t : BitSetUtils.members(nodeLabels.get(v))) {
                            taxonCount.put(t, taxonCount.getOrDefault(t, 0) + 1);
                            theTaxa.set(t);
                        }
                    }
                    for (var t : BitSetUtils.members(theTaxa)) {
                        if (taxonCount.get(t) == null || taxonCount.get(t) != 2)
                            System.err.println("Error: taxon " + t + ": count=" + taxonCount.get(t));
                    }
                }

                if (!taxonReverseSequenceMap.isEmpty()) {
                    throw new RuntimeException("taxonReverseSequenceMap: " + taxonReverseSequenceMap.size());
                }
            }
            progress.checkForCancel();
        }

        if (useRefinementHeuristic) {
            applyRefinementRule1(hyperSequenceTable, taxonRank);
        }
        if (useMissingTaxaHeuristic) {
            applyMissingTaxaRule3(hyperSequenceTable, allTaxa, treeTaxa);
        }
        return hyperSequenceTable;
    }

    /**
     * metadata-aware refinement rule:
     * remove R from E and insert a copied metadata-preserving version of R at the front of seq1t
     */
    private static void applyRefinementRule1(Table<Integer, Integer, HyperSequenceTreeTrace> hyperSequenceTable, int[] taxonRank) {
        var ordering = inorder(taxonRank);
        var treeOrder = new TreeSet<>(hyperSequenceTable.columnKeySet());

        for (var i = 0; i < ordering.size() - 1; i++) {
            var taxonS = ordering.get(i);

            for (var tree1 : treeOrder) {
                var seq1s = hyperSequenceTable.get(taxonS, tree1);
                if (seq1s == null)
                    continue;

                for (var elementE : seq1s.elements()) {
                    if (elementE.taxa().cardinality() <= 1)
                        continue;

                    var taxonZ = getLargest(taxonRank, elementE.taxa());
                    var remainingR = BitSetUtils.minus(elementE.taxa(), BitSetUtils.asBitSet(taxonZ));

                    if (seq1s.elements().stream()
                            .filter(e -> e != elementE)
                            .noneMatch(e -> e.taxa().intersects(remainingR))) {
                        loop:
                        for (var j = i + 1; j < ordering.size(); j++) {
                            var taxonT = ordering.get(j);
                            for (var tree2 : treeOrder) {
                                if (tree2.equals(tree1))
                                    continue;

                                var seq2t = hyperSequenceTable.get(taxonT, tree2);
                                if (seq2t == null)
                                    continue;

                                if (BitSetUtils.contains(unionTaxa(seq2t.elements()), remainingR)) {
                                    var moved = elementE.inducedBy(remainingR);
                                    elementE.removeTaxa(remainingR);

                                    var seq1t = hyperSequenceTable.get(taxonT, tree1);
                                    if (seq1t != null && !moved.isEmpty()) {
                                        seq1t.elements().add(0, moved);
                                        break loop;
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * tree id-aware missing taxa heuristic
     * - when replacing y by missing taxon x inside S_i,p, assign x the target-tree id {treeP}
     * - for the missing taxon sequence S_x,p, add ONLY the borrowed y-element, also with tree id {treeP}
     * - do not rebuild/copy the rest of S_x,j into S_x,p
     *
     * @param hyperSequenceTable hyper sequence table
     * @param allTaxa all taxa
     * @param treeTaxa taxa present in each tree
     */
    private static void applyMissingTaxaRule3(Table<Integer, Integer, HyperSequenceTreeTrace> hyperSequenceTable,
                                              BitSet allTaxa, List<BitSet> treeTaxa) {
        var verbose = false;
        var treeOrder = new TreeSet<>(hyperSequenceTable.columnKeySet());

        for (var taxA : BitSetUtils.members(allTaxa)) {
            var aSet = BitSetUtils.asBitSet(taxA);

            loop:
            for (var treeJ : treeOrder) {
                if (!treeTaxa.get(treeJ).get(taxA))
                    continue;
                if (!hyperSequenceTable.contains(taxA, treeJ))
                    continue;

                var seqSaj = hyperSequenceTable.get(taxA, treeJ);

                for (var treeP : treeOrder) {
                    if (treeJ.equals(treeP))
                        continue;
                    if (treeTaxa.get(treeP).get(taxA))
                        continue;

                    for (var taxI : hyperSequenceTable.rowKeySet()) {
                        if (!hyperSequenceTable.contains(taxI, treeJ))
                            continue;
                        if (!hyperSequenceTable.contains(taxI, treeP))
                            continue;

                        var seqSij = hyperSequenceTable.get(taxI, treeJ);
                        var seqSip = hyperSequenceTable.get(taxI, treeP);

                        for (var sourceAInSij : seqSij.elements()) {
                            if (!sourceAInSij.taxa().equals(aSet))
                                continue;

                            if (BitSetUtils.contains(treeTaxa.get(treeP), aSet))
                                continue;

                            for (int pos = 0; pos < seqSip.elements().size(); pos++) {
                                var bElementInSip = seqSip.elements().get(pos);
                                var bSet = bElementInSip.taxa();

                                if (bSet.cardinality() < 1)
                                    continue;

                                var matchingInSaj = firstElementWithSameTaxa(seqSaj, bSet);
                                if (matchingInSaj == null)
                                    continue;

                                // replace y by x in S_i,p, but x now carries target-tree metadata {treeP}
                                var replacementXInTreeP =
                                        HyperSequenceTreeTrace.Element.fromTaxaAndTree(aSet, treeP);
                                seqSip.elements().set(pos, replacementXInTreeP);

                                // create S_a,p with ONLY the borrowed y-element, tagged with target tree {treeP}
                                var newSap = new HyperSequenceTreeTrace();
                                newSap.add(HyperSequenceTreeTrace.Element.fromTaxaAndTree(bSet, treeP));
                                hyperSequenceTable.put(taxA, treeP, newSap);

                                if (verbose) {
                                    System.err.println("modified missing-taxa rule:");
                                    System.err.println("  missing taxon a = " + taxA + " in tree " + treeP);
                                    System.err.println("  donor tree_j    = " + treeJ);
                                    System.err.println("  witness taxon i = " + taxI);
                                    System.err.println("  replaced in S_" + taxI + "," + treeP + ": "
                                            + bElementInSip + " -> " + replacementXInTreeP);
                                    System.err.println("  new S_" + taxA + "," + treeP + " = " + newSap);
                                }

                                break loop;
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * metadata-preserving simplification:
     * if a taxon survives in the next element, carry its metadata forward before removing it here
     */
    private static HyperSequenceTreeTrace simplifyTraceSequence(HyperSequenceTreeTrace hyperSequence) {
        var simplified = new HyperSequenceTreeTrace();
        if (hyperSequence == null || hyperSequence.isEmpty())
            return simplified;

        var working = hyperSequence.copy().elements();

        for (int i = 0; i < working.size() - 1; i++) {
            var current = working.get(i);
            var next = working.get(i + 1);

            transferOverlapTreeInfo(current, next);

            var keep = BitSetUtils.minus(current.taxa(), next.taxa());
            if (keep.cardinality() > 0) {
                simplified.add(current.inducedBy(keep));
            }
        }

        simplified.add(working.get(working.size() - 1).copy());
        simplified.removeEmptyElements();
        return simplified;
    }

    private static void transferOverlapTreeInfo(HyperSequenceTreeTrace.Element current,
                                                HyperSequenceTreeTrace.Element next) {
        BitSet overlap = (BitSet) current.taxa().clone();
        overlap.and(next.taxa());

        for (int taxon = overlap.nextSetBit(0); taxon >= 0; taxon = overlap.nextSetBit(taxon + 1)) {
            var currentTrees = current.treeIdsPerTaxon().get(taxon);
            if (currentTrees != null) {
                next.treeIdsPerTaxon().computeIfAbsent(taxon, k -> new BitSet()).or((BitSet) currentTrees.clone());
            }
        }
    }

    private static HyperSequenceTreeTrace.Element firstElementWithSameTaxa(HyperSequenceTreeTrace sequence, BitSet taxa) {
        for (var element : sequence.elements()) {
            if (element.taxa().equals(taxa)) {
                return element;
            }
        }
        return null;
    }

    private static BitSet unionTaxa(Collection<HyperSequenceTreeTrace.Element> elements) {
        var result = new BitSet();
        for (var element : elements) {
            result.or(element.taxa());
        }
        return result;
    }

    public static int findMin(BitSet taxa, int[] taxonRank) {
        var result = -1;
        for (var t : BitSetUtils.members(taxa)) {
            if (result == -1)
                result = t;
            else if (taxonRank[t] < taxonRank[result])
                result = t;
        }
        return result;
    }

    public static ArrayList<Integer> inorder(int[] taxonRank) {
        var ordering = new int[taxonRank.length];
        for (var t = 1; t < taxonRank.length; t++) {
            ordering[taxonRank[t]] = t;
        }
        var list = new ArrayList<Integer>();
        for (var t = 1; t < ordering.length; t++) {
            if (ordering[t] != 0)
                list.add(ordering[t]);
        }
        return list;
    }

    public static int[] ranking(int[] order) {
        var taxonRank = new int[Arrays.stream(order).max().orElse(0) + 1];
        var rank = 0;
        for (int taxon : order) {
            if (taxon > 0)
                taxonRank[taxon] = ++rank;
        }
        return taxonRank;
    }

    private static int getLargest(int[] taxonRank, BitSet set) {
        var largestRank = 0;
        var largestTaxon = 0;
        for (var t : BitSetUtils.members(set)) {
            if (taxonRank[t] > largestRank) {
                largestRank = taxonRank[t];
                largestTaxon = t;
            }
        }
        return largestTaxon;
    }

    public static BitSet union(Collection<Iterable<Integer>> sets) {
        var result = new BitSet();
        for (var set : sets) {
            for (var t : set) {
                result.set(t);
            }
        }
        return result;
    }

    private static void setNodeTreeIds(Node v, BitSet ids) {
        v.setInfo((BitSet) ids.clone());
    }

    private static BitSet getNodeTreeIds(Node v) {
        return v.getInfo() instanceof BitSet bs ? (BitSet) bs.clone() : new BitSet();
    }

    private static void mergeNodeTreeIds(Node v, BitSet ids) {
        var current = getNodeTreeIds(v);
        current.or(ids);
        setNodeTreeIds(v, current);
    }

    private static void setEdgeTreeIds(Edge e, BitSet ids) {
        e.setInfo((BitSet) ids.clone());
    }

    private static BitSet getEdgeTreeIds(Edge e) {
        return e.getInfo() instanceof BitSet bs ? (BitSet) bs.clone() : new BitSet();
    }
}
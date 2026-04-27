package splitstree6.xtra.phyloFusionTreeTrace;

import jloda.util.*;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashSet;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * determines the shortest common hypersequenceWithTreeInfo using dynamic programming
 * Daniel Huson, 8.2024
 */
public class SCSTreeTrace {
    private static final byte TRACEBACK_INSERT_A = 1;
    private static final byte TRACEBACK_INSERT_B = 2;
    private static final byte TRACEBACK_MATCH = 4;

    /**
     * determines the shortest common hypersequenceWithTreeInfo using dynamic programming
     *
     * @param a one hypersequenceWithTreeInfo
     * @param b the other
     * @return super sequence
     */
    public static HyperSequenceTreeTrace align(HyperSequenceTreeTrace a, HyperSequenceTreeTrace b) {
        var m = a.size();
        var n = b.size();

        var matrix = new int[m + 1][n + 1];
        var traceback = new byte[m + 1][n + 1];

        for (var i = 1; i <= m; i++) {
            matrix[i][0] = i;
            traceback[i][0] = TRACEBACK_INSERT_A;
        }
        for (var j = 1; j <= n; j++) {
            matrix[0][j] = j;
            traceback[0][j] = TRACEBACK_INSERT_B;
        }

        for (var i = 1; i <= m; i++) {
            var i1 = i - 1;
            for (var j = 1; j <= n; j++) {
                var j1 = j - 1;

                var insertionInA = matrix[i1][j] + 1;
                var insertionInB = matrix[i][j1] + 1;

                if (BitSetUtils.contains(a.get(i1), b.get(j1)) || BitSetUtils.contains(b.get(j1), a.get(i1))) {
                    var match = matrix[i1][j1];
                    var best = NumberUtils.min(insertionInA, insertionInB, match);
                    if (insertionInA == best)
                        traceback[i][j] |= TRACEBACK_INSERT_A;
                    if (insertionInB == best)
                        traceback[i][j] |= TRACEBACK_INSERT_B;
                    if (match == best)
                        traceback[i][j] |= TRACEBACK_MATCH;
                    matrix[i][j] = best;
                } else {
                    var best = Math.min(insertionInA, insertionInB);
                    if (insertionInA == best)
                        traceback[i][j] |= TRACEBACK_INSERT_A;
                    if (insertionInB == best)
                        traceback[i][j] |= TRACEBACK_INSERT_B;
                    matrix[i][j] = best;
                }
            }
        }

        var best = new Single<>(Integer.MAX_VALUE);
        var result = new Single<HyperSequenceTreeTrace>();
        var seen = new HashSet<Pair<ArrayList<Integer>, ArrayList<Integer>>>();

        traceback(m, n, matrix, traceback, 100, (aTrace, bTrace) -> {
            var pair = new Pair<>(aTrace, bTrace);
            if (!seen.add(pair))
                return;

            aTrace = CollectionUtils.reverse(aTrace);
            bTrace = CollectionUtils.reverse(bTrace);

            var hyperSequenceWithTreeInfo = new HyperSequenceTreeTrace();
            for (var p = 0; p < aTrace.size(); p++) {
                HyperSequenceTreeTrace.Element merged;

                if (aTrace.get(p) != -1 && bTrace.get(p) != -1) {
                    merged = mergeElements(a.getElement(aTrace.get(p)), b.getElement(bTrace.get(p)));
                } else if (aTrace.get(p) != -1) {
                    merged = a.getElement(aTrace.get(p)).copy();
                } else if (bTrace.get(p) != -1) {
                    merged = b.getElement(bTrace.get(p)).copy();
                } else {
                    merged = new HyperSequenceTreeTrace.Element(new BitSet());
                }
                hyperSequenceWithTreeInfo.add(merged);
            }

            var simplified = new HyperSequenceTreeTrace();
            var count = 0;

            var working = new ArrayList<HyperSequenceTreeTrace.Element>();
            for (var element : hyperSequenceWithTreeInfo.elements()) {
                working.add(element.copy());
            }

            for (var i = 0; i < working.size() - 1; i++) {
                var current = working.get(i);
                var next = working.get(i + 1);

                transferOverlapTreeInfo(current, next);

                var keep = BitSetUtils.minus(current.taxa(), next.taxa());
                if (keep.cardinality() > 0) {
                    count += keep.cardinality();
                    simplified.add(restrictElement(current, keep));
                }
            }

            if (!working.isEmpty()) {
                var last = working.get(working.size() - 1);
                count += last.taxa().cardinality();
                simplified.add(last.copy());
            }

            if (count < best.get()) {
                best.set(count);
                result.set(simplified);
            }
        });

        return result.get();
    }


    /**
     * perform trace back
     *
     * @param m                 starting row
     * @param n                 starting column
     * @param matrix            DP matrix
     * @param maxResults        the maximum number of results to consider
     * @param tracebackConsumer consume the resulting traceback
     */
    private static void traceback(int m, int n, int[][] matrix, byte[][] traceback, int maxResults, BiConsumer<ArrayList<Integer>, ArrayList<Integer>> tracebackConsumer) {
        traceBackRec(m, n, matrix[m][n], matrix, traceback, new ArrayList<>(), new ArrayList<>(), new Counter(maxResults), tracebackConsumer);
    }

    private static void traceBackRec(final int i, final int j, final int value, int[][] matrix, byte[][] traceback, ArrayList<Integer> aTrace, ArrayList<Integer> bTrace, Counter resultsToConsume, BiConsumer<ArrayList<Integer>, ArrayList<Integer>> tracebackConsumer) {
        if ((traceback[i][j] & TRACEBACK_INSERT_A) != 0) {
            aTrace.add(i - 1);
            bTrace.add(-1);
            traceBackRec(i - 1, j, matrix[i - 1][j], matrix, traceback, aTrace, bTrace, resultsToConsume, tracebackConsumer);
            aTrace.remove(aTrace.size() - 1);
            bTrace.remove(bTrace.size() - 1);
            if (resultsToConsume.get() == 0)
                return;
        }
        if ((traceback[i][j] & TRACEBACK_INSERT_B) != 0) {
            aTrace.add(-1);
            bTrace.add(j - 1);
            traceBackRec(i, j - 1, matrix[i][j - 1], matrix, traceback, aTrace, bTrace, resultsToConsume, tracebackConsumer);
            aTrace.remove(aTrace.size() - 1);
            bTrace.remove(bTrace.size() - 1);
            if (resultsToConsume.get() == 0)
                return;
        }
        if ((traceback[i][j] & TRACEBACK_MATCH) != 0) {
            aTrace.add(i - 1);
            bTrace.add(j - 1);
            traceBackRec(i - 1, j - 1, matrix[i - 1][j - 1], matrix, traceback, aTrace, bTrace, resultsToConsume, tracebackConsumer);
            aTrace.remove(aTrace.size() - 1);
            bTrace.remove(bTrace.size() - 1);
            if (resultsToConsume.get() == 0)
                return;
        }
        if (i == 0 && j == 0) {
            tracebackConsumer.accept(aTrace, bTrace);
            resultsToConsume.decrement();
        }
    }

    private static void reportTrace(HyperSequenceTreeTrace a, HyperSequenceTreeTrace b, ArrayList<Integer> aTrace, ArrayList<Integer> bTrace) {
        var bufA = new StringBuilder();
        var bufB = new StringBuilder();

        for (var p = 0; p < aTrace.size(); p++) {
            String wordA;
            String wordB;
            if (aTrace.get(p) == -1) {
                wordB = StringUtils.toString(b.get(bTrace.get(p)));
                wordA = " ".repeat(wordB.length());
            } else if (bTrace.get(p) == -1) {
                wordA = StringUtils.toString(a.get(aTrace.get(p)));
                wordB = " ".repeat(wordA.length());
            } else {
                wordA = StringUtils.toString(a.get(aTrace.get(p)));
                wordB = StringUtils.toString(b.get(bTrace.get(p)));
                if (wordA.length() < wordB.length()) {
                    wordA += " ".repeat(wordB.length() - wordA.length());
                } else if (wordB.length() < wordA.length()) {
                    wordB += " ".repeat(wordA.length() - wordB.length());
                }
            }
            bufA.append(wordA).append(" : ");
            bufB.append(wordB).append(" : ");
        }
        System.err.println("\nAlignment:");
        System.err.println(bufA);
        System.err.println(bufB);
        System.err.println();
    }

    public static Pair<HyperSequenceTreeTrace, HyperSequenceTreeTrace> preProcessExpansion(HyperSequenceTreeTrace a, HyperSequenceTreeTrace b) {
        var aExpanded = new HyperSequenceTreeTrace();
        var bExpanded = new HyperSequenceTreeTrace();

        for (var i = 0; i < 2; i++) {
            var first = (i == 0 ? a : b);
            var second = (i == 0 ? b : a);
            var expanded = (i == 0 ? aExpanded : bExpanded);

            for (var element : first.elements()) {
                var set = element.taxa();
                if (set.cardinality() == 1)
                    expanded.add(element.copy());
                else {
                    var remaining = BitSetUtils.copy(set);
                    var list = new ArrayList<HyperSequenceTreeTrace.Element>();

                    for (var other : second.elements()) {
                        if (set.intersects(other.taxa())) {
                            var intersection = BitSetUtils.intersection(set, other.taxa());
                            remaining.andNot(intersection);
                            list.add(restrictElement(element, intersection));
                        }
                    }
                    if (remaining.cardinality() > 0) {
                        expanded.add(restrictElement(element, remaining));
                    }
                    list.forEach(expanded::add);
                }
            }
        }
        return new Pair<>(aExpanded, bExpanded);
    }

    public static HyperSequenceTreeTrace postProcessExpansion(HyperSequenceTreeTrace a, HyperSequenceTreeTrace b, HyperSequenceTreeTrace superseq) {
        // todo: implement
        return superseq;
    }

    private static HyperSequenceTreeTrace.Element mergeElements(HyperSequenceTreeTrace.Element a, HyperSequenceTreeTrace.Element b) {
        var taxa = BitSetUtils.copy(a.taxa());
        taxa.or(b.taxa());

        var merged = new HyperSequenceTreeTrace.Element(taxa);
        merged.mergeMetadata(a);
        merged.mergeMetadata(b);
        merged.restrictToTaxa(taxa);
        return merged;
    }

    private static HyperSequenceTreeTrace.Element restrictElement(HyperSequenceTreeTrace.Element element, BitSet taxa) {
        var restricted = element.copy();
        restricted.taxa().clear();
        restricted.taxa().or(taxa);
        restricted.restrictToTaxa(taxa);
        return restricted;
    }

    static BitSet bitSetOf(int... values) {
        BitSet b = new BitSet();
        for (int v : values) {
            b.set(v);
        }
        return b;
    }

    private static void transferOverlapTreeInfo(HyperSequenceTreeTrace.Element current,
                                                HyperSequenceTreeTrace.Element next) {
        BitSet overlap = (BitSet) current.taxa().clone();
        overlap.and(next.taxa());

        for (int taxon = overlap.nextSetBit(0); taxon >= 0; taxon = overlap.nextSetBit(taxon + 1)) {
            BitSet currentTrees = current.treeIdsPerTaxon().get(taxon);
            if (currentTrees != null) {
                next.treeIdsPerTaxon()
                        .computeIfAbsent(taxon, k -> new BitSet())
                        .or((BitSet) currentTrees.clone());
            }
        }
    }

    public static void main(String[] args) {
        /**var a = new HyperSequenceWithTreeInfo();
        a.add(new HyperSequenceWithTreeInfo.Element(bitSetOf(6), Map.of(6, bitSetOf(1))));
        a.add(new HyperSequenceWithTreeInfo.Element(bitSetOf(3), Map.of(3, bitSetOf(1, 2))));
        a.add(new HyperSequenceWithTreeInfo.Element(bitSetOf(2), Map.of(2, bitSetOf(2))));
        a.add(new HyperSequenceWithTreeInfo.Element(bitSetOf(8), Map.of(8, bitSetOf(1, 2, 3))));
        // 4(2),5(1,2)
        a.add(new HyperSequenceWithTreeInfo.Element(bitSetOf(4, 5), Map.of(4, bitSetOf(2), 5, bitSetOf(1, 2))));
        a.add(new HyperSequenceWithTreeInfo.Element(bitSetOf(7), Map.of(7, bitSetOf(3))));

        var b = new HyperSequenceWithTreeInfo(new ArrayList<>());
        b.add(new HyperSequenceWithTreeInfo.Element(bitSetOf(6), Map.of(6, bitSetOf(2))));
        // 2(1-3),4(1),8(2-4)
        b.add(new HyperSequenceWithTreeInfo.Element(bitSetOf(2, 4, 8), Map.of(2, bitSetOf(1, 2, 3), 4, bitSetOf(1), 8, bitSetOf(2, 3, 4))));
        b.add(new HyperSequenceWithTreeInfo.Element(bitSetOf(5), Map.of(5, bitSetOf(1, 2))));
        b.add(new HyperSequenceWithTreeInfo.Element(bitSetOf(7), Map.of(7, bitSetOf(3))));
        b.add(new HyperSequenceWithTreeInfo.Element(bitSetOf(3), Map.of(3, bitSetOf(1))));**/

        var a = new HyperSequenceTreeTrace();
        a.add(new HyperSequenceTreeTrace.Element(bitSetOf(2,3,5), Map.of(2, bitSetOf(1,3), 3, bitSetOf(2), 5, bitSetOf(1,2))));
        a.add(new HyperSequenceTreeTrace.Element(bitSetOf(4), Map.of(4, bitSetOf(2))));
        a.add(new HyperSequenceTreeTrace.Element(bitSetOf(8), Map.of(8, bitSetOf(1,3))));
        a.add(new HyperSequenceTreeTrace.Element(bitSetOf(9), Map.of(9, bitSetOf(3))));

        var b = new HyperSequenceTreeTrace();
        b.add(new HyperSequenceTreeTrace.Element(bitSetOf(6), Map.of(6, bitSetOf(2))));
        b.add(new HyperSequenceTreeTrace.Element(bitSetOf(2), Map.of(2, bitSetOf(1))));
        b.add(new HyperSequenceTreeTrace.Element(bitSetOf(3), Map.of(3, bitSetOf(2,3))));
        b.add(new HyperSequenceTreeTrace.Element(bitSetOf(4,5), Map.of(4, bitSetOf(1), 5, bitSetOf(2))));
        b.add(new HyperSequenceTreeTrace.Element(bitSetOf(8), Map.of(8, bitSetOf(1,2))));

        System.err.println("Input:");
        System.err.println("a = " + a);
        System.err.println("b = " + b);

        var aligned = SCSTreeTrace.align(a, b);

        System.err.println("SCS = " + aligned);

        /**for (int i = 0; i < aligned.size(); i++) {
            var element = aligned.getElement(i);
            BitSet taxa = element.taxa();
            for (int taxon = taxa.nextSetBit(0); taxon >= 0; taxon = taxa.nextSetBit(taxon + 1)) {
                var trees = element.treeIdsPerTaxon().get(taxon);

                System.err.println("  taxon " + taxon + " -> trees " + trees);
            }
        }**/
    }
}
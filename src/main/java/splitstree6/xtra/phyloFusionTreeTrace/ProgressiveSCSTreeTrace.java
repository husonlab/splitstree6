package splitstree6.xtra.phyloFusionTreeTrace;

import jloda.graph.NodeArray;
import jloda.util.CanceledException;
import jloda.util.progress.ProgressSilent;
import splitstree6.algorithms.distances.distances2trees.UPGMA;
import splitstree6.data.DistancesBlock;
import splitstree6.data.TaxaBlock;

import java.util.ArrayList;
import java.util.Map;

import static splitstree6.xtra.phyloFusionTreeTrace.SCSTreeTrace.bitSetOf;

/**
 * pairwise shortest common hyper-sequence heuristic with tree metadata
 */
public class ProgressiveSCSTreeTrace {
    /**
     * run the progressive SCS heuristic
     *
     * @param hyperSequences input hyper sequences
     * @return shortest common hyper sequence
     */
    public static HyperSequenceTreeTrace apply(ArrayList<HyperSequenceTreeTrace> hyperSequences) {
        if (hyperSequences.size() == 1)
            return hyperSequences.get(0);
        else if (hyperSequences.size() == 2) {
            var expanded = SCSTreeTrace.preProcessExpansion(hyperSequences.get(0), hyperSequences.get(1));
            return SCSTreeTrace.align(expanded.getFirst(), expanded.getSecond());
        } else if (hyperSequences.size() == 3) {
            var one = SCSTreeTrace.align(hyperSequences.get(0), hyperSequences.get(1));
            return SCSTreeTrace.align(one, hyperSequences.get(2));
        }

        // setup distances for UPGMA
        var taxa = new TaxaBlock();
        for (var t = 0; t < hyperSequences.size(); t++) {
            taxa.addTaxonByName("s" + t);
        }
        var distancesBlock = new DistancesBlock();
        distancesBlock.setNtax(taxa.getNtax());

        for (var i = 1; i <= taxa.getNtax(); i++) {
            var si = hyperSequences.get(i - 1);
            for (var j = i + 1; j <= taxa.getNtax(); j++) {
                var sj = hyperSequences.get(j - 1);
                var aligned = SCSTreeTrace.align(si, sj);
                var minLength = Math.min(si.size(), sj.size());
                var d = (double) (aligned.size() - minLength) / (double) minLength;
                distancesBlock.set(i, j, d);
                distancesBlock.set(j, i, d);
            }
        }

        try {
            var tree = UPGMA.computeUPGMATree(new ProgressSilent(), taxa, distancesBlock);

            // progressive alignment up tree:
            try (NodeArray<HyperSequenceTreeTrace> mhs = tree.newNodeArray()) {
                tree.postorderTraversal(u -> {
                    if (u.isLeaf()) {
                        var sequence = hyperSequences.get(tree.getTaxon(u) - 1);
                        mhs.put(u, sequence);
                    } else {
                        var v = u.getFirstOutEdge().getTarget();
                        var w = u.getLastOutEdge().getTarget();
                        mhs.put(u, SCSTreeTrace.align(mhs.get(v), mhs.get(w)));
                    }
                });
                return mhs.get(tree.getRoot());
            }
        } catch (CanceledException ignored) {
            // can't happen
            return null;
        }
    }

    public static void main(String[] args) {
        /**var sequences = new ArrayList<HyperSequenceTreeTrace>();
        var s1 = new HyperSequenceTreeTrace();
        var e1 = new HyperSequenceTreeTrace.Element(jloda.util.BitSetUtils.asBitSet(1));
        e1.addTree(1, 1);
        s1.add(e1);
        sequences.add(s1);

        var s2 = new HyperSequenceTreeTrace();
        var e2 = new HyperSequenceTreeTrace.Element(jloda.util.BitSetUtils.asBitSet(1, 2));
        e2.addTree(1, 2);
        e2.addTree(2, 2);
        s2.add(e2);
        sequences.add(s2);

        var s3 = new HyperSequenceTreeTrace();
        var e3 = new HyperSequenceTreeTrace.Element(jloda.util.BitSetUtils.asBitSet(2));
        e3.addTree(2, 3);
        s3.add(e3);
        sequences.add(s3);**/

        var sequences = new ArrayList<HyperSequenceTreeTrace>();

        var s1 = new HyperSequenceTreeTrace();
        var e1 = new HyperSequenceTreeTrace.Element(jloda.util.BitSetUtils.asBitSet(4));
        e1.addTree(4, 1);
        s1.add(e1);
        var e2 = new HyperSequenceTreeTrace.Element(jloda.util.BitSetUtils.asBitSet(2));
        e2.addTree(2, 1);
        s1.add(e2);
        sequences.add(s1);

        var s2 = new HyperSequenceTreeTrace();
        var e3 = new HyperSequenceTreeTrace.Element(jloda.util.BitSetUtils.asBitSet(5));
        e3.addTree(5, 2);
        s2.add(e3);
        var e4 = new HyperSequenceTreeTrace.Element(jloda.util.BitSetUtils.asBitSet(6));
        e4.addTree(6, 2);
        s2.add(e4);
        var e5 = new HyperSequenceTreeTrace.Element(jloda.util.BitSetUtils.asBitSet(4));
        e5.addTree(4, 2);
        s2.add(e5);
        var e6 = new HyperSequenceTreeTrace.Element(jloda.util.BitSetUtils.asBitSet(3));
        e6.addTree(3, 2);
        s2.add(e6);
        var e7 = new HyperSequenceTreeTrace.Element(jloda.util.BitSetUtils.asBitSet(2));
        e7.addTree(2, 2);
        s2.add(e7);
        sequences.add(s2);

        var s3 = new HyperSequenceTreeTrace();
        var e8 = new HyperSequenceTreeTrace.Element(jloda.util.BitSetUtils.asBitSet(4));
        e8.addTree(4, 3);
        s3.add(e8);
        var e9 = new HyperSequenceTreeTrace.Element(jloda.util.BitSetUtils.asBitSet(2));
        e9.addTree(2, 3);
        s3.add(e9);
        var e10 = new HyperSequenceTreeTrace.Element(jloda.util.BitSetUtils.asBitSet(3));
        e10.addTree(3, 3);
        s3.add(e10);
        var e11 = new HyperSequenceTreeTrace.Element(jloda.util.BitSetUtils.asBitSet(5));
        e11.addTree(5, 3);
        s3.add(e11);
        sequences.add(s3);

        System.err.println("Input ");
        for (int i = 0; i < sequences.size(); i++) {
            System.err.println("HyperSequence " + i + ": " + sequences.get(i));
        }

        var result = apply(sequences);
        System.err.println(result);

        /**System.err.println("Aligned elements with tree info:");
        for (int i = 0; i < result.size(); i++) {
            var element = result.getElement(i);
            System.err.println("Element " + i);
            var taxa = element.taxa();
            for (int taxon = taxa.nextSetBit(0); taxon >= 0; taxon = taxa.nextSetBit(taxon + 1)) {
                System.err.println("  taxon " + taxon + " -> trees " + element.treeIdsPerTaxon().get(taxon));
            }
        }**/
    }
}
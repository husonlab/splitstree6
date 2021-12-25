/*
 * OutlineCircularNetwork.java Copyright (C) 2021. Daniel H. Huson
 *
 * (Some code written by other authors, as named in code.)
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

package splitstree6.view.splits.layout.algorithms;

import javafx.geometry.Point2D;
import jloda.fx.util.GeometryUtilsFX;
import jloda.graph.Edge;
import jloda.graph.Node;
import jloda.graph.NodeArray;
import jloda.phylo.PhyloSplitsGraph;
import jloda.util.BitSetUtils;
import jloda.util.CanceledException;
import jloda.util.progress.ProgressListener;
import splitstree6.algorithms.utils.PhyloGraphUtils;
import splitstree6.algorithms.utils.SplitsUtilities;
import splitstree6.data.SplitsBlock;
import splitstree6.data.TaxaBlock;
import splitstree6.data.parts.ASplit;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.HashMap;
import java.util.function.Function;

/**
 * runs the outline algorithm due to Bryant and Huson, 2021
 * Daniel Huson, 1.2020
 */
public class Outline {
    /**
     * apply the algorithm to build a new graph
     *
     * @param progress
     * @param useWeights
     * @param taxaBlock
     * @param splits
     * @param graph
     * @param nodePointMap
     */
    public static void apply(ProgressListener progress, boolean useWeights, TaxaBlock taxaBlock, SplitsBlock splits,
                             PhyloSplitsGraph graph, NodeArray<Point2D> nodePointMap, BitSet usedSplits,
                             ArrayList<ArrayList<Node>> loops, boolean rooted) throws CanceledException {
        progress.setTasks("Outline", null);

        if (nodePointMap == null)
            nodePointMap = graph.newNodeArray();

        loops.clear();

        final var splits2node = new HashMap<BitSet, Node>();

        final var origNSplits = splits.getNsplits();
        addAllTrivial(taxaBlock.getNtax(), splits); // these will be removed again

        try {
            final var cycle = splits.getCycle();
            //final int[] cycle=SplitsUtilities.normalizeCycle(splits.getCycle());
            final var split2angle = EqualAngle.assignAnglesToSplits(taxaBlock.getNtax(), splits, splits.getCycle(), rooted ? 160 : 360);

            final ArrayList<Event> events;
            {
                final var outbound = new ArrayList<Event>();
                final var inbound = new ArrayList<Event>();

                for (var s = 1; s <= splits.getNsplits(); s++) {
                    final var split = splits.get(s);
                    if (split.isTrivial() || SplitsUtilities.isCircular(taxaBlock, cycle, split)) {
                        outbound.add(new Event(Event.Type.outbound, s, cycle, split));
                        inbound.add(new Event(Event.Type.inbound, s, cycle, split));
                        if (s <= origNSplits)
                            usedSplits.set(s, true);
                    }
                }
                events = Event.radixSort(taxaBlock.getNtax(), outbound, inbound);
            }

            final var currentSplits = new BitSet();
            var location = new Point2D(0, 0);
            final var start = graph.newNode();
            nodePointMap.put(start, new Point2D(location.getX(), location.getY()));

            splits2node.put(new BitSet(), start);

            Event previousEvent = null;

            progress.setMaximum((int) (1.1 * events.size()));

            // System.err.println("Algorithm:");
            // System.err.println("Start: " + start.getId());

            final BitSet taxaFound = new BitSet();

            var previousNode = start;
            for (var event : events) {
                // System.err.println(event);

                if (event.isStart()) {
                    currentSplits.set(event.getS(), true);
                    location = GeometryUtilsFX.translateByAngle(location, split2angle[event.getS()], useWeights ? event.getWeight() : 1);
                } else {
                    currentSplits.set(event.getS(), false);
                    location = GeometryUtilsFX.translateByAngle(location, split2angle[event.getS()] + 180, useWeights ? event.getWeight() : 1);
                }

                final var mustCreateNode = (splits2node.get(currentSplits) == null);
                final Node v;
                if (mustCreateNode) {
                    v = graph.newNode();
                    splits2node.put(BitSetUtils.copy(currentSplits), v);
                    nodePointMap.put(v, new Point2D(location.getX(), location.getY()));
                } else {
                    v = splits2node.get(currentSplits);
                    location = nodePointMap.get(v);
                }
                // System.err.println("Node: " + v.getId());

                if (!v.isAdjacent(previousNode)) {
                    final var e = graph.newEdge(previousNode, v);
                    graph.setSplit(e, event.getS());
                    graph.setWeight(e, useWeights ? event.getWeight() : 1);
                    graph.setAngle(e, split2angle[event.getS()]);

                    if (!mustCreateNode) // just closed loop
                    {
                        loops.add(createLoop(v, e));
                    }
                }

                if (previousEvent != null) {
                    if (event.getS() == previousEvent.getS()) {
                        for (int t : BitSetUtils.members(splits.get(event.getS()).getPartNotContaining(cycle[1]))) {
                            graph.addTaxon(previousNode, t);
                            taxaFound.set(t);
                        }
                    }
                }

                previousNode = v;
                previousEvent = event;

                progress.incrementProgress();
            }

            for (var t = 1; t <= taxaBlock.getNtax(); t++) {
                if (!taxaFound.get(t))
                    graph.addTaxon(start, t);
            }

            {
                final var edgesToRemove = new ArrayList<Edge>();
                final var splitsToRemove = new BitSet();
                for (var e : graph.edges()) {
                    final var s = graph.getSplit(e);
                    if (s > origNSplits) {
                        edgesToRemove.add(e);
                        splitsToRemove.set(s);
                    }
                }
                for (var e : edgesToRemove) {
                    final var leaf = (e.getSource().getDegree() == 1 ? e.getSource() : e.getTarget());
                    final var other = (e.getSource().getDegree() == 1 ? e.getTarget() : e.getSource());
                    //System.err.println("Removing node: " + leaf.getId() + " with taxa " + Basic.toString(graph.getTaxa(leaf), ","));
                    final var taxa = new ArrayList<Integer>();
                    for (int t : graph.getTaxa(leaf))
                        taxa.add(t);
                    graph.deleteNode(leaf);
                    for (int t : taxa)
                        graph.addTaxon(other, t);
                }
                if (splitsToRemove.cardinality() != splits.getNsplits() - origNSplits)
                    throw new IllegalArgumentException("splits to remove " + splitsToRemove.cardinality() + " should be " + (splits.getNsplits() - origNSplits));
            }

            progress.setMaximum(100);
            progress.setProgress(90);

            PhyloGraphUtils.addLabels(taxaBlock, graph);
            progress.setProgress(100);   //set progress to 100%

            if (false) {
                for (var v : graph.nodes()) {
                    // if (graph.getLabel(v) != null)
                    System.err.println("Node " + v.getId() + " " + graph.getLabel(v) + " point: " + nodePointMap.get(v));
                }
                for (var e : graph.edges()) {
                    System.err.println("Edge " + e.getSource().getId() + " - " + e.getTarget().getId() + " split: " + graph.getSplit(e));
                }
            }
        } finally {
            splits.getSplits().subList(origNSplits, splits.getNsplits()).clear(); // this is 0-based
        }
    }

    /**
     * determines loop that is closed by reentering v
     *
     * @param v
     * @param inEdge
     * @return
     */
    private static ArrayList<Node> createLoop(Node v, Edge inEdge) {
        final var loop = new ArrayList<Node>();
        var w = v;
        var e = inEdge;
        do {
            loop.add(w);
            w = e.getOpposite(w);
            e = w.getNextAdjacentEdgeCyclic(e);
        }
        while (w != v);
        return loop;
    }

    private static void addAllTrivial(int ntaxa, SplitsBlock splits) {
        if (ntaxa > 2) {
            final var taxaWithTrivialSplit = new BitSet();

            for (var s = 1; s <= splits.getNsplits(); s++) {
                final var split = splits.get(s);
                if (split.isTrivial())
                    taxaWithTrivialSplit.set(split.getSmallerPart().nextSetBit(0));
            }
            for (var t = taxaWithTrivialSplit.nextClearBit(1); t != -1 && t <= ntaxa; t = taxaWithTrivialSplit.nextClearBit(t + 1)) {
                splits.getSplits().add(new ASplit(BitSetUtils.asBitSet(t), ntaxa, 0.0001));
            }
        }
    }

    static class Event {
        enum Type {outbound, inbound}

        private final double weight;
        private int iPos;
        private int jPos;
        private final int s;
        private final Type type;

        public Event(Type type, int s, int[] cycle, ASplit split) {
            this.type = type;
            this.s = s;
            this.weight = split.getWeight();
            int firstInCycle = cycle[1];

            iPos = Integer.MAX_VALUE;
            jPos = Integer.MIN_VALUE;
            final BitSet farSide = split.getPartNotContaining(firstInCycle);
            for (var i = 0; i < cycle.length; i++) {
                final var t = cycle[i];
                if (t > 0 && farSide.get(t)) {
                    iPos = Math.min(iPos, i);
                    jPos = Math.max(jPos, i);
                }
            }
        }

        public int getS() {
            return s;
        }

        private int getIPos() {
            return iPos;
        }

        private int getJPos() {
            return jPos;
        }

        public double getWeight() {
            return weight;
        }

        public boolean isStart() {
            return type == Type.outbound;
        }

        public boolean isEnd() {
            return type == Type.inbound;
        }

        public String toString() {
            return type.name() + " S" + s + " (" + iPos + "-" + jPos + ")"; //: " + Basic.toString(split.getPartNotContaining(firstInCycle), ",");
        }

        public static ArrayList<Event> radixSort(int ntax, ArrayList<Event> outbound, ArrayList<Event> inbound) {
            countingSort(outbound, ntax, a -> ntax - a.getJPos());
            countingSort(outbound, ntax, Event::getIPos);
            countingSort(inbound, ntax, a -> ntax - a.getIPos());
            countingSort(inbound, ntax, Event::getJPos);

            return merge(outbound, inbound);
        }

        private static void countingSort(ArrayList<Event> events, int maxKey, Function<Event, Integer> key) {
            if (events.size() > 1) {
                final var key2pos = new int[maxKey + 1];
                // count keys
                for (var event : events) {
                    final int value = key.apply(event);
                    key2pos[value]++;
                }

                // set positions
                {
                    var pos = 0;
                    for (var i = 0; i < key2pos.length; i++) {
                        final var add = key2pos[i];
                        key2pos[i] = pos;
                        pos += add;
                    }
                }

                final var other = new Event[events.size()];

                // insert at positions:
                for (var event : events) {
                    final var k = key.apply(event);
                    final var pos = key2pos[k]++;
                    other[pos] = event;
                }

                // copy to result:
                events.clear();
                Collections.addAll(events, other);
            }
        }

        private static ArrayList<Event> merge(ArrayList<Event> outbound, ArrayList<Event> inbound) {
            final var events = new ArrayList<Event>(outbound.size() + inbound.size());

            var ob = 0;
            var ib = 0;
            while (ob < outbound.size() && ib < inbound.size()) {
                if (outbound.get(ob).getIPos() < inbound.get(ib).getJPos() + 1) {
                    events.add(outbound.get(ob++));
                } else {
                    events.add(inbound.get(ib++));
                }
            }
            while (ob < outbound.size())
                events.add(outbound.get(ob++));
            while (ib < inbound.size())
                events.add(inbound.get(ib++));

            return events;
        }
    }
}

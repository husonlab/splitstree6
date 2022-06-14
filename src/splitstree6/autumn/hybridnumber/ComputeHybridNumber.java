/*
 * ComputeHybridNumber.java Copyright (C) 2022 Daniel H. Huson
 *
 * (Some files contain contributions from other authors, who are then mentioned separately.)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package splitstree6.autumn.hybridnumber;

import jloda.phylo.PhyloTree;
import jloda.util.*;
import jloda.util.progress.ProgressListener;
import org.apache.commons.collections4.map.LRUMap;
import splitstree6.autumn.*;
import splitstree6.data.TaxaBlock;

import java.io.IOException;
import java.util.BitSet;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledThreadPoolExecutor;

/**
 * computes the hybrid number of two multifurcating trees
 * Daniel Huson, 4.2011
 */
public class ComputeHybridNumber {
    final public static int LARGE = 1000;
    public static final boolean checking = false;

    final private LRUMap lookupTable = new LRUMap(5000000);
    final private ProgressListener progressListener;

    private long startTime = 0;
    private long nextTime = 0;
    private long waitTime = 1000;

    private final Value bestScore = new Value(LARGE);

    private boolean initialized = false;

    boolean silent = false;

    final private int additionalThreads;
    final private ScheduledThreadPoolExecutor scheduledThreadPoolExecutor;

    /**
     * constructor
     *
	 */
    ComputeHybridNumber(ProgressListener progressListener) {
        this.progressListener = progressListener;

        additionalThreads = Math.max(1, ProgramProperties.get("additional-threads", Runtime.getRuntime().availableProcessors() - 1));
        scheduledThreadPoolExecutor = new ScheduledThreadPoolExecutor(additionalThreads);
    }

    /**
     * computes the hybrid number for two multi-furcating trees
     *
     * @return hybrid number
     */
    public static int apply(PhyloTree tree1, PhyloTree tree2, ProgressListener progressListener) throws IOException {
        progressListener.setTasks("Computing hybrid number", "(Unknown how long this will really take)");
        ComputeHybridNumber computeHybridNumber = new ComputeHybridNumber(progressListener);
        computeHybridNumber.run(tree1, tree2, new TaxaBlock());
        return computeHybridNumber.done();
    }

    /**
     * computes the hybrid number for two multifurcating trees
     *
     * @return hybrid number
     */
    public static int apply(PhyloTree tree1, PhyloTree tree2, ProgressListener progressListener, int bestScore) throws IOException {
        ComputeHybridNumber computeHybridNumber = new ComputeHybridNumber(progressListener);
        computeHybridNumber.bestScore.set(bestScore);
        computeHybridNumber.run(tree1, tree2, new TaxaBlock());
        return computeHybridNumber.done();
    }

    /**
     * run the algorithm. This can be reentered by rerootings of the same two trees
     *
     * @return reduced trees
     */
    int run(PhyloTree tree1, PhyloTree tree2, TaxaBlock allTaxa) throws IOException {
        if (!initialized) {
            initialized = true;
            progressListener.setMaximum(20);
            progressListener.setProgress(0);
            startTime = System.currentTimeMillis();
            nextTime = this.startTime + waitTime;
        }

        if (bestScore.get() == LARGE) { // no upper bound given, use cluster network
            System.err.print("Computing upper bound using cluster network: ");
            int upperBound = Utilities.getNumberOfReticulationsInClusterNetwork(tree1, tree2);
            System.err.println(upperBound);
            bestScore.set(upperBound);
        }

        Pair<Root, Root> roots = PreProcess.apply(tree1, tree2, allTaxa);
        Root root1 = roots.getFirst();
        Root root2 = roots.getSecond();

        BitSet onlyTree1 = Cluster.setminus(root1.getTaxa(), root2.getTaxa());
        BitSet onlyTree2 = Cluster.setminus(root2.getTaxa(), root1.getTaxa());

        if (root1.getTaxa().cardinality() == onlyTree1.cardinality())
            throw new IOException("None of the taxa in tree2 are contained in tree1");
        if (root2.getTaxa().cardinality() == onlyTree2.cardinality())
            throw new IOException("None of the taxa in tree1 are contained in tree2");

        if (onlyTree1.cardinality() > 0) {
            if (!silent)
                System.err.println("Killing all taxa only present in tree1: " + onlyTree1.cardinality());
            for (int t = onlyTree1.nextSetBit(0); t != -1; t = onlyTree1.nextSetBit(t + 1)) {
                BitSet one = new BitSet();
                one.set(t);
                root1 = CopyWithTaxaRemoved.apply(root1, one);
            }
        }

        if (onlyTree2.cardinality() > 0) {
            if (!silent)
                System.err.println("Killing all taxa only present in tree2: " + onlyTree2.cardinality());
            for (int t = onlyTree2.nextSetBit(0); t != -1; t = onlyTree2.nextSetBit(t + 1)) {
                BitSet one = new BitSet();
                one.set(t);
                root2 = CopyWithTaxaRemoved.apply(root2, one);
            }
        }

        if (!root1.getTaxa().equals(root2.getTaxa()))
            throw new IOException("Trees have unequal taxon sets (even after killing)");

        // run the refine algorithm
        if (!silent)
            System.err.println("Computing common refinement of both trees");
        Refine.apply(root1, root2);

        if (true) {
            System.err.println(root1.toStringTree());
            System.err.println(root2.toStringTree());
        }

        if (tree1.getRoot() == null || tree2.getRoot() == null) {
            throw new IOException("Can't compute hybrid number, at least one of the trees is empty or unrooted");
        }

        // we maintain both trees in lexicographic order for ease of comparison
        root1.reorderSubTree();
        root2.reorderSubTree();

        if (!silent)
            System.err.println("Computing hybridization number using Autumn algorithm...");
        if (!silent)
            System.err.println("(Number of worker threads: " + (additionalThreads + 1) + ")");

        int result = computeHybridNumberRec(root1, root2, false, null, null, true, 0, new ValuesList());
        if (!silent)
            System.err.println("(Result: " + result + ")");
        if (!silent)
            System.err.println("Hybridization number: " + bestScore.get());
        if (bestScore.get() > result)
            throw new IOException("bestScore > result: " + bestScore.get() + " " + result);

        return bestScore.get();
    }

    /**
     * close down the thread pool and collect garbage
     *
     * @return best score
     */
    protected int done() {
        System.err.println("Best score: " + bestScore.get());
        System.err.println("Time: " + ((System.currentTimeMillis() - startTime) / 1000) + " secs");
        System.err.println("(Size of lookup table: " + lookupTable.size() + ")");
        lookupTable.clear();
        scheduledThreadPoolExecutor.shutdown();
        System.gc();
        return bestScore.get();
    }


    /**
     * recursively compute the hybrid number
     *
     * @param isReduced       @return hybrid number
	 */
    private int computeHybridNumberRec(final Root root1, final Root root2, boolean isReduced, Integer previousHybrid, BitSet retry, final boolean topLevel, final int scoreAbove, final ValuesList additionalAbove) throws IOException {
        if (System.currentTimeMillis() > nextTime) {
            synchronized (progressListener) {
                nextTime += waitTime;
                waitTime *= 1.5;
                progressListener.incrementProgress();
            }
        } else
            progressListener.checkForCancel();

        // System.err.println("computeHybridNumberRec: tree1=" + Basic.toString(root1.getTaxa()) + " tree2=" + Basic.toString(root2.getTaxa()));
        // root1.reorderSubTree();
        //  root2.reorderSubTree();
        if (checking) {
            root1.checkTree();
            root2.checkTree();
        }

        BitSet taxa = root1.getTaxa();

        String key = root1.toStringTreeSparse() + root2.toStringTreeSparse();
        // System.err.println("Key: "+key);
        Integer value;
        synchronized (lookupTable) {
            value = (Integer) lookupTable.get(key);
            if (value != null)
                return value;
        }

        if (!root2.getTaxa().equals(taxa))
            throw new RuntimeException("Unequal taxon sets: X=" + StringUtils.toString(root1.getTaxa()) + " vs " + StringUtils.toString(root2.getTaxa()));
        if (!isReduced) {
            switch (SubtreeReduction.apply(root1, root2, null)) {
                case ISOMORPHIC:
                    synchronized (lookupTable) {
                        lookupTable.put(key, 0);
                    }
                    if (topLevel) {
                        bestScore.lowerTo(0);
                        progressListener.setSubtask("Best score: " + bestScore);
                    }
                    return 0; // two trees are isomorphic, no hybrid node needed
                case REDUCED:  // a reduction was performed, cannot maintain lexicographical ordering in removal loop below
                    previousHybrid = null;
                    break;
                case IRREDUCIBLE:
                    break;
            }

            Single<Integer> placeHolderTaxa = new Single<>();
            final Pair<Root, Root> clusterTrees = ClusterReduction.apply(root1, root2, placeHolderTaxa);
            final boolean retryTop = false && (previousHybrid != null && placeHolderTaxa.get() < previousHybrid);
            // if the taxa involved in the cluster reduction come before the previously removed hybrid, do full retry
            // retryTop doesn't work
            final BitSet fRetry = retry;

            if (clusterTrees != null) // will perform cluster-reduction
            {
                final Value score1 = new Value(0);
                final Value score2 = new Value(1);  // because the cluster could not be reduced using an subtree reduction, can assume that we will need one reticulation for this

                final boolean verbose = ProgramProperties.get("verbose-HL-parallel", false);
                if (verbose)
                    System.err.println("Starting parallel loop");

                final CountDownLatch countDownLatch = new CountDownLatch(2);
                final Integer fPrevious = previousHybrid;

                // create task:
                final var task1 = new splitstree6.autumn.Task(() -> {
                    try {
                        if (verbose) {
                            System.err.println("Launching thread on cluster-reduction");
                            System.err.println("Active threads " + scheduledThreadPoolExecutor.getActiveCount());
                        }
                        final ValuesList additionalAbove1 = additionalAbove.copyWithAdditionalElement(score2);
                        if (scoreAbove + additionalAbove1.sum() < bestScore.get()) {
                            int h = computeHybridNumberRec(root1, root2, false, fPrevious, fRetry, false, scoreAbove, additionalAbove1);
                            score1.set(h);
                        } else {
                            score1.set(LARGE);
                        }
                        additionalAbove1.clear();
                    } catch (Exception ex) {
                        while (countDownLatch.getCount() > 0)
                            countDownLatch.countDown();
                    }
                    countDownLatch.countDown();
                });

                final var task2 = new splitstree6.autumn.Task(() -> {
                    try {
                        if (verbose) {
                            System.err.println("Launching thread on cluster-reduction");
                            System.err.println("Active threads " + scheduledThreadPoolExecutor.getActiveCount());
                        }
                        final ValuesList additionalAbove2 = additionalAbove.copyWithAdditionalElement(score1);
                        if (scoreAbove + additionalAbove2.sum() < bestScore.get()) {
                            int h = computeHybridNumberRec(clusterTrees.getFirst(), clusterTrees.getSecond(), true, fPrevious, fRetry, false, scoreAbove, additionalAbove2);
                            score2.set(h);
                        } else {
                            score2.set(LARGE);
                        }
                        additionalAbove2.clear();
                    } catch (Exception ex) {
                        while (countDownLatch.getCount() > 0)
                            countDownLatch.countDown();
                    }
                    countDownLatch.countDown();
                });

                // start a task in this thread
                scheduledThreadPoolExecutor.execute(task1);
                task2.run();
                task1.run(); // try to run task1 in current thread if it hasn't yet started execution. If the task is already running or has completed, will simply return

                try {
                    if (verbose)
                        System.err.println("waiting...");
                    // wait until all tasks have completed
                    countDownLatch.await();
                    if (verbose)
                        System.err.println("done");
                } catch (InterruptedException e) {
                    Basic.caught(e);
                }

                clusterTrees.getFirst().deleteSubTree();
                clusterTrees.getSecond().deleteSubTree();

                int total = scoreAbove + additionalAbove.sum() + score1.get() + score2.get();

                if (topLevel && (total < bestScore.get()))    // score above will be zero, but put this here anyway to avoid confusion
                {
                    bestScore.lowerTo(total);
                    progressListener.setSubtask("Current best score: " + bestScore);
                }

                synchronized (lookupTable) {
                    Integer old = (Integer) lookupTable.get(key);
                    if (old == null || total < old)
                        lookupTable.put(key, total);
                }
                return score1.get() + score2.get();
            }
        }

        List<Root> leaves1 = root1.getAllLeaves();

        if (leaves1.size() <= 2) // try 2 rather than one...
        {
            return 0;
        }

        final boolean verbose = ProgramProperties.get("verbose-HL-parallel", false);
        if (verbose)
            System.err.println("Starting parallel loop");

        final CountDownLatch countDownLatch = new CountDownLatch(leaves1.size());

        final Value bestSubH = new Value(LARGE);

        // schedule all tasks to be performed
        final var queue = new ConcurrentLinkedQueue<splitstree6.autumn.Task>();

        for (var leaf2remove : leaves1) {
            final BitSet taxa2remove = leaf2remove.getTaxa();

            if (previousHybrid == null || previousHybrid < taxa2remove.nextSetBit(0)) {

                if (scoreAbove + additionalAbove.sum() + 1 >= bestScore.get())
                    return LARGE;  // other thread has found a better result, abort

				// create task:
                final var task = new splitstree6.autumn.Task();
                task.setRunnable(() -> {
                    try {
                        if (verbose) {
                            System.err.println("Launching thread on " + StringUtils.toString(taxa2remove));
                            System.err.println("Active threads " + scheduledThreadPoolExecutor.getActiveCount());
                        }
                        queue.remove(task);
                        if (scoreAbove + additionalAbove.sum() + 1 < bestScore.get()) {
                            Root tree1X = CopyWithTaxaRemoved.apply(root1, taxa2remove);
                            Root tree2X = CopyWithTaxaRemoved.apply(root2, taxa2remove);

                            Refine.apply(tree1X, tree2X);

                            int scoreBelow = computeHybridNumberRec(tree1X, tree2X, false, taxa2remove.nextSetBit(0), null, false, scoreAbove + 1, additionalAbove) + 1;

                            if (topLevel && scoreBelow < bestScore.get()) {
                                bestScore.lowerTo(scoreBelow);
                                progressListener.setSubtask("Current best score: " + bestScore);
                            }

                            synchronized (bestSubH) {
                                if (scoreBelow < bestSubH.get())
                                    bestSubH.set(scoreBelow);
                            }

                            tree1X.deleteSubTree();
                            tree2X.deleteSubTree();
                        }
                    } catch (Exception ex) {
                        while (countDownLatch.getCount() > 0)
                            countDownLatch.countDown();
                    }
                    countDownLatch.countDown();
                });
                queue.add(task);
            } else // no task for this item, count down
            {
                countDownLatch.countDown();
                progressListener.checkForCancel();
            }
        }
        // grab one task for the current thread:
        var taskForCurrentThread = queue.size() > 0 ? queue.poll() : null;
        // launch all others in the executor
        for (var task : queue)
            scheduledThreadPoolExecutor.execute(task);

        // start a task in this thread
        if (taskForCurrentThread != null)
            taskForCurrentThread.run();

        // try to run other tasks from the queue. Note that any task that is already running will return immediately
        while (queue.size() > 0) {
            var task = queue.poll();
            if (task != null)
                task.run();
        }
        try {
            if (verbose)
                System.err.println("waiting...");
            // wait until all tasks have completed
            countDownLatch.await();

            if (verbose) System.err.println("done");
        } catch (InterruptedException e) {
            Basic.caught(e);
            return LARGE;
        }
        // return the best value
        synchronized (lookupTable) {
            var old = (Integer) lookupTable.get(key);
            if (old == null || old > bestSubH.get())
                lookupTable.put(key, bestSubH.get());
        }
        return bestSubH.get();
    }
}

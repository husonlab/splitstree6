package splitstree6.algorithms.distances.distances2splits.neighbornet.NeighborNetPCG;


import jloda.util.CanceledException;

public class NnetTest {
    public static void main(String[] args) throws CanceledException {
        //TridiagonalMatrix.test(100);
        //CircularSplitAlgorithms.test(10);
        //BlockXMatrix.test(12);
        long startTime = System.currentTimeMillis();
        NeighborNetBlockPivot.test(50);
        long finishTime = System.currentTimeMillis();
        System.err.println("Block Pivot took " + (finishTime - startTime) + " milliseconds");
    }
}

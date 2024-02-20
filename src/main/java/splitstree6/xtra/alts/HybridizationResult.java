package splitstree6.xtra.alts;

import java.util.*;

public class HybridizationResult {
    private final int hybridizationScore;
    private final Map<Integer, String> alignments;

    private final LinkedList<Integer> order;

    public HybridizationResult(int hybridizationScore, Map<Integer, String> alignments, LinkedList<Integer> order) {
        this.hybridizationScore = hybridizationScore;
        this.alignments = alignments;
        this.order = order;
    }

    public int getHybridizationScore() {
        return hybridizationScore;
    }
    public Map<Integer, String> getAlignments() {
        return Collections.unmodifiableMap(alignments);
    }
    public LinkedList<Integer> getOrder() {return order;}

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        HybridizationResult that = (HybridizationResult) o;
        return hybridizationScore == that.hybridizationScore &&
                Objects.equals(alignments, that.alignments);
    }
    @Override
    public int hashCode() {
        return Objects.hash(hybridizationScore, alignments);
    }
}

class HybridizationContext {
    public Set<HybridizationResult> hybridizationResultSet;
    public int numOfPermutations;
    public int minHybridizationScore;

    public HybridizationContext() {
        this.hybridizationResultSet = new HashSet<>();
        this.numOfPermutations = 0;
        this.minHybridizationScore = Integer.MAX_VALUE; // Assuming you want to minimize the score
    }
}
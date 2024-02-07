package splitstree6.xtra.alts;

import java.util.Map;

public class HybridizationResult {
    private final int hybridizationScore;
    private final Map<String, String> alignments;

    public HybridizationResult(int hybridizationScore, Map<String, String> alignments) {
        this.hybridizationScore = hybridizationScore;
        this.alignments = alignments;
    }



    public int getHybridizationScore() {
        return hybridizationScore;
    }

    public Map<String, String> getAlignments() {
        return alignments;
    }
}
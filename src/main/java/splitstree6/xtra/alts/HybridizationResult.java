package splitstree6.xtra.alts;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class HybridizationResult {
    private final int hybridizationScore;
    private final Map<String, String> alignments;

    private final List<String> order;

    public HybridizationResult(int hybridizationScore, Map<String, String> alignments, List<String> order) {
        this.hybridizationScore = hybridizationScore;
        this.alignments = alignments;
        this.order = order;
    }


    public int getHybridizationScore() {
        return hybridizationScore;
    }

    public Map<String, String> getAlignments() {
        return Collections.unmodifiableMap(alignments);
    }
    public List<String> getOrder() {return order;}

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
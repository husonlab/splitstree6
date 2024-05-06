/*
 *  HybridizationResult.java Copyright (C) 2024 Daniel H. Huson
 *
 *  (Some files contain contributions from other authors, who are then mentioned separately.)
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
 */

package splitstree6.xtra.alts;

import splitstree6.xtra.hyperstrings.HyperSequence;

import java.util.*;

public class HybridizationResult {
    private final int hybridizationScore;
    private final Map<Integer, HyperSequence> alignments;

    private final LinkedList<Integer> order;

    public HybridizationResult(int hybridizationScore, Map<Integer, HyperSequence> alignments, LinkedList<Integer> order) {
        this.hybridizationScore = hybridizationScore;
        this.alignments = alignments;
        this.order = order;
    }

    public int getHybridizationScore() {
        return hybridizationScore;
    }
    public Map<Integer, HyperSequence> getAlignments() {
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
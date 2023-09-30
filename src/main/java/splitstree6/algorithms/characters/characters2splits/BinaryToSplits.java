/*
 * BinaryToSplits.java Copyright (C) 2023 Daniel H. Huson
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
package splitstree6.algorithms.characters.characters2splits;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import jloda.util.BitSetUtils;
import jloda.util.StringUtils;
import jloda.util.progress.ProgressListener;
import splitstree6.algorithms.splits.splits2splits.DimensionFilter;
import splitstree6.algorithms.utils.SplitsBlockUtilities;
import splitstree6.data.CharactersBlock;
import splitstree6.data.SplitsBlock;
import splitstree6.data.TaxaBlock;
import splitstree6.splits.ASplit;
import splitstree6.data.parts.CharactersType;
import splitstree6.splits.Compatibility;

import java.io.IOException;
import java.util.BitSet;
import java.util.HashMap;
import java.util.List;

/**
 * computes splits from binary data
 * <p>
 * Daniel Huson, Feb 2004
 */
public class BinaryToSplits extends Characters2Splits {
    private final DoubleProperty optionMinSplitWeight = new SimpleDoubleProperty(this, "optionMinSplitWeight", 0.0);
    private final BooleanProperty optionHighDimensionFilter = new SimpleBooleanProperty(this, "optionHighDimensionFilter", true);
    private final BooleanProperty optionAddAllTrivial = new SimpleBooleanProperty(this, "optionAddAllTrivial", false);

    @Override
    public List<String> listOptions() {
        return List.of(optionMinSplitWeight.getName(), optionHighDimensionFilter.getName(), optionAddAllTrivial.getName());
    }

    /**
     * Applies the method to the given data
     */
    public void compute(ProgressListener progress, TaxaBlock taxaBlock, CharactersBlock chars, SplitsBlock splitsBlock) throws IOException {
        progress.setMaximum(chars.getNchar());    //initialize maximum progress
        progress.setProgress(0);

        var clusterCharactersMap = new HashMap<BitSet, BitSet>();

        for (int c = 1; c <= chars.getNchar(); c++) {
            // make one side of the split:
            var current = new BitSet();
            var stateTaxon1 = chars.get(1, c);
            for (int t = 1; t <= chars.getNtax(); t++) {
                if (chars.get(t, c) == stateTaxon1) current.set(t);
            }
            if (current.cardinality() < chars.getNtax()) {
                clusterCharactersMap.put(current, BitSetUtils.add(clusterCharactersMap.getOrDefault(current, new BitSet()), c));
            }
            progress.setProgress(c);
        }

        var computedSplits = new SplitsBlock();

        for (var cluster : clusterCharactersMap.keySet()) {
            var sites = clusterCharactersMap.get(cluster);

            var weight = sites.stream().mapToDouble(chars::getCharacterWeight).sum();
            if (weight >= getOptionMinSplitWeight()) {
                var split = new ASplit(cluster, taxaBlock.getNtax(), weight);
                split.setLabel(StringUtils.toString(sites));
                computedSplits.getSplits().add(split);
            }
        }

        // add all missing trivial
		computedSplits.getSplits().addAll(SplitsBlockUtilities.createAllMissingTrivial(computedSplits.getSplits(), taxaBlock.getNtax(), isOptionAddAllTrivial() ? 1.0 : 0.0));

        if (isOptionHighDimensionFilter()) {
            DimensionFilter.apply(progress, 4, computedSplits.getSplits(), splitsBlock.getSplits());
        } else
            splitsBlock.copy(computedSplits);

		splitsBlock.setCycle(SplitsBlockUtilities.computeCycle(taxaBlock.getNtax(), splitsBlock.getSplits()));
        splitsBlock.setFit(-1);
        splitsBlock.setCompatibility(Compatibility.compute(taxaBlock.getNtax(), splitsBlock.getSplits(), splitsBlock.getCycle()));
    }

    @Override
    public String getCitation() {
        return "Huson et al 2012;D.H. Huson, R. Rupp and C. Scornavacca, Phylogenetic Networks, Cambridge, 2012.";
    }

    @Override
    public boolean isApplicable(TaxaBlock taxaBlock, CharactersBlock parent) {
        return parent.getDataType() == CharactersType.Standard;
    }

    public double getOptionMinSplitWeight() {
        return optionMinSplitWeight.get();
    }

    public DoubleProperty optionMinSplitWeightProperty() {
        return optionMinSplitWeight;
    }

    public void setOptionMinSplitWeight(double optionMinSplitWeight) {
        this.optionMinSplitWeight.set(optionMinSplitWeight);
    }

    public boolean isOptionHighDimensionFilter() {
        return optionHighDimensionFilter.get();
    }

    public BooleanProperty optionHighDimensionFilterProperty() {
        return optionHighDimensionFilter;
    }

    public void setOptionHighDimensionFilter(boolean optionHighDimensionFilter) {
        this.optionHighDimensionFilter.set(optionHighDimensionFilter);
    }

    public boolean isOptionAddAllTrivial() {
        return optionAddAllTrivial.get();
    }

    public BooleanProperty optionAddAllTrivialProperty() {
        return optionAddAllTrivial;
    }

    public void setOptionAddAllTrivial(boolean optionAddAllTrivial) {
        this.optionAddAllTrivial.set(optionAddAllTrivial);
    }
}

/*
 * BinaryToSplits.java Copyright (C) 2022 Daniel H. Huson
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

import javafx.beans.property.*;
import jloda.util.BitSetUtils;
import jloda.util.StringUtils;
import jloda.util.progress.ProgressListener;
import splitstree6.algorithms.splits.splits2splits.DimensionFilter;
import splitstree6.algorithms.utils.SplitsUtilities;
import splitstree6.data.CharactersBlock;
import splitstree6.data.SplitsBlock;
import splitstree6.data.TaxaBlock;
import splitstree6.data.parts.ASplit;
import splitstree6.data.parts.Compatibility;

import java.io.IOException;
import java.util.BitSet;
import java.util.HashMap;
import java.util.List;

/**
 * computes splits from DNA characters
 * <p>
 * Daniel Huson, Feb 2004
 */
public class DnaToSplits extends Characters2Splits {
    public enum Method {
        MajorityState, RYAlphabet
    }

    private final ObjectProperty<Method> optionMethod = new SimpleObjectProperty<>(this, "optionMethod", Method.MajorityState);

    private final DoubleProperty optionMinSplitWeight = new SimpleDoubleProperty(this, "optionMinSplitWeight", 0.0);
    private final BooleanProperty optionHighDimensionFilter = new SimpleBooleanProperty(this, "optionHighDimensionFilter", true);

    @Override
    public List<String> listOptions() {
        return List.of(optionMethod.getName(), optionMinSplitWeight.getName(), optionHighDimensionFilter.getName());
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
            switch (getOptionMethod()) {
                case RYAlphabet -> {
                    var countR = 0;
                    var countY = 0;

                    for (int t = 1; t <= chars.getNtax(); t++) {
                        var ch = Character.toLowerCase(chars.get(t, c));
                        if ("agrdv".indexOf(ch) >= 0)
                            countR++;
                        else if ("ctuybh".indexOf(ch) >= 0)
                            countY++;
                    }
                    var rAgainstOthers = (countR <= countY);

                    char stateTaxon1 = 0;
                    for (int t = 1; t <= chars.getNtax(); t++) {
                        var ch = Character.toLowerCase(chars.get(t, c));
                        char state;
                        if ((rAgainstOthers && "agrdv".indexOf(ch) >= 0) || (!rAgainstOthers && "ctuybh".indexOf(ch) >= 0))
                            state = 0;
                        else
                            state = 1;
                        if (t == 1)
                            stateTaxon1 = state;
                        if (state == stateTaxon1)
                            current.set(t);
                    }
                }
                case MajorityState -> {
                    var charCountArray = new char[256];
                    for (int t = 1; t <= chars.getNtax(); t++) {
                        var ch = chars.get(t, c);
                        if (ch != chars.getMissingCharacter() && ch != chars.getGapCharacter()) {
                            charCountArray[ch]++;
                        }
                    }
                    char majorityState = 0;
                    for (var i = 0; i < charCountArray.length; i++) {
                        if (charCountArray[i] > charCountArray[majorityState]) {
                            majorityState = (char) i;
                        }
                    }
                    char stateTaxon1 = 0;
                    for (int t = 1; t <= chars.getNtax(); t++) {
                        var ch = chars.get(t, c);
                        char state;
                        if (ch == majorityState) {
                            state = '0';
                        } else {
                            state = '1';
                        }
                        if (t == 1)
                            stateTaxon1 = state;
                        if (state == stateTaxon1)
                            current.set(t);
                    }
                }
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
        computedSplits.getSplits().addAll(SplitsUtilities.createAllMissingTrivial(computedSplits.getSplits(), taxaBlock.getNtax(), 0.0));

        if (isOptionHighDimensionFilter()) {
            DimensionFilter.apply(progress, 4, computedSplits.getSplits(), splitsBlock.getSplits());
        } else
            splitsBlock.copy(computedSplits);

        splitsBlock.setCycle(SplitsUtilities.computeCycle(taxaBlock.getNtax(), splitsBlock.getSplits()));
        splitsBlock.setFit(-1);
        splitsBlock.setCompatibility(Compatibility.compute(taxaBlock.getNtax(), splitsBlock.getSplits(), splitsBlock.getCycle()));
    }

    @Override
    public String getCitation() {
        return "Huson et al 2012;D.H. Huson, R. Rupp and C. Scornavacca, Phylogenetic Networks, Cambridge, 2012.";
    }

    @Override
    public boolean isApplicable(TaxaBlock taxaBlock, CharactersBlock parent) {
        return parent.getDataType().isNucleotides();
    }

    public Method getOptionMethod() {
        return optionMethod.get();
    }

    public ObjectProperty<Method> optionMethodProperty() {
        return optionMethod;
    }

    public void setOptionMethod(Method optionMethod) {
        this.optionMethod.set(optionMethod);
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
}

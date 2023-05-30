/*
 * SplitsBlock.java Copyright (C) 2023 Daniel H. Huson
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

package splitstree6.data;

import javafx.collections.FXCollections;
import jloda.util.StringUtils;
import splitstree6.algorithms.splits.splits2splits.SplitsTaxaFilter;
import splitstree6.algorithms.utils.SplitsUtilities;
import splitstree6.data.parts.ASplit;
import splitstree6.data.parts.Compatibility;
import splitstree6.workflow.DataBlock;
import splitstree6.workflow.DataTaxaFilter;

import java.util.*;

public class SplitsBlock extends DataBlock {
	private final ArrayList<ASplit> splits;

	private Compatibility compatibility = Compatibility.unknown;
	private float fit = -1;

	private float threshold = 0; // todo: this belongs in SplitsFilter?

	private boolean partial = false;

	private int[] cycle;

	private SplitsFormat format;

	private final Map<Integer, String> splitLabels;

	/**
	 * default constructor
	 */
	public SplitsBlock() {
		splits = new ArrayList<>();
		splitLabels = FXCollections.observableMap(new TreeMap<>());
		format = new SplitsFormat();
	}

	public SplitsBlock(SplitsBlock that) {
		this();
		copy(that);
	}

	/**
	 * shallow copy
	 */
	public void copy(SplitsBlock that) {
		clear();
		for (ASplit split : that.getSplits())
			splits.add(split.clone());
		compatibility = that.getCompatibility();
		fit = that.getFit();
		threshold = that.getThreshold();
		partial = that.isPartial();
		if (that.getCycle() != null)
			cycle = that.getCycle().clone();
		splitLabels.putAll(that.splitLabels);
		format = that.format;
	}

	@Override
	public void clear() {
		super.clear();
		splits.clear();
		cycle = null;
		compatibility = Compatibility.unknown;
		fit = -1;
		threshold = 0;
		setShortDescription("");
		splitLabels.clear();
	}

	public ArrayList<ASplit> getSplits() {
		return splits;
	}

	@Override
	public int size() {
		return splits.size();
	}

	public int getNsplits() {
		return splits.size();
	}

	public Iterable<ASplit> splits() {
        return () -> new Iterator<>() {
            int i = 0; // 0-based because we do not use get(i) but rather splits.get(i)

            @Override
            public boolean hasNext() {
                return i < splits.size();
            }

            @Override
            public ASplit next() {
                if (i >= splits.size())
                    throw new NoSuchElementException();
                return splits.get(i++);
            }
        };
	}

	public Compatibility getCompatibility() {
		return compatibility;
	}

	public void setCompatibility(Compatibility compatibility) {
		this.compatibility = compatibility;
	}

	public float getFit() {
		return fit;
	}

	public void setFit(float fit) {
		this.fit = fit;
	}

	public float getThreshold() {
		return threshold;
	}

	public void setThreshold(float threshold) {
		this.threshold = threshold;
	}

	public boolean isPartial() {
		return partial;
	}

	public void setPartial(boolean partial) {
		this.partial = partial;
	}

	public int[] getCycle() {
		if (cycle == null && getNsplits() > 0) {
			final BitSet taxa = new BitSet();
			for (ASplit split : splits) {
				taxa.or(split.getA());
				taxa.or(split.getB());
			}
			cycle = SplitsUtilities.computeCycle(taxa.cardinality(), splits);
		}
		return cycle;
	}

	/**
	 * get the i-th split, 1-based
	 *
	 * @return i-th split
	 */
	public ASplit get(int i) {
		return splits.get(i - 1);
	}

	/**
	 * gets  all taxa that are included in one specified side of one split and also one specified side of the other split.
	 *
	 * @param splitP the index of split "P", 1-based
	 * @param sideP  the "side" of the split P that should be considered
	 * @param splitQ the index of the other split "Q", 1-based
	 * @param sideQ  the "side" of the split Q that should be considered
	 */
	public BitSet intersect2(int splitP, boolean sideP, int splitQ, boolean sideQ) {
		final BitSet result = new BitSet();
		result.or(sideP ? get(splitP).getA() : get(splitP).getB());
		result.and(sideQ ? get(splitQ).getA() : get(splitQ).getB());
		return result;
	}

	public void setCycle(int[] cycle) {
		setCycle(cycle, true);
	}

	public void setCycle(Collection<Integer> cycle) {
		var array1based = new int[cycle.size() + 1];
		var index = 0;
		for (var value : cycle) {
			array1based[++index] = value;
		}
		setCycle(array1based, true);
	}


	/**
	 * set the cycle and possibly normalize it
	 */
	public void setCycle(int[] cycle, boolean normalize) {
		if (cycle != null) {
			BitSet set = new BitSet();
			for (int i : cycle) {
				set.set(i);
			}
			if (set.cardinality() != cycle.length) {
				System.err.println("Internal error: setCycle() failed: wrong size");
				cycle = null;
			} else {
				if (normalize)
					cycle = SplitsUtilities.normalizeCycle(cycle);
			}
		}
		this.cycle = cycle;
	}


	public boolean hasConfidenceValues() {
		return splits.stream().anyMatch(s -> s.getConfidence() != -1);
	}

	public Map<Integer, String> getSplitLabels() {
		return splitLabels;
	}


	public int indexOf(ASplit split) {
		for (int s = 1; s <= getNsplits(); s++) {
			if (get(s).equals(split))
				return s;
		}
		return -1;
	}

	@Override
	public DataTaxaFilter<? extends DataBlock, ? extends DataBlock> createTaxaDataFilter() {
		return new SplitsTaxaFilter();
	}

	@Override
	public SplitsBlock newInstance() {
		return (SplitsBlock) super.newInstance();
	}

	public SplitsFormat getFormat() {
		return format;
	}

	public void setFormat(SplitsFormat format) {
		this.format = format;
	}

	public static final String BLOCK_NAME = "SPLITS";

	@Override
	public void updateShortDescription() {
		setShortDescription(String.format("%,d splits", getNsplits()) + (compatibility != Compatibility.unknown ? ", " + StringUtils.fromCamelCase(compatibility.toString()).toLowerCase() : ""));
	}

	@Override
	public String getBlockName() {
		return BLOCK_NAME;
	}

}

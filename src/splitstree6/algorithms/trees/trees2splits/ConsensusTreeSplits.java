/*
 * ConsensusTreeSplits.java Copyright (C) 2021. Daniel H. Huson
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

package splitstree6.algorithms.trees.trees2splits;

import javafx.beans.property.SimpleObjectProperty;
import jloda.util.BitSetUtils;
import jloda.util.progress.ProgressListener;
import splitstree6.algorithms.utils.SplitsUtilities;
import splitstree6.data.SplitsBlock;
import splitstree6.data.TaxaBlock;
import splitstree6.data.TreesBlock;
import splitstree6.data.parts.ASplit;
import splitstree6.data.parts.Compatibility;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * implements consensus tree splits
 * <p>
 * Daniel Huson, 2.2018
 */
public class ConsensusTreeSplits extends Trees2Splits {
	public enum Consensus {Strict, Majority, Greedy} // todo: add loose?

	private final SimpleObjectProperty<Consensus> optionConsensus = new SimpleObjectProperty<>(this, "optionConsensus", Consensus.Majority);
	private final SimpleObjectProperty<ConsensusNetwork.EdgeWeights> optionEdgeWeights = new SimpleObjectProperty<>(this, "optionEdgeWeights", ConsensusNetwork.EdgeWeights.TreeSizeWeightedMean);

	@Override
	public List<String> listOptions() {
		return List.of(optionConsensus.getName(), optionEdgeWeights.getName());
	}

	@Override
	public String getToolTip(String optionName) {
		if (optionName.equals(optionConsensus.getName()))
			return "Select consensus method";
		else if (optionName.equals(optionEdgeWeights.getName()))
			return "Determine how to calculate edge weights in resulting network";
		else
			return super.getToolTip(optionName);
	}

	/**
	 * compute the consensus splits
	 */
	public void compute(ProgressListener progress, TaxaBlock taxaBlock, TreesBlock parent, SplitsBlock child) throws IOException {
		final ConsensusNetwork consensusNetwork = new ConsensusNetwork();
		switch (getOptionConsensus()) {
			default -> consensusNetwork.setOptionThresholdPercent(50);
			case Majority -> consensusNetwork.setOptionThresholdPercent(50);
			case Strict -> consensusNetwork.setOptionThresholdPercent(99.999999); // todo: implement without use of splits
			case Greedy -> consensusNetwork.setOptionThresholdPercent(0);
		}
		final SplitsBlock consensusSplits = new SplitsBlock();
		consensusNetwork.setOptionEdgeWeights(getOptionEdgeWeights());
		consensusNetwork.compute(progress, taxaBlock, parent, consensusSplits);

		if (getOptionConsensus().equals(Consensus.Greedy)) {
			final ArrayList<ASplit> list = new ArrayList<>(consensusSplits.getSplits());
			list.sort((s1, s2) -> {
				if (s1.getWeight() > s2.getWeight())
					return -1;
				else if (s1.getWeight() < s2.getWeight())
					return 1;
				else
					return BitSetUtils.compare(s1.getA(), s2.getA());
			});
			for (ASplit split : list) {
				if (Compatibility.isCompatible(split, child.getSplits()))
					child.getSplits().add(split);
			}
		} else {
			child.getSplits().clear();
			child.getSplits().addAll(consensusSplits.getSplits());
		}

		child.setCompatibility(Compatibility.compatible);
		child.setCycle(SplitsUtilities.computeCycle(taxaBlock.getNtax(), child.getSplits()));
	}

	@Override
	public boolean isApplicable(TaxaBlock taxaBlock, TreesBlock parent) {
		return !parent.isPartial() && !parent.isReticulated();
	}

	public Consensus getOptionConsensus() {
		return optionConsensus.get();
	}

	public SimpleObjectProperty<Consensus> optionConsensusProperty() {
		return optionConsensus;
	}

	public void setOptionConsensus(Consensus optionConsensus) {
		this.optionConsensus.set(optionConsensus);
	}

	public ConsensusNetwork.EdgeWeights getOptionEdgeWeights() {
		return optionEdgeWeights.get();
	}

	public SimpleObjectProperty<ConsensusNetwork.EdgeWeights> optionEdgeWeightsProperty() {
		return optionEdgeWeights;
	}

	public void setOptionEdgeWeights(ConsensusNetwork.EdgeWeights optionEdgeWeights) {
		this.optionEdgeWeights.set(optionEdgeWeights);
	}
}

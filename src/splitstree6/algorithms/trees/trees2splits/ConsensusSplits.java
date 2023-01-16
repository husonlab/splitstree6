/*
 * ConsensusSplits.java Copyright (C) 2023 Daniel H. Huson
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

package splitstree6.algorithms.trees.trees2splits;

import javafx.beans.property.DoubleProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleObjectProperty;
import jloda.util.progress.ProgressListener;
import splitstree6.algorithms.utils.GreedyCircular;
import splitstree6.algorithms.utils.GreedyCompatible;
import splitstree6.algorithms.utils.GreedyWeaklyCompatible;
import splitstree6.algorithms.utils.SplitsUtilities;
import splitstree6.data.SplitsBlock;
import splitstree6.data.TaxaBlock;
import splitstree6.data.TreesBlock;
import splitstree6.data.parts.ASplit;
import splitstree6.data.parts.Compatibility;

import java.io.IOException;
import java.util.List;

/**
 * implements consensus tree splits
 * <p>
 * Daniel Huson, 2.2018
 */
public class ConsensusSplits extends Trees2Splits {
	public enum Consensus {Majority, Strict, GreedyCompatible, GreedyCircular, GreedyWeaklyCompatible} // todo: add loose?

	private final ObjectProperty<Consensus> optionConsensus = new SimpleObjectProperty<>(this, "optionConsensus", Consensus.Majority);
	private final ObjectProperty<ConsensusNetwork.EdgeWeights> optionEdgeWeights = new SimpleObjectProperty<>(this, "optionEdgeWeights", ConsensusNetwork.EdgeWeights.TreeSizeWeightedMean);
	private final DoubleProperty optionThresholdPercent = new SimpleDoubleProperty(this, "optionThresholdPercent", 0.0);

	@Override
	public List<String> listOptions() {
		return List.of(optionConsensus.getName(), optionEdgeWeights.getName(), optionThresholdPercent.getName());
	}

	@Override
	public String getToolTip(String optionName) {
		if (optionName.equals(optionConsensus.getName()))
			return "Select consensus method";
		else if (optionName.equals(optionEdgeWeights.getName()))
			return "Determine how to calculate edge weights in resulting network";
		else if (optionName.equals(optionThresholdPercent.getName()))
			return "Determine threshold for percent of input trees that split has to occur in for it to appear in the output";
		else
			return super.getToolTip(optionName);
	}

	/**
	 * compute the consensus splits
	 */
	public void compute(ProgressListener progress, TaxaBlock taxaBlock, TreesBlock treesBlock, SplitsBlock splitsBlock) throws IOException {
		final var consensusNetwork = new ConsensusNetwork();
		switch (getOptionConsensus()) {
			case Majority -> {
				consensusNetwork.setOptionThresholdPercent(50);
				setOptionThresholdPercent(0);
			}
			case Strict -> {
				consensusNetwork.setOptionThresholdPercent(99.999999); // todo: implement without use of splits
				setOptionThresholdPercent(0);
			}
			default -> consensusNetwork.setOptionThresholdPercent(getOptionThresholdPercent());
		}

		final var consensusSplits = new SplitsBlock();
		consensusNetwork.setOptionEdgeWeights(getOptionEdgeWeights());
		consensusNetwork.setOptionHighDimensionFilter(false);
		consensusNetwork.compute(progress, taxaBlock, treesBlock, consensusSplits);

		splitsBlock.clear();

		switch (getOptionConsensus()) {
			case GreedyCompatible -> {
				splitsBlock.getSplits().addAll(GreedyCompatible.apply(progress, consensusSplits.getSplits(), ASplit::getConfidence));
				splitsBlock.setCompatibility(Compatibility.compatible);
			}
			case GreedyCircular -> {
				splitsBlock.getSplits().addAll(GreedyCircular.apply(progress, taxaBlock.getTaxaSet(), consensusSplits.getSplits(), ASplit::getConfidence));
				splitsBlock.setCompatibility(Compatibility.compute(taxaBlock.getNtax(), splitsBlock.getSplits()));
			}
			case GreedyWeaklyCompatible -> {
				splitsBlock.getSplits().addAll(GreedyWeaklyCompatible.apply(progress, consensusSplits.getSplits(), ASplit::getConfidence));
				splitsBlock.setCompatibility(Compatibility.compute(taxaBlock.getNtax(), splitsBlock.getSplits()));
			}
			case Majority, Strict -> {
				splitsBlock.getSplits().clear();
				splitsBlock.getSplits().addAll(consensusSplits.getSplits());
				splitsBlock.setCompatibility(Compatibility.compute(taxaBlock.getNtax(), splitsBlock.getSplits()));
				splitsBlock.setCompatibility(Compatibility.compatible);
			}
		}
		splitsBlock.setCycle(SplitsUtilities.computeCycle(taxaBlock.getNtax(), splitsBlock.getSplits()));
	}

	@Override
	public boolean isApplicable(TaxaBlock taxaBlock, TreesBlock parent) {
		return !parent.isPartial();
	}

	public Consensus getOptionConsensus() {
		return optionConsensus.get();
	}

	public ObjectProperty<Consensus> optionConsensusProperty() {
		return optionConsensus;
	}

	public void setOptionConsensus(Consensus optionConsensus) {
		this.optionConsensus.set(optionConsensus);
	}

	public void setOptionConsensus(String optionConsensus) {
		if (optionConsensus.equals("Greedy"))
			optionConsensus = "GreedyCompatible";
		this.optionConsensus.set(Consensus.valueOf(optionConsensus));
	}

	public ConsensusNetwork.EdgeWeights getOptionEdgeWeights() {
		return optionEdgeWeights.get();
	}

	public ObjectProperty<ConsensusNetwork.EdgeWeights> optionEdgeWeightsProperty() {
		return optionEdgeWeights;
	}

	public void setOptionEdgeWeights(ConsensusNetwork.EdgeWeights optionEdgeWeights) {
		this.optionEdgeWeights.set(optionEdgeWeights);
	}

	public double getOptionThresholdPercent() {
		return optionThresholdPercent.get();
	}

	public DoubleProperty optionThresholdPercentProperty() {
		return optionThresholdPercent;
	}

	public void setOptionThresholdPercent(double optionThresholdPercent) {
		this.optionThresholdPercent.set(optionThresholdPercent);
	}
}

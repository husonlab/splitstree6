/*
 * NeighborNet.java Copyright (C) 2022 Daniel H. Huson
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

package splitstree6.algorithms.distances.distances2splits;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import jloda.util.CanceledException;
import jloda.util.progress.ProgressListener;
import jloda.util.progress.ProgressSilent;
import splitstree6.algorithms.distances.distances2splits.neighbornet.NeighborNetCycle;
import splitstree6.algorithms.distances.distances2splits.neighbornet.NeighborNetSplits;
import splitstree6.algorithms.splits.IToCircularSplits;
import splitstree6.algorithms.utils.SplitsUtilities;
import splitstree6.data.DistancesBlock;
import splitstree6.data.SplitsBlock;
import splitstree6.data.TaxaBlock;
import splitstree6.data.parts.ASplit;
import splitstree6.data.parts.Compatibility;

import java.util.ArrayList;
import java.util.List;

public class NeighborNet extends Distances2Splits implements IToCircularSplits {
	// public enum WeightsAlgorithm {NNet2004, NNet2021, LP}
	//public enum WeightsAlgorithm {NNet2004, NNet2021}

	//private final ObjectProperty<WeightsAlgorithm> optionWeights = new SimpleObjectProperty<>(this,"optionWeights",WeightsAlgorithm.NNet2004);

	//public enum InferenceAlgorithm {ActiveSet, BlockPivot}
	public enum InferenceAlgorithm {BlockPivot,SpeedKnitter}  //TODO: ActiveSet not working at present. Will be rewritten.

	private final ObjectProperty<InferenceAlgorithm> optionInferenceAlgorithm = new SimpleObjectProperty<>(this, "optionInferenceAlgorithm", InferenceAlgorithm.BlockPivot);

	private final BooleanProperty optionUsePreconditioner = new SimpleBooleanProperty(this, "optionUsePreconditioner", false);

	private final BooleanProperty optionUseDual = new SimpleBooleanProperty(this, "optionUseDual", true);

	public List<String> listOptions() {
		return List.of(optionInferenceAlgorithm.getName(), optionUsePreconditioner.getName(),optionUseDual.getName());
	}

	@Override
	public String getCitation() {
		return "Bryant & Moulton 2004; " +
			   "D. Bryant and V. Moulton. Neighbor-net: An agglomerative method for the construction of phylogenetic networks. " +
			   "Molecular Biology and Evolution, 21(2):255– 265, 2004.";
	}


	/**
	 * run the neighbor net algorithm
	 */
	@Override
	public void compute(ProgressListener progress, TaxaBlock taxaBlock, DistancesBlock distancesBlock, SplitsBlock splitsBlock) throws CanceledException {
		double optionThreshold = 1e-6; //TODO This should be a parameter, or maybe just set to zero and use the splits filter.

		if (SplitsUtilities.computeSplitsForLessThan4Taxa(taxaBlock, distancesBlock, splitsBlock))
			return;

		progress.setMaximum(-1);

		final var cycle = NeighborNetCycle.compute(progress, distancesBlock.size(), distancesBlock.getDistances());

		progress.setTasks("NNet", "edge weight optimization");

		final ArrayList<ASplit> splits;

		final var start = System.currentTimeMillis();

		var useBlockPivot = (getOptionInferenceAlgorithm() == InferenceAlgorithm.BlockPivot);
		var speedKnitter =  (getOptionInferenceAlgorithm() == InferenceAlgorithm.SpeedKnitter);
		var useDual = isOptionUseDual();
		var usePreconditioner = isOptionUsePreconditioner();
		splits = NeighborNetSplits.compute(cycle, distancesBlock.getDistances(), optionThreshold, useBlockPivot, useDual, usePreconditioner, speedKnitter, progress);

		// add all missing trivial
		splits.addAll(SplitsUtilities.createAllMissingTrivial(splits, taxaBlock.getNtax()));

		if (Compatibility.isCompatible(splits))
			splitsBlock.setCompatibility(Compatibility.compatible);
		else
			splitsBlock.setCompatibility(Compatibility.cyclic);
		splitsBlock.setCycle(cycle);
		splitsBlock.setFit(SplitsUtilities.computeLeastSquaresFit(distancesBlock, splits));

		splitsBlock.getSplits().addAll(splits);

		if (!(progress instanceof ProgressSilent)) {
			var seconds = (System.currentTimeMillis() - start) / 1000.0;
			if (seconds > 10)
				System.err.printf("Time: %,.1fs%n", seconds);
		}
	}

	@Override
	public boolean isApplicable(TaxaBlock taxaBlock, DistancesBlock parent) {
		return parent.getNtax() > 0;
	}

    /* public WeightsAlgorithm getOptionWeights() {
        return optionWeights.get();
    }

    public ObjectProperty<WeightsAlgorithm> optionWeightsProperty() {
        return optionWeights;
    }

    public void setOptionWeights(WeightsAlgorithm optionWeights) {
        this.optionWeights.set(optionWeights);
    }
    */

	public InferenceAlgorithm getOptionInferenceAlgorithm() {
		return optionInferenceAlgorithm.get();
	}

	public ObjectProperty<InferenceAlgorithm> optionInferenceAlgorithmProperty() {
		return optionInferenceAlgorithm;
	}

	public void setOptionInferenceAlgorithm(InferenceAlgorithm optionInferenceAlgorithm) {
		this.optionInferenceAlgorithm.set(optionInferenceAlgorithm);
	}

	public boolean isOptionUsePreconditioner() {
		return optionUsePreconditioner.get();
	}

	public BooleanProperty optionUsePreconditionerProperty() {
		return optionUsePreconditioner;
	}

	public void setOptionUsePreconditioner(boolean optionUsePreconditioner) {
		this.optionUsePreconditioner.set(optionUsePreconditioner);
	}

	public boolean isOptionUseDual() {
		return optionUseDual.get();
	}

	public BooleanProperty optionUseDualProperty() {
		return optionUseDual;
	}

	public void setOptionUseDual(boolean optionUseDual) {
		this.optionUseDual.set(optionUseDual);
	}



}

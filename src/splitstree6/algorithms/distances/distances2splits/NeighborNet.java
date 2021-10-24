/*
 *  NeighborNet.java Copyright (C) 2021 Daniel H. Huson
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

package splitstree6.algorithms.distances.distances2splits;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import jloda.util.CanceledException;
import jloda.util.progress.ProgressListener;
import splitstree6.algorithms.distances.distances2splits.neighbornet.NeighborNetCycle;
import splitstree6.algorithms.distances.distances2splits.neighbornet.NeighborNetSplits;
import splitstree6.data.DistancesBlock;
import splitstree6.data.SplitsBlock;
import splitstree6.data.TaxaBlock;
import splitstree6.data.parts.ASplit;
import splitstree6.data.parts.Compatibility;
import splitstree6.utils.SplitsUtilities;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class NeighborNet extends Distances2Splits {
	// public enum WeightsAlgorithm {NNet2004, NNet2021, LP}
	public enum WeightsAlgorithm {NNet2004, NNet2021}

	private final ObjectProperty<WeightsAlgorithm> optionWeights = new SimpleObjectProperty<>(WeightsAlgorithm.NNet2004);

	public enum InferenceAlgorithm {ActiveSet, BlockPivot}

	private final ObjectProperty<InferenceAlgorithm> optionInferenceAlgorithm = new SimpleObjectProperty<>(InferenceAlgorithm.BlockPivot);

	private final BooleanProperty optionUsePreconditioner = new SimpleBooleanProperty(true);

	public List<String> listOptions() {
		return Arrays.asList("optionInferenceAlgorithm", "optionUsePreconditioner", "optionWeights");
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

		// todo: this is for testing the WorkflowTab
		if (false)
			try {
				Thread.sleep(4000);
			} catch (InterruptedException ignored) {
			}

		if (SplitsUtilities.computeSplitsForLessThan4Taxa(taxaBlock, distancesBlock, splitsBlock))
			return;

		progress.setMaximum(-1);

		final int[] cycle = NeighborNetCycle.compute(progress, distancesBlock.size(), distancesBlock.getDistances());

		progress.setTasks("NNet", "edge weights");

		final ArrayList<ASplit> splits;

		final long start = System.currentTimeMillis();


		//  if (getOptionWeights().equals(WeightsAlgorithm.LP))
		//      splits = NeighborNetSplitsLP.compute(taxaBlock.getNtax(), cycle, distancesBlock.getDistances(), 0.000001, progress);
		//  else
		splits = NeighborNetSplits.compute(getOptionWeights().equals(WeightsAlgorithm.NNet2021),
				taxaBlock.getNtax(), cycle, distancesBlock.getDistances(), distancesBlock.getVariances(), 0.000001, NeighborNetSplits.LeastSquares.ols, NeighborNetSplits.Regularization.nnls, 1,
				progress);

		if (Compatibility.isCompatible(splits))
			splitsBlock.setCompatibility(Compatibility.compatible);
		else
			splitsBlock.setCompatibility(Compatibility.cyclic);
		splitsBlock.setCycle(cycle);
		splitsBlock.setFit(SplitsUtilities.computeLeastSquaresFit(distancesBlock, splits));

		splitsBlock.getSplits().addAll(splits);

		System.err.println("Time: " + Math.round((System.currentTimeMillis() - start)) + "ms");
	}

	@Override
	public boolean isApplicable(TaxaBlock taxaBlock, DistancesBlock parent) {
		return parent.getNtax() > 0;
	}

	public WeightsAlgorithm getOptionWeights() {
		return optionWeights.get();
	}

	public ObjectProperty<WeightsAlgorithm> optionWeightsProperty() {
		return optionWeights;
	}

	public void setOptionWeights(WeightsAlgorithm optionWeights) {
		this.optionWeights.set(optionWeights);
	}

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
}
